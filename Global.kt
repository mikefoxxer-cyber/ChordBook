import Global.fileLoaded
import Global.score
import Global.timeNow
import MessageType.*

import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Menu
import tornadofx.runLater
import java.io.File
import javax.sound.midi.Instrument

// Global state
//
// Central data structure is a sequence of Items
// If no MusicXML file, the sequence contains only one Item: the current notes
// entered by the user

private val sequence = ArrayList<Item>()
val singleton = Item()

fun items() : Iterable<Item> {
    return if (sequence.isEmpty()) {
        arrayOf(singleton).asIterable()
    } else {
        sequence.asIterable()
    }
}

val curIndex: Int
    get () {
        val item = curItem
        return if (item === singleton) {
            0
        } else {
            val it = sequence.withIndex(). find { it.value === item }
            it?.index ?: 0
        }
    }

// either the singleton or the item that spans the current time
val curItem: Item
    get() {
        if (sequence.isEmpty())
            return singleton
        val m = timeNow.first
        val measure = score.measureNumber(m)
        if (measure != null) {
            val s = timeNow.second
            val item = items().find { it.measure == measure && it.mTick <= s && it.mTick + it.length > s }
            if (item != null)
                return item
        }
        return singleton
    }

fun isJust(): Boolean = Global.isJust.value

private val listeners = HashMap<MessageType, ArrayList<MessageListener>>()

internal fun subscribeOne(listener: MessageListener, t: MessageType) {
    val arr = listeners.getOrDefault(t, ArrayList())
    arr.add(listener)
    listeners[t] = arr
}

fun subscribe(listener: MessageListener, vararg args: MessageType) {
    for (t in args) {
        subscribeOne(listener, t)
    }
}

class MessageHandler : MessageCreator, MessageListener {
    init {
        for (e in values()) {
            subscribe(this, e)
        }
    }
    override fun receive(o: MessageCreator, msg: Message) {
//        Main.log("${o.javaClass} -> $e")
        when(msg.t) {
            OptionsChange, PlayRequest, StopRequest, ChordIndexChange, ChordListChange -> {
                // ignore
            }
            PaintRequest, AnalysisRequest, ItemAnalysisRequest, TransposeChange, ChordInstanceChange -> {
                // ignore
            }
            ArpeggiateRequest, IntervalsChange, RatiosChange -> {
                // ignore
            }
            MuteRequest, SoloRequest, LegatoChange -> {
                // ignore
            }
            ModeChange, TuneToRequest, AltTuningRequest -> {
                send(ArpeggiateRequestMessage())
                send(PaintRequestMessage())
            }
            FileChange -> {
                sequence.clear()
                if (score.isEmpty()) {
                    Main.log("Score is empty")
                    fileLoaded.set(false)
                } else {
                    fileLoaded.set(true)
                    sequence.addAll(score.items())
                    timeNow = Pair(score.measures.first().number, 0)
                    runLater {
                        send(PaintRequestMessage())
                        send(ArpeggiateRequestMessage())
                    }
                }
            }
            TickChange -> {
                val tc = msg as TickChangeMessage
                if (tc.text.trim() == "") {
                    Main.log("Empty input for M:B:T, ignored")
                    return
                }
                val arr = tc.text.trim().split(":")
                when (arr.size) {
                    1 -> {
                        val t = arr[0].trim().toLong()
                        timeNow = when {
                            t < 0L -> {
                                Pair(0, 0)
                            }
                            else -> {
                                val m = score.measureContainingTick(t) ?: throw ErrorMessage("Tick not found: $t")
                                val s = t - m.start
                                Pair(m.number, s.toInt())
                            }
                        }
                    }
                    2 -> {
                        val m = arr[0].trim().toInt()
                        val s = arr[1].trim().toInt()
                        if (score.measureNumber(m) == null)
                            throw ErrorMessage("Measure $m not found")
                        timeNow = Pair(m, s)
                    }
                    3 -> {
                        val m = arr[0].trim().toInt()
                        val b = arr[1].trim().toInt()
                        val t = arr[2].trim().toInt()
                        val measure = score.measureNumber(m)
                        if (measure != null) {
                            val s = (measure.ticksPerBeat() * (b - 1)) + t
                            timeNow = Pair(measure.number, s)
                        } else {
                            throw ErrorMessage("No such time: ${tc.text}")
                        }
                    }
                    else -> {
                        Main.log("Bad syntax; use M:B:T (measure, beat, tick) or M:S (measure, offset in measure)\n" +
                                "or just T (offset in score) (all integers)")
                        return
                    }
                }
                send(PaintRequestMessage())
            }
            FwdRequest -> {
                when {
                    sequence.isEmpty() -> {
                        throw ErrorMessage("No MusicXML file loaded")
                    }
                    curIndex >= sequence.lastIndex -> {
                        throw ErrorMessage("Cannot advance")
                    }
                    else -> {
                        val i = curIndex
                        val item = sequence[i + 1]
                        timeNow = Pair(item.measure!!.number, item.mTick)
                        runLater {
                            send(PaintRequestMessage())
                            send(ArpeggiateRequestMessage())
                        }
                    }
                }
            }
            BackRequest -> {
                when {
                    sequence.isEmpty() -> {
                        throw ErrorMessage("No MusicXML file loaded")
                    }
                    curIndex == 0 -> {
                        throw ErrorMessage("Cannot back up")
                    }
                    else -> {
                        val i = curIndex
                        val item = sequence[i - 1]
                        timeNow = Pair(item.measure!!.number, item.mTick)
                        runLater {
                            send(PaintRequestMessage())
                            send(ArpeggiateRequestMessage())
                        }
                    }
                }
            }
            TempoChange -> {
                val tc = msg as TempoChangeMessage
                Global.p_tempo = tc.newVal
            }
            InstrumentChange -> {
                val ic = msg as InstrumentChangeMessage
                Global.p_instrument = ic.newVal
            }
            RootChange -> {
                val rc = msg as RootChangeMessage
                if (rc.newVal == null)
                    curItem.chosenChord = -1
                else
                    curItem.chord.root = rc.newVal
                send(ChordInstanceChangeMessage(Global.chordInstance))
            }
            ChordTypeChange -> {
                val cc = msg as ChordTypeChangeMessage
                if (cc.newVal == null)
                    curItem.chosenChord = -1
                else
                    curItem.chord.chord = cc.newVal
                send(ChordInstanceChangeMessage(Global.chordInstance))
            }
            InversionChange -> {
                val ic = msg as InversionChangeMessage
                curItem.chord.inv = ic.newVal
                send(ChordInstanceChangeMessage(Global.chordInstance))
            }
            BassChange -> {
                val bc = msg as BassChangeMessage
                curItem.chord.bass = bc.newVal
                send(ChordInstanceChangeMessage(Global.chordInstance))
            }
            DurationChange -> {
                val dc = msg as DurationChangeMessage
                val value = dc.newVal
                if (value !in 100L..60_000L) {
                    Main.log("Duration out of range (100 to 10,000 ms), ignored")
                } else {
                    Global.p_duration = value
                }
            }
            DelayChange -> {
                val dc = msg as DelayChangeMessage
                val value = dc.newVal
                if (value !in 0L..10_000L) {
                    Main.log("Delay value out of range (0 to 10,000), ignored")
                } else {
                    Global.p_delay = value
                }
            }
            VolumeChange -> {
                val vc = msg as VolumeChangeMessage
                val value = vc.newVal
                if (value in 0..127) {
                    Global.p_volume = value
                } else {
                    Main.log("Volume value out of range (0..127), ignored")
                }
            }
            Swing8Change -> {
                val sc = msg as Swing8ChangeMessage
                Global.p_swing8 = sc.newVal
            }
            SwingDot8Change -> {
                val sc = msg as SwingDot8ChangeMessage
                Global.p_swingDot8 = sc.newVal
            }
//            else -> {
//                Main.log("UNHANDLED: $msg from ${o.javaClass}")
//            }
        }
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }
}

