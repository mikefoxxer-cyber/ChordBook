class TuningSpec(var tick: Long = 0L,
                 var note: Note? = null,
                 var vp: VoicePart? = null,
                 var cents: Double = 0.0
) {

    fun pitchBend() : Int {
        if (cents == 0.0)
            return 0
        return (cents * 40.96).toInt()
    }

    override fun toString(): String {
        val s = ""
        val c = if (cents == 0.0) s else (String.format("%+.3f", cents))
        return "$tick: ${vp?.shortName ?: s}${note?.toString() ?: s}$c"
    }
}
