enum class Syllable {
    single, begin, middle, end
}

data class Lyric(val s: String) {
    constructor(str: String, num: Int, syllabic: Syllable, ext: String = "") : this(str) {
        number = num
        syllable = syllabic
        extend = ext
    }

    var number: Int = 1
    var syllable: Syllable = Syllable.single
    var extend: String = ""

    override fun toString() =
        if (extend == "stop" || extend == "_")
            "_"
        else when (syllable) {
            Syllable.single -> s
            Syllable.begin -> "$s -"
            Syllable.middle -> "- $s -"
            Syllable.end -> "- $s"
        }

    fun clone(): Lyric {
        return Lyric(s, number, syllable, extend)
    }
}
