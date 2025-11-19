import Global.swing8
import Global.swingDot8

// Array of pitches (i.e., a chord), or a rest, at a particular (absolute) tick, with duration
// (or a fermata)

data class Item(var tick: Long = 0) {
    var measure: MeasureData? = null
    val mTick: Int  // tick relative to the measure
        get () { return (tick - measure!!.start).toInt() }
    val beat: Int
        get () { return figureBeat() }
    val subTick: Int
        get () { return figureSubTick() }
    var labels = ArrayList<String>()
    var factors = ArrayList<Int>()
    val pitches = ArrayList<Pitch>()
    val chord: ChordInstance
        get () {
            return when {
                chordList.isEmpty() -> ChordInstance(C4.note, NoChord, 0, null)
                chosenChord >= 0 -> chordList[chosenChord]
                else -> chordList[0]
            }
        }
    var altTuning: Boolean = false
    var chordList = ArrayList<ChordInstance>()
    var chosenChord = -1
    var fermata = false
    var length = 0
    var lyrics = HashMap<VoicePart, Lyric>()

    fun reset() {
        labels.clear()
        factors.clear()
        pitches.clear()
//        altTuning = false
    }

    fun add(p: Pitch) {
        // maintain pitches sorted by MIDI number
        val n = p.n
        for (i in pitches.indices) {
            if (n < pitches[i].n) {
                pitches.add(i, p)
                return
            }
        }
        pitches.add(p)
    }

    fun lastTick(): Long {
        return tick + length
    }

    private fun figureFreq(rootPitch: Pitch, a: Int, b: Int) : Double {
        // compute freq of p, given rootPitch and ratio a:b; return freq in Hz
        val baseFreq = rootPitch.unbent()
        return baseFreq / a.toDouble() * b.toDouble()
    }

    fun mbt() : String {
        if (measure == null)
            return "0"
        return "${measure!!.number}:$beat:$subTick"
    }

    private fun figureBeat() : Int {
        if (measure == null)
            return 1
        return (((tick - measure!!.start) / measure!!.ticksPerBeat()) + 1).toInt()
    }

    private fun figureSubTick() : Int {
        if (measure == null)
            return 0
        return ((tick - measure!!.start) - (beat-1) * measure!!.ticksPerBeat()).toInt()
    }

    fun setRatios(text: String?) {
        if (text == null)
            return
        val arr = text.trim().replace("[ ,:]+".toRegex(), ":").split(":")
        val notes = pitches.size
        if (arr.size != notes) {
            Main.log("Number of entries must match number of notes in chord ($notes)")
        } else {
            val f = ArrayList<Int>()
            arr.forEach { n ->
                val fac = n.toIntOrNull()
                if (fac == null) {
                    Main.log("Cannot convert $n to integer")
                    return
                }
                f.add(fac)
            }
            this.factors = f
            for (i in f.indices) {
                val a = f[0]
                val b = f[i]
                val freq = figureFreq(pitches[0], a, b)
                pitches[i].setBendFromFreq(freq)
            }
        }
    }

    // account for swung 8ths or swung dotted 8th/16ths
    private fun swungTick(): Int {
        if (measure?.beatType != 4)
            return subTick
        val dpq = measure?.ticksPerBeat() ?: Global.divsPerQuarterNote()
        if (swing8 && subTick == dpq / 2 && length == dpq / 2)
            return (dpq * 2) / 3
        if (swingDot8 && subTick == (dpq * 3) / 4 && length == dpq / 4)
            return (dpq * 2) / 3
        return subTick
    }

    fun isAtTick(t: Long) : Boolean {
        if (measure?.containsTick(t) == true) {
            if ((measure?.start?.plus((beat - 1) * measure?.ticksPerBeat()!!) ?: 0) + swungTick() == t)
                return true
        }
        return false
    }
}