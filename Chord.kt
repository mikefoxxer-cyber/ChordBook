import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max

val canBeRootless = arrayOf("9", "maj9", "m9", "m-maj9")

val OneNote = Chord(" ", arrayListOf(P1))

val NoChord = Chord("<none>", ArrayList())

data class Chord(val suffix: String) : Comparable<Chord> {

    var isInterval = false
    var isRootless = false
    val canBeRootless : Boolean
        get () { return !isRootless && rootless != null && rootless!!.isRootless }

    // Ab 9 can be rootless; it's the same as Cm7b5. In chord "9",
    // the following property will point to "9 (no root)", with intervals from
    // m7b5. Also, in "9 (no root)", the following property will point to "9".
    var rootless : Chord? = null

    // var complexity = 0
    var intervals = ArrayList<Interval>()
    private val deltas = ArrayList<Int>()
    var ratios = ArrayList<RatioSet>()
    val aliases = HashSet<String>()

    override fun toString(): String {
        return suffix
    }

    fun computeDeltas() {
        for (int in reduceIntervals(intervals)) {
            deltas.add(int.semis)
        }
        deltas.sort()
    }

    constructor(s: String, int: ArrayList<Interval>) : this(s) {
        this.intervals = int
    }

    fun addTuning(a: Array<Int>) {
        ratios.add(RatioSet(a))
    }

    override fun compareTo(other: Chord) =
        when {
            this.isInterval -> {
                if (other.isInterval) {
                    val o = other.intervals[0]
                    if (o.basicSize == intervals[0].basicSize) {
                        o.semis - intervals[0].semis
                    } else {
                        o.basicSize - intervals[0].basicSize
                    }
                } else {
                    1 // Intervals all come before non-Intervals
                }
            }
            other.isInterval -> {
                -1 // Intervals all come before non-Intervals
            }
            else -> {
                // rough ordering: number of notes in ratio set
                other.ratios[0].values.size - this.ratios[0].values.size
            }
        }

    fun third() : Interval {
        for (int in intervals) {
            if (int.basicSize == 3)
                return int
        }
        throw IllegalArgumentException("Chord has no third!")
    }
}

fun readChords(value: String) {
    val todo = ArrayList<Chord>()
    val st = StringTokenizer(value, ";")
    while (st.hasMoreTokens()) {
        val names = st.nextToken()
        val intervals = st.nextToken()
        val ratios = st.nextToken()
        val nameTokens = StringTokenizer(names, "|")
        var c: Chord? = null
        while (nameTokens.hasMoreTokens()) {
            var name = nameTokens.nextToken()
            name = name.replace("(", " (")
            name = name.replace("no", "no ")
            name = name.replace("  ", " ")
            if (c == null) {
                if (name == "maj") {
                    c = create("", intervals, ratios)
                    c.aliases.add("maj")
                } else {
                    c = create(name, intervals, ratios)
                    if (canBeRootless.contains(name)) {
                        todo.add(c)
                    }
                }
            } else {
                c.aliases.add(name)
            }
        }
    }
    power = suffixMap["5"][0]
    major = suffixMap[""][0]

    // add rootless variants
    todo.forEach { c ->
        val name = c.suffix
        val c2 = Chord("$name (no root)")
        c2.isRootless = true
        val rootless = suffixMap[rootlessSuffix(name)][0]
        c2.intervals.clear()
        c2.intervals.addAll(rootless.intervals)
        c2.ratios = rootless.ratios
        c.rootless = c2
        c2.rootless = c
        addToMaps(c2)
    }

    // add "no 5" chords
    val keys = HashSet<String>()
    keys.addAll(suffixMap.keys())
    for (suffix in keys) {
        for (c in suffixMap[suffix]) {
            if (c.intervals.contains(P5)) {
                if (c.isInterval || c == power) {
                    continue
                }
                val newInt = ArrayList<Interval>()
                newInt.addAll(c.intervals)
                newInt.remove(P5)
                val c2 = Chord("$suffix (no 5)")
                if (suffixMap.containsKey(c2.suffix))
                    continue // "m7 (no 5)" would be dup otherwise
                c2.intervals = newInt
                val a = figureRatios(newInt).toTypedArray()
                c2.addTuning(a)
                addToMaps(c2)
            }
        }
    }
}

