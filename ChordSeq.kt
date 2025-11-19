import kotlin.math.max
import kotlin.math.min

class ChordSeq {
    val list = ArrayList<Item>()
    private var lastTick = 0L

    // insert a chord, maintaining time order
    fun insert(e: Item) {
        for (i in list.indices) {
            if (e.tick < list[i].tick) {
                list.add(i, e)
                return
            }
        }
        if (list.isNotEmpty()) {
            val last = list.last()
            last.length = min(last.length, (e.tick - last.tick).toInt())
        }
        list.add(e)
        lastTick = max(lastTick, e.lastTick())
    }

//    fun isEmpty(): Boolean = list.isEmpty()

//    fun lastTick(): Long {
//        return lastTick
//    }
}