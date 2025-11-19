import Global.delay
import Global.duration
import Global.instrument
import Global.legato
import Global.score
import Global.swing8
import Global.swingDot8
import Global.volume
import MessageType.*
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.ToggleGroup
import tornadofx.*
import java.io.InputStreamReader
import java.io.LineNumberReader
import java.rmi.UnexpectedException
import java.util.*
import javax.sound.midi.*
import javax.sound.midi.Sequence.PPQ
import javax.sound.midi.ShortMessage.*
import kotlin.collections.ArrayList
import kotlin.collections.asList
import kotlin.collections.contains
import kotlin.collections.find
import kotlin.collections.forEach
import kotlin.collections.indexOf
import kotlin.collections.indices
import kotlin.collections.isEmpty
import kotlin.collections.last
import kotlin.collections.lastIndex
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toTypedArray
import kotlin.collections.withIndex
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.math.min

class MidiSequencer : MessageCreator, MessageListener {
    val tickPosition: Long
        get() {
            return sequencer.tickPosition
        }
    private val sequencer: Sequencer = MidiSystem.getSequencer(false)
    private var seq = Sequence(PPQ, 1, 16)
    private var instruments: Array<Instrument>
    private var savedInstMenu: Menu? = null
    private val muted = HashSet<VoicePart>()
    private val soloed = HashSet<VoicePart>()
    private val devices: Array<MidiDevice.Info> = MidiSystem.getMidiDeviceInfo()
    private val toggleGroup = ToggleGroup()
    var midiOut: MidiDeviceReceiver? = null
    private var listener = MidiEventListener(this)
    private val channelMap = HashMap<Long, Int>() // tick -> bitmap of busy channels

    init {
        subscribe(this, TempoChange, InstrumentChange, LegatoChange,
                PlayRequest, ArpeggiateRequest, Swing8Change, SwingDot8Change,
                StopRequest, MuteRequest, SoloRequest)
        sequencer.open()
        instruments = makeInstruments()
        sequencer.addMetaEventListener(listener)
        openDevice("Gervill")
        sequencer.tempoInBPM = Global.tempo.toFloat()
    }

    fun openDevice(regex: String) {
        for (d in devices) {
            val device = MidiSystem.getMidiDevice(d)
            if (device.maxReceivers != 0) {
                val s = device.infoString()
                if (s.contains(regex.toRegex())) {
                    choose(device)
                    return
                }
            }
        }
        Main.log("Cannot locate MIDI output device matching \"$regex\"")
    }

    private fun makeInstruments(): Array<Instrument> {
        if (midiOut() && midiOut!!.midiDevice.deviceInfo == MidiSystem.getSynthesizer().deviceInfo) {
            return MidiSystem.getSynthesizer().availableInstruments.asList().toTypedArray()
        }
        val arr = ArrayList<Instrument>()
        val url = javaClass.getResource("GM-instruments.txt")
        val rdr = LineNumberReader(InputStreamReader(url.openStream()))
        var n = 0
        rdr.forEachLine { name ->
            val inst = MyInstrument(Patch(0, n++), name)
            arr += inst
        }
        return arr.toTypedArray()
    }

    private fun connect(rec: Receiver) {
        sequencer.transmitter.receiver = rec
        sequencer.open()
    }

