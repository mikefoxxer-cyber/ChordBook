import IntervalNames.fractionMap

// Distance between two pitches

object IntervalNames {
    private val basic = arrayOf(
        Triple(1, 0, "P1"),
        Triple(2, 1, "m2"),
        Triple(2, 2, "M2"),
        Triple(3, 3, "m3"),
        Triple(3, 4, "M3"),
        Triple(4, 5, "P4"),
        Triple(5, 6, "d5"),
        Triple(5, 7, "P5"),
        Triple(5, 8, "A5"),
        Triple(6, 8, "m6"),
        Triple(6, 9, "M6"),
        Triple(7, 9, "d7"),
        Triple(7, 10, "m7"),
        Triple(7, 11, "M7"),
        Triple(8, 12, "P8"),
        Triple(9, 13, "m9"),
        Triple(9, 14, "M9"),
        Triple(10, 15, "m10"),
        Triple(10, 16, "M10"),
        Triple(11, 17, "P11"),
        Triple(12, 18, "d12"),
        Triple(12, 19, "P12"),
        Triple(12, 20, "A12"),
        Triple(13, 20, "m13"),
        Triple(13, 21, "M13"),
        Triple(14, 21, "d14"),
        Triple(14, 22, "m14"),
        Triple(14, 23, "M14"),
        Triple(15, 24, "P15")
   )

    val fractionMap = HashMap<String, Pair<Int, Int>>()

    fun initialize() {
        for (t in basic) {
            val int = Interval(t.first, t.second, t.third)
            nameMap[t.third] = int
        }
        for (semis in 0..12) {
            val pair = when (semis) {
                0 -> Pair("P1", Pair(1, 1))
                1 -> Pair("m2", Pair(16, 15))
                2 -> Pair("M2", Pair(9, 8))
                3 -> Pair("m3", Pair(6, 5))
                4 -> Pair("M3", Pair(5, 4))
                5 -> Pair("P4", Pair(4, 3))
                6 -> Pair("A4", Pair(45, 32))
                7 -> Pair("P5", Pair(3, 2))
                8 -> Pair("m6", Pair(8, 5))
                9 -> Pair("M6", Pair(5, 3))
                10 -> Pair("m7", Pair(9, 5))
                11 -> Pair("M7", Pair(15, 8))
                12 -> Pair("P8", Pair(2, 1))
                else -> throw UnknownError("Can't happen")
            }
            fractionMap[pair.first] = pair.second
        }
    }
}

val nameMap = HashMap<String, Interval>()

fun I(s: String) = nameMap[s]!!

val BadInterval = Interval(-1, -1, "<error>")
val P1 = Interval(1, 0, "P1")
val P5 = Interval(5, 7, "P5")
val P8 = Interval(8, 12, "P8")
val P15 = Interval(15, 24, "P15")

fun getInterval(bs: Int, semis: Int) : Interval {
    if (bs < 1) {
        Main.log("Interval smaller than unison; check note order!")
        return BadInterval
    }
    for (int in nameMap.values) {
        if (int.bs == bs && int.semis == semis) {
            return int
        }
    }
    // create it
    if (bs > 8) {
        for ((n, i) in nameMap) {
            if (i.bs == bs - 7 && i.semis == semis - 12) {
                val kind = n[0]
                val name = "$kind$bs"
                val newInt = Interval(bs, semis, name)
                nameMap[name] = newInt
                return newInt
            }
        }
    } else {
        for ((n, i) in nameMap) {
            if (i.bs == bs + 7 && i.semis == semis + 12) {
                val kind = n[0]
                val name = "$kind$bs"
                val newInt = Interval(bs, semis, name)
                nameMap[name] = newInt
                return newInt
            }
        }
    }
    // try deriving it
    for (i in nameMap.values) {
        if (i.bs == bs && i.semis > semis && i.isBasic()) {
            val name = when {
                i.semis - semis == 1 -> {
                    "d$bs"
                }
                i.semis - semis == 2 -> {
                    "dd$bs"
                }
                else -> {
                    val size = i.semis - semis
                    "${size}d$bs"
                }
            }
            val int = Interval(bs, semis, name)
            nameMap[name] = int
            return int
        } else if (i.bs == bs && i.semis < semis && i.isBasic()) {
            val name = when {
                semis - i.semis == 1 -> {
                    "A$bs"
                }
                semis - i.semis == 2 -> {
                    "AA$bs"
                }
                else -> {
                    val size = semis - i.semis
                    "${size}A$bs"
                }
            }
            val int = Interval(bs, semis, name)
            nameMap[name] = int
            return int
        }
    }
    throw IllegalArgumentException("No base for interval (bs=$bs, semis=$semis)")
}

data class Interval(val bs: Int, val semis: Int, var name: String = "") : Comparable<Interval> {
    init {
        if (name == "") {
            name = figureName()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is Interval)
            return bs == other.bs && semis == other.semis
        return false
    }

    override fun hashCode(): Int {
        return semis * 1000 + bs
    }

    override fun toString(): String {
        return name
    }

