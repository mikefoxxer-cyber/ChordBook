import Global.eh
import Global.send
import VoiceParts.allVPs
import javafx.event.EventHandler
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.text.Font
import tornadofx.runLater
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.zip.ZipFile
import javax.xml.stream.*
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent
import kotlin.math.max

object VoiceParts {
    var nextVoicePartNum = 0
        get() {
            return field++
        }

    val allVPs = ArrayList<VoicePart>()

    var nameMap = HashMap<VoicePart, String>()

    fun partNamesWidth(font: Font) : Double {
        var w = getWidth("(0)", font)
        for (vp in allVPs) {
            val nw = getWidth(vp.toString(), font)
            w = max(w, nw)
        }
        return w + 6.0
    }

    fun reset() {
        nextVoicePartNum = 0
        allVPs.clear()
    }

    fun get(voice: Int, part: Int) : VoicePart {
        for(vp in allVPs) {
            if (vp.voice == voice && vp.part == part)
                return vp
        }
        var vp2: VoicePart? = null
        for (vp in allVPs) {
            if (vp.part == part) {
                vp2 = vp
                break
            }
        }
        if (vp2 != null) {
            val vp = VoicePart(voice, part)
            vp.id = vp2.id
            val name = nameMap[vp2]
            if (name?.matches(".* .*".toRegex()) == true) {
                val arr = name.split("  *".toRegex())
                nameMap[vp2] = arr[0]
                nameMap[vp] = arr[1]
            }
            return vp
        }
        return VoicePart(voice, part)
    }

    fun get(id: String) : VoicePart? {
        allVPs.forEach { vp ->
            if (vp.id == id)
                return vp
        }
        return null
    }

    fun populateMuteMenu(menu: Menu) {
        menu.items.clear()
        allVPs.forEach { vp ->
            if (vp.voice >= 0) {
                val mi = CheckMenuItem(vp.toString())
                mi.userData = vp
                mi.isSelected = vp.muted
                mi.onAction = EventHandler {
                    run {
                        vp.muted = (it.target as CheckMenuItem).isSelected
                        runLater {
                            send(eh, MuteRequestMessage(vp), false)
                        }
                    }
                }
                menu.items.add(mi)
            }
        }
        menu.items.sortBy { i -> i.userData as VoicePart }
        val mi = MenuItem("None")
        mi.onAction = EventHandler {
            run {
                for (p in allVPs) {
                    p.muted = false
                }
                for (item in menu.items) {
                    when (item) {
                        is CheckMenuItem -> {
                            item.isSelected = false
                        }
                    }
                }
                send(eh, MuteRequestMessage(null), true)
            }
        }
        menu.items.add(mi)
    }

    fun populateSoloMenu(menu: Menu) {
        menu.items.clear()
        allVPs.forEach { vp ->
            if (vp.voice >= 0) {
                val mi = CheckMenuItem(vp.toString())
                mi.userData = vp
                mi.isSelected = vp.soloed
                mi.onAction = EventHandler {
                    run {
                        vp.soloed = (it.target as CheckMenuItem).isSelected
                        runLater {
                            send(eh, SoloRequestMessage(vp), false)
                        }
                    }
                }
                menu.items.add(mi)
            }
        }
        menu.items.sortBy { i -> i.userData as VoicePart }
        val mi = MenuItem("None")
        mi.onAction = EventHandler {
            run {
                for (p in allVPs) {
                    p.soloed = false
                }
                for (item in menu.items) {
                    when (item) {
                        is CheckMenuItem -> {
                            item.isSelected = false
                        }
                    }
                }
                send(eh, SoloRequestMessage(null), true)
            }
        }
        menu.items.add(mi)
    }

    fun muted(): String {
        val sb = StringBuilder()
        for (vp in allVPs) {
            if (vp.voice > 0) {
                if (vp.muted)
                    sb.append("${vp.shortName} ")
            }
        }
        return sb.toString().trim()
    }

    fun soloed(): String {
        val sb = StringBuilder()
        for (vp in allVPs) {
            if (vp.voice > 0) {
                if (vp.soloed)
                    sb.append("${vp.shortName} ")
            }
        }
        return sb.toString().trim()
    }

    fun setMuted(v: String) {
        val arr = v.split(" ")
        for (vp in allVPs) {
            vp.muted = arr.contains(vp.shortName)
            send(eh, MuteRequestMessage(vp), true)
        }
    }

    fun setSoloed(v: String) {
        val arr = v.split(" ")
        for (vp in allVPs) {
            vp.soloed = arr.contains(vp.shortName)
            send(eh, SoloRequestMessage(vp), true)
        }
    }
}

data class VoicePart(var voice: Int = 0, var part: Int = 0) : Comparable<VoicePart>, MessageCreator {
    val shortName: String?
        get () {
            return "($part/$voice)"
        }
    val num = VoiceParts.nextVoicePartNum
    var id: String? = null
    val name: String?
        get () {
            return VoiceParts.nameMap[this]
        }
    var muted = false
    var soloed = false

    init {
        allVPs.add(this)
    }

    override fun hashCode() = num.hashCode()

    override fun send(msg: Message, meToo: Boolean) {
        send(this, msg, meToo)
    }

    override fun equals(other: Any?): Boolean {
        return if (other is VoicePart) {
            (voice == other.voice && part == other.part)
        } else {
            false
        }
    }

    override fun toString(): String {
        val s = ""
        return "${name?:s}($part/$voice)"
    }

    override fun compareTo(other: VoicePart): Int {
        return (this.part * 100 + this.voice) - (other.part * 100 + other.voice)
    }
}

