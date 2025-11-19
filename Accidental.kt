import javafx.scene.shape.Rectangle

// An accidental on the Staff canvas
class Accidental(k: AccidentalKind, x: Double, y: Double, w: Double, h: Double) {
    val kind: AccidentalKind = k
    val r: Rectangle = Rectangle(x, y, w, h)

    fun collidesWith(a: Accidental) : Boolean {
        return this.r.intersects(a.r.x, a.r.y, a.r.width, a.r.height)
    }
}

// adjust the X coordinates of the given accidentals such that no two of them overlap
// and suppress multiple copies of same accidental on same line
// y coords are actual, x coords will be subtracted from note's x coord, so positive is to the left
fun layoutAccidentals(acc: Array<Accidental?>) {
    var ok = false
    val map = HashMap<Double, Accidental>()

    while (!ok) {
        ok = true
        for (i in acc.indices) {
            if (acc[i] == null)
                continue
            val a = acc[i]!!
            if (a.kind == AccidentalKind.none)
                continue
            val y = a.r.y
            if (map.containsKey(y)) {
                if (map[y]!!.kind == a.kind && map[y] != a) {
                    a.r.height = 0.0
                    continue
                }
            }
            map[a.r.y] = a
            for (j in acc.indices) {
                if (j == i)
                    continue
                if (acc[j] == null)
                    continue
                val b = acc[j]!!
                if (b.r.width == 0.0)
                    continue
                if (b.kind == AccidentalKind.none)
                    continue
                if (a.collidesWith(b)) {
                    ok = false
                    b.r.x += a.r.width + 2
                }
            }
            if (!ok)
                break
        }
    }
}