    fun simplify() : Interval { // reduce to less than one octave
        if (bs > 7 && semis > 11) {
            return getInterval(bs - 7, semis - 12).simplify()
        }
        return this
    }

    val basicSize = bs

    fun isBasic() = when (name[0]) {
            'P', 'm', 'M' -> true
            else -> false
        }

    private fun figureName() : String {
        for ((n, i) in nameMap) {
            if (i == this) {
                return n
            }
        }
        // not found? how about the octave related one?
        if (bs >= 8) {
            val other = getInterval(bs - 7, semis - 12)
            for ((n, i) in nameMap) {
                if (i == other) {
                    val kind = n[0]
                    val size = i.bs + 7
                    val name = "$kind$size"
                    nameMap[name] = this
                    return name
                }
            }
        } else {
            val other = getInterval(bs + 7, semis + 12)
            for ((n, i) in nameMap) {
                if (i == other) {
                    val kind = n[0]
                    val size = i.bs - 7
                    val name = "$kind$size"
                    nameMap[name] = this
                    return name
                }
            }
        }
        throw IllegalArgumentException("Cannot figure name for interval(bs = $bs, semis=$semis)")
    }

    fun getJIFraction() : Pair<Int, Int> {
        val pair: Pair<Int, Int>? = fractionMap[name]
        if (pair == null) {
            // generate it
            if (semis >= 12) {
                // try octave equivalence
                for ((n, p) in fractionMap) {
                    val int = lookupInterval(n)
                    if (int.semis == semis - 12) {
                        val newPair = Pair(p.first * 2, p.second)
                        fractionMap[name] = newPair
                        break
                    }
                }
            } else if (semis < 0) { // 3d2, for example
                for ((n, i) in nameMap) {
                    if (i.semis == semis * -1) {
                        val otherPair = fractionMap[n]
                        if (otherPair != null) {
                            fractionMap[name] = Pair(otherPair.second, otherPair.first)
                            break
                        }
                    }
                }
            } else {
                for ((n, i) in nameMap) {
                    if (i.semis == semis) {
                        val otherPair = fractionMap[n]
                        if (otherPair != null) {
                            fractionMap[name] = otherPair
                            break
                        }
                    }
                }
            }
        }
        return fractionMap[name] ?:
            throw IllegalArgumentException("No JI fraction for $this")
    }

    override fun compareTo(other: Interval): Int {
        return if (other.semis == this.semis) {
            this.bs - other.bs
        } else {
            this.semis - other.semis
        }
    }

    fun minus(other: Interval) : Interval? {
        return if (this.semis >= other.semis && this.bs >= other.bs) {
            Interval(bs - other.bs + 1, semis - other.semis)
        } else {
            null
        }
    }

    val quality: String
        get() {
            if (this == BadInterval)
                return "<error>"
            return figureQuality()
        }

    private fun figureQuality() : String {
        return when (name[0]) {
            'P' -> "perfect"
            'm' -> "minor"
            'M' -> "major"
            'd' -> {
                if (name[1] == 'd') {
                    "doubly-diminished"
                } else {
                    "diminished"
                }
            }
            'A' -> {
                if (name[1] == 'A') {
                    "doubly-augmented"
                } else {
                    "augmented"
                }
            }
            else -> {
                val quantity = "${name[0]}".toInt()
                if (name[1] == 'd') {
                    "${quantity(quantity)}-diminished"
                } else {
                    "${quantity(quantity)}-augmented"
                }
            }
        }
    }

    // if smaller than an octave, return this plus an octave
    fun expand() : Interval {
        return if (bs <= 8) {
            Interval(bs + 7, semis + 12)
        } else {
            this
        }
    }
}

private fun quantity(n: Int) = when(n) {
    2 -> "doubly"
    3 -> "triply"
    4 -> "quadruply"
    5 -> "pentuply"
    6 -> "sextuply"
    7 -> "septuply"
    else -> throw IllegalArgumentException("Bad quantity: $n")
}

fun lookupInterval(s: String) :  Interval {
    if (nameMap.size == 0) {
        IntervalNames.initialize()
    }

    if (nameMap[s] != null)
        return nameMap[s]!!

    // generate it
    val kind = s[0]
    val i = s.indexOfAny("0123456790".toCharArray(), 1)
    val size = s.substring(i).toInt()
    if (kind == 'd') {
        var found: Interval? = null
        for (int in nameMap.values) {
            if (int.bs == size &&
                (int.name[0] == 'm' || int.name[0] == 'P') ) {
                found = int
                break
            }
        }
        if (found != null) {
            val newInt = Interval(size, found.semis - 1, s)
            nameMap[s] = newInt
        } else {
            // try octave smaller
            for (int in nameMap.values) {
                if (int.bs == size - 7 &&
                    (int.name[0] == 'm' || int.name[0] == 'P')) {
                    found = int
                    break
                }
            }
            if (found != null) {
                val newInt = Interval(size, found.semis + 11, s)
                nameMap[s] = newInt
            } else {
                throw IllegalArgumentException("No base interval for $s`")
            }
        }
    } else if (kind == 'A') {
        var found: Interval? = null
        for (int in nameMap.values) {
            if (int.bs == size &&
                (int.name[0] == 'M' || int.name[0] == 'P') ) {
                found = int
                break
            }
        }
        if (found != null) {
            val newInt = Interval(size, found.semis + 1, s)
            nameMap[s] = newInt
        } else {
            // try octave smaller
            for (int in nameMap.values) {
                if (int.bs == size - 7 &&
                    (int.name[0] == 'M' || int.name[0] == 'P')) {
                    found = int
                    break
                }
            }
            if (found != null) {
                val newInt = Interval(size, found.semis + 11, s)
                nameMap[s] = newInt
            } else {
                throw IllegalArgumentException("No base interval for $s`")
            }
        }
    } else {
        if (size < 8) {
            throw IllegalArgumentException("Bad interval name: $s")
        } else {
            var found: Interval? = null
            for (int in nameMap.values) {
                if (int.bs == size - 7 && int.name[0] == kind) {
                    found = int
                    break
                }
            }
            if (found != null) {
                val newInt = Interval(found.bs + 7, found.semis + 12, s)
                nameMap[s] = newInt
            } else {
                throw IllegalArgumentException("No base interval for $s")
            }
        }
    }

    return nameMap[s] ?:
        throw IllegalArgumentException("Unknown Interval: $s")
}