class Score(var file: File? = null) : MessageCreator {
    private lateinit var partWise: Element
    private lateinit var partList: Element
    private var infoElement: Element? = null
    private var start: XMLEvent? = null
    private var dtd: XMLEvent? = null
    private var root: Element? = null
    private val parts = ArrayList<Element>()
    private val divData = DivData()
    val measures = ArrayList<MeasureData>()
    private var chordSeq: ChordSeq? = null
    lateinit var lyricsMap: LyricsMap

    private lateinit var reader: XMLEventReader

    fun open(f: File) {
        file = f
        try {
            if (file?.extension == "mxl") {
                ZipFile(file).use { zip ->
                    var path: String? = null
                    // find and read the container.xml file to discover which .musicxml file is the root
                    zip.entries().asSequence().find { it.name == "META-INF/container.xml" }.let { entry ->
                            val rdr = zip.getInputStream(entry)
                            val factory = XMLInputFactory.newInstance()
                            factory.setProperty(XMLInputFactory.IS_VALIDATING, false)
                            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
                            val reader = factory.createXMLEventReader(rdr)
                            while (path == null) {
                                val next = reader.peek() ?: break
                                when {
                                    next.isStartElement -> {
                                        val e = reader.nextEvent() as StartElement
                                        if (e.name.toString() == "rootfile") {
                                            e.attributes.asSequence().find { (it as Attribute).name.toString() == "full-path" }.let {
                                                path = (it as Attribute).value
                                            }
                                        }
                                    }
                                }
                                reader.nextEvent() // ignore
                            }
                        }
                        if (path != null) {
                            zip.entries().asSequence().find { it.name == path }.let { zFile ->
                                var rdr = InputStreamReader(zip.getInputStream(zFile), "UTF8")
                                var n = bomLength(rdr)
                                rdr = InputStreamReader(zip.getInputStream(zFile), "UTF8")
                                while (n > 0) {
                                    n--
                                    rdr.read()
                                }
                                process(rdr)
                            }
                        }
                     }
            } else if (file != null) {
                var fis = FileInputStream(file!!)
                var n = bomLength(InputStreamReader(fis, "UTF8"))
                fis.close()
                fis = FileInputStream(file!!)
                val rdr = InputStreamReader(fis, "UTF8")
                while(n > 0) {
                    n--
                    rdr.read()
                }
                process(rdr)
            }
        } catch (e: FileNotFoundException) {
            Main.log(e.localizedMessage)
        } catch (e: XMLStreamException) {
            Main.log(e.localizedMessage)
        }
        send(FileChangeMessage())
    }

    private fun bomLength(rdr: InputStreamReader) : Int {
        // skip BOM mark, if any
        val buf = charArrayOf(' ', ' ', ' ', ' ')
        var n = 0
        val result = rdr.read(buf)
        if (result < buf.size) {
            throw ErrorMessage("Short read: $result bytes")
        }
        while (buf[n] != '<')
            n++
        return n
    }

