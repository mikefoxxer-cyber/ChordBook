enum class AccidentalKind {
    tripleFlat, doubleFlat, flat, natural, sharp, doubleSharp, tripleSharp, none;

    override fun toString(): String {
        return when(this) {
            tripleFlat  -> "bbb"
            doubleFlat  -> "bb"
            flat        -> "b"
            natural     -> ""
            sharp       -> "#"
            doubleSharp -> "x"
            tripleSharp -> "x#"
            none        -> ""
        }
    }

    fun getString() = when(this) { // Unicode for Maestro font symbol
        tripleFlat  -> "\uF062\uF062\uF062"
        doubleFlat  -> "\uF0BA"
        flat        -> "\uF062"
        natural     -> "\uF06E"
        sharp       -> "\uF023"
        doubleSharp -> "\uF0DC"
        tripleSharp -> "\uF023\uF0DC"
        none        -> ""
    }

    fun modifier() = when(this) {
        tripleFlat  -> -3
        doubleFlat  -> -2
        flat        -> -1
        natural     -> 0
        sharp       -> 1
        doubleSharp -> 2
        tripleSharp -> 3
        none        -> 0
    }
}

fun accidentalKindForModifier(mod: Int) : AccidentalKind {
    if (mod in -3 .. 3) {
        if (mod == 0)
            return AccidentalKind.none
        return AccidentalKind.values()[mod + 3]
    } else {
        throw IllegalArgumentException("Bad modifier: $mod")
    }
}