    private fun createSeq() {
        if (!midiOut())
            return
        setupChannels()
        if (score.isEmpty())
            return
        if (legato.value) {
            var now = 0L
            channelMap.clear()
            var nextMeasure = -1
            var repeatFrom = -1
            var repeatNum = 1
            var maxRepeat = 1
            for (m in score.measures) {
                for (e in m.endings) {
                    maxRepeat = max(maxRepeat, e)
                }
            }
            var i = score.measures[0].number
            while (score.measureNumber(i) != null) {
                var measure = score.measureNumber(i)
                val fine = measure!!.direction.find { d -> d.first == "fine" }
                if (fine != null && repeatNum > 1) {
                    // stop after this measure
                    nextMeasure = score.measures.last().number + 1
                }
                val segno = measure.direction.find { d -> d.first == "dalsegno" }
                if (segno != null && repeatNum == 1) {
                    val to = score.measures.find { m ->
                        m.direction.find { it.first == "segno" && it.second == segno.second } != null
                    }
                    if (to != null) {
                        // jump to the sign after this measure
                        repeatNum++
                        nextMeasure = to.number
                    }
                }
                val coda = measure.direction.find { d -> d.first == "tocoda" }
                if (coda != null && repeatNum > 1) {
                    val to = score.measures.find { m ->
                        m.direction.find { it.first == "coda" && it.second == coda.second } != null
                    }
                    if (to != null) {
                        // jump to the coda after this measure
                        nextMeasure = to.number
                    }
                }
                val capo = measure.direction.find { d -> d.first == "dacapo" }
                if (capo != null && repeatNum == 1) {
                    // jump to the first measure after this one
                    repeatNum++
                    nextMeasure = score.measures[0].number
                }
                if (measure.endingType in arrayOf(EndingType.Start, EndingType.StartStop)) {
                    if (repeatNum !in measure.endings) {
                        while (i < score.measures.size) {
                            if (score.measures[i].endings.contains(repeatNum)) {
                                measure = score.measures[i]
                                break
                            }
                            i++
                        }
                    }
                }
                if (measure!!.repeatType == RepeatType.Forward) {
                    repeatFrom = measure.number
                } else if (measure.repeatType == RepeatType.Backward) {
                    nextMeasure = repeatFrom
                    repeatNum++
                    if (repeatNum > maxRepeat)
                        break
                }
                metronome(now, measure)
                for (note in measure.notes) {
                    addNote(measure, note, now)
                }
                now += measure.length
                if (nextMeasure >= 0) {
                    i = score.measureNumber(nextMeasure)?.number ?: break
                    nextMeasure = -1
                } else {
                    i++
                }
            }
        } else {
            var m: MeasureData? = score.measures[0]
            emitMeasure(m!!.number, m.start)
            for (item in items()) {
                if (m?.containsTick(item.tick) == false) {
                    m = score.measureNumber(m.number + 1)
                    emitMeasure(m!!.number, m.start)
                }
                addItem(item)
            }
        }
        sequencer.tickPosition = curItem.tick
        sequencer.sequence = seq
        sequencer.tempoInBPM = Global.tempo.toFloat()
    }

    private fun setupChannels() {
        seq = Sequence(PPQ, Global.divsPerQuarterNote(), 16)
        sequencer.sequence = seq
        val bank = instrument?.patch?.bank ?: 0
        val pgm = instrument?.patch?.program ?: 0
        for (i in 0..15) {
            val m1 = ShortMessage(CONTROL_CHANGE, i, 0, bank shr 7)
            val m2 = ShortMessage(CONTROL_CHANGE, i, 32, bank and 0x7f)
            val m3 = ShortMessage(PROGRAM_CHANGE, i, pgm, 0)
            seq.tracks[i].add(MidiEvent(m1, 0))
            seq.tracks[i].add(MidiEvent(m2, 0))
            seq.tracks[i].add(MidiEvent(m3, 0))
        }
    }

    private fun findChannel(tick: Long, count: Int): Int {
        for (chan in 0..15) {
            var ok = true
            for (t in tick until (tick + count)) {
                val bm = channelMap.getOrDefault(t, 0)
                if (bm and (1 shl chan) != 0) {
                    ok = false
                }
            }
            if (ok)
                return chan
        }
        throw UnexpectedException("Out of MIDI channels at tick $tick")
    }

    private fun allocateChannel(chan: Int, start: Long, count: Int) {
        for (tick in start until (start + count)) {
            var bm = channelMap.getOrDefault(tick, 0)
            if (bm and (1 shl chan) != 0) {
                throw UnexpectedException("Trying to allocate busy channel")
            } else {
                bm = bm or (1 shl chan)
                channelMap[tick] = bm
            }
        }
    }