    private fun process(rdr: InputStreamReader) {
        divData.reset()
        VoiceParts.reset()
        measures.clear()
        parts.clear()
        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.IS_VALIDATING, false)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        reader = factory.createXMLEventReader(rdr)
        root = parse()
        traverse(root)
        divData.adjustForSwing()
        reconcile()
        chordSeq = createSequence(measures)
        infoElement?.let { processInfo(it) }
    }

    private fun traverse(element: Element?) {
        var e: Element? = element
        while (e != null) {
            processScore(e)
            e = e.sibling
        }
    }

    private fun processInfo(element: Element) {
        for (attr in element.attributes) {
            when (attr.first) {
                "options" -> {
                    Global.parseOptionString(attr.second)
                }
                "tune-to" -> {
                    val sb = StringBuilder()
                    val arr = attr.second.split(";")
                    for (s in arr) {
                        if (s.length < 3)
                            continue
                        val a = s.split(":")
                        val tick = a[0].trim().toLong()
                        val m = items().find { it.isAtTick(tick) }
                        if (m == null) {
                            Main.log("Cannot locate chord at tick $tick")
                        } else {
                            sb.append("${m.mbt()}${a[1]} ")
                        }
                    }
                    Main.parseTuneTo(sb.toString(), setField = true)
                }
                "chord-sequence" -> {
                    val arr = attr.second.split(";")
                    for (s in arr) {
                        if (s.length < 3)
                            continue
                        val t = s.substring(0, s.indexOf(' '))
                        val c = s.substring(t.length + 1)
                        val tick = t.toLong()
//                        Main.log("chord-seq: $t -> $c")
                        val it = items().find { it.tick == tick }
                        if (it == null) {
                            Main.log("Cannot locate item at tick $tick")
                        } else {
                            var found = false
                            for (i in it.chordList.withIndex()) {
                                if (i.value.toString() == c) {
                                    it.chosenChord = i.index
                                    send(ChordIndexChangeMessage(it))
                                    found = true
                                    break
                                }
                            }
                            if (!found) {
                                Main.log("No such chord in chord list at tick $tick: $c")
                            }
                        }
                    }
                }
                "alt-tuning" -> {}
                "pitch-bends" -> {}
            }
        }
    }

    private fun processScore(element: Element) {
        val name = element.name
        if (name == "score-partwise") {
            partWise = element
            processParts(element.child)
        } // else ignore subtree
    }

    private fun reconcile() {
        // walk over the measure and note data, setting start values
        // and lengths in terms of ticks, now that we know all the
        // possible values of "divisions"
        var now = 0L
        val pCursor = arrayListOf<Int>()
        measures.forEach { m ->
            m.start = now
            var length = 0
            for (i in pCursor.indices) {
                pCursor[i] = 0
            }
            m.notes.forEach { n ->
                val part = n.part
                val voice = n.voice
                val vp = VoiceParts.get(voice, part)
                if (vp.num >= pCursor.size) {
                    for (i in pCursor.size .. vp.num)
                        pCursor.add(0)
                }
                val factor = divData.factor(vp)
                n.startTime = n.divStart * factor
                n.length = n.divLength * factor
                pCursor[vp.num] = max(pCursor[vp.num], (n.startTime + n.length))
                if (m.beats > 0 && pCursor[vp.num] > m.beats * divData.ticksPerBeat(m.beatType))
                    Main.log("Too many ticks in measure ${m.number}")
            }
            for (i in pCursor.indices) {
                if (pCursor[i] > length)
                    length = pCursor[i]
            }
            m.length = length
            if (m.beatType < 0)
                m.beatType = 4
            val beatLength = (divData.ticksPerQuarter * 4) / m.beatType
            m.actualBeats = length.toDouble() / beatLength
            now += length
        }
    }

    private fun processPartList(element: Element) {
        var e: Element? = element.child
        while (e != null) {
            val name = e.name
            if (name == "score-part") {
                var vp: VoicePart? = null
                val id = e.getNamedAttribute("id")
                if (id != null) {
                    vp = VoiceParts.get(id)
                    if (vp == null) {
                        vp = VoicePart()
                        vp.id = id
                        if (id.matches("P.-V.".toRegex())) {
                            vp.part = id.substring(1,2).toInt()
                            vp.voice = id.substring(4,5).toInt()
                        } else {
                            vp.part = id.substring(1).toIntOrNull() ?: -1
                            vp.voice = 1
                        }
                    }
                }
                var child = e.child
                while (child != null) {
                    if (child.name == "part-name") {
                        if (vp != null) {
                            val pn = child.value.replace('\n', ' ')
                            VoiceParts.nameMap[vp] = pn
                        }
                    }
                    child = child.sibling
                }
            }
            e = e.sibling
        }
    }

    private fun processParts(element: Element?) {
        var e = element
        while (e != null) {
            when (e.name) {
                "part" -> {
                    parts.add(e) // remember root of each part
                    processPart(e)
                }
                "part-list" -> {
                    partList = e
                    processPartList(e)
                }
                "other-technical" -> {
                    infoElement = e
                } // else ignore subtree
            } // else ignore subtree
            e = e.sibling
        }
    }

    private fun processPart(element: Element) {
        var voicePart: VoicePart? = null
        var measureNumber = -1
        val partId = element.getNamedAttribute("id")
        if (partId != null) {
            voicePart = VoiceParts.get(partId)
        }
        var child = element.child
        while (child != null) {
            val name = child.name
            if (name == "measure") {
                val mNum = child.getNamedAttribute("number")
                if (mNum != null) {
                    measureNumber = mNum.toInt()
                }
                processMusicData(child.child, voicePart!!, measureNumber)
            }
            child = child.sibling
        }
    }

    fun measureNumber(num: Int): MeasureData? {
        return measures.find { it.number == num }
    }

    // find or create measure number "num"
    private fun findMeasure(num: Int) : MeasureData {
        measures.forEach {
            if (it.number == num)
                return it
        }
        val measure = MeasureData(num)
        measures.add(measure)
        return measure
    }

    private fun processMusicData(elem: Element?, voicePart: VoicePart, mNum: Int) {
        val measure = findMeasure(mNum)
        val firstMeasureNumber = measures[0].number
        val prevMeas = if (mNum > firstMeasureNumber) findMeasure(mNum - 1) else null
        if (prevMeas != null) {
            if (measure.beatType == -1 && prevMeas.beatType > 0) {
                measure.beatType = prevMeas.beatType
                measure.beats = prevMeas.beats
            }
        }
        var now = 0 // measure-relative, in "divisions" (part-specific)
        var delta = 0
        var lastNote: NoteData? = null
        var element = elem
        while (element != null) {
            when (element.name) {
                "barline" -> {
                    var e = element.child
                    while (e != null) {
                        val s = e.name
                        if (s == "repeat") {
                            for (pair in e.attributes) {
                                if (pair.first == "direction") {
                                    if (pair.second == "forward") {
                                        measure.repeatType = RepeatType.Forward
                                    } else {
                                        measure.repeatType = RepeatType.Backward
                                    }
                                }
                            }
                        } else if (s == "ending") {
                            val arr = e.getNamedAttribute("number")!!.split(",")
                            for (item in arr) {
                                val number = item.trim().toInt()
                                if (!measure.endings.contains(number))
                                    measure.endings.add(number)
                            }
                            val type = e.getNamedAttribute("type")
                            if (type == "start") {
                                measure.endingType = EndingType.Start
                            } else {
                                if (measure.endingType == EndingType.Start) {
                                    measure.endingType = EndingType.StartStop
                                } else {
                                    measure.endingType = EndingType.Stop
                                }
                            }
                        }
                        e = e.sibling
                    }
                }
                "note" -> {
                    val note = NoteData()
                    var number = 0
                    var text = ""
                    var syllabic = Syllable.single
                    var extend = ""
                    note.vp = voicePart
                    note.divStart = now
                    var e = element.child
                    while (e != null) {
                        when (e.name) {
                            "pitch" -> {
                                var t = e.child
                                while (t != null) {
                                    when (t.name) {
                                        "step" -> {
                                            note.degree = Step.valueOf(t.value.trim())
                                        }
                                        "alter" -> {
                                            val a = t.value.trim().toInt()
                                            if (a in -3..3)
                                                note.alter = t.value.trim().toInt()
                                            else
                                                Main.log(
                                                    "Bad value for \"alter\" ($a) " +
                                                            "at measure $mNum in part $voicePart"
                                                )
                                        }
                                        "octave" -> {
                                            note.octave = t.value.trim().toInt()
                                        }
                                    }
                                    t = t.sibling
                                }
                            }
                            "chord" -> {
                                now = lastNote!!.divStart
                                note.divStart = now
                            }
                            "duration" -> {
                                note.divLength = e.value.trim().toInt()
                                delta = note.divLength
                            }
                            "voice" -> {
                                val voice = e.value.trim().toInt()
                                val vp = VoiceParts.get(voice,voicePart.part)
                                note.vp = vp
                            }
                            "tie" -> {
                                val attr = e.getNamedAttribute("type")
                                if (attr == "start")
                                    note.tieFrom = true
                                else if (attr == "stop")
                                    note.tieTo = true
                            }
                            "notations" -> {
                                var t = e.child
                                while (t != null) {
                                    if (t.name == "fermata") {
                                        note.fermata = true
                                    }
                                    t = t.sibling
                                }
                            }
                            "lyric" -> {
                                var t = e.child
                                number = e.getNamedAttribute("number")?.toIntOrNull() ?: 1
                                while (t != null) {
                                    when (t.name) {
                                        "text" -> {
                                            text = t.value
                                        }
                                        "syllabic" -> {
                                            syllabic = Syllable.valueOf(t.value)
                                        }
                                        "extend" -> {
                                            val v = t.getNamedAttribute("type")
                                            extend = v ?: ""
                                            if (v == "stop") {
                                                text = "<>"
                                                syllabic = Syllable.single
                                            }
                                        }
                                    }
                                    t = t.sibling
                                }
                            }
                        }
                        e = e.sibling
                        if (text != "" || extend != "") {
                            note.lyric = Lyric(text, number, syllabic, extend)
                            text = ""
                        }
                    }
                    now += delta
                    delta = 0
                    lastNote = note
                    measure.notes.add(note)
                }
                "backup" -> {
                    val e = element.child
                    if (e?.name == "duration") {
                        now -= e.value.trim().toInt()
                    }
                }
                "forward" -> {
                    val e = element.child
                    if (e?.name == "duration") {
                        now += e.value.trim().toInt()
                    }
                }
                "sound" -> {
                    val attr = element.getNamedAttribute("tempo")
                    if (attr != null) {
                        measure.tempo = attr.trim().toDouble()
                    }
                }
                "attributes" -> {
                    var e = element.child
                    while (e != null) {
                        when (e.name) {
                            "divisions" -> {
                                updateTpQ(divData, voicePart, e.value.trim().toInt())
                            }
                            "key" -> {
                                var el = e.child
                                while (el != null) {
                                    if (el.name == "fifths") {
                                        measure.fifths = el.value.trim().toInt()
                                    } else if (el.name == "mode") {
                                        measure.mode = e.value.trim()
                                    }
                                    el = el.sibling
                                }
                            }
                            "time" -> {
                                var el = e.child
                                var beats = -1
                                var beatType = -1
                                while (el != null) {
                                    if (el.name == "beats") {
                                        beats = el.value.trim().toInt()
                                    } else if (el.name == "beat-type") {
                                        beatType = el.value.trim().toInt()
                                    }
                                    el = el.sibling
                                }
                                if (beats > 0 && beatType > 0) {
                                    measure.beats = beats
                                    measure.beatType = beatType
                                }
                            }
                        }
                        e = e.sibling
                    }
                }
                "direction" -> {
                    var e = element.child
                    while (e != null) {
                        if (e.name == "sound") {
                            for (attr in e.attributes) {
                                measure.direction.add(attr)
                            }
                        }
                        e = e.sibling
                    }
                }
            }
            element = element.sibling
        }
    }

    private fun parse() : Element? {
        var element: Element? = null
        try {
            while (true) {
                val next = reader.peek() ?: break
                when {
                    next.isStartElement -> {
                        element = parseElement()
                    }
                    next.isEndDocument -> {
                        return element
                    }
                    next.isStartDocument -> {
                        start = next
                        reader.next()
                    }
                    next.isProcessingInstruction -> {
                        dtd = next
                        reader.next()
                    }
                    else -> {
                        reader.next() // ignore
                    }
                }
            }
        } catch (e: XMLStreamException) {
            Main.log(e.localizedMessage)
        }
        return element
    }

    private fun parseElement() : Element? {
        val e = reader.nextEvent() as StartElement
        val element = Element(e.name)
        e.attributes.forEach {
            val attr = it as Attribute
            val name = attr.name.toString()
            val value = attr.value.trim()
            element.setAttribute(name, value)
        }
        var next = reader.peek()
        while (!next.isEndElement) {
            when {
                next.isCharacters -> {
                    element.append(reader.nextEvent().asCharacters())
                }
                next.isStartElement -> {
                    val child = parseElement()!!
                    element.addChild(child)
                }
                else -> {
                    reader.nextEvent() // ignore
                }
            }
            next = reader.peek()
        }
        if (next.isEndElement) {
            val end = reader.nextEvent() as EndElement
            if (end.name.toString() != element.name) {
                throw XMLStreamException("out of sync!")
            }
        }
        return element
    }

    private fun createSequence(measures: ArrayList<MeasureData>) : ChordSeq {
        val seq = ChordSeq()
        measures.forEach { m ->
            val times = m.getTimes()
            for (tick in times) {
                val item = extractItem(m, tick)
                seq.insert(item)
                Global.analyzer.analyzeItem(item, fire = false)
            }
        }
        for (it in seq.list.withIndex()) {
            val item = it.value
            if (item.pitches.size == 0) {
                if (item.length == 0) {
                    for (i in it.index + 1 until seq.list.size) {
                        val other = seq.list[i]
                        if (other.tick > item.tick) {
                            item.length = (other.tick - item.tick).toInt()
                            break
                        }
                    }
                }
            }
        }
        return seq
    }

    // Create one Item for each chord change
    private fun extractItem(m: MeasureData, tick: Int) : Item {
        val item = Item(m.start + tick.toLong())
        item.measure = m
        val map = HashMap<VoicePart, Pitch>()
        for (nd in m.notes) {
            if (nd.degree == Step.Rest) {
                continue
            }
            var vp = VoiceParts.get(nd.voice, nd.part)
            val start = m.getStartTime(nd)
            val dur = nd.length
            if (start > tick) {
                continue // too late a start
            }
            if (start + dur <= tick) {
                continue // too early a finish
            }
            val p = nd.pitch ?: continue  // a rest
            val existing = map[vp]
            if (p.n == existing?.n)
                continue // a duplicate
            if (existing != null) {
                // already have a note for this voice part; create new voice part
                val orig = vp
                val part = vp.part
                var voice = vp.voice + 1
                vp = VoiceParts.get(part, voice)
                while (map[vp] != null) {
                    voice++
                    vp = VoiceParts.get(part, voice)
                }
                vp.id = orig.id
            }
            p.voicePart = vp
            map[vp] = p
            item.add(p)
            if (nd.lyric != null) {
                item.lyrics[vp] = nd.lyric!!.clone()
                if (m.start + nd.startTime != item.tick) {
                    item.lyrics[vp]!!.extend = "_"
                }
            }
            item.length = max(item.length, dur)
            if (nd.fermata)
                item.fermata = true
        }
        return item
    }

    fun divsPerQuarterNote() = divData.ticksPerQuarter

    fun items() : Iterable<Item> {
        return chordSeq?.list ?: emptyList()
    }

    fun countMeasures() = measures.size

    fun countChords() = chordSeq?.list?.size ?: 0

    fun countNotes() : Int {
        var n = 0
        for (m in measures) {
            n += m.notes.size
        }
        return n
    }

    override fun send(msg: Message, meToo: Boolean) {
        send(this, msg, meToo)
    }

    fun isEmpty() = countNotes() == 0

    // write score to a MusicXML file, including pitchbend info
    fun save(file: File) {
        val fileWriter = file.printWriter()
        val factory = XMLOutputFactory.newFactory()
        val writer = factory.createXMLStreamWriter(fileWriter)
        if (start != null) {
            writer.writeStartDocument()
        }
        if (dtd != null)
            writer.writeDTD(dtd.toString())
        write(root, writer)
        writer.writeEndDocument()
        writer.writeCharacters("\n")
        writer.close()
        fileWriter.close()
    }

    private fun write(e: Element?, writer: XMLStreamWriter) {
        if (e == null)
            return
        writer.writeStartElement(e.name)
        for (pair in e.attributes) {
            var name = pair.first
            if (name.startsWith("{http://www.w3.org/XML/1998/namespace}")) {
                name = "xml:${name.substring(38)}"
            }
            writer.writeAttribute(name, pair.second)
        }
        writer.writeCharacters(e.value.trim())
        if (e == root) {
            // add chord choices for each tick, pitch bend info
            val tuneTo = Global.tuneTo
            val sb0 = StringBuilder()
            tuneTo.forEach {
                sb0.append("$it; ")
            }
            val sb1 = StringBuilder()
            chordSeq?.list?.forEach {
                sb1.append("${it.tick} ${it.chord};")
            }
            val sb2 = StringBuilder()
            val sb3 = StringBuilder()
            val sb4 = StringBuilder()
            items().forEach {
                if (it.altTuning)
                    sb2.append("${it.tick} ")
                for (p in it.pitches) {
                    if (p.pitchBend() != 0)
                        sb4.append("$p ")
                }
                if (sb4.isNotEmpty()) {
                    sb4.setLength(sb4.length - 1)
                    sb3.append("${it.tick} ")
                    sb3.append("$sb4; ")
                    sb4.setLength(0)
                }
            }
            if (e.child?.name == "other-technical") {
                // update before writing it out
                val ot = e.child!!
                ot.clearAttributes()
                ot.setAttribute("tune-to", sb0.toString())
                ot.setAttribute("chord-sequence", sb1.toString())
                ot.setAttribute("alt-tuning", sb2.toString())
                ot.setAttribute("pitch-bends", sb3.toString())
                ot.setAttribute("score-length", this.lengthInTicks().toString())
                ot.setAttribute("options", Global.getOptionString())
            } else {
                // create
                writer.writeStartElement("other-technical")
                writer.writeAttribute("tune-to", sb0.toString())
                writer.writeAttribute("chord-sequence", sb1.toString())
                writer.writeAttribute("alt-tuning", sb2.toString())
                writer.writeAttribute("pitch-bends", sb3.toString())
                writer.writeAttribute("score-length", this.lengthInTicks().toString())
                writer.writeAttribute("options", Global.getOptionString())
                writer.writeEndElement()
            }
        }
        write(e.child, writer)
        writer.writeEndElement()
        write(e.sibling, writer)
    }

    private fun lengthInTicks(): Long {
        val lastMeasure = measures.last()
        return lastMeasure.start + lastMeasure.length
    }

    fun measureContainingTick(tick: Long): MeasureData? {
        return measures.findLast { it.start <= tick}
    }

    fun currentMeasure(): MeasureData? {
        return measureNumber(Global.timeNow.first)
    }

    fun splitStaves() {
        val newParts = ArrayList<Element>()
        // clone each staff that contains multiple voices
        val toSplit = ArrayList<String>()
        for (part in parts) {
            val id = part.getNamedAttribute("id") ?: "<none>"
            val vp = VoiceParts.get(id) ?: throw ErrorMessage("Cannot locate part $id")
            val num = vp.part
            var n = 0
            for (v in allVPs) {
                if (v.part == num && v.voice != -1)
                    n++
            }
            // we have n voices on this staff; eligible for splitting if > 1
            if (n > 1) {
                toSplit.add(id)
            }
        }
        if (toSplit.isEmpty()) {
            Main.log("No staff has more than one part")
            return
        }
        val dialog = SplitDialog(toSplit)
        val result = dialog.showAndWait()
        val arr = result.get().split(";")
        if (result.get() == "") {
            Main.log("Canceled")
            return
        }
        if (result.isPresent)
            Main.log("Splitting ${result.get().replace(';', ' ')}")
        gatherLyrics()
        for (part in parts) {
            val id = part.getNamedAttribute("id")!!
            if (arr.contains(id)) {
                newParts.addAll(extractParts(part))
            }
        }
        val firstPart = findChild(partWise.child, "part")
        var leftSib = partWise.child
        while (leftSib?.sibling != firstPart) {
            leftSib = leftSib?.sibling
        }
        leftSib?.sibling = newParts[0]
        var part: Element? = newParts[0]
        for (i in 1 .. newParts.lastIndex) {
            part!!.sibling = newParts[i]
            part = part.sibling
        }
        newParts.last().sibling = null
        // revise part-list
        var nVoice = 1
        val newPartList = ArrayList<Element>()
        var el = partList.child
        while (el != null) {
            val next = el.sibling
            el.sibling = null
            if (el.name == "score-part") {
                val id1 = el.getNamedAttribute("id")
                // add original and modify id
                val newEl = Element(el.name)
                for (attr in el.attributes) {
                    val v = if (attr.first == "id") {
                        "$id1-V1"
                    } else {
                        attr.second
                    }
                    newEl.setAttribute(attr.first, v)
                }
                val newId = "$id1-V1"
                newEl.setAttribute("id", newId)
                newPartList.add(newEl)
                // clone it
                val clone = Element(el.name)
                for (attr in el.attributes) {
                    val v = if (attr.first == "id") {
                        "$id1-V2"
                    } else {
                        attr.second
                    }
                    clone.setAttribute(attr.first, v)
                }
                newPartList.add(clone)
                val tree1 = Tree(newEl)
                val tree2 = Tree(clone)
                // copy children
                var child = el.child
                while (child != null) {
                    val nextKid = child.sibling
                    child.sibling = null
                    when (child.name) {
                        "part-name" -> {
                            val arr2 = child.value.split("[ ][ ]*".toRegex())
                            if (arr2.size == 2) {
                                child.value = arr2[0]
                                tree1.addKid(child)
                                val newKid = Element(child.name)
                                newKid.value = arr2[1]
                                tree2.addKid(newKid)
                            } else {
                                child.value = "V$nVoice"
                                tree1.addKid(child)
                                nVoice++
                                val newKid = Element(child.name)
                                newKid.value = "V$nVoice"
                                nVoice++
                                tree2.addKid(newKid)
                            }
                        }
                        "part-name-display" -> {
                            val text = findChild(child.child, "display-text") ?: throw ErrorMessage("Missig display-text for part-name-display")
                            val arr2 = text.value.split("\n")
                            if (arr2.size == 2) {
                                text.value = arr2[0]
                                tree1.addKid(child)
                                val newKid = cloneSubTree(child)
                                val text2 = findChild(newKid.child, "display-text")!!
                                text2.value = arr2[1]
                                tree2.addKid(newKid)
                            } else {
                                tree1.addKid(child)
                                val newKid = cloneSubTree(child)
                                tree2.addKid(newKid)
                            }
                        }
                        "score-instrument", "midi-device", "midi-instrument" -> {
                            if (findChild(el.child, child.name) == null) {
                                tree1.addKid(child)
                            } else {
                                tree2.addKid(child)
                            }
                        }
                        else -> {
                            val newKid = cloneSubTree(child)
                            tree1.addKid(child)
                            tree2.addKid(newKid)
                        }
                    }
                    child = nextKid
                }
            } else { // just add it to the list
                newPartList.add(el)
            }
            el = next
        }
        partList.child = newPartList[0]
        for (i in 1 .. newPartList.lastIndex) {
            newPartList[i-1].sibling = newPartList[i]
        }
        // copy lyrics as needed
        for (p in newParts) {
            val vp = getVoicePart(p)
            var m = p.child
            var tick: Long
            while (m != null) {
                val mNum = m.getNamedAttribute("number")?.toIntOrNull() ?: -1
                val md = findMeasure(mNum)
                tick = md.start
                var e = m.child
                while (e != null) {
                    if (e.name == "note") {
                        if (findChild(e.child, "rest") == null) {
                            val l = findChild(e.child, "lyric")
                            if (l == null) {
                                val lyric = lyricsMap.find(tick, vp)
                                if (lyric != null) {
                                    val lastSib = lastSibling(e.child!!)
                                    val newLyric = makeSubTree(lyric)
                                    lastSib.sibling = newLyric
                                }
                            } else {
                                l.setAttribute("number", "1")
                            }
                        }
                        val dur = findChild(e.child, "duration")
                        tick += dur!!.value.toLong() * divData.factor(vp)
                    }
                    e = e.sibling
                }
                m = m.sibling
            }
        }
        Main.log("Completed.")
    }

    private fun getVoicePart(part: Element) : VoicePart {
        if (part.name != "part")
            throw ErrorMessage("Expecting element name == \"part\"; saw \"${part.name}\"")
        val id = part.getNamedAttribute("id") ?: throw ErrorMessage("Missing id")
        val arr = id.split("-")
        if (arr.size != 2 || arr[0][0] != 'P' || arr[1][0] != 'V')
            throw ErrorMessage("ID is not in proper format: $id")
        val p = arr[0].substring(1).toInt()
        val v = arr[1].substring(1).toInt()
        return VoiceParts.get(v, p)
    }

    private fun gatherLyrics() {
        lyricsMap = LyricsMap()
        for (item in items()) {
            val tick = item.tick
            val lyrics = Lyrics(tick)
            lyricsMap.add(lyrics)
        }
        for (m in measures) {
            for (n in m.notes) {
                if (n.lyric != null) {
                    val tick = n.startTime + m.start
                    val part = n.part
                    val voice = n.voice
                    val vp = VoiceParts.get(voice, part)
                    val lyric = n.lyric!!
                    val lyrics = lyricsMap.find(tick)
                    if (lyrics != null) {
                        lyrics.map[vp] = lyric
                    } else {
                        throw ErrorMessage("Cannot locate lyrics at ${n.startTime} in measure ${m.number}")
                    }
                }
            }
        }
    }

    fun close() {
        file = null
        measures.clear()
        chordSeq?.list?.clear()
        send(FileChangeMessage())
    }
}

