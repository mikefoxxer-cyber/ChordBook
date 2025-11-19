import PitchClass.*
import AccidentalKind.*

// Note has a PitchClass, a Step, and a modifier (-3..3).
// There are 49 distinct Notes, but only 12 pitch classes, so each Note has several aliases.
// A Note does not have an octave, thus does not have a frequency or Pitch
// A Pitch is a particular Note (i.e., with octave), thus has a frequency

data class Note(var pc: PitchClass, val s: Step, val mod: Int) {
    constructor(step: Step) : this(firstPC[step.ordinal], step, 0)

    private val suffix: String
        get() {
            return ak().toString()
        }

    fun ak() = when(mod) {
        -3 -> tripleFlat
        -2 -> doubleFlat
        -1 -> flat
        0 -> none
        1 -> sharp
        2 -> doubleSharp
        3 -> tripleSharp
        else -> throw java.lang.IllegalArgumentException("Bad modifier for AccidentalKind: $mod")
    }

    override fun toString(): String {
        return "$s${suffix}"
    }

    /*
    private fun simplify() =
        when (mod) {
            0 -> this
            -1 -> when (s) {
                'C' -> noteLookup("B")
                'F' -> noteLookup("E")
                else -> this
            }
            1 -> when (s) {
                'E' -> noteLookup("F")
                'B' -> noteLookup("C")
                else -> this
            }
            -2 -> when (s) {
                'A' -> noteLookup("G")
                'B' -> noteLookup("A")
                'C' -> noteLookup("Bb")
                'D' -> noteLookup("C")
                'E' -> noteLookup("D")
                'F' -> noteLookup("Eb")
                'G' -> noteLookup("F")
                else -> throw UnexpectedException("Bad note name: $s")
            }
            2 -> when (s) {
                'A' -> noteLookup("B")
                'B' -> noteLookup("C#")
                'C' -> noteLookup("D")
                'D' -> noteLookup("E")
                'E' -> noteLookup("F#")
                'F' -> noteLookup("G")
                'G' -> noteLookup("A")
                else -> throw UnexpectedException("Bad note name: $s")
            }
            -3 -> when (s) {
                'A' -> noteLookup("Gb")
                'B' -> noteLookup("Ab")
                'C' -> noteLookup("A")
                'D' -> noteLookup("B")
                'E' -> noteLookup("Db")
                'F' -> noteLookup("D")
                'G' -> noteLookup("E")
                else -> throw UnexpectedException("Bad note name: $s")
            }
            3 -> when (s) {
                'A' -> noteLookup("C")
                'B' -> noteLookup("D")
                'C' -> noteLookup("D#")
                'D' -> noteLookup("F")
                'E' -> noteLookup("G")
                'F' -> noteLookup("G#")
                'G' -> noteLookup("A#")
                else -> throw UnexpectedException("Bad note name: $s")
            }
            else -> {
                throw UnexpectedException("Bad note modifier: $mod")
            }
        }
     */

    // Next higher note with same letter name, or null
    fun sharpen() : Note? {
        return if (mod == 3)
            null
        else Note(pc.next(), s, mod + 1)
    }

    // Next lower note with same letter name, or null
    fun flatten() : Note? {
        return if (mod == -3)
            null
        else Note(pc.prev(), s, mod - 1)
    }

    // Distance (0..11) in semitones from this down to other
    fun minus(other: Note) : Int {
        return (pc.ordinal - other.pc.ordinal + 12) % 12
    }

    // Distance (unison == 1) from this up to other, considering only note names
    fun nameDist(other: Note) : Int {
        return (other.s.ordinal - s.ordinal + 7) % 7 + 1
    }

    fun aliases() = notesForPitchClass(pc)

    fun midiNumber() : Int {
        return pc.midiNumber(0, mod)
    }

    fun plus(int: Interval) : Note? {
        val newPc = pc + int.semis
        val arr = notesForPitchClass(newPc)
        arr.forEach { n ->
            if (this.nameDist(n) % 7 == int.basicSize % 7)
                return n
        }
        return null
//        throw IllegalArgumentException("cannot compute $this.plus($int)")
    }

    fun minus(int: Interval) : Note? {
        val newPc = pc - int.semis
        val arr = notesForPitchClass(newPc)
        arr.forEach { n ->
            if (n.nameDist(this) % 7 == int.basicSize % 7)
                return n
        }
        return null
//        throw IllegalArgumentException("cannot compute $this.minus($int)")
    }

    fun natural(): Note {
        return noteLookup(s.toString())
    }
}

fun notesForPitchClass(pc: PitchClass) : Array<Note> {
    val arr = ArrayList<Note>()
    noteMap.values.forEach { n ->
        if (n.pc == pc)
            arr.add(n)
    }
    return arr.sortedByDescending { it.mod }.toTypedArray()
}

fun noteForMidiNumber(number: Int) =
    when((number + 12) % 12) {
        0 -> noteLookup("C")
        1 -> noteLookup("Db")
        2 -> noteLookup("D")
        3 -> noteLookup("Eb")
        4 -> noteLookup("E")
        5 -> noteLookup("F")
        6 -> noteLookup("F#")
        7 -> noteLookup("G")
        8 -> noteLookup("Ab")
        9 -> noteLookup("A")
        10 -> noteLookup("Bb")
        11 -> noteLookup("B")
        else -> throw IllegalArgumentException("Can't happen")
    }

val noteMap = HashMap<String, Note>()

val firstPC =   arrayOf(PC6, PC8, PC9, PC11, PC1, PC2, PC4)

fun initNotes() {
    Step.values().forEach { step ->
        if (step != Step.Rest) {
            var pc = firstPC[step.ordinal]
            for (mod in -3..3) {
                val note = Note(pc, step, mod)
                noteMap[note.toString()] = note
                pc = pc.next()
            }
        }
    }
}

fun noteLookup(s: String) : Note =
    (if(noteMap[s] != null) {
        noteMap[s]
    } else {
        throw ErrorMessage("unrecognized note name: $s")
    })!!
