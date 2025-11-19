import SymbolConstants.*
import java.io.StringReader

class ChordSymbolParser {
    private var root: Note? = null
    private var inversion = 0
    private var chord: Chord? = null
    private var bass: Note? = null

    private fun reset() {
        root = null
        inversion = 0
        chord = null
        bass = null
    }

    fun parse(text: String): Boolean {
        var unrooted = false
        val rdr = StringReader(text)
        Symbol.ReInit(rdr)
        reset()
        val res = Symbol.one_line()
        if (res != 0)
            return false
        val tok = Symbol.tok.toTypedArray()
        Symbol.tok.clear()
        if (Symbol.root[0] == null && tok.isEmpty()) {
            throw IllegalArgumentException("Please enter a chord symbol.")
        }
        inversion = 0
        var r = "C"
        if (Symbol.root[0] != null) {
            r = ""
            for (i in Symbol.root.indices) {
                if (Symbol.root[i] != null)
                    r += Symbol.root[i].image
            }
        }
        root = noteLookup(r)
        var type = ""
        if (Symbol.type != null) {
            type = Symbol.type.image
        }
        var c: Chord = chordLookup(type) ?: throw IllegalArgumentException("Unrecognized chord: $text")
        val intervals = HashSet<Interval>()
        intervals.addAll(c.intervals)
        var i = 0
        while (i < tok.size) {
            // process modifiers
            if (tok[i] == null) {
                i++
                continue
            }
            when (tok[i].kind) {
                AUGMENTED, DIMINISHED, MINOR, MAJOR, PERFECT -> {
                    val s = tok[i].image + " " + tok[i + 1].image
                    c = chordLookup(s) ?: throw IllegalArgumentException("Unrecognized interval: $s")
                    chord = c
                    return true
                }
                OCTAVE, UNISON, DBLOCTAVE -> {
                    val s = "perfect ${tok[i]}"
                    c = chordLookup(s) ?: throw IllegalArgumentException("Unrecognized interval: $s")
                    chord = c
                    return true
                }
                // not an interval -- parse a chord name
                else -> {
                    when (val m = tok[i].image) {
                        "noroot", "no root" -> {
                            if (unrooted) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            unrooted = true
                        }
                        "no3", "no 3" -> {
                            val ok = intervals.remove(I("M3"))
                            if (!ok) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                        }
                        "b5", "-5" -> {
                            intervals.remove(P5)
                            if (intervals.contains(I("d5"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("d5"))
                        }
                        "#5", "+5", "+" -> {
                            intervals.remove(P5)
                            if (intervals.contains(I("A5"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("A5"))
                        }
                        "no5", "no 5" -> {
                            val ok = intervals.remove(P5)
                            if (!ok || intervals.contains(I("A5")) ||
                                intervals.contains(I("d5"))
                            ) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                        }
                        "6", "add6", "add 6", "/6" -> {
                            if (intervals.contains(I("M6"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M6"))
                        }
                        "7" -> {
                            if (type == "dim") {
                                if (intervals.contains(I("d7"))) {
                                    throw IllegalArgumentException("Unexpected: $m")
                                }
                                intervals.add(I("d7"))
                            } else {
                                if (intervals.contains(I("m7"))) {
                                    throw IllegalArgumentException("Unexpected: $m")
                                }
                                intervals.add(I("m7"))
                            }
                        }
                        "M7", "maj7", "-maj7" -> {
                            if (type == "" && m == "-maj7") { // should be minor triad plus maj7
                                intervals.remove(I("M3"))
                                intervals.add(I("m3"))
                            }
                            if (intervals.contains(I("M7"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M7"))
                        }
                        "9" -> {
                            if (intervals.contains(I("M9"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M9"))
                            if (!intervals.contains(I("M6"))) {
                                intervals.add(I("m7"))
                            }
                        }
                        "M9", "maj9", "-maj9" -> {
                            if (intervals.contains(I("M9"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M9"))
                            intervals.add(I("M7"))
                        }
                        "b9", "-9" -> {
                            if (intervals.contains(I("m9"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("m9"))
                            intervals.remove(I("M9"))
                        }
                        "#9", "+9" -> {
                            if (intervals.contains(I("A9"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("A9"))
                            intervals.remove(I("M9"))
                        }
                        "b10", "-10" -> {
                            if (intervals.contains(I("m10"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("m10"))
                            intervals.remove(I("M10"))
                        }
                        "11" -> {
                            if (intervals.contains(I("P11"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
//                            intervals.remove(M3)
                            intervals.add(I("P11"))
                            intervals.add(I("M9"))
                            intervals.add(I("m7"))
                        }
                        "M11", "maj11", "-maj11" -> {
                            if (intervals.contains(I("P11"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("P11"))
                            intervals.add(I("M9"))
                            intervals.add(I("M7"))
                        }
                        "#11", "+11" -> {
                            if (intervals.contains(I("P11"))) {
                                intervals.remove(I("P11"))
                            }
                            intervals.add(I("A11"))
                            if (!intervals.contains(I("m9")) && !intervals.contains(I("m10"))) {
                                intervals.add(I("M9"))
                            }
                            if (!intervals.contains(I("M7"))) {
                                intervals.add(I("m7"))
                            }
                        }
                        "no11", "no 11" -> {
                            intervals.remove(I("P11"))
                            intervals.remove(I("A11"))
                        }
                        "13" -> {
                            if (intervals.contains(I("M13"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M13"))
                            if (!intervals.contains(I("M3"))) {
                                intervals.add(I("P11"))
                            }
                            intervals.add(I("M9"))
                            intervals.add(I("m7"))
                        }
                        "maj13", "M13" -> {
                            if (intervals.contains(I("M13"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M13"))
                            intervals.add(I("A11"))
                            intervals.add(I("M9"))
                            intervals.add(I("M7"))
                        }
                        "b13" -> {
                            if (intervals.contains(I("m13"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("m13"))
                            if (!intervals.contains(I("M3"))) {
                                intervals.add(I("P11"))
                            }
                            if (!intervals.contains(I("m9")) && !intervals.contains(I("A9")) &&
                                !intervals.contains(I("m10"))
                            ) {
                                intervals.add(I("M9"))
                            }
                            if (!intervals.contains(I("M7"))) {
                                intervals.add(I("m7"))
                            }
                        }
                        "sus", "sus4" -> {
                            if (intervals.contains(I("P4")) ||
                                (!intervals.contains(I("M3")) && !intervals.contains(I("m3")))
                            ) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.remove(I("M3"))
                            intervals.remove(I("m3"))
                            intervals.add(I("P4"))
                        }
                        "sus2" -> {
                            if (intervals.contains(I("M2")) ||
                                !intervals.contains(I("M3"))
                            ) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.remove(I("M3"))
                            intervals.add(I("M2"))
                        }
                        "add2", "add 2", "/2" -> {
                            if (intervals.contains(I("M2"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M2"))
                        }
                        "add4", "4", "/4" -> {
                            if (intervals.contains(I("P4"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("P4"))
                        }
                        "add9", "/9" -> {
                            if (intervals.contains(I("M9"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M9"))
                        }
                        "over" -> {
                            val overtones = arrayOf("M3", "P5", "m7", "P8", "M9", "M10", "A11", "P12", "M13", "M14")
                            overtones.forEach { s ->
                                intervals.add(I(s))
                            }
                        }
                        "/7" -> {
                            if (intervals.contains(I("m7"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("m7"))
                        }
                        "/11" -> {
                            if (intervals.contains(I("P11"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("P11"))
                        }
                        "/13" -> {
                            if (intervals.contains(I("M13"))) {
                                throw IllegalArgumentException("Unexpected: $m")
                            }
                            intervals.add(I("M13"))
                        }
                        "/" -> {
                            // alternate base note
                            if (++i == tok.size || tok[i] == null) {
                                throw IllegalArgumentException("Missing bass note after /")
                            }
                            var s = tok[i++].image
                            if (i < tok.size && tok[i] != null) {
                                val a = tok[i].image
                                if (a == "b" || a == "bb" || a == "#" || a == "x") {
                                    s += a
                                    i++
                                }
                            }
                            bass = noteLookup(s)
                            if (bass == null) {
                                throw IllegalArgumentException("Unrecognized bass note: $s")
                            }
                        }
                        else -> {
                            // assume it's an inversion indicator
                            if ("123456789".indexOf(m[0]) < 0) {
                                throw IllegalArgumentException("Expecting a digit, saw $m")
                            }
                            inversion = when (m) {
                                "1st" -> 1
                                "2nd" -> 2
                                "3rd" -> 3
                                else -> {
                                    val n = m.indexOf("th")
                                    if (n < 1) {
                                        throw IllegalArgumentException("Unrecognized inversion: $m")
                                    }
                                    m.substring(0, n).toInt()
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
        if (intervals.isEmpty()) {
            throw IllegalArgumentException("No intervals!")
        }
        val chords = chordLookup(intervals.toList().sorted())

        if (chords.isNotEmpty()) {
//            Main.log("Chord = $chord")
            if (unrooted) {
                var found = false
                for (c2 in chords) {
                    if (c2.canBeRootless) {
                        bass = root!!.plus(c2.third())
                        chord = c2.rootless
                        found = true
                        break
                    }
                }
                if (!found) {
                    val t = if (type == "") "major" else type
                    throw IllegalArgumentException("Unexpected; chord type \"$t\" cannot be rootless")
                }
            } else {
                chord = chords[0]
            }
        }

        return root != null && chord != null
    }

    fun chordInstance() = if (root == null || chord == null)
            null
        else
            ChordInstance(root!!, chord!!, inversion, bass)
}