fun getStaffLabel(part: String) : String {
    val id = VoiceParts.get(part)!!
    return VoiceParts.nameMap.getOrDefault(id, part)
}

private fun extractParts(element: Element) : ArrayList<Element> {
    if (element.name != "part")
        throw ErrorMessage("Expecting Element name == \"part\"; found \"${element.name}\"")
    // TODO: more than 2 parts per staff
    // split "part" element
    val partId = element.getNamedAttribute("id")!!
    // rename top part
    element.setAttribute("id", "$partId-V1")
    element.sibling = null
    val element2 = Element(element.name)
    for (attr in element.attributes) {
        if (attr.first == "id") {
            element2.setAttribute("id", "$partId-V2")
        } else {
            element2.setAttribute(attr.first, attr.second)
        }
    }
    var e: Element? = element.child ?: throw ErrorMessage("No measures in part $partId")
    val e1 = Element(e!!.name)
    for (attr in e.attributes) {
        e1.setAttribute(attr.first, attr.second)
    }
    element.child = e1
    val tree1 = Tree(e1)
    lateinit var tree2: Tree
    var first = true
    while (e != null) {
        val nextMeasure = e.sibling
        e.sibling = null
        if (e.name == "measure") { // clone for each voice in this part
            val num = e.getNamedAttribute("number")
            val e2 = Element(e.name)
            for (attr in e.attributes)
                e2.setAttribute(attr.first, attr.second)
            var child = e.child
            e.child = null
            if (first) {
                tree2 = Tree(e2)
                element2.child = e2
            } else {
                tree1.addSib(e)
                tree2.addSib(e2)
            }
            first = false
            while(child != null) {
                val next = child.sibling
                child.sibling = null
                if (child.name == "note") {
                    val voiceNode = findChild(child.child, "voice")
                    if (voiceNode != null) {
                        val voice = voiceNode.value.toIntOrNull()
                            ?: throw ErrorMessage("Bad voice number in part $partId, measure $num")
                        if (voice != 1) { // move to other staff
                            tree2.addKid(child)
                            voiceNode.value = "1"
                        } else { // leave it on voice 1
                            tree1.addKid(child)
                        }
                    } else {
                        throw ErrorMessage("Note without voice at part $partId, measure $num")
                    }
                } else if (child.name == "backup") {
                    // skip it
                } else if (child.name == "print") {
                    // clone and modify slightly
                    val newKid = cloneSubTree(child)
                    tree1.addKid(child)
                    val measureNumber = findChild(newKid.child, "measure-numbering")
                    if (measureNumber != null) {
                        measureNumber.value = "none"
                    }
                    tree2.addKid(newKid)
                } else {
                    // just clone it
                    val newKid = cloneSubTree(child)
                    tree1.addKid(child)
                    tree2.addKid(newKid)
                }
                child = next
            }
        } else {
            throw ErrorMessage("Non-measure child of part $partId")
        }
        e = nextMeasure
    }
    return arrayListOf(element, element2)
}