val sequencer = MidiSequencer()

object Global {

    var kbdScale: Double = 1.0

    var staffScale: Double = 1.0

    var splits = arrayOf (0.1, 0.666, 0.666)

    var kbdOffset = 0.0

    var staffTranslations = arrayOf(0.0, 0.0)

    var clickNote: Int = 60

    lateinit var analyzer: ChordAnalyzer

    val eh = MessageHandler()

    var timeNow: Pair<Int, Int> = Pair(0, 0)

    val score = Score()

    fun openFile(file: File) {
        score.open(file)
    }

    fun closeFile() {
        sequence.clear()
        score.close()
    }

    fun saveFile(file: File) {
        score.save(file)
    }

    fun key() : Int {
        val k = score.measureNumber(timeNow.first)
        return k?.fifths ?: 0
    }

    fun getOptionString() : String {
        val sb = StringBuilder()
        sb.append("just=${isJust()}; ")
        sb.append("device='${sequencer.midiInfo()}'; ")
        sb.append("instrument='${instrument?.patch?.bank},${(instrument?.patch?.program ?: 0) + 1}'; ")
        sb.append("duration=$duration; ")
        sb.append("delay=$delay; ")
        sb.append("volume=$volume; ")
        sb.append("clickVolume=$clickVolume; ")
        sb.append("noteValue='$noteValue'; ")
        sb.append("clickNote=$clickNote; ")
        sb.append("swing8=$swing8; ")
        sb.append("swingDot8=$swingDot8; ")
        sb.append("tempo=$tempo; ")
        sb.append("legato=${legato.value}; ")
        sb.append("mute=${VoiceParts.muted()}; ")
        sb.append("solo=${VoiceParts.soloed()}; ")
        return sb.toString()
    }

    private fun strip(s: String) = if (s[0] == '\'')
        s.substring(1, s.length - 1)
    else
        s

