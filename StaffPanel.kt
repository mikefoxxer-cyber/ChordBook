import MessageType.*
import Step.*
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.BoundingBox
import javafx.geometry.Point2D
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import tornadofx.*
import tornadofx.FX.Companion.primaryStage
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Displays a grand staff with the notes of the current chord
 *
 */
class StaffPanel : DraggableCanvas(), MessageListener {
    private var bracketImage = loadBracketImage()
    private var origin = Point2D(26.0, 100.0)
    private var maestro = Font("Maestro", 24.0)
    private var noteY = arrayOf(0.0)
    private var noteOffsets = arrayOf(0.0)
    private var middleCy = 0.0
    var leftX = 0.0
    private val mouseTracker : EventHandler<MouseEvent>
    private var transX = 0.0
    private var transY = 0.0

    private var showing = -1

    init {
        subscribe(this, PaintRequest, ModeChange, FileChange)
        mouseTracker = EventHandler<MouseEvent> { event ->
            run {
                val pitches = curItem.pitches
                val minX = middleX() * scaleFactor - 20.0
                val maxX = minX + 40.0
                if (event.x > minX && event.x < maxX) {
                    if (pitches.isNotEmpty()) {
                        for (i in noteY.indices) {
                            if (abs(noteY[i] * scaleFactor - event.y) < 5.0) {
                                if (showing != i) {
                                    showing = i
                                    val vp = if (pitches[i].voicePart == null) "" else "${pitches[i].voicePart} "
                                    val str = vp + "${pitches[i]}(${pitches[i].n}) = " +
                                            String.format("%.3f", pitches[i].bent()) + " Hz"
                                    Main.log(str)
                                    showTooltip(str, event)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun pane(p: Pane) {
        pane = p
    }

    private fun showTooltip(str: String, event: MouseEvent) {
        val tt = Tooltip(str)
        tt.isAutoHide = true
        val x = event.screenX + 30.0
        val y = event.screenY
        tt.show(canvas.parent, x, y)
        Timer().schedule(3000) {
            runLater {
                tt.hide()
                showing = -1
            }
        }
    }

    override fun refresh() {
        showing = -1
        clear()
        show()
        Main.getLyricsPanel().paint()
    }

    private fun clear() {
        if (!canvasIsInitialized())
            return
        val gc = canvas.graphicsContext2D
        gc.fill = Color.WHITE
        val b = canvas.boundsInLocal
        gc.fillRect(0.0, 0.0, b.width, b.height)
    }

    private fun paint() {
        if (!canvasIsInitialized()) {
            canvas = Canvas(primaryStage.width, primaryStage.height)
            canvas.onMouseMoved = mouseTracker
            canvas.translateX = transX
            canvas.translateY = transY
            pane.children.add(canvas)
            addMouseDragHandlers()
            Main.setLyricCanvas(canvas)
        }
        clear()
        val gc = canvas.graphicsContext2D
        val b = pane.layoutBounds
        val r = BoundingBox( 0.0, 0.0, b.maxX, b.maxY)
        origin = Point2D(20.0,
                (r.height / 2.0 - 60.0).roundToInt().toDouble() / scaleFactor)
        gc.scale(scaleFactor, scaleFactor)
        gc.drawImage(bracketImage, origin.x - 16.0, origin.y - 2.0)
        gc.stroke = Color.BLACK
        gc.fill = Color.BLACK
        gc.font = Font("Maestro", 36.0)
        middleCy = origin.y + 50.0
        val x = origin.x
        var y = origin.y
        gc.fillText("\uF026", x + 10.0, y + 30.0)
        for (i in 0..4) {
            gc.strokeLine(x, y, (r.width - origin.x)/scaleFactor, y)
            y += 10.0
        }
        gc.fillText("\uF03F", x + 10.0, y + 30.0)
        y += 20.0
        for (i in 0..4) {
            gc.strokeLine(x, y, (r.width - origin.x)/scaleFactor, y)
            y += 10.0
        }
        gc.strokeLine(origin.x, origin.y, x, y - 10.0)
        // key signature
        var x0 = origin.x + 10.0
        var y0: Double
        gc.fill = Color.BLACK
        val key = Global.key()
        if (key < 0) { // flats
            val kind = AccidentalKind.flat
            val order = arrayOf (B, E, A, D, G, C, F)
            val octG = arrayOf(4, 5, 4, 5, 4, 5, 4)
            val octF = arrayOf(2, 3, 2, 3, 2, 3, 2)
            for (i in 0 until (key * -1)) {
                var p = Pitch(Note(order[i]).flatten()!!, octG[i])
                x0 = origin.x + 40.0 + i * 10
                y0 = middleCy - C4.distTo(p) * 5.0
                val w = getWidth(kind.getString(), maestro) + 2
                val m = kind.getString()
                gc.fillText(m, x0, y0)
                p = Pitch(Note(order[i]).flatten()!!, octF[i])
                y0 = middleCy - C4.distTo(p) * 5.0 + 10.0 // mind the gap!
                gc.fillText(m, x0, y0)
                x0 += w
            }
        } else if (key > 0) { // sharps
            val kind = AccidentalKind.sharp
            val order = arrayOf(F, C, G, D, A, E, B)
            val octG = arrayOf(5, 5, 5, 5, 4, 5, 4)
            val octF = arrayOf(3, 3, 3, 3, 2, 3, 2)
            for (i in 0 until key) {
                var p = Pitch(Note(order[i]).sharpen()!!, octG[i])
                x0 = origin.x + 40.0 + i * 10
                y0 = middleCy - C4.distTo(p) * 5.0
                val w = getWidth(kind.getString(), maestro) + 2
                val m = kind.getString()
                gc.fillText(m, x0, y0)
                p = Pitch(Note(order[i]).sharpen()!!, octF[i])
                y0 = middleCy -C4.distTo(p) * 5.0 + 10.0 // mind the gap!
                gc.fillText(m, x0, y0)
                x0 += w
            }
        } else {
            // key is C
            x0 += 20.0
        }
        // time signature
        val m = Global.score.currentMeasure()
        if (m != null && m.beats > 0) {
            val num = m.beats
            val den = m.beatType
            gc.font = Font("Maestro", 42.0)
            x0 += 20.0
            y0 = middleCy - 40.0
            var x1 = x0
            val w0 = getWidth(num.toString(), gc.font)
            val w1 = getWidth(den.toString(), gc.font)
            if (w0 > w1) {
                x1 += (w0 - w1) / 2.0
            } else {
                x0 += (w1 - w0) / 2.0
            }
            gc.fillText(num.toString(), x0, y0)
            y0 += 20.0
            gc.fillText(den.toString(), x1, y0)
            y0 += 50.0
            gc.fillText(num.toString(), x0, y0)
            y0 += 20.0
            gc.fillText(den.toString(), x1, y0)
        }
        leftX = x0
        gc.scale(1.0/scaleFactor, 1.0/scaleFactor)

    }

    private fun loadBracketImage() : Image {
        try {
            val url = javaClass.getResource("brace.jpg")
            return Image(url.toString())
        } catch (e: IOException) {
            System.err.println("Cannot load \"brace.jpg\" image file.")
            System.err.println(e.localizedMessage)
            Platform.exit()
            throw ErrorMessage("Can't happen")
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest, FileChange -> {
                show()
            }
            ModeChange -> {
                showing = -1
            }
            else -> {
                // ignore
            }
        }
    }

    private fun middleX() = leftX / scaleFactor + ((pane.layoutBounds.width - leftX) / scaleFactor) / 2.0

    private fun show() {
        val pitches = curItem.pitches
        paint()
        val gc = canvas.graphicsContext2D
        gc.fill = Color.BLACK
        gc.stroke = Color.BLACK
        if (pitches.isEmpty())
            return
        gc.scale(scaleFactor, scaleFactor)
        noteOffsets = Array(pitches.size) { 0.0 }
        val acc = Array<Accidental?>(pitches.size) { null }
        noteY = Array(pitches.size) { 0.0 }
        val x = middleX()
        var y: Double
        for (i in pitches.indices) {
            val p = pitches[i]
            y = middleCy - C4.distTo(p) * 5.0
            if (y > middleCy)
                y += 10.0 // adjust for gap between staves
            noteY[i] = y
        }
        // now, layout X coords of notes
        for (i in 1..pitches.lastIndex) {
            val d = noteY[i - 1] - noteY[i]
            if (d < 10) {
                // adjust upper note if lower note is in normal position
                if (noteOffsets[i-1]  == 0.0)
                    noteOffsets[i] = 12.0
                else
                    noteOffsets[i] = 0.0
            }
        }
        // now, layout X coords of accidentals
        for (i in pitches.indices) {
            val n = pitches[i].note
            var kind: AccidentalKind = n.ak()
            val key = Global.key()
            if (kind == AccidentalKind.none || kind == AccidentalKind.natural) {
                if (inKeySig(key,n.sharpen()) || inKeySig(key, n.flatten()))
                    kind = AccidentalKind.natural
            }
            if (inKeySig(key, n))
                kind = AccidentalKind.none
            y = noteY[i]
            val w = getWidth(kind.getString(), maestro) + 2
            val h = when (kind) {
                AccidentalKind.none -> 0.0
                AccidentalKind.doubleSharp -> 12.0
                else -> 24.0
            }
            acc[i] = Accidental(kind, 0.0, y, w, h)
        }
        layoutAccidentals(acc)
        // finally, draw the notes and accidentals
        for (i in pitches.indices) {
            drawNote(gc, i, x, pitches[i], acc[i])
        }
        gc.scale(1.0/scaleFactor, 1.0/scaleFactor)
    }

    private fun inKeySig(key: Int, n: Note?) =
        when (n?.toString() ?: "") {
            "Bb" -> key in -1 downTo -7
            "Eb" -> key in -2 downTo -7
            "Ab" -> key in -3 downTo -7
            "Db" -> key in -4 downTo -7
            "Gb" -> key in -5 downTo -7
            "Cb" -> key in -6 downTo -7
            "Fb" -> key in -7 downTo -7
            "F#" -> key in 1 .. 7
            "C#" -> key in 2 .. 7
            "G#" -> key in 3 .. 7
            "D#" -> key in 4 .. 7
            "A#" -> key in 5 .. 7
            "E#" -> key in 6 .. 7
            "B#" -> key in 7 .. 7
            else -> false
    }

    private fun drawNote(gc: GraphicsContext, i: Int, x: Double, p: Pitch, acc: Accidental?) {
        var y = noteY[i]
        val x0: Double
        val saved = gc.fill
        gc.fill = getColor(p)
        x0 = if (acc!!.r.height > 0.0) {
            val m = acc.kind.getString()
            val delta = getWidth(m, maestro) + 3.0
            gc.fillText(m, x - delta - acc.r.x - 2, y)
            x - delta - acc.r.x - 3
        } else {
            x - 3
        }
        gc.fillText("\uF077", x + noteOffsets[i], y) // whole note
        gc.fill = saved
        gc.stroke = Color.BLACK
        val x1: Double = x + noteOffsets[i] + getWidth("\uF077", maestro) + 8.0
        // draw leger lines, if needed
        when {
            y == middleCy -> {
                gc.strokeLine(x0, y, x1, y)
            }
            y < origin.y -> {
                while (y < origin.y) {
                    if ((origin.y - y).toInt() % 10 == 0)
                        gc.strokeLine(x0, y+0.5, x1, y+0.5)
                    y += 5.0
                }
            }
            y > origin.y + 110.0 -> {
                while (y > origin.y + 110.0) {
                    if ((y - origin.y).toInt() % 10 == 0)
                        gc.strokeLine(x0, y+0.5, x1, y+0.5)
                    y -= 5.0
                }
            }
        }
    }

    fun getTranslation(): Pair<Double, Double> {
        return Pair(canvas.translateX, canvas.translateY)
    }

    fun setTranslation(arr: Array<Double>) {
        setTranslation(arr[0], arr[1])
    }

    fun setTranslation(x: Double, y: Double) {
        transX = x
        transY = y
        if (canvasIsInitialized()) {
            canvas.translateX = x
            canvas.translateY = y
        }
    }

    override fun setFactor(d: Double) {
        scaleFactor = d
        runLater {
            refresh()
        }
    }
}

fun getWidth(s: String, font: Font) : Double {
    val text = Text(s)
    text.font = font
    return text.layoutBounds.width + 2.0
}

fun getColor(p: Pitch) : Color {
    val bend = p.bend
    if (!isJust() || bend == 0.0) {
        return Color.BLACK
    }
    var mag = (abs(bend) + 20.0) / 50.0
    mag = min(mag, 1.0)
    return if (bend < 0)
        Color.color(0.0, 0.0, mag) // blue
    else
        Color.color(mag, 0.0, 0.0) // red
}