private class DivData {
    internal val divMap = HashMap<VoicePart, Int>() // value of "division" for each part/voice combo
    var ticksPerQuarter = 1 // least common multiple of all "division" values

    fun ticksPerQuarter(vp: VoicePart) : Int {
        if (divMap[vp] != null)
            return divMap[vp]!!
        // no entry for this voice, use divs for voice 1 of this part
        val base = VoiceParts.get(1, vp.part)
        return divMap[base] ?: ticksPerQuarter
    }

    // return the factor by which we multiply each "div" in the part to get ticks
    fun factor(vp: VoicePart) : Int {
        val j = ticksPerQuarter(vp)
        assert (ticksPerQuarter % j == 0)
        return ticksPerQuarter / j
    }

    fun reset() {
        divMap.clear()
        ticksPerQuarter = 1
    }

    fun adjustForSwing() {
        // if ticks per quarter note is not a multiple of 6, multiply by 6, so that we
        // have a place to put swung eighths and swung dotted eighths as well as straigth
        // eighths
        if (ticksPerQuarter % 6 != 0)
            ticksPerQuarter *= 6
    }

    fun ticksPerBeat(beatType: Int): Int {
        return (ticksPerQuarter * (4.0 / beatType)).toInt()
    }
}

private fun updateTpQ(divData: DivData, vp: VoicePart, n: Int) {
    // maintain ticksPerQuarter as the least common multiple of all divisions
    val set = HashSet<Int>()
    set.add(n)
    divData.divMap.values.forEach {
        set.add(it)
    }
    divData.divMap[vp] = n
    val divs = set.toIntArray()
    val newTpQ = lcm(divs)
    divData.ticksPerQuarter = newTpQ
}

