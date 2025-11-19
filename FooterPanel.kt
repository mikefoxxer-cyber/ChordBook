import MessageType.PaintRequest
import javafx.scene.control.Label

class FooterPanel(private val label: Label) : MessageListener {
    init {
        subscribe(this, PaintRequest)
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest -> {
                if (!isJust())
                    label.text = ""
                else
                    chordChanged(curItem)
            }
            else -> {
                // ignore
            }
        }
    }

    private fun chordChanged(item: Item) {
        if (!isJust())
            return
        val ratios = item.factors
        val sb = StringBuilder()
        sb.append(" ")
        for (n in ratios) {
            sb.append(n.toString())
            sb.append(":")
        }
        sb.setLength(sb.length - 1)
        label.text = sb.toString()
    }
}