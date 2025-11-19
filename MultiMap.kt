class MultiMap {
    private val map = HashMap<String, ArrayList<Chord>>()

    fun add(key: String, c: Chord) {
        val a = map.getOrDefault(key, ArrayList())
        if (!a.contains(c))
            a.add(c)
        map[key] = a
    }

    operator fun get(key: String) : ArrayList<Chord> {
        return map.getOrDefault(key, ArrayList())
    }

    fun containsKey(key: String) = map.containsKey(key)

    fun keys() = map.keys

    fun values() : ArrayList<Chord> {
        val arr = ArrayList<Chord>()
        map.forEach { (_, a) ->
            arr.addAll(a)
        }
        return arr
    }
}