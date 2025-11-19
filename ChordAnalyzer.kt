import Global.score
import MessageType.*
import tornadofx.runLater
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

class ChordAnalyzer : MessageCreator, MessageListener {
    private var text = ""

    init {
        subscribe(
            this, AnalysisRequest, ItemAnalysisRequest, ChordInstanceChange, ChordIndexChange,
            TransposeChange, TuneToRequest, AltTuningRequest
        )
    }

    private fun figureNotes(ci: ChordInstance) {
        val notes = ArrayList<Note>()
        var root = ci.root
        text = ""
        if (ci.bass != null) {
            notes.add(ci.bass!!)
            if (ci.chord.isRootless)
                root = ci.bass!!
        } else if (ci.inv != 0) {
            if (ci.inv > ci.chord.intervals.size) {
                Main.log("No such inversion")
                return
            }
            val n = ci.root.midiNumber()
            val int = ci.chord.intervals[ci.inv - 1]
            val m = n + int.semis
            val note = noteForMidiNumber(m)
            val arr = notesForPitchClass(note.pc)
            // which spelling?
            for (i in arr.indices) {
                if (intervalBetween(ci.root, arr[i]) == int) {
                    notes.add(arr[i])
                    break
                }
            }
        }
        if (!ci.chord.isRootless)
            notes.add(ci.root)
        ci.chord.intervals.sorted().forEach { int ->
            val n = noteForMidiNumber(int.semis + root.midiNumber())
            val arr = notesForPitchClass(n.pc)
            var added = false
            arr.forEach {
                if (!added && root.nameDist(it) == int.basicSize) {
                    notes.add(it)
                    added = true
                } else if (!added && int.basicSize > 7 && root.nameDist(it) == int.basicSize - 7) {
                    notes.add(it)
                    added = true
                } else if (!added && int.basicSize == 15 && it == root) {
                    notes.add(it)
                    added = true
                }
            }
            if (!added) {
                // use enharmonic spelling
                val r = ci.root
                if (arr.size == 2) {
                    if (r.mod <= 0) // prefer flat
                        notes.add(arr[1])
                    else
                        notes.add(arr[0])
                } else { // 3 notes to choose from
                    if (r.mod <= 0) { // prefer flat or double flat
                        for (i in arr.indices) {
                            if (arr[i].mod < 0) {
                                notes.add(arr[i])
                                break
                            }
                        }
                    } else {
                        for (i in arr.indices.reversed()) {
                            if (arr[i].mod >= 0) {
                                notes.add(arr[i])
                                break
                            }
                        }
                    }
                }
            }
        }
        if (ci.chord.isRootless)
            root = ci.root
        if (ci.chord.isInterval) {
            val n = ci.chord.intervals[0]
            text = if (n.basicSize > 8 || n.semis > 12)
                "${notes[0]} _ ${notes[1]}"
            else
                "${notes[0]} ${notes[1]}"
        } else {
            for (note in notes) {
                text += "$note "
            }
        }
        Main.getNoteList().text = text
    }

//    private fun getPitches() : ArrayList<Pitch> {
//        if (dirty) {
//            pList.clear()
//            for (i in pressed.indices) {
//                if (pressed[i]) {
//                    val p = Pitch(i)
//                    if (preferred[i] != null) {
//                        p.note = preferred[i]!!
//                    }
//                    pList.add(p)
//                }
//            }
//            dirty = false
//        }
//        return pList
//    }

//    private fun updatePitches(newPitches: List<*>, delay: Long = -1) {
//        val set = HashSet<Int>()
//        newPitches.forEach { p ->
//            val pitch = p as Pitch
//            set.add(pitch.n)
//        }
//        for (i in pressed.indices) {
//            pressed[i] = false
//            label[i] = ""
//            if (!set.contains(i))
//                preferred[i] = null
//        }
//        newPitches.forEach { p ->
//            val pitch = p as Pitch
//            pressed[pitch.n] = true
//            preferred[pitch.n] = pitch.note
//        }
//        updateChord()
//        figureLabels()
//        fire(PlayRequestEvent(oneShot = true, delay = delay))
//    }

