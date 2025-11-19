import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

// A MIDI pitch (0..127), with optional pitch bend

// MIDI pitch 0 is C in octave -1; middle C (C4) is 60

const val MINFREQ = 8.1757989156 // C-1's freq in Hz (MIDI pitch # 0)
var SEMITONE = 2.0.pow(1.0 / 12.0) // 12th root of 2

data class Pitch(val n: Int) {

    var voicePart: VoicePart? = null
    var note: Note
    var bend = 0.0 // in cents
    var userBend = false

    // NOTE: bend in cents is Double, in PBUs is Int!

    fun pitchBend() =  // in Midi Pitch Bend Units (PBUs)
            (bend * 40.96).toInt()

    init {
        note = noteForMidiNumber(n)
    }

    constructor(nt: Note, oct: Int) : this(nt.pc.midiNumber(oct, nt.mod)) {
        note = nt
    }

    fun setPbus(pbus: Int, user: Boolean = false) {
        bend = pbus.toDouble() / 40.96 // PBU per cent
        if (user)
            userBend = true
    }

    fun setCents(cents: Double, user: Boolean = false) {
        bend = cents
        if (user)
            userBend = true
    }

    override fun toString(): String {
        var s = ""
        s += note.toString()
        s += "${getOctave()}"
        if (bend != 0.0)
            s += String.format("(%+.3f)", bend)
        return s
    }

    fun minus(other: Pitch) : Interval {
        var int = intervalBetween(note, other.note)
        if (n - other.n >= 12)
            int = int.expand()
        return int
    }

    fun plus(semis: Int) : Pitch {
        val p = Pitch(n + semis)
        p.bend = bend
        return p
    }

    fun plusOctave(oct: Int): Pitch {
        val p = Pitch(n + 12 * oct)
        p.bend = bend
        p.note = note
        if (p.n < 0 || p.n > 127)
            throw IllegalArgumentException("MIDI number out of range: ${p.n}")
        return p
    }

    fun plus(int: Interval): Pitch {
        val n = this.n + int.semis
        val newNote = note.plus(int)
        val newPitch = Pitch(n)
        if (newNote == null)
            Main.log("Failed to compute $this plus $int")
        newPitch.note = newNote ?: this.note
        return newPitch
    }

    fun minus(int: Interval): Pitch {
        val n = this.n - int.semis
        val newNote = note.minus(int)
        val newPitch = Pitch(n)
        if (newNote == null)
            Main.log("Failed to compute $this minus $int")
        newPitch.note = newNote ?: this.note
        return newPitch
    }

    fun getOctave() : Int {
        return note.pc.octave(note, midi = n)
    }

    /*
     * The "line" for the note. This is an integer, 0 - 74, that corresponds to
     * the line or space of the staff that this pitch would be drawn on. 0 ==
     * C0, 35 == C4 (middle C). Spaces are even numbers.
     */
    private fun line() : Int {
        var n = 35
        n += C4.note.nameDist(this.note) - 1
        n += (getOctave() - 4) * 7
        return n
    }

    // Upward distance to another Pitch, in note names; unison = 0
    fun distTo(p: Pitch): Double {
        val n = this.line()
        val m = p.line()
        return (m - n).toDouble()
    }

    fun unbent() : Double { // un-pitchbent frequency of this pitch, in Hz
        var freq = MINFREQ
        var n = 0

        while (n < this.n - 11) {
            freq *= 2.0
            n += 12
        }

        // freq is pitch of the C which is 0..11 semitones below this

        while (n < this.n) {
            freq *= SEMITONE
            n++
        }

        return freq
    }

    fun bent(): Double { // freq (in Hz) after applying bend
        val baseFreq = unbent()
        return baseFreq * 2.0.pow(bend / (12.0 * 4096.0 * ln(2.0)))
    }

    fun setBendFromFreq(freq: Double) {
        if (!userBend) {
            val baseFreq = unbent()
            bend = 1200.0 * ln(freq / baseFreq) / ln(2.0)
            if (abs(bend) > 100.0) {
                Main.log("Large pitch bend: $this")
            }
        }
    }
}

val C4 = Pitch(60)