fun intervalBetween(a: Note, b: Note) : Interval {
    val semis = b.minus(a)
    val bs = a.nameDist(b)
    return Interval(bs, semis)
}

fun intervalForSemis(s: Int) : Interval {
    return when (s) {
        0 -> getInterval(1, 0)
        1 -> getInterval(2, 1)
        2 -> getInterval(2, 2)
        3 -> getInterval(3, 3)
        4 -> getInterval(3, 4)
        5 -> getInterval(4, 5)
        6 -> getInterval(5, 6)
        7 -> getInterval(5, 7)
        8 -> getInterval(7, 8)
        9 -> getInterval(6, 9)
        10 -> getInterval(7, 10)
        11 -> getInterval(7, 11)
        12 -> getInterval(8, 12)
        13 -> getInterval(9, 13)
        14 -> getInterval(9, 14)
        15 -> getInterval(10, 15)
        16 -> getInterval(10, 16)
        17 -> getInterval(11, 17)
        18 -> getInterval(12, 18)
        19 -> getInterval(12, 19)
        20 -> getInterval(13, 20)
        21 -> getInterval(13, 21)
        22 -> getInterval(14, 22)
        23 -> getInterval(14, 23)
        24 -> getInterval(15, 24)
        else -> throw IllegalArgumentException("Bad semitone count for interval: $s")
    }
}

fun reduceIntervals(intervals: List<Interval>) : ArrayList<Interval> {
    val arr = ArrayList<Interval>()
    arr.addAll( intervals.sorted() )
    return reduceIntervals(arr)
}

fun reduceIntervals(intervals: ArrayList<Interval>) : ArrayList<Interval> {
    val set = HashSet<Interval>()
    intervals.forEach { int ->
        if (int != P8) {
            set.add(int.simplify())
        }
    }
    val arr = ArrayList<Interval>()
    arr.addAll(set)
    arr.sort()
    return arr
}

fun getDeltas(intervals: ArrayList<Interval>) : ArrayList<Int> {
    val deltas = ArrayList<Int>()
    intervals.forEach { int ->
        val simple = int.simplify()
        if (simple.semis != 0)
            deltas.add(simple.semis)
    }
    deltas.sort()
    return deltas
}

fun analyzeIntervals(pitchList: ArrayList<Pitch>) : ArrayList<Interval> {
    if (pitchList.size == 0)
        return ArrayList()
    val notes = HashSet<Note>()
    for (p in pitchList)
        notes.add(p.note)
    val pitches = ArrayList<Pitch>()
    pitches.add(pitchList[0])
    if (notes.size == 1) {
        // some form of Unison or Octave
        for (p in pitchList) {
            if (pitches.contains(p))
                continue
            pitches.add(p)
        }
        if (pitches.size == 1)
            return arrayListOf(P1)
        val root = pitches[0]
        val arr = ArrayList<Interval>()
        for (i in 1 .. pitches.lastIndex) {
            var p = pitches[i]
            while (root.distTo(p) > 15) {
                p = p.plusOctave(-1)
            }
            val int = p.minus(root)
            if (arr.contains(int))
                continue
            arr.add(int)
        }
        val list = arr.sorted()
        return ArrayList(list)
    } else {
        // simplify: remove duplicated notes
        notes.remove(pitches[0].note)
        for (p in pitchList) {
            if (notes.contains(p.note)) {
                pitches.add(p)
                notes.remove(p.note)
            }
        }
    }
    val arr = ArrayList<Interval>()
    val root = pitches[0]
    for (i in 1 .. pitches.lastIndex) {
        var p = pitches[i]
        if (arr.size > 0 && p.note == root.note)
            continue
        while (root.distTo(p) > 15) {
            p = p.plusOctave(-1)
        }
        val ii = getInterval(root.distTo(p).toInt() + 1, p.n - root.n)
        arr.add(ii)
    }
    arr.sort()
    return arr
}