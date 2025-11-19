var lastFifths = 0

data class MeasureData(val number: Int) {
    var fifths: Int = 0 // key; 0 = C, -1 = F, +1 = G, etc.
        set(value) {
            field = value
            lastFifths = fifths
        }
    var mode = ""
    var tempo = 0.0 // quarter-notes per minute
    var start = -1L // in ticks
    var length = -1 // in ticks
    var beatType = -1 // as notated
    var beats = -1 // as notated, may be shorter (pick-up/final)
    var actualBeats = -1.0 // set by Score.reconcile()
    var repeatType = RepeatType.None
    var endingType = EndingType.None
    val endings = ArrayList<Int>()
    val direction = ArrayList<Pair<String, String>>()

    val isMinor: Boolean
        get() { return mode == "minor" }

    val notes = ArrayList<NoteData>()

    init {
        fifths = lastFifths
    }

    fun ticksPerBeat() : Int {
        return ((length).toDouble() / actualBeats).toInt()
    }

    fun getStartTime(nd: NoteData, swing8: Boolean = false, swingDot8: Boolean = false): Int {
        var s = nd.startTime
        val beat = ticksPerBeat()
        var t = s % beat
        s -= t
        if (swing8) {
            if (t.toDouble() / beat.toDouble() == 0.5) {
                t = 2 * beat / 3
            }
        }
        if (swingDot8) {
            if (t.toDouble() / beat.toDouble() == 0.75) {
                t = 2 * beat / 3
            }
        }
        return s + t
    }

    // return list of ticks at which the chord changes
    fun getTimes(): IntArray {
        val set = HashSet<Int>()
        notes.forEach { nd ->
            val startTime = getStartTime(nd)
            set.add(startTime)
        }
        var i = 0
        val arr = IntArray(set.size)
        for (tick in set) {
            arr[i++] = tick
        }
        arr.sort()
        return arr
    }

    fun containsTick(t: Long): Boolean {
        return start <= t && start + length > t
    }
}

enum class RepeatType {
    None, Forward, Backward
}

enum class EndingType {
    None, Start, Stop, StartStop
}