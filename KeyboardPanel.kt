import Global.pitches
import MessageType.PaintRequest
import javafx.event.EventHandler
import javafx.geometry.BoundingBox
import javafx.geometry.Point2D
import javafx.geometry.VPos
import javafx.scene.canvas.Canvas
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import tornadofx.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule
import kotlin.math.abs

class KeyboardPanel : DraggableCanvas(true), MessageCreator, MessageListener {
    private var h = 1.0
    private var oldH = 1.0
    var lowNote = 0  // C0 is the lowest note we ever draw, MIDI note 12
    private val key = Array(128) { Rectangle(0.0, 0.0)}
    private val border = 5.0
    private val labelHeight = 20.0
    private val w = arrayOf(23, 24, 23, 24, 23, 23, 24) // white key width at bottom edge
    private val diff = arrayOf(0, -1, 5, -1, 10, 0, -1, 3, -1, 7, -1, 11, 0) // difference
                                // between left top and left bottom for white keys
    private val b = arrayOf(14, 14, 14, 14, 14, 13, 14, 13, 14, 13, 14, 13) // key width at top edge
    private val isBlack = arrayOf(false, true, false, true, false, false, true, false, true, false, true, false)
    private var middleCXPos = 0.0
    private var mouseDown: Point2D? = null
    private var lastDrag = -1
    private var kbdWidth: Int
//    private var dragStarted = -1
//    private var dragY = -1.0

    init {
        subscribe(this, PaintRequest)
        kbdWidth = 0
        var j = 0
        for (i in 12..127) {
            kbdWidth += if (isBlack[i%12]) 0 else w[j++%7]
        }
    }

    fun pane(p: Pane) {
        pane = p
        h = p.height
        oldH = h
        canvas = Canvas(kbdWidth.toDouble() + 2 * border, h)
        p.children.add(canvas)
        addMouseDragHandlers()
    }

    fun getTranslation() = canvas.translateX

    fun setTranslation(x: Double) {
        canvas.translateX = x
    }

    fun paint() {
        val pressed = Array<Boolean>(128) { false }
        for (p in curItem.pitches) {
            pressed[p.n] = true
        }
        val gc = canvas.graphicsContext2D
        val lh = labelHeight
        gc.fill = Color.web("#eeeeee")
        gc.fillRect(0.0, lh, canvas.width, canvas.height)
        gc.fill = Color.BLACK
        val a = canvas.boundsInParent

        val bounds = BoundingBox(a.minX, a.minY + lh, kbdWidth.toDouble(), a.height - lh)

        var n = border
        for (oct in 0..3) {
            for (i in w.indices) {
                n += w[i]
            }
           // n += w[0] / 2
            middleCXPos = n
        }

        // what note should be lowNote so that middleCXPos is near middle of screen?
        lowNote = 12 // C0 (for now)

        // outer rectangle
        gc.stroke = Color.BLACK
        gc.strokeRect(border, border + lh, kbdWidth.toDouble(), bounds.height - lh - 2 * border)

        var midi = lowNote
        var x = border
        val mid = (bounds.height - lh) / 2.0
        var i = lowNote
        var ii = i % 12
        // white keys
        while (i < 80) {
            gc.stroke = Color.BLACK
            gc.lineWidth = 1.0
            gc.strokeLine(x, border + lh, x, bounds.height - border)
            x += w[ii++]
            if ( ii >= w.size) {
                ii = 0
            }
            i++
        }
        // black keys
        x = border
        i = lowNote
        ii = i % 12
        while (i < 128) {
            if (isBlack[ii]) {
                gc.fillRect(x, border + lh, b[ii].toDouble(), mid)
            }
            if (midi < 128) {
                key[midi].x = x
                key[midi].y = border
                key[midi].height = mid
            }
            x += b[ii++]
            if (midi < 128) {
                key[midi].width = x - key[midi].x
            }
            if (ii >= b.size) {
                ii = 0
            }
            midi++
            i++
        }
        if (midi == 127) {
            key[midi].x = x
            key[midi].y = border + lh
            key[midi].width = bounds.width - border - x
            key[midi].height = mid
        }
        // mark pressed keys
        gc.fill = Color.RED
        for (j in pressed.indices) {
            if (pressed[j]) {
                val r = Rectangle(key[j].x+1.0, lh + key[j].y, key[j].width-2.0, key[j].height)
                if (j == 127)
                    r.width = 16.0
                gc.fillRect(r.x, r.y, r.width, r.height)
                val m = j % 12
                if (!isBlack[m]) {
                    x = r.x - diff[m]
                    var width = 22.0
                    if (m == 2 || m == 5 || m == 11) {
                        width++
                    }
                    if (j == 127) {
                        width = 18.0
                    }
                    gc.fillRect(x, lh + border + mid, width, bounds.height - mid - 2 * border - lh)
                }
            }
        }
        // middle C marker
        gc.fill = Color.BLACK
        val cx = middleCXPos + (w[0] -10)/2
        val cy = bounds.height - border - 18 * scaleFactor
        val s = 10.0
        gc.fillOval(cx, cy, s, s * scaleFactor)
        // labels
        val lgc = canvas.graphicsContext2D
        lgc.font = Font( "Arial", 10.0)
        lgc.fill = Color.web("#eeeeee")
        val b = canvas.boundsInLocal
        lgc.fillRect(0.0, 0.0, b.width, lh)
        lgc.textBaseline = VPos.TOP
        lgc.fill = Color.BLACK
        val item = curItem
        for (j in item.pitches.indices) {
            if (j <= item.labels.lastIndex && item.labels[j] != "") {
                lgc.fillText(item.labels[j], key[item.pitches[j].n].x, 2.0)
            }
            if (isJust() && j <= item.factors.lastIndex && item.factors[j] != 0) {
                lgc.fillText(item.factors[j].toString(), key[item.pitches[j].n].x, 12.0)
            }
        }
        canvas.transforms.clear()
        canvas.scaleX = scaleFactor
    }

