import MessageType.PaintRequest
import javafx.scene.control.TextField
import java.util.*
import kotlin.collections.ArrayList

class NotesField(private val text: TextField) : MessageCreator, MessageListener {
    init {
        subscribe(this, PaintRequest)
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest -> {
                val list = curItem.pitches
                text.text = formatNotes(list)
                text.positionCaret(text.text.length)
            }
            else -> {
                // ignore
            }
        }
    }

    private fun formatNotes(p: List<*>) : String {
        val sb = StringBuilder()
        p.forEach {
            val pitch = it as Pitch
            sb.append(pitch.note.toString())
            sb.append(pitch.getOctave().toString())
            if (isJust() && pitch.userBend)
                sb.append(String.format("%+4.3f", pitch.bend))
            sb.append(" ")
        }
        return sb.toString()
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }
}

fun parseNotes(text: String) {
    var explicitOctave = false
    var underscores = 0
    var octave = 3

    val st = StringTokenizer(text, " ")
    val notes = ArrayList<Note>()
    val pArr = ArrayList<Pitch>()
    try {
        while (st.hasMoreTokens()) {
            var str = st.nextToken()
            if (str == "_") {
                underscores++
                continue
            }
            if (str.matches("[0-9]+".toRegex())) {
                octave = str.toInt()
                explicitOctave = true
                continue
            }
            if (str.matches("[a-gA-G](#|x|x#|b|bb|bbb)?[0-9]+.*".toRegex())) {
                explicitOctave = true
                var n = 0
                while (n < str.length) {
                    if ("0123456789".indexOf(str[n]) >= 0) {
                        break
                    }
                    n++
                }
                var m = n + 1
                if (m < str.length &&
                    "0123456789".indexOf(str[m]) >= 0
                ) {
                    m++
                }
                var oString: String?
                oString = if (m < str.length)
                    str.substring(n, m)
                else
                    str.substring(n)
                str = str.substring(0, n) + str.substring(m)
                octave = oString.toInt()
                // fall through
            }
            var n: Note?
            var cents = 0.0
            var bend = 0.0
            if (str.matches(".+[+-][0-9]+.*".toRegex())) {
                val bStr = str.split("+", "-")
                cents = bStr[1].toDouble()
                if (str.indexOf('-') > 0)
                    cents = -cents
                str = bStr[0]
                Global.isJust.value = true
            } else if (str.matches(".+[<>][0-9]+.*".toRegex())) {
                val bStr = str.split("<", ">")
                bend = bStr[1].toDouble()
                if (str.indexOf('<') > 0)
                    bend = -bend
                str = bStr[0]
                Global.isJust.value = true
            }
            n = noteLookup(str)
            notes.add(n)
            var p = Pitch(n, octave)
            if (cents != 0.0)
                p.setCents(cents, true)
            if (bend != 0.0)
                p.setPbus(bend.toInt(), true)
            if (!explicitOctave && pArr.size > 0) {
                val prev = pArr[pArr.size - 1]
                while (p.n <= prev.n) {
                    p = p.plusOctave(1)
                }
                if (underscores > 0 && prev.distTo(p) < 8) {
                    p = p.plusOctave(underscores)
                }
            }
            pArr.add(p)
            underscores = 0
            octave = p.getOctave()
            explicitOctave = false
        }
        if (pArr.size > 16) {
            Main.log("Too many notes! Limit is 16.")
            return
        }
        if (pArr.size == curItem.pitches.size) {
            for (i in pArr.indices) {
                val p = curItem.pitches[i]
                pArr[i].voicePart = p.voicePart
            }
        } else if (Global.isFileLoaded) {
            throw ErrorMessage("Cannot change number of notes in a chord; expecting ${curItem.pitches.size}")
        }
        Global.send(Global.eh, AnalysisRequestMessage(pArr), meToo = false)
    } catch (e: Exception) {
        Main.log(e.localizedMessage ?: e.javaClass.toString())
        throw e
    }
}
