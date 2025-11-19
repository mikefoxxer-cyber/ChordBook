import MessageType.*
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.ListView
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.FontWeight.BOLD
import javafx.scene.text.Text

// 5 Strings per pitch plus a canvas
// Voice  Note+Oct  Cents  Interval  Ratio (graph)
// make the graph canvas half the total width (right half)

class NoteGraphPanel : MessageListener {
    private lateinit var graphBox: HBox
    private lateinit var canvas: Canvas
    private lateinit var myFont: Font

    private fun font(): Font {
        val f = Main.getNoteList().font
        return Font.font(f.family, BOLD, f.size)
    }

    private var midX = 0.0

    init {
        subscribe(this, PaintRequest)
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest -> {
                checkCanvas()
                paint()
            }
            else -> {
                // ignore
            }
        }
    }

    private fun checkCanvas() {
        if (!this::canvas.isInitialized) {
            graphBox = Main.getGraphBox()
            canvas = Main.getGraphCanvas()
            canvas.height = graphBox.height
            canvas.width = graphBox.width
            graphBox.widthProperty().addListener { _, oldValue, newValue ->
                if (oldValue != newValue) {
                    canvas.width = newValue as Double
                    paint()
                }
            }
            graphBox.heightProperty().addListener { _, oldValue, newValue ->
                if (oldValue != newValue) {
                    canvas.height = newValue as Double
                    paint()
                }
            }
        }
    }

    private fun paint() {
        if (!::myFont.isInitialized)  {
            myFont = font()
        }
        val item = curItem
        val pitches = item.pitches
        val factors = item.factors
        checkCanvas()
        val gc = canvas.graphicsContext2D
        gc.font = myFont
        val rowH = getHeight(myFont) + 2.0
        canvas.height = pitches.size * rowH
        val r = canvas.layoutBounds
        midX = r.width / 2.0
        gc.fill = Color.web("#dddddd")
        gc.fillRect(0.0, 0.0, midX, r.height)
        gc.fill = Color.WHITE
        gc.fillRect(midX, 0.0, r.width, r.height)
        gc.stroke = Color.BLACK
        gc.strokeLine(midX, 0.0, midX, r.height)
        // draw the boxes
        // first the horizontal lines
        for (i in 1 until pitches.size) {
            gc.strokeLine(0.0, i * rowH, midX, i * rowH)
        }
        // now the vertical lines
        val w = r.width / 7
        val cellW = arrayListOf(0.0, w, w, w, w)
        val sum = cellW.sum()
        val factor = (midX - VoiceParts.partNamesWidth(gc.font)) / sum
        for (i in  1 .. cellW.lastIndex) {
            cellW[i] *= factor
        }
        cellW[0] = VoiceParts.partNamesWidth(gc.font)
        for (i in 0..4) {
            var x = 0.0
            for (j in 0 .. i)
                x += cellW[j]
            gc.strokeLine(x, 0.0, x, r.height)
        }
        // draw the 0-cents line in red
        gc.stroke = Color.RED
        gc.strokeLine(midX * 1.5, 0.0, midX * 1.5, r.height)
        gc.stroke = Color.BLACK
        gc.fill = Color.BLACK
        // fill in the data, bottom to top
        for (i in pitches.indices) {
            val p = pitches[i]
            val row = pitches.size - i
            var x = 3.0
            val y = row * rowH - 4.0
            var s = if (p.voicePart == null) "($i)" else "${p.voicePart}"
            gc.fillText(s, x, y)
            x += cellW[0]
            s = "${p.note}${p.getOctave()}"
            gc.fillText(s, x, y)
            x += cellW[1]
            s = if (!isJust() || p.bend == 0.0) "" else String.format("%+.2f", p.bend)
            gc.fillText(s, x, y)
            x += cellW[2]
            s = intervalOf(i)
            gc.fillText(s, x, y)
            x += cellW[3]
            s = if (isJust() && factors.isNotEmpty()) "${factors[i]}" else ""
            gc.fillText(s, x, y)
            if (isJust()) {
                x = midX * 1.5 // middle of graph
                x += (p.bend / 200.0) * midX
                gc.strokeLine(x, y - rowH, x, y)
            }
        }
    }

    private fun getHeight(font: Font): Double {
        val text = Text("X")
        text.font = font
        return text.layoutBounds.height + 2.0
    }

    private fun intervalOf(i: Int): String {
        if (i > curItem.labels.lastIndex)
            return ""
        return curItem.labels[i]
    }

//    private fun refresh() {
//        val clone1 = ArrayList<Pitch>()
//        clone1.addAll(pitches)
//        val clone2 = ArrayList<Interval>()
//        clone2.addAll(intervals)
//        show(clone1, clone2, ratios)
//    }
}