data class Lyrics(val tick: Long, val map: HashMap<VoicePart, Lyric> = HashMap<VoicePart, Lyric>())

class LyricsMap {
    val list = ArrayList<Lyrics>()

    fun add(lyrics: Lyrics) = list.add(lyrics)

    fun find(tick: Long) = list.find { it.tick == tick }

    private fun inExtend(tick: Long, vp: VoicePart): Boolean {
        val prev = list.findLast { it.tick < tick && it.map[vp] != null }
        if (prev != null) {
            return prev.map[vp]!!.extend == "start"
        }
        return false
    }

    fun find(tick: Long, vp: VoicePart) : Lyric? {
        val lyrics = find(tick) ?: return null
        val lyric = lyrics.map[vp]
        if (lyric != null) {
            return lyric
        } else if (lyrics.map.size == 1) {
            return lyrics.map.entries.toList()[0].value
        } else if (inExtend(tick, vp)) {
            return null
        } else {
            // best match
            for (item in lyrics.map) {
                if (item.key.part == vp.part)
                    return item.value
            }
            // no match in same part; get "nearest" line
            // this assumes P1 is above P2 and voice 1 is above voice 2
            if (vp.part == 1) {
                // take upper value of remaining (part 2, voice 1)
                val other = VoiceParts.get(1, 2)
                return lyrics.map[other]
            } else {
                // take lower vale of remaining (part1, voice 2)
                val other = VoiceParts.get(2, 1)
                return lyrics.map[other]
            }
        }
    }
}