    private fun findPitch(m: MeasureData, note: NoteData): Pitch? {
        val item = score.items().find { it.tick - m.start == note.startTime.toLong() } ?: return null
        for (p in item.pitches) {
            if (p.n == note.pitch?.n) {
                return p
            }
        }
        return null
    }

    private fun findLength(pair: Pair<MeasureData, NoteData>): Int {
        val dpq = Global.divsPerQuarterNote()
        val (_, note) = pair
        if (note.tieFrom)
            return note.length + findLength(next(pair))
        // swung eighth?
        if (swing8 && note.startTime % dpq == 0 && note.length == dpq / 2)
            return (dpq * 2) / 3
        if (swing8 && note.startTime % dpq == dpq / 2 && note.length == dpq / 2)
            return dpq / 3
        // swung dotted eighth?
        if (swingDot8 && note.startTime % dpq == 0 && note.length == (dpq * 3) / 4)
            return (dpq * 2) / 3
        if (swingDot8 && note.startTime % dpq == (dpq * 3) / 4 && note.length == dpq / 4)
            return dpq / 3
        return note.length
    }

    private fun next(pair: Pair<MeasureData, NoteData>): Pair<MeasureData, NoteData> {
        val (m, note) = pair
        val p = note.pitch!!
        for (n in m.notes) {
            if (n.startTime <= note.startTime)
                continue
            if (n.pitch?.n == p.n) {
                if (!n.tieTo)
                    continue
                return Pair(m, n)
            }
        }
        val num = m.number + 1
        val nextMeasure = score.measureNumber(num)
                ?: throw UnexpectedException("Cannot locate end of tied note")
        for (n in nextMeasure.notes) {
            if (n.pitch?.n == p.n) {
                if (!n.tieTo)
                    continue
                return Pair(nextMeasure, n)
            }
        }
        throw UnexpectedException("Cannot locate end of tied note")
    }

    private fun addNote(m: MeasureData, note: NoteData, measureStart: Long) {
        val p = findPitch(m, note) ?: return // rest
        if (!playing(p))
            return
        val tick = measureStart + m.getStartTime(note, swing8, swingDot8).toLong()
        if (note.tieTo)
            return
//        emitTick(measureStart + m.getStartTime(note).toLong(), m)
        val meta = MetaMessage()
        meta.setMessage(6, byteArrayOf(0), 1)
        seq.tracks[0].add(MidiEvent(meta, tick))
        val length = findLength(Pair(m, note))
        val chan = findChannel(tick, length)
        allocateChannel(chan, tick, length)
        val pb = 8192 + if (isJust()) p.pitchBend() else 0
        val msb = (pb shr 7) and 0x7f
        val lsb = pb and 0x7f
        val msg = ShortMessage(NOTE_ON, chan, p.n, volume)
        val pbm = ShortMessage(PITCH_BEND, chan, lsb, msb)
        if (isJust())
            seq.tracks[chan].add(MidiEvent(pbm, tick))
        seq.tracks[chan].add(MidiEvent(msg, tick))
        val off = ShortMessage(NOTE_OFF, chan, p.n, 0)
        seq.tracks[chan].add(MidiEvent(off, tick + length))
    }

    private fun addItem(item: Item) {
        emitTick(item.tick, item.measure!!)
        for ((i, p) in item.pitches.withIndex()) {
            if (!playing(p))
                continue
            val chan = if (i == 9) 15 else i
            val pb = 8192 + if (isJust()) p.pitchBend() else 0
            val msb = (pb shr 7) and 0x7f
            val lsb = pb and 0x7f
            val msg = ShortMessage(NOTE_ON, chan, p.n, volume)
            val pbm = ShortMessage(PITCH_BEND, chan, lsb, msb)
            val tick = item.tick
            if (isJust())
                seq.tracks[chan].add(MidiEvent(pbm, tick))
            seq.tracks[chan].add(MidiEvent(msg, tick))
            val off = ShortMessage(NOTE_OFF, chan, p.n, 0)
            seq.tracks[chan].add(MidiEvent(off, tick + item.length))
        }
    }