    private fun pickNote(p: Point2D) = pickNote(p.x, p.y)

    private fun pickNote(x: Double, y: Double) : Int {
        var octave = 0
        if (x < border || x > canvas.width - border)
            return -1
        if (y < border || y > canvas.height - border)
            return -1
        var i = 0
        if (y < canvas.height / 2.0 + border) {
            // above the midpoint; may be black or white key
            var kx = x - border
            while (kx > 0) {
                kx -= b[i]
                if (kx > 0) {
                    i++
                    if (i >= b.size) {
                        i = 0
                        octave++
                    }
                }
            }
        } else {
            // below the midpoint; must be a white key
            var kx = x - border
            while (kx > 0) {
                kx -= w[i]
                if (kx > 0) {
                    i++
                    if (i > w.lastIndex) {
                        i = 0
                        octave++
                    }
                }
            }
            // adjust for the black keys
            i += when(i) {
                1    -> 1
                2,3  -> 2
                4    -> 3
                5    -> 4
                6    -> 5
                else -> 0
            }
        }
        return 12 + octave * 12 + i
    }

    override fun refresh() {
        paint()
    }

    private var tt: Tooltip? = null

    private fun showTooltip(str: String) {
        if (tt != null) {
            if (tt!!.text == str)
                return
            tt!!.hide()
        }
        tt = Tooltip(str)
        tt!!.isAutoHide = true
        val deltaX = if (mouseDown!!.x > canvas.width / 2.0) -80.0 else 30.0
        val x = canvas.localToScreen(mouseDown!!).x + deltaX
        val y = canvas.localToScreen(mouseDown!!).y
        tt!!.show(canvas.parent, x, y)
        Timer().schedule(3000) {
            runLater {
                tt!!.hide()
            }
        }
    }

    override fun drag(e: MouseEvent) {
        if (mouseDown == null)
            return // can't happen (?)
        // is this a horizontal or vertical drag?
        val deltaX = abs(e.x - mouseDown!!.x)
        val deltaY = abs(e.y - mouseDown!!.y)
        if (deltaY > deltaX) { // vertical
            val i = pickNote(mouseDown!!)
            val p = pitches.find { p -> p.n == i } ?: return
            val note = p.note
            val aliases = note.aliases()
            val h = canvas.height - labelHeight
            val dy = e.y - labelHeight
            val n = aliases.size
            var sel = ((dy / h) * n).toInt()
            if (sel > n)
                sel = n
            if (sel < 1)
                sel = 1
            sel = n - sel
            p.note = aliases[sel]
            val str = "${p.note}"
            showTooltip(str)
        } else {
            // horizontal drag
            val i = pickNote(e.x, e.y)
            if (i != lastDrag) {
                val pList = ArrayList<Pitch>(pitches)
                if (lastDrag >= 0) {
                    val p = pitches.find { p -> p.n == lastDrag }
                    pList.remove(p)
                }
                lastDrag = i
                mouseDown = Point2D(e.x, e.y)
                pList.add(Pitch(i))
                pList.sortBy { it.n }
                send(AnalysisRequestMessage(pList))
                return
            }
        }
        send(AnalysisRequestMessage(pitches))
    }