fun create(suffix: String, list: ArrayList<Interval>, values: Array<Int>): Chord {
    val c = Chord(suffix)
    c.intervals = list
    c.ratios.clear()
    c.ratios.add(RatioSet(values))
    addToMaps(c)
    return c
}

fun create(suffix: String, intervals: String, ratios: String): Chord {
    val ia = ArrayList<Interval>()
    val arr = ArrayList<Int>()
    var st = StringTokenizer(intervals, " ")
    while (st.hasMoreTokens()) {
        ia.add(lookupInterval(st.nextToken()))
    }
    val r = ratios.split("|")
    st = StringTokenizer(r[0], " ")
    while (st.hasMoreTokens()) {
        val s = st.nextToken()
        if (s == "*") {
            arr.clear()
            arr.addAll(figureRatios(ia))
        } else {
            arr.add(s.toInt())
        }
    }
    val a = Array(arr.size) { i -> arr[i] }

    val chord = create(suffix, ia, a)

    if (r.size > 1) {
        arr.clear()
        for (j in 1..r.lastIndex) {
            st = StringTokenizer(r[j], " ")
            while (st.hasMoreTokens()) {
                val s = st.nextToken()
                if (s == "*")
                    continue // no ratios
                arr.add(s.toInt())
            }
            for (i in a.indices) {
                a[i] = arr[i]
            }
            chord.addTuning(a)
        }
    }
    return chord
}

val suffixMap = MultiMap()

//val chordNoteMap = MultiMap()

val deltaMap = MultiMap()

var power: Chord = Chord("5")

var major: Chord = Chord("")

fun figureRatios(intervals: ArrayList<Interval>): ArrayList<Int> {
    // find LCM of denominators
    var lcm = 1
    val num = Array(intervals.size) { 0 }
    val den = Array(intervals.size) { 0 }
    for (i in intervals.indices) {
        val n = intervals[i]
        val d = n.getJIFraction().second
        num[i] = n.getJIFraction().first
        den[i] = d
        lcm = lcm(lcm, d)
    }
    for (i in num.indices) {
        num[i] *= lcm / den[i]
    }
    val r = ArrayList<Int>()
    r.add(lcm)
    for (i in num.indices) {
        r.add(num[i])
    }
    return r
}

fun gcd(aa: Int, bb: Int): Int {
    var a = aa
    var b = bb
    while (b > 0) {
        val temp = b
        b = a % b
        a = temp
    }
    return a
}

fun lcm(a: Int, b: Int): Int = a * (b / gcd(a, b))

fun lcm(arr: IntArray) : Int {
    var result = arr[0]
    for (i in 1..arr.lastIndex) {
        result = lcm(result, arr[i])
    }
    return result
}

fun addToDeltaMap(c: Chord) {
    val s = getDeltas(reduceIntervals(c.intervals)).toString()
    deltaMap.add(s, c)
}

var maxInterval = 0

fun addToMaps(c: Chord) {
    val suffix = c.suffix
    c.computeDeltas()
    addToDeltaMap(c)
    if (suffixMap.containsKey(suffix)) {
        System.err.println("DUP SUFFIX: $suffix")
        return
    }
    suffixMap.add(suffix, c)
    maxInterval = max(maxInterval, c.intervals.size)
}

fun allChords() : ArrayList<Chord> {
    val arr = ArrayList<Chord>()
    suffixMap.values().forEach { c ->
        arr.add(c)
    }
    return arr
}

fun chordLookup(suffix: String): Chord? {
    val a = suffixMap[suffix]
    return if (a.isEmpty())
        null
    else
        a[0]
}

fun create(n: Interval): Chord {
    val c = Chord(intervalNameOf(n), arrayListOf(n))
    c.isInterval = true
    c.computeDeltas()
    try {
        val f = n.getJIFraction()
        c.ratios.add(RatioSet(arrayOf(f.second, f.first)))
    } catch (e: IllegalArgumentException) {
        System.err.println(e.localizedMessage + " for interval $c")
    }
    suffixMap.add(intervalNameOf(n), c)
    return c
}