    private fun emitMeasure(num: Int, tick: Long) {
        val b = byteArrayOf(0, 0, 0, 0, 0)
        b[0] = 2 // start of measure
        b[1] = ((num shr 21) and 0x7F).toByte()
        b[2] = ((num shr 14) and 0x7F).toByte()
        b[3] = ((num shr 7) and 0x7F).toByte()
        b[4] = ((num) and 0x7F).toByte()
        val msg = MetaMessage()
        msg.setMessage(6, b, 5)
        seq.tracks[0].add(MidiEvent(msg, tick))
    }

    private fun getClickLength(m: MeasureData): Int {
        val factor = when (Global.noteValue) { // in sixteenths
            "auto" -> 0
            "sixteenth" -> 1
            "eighth" -> 2
            "dotted eighth" -> 3
            "quarter" -> 4
            "dotted quarter" -> 6
            "half" -> 8
            "dotted half" -> 12
            "whole" -> 16
            else -> throw IllegalArgumentException("Unexpected noteValue: ${Global.noteValue}")
        }
        if (factor == 0) {
            // compute it dynamically
            return when (m.beats) {
                6, 9, 12, 15, 18, 21, 24 -> {
                    m.ticksPerBeat() * 3
                }
                else -> { // every beat
                    m.ticksPerBeat()
                }
            }
        }
        val dpq = Global.divsPerQuarterNote()
        if (dpq * factor / 4 == 0)
            return dpq
        return dpq * factor / 4
    }

    private fun metronome(tick: Long, m: MeasureData) {
        var now = tick
        val start = now
        val num = m.number
        // first message for measure includes measure number
        val b = byteArrayOf(0, 0, 0, 0, 0)
        b[0] = 3 // start of measure + metronome click
        b[1] = ((num shr 21) and 0x7F).toByte()
        b[2] = ((num shr 14) and 0x7F).toByte()
        b[3] = ((num shr 7) and 0x7F).toByte()
        b[4] = ((num) and 0x7F).toByte()
        val length = getClickLength(m)
        var msg = MetaMessage()
//        System.err.println("Metronome for measure $num at $now")
        msg.setMessage(6, b, 5)
        seq.tracks[0].add(MidiEvent(msg, now))
        val b2 = byteArrayOf(1)
        msg = MetaMessage()
        msg.setMessage(6, b2, 1)
        while (now < start + m.length) {
//            System.err.println("Metronome tick at $now")
            emitTick(now, m)
            now += length
        }
    }

    private fun emitTick(tick: Long, m: MeasureData) {
        val msg = ShortMessage(NOTE_ON, 9, Global.clickNote, Global.clickVolume)
        seq.tracks[9].add(MidiEvent(msg, tick))
        val off = ShortMessage(NOTE_OFF, 9, Global.clickNote, 0)
        seq.tracks[9].add(MidiEvent(off, tick + getClickLength(m)))
    }