private fun cloneSubTree(e: Element) : Element {
    val newRoot = Element(e.name)
    newRoot.value = e.value
    for (attr in e.attributes) {
        newRoot.setAttribute(attr.first, attr.second)
    }
    if (e.child != null) {
        newRoot.child = cloneSubTree(e.child!!)
    }
    if (e.sibling != null) {
        newRoot.sibling = cloneSubTree(e.sibling!!)
    }
    return newRoot
}

private fun findChild(e: Element?, name: String) : Element? = when {
    e == null -> null
    e.name == name -> e
    else -> findChild(e.sibling, name)
}

private fun lastSibling(e: Element): Element {
    if (e.sibling == null)
        return e
    return lastSibling(e.sibling!!)
}

private fun makeSubTree(lyric: Lyric) : Element {
    val newElement = Element("lyric")
    newElement.setAttribute("number", "1")
    var syl: Element? = null
    if (lyric.extend != "stop") {
        syl = Element("syllabic")
        syl.value = lyric.syllable.toString()
        newElement.child = syl
    }
    var text: Element? = null
    if (lyric.extend != "stop") {
        text = Element("text")
        text.value = lyric.s
        if (syl != null)
            syl.sibling = text
        else
            newElement.child = text
    }
    if (lyric.extend != ""){
        val extend = Element("extend")
        extend.setAttribute("type", lyric.extend)
        when {
            text != null -> {
                text.sibling = extend
            }
            syl != null -> {
                syl.sibling = extend
            }
            else -> {
                newElement.child = extend
            }
        }
    }
    return newElement
}

