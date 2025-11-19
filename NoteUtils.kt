enum class Step {
    A, B, C, D, E, F, G, Rest
}

// PitchClass 0 == C
enum class PitchClass {
    PC0, PC1, PC2, PC3, PC4, PC5, PC6, PC7, PC8, PC9, PC10, PC11;

    fun midiNumber(oct: Int, mod: Int) : Int {
        val n = ordinal + (oct + 1) * 12 // C0 is midi # 12
        return when {
            ordinal - mod < 0 -> {
                n + 12 // sharp modifier crosses octave boundary
            }
            ordinal - mod > 11 -> {
                n - 12 // flat modifier crosses octave boundary
            }
            else -> {
                n
            }
        }
    }

    fun octave(note: Note, midi: Int) : Int {
        return (midi - note.mod) / 12 - 1
    }

    fun next(): PitchClass {
        val n = (this.ordinal + 1) % 12
        return values()[n]
    }

    fun prev(): PitchClass {
        val n = (this.ordinal + 11) % 12
        return values()[n]
    }

    operator fun plus(semis: Int): PitchClass {
        val n = (this.ordinal + semis) % 12
        return values()[n]
    }

    operator fun minus(semis: Int): PitchClass {
        val n = (this.ordinal + 12 - semis) % 12
        return values()[n]
    }

    operator fun minus(pc: PitchClass): Int {
        return (this.ordinal + 12 - pc.ordinal) % 12
    }
}