    private fun figureLabels(item: Item) {
        if (item.chord.chord === OneNote) {
            return
        }
        val ci = item.chord
        var rootPitch: Pitch? = null
        if (ci.chord.intervals.size > 0) {
            rootPitch = getRootPitch(ci, item.pitches)
            if (rootPitch == null) {
                send(IntervalsChangeMessage())
            } else {
                val label = Array(128) { "" }
                val intervals = if (ci.chord.isRootless) ci.chord.rootless!!.intervals else ci.chord.intervals
                item.pitches.forEach {
                    val semis = it.n - rootPitch.n
                    if (semis % 12 == 0) {
                        label[it.n] = "R"
                    } else {
                        label[it.n] = "?"
                        for (int in intervals) {
                            if (int.semis % 12 == semis % 12) {
                                label[it.n] = int.name
                                break
                            }
                        }
                        if (label[it.n] == "?") {
                            val where = if (curItem === singleton) "" else " at ${item.mbt()}"
                            Main.log("Can't figure interval label for $it$where")
                            label[it.n] = ""
                        }
                    }
                }
                item.labels.clear()
                for (p in item.pitches) {
                    item.labels.add(label[p.n])
                }
                send(IntervalsChangeMessage())
            }
        }
        if (rootPitch != null) {
            val factor = Array(128) { 0 }
            val list = ArrayList<Int>() // note num
            val f = ArrayList<Int>() // factor
            item.pitches.forEach {
                list.add(it.n)
                f.add(getRatio(ci, rootPitch, it, item.altTuning))
            }
            val rs = RatioSet(f.toTypedArray()) // to reduce ratios to lowest terms
            for (i in list.indices) {
                factor[list[i]] = rs.values[i]
            }
            for (i in list.indices) {
                val a = factor[list[0]]
                val b = factor[list[i]]
                val freq = figureFreq(item.pitches[0], a, b)
                item.pitches[i].setBendFromFreq(freq)
                send(RatiosChangeMessage())
            }
            item.factors.clear()
            for (p in item.pitches) {
                item.factors.add(factor[p.n])
            }
        }
    }

    private fun getRootPitch(ci: ChordInstance, pList: ArrayList<Pitch>): Pitch? {
        var p: Pitch? = null
        if (ci.chord.isInterval || ci.chord === OneNote) {
            return pList[0]
        }
        if (ci.chord.isRootless) {
            p = Pitch(ci.root, pList[0].getOctave())
            while (p!!.n - pList[0].n > 0) {
                p = p.plusOctave(-1)
            }
        }
        if (p == null) {
            pList.forEach {
                if (it.note.minus(ci.root) == 0) {
                    p = it
                }
            }
        }
        if (p != null) {
            if (p!!.n > pList[0].n) {
                p = Pitch(p!!.note, pList[0].getOctave())
                if (p!!.n > pList[0].n) {
                    p = p!!.plusOctave(-1)
                }
            }
        }
        return p
    }

    private fun figureFreq(rootPitch: Pitch, a: Int, b: Int): Double {
        // compute freq of p, given rootPitch and ratio a:b; return freq in Hz
        val baseFreq = rootPitch.unbent()
        return baseFreq / a.toDouble() * b.toDouble()
    }

    fun analyzeItem(item: Item, fire: Boolean = true) {
        val cList = chordLookup(item.pitches, useEnharmonic = true)
        item.chordList.clear()
        item.chordList.addAll(cList.sorted())
        if (fire) {
            runLater {
                send(ChordListChangeMessage())
            }
        }
        analyzePitches(item)
    }