fun chordLookup(intervals: List<Interval>): ArrayList<Chord> {
//    val best = chordNoteMap[intervals.sorted().toString()]
    val best = deltaMap[getDeltas(reduceIntervals(intervals)).toString()]
    if (best.size > 0 && best[0].isInterval) {
        return best
    }
//    if (intervals.size == 1 && intervals[0] != Interval.P5) {
//        val n = intervals[0]
//        if (!suffixMap.containsKey(n.name)) {
//            create(n)
//        }
//        return arrayListOf(chordLookup(n.name)!!)
//    }
    val reduced = reduceIntervals(intervals)
    val result = HashSet<Chord>()
    result.addAll(best)
    if (reduced != intervals) {
        result.addAll(chordLookup(reduceIntervals(intervals)))
    }
    val list = ChordList()
    list.addAll(result.toList())
    return list.getArrayList()
}

fun chordLookup(pitches: ArrayList<Pitch>): ArrayList<ChordInstance> {
    val intervals = analyzeIntervals(pitches)
    if (intervals.size > 1 && intervals.contains(P8)) {
        intervals.remove(P8)
    }
    val arr = ArrayList<Chord>()
    arr.addAll(chordLookup(intervals))
    val ci = ArrayList<ChordInstance>()
    arr.forEach { c ->
        ci.add(ChordInstance(pitches[0].note, c, 0))
    }
    return ci
}

fun chordLookup(
    pitches: ArrayList<Pitch>,
    useEnharmonic: Boolean,
    recursive: Boolean = true
): ArrayList<ChordInstance> {
    if (pitches.isEmpty())
        return ArrayList()
    if (pitches.size == 1) {
        return arrayListOf(ChordInstance(pitches[0].note, OneNote, 0, null))
    }
    if (useEnharmonic) {
        val intervals = analyzeIntervals(pitches)
        if (intervals.size == 1) {
            val n = intervals[0]
            if (!suffixMap.containsKey(intervalNameOf(n))) {
                create(n)
            }
            val c = suffixMap[intervalNameOf(n)][0]
            return arrayListOf(ChordInstance(pitches[0].note, c, 0))
        } else {
            val set = HashSet<Chord>()
//            var list = chordLookup(intervals)
//            set.addAll(list)
//            intervals = reduceIntervals(intervals)
//            list = chordLookup(intervals)
//            set.addAll(list)
            while (intervals.size > 1 && intervals.remove(P8))
                Unit
            while (intervals.size > 1 && intervals.remove(P1))
                Unit
            while (intervals.size > 1 && intervals.remove(P15))
                Unit
            val deltas = getDeltas(intervals)
            val list = deltaMap[deltas.toString()]
            set.addAll(list)
            list.clear()
            list.addAll(set)
//            list.sortBy { c -> c.complexity }
            if (list.size > 0 && list[0].isInterval) {
                val c = list[0]
                list.clear()  // return only best match for interval
                val int = c.intervals[0]
                if (int == P5)
                    list.add(power)
                list.add(c)
            }
            val ci = ArrayList<ChordInstance>()
            list.forEach { c ->
                if (c.isRootless) {
                    val root = pitches[0].note.minus(c.rootless!!.intervals[0])
                    if (root != null)
                        ci.add(ChordInstance(root, c, 0))
                } else {
                    ci.add(ChordInstance(pitches[0].note, c, 0))
                }
            }
            if (recursive) {
                // try each pitch as the root
                val octave = pitches[0].getOctave() - 1
                for (i in 1..pitches.lastIndex) {
                    val p = Pitch(pitches[i].note, octave)
                    val plist = ArrayList<Pitch>()
                    plist.add(p)
                    plist.addAll(pitches)
                    plist.remove(pitches[i])
                    val chordList = chordLookup(plist, useEnharmonic = true, recursive = false)
                    chordList.forEach {
                        val d = pitches[0].note.minus(p.note) % 12
                        for (j in it.chord.intervals.indices) {
                            if (it.chord.intervals[j].semis % 12 == d) {
                                it.inv = j + 1
                                break
                            }
                        }
                    }
                    ci.addAll(chordList)
                }
            }
            return ArrayList(ci.distinct())
        }
    } else {
        return chordLookup(pitches)
    }
}

