import MessageType.*
import javafx.scene.control.TextField

class ChordSymbolField(private val field: TextField) : MessageCreator, MessageListener {
    private var root = ""
    private var type = ""
    private var inv = 0
    private var bass = ""

    private fun iStr(i: Int) = if (i == 0)
        ""
    else
        " (${inversionName(i)} inversion)"

    private fun bStr() = if (bass == "")
        ""
    else
        " / $bass"

    init {
        subscribe(this, RootChange, ChordTypeChange, InversionChange, BassChange, PaintRequest)
    }

    private fun parse() {
        if (root == "")
            root = "C"

        field.text = "$root $type ${iStr(inv)}${bStr()}"

        parse(field.text)
    }

    private fun format(ci: ChordInstance?) : String {
        if (ci == null || ci.chord === OneNote || ci.chord === NoChord)
            return ""
        return "${ci.root} ${ci.chord.suffix} ${iStr(ci.inv)}"
    }

    fun parse(text: String) {
        try {
            val p = ChordSymbolParser()
            val res: Boolean
            try {
                res = p.parse(text)
            } catch (e: Exception) {
                Main.log(e.localizedMessage)
                return
            } catch (err: TokenMgrError) {
                Main.log(err.localizedMessage)
                return
            }
            val ci = p.chordInstance()
            if (!res || ci == null) {
                Main.log("Unrecognized: $text")
            } else {
                send(ChordInstanceChangeMessage(ci))
                send(PaintRequestMessage())
            }
        } catch (e: Exception) {
            if (e.localizedMessage == null)
                Main.log("Null exception message")
            else
                Main.log(e.localizedMessage)
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            RootChange -> {
                root = (msg as RootChangeMessage).newVal.toString()
                parse()
            }
            ChordTypeChange -> {
                type = (msg as ChordTypeChangeMessage).newVal?.suffix ?: ""
                parse()
            }
            InversionChange -> {
                inv = (msg as InversionChangeMessage).newVal
                parse()
            }
            BassChange -> {
                bass = (msg as BassChangeMessage).newVal?.toString() ?: ""
                parse()
            }
            PaintRequest -> {
                updateSubfields(Global.chordInstance)
            }
            else -> {
                // ignore
            }
        }
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }

    fun updateSubfields(ci: ChordInstance?) {
        if (ci == null || ci.chord === NoChord) {
            root = ""
            type = ""
            inv = 0
            bass = ""
        } else {
            root = ci.root.toString()
            type = ci.chord.suffix
            inv = ci.inv
            bass = ci.bass?.toString() ?: ""
        }

        field.text = format(ci)
    }
//
//    private fun chordChanged(ci: ChordInstance?) {
//        when {
//            ci == null -> {
//                root = ""
//                type = ""
//                inv = 0
//            }
//            ci.chord.isInterval -> {
//                root = ci.root.toString()
//                type = intervalNameOf(ci.chord)
//                inv = 0
//            }
//            else -> {
//                root = ci.root.toString()
//                type = ci.chord.suffix
//                inv = ci.inv
//            }
//        }
//        field.text = "$root $type ${iStr()}"
//    }
}