    private fun play() {
        if (!midiOut())
            return
        val rec = midiOut!!.midiDevice.receiver
        val pitches = curItem.pitches
        var now = 0L
        val totalDur = ((pitches.size - 1) * delay) + duration
        for ((c, p) in pitches.withIndex()) {
            if (!playing(p))
                continue
            val chan = if (c == 9) 15 else c
            val pb = 8192 + if (isJust()) p.pitchBend() else 0
            if (isJust()) {
                val lsb = pb and 0x7f
                val msb = (pb shr 7) and 0x7f
                rec.send(ShortMessage(PITCH_BEND + chan, lsb, msb), -1)
            }
            Timer().schedule(now) {
                rec.send(ShortMessage(NOTE_ON, chan, p.n, volume), -1)
            }
            now += delay
            Timer().schedule(totalDur) {
                rec.send(ShortMessage(NOTE_OFF, chan, p.n, 0), -1)
            }
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            TempoChange -> {
                val tc = msg as TempoChangeMessage
                sequencer.tempoInBPM = tc.newVal.toFloat()
            }
            StopRequest -> {
                sequencer.stop()
            }
            InstrumentChange -> {
                val ic = msg as InstrumentChangeMessage
                instrumentChange(ic.newVal)
            }
            LegatoChange, Swing8Change, SwingDot8Change -> {
                createSeq()
            }
            PlayRequest -> {
                createSeq()
                runLater {
                    if (midiOut()) {
                        sequencer.tickPosition = curItem.tick
                        sequencer.start()
                    }
                }
            }
            ArpeggiateRequest -> {
                play()
            }
            MuteRequest -> {
                val mr = msg as MuteRequestMessage
                if (mr.vp != null) {
                    if (mr.vp.muted)
                        muted.add(mr.vp)
                    else
                        muted.remove(mr.vp)
                } else {
                    muted.clear()
                }
            }
            SoloRequest -> {
                val sr = msg as SoloRequestMessage
                if (sr.vp != null) {
                    if (sr.vp.soloed)
                        soloed.add(sr.vp)
                    else
                        soloed.remove(sr.vp)
                } else {
                    soloed.clear()
                }
            }
            else -> {
                // ignore
            }
        }
    }

    private fun findInstrument(patch: Patch): Instrument {
        for (i in instruments.indices) {
            if (instruments[i].patch.bank == patch.bank && instruments[i].patch.program == patch.program)
                return instruments[i]
        }
        return instruments[0]
    }

    fun parseInstrument(inst: String): Instrument? {
        if (!midiOut())
            return null
        val s = inst.split(",")
        val bank: Int
        val instNum: Int
        if (s.size == 2) {
            bank = s[0].trim().toInt()
            instNum = s[1].trim().toInt()
        } else {
            bank = 0
            instNum = inst.trim().toInt()
        }
        val patch = Patch(bank, instNum - 1)
        return findInstrument(patch)
    }

    fun createDeviceMenu(dev: Menu) {
        var selected = false
        if (devices.isEmpty()) {
            dev.items.clear()
            dev.items.add(MenuItem("(no MIDI devices found)"))
        } else {
            dev.items.clear()
            for (d in devices) {
                val device = MidiSystem.getMidiDevice(d)
                if (device.maxReceivers != 0) {
                    val s = device.infoString()
                    val mi = RadioMenuItem(s)
                    mi.userData = s
                    mi.toggleGroup = toggleGroup
                    mi.setOnAction {
//                        Main.log("Chose: $s")
                        choose(device)
                    }
                    if (device.infoString() == midiOut?.midiDevice?.infoString() ?: "") {
                        mi.isSelected = true
                        selected = true
                    }
                    dev.items.add(mi)
                }
            }
            val mi = RadioMenuItem("None")
            mi.toggleGroup = toggleGroup
            mi.isSelected = !selected
            mi.setOnAction {
                Main.log("Chose: None")
                choose(null)
            }
            dev.items.add(mi)
        }
    }

    private fun choose(device: MidiDevice?) {
        midiOut?.midiDevice?.close()
        midiOut?.close()
        if (device == null) {
            midiOut = null
        } else {
            sequencer.close()
            midiOut = device.receiver as MidiDeviceReceiver?
            try {
                device.open()
                instrumentChange(instrument)
                connect(midiOut!!)
                instruments = makeInstruments()
                createInstrumentMenu()
            } catch (e: MidiUnavailableException) {
                throw ErrorMessage("Failed: ${e.localizedMessage}")
            }
        }
    }

    private fun midiOut() = midiOut != null

