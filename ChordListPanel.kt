import MessageType.*
import javafx.scene.control.ListView
import tornadofx.*

class ChordListPanel(private val view: ListView<ChordInstance>) : MessageListener {
    init {
        subscribe(this, PaintRequest, ChordListChange)
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            ChordInstanceChange -> {
                val cc = msg as ChordInstanceChangeMessage
                if (cc.newVal != null) {
                    for (i in view.items.indices) {
                        if (view.items[i].chord == cc.newVal.chord) {
                            view.selectionModel.select(i)
                            break
                        }
                    }
                }
            }
            PaintRequest, ChordListChange -> {
                runLater {
                    view.items.clear()
                    curItem.chordList.forEach {
                        view.items.add(it)
                    }
                    if (curItem.chordList.isNotEmpty()) {
                        val n = if (curItem.chosenChord < 0) 0 else curItem.chosenChord
                        view.selectionModel.select(n)
                        Main.setChordSym(view.items[n])
                    }
                }
            }
            else -> {
                // ignore
            }
        }
    }
}