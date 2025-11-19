import Quality.*

enum class Quality {
    Perfect, Major, Minor,
    Augmented, Diminished,
    Augmented2, Diminished2,
    Augmented3, Diminished3,
    Augmented4, Diminished4,
    Augmented5, Diminished5,
    Augmented6, Diminished6,
    Augmented7, Diminished7;

    override fun toString(): String {
        return qName[this]!!
    }

    fun next() = when (this) {
        Perfect -> Augmented
        Major -> Augmented
        Minor -> Major
        Augmented -> Augmented2
        Augmented2 -> Augmented3
        Augmented3 -> Augmented4
        Augmented4 -> Augmented5
        Augmented5 -> Augmented6
        Augmented6 -> Augmented7
        else -> throw IllegalArgumentException("bad interval quality in next(): $this")
    }

    fun prev() = when (this) {
        Perfect -> Diminished
        Major -> Minor
        Minor -> Diminished
        Diminished -> Diminished2
        Diminished2 -> Diminished3
        Diminished3 -> Diminished4
        Diminished4 -> Diminished5
        Diminished5 -> Diminished6
        Diminished6 -> Diminished7
        else -> throw IllegalArgumentException("bad interval quality in prev(): $this")
    }

    fun modLevel() = when (this) {
        Perfect, Major, Minor -> 0
        Augmented, Diminished -> 1
        Augmented2, Diminished2 -> 2
        Augmented3, Diminished3 -> 3
        Augmented4, Diminished4 -> 4
        Augmented5, Diminished5 -> 5
        Augmented6, Diminished6 -> 6
        Augmented7, Diminished7 -> 7
    }
}

val qName = HashMap<Quality, String>()

fun initQuality() {
    qName[Perfect] = "perfect"
    qName[Major] = "major"
    qName[Minor] = "minor"
    qName[Augmented] = "augmented"
    qName[Diminished] = "diminished"
    qName[Augmented2] = "doubly-augmented"
    qName[Diminished2] = "doubly-diminished"
    qName[Augmented3] = "triply-augmented"
    qName[Diminished3] = "triply-diminished"
    qName[Augmented4] = "quadruply-augmented"
    qName[Diminished4] = "quadruply-diminished"
    qName[Augmented5] = "quintuply-augmented"
    qName[Diminished5] = "quintuply-diminished"
    qName[Augmented6] = "sextuply-augmented"
    qName[Diminished6] = "sextuply-diminished"
    qName[Augmented7] = "septuply-augmented"
    qName[Diminished7] = "septuply-diminished"
}