private fun indent(n: Int, sb: StringBuilder) {
    for (i in 0 .. n) {
        sb.append("    ")
    }
}

fun dump(e: Element?, indent: Int = 0, sb: StringBuilder = StringBuilder()) : String {
    // print element with all attributes
    if (e == null)
        return ""
    indent(indent, sb)
    sb.append("<${e.name} ")
    for (a in e.attributes) {
        sb.append("${a.first}=\"${a.second}\" ")
    }
    val v = e.value
    sb.append("> $v\n")
    if (e.child != null) {
        dump(e.child, indent + 1, sb)
    }
    if (e.sibling != null) {
        dump(e.sibling, indent, sb)
    }
    return sb.toString()
}

data class Tree(val root: Element) {
    var tail: Element = root
    var kTail: Element? = null

    init {
        if (tail.child != null) {
            kTail = tail.child
            while (kTail?.sibling != null)
                kTail = kTail!!.sibling
        }
    }

    fun addSib(e: Element) {
        tail.sibling = e
        tail = e
        if (e.sibling != null)
            throw ErrorMessage("Non-null sibling in addSib()")
        if (e.child != null)
            throw ErrorMessage("Non-null child in addSib()")
        kTail = null
    }

    fun addKid(e: Element) {
        if (tail.child == null) {
            tail.child = e
        } else {
            if (kTail == null)
                throw ErrorMessage("Null kTail in addKid()")
            kTail!!.sibling = e
        }
        kTail = e
        if (e.sibling != null)
            throw ErrorMessage("Non-null sibling in addKid()")
    }
}