data class ChordInstance(var root: Note, var chord: Chord, var inv: Int, var bass: Note? = null) : Comparable<ChordInstance> {
    override fun toString(): String {
        if (chord === OneNote) {
            return ""
        }
        var text = "$root $chord"
        if (inv > 0) {
            text += " (${inversionName(inv)} inversion)"
        }
        bass?.let { text += " /$bass"}
        return text
    }

    private fun score() : Int {
        var s = 0
        s += 100 * chord.suffix.length
        s += chord.ratios[0].values.sum()
        return s
    }

    override fun compareTo(other: ChordInstance): Int {
        val score1 = this.score()
        val score2 = other.score()
        return score1 - score2
    }
}

class ChordList {
    private val arr = HashSet<Chord>()

    fun add(c: Chord) {
        arr.add(c)
    }

    fun addAll(list: List<Chord>) {
        arr.addAll(list)
    }

    fun getArrayList(): ArrayList<Chord> {
        val a = arr.toTypedArray()
        val arr = ArrayList<Chord>()
        arr.addAll(a)
        return arr
    }
}

fun inversionName(n: Int) = when (n) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${n}th"
}

fun intToInterval(n: Int) = when (n) {
    1 -> "unison"
    2 -> "second"
    3 -> "third"
    4 -> "fourth"
    5 -> "fifth"
    6 -> "sixth"
    7 -> "seventh"
    8 -> "octave"
    9 -> "ninth"
    10 -> "tenth"
    11 -> "eleventh"
    12 -> "twelfth"
    13 -> "thirteenth"
    14 -> "fourteenth"
    15 -> "double octave"
    else -> n.toString() + "th"
}

//fun intervalNameOf(c: Chord): String {
//    if (!c.isInterval)
//        return "????"
//    val int = c.intervals[0]
//    return qualityOf(int).toString() + " " + intToInterval(int.basicSize())
//}

fun intervalNameOf(int: Interval): String {
    if (int == BadInterval)
        return "<error>"
    return int.quality + " " + intToInterval(int.basicSize)
}

fun getRatio(ci: ChordInstance, rootPitch: Pitch, p: Pitch, altTuning: Boolean): Int {
    if (ci.chord === OneNote || ci.chord === NoChord)
        return 1
    val tuning = if (altTuning && ci.chord.ratios.size > 1) 1 else 0
    if (p.note.pc == rootPitch.note.pc) {
        var oct = (p.n - rootPitch.n) / 12
        var n = 1
        while (oct > 0) {
            oct--
            n *= 2
        }
        return n * ci.chord.ratios[tuning].values[0] * 2
    }
    val intervals = if (ci.chord.isRootless) ci.chord.rootless!!.intervals else ci.chord.intervals
    val ratios = if (ci.chord.isRootless) ci.chord.rootless!!.ratios else ci.chord.ratios
    for (i in intervals.indices) {
        var int = intervals[i]
        if (ci.chord.isInterval && ci.inv == 1) {
            int = P8.minus(int.simplify())!!
        }
        var tp = rootPitch.plus(int)
        var n = 1
        if (tp.n - p.n == 12 && int.semis > 12)
            return ratios[tuning].values[i + 1]
        while (tp.n < p.n) {
            tp = tp.plusOctave(1)
            n *= 2
        }
        if (tp.n == p.n)
            return n * ratios[tuning].values[i + 1] * 2
    }
    return 0 // error!
}

private fun rootlessSuffix(name: String) =
    when (name) {
         "m9" -> "maj7"
        "m-maj9" -> "maj7#5"
        "maj9" -> "m7"
        "9" -> "m7b5"
        else -> throw IllegalArgumentException("Bad suffix for rootless")
    }
