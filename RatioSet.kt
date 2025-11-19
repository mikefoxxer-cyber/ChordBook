import java.lang.IllegalArgumentException
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A set of integers representing the frequency ratios in a Just Intonation
 * chord
 */
class RatioSet(args: Array<Int>) {
    val values = ArrayList<Int>() // the integer values of the ratios
    private var degenerate = false

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[")
        values.forEach {
            sb.append("$it:")
        }
        sb.deleteCharAt(sb.lastIndex)
        sb.append("]")
        return sb.toString()
    }

    init {
        val n = args.size
        for (i in 0 until n) {
            values.add(args[i])
            if (values[0] == 1 && values[i] == 1)
                degenerate = true
//            if (i > 0) {
//                if (!degenerate && values[i] < values[i - 1]) {
//                    throw IllegalArgumentException("RatioSet components not in increasing order")
//                }
//            }
        }
        reduce()
    }

    private fun reduce() {
        if (values.size == 0)
            return
        val base = values[0]
        val factors = factor(base)
        factors.forEach { f ->
            var isFactor = true
            if (f == 0)
                throw IllegalArgumentException("Divide by zero!")
            for (i in 1..values.lastIndex) {
                if (values[i] % f != 0) {
                    isFactor = false
                    break
                }
            }
            if (isFactor) {
                for (i in values.indices) {
                    values[i] /= f
                }
            }
        }
    }
}

fun factor(n: Int) : ArrayList<Int> {
    val list = ArrayList<Int>()
    val max = ceil(sqrt(n.toDouble())).toInt()
    for (i in 2..max) {
        if (i > 3 && factor(i).size > 1) {
            continue // not prime
        }
        if (n % i == 0) {
            list.add(i)
            val other = factor(n / i)
            list.addAll(other)
            break
        }
    }
    if (list.isEmpty())
        list.add(n)
    return list
}