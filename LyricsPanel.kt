import MessageType.FileChange
import MessageType.PaintRequest
import Styles.Companion.hoverColor
import javafx.scene.canvas.Canvas
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import kotlin.math.max
import kotlin.math.min

class LyricsPanel(private val staffPanel: StaffPanel) : MessageListener {
    private var maxLines = -1
    private lateinit var pane: Pane
    private lateinit var canvas: Canvas
    private val lyrics = ArrayList<Pair<Long,Array<String>>>()
    private lateinit var myFont: Font

    init {
        subscribe(this, PaintRequest, FileChange)
    }

    private fun font(): Font {
        val f = Main.getNoteList().font
        val size = max(f.size - 2.0, 5.0)
        return Font.font(f.family, size)
    }

    fun paint() {
        if (!::myFont.isInitialized) {
            myFont = font()
        }
        if (!::canvas.isInitialized)
            return
        val leftX = staffPanel.leftX
        val r = canvas.parent.layoutBounds
        val midX = leftX + (r.width - leftX) / 2.0
        val gc = canvas.graphicsContext2D
        gc.font = myFont
        val text = Text("Test")
        text.font = gc.font
        val lineHeight = text.layoutBounds.height
        val lines = curItem.lyrics.keys.size
        maxLines = max(maxLines, lines)
        var x: Double
        var y = r.height - ((maxLines + 1) * lineHeight) - staffPanel.getTranslation().second
        gc.fill = Color.web("#efefef")
        gc.fillRect(0.0, y, r.width, r.height)
        y += lineHeight * 1.5
        gc.fill = Color.BLACK
//        gc.fillText("lyrics", r.minX + 5.0, r.minY + 20.0)
        if (lyrics.isEmpty())
            return
        val cursor = lyrics.withIndex().find {
            it.value.first == curItem.tick
        } ?: return // can't happen!
        val start = max(0, cursor.index - 3)
        val end = min(lyrics.lastIndex, cursor.index + 3)
        // compute total width
        var w = 0.0
        for (i in start .. end) {
            // width of one group
            val groupWidth = getGroupWidth(lyrics[i].second, gc.font)
            w += groupWidth + 2.0
        }
        // now draw all the strings, highlighting the ones at the cursor
        x = midX - w / 2.0
        val y0 = y
        for (i in start .. end) {
            y = y0
            if (i == cursor.index) {
                gc.fill = hoverColor
            } else {
                gc.fill = Color.web("#efefef")
            }
            val arr = lyrics[i].second
            val gw = getGroupWidth(arr, gc.font)
            gc.fillRect(x, y-lineHeight, gw, lineHeight * maxLines + lineHeight/2.0 )
            for (j in arr.indices) {
                if (i == cursor.index)
                    gc.fill = Color.WHITE
                else
                    gc.fill = Color.BLACK
                gc.fillText(arr[j], x + (gw - getWidth(arr[j], gc.font)) / 2.0, y)
                y += lineHeight
            }
            x += gw
        }
    }

    private fun getGroupWidth(arr: Array<String>, font: Font): Double {
        var w = 0.0
        for (s in arr) {
            w = max(w, getWidth(s, font))
        }
        return w
    }

    fun refresh() {
        paint()
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest -> {
                paint()
            }
            FileChange -> {
                val vpSet = HashSet<VoicePart>()
                for (item in items()) {
                    for (vp in item.lyrics.keys)
                        vpSet.add(vp)
                }
                lyrics.clear()
                val vpList = vpSet.toList().sorted()
                maxLines = vpList.size
                for (it in items().withIndex()) {
                    val item = it.value
                    val tick = item.tick
                    val arr = Array(maxLines) { "" }
                    val keys = item.lyrics.keys.toList().sorted()
                    for (i in keys.indices) {
                        val key = keys[i]
                        val j = vpList.indexOf(key)
                        val s = item.lyrics[key].toString()
                        arr[j] = s
                    }
                    val pair = Pair(tick, arr)
                    lyrics.add(pair)
                }
                refresh()
            }
            else -> {
                // ignore
            }
        }
    }

    fun setPane(p: Pane) {
        pane = p
    }

    fun setcanvas(canvas: Canvas) {
        this.canvas = canvas
    }
}