    private fun analyzePitches(item: Item) {
        val ci = item.chord
        val rootPitch = getRootPitch(ci, item.pitches)
        if (rootPitch != null) {
            val labels = ArrayList<String>()
            if (ci.chord !== OneNote && ci.chord !== NoChord) {
                val intervals = if (ci.chord.isRootless) ci.chord.rootless!!.intervals else ci.chord.intervals
                item.pitches.withIndex().forEach {
                    val semis = it.value.n - rootPitch.n
                    if (semis % 12 == 0) {
                        labels.add("R")
                    } else {
                        labels.add("?")
                        for (int in intervals) {
                            if (int.semis % 12 == semis % 12) {
                                labels[it.index] = int.name
                                break
                            }
                        }
                        if (labels[it.index] == "?") {
                            val where = if (curItem === singleton) "" else " at ${item.mbt()}"
                            Main.log("Can't figure interval label for ${it.value}$where")
                            labels[it.index] = ""
                        }
                    }
                }
            }
            item.labels.clear()
            item.labels.addAll(labels)
        }
        if (ci.chord !== NoChord && rootPitch != null && item.pitches.size > 1) {
            val factor = Array(128) { 0 }
            val list = ArrayList<Int>() // note num
            val f = ArrayList<Int>() // factor
            item.pitches.forEach {
                list.add(it.n)
                try {
                    f.add(getRatio(ci, rootPitch, it, item.altTuning))
                } catch (e: KotlinNullPointerException) {
                    throw ErrorMessage(e.localizedMessage)
                }
            }
            val rs = RatioSet(f.toTypedArray())
            for (i in list.indices) {
                factor[list[i]] = rs.values[i]
            }
            if (item.chord.chord.isRootless) {
                // use lowest note
                for (i in list.indices) {
                    val a = factor[list[0]]
                    val b = factor[list[i]]
                    val freq = figureFreq(item.pitches[0], a, b)
                    item.pitches[i].setBendFromFreq(freq)
                }
                item.factors.clear()
                for (p in item.pitches) {
                    val fac = if (factor[p.n] == 0) 1 else factor[p.n]
                    item.factors.add(fac)
                }
            } else {
                val rootInList = item.pitches.find { it.note == rootPitch.note }
                val rootIndex = item.pitches.indexOf(rootInList)
                if (rootIndex !in item.pitches.indices) {
                    Main.log("Cannot locate root pitch ${rootPitch.note} at ${item.mbt()}")
                } else {
                    for (i in list.indices) {
                        val a = factor[list[rootIndex]]
                        val b = factor[list[i]]
                        val freq = figureFreq(item.pitches[rootIndex], a, b)
                        item.pitches[i].setBendFromFreq(freq)
                    }
                    item.factors.clear()
                    for (p in item.pitches) {
                        val fac = if (factor[p.n] == 0) 1 else factor[p.n]
                        item.factors.add(fac)
                    }
                }
            }
        } // rootPitch != null
        applyTuneTo(item)
    }

    private fun applyTuneTo(item: Item) {
        if (Global.tuneTo.isNotEmpty()) {
            var pb = 0
            var note: Note? = null
            val ts = getTuningSpec(item.tick)
            if (ts != null) {
                var delta = 0
                val vp = ts.vp
                if (vp != null) {
                    // choose the note being sung by the give VoicePart
                    if (vp.voice == 0) {
                        // not a true VP; choose the note by number
                        if (vp.voice in item.pitches.indices) {
                            val p = item.pitches[vp.part]
                            note = p.note
                            pb = p.pitchBend()
//                            Main.log("Tuning to ${p.note}")
                        } else {
                            Main.log("Can't find $vp note in chord at ${item.mbt()}")
                        }
                    } else {
                        val p = item.pitches.find { it.voicePart == vp }
                        if (p == null) {
                            Main.log("Can't find $vp note in chord at ${item.mbt()}")
                        } else {
                            note = p.note
                            pb = p.pitchBend()
//                            Main.log("Tuning to $vp (${p.note})")
                        }
                    }
                    if (note != null) {
                        delta = getDelta(item.tick, note, pb)
                    } else {
                        // note to tune to was not found; tune to the key
                        // first, pick a note; if lead is singing, use that
                        var pitch: Pitch?
                        pitch = item.pitches.find {
                            it.voicePart != null &&
                                    it.voicePart!!.part == 0
                                    && it.voicePart!!.voice == 2
                        }
                        if (pitch == null) {
                            // next, if no lead, but bass is singing, use that
                            pitch = item.pitches.find {
                                it.voicePart != null
                                        && it.voicePart!!.part == 1
                                        && it.voicePart!!.voice == 2
                            }
                        }
                        if (pitch == null) {
                            // no lead or bass; just pick the lowest note
                            if (item.pitches.isNotEmpty())
                                pitch = item.pitches[0]
                            else
                                return
                        }
                        delta = getDelta(item.tick, pitch.note, pitch.pitchBend())
                    }
                } else {
                    // not a VoicePart; must be a Note or just cents
                    note = ts.note
                    if (note == null) {
                        // just a cents value
                        // tune the root of the chord relative to E.T.
                        val root = item.chord.root
                        val p = item.pitches.find {
                            it.note == root
                        }
                        if (p != null) {
                            delta = ts.pitchBend()
                            delta += p.pitchBend()
                        }
                    } else {
                        // find the note in the chord
                        val p = item.pitches.find {
                            it.note == note
                        }
                        if (p != null) {
                            delta = getDelta(item.tick, p.note, p.pitchBend())
                            delta += ts.pitchBend()
                        } else {
                            Main.log("Unable to locate $note in chord at ${item.mbt()}")
                        }
                    }
                }
                for (p in item.pitches) {
                    p.setPbus(p.pitchBend() + delta)
                }
            }
        }
    }