    fun createInstrumentMenu(ins: Menu? = savedInstMenu) {
        if (ins == null)
            return
        if (savedInstMenu == null)
            savedInstMenu = ins
        if (instruments.isEmpty()) {
            ins.items.clear()
            ins.items.add(MenuItem("(no sound bank)"))
        } else {
            ins.items.clear()
            val nSubs = (instruments.size - 1) / 32
            val subs = ArrayList<Menu>()
            for (i in 0..nSubs) {
                val from = i * 32
                val upto = min((i + 1) * 32 - 1, instruments.size - 1)
                subs.add(Menu("$from - $upto"))
                ins.items.add(subs[i])
            }
            var n = 0
            instruments.forEach { inst ->
                val s = n / 32
                val name = "$n ${inst.desc()}"
                val item = MenuItem(name)
                item.setOnAction {
                    send(InstrumentChangeMessage(inst), meToo = true)
                }
                subs[s].items.add(item)
                n++
            }
        }
    }

    private fun instrumentChange(inst: Instrument?) {
        if (inst == null)
            return
        val patch = Patch(inst.patch.bank, inst.patch.program)
//        channels.forEach { chan ->
//            chan.programChange(patch.bank, patch.program)
//        }
        if (midiOut()) {
            val msb = (inst.patch.bank shr 7) and 0x7f
            val lsb = inst.patch.bank and 0x7f
            for (i in 0..15) {
                midiOut?.send(ShortMessage(CONTROL_CHANGE or i, 0, msb), -1)
                midiOut?.send(ShortMessage(CONTROL_CHANGE or i, 32, lsb), -1)
                midiOut?.send(ShortMessage(PROGRAM_CHANGE or i, patch.program, 0), -1)
            }
        }
    }

    fun prevInst() {
        val i = instruments.indexOf(instrument)
        var n = i - 1
        if (n < 0)
            n = instruments.lastIndex
        send(InstrumentChangeMessage(instruments[n]), meToo = true)
    }

    private fun playing(p: Pitch) = (soloed.contains(p.voicePart) ||
            (soloed.size == 0 && !muted.contains(p.voicePart)))

    fun nextInst() {
        val i = instruments.indexOf(instrument)
        var n = i + 1
        if (n > instruments.lastIndex)
            n = 0
        send(InstrumentChangeMessage(instruments[n]), meToo = true)
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }

    fun midiInfo(): String {
        return if (midiOut())
            "${midiOut?.midiDevice?.infoString()}"
        else
            "None"
    }

    fun click() {
        // emit a click using the current settings
//        midiOut.send(MidiMessage(PROGRAM_CHANGE))
        midiOut?.send(ShortMessage(NOTE_ON, 9, Global.clickNote, Global.clickVolume), -1L)
        midiOut?.send(ShortMessage(NOTE_OFF, 9, Global.clickNote, 0), -1L)
    }
}

class MyInstrument(patch: Patch, name: String) : Instrument(null, patch, name, null) {
    override fun getData(): Any {
        throw UnexpectedException("Cannot call getData() on MyInstrument")
    }

    override fun toString(): String {
        return "${patch.program + 1} $name"
    }
}

fun Instrument.desc() = "(${patch.bank}, ${patch.program + 1}) $name"

class MidiEventListener(private val seq: MidiSequencer) : MetaEventListener, MessageCreator {

    private var m = 0
    private var t = -1L
    private var lastTick = -1L

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }

    override fun meta(meta: MetaMessage?) {
        if (meta != null) {
            val b0 = meta.data[0]
            val d = meta.data
//            val now = seq.tickPosition
//            System.out.print("$now: meta($b0) len = ${meta.length}")
//            for (i in 0..(meta.length - 4))
//                System.out.print(" ${d[i]}")
//            System.out.println()
            if ((b0.toInt() and 2) != 0) {
                // new measure
                t = seq.tickPosition
                m = (d[1].toInt() shl 21) or (d[2].toInt() shl 14) or
                    (d[3].toInt() shl 7) or d[4].toInt()
            }
            if (lastTick == seq.tickPosition) // suppress multiple sending of TickChangeMessages
                return
            lastTick = seq.tickPosition
            val s = seq.tickPosition - t
            send(TickChangeMessage("$m:$s"))
        }
    }
}

fun MidiDevice.infoString() = "${deviceInfo.description} | ${deviceInfo.name} | ${deviceInfo.vendor}"