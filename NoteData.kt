import Step.Rest

data class NoteData(var degree: Step = Rest, var alter: Int = 0) {
    val note: Note?
        get () {
            if (degree == Rest)
                return null
            return noteLookup("$degree$suffix")
        }
    private val suffix: String
        get () {
            return accidentalKindForModifier(alter).toString()
        }
    val pitch: Pitch?
        get () {
            if (note == null)
                return null
            return Pitch(note!!, octave)
        }
    var octave = 0
    var vp: VoicePart? = null
    val part: Int
        get() { return if (vp == null ) 0 else vp!!.part }
    val voice: Int
        get() { return if (vp == null) 0 else vp!!.voice }
    var divStart = 0 // in divisions, measure-relative
    var startTime = 0 // in ticks, measure-relative
    var divLength = 0 // in divisions
    var length = 0 // in ticks
    var tieFrom = false
    var tieTo = false
    var fermata = false
    var lyric: Lyric? = null

    private fun midiNoteNumber() : Int {
        return note?.pc?.midiNumber(octave, alter) ?: -1
    }

    override fun toString(): String {
        return "Note($degree$suffix,oct=$octave,num=${midiNoteNumber()},start=$startTime,len=$length}"
    }
}