    fun parseOptionString(str: String) {
        val arr = str.split(";")
        for (s in arr) {
            if (s.length < 3)
                continue
            val arr2 = s.split("=")
            val n = arr2[0].trim()
            val v = arr2[1].trim()
            when (n) {
                "just" -> {
                    isJust.value = v == "true"
                }
                "device" -> {
                    sequencer.openDevice(strip(v))
                }
                "instrument" -> {
                    setInstrument(strip(v))
                }
                "duration" -> {
                    p_duration = v.toLong()
                }
                "delay" -> {
                    p_delay = v.toLong()
                }
                "volume" -> {
                    p_volume = v.toInt()
                }
                "clickVolume" -> {
                    clickVolume = v.toInt()
                }
                "noteValue" -> {
                    noteValue = strip(v).toLowerCase()
                }
                "clickNote" -> {
                    clickNote = v.toInt()
                }
                "swing8" -> {
                    p_swing8 = v == "true"
                }
                "swingDot8" -> {
                    p_swingDot8 = v == "true"
                }
                "tempo" -> {
                    p_tempo = v.toInt()
                }
                "legato" -> {
                    legato.value = v == "true"
                }
                "mute" -> {
                    VoiceParts.setMuted(v)
                }
                "solo" -> {
                    VoiceParts.setSoloed(v)
                }
            }
            send(eh, OptionsChangeMessage(), false)
        }
    }

    var isJust = SimpleBooleanProperty(false)

    var fileLoaded = SimpleBooleanProperty(false)
    val isFileLoaded: Boolean
        get () { return fileLoaded.value }

    fun createInstrumentMenu(m: Menu) {
        sequencer.createInstrumentMenu(m)
    }

    fun createDevicesMenu(m: Menu) {
        sequencer.createDeviceMenu(m)
    }

    internal var p_instrument: Instrument? = null
    val instrument: Instrument?
        get() { return p_instrument }

    fun setInstrument(s: String) {
        val inst = sequencer.parseInstrument(s)
        if (inst != null) {
            p_instrument = inst
            eh.send(InstrumentChangeMessage(inst))
        } else {
            Main.log("Instrument not found: $s")
        }
    }

    internal var p_duration = 3000L
    val duration: Long
        get() { return p_duration }

    internal var p_delay = 250L
    val delay: Long
        get () { return p_delay }

    internal var p_volume = 90
    val volume: Int
        get () { return p_volume }

    var clickVolume = 80

    var noteValue = "auto"

    val pitches: List<Pitch>
        get () { return curItem.pitches.toList() }

    val intervals: List<Interval>
        get() { return curItem.chord.chord.intervals.toList() }

    internal var p_swing8: Boolean = false
    val swing8: Boolean
        get() { return p_swing8 }

    internal var p_swingDot8: Boolean = false
    val swingDot8: Boolean
        get() { return p_swingDot8 }

    internal var p_tempo: Int = 60
    val tempo: Int
        get () { return p_tempo }

    val mbt : String
        get() {
            val m = timeNow.first
            val measure = score.measureNumber(m)
            val tpb = measure?.ticksPerBeat()
            return if (tpb != null && tpb != 0) {
                val b = timeNow.second / tpb + 1
                val t = timeNow.second % tpb
                "$m:$b:$t"
            } else {
                val t = timeNow.second
                "$m:$t"
            }
        }

    fun divsPerQuarterNote() : Int {
        if (score.isEmpty())
            return 1
        return score.divsPerQuarterNote()
    }

    var altTuning = SimpleBooleanProperty(false)
        set (value) {
            val fire = field != value
            field = value
            if (fire)
                eh.send(AltTuningRequestMessage())
        }

    var altTuningIsAvailable = SimpleBooleanProperty(false)

    var tuneTo = ArrayList<TuningSpec>()

    var legato = SimpleBooleanProperty(true)
        set(value) {
            val fire = field != value
            field = value
            if (fire)
                eh.send(LegatoChangeMessage())
        }

    val root: Note?
        get() { return chordInstance?.root }

    val chord: Chord?
        get() { return chordInstance?.chord }

    val inversion: Int
        get() { return chordInstance?.inv ?: 0 }

    val bass: Note?
        get() { return chordInstance?.bass }

    val chordInstance: ChordInstance?
        get() {
            return curItem.chord
        }

    fun chordName() : String {
        val ci = chordInstance ?: return ""
        val iStr = if (ci.inv == 0) "" else " ${inversionName(ci.inv)}"
        val bStr = if (ci.bass == null) "" else " / ${ci.bass}"
        return "${ci.root} ${ci.chord.suffix}$iStr$bStr"
    }

    fun send(o: MessageCreator, e: Message, meToo: Boolean) {
        val t = e.t
        listeners.getOrDefault(t, ArrayList()).forEach { listener ->
            val l = listener::class
            val sender = o.javaClass.toString().substring(6)
            val receiver = l.toString().substring(6)
            if (meToo || sender != receiver) {
                runLater {
//                    if (receiver != "MessageHandler")
//                        Main.log("$sender -> $e -> $receiver")
                    listener.receive(o, e)
                }
            }
        }
    }

    fun click() {
        sequencer.click()
    }
}