    override fun mouseDown(e: MouseEvent) {
        mouseDown = Point2D(e.x, e.y)
        lastDrag = pickNote(e.x, e.y)
    }

    override fun mouseUp(e: MouseEvent) {
        lastDrag = -1
        val orig = pickNote(mouseDown ?: Point2D.ZERO)
        val i = pickNote(e.x, e.y)
        if ( i < 0 ) {
            mouseDown = null
            return
        }
        if (i == orig) {
            // figure distance moved to determine if it's a click or a drag
            val d = mouseDown!!.distance(e.x, e.y)
            if (d >= 2.0) {
                mouseDown = null
                return
            } else {
                mouseDown = null
            }
        } else {
            mouseDown = null
            return
        }
        val p = pitches.find { p -> p.n == i }
        val pList = ArrayList<Pitch>(pitches)
        if (e.button == MouseButton.PRIMARY) {
            if (p == null) {
                pList.add(Pitch(i))
                pList.sortBy { it.n }
            } else {
                pList.remove(p)
            }
            send(AnalysisRequestMessage(pList))
        } else if (p != null && e.button == MouseButton.SECONDARY) {
            // popup enharmonic spelling menu
            val note = p.note
            val aliases = note.aliases()
            val menu = ContextMenu()
            aliases.forEach {
                val item = MenuItem(it.toString())
                item.setOnAction { _ ->
                    p.note = it
                    send(AnalysisRequestMessage(pitches))
                }
                menu.items.add(item)
            }
            menu.show(canvas, e.screenX, e.screenY)
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            PaintRequest -> {
                paint()
            }
            else -> {
                // ignore
            }
        }
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }

    override fun setFactor(d: Double) {
        scaleFactor = d
        paint()
    }

    fun setHeight(newValue: Double) {
        h = newValue
        canvas.height = h
    }
}

class DragContext {
    var mouseAnchorX = 0.0
    var mouseAnchorY = 0.0
    var initialTranslateX = 0.0
    var initialTranslateY = 0.0
    var scaleFactor = 1.0
}

open class DraggableCanvas(private val horizontalOnly: Boolean = false) {
    var dragging = false
    var scaleFactor = 1.0
    val dc = DragContext()
    open lateinit var pane: Pane
    open lateinit var canvas: Canvas

    fun canvasIsInitialized() = ::canvas.isInitialized

    fun addMouseDragHandlers() {
        canvas.onMousePressed = EventHandler {
            if (it.isShiftDown || it.isControlDown) {
                dragging = true
                dc.mouseAnchorX = it.x
                if (!horizontalOnly)
                    dc.mouseAnchorY = it.y
                dc.initialTranslateX = canvas.translateX
                dc.initialTranslateY = canvas.translateY
                dc.scaleFactor = scaleFactor
            } else {
                mouseDown(it)
            }
        }
        canvas.onMouseDragged = EventHandler {
            if (dragging) {
                if (it.isShiftDown) {
                    canvas.translateX = dc.initialTranslateX + it.x - dc.mouseAnchorX
                    if (!horizontalOnly)
                        canvas.translateY = dc.initialTranslateY + it.y - dc.mouseAnchorY
                    refresh()
                } else {
                    // change scale factor
                    val min = 0.2
                    val max = 10.0
                    val range = max - min
                    val delta = it.x - dc.mouseAnchorX
                    var newScale = dc.scaleFactor + (delta / pane.width) * range
                    if (newScale > max)
                        newScale = max
                    if (newScale < min)
                        newScale = min
                    setFactor(newScale)
                }
            } else {
                drag(it)
            }
        }
        canvas.onMouseReleased = EventHandler {
            dragging = false
            if (it.isControlDown) {
                Global.kbdScale = scaleFactor
            }
            mouseUp(it)
        }
    }

    open fun setFactor(d: Double) {
        scaleFactor = d
    }

    open fun mouseDown(e: MouseEvent) {

    }

    open fun mouseUp(e: MouseEvent) {

    }

    open fun drag(e: MouseEvent) {

    }

    open fun refresh() {

    }
}