    private fun getKey(fifths: Int, isMinor: Boolean) : Note {
        val major = arrayOf("Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C",
                                            "G", "D", "A", "E", "B", "F#", "C#")
        val minor = arrayOf("Ab", "Eb", "Bb", "F", "C", "G", "D", "A",
                                            "E", "B", "F#", "C#", "G#", "D#", "A#")
        val s = if (isMinor) minor[fifths + 7] else major[fifths + 7]
        return noteMap[s]!!
    }

    private fun getDelta(tick: Long, note: Note, pb: Int) : Int {
        val delta: Int
        var fifths = 0
        var isMinor = false
        val m = score.measureContainingTick(tick)
        if (m != null) {
            fifths = m.fifths
            isMinor = m.isMinor
        }
        val keyNote = getKey(fifths, isMinor)
        val i = intervalBetween(keyNote, note)
        val f = i.getJIFraction()
        val ratio = f.first.toDouble() / f.second.toDouble()
        val etRatio = 2.0.pow(i.semis/12.0)
        val pRatio = ratio / etRatio
        delta = round(4096.0 * 12 * ln(pRatio) / ln(2.0)).toInt()
        return delta - pb
    }

    private fun getTuningSpec(tick: Long) : TuningSpec? {
        if (Global.tuneTo.isEmpty())
            return null
        return Global.tuneTo.findLast { ts ->
            ts.tick <= tick
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            AnalysisRequest -> {
                val ar = msg as AnalysisRequestMessage
                if (ar.pitches == null) {
                         for (item in score.items()) {
                            analyzeItem(item, fire = false)
                    }
                } else {
                    val chosen = curItem.chosenChord
                    val pitches = ArrayList<Pitch>(curItem.pitches)
                    curItem.reset()
                    curItem.pitches.addAll(ar.pitches)
                    analyzeItem(curItem)
                    if (chosen >= 0) {
                        var diff = false
                        if (pitches.size != curItem.pitches.size) {
                            diff = true
                        } else {
                            for (i in pitches.indices) {
                                if (pitches[i] != curItem.pitches[i]) {
                                    diff = true
                                    break
                                }
                            }
                        }
                        if (!diff)
                            curItem.chosenChord = chosen
                    }
                    if (curItem.length == 0)
                        curItem.length = 2
                    runLater {
                        send(PaintRequestMessage())
                        send(ArpeggiateRequestMessage())
                    }
                }
            }
            ItemAnalysisRequest -> {
                val ia = msg as ItemAnalysisRequestMessage
                val item = ia.item
                analyzeItem(item, fire = false)
            }
            ChordInstanceChange -> {
                val cc = msg as ChordInstanceChangeMessage
                if (cc.newVal != null) {
                    figureNotes(cc.newVal)
                    parseNotes(text)
                }
            }
            ChordIndexChange -> {
                // don't change pitches, just re-interpret
                val cic = msg as ChordIndexChangeMessage
                val saved = ArrayList<Pitch>(cic.item.pitches)
                cic.item.reset()
                cic.item.pitches.addAll(saved)
                analyzePitches(cic.item)
                figureLabels(cic.item)
                applyTuneTo(cic.item)
                if (cic.item == curItem) {
                    runLater {
                        send(PaintRequestMessage())
                        send(ArpeggiateRequestMessage())
                    }
                }
            }
            TuneToRequest -> {
                for (item in score.items()) {
                    applyTuneTo(item)
                }
                send(PaintRequestMessage())
            }
            AltTuningRequest -> {
                analyzePitches(curItem)
                applyTuneTo(curItem)
                send(PaintRequestMessage())
            }
            TransposeChange -> {
                val tc = msg as TransposeChangeMessage
                val newPitches = ArrayList<Pitch>()
                Global.pitches.forEach { p ->
                    val newP = if (tc.up) p.plus(tc.amount) else p.minus(tc.amount)
                    if (tc.amount == P1) { // Enharmonically
                        val arr = newP.note.aliases()
                        val index = arr.indexOf(newP.note)
                        if (tc.up) {
                            if (index < arr.lastIndex) {
                                newP.note = arr[index + 1]
                            }
                        } else {
                            if (index > 0) {
                                newP.note = arr[index - 1]
                            }
                        }
                    }
                    newPitches.add(newP)
                }
                curItem.reset()
                curItem.pitches.addAll(newPitches)
                analyzeItem(curItem)
                if (curItem.length == 0)
                    curItem.length = 2
                runLater {
                    send(PaintRequestMessage())
                    send(ArpeggiateRequestMessage())
                }
            }
            else -> {
                // ignore
            }
        }
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }
}