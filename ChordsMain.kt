import Global.altTuning
import Global.altTuningIsAvailable
import Global.clickNote
import Global.clickVolume
import Global.delay
import Global.duration
import Global.fileLoaded
import Global.intervals
import Global.isJust
import Global.legato
import Global.mbt
import Global.noteValue
import Global.p_swing8
import Global.p_swingDot8
import Global.p_tempo
import Global.score
import Global.swing8
import Global.swingDot8
import Global.timeNow
import MessageType.*
import Styles.Companion.hoverColor
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight.BOLD
import javafx.scene.text.TextAlignment
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Modality
import javafx.stage.Stage
import tornadofx.*
import tornadofx.FileChooserMode.Save
import tornadofx.FileChooserMode.Single
import java.io.*
import java.net.URL
import java.util.*
import kotlin.concurrent.schedule

object Main {
    private lateinit var main: ChordsMain

    fun setMainView(v: ChordsMain) {
        main = v
    }

    fun getGraphBox(): HBox = main.getGraphBox()

    fun getGraphCanvas(): Canvas = main.graphCanvas

    fun getNoteList(): TextField = main.noteList

    fun log(vararg args: String) {
        main.log(*args)
    }

    fun parseTuneTo(s: String, setField: Boolean = false) {
        main.parseTuneTo(s, setField)
    }

    fun setChordSym(ci: ChordInstance) {
//        main.chordSym.text = main.chordSymField.format(ci)
        main.chordSymField.updateSubfields(ci)
        curItem.chordList.forEachIndexed { n, c ->
            if (c == ci)
                curItem.chosenChord = n
        }
    }

    fun saveConfig() {
        main.saveConfig()
    }

    fun usage() {
        println("Usage: jc [options...] [XML file]")
        println()
        println("Options:")
        println("    --help                         display this info and exit")
        println("    --overview                     display an overview of usage and exit")
        println("    --keyboardScaleFact            scale factor for keyboard panel (0.2 .. 10.0)")
        println("    --staffScaleFactor             scale factor for staff panel (0.2 .. 10.0)")
        println("    --delay=<n>                    delay between notes (milliseconds) when arpeggiating a chord")
        println("    --duration=<n>                 duration of chord playback (milliseconds)")
        println("    --volume=<n>                   volume of chord playback (5..125)")
        println("    --device=\"<pattern>\"           regular expression matching name of MIDI output device")
        println("    --instrument=<bank,patch>      MIDI instrument selector")
        println("    --clickVolume=<n>              metronome click volume (0..127)")
        println("    --clickNote=<n>                MIDI note number for metronome click (0..127)")
        println("    --noteValue=\"<note>\"           click length (note name)")
        println("    --legato=<boolean>             enable (or disable) legato playback")
        println("    --swing8=<boolean>             enable (or disable) swing eighth playback")
        println("    --swingDot8=<boolean>          enable (or disable) swing dotted-eighth playback")
        println("    --tempo=<n>                    tempo (in quarter-notes per minute) for playback")
    }

    fun overview() {
        val file = javaClass.getResource("overview.txt").file
        val reader = FileReader(file)
        reader.forEachLine {
            println(greedyWordwrap(it, 80))
        }
        reader.close()
    }

    private fun greedyWordwrap(text: String, lineWidth: Int): String {
        if (text.startsWith("   o ")) {
            val newText = greedyWordwrap(text.substring(6), lineWidth - 6)
            val sb = StringBuilder()
            for (line in newText.split('\n')) {
                sb.append("       ")
                sb.append(line)
                sb.append("\n")
            }
            return "   o  " + sb.drop(6).dropLast(1)
        }
        val words = text.split(' ')
        val sb = StringBuilder(words[0])
        var spaceLeft = lineWidth - words[0].length
        for (word in words.drop(1)) {
            val len = word.length
            if (len + 1 > spaceLeft) {
                sb.append("\n").append(word)
                spaceLeft = lineWidth - len
            }
            else {
                sb.append(" ").append(word)
                spaceLeft -= (len + 1)
            }
        }
        return sb.toString()
    }

    fun setKbdScaleFactor(factor: Number) {
        main.setKbdScaleFactor(factor)
    }

    fun setStaffScaleFactor(factor: Number) {
        main.setStaffScaleFactor(factor)
    }

    fun setLyricCanvas(canvas: Canvas) {
        main.setLyricCanvas(canvas)
    }

    fun getLyricsPanel(): LyricsPanel {
        return main.lyricsPanel
    }
}

class ChordsMain : View(), MessageCreator, MessageListener {
    private lateinit var devices: Menu
    private lateinit var instruments: Menu
    private lateinit var roots: Menu
    private lateinit var types: Menu
    private lateinit var inversions: Menu
    private lateinit var transpose: Menu
    private lateinit var legatoItem: CheckMenuItem
    private lateinit var swing8Item: CheckMenuItem
    private lateinit var swingDot8Item: CheckMenuItem
    private lateinit var muteMenu: Menu
    private lateinit var soloMenu: Menu
    private lateinit var fileName: Label
    private val modeToggleGroup = ToggleGroup()
    private lateinit var etButton: ToggleButton
    private lateinit var jiButton: ToggleButton
    lateinit var noteList: TextField
    private lateinit var tickField: TextField
    private lateinit var ratios: TextField
    private lateinit var tuneTo: TextField
    private lateinit var tempoSpinner: Spinner<Int>
    lateinit var graphCanvas: Canvas
    private lateinit var keyboardPane: Pane
    private lateinit var staffPane: Pane
    private lateinit var console: TextArea
    private lateinit var keyboardBox: BorderPane
    private lateinit var mainBox: SplitPane
    private lateinit var staffBox: BorderPane
    private var graphBox: HBox? = null
    private lateinit var chordSym: TextField
    private lateinit var chordList: ListView<ChordInstance>
    private lateinit var footer: Label
    private lateinit var instName: Label
    private lateinit var altTuningCheckBox: CheckBox

    private var notesField: NotesField
    lateinit var chordSymField: ChordSymbolField
    private var chordListPanel: ChordListPanel
    private val footerPanel: FooterPanel
    private val keyboardPanel = KeyboardPanel()
    private val staffPanel = StaffPanel()
    val lyricsPanel = LyricsPanel(staffPanel)
    private val instrumentField: InstrumentText
    private val tuningSpec = ArrayList<TuningSpec>()
    private val helpView = HelpView("Chords Help")

    private val em = getWidth("M", font())

    fun font() : Font {
        val field = TextField()
        return field.font
    }

    override val root = borderpane {
        top = vbox {
            anchorpane {
                stackpane {
                    anchorpaneConstraints {
                        leftAnchor = 0.0
                        rightAnchor = 0.0
                    }
                    anchorpane {
                        hboxConstraints { hGrow = Priority.ALWAYS }
                        menubar {
                            anchorpaneConstraints {
                                leftAnchor = 0.0
                                rightAnchor = 0.0
                            }
                            style {
                                fontWeight = BOLD
                            }
                            menu("File") {
                                item("Open Music XML File") {
                                    action {
                                        val ef =
                                            arrayOf(ExtensionFilter("Music XML files", "*.musicxml", "*.mxl", "*.xml"))
                                        val result = chooseFile(
                                            "Choose a Music XML file", ef, Single, currentWindow
                                        ) {
                                            initialDirectory = File(".")
                                        }
                                        if (result.isNotEmpty()) {
                                            Global.openFile(result[0])
                                        }
                                    }
                                }
                                item("Save") {
                                    action {
                                        val ef =
                                            arrayOf(ExtensionFilter("Music XML files", "*.musicxml", "*.mxl", "*.xml"))
                                        val result = chooseFile(
                                            "Choose Output File", ef, Save, currentWindow
                                        ) {
                                            initialDirectory = File(".")
                                            initialFileName = score.file?.name ?: ""

                                        }
                                        if (result.isNotEmpty()) {
                                            Global.saveFile(result[0])
                                        }
                                    }
                                }
                                item("Close") {
                                    enableWhen { fileLoaded }
                                    action {
                                        Global.closeFile()
                                    }
                                }
                                devices = menu("MIDI Output Device")
                                instruments = menu("Instrument") {
                                    action {
                                        System.err.println("not yet initialized")
                                    }
                                }
                                item("Split Staves") {
                                    enableWhen { fileLoaded }
                                    action {
                                        score.splitStaves()
                                    }
                                }
                                item("Analyze") {
                                    enableWhen { fileLoaded }
                                    action {
                                        analyzeSequence()
                                    }
                                }
                                separator { }
                                item("Exit") {
                                    action {
                                        Platform.exit()
                                    }
                                }
                            }
                            roots = menu("Root")
                            types = menu("Type")
                            inversions = menu("Inversion")
                            transpose = menu("Transpose")
                            menu("Options") {
                                item("Scale Factors") {
                                    action {
                                        val dialog = ScaleDialog()
                                        val result = dialog.showAndWait()
                                        if (result.isPresent) {
                                            val arr = result.get().split(':')
                                            Global.kbdScale = arr[0].toDouble()
                                            setKbdScaleFactor(Global.kbdScale)
                                            Global.staffScale = arr[1].toDouble()
                                            setStaffScaleFactor(Global.staffScale)
                                        }
                                    }
                                }
                                item("Inter-note Delay") {
                                    action {
                                        val dialog = DelayDialog(delay.toString())
                                        val result = dialog.showAndWait()
                                        if (result.isPresent) {
                                            send(DelayChangeMessage(result.get().toLong()))
                                        }
                                    }
                                }
                                item("Duration") {
                                    action {
                                        val dialog = DurationDialog(duration.toString())
                                        val result = dialog.showAndWait()
                                        if (result.isPresent) {
                                            send(DurationChangeMessage(result.get().toLong()))
                                        }
                                    }
                                }
                                item("Volume") {
                                    action {
                                        val dialog = VolumeDialog()
                                        val result = dialog.showAndWait()
                                        if (result.isPresent)
                                            send(VolumeChangeMessage(result.get().toInt()))
                                    }
                                }
                                item("Metronome") {
                                    action {
                                        val dialog = MetronomeDialog()
                                        val result = dialog.showAndWait()
                                        val nv = dialog.selectedNoteValue.value.toLowerCase()
                                        val newVol = result.get().toIntOrNull()
                                        if (newVol == null) {
                                            dialog.revert()
                                        } else {
                                            clickVolume = newVol
                                            noteValue = nv
                                        }
                                    }
                                }
                                legatoItem = checkmenuitem("Legato Playback") {
                                    isSelected = legato.value
                                    action {
                                        legato.set(isSelected)
                                    }
                                }
                                swing8Item = checkmenuitem("Swing Eighths") {
                                    isSelected = swing8
                                    action {
                                        send(Swing8ChangeMessage(this.isSelected))
                                    }
                                }
                                swingDot8Item = checkmenuitem("Swing Dotted Eighths") {
                                    isSelected = swingDot8
                                    action {
                                        send(SwingDot8ChangeMessage(this.isSelected))
                                    }
                                }
                                muteMenu = menu("Mute") {
                                    enableWhen { fileLoaded }
                                    item("No <XML> file")
                                }
                                soloMenu = menu("Solo") {
                                    enableWhen { fileLoaded }
                                    item("No <XML> file")
                                }
                            }
                            menu("Help") {
                                item("Welcome") {
                                    action {
                                        showHelp("welcome")
                                    }
                                }
                                item("Overview") {
                                    action {
                                        showHelp("overview")
                                    }
                                }
                                item("Options") {
                                    action {
                                        showHelp("options")
                                    }
                                }
                                item("Syntax") {
                                    action {
                                        showHelp("syntax")
                                    }
                                }
                                item("Command Line") {
                                    action {
                                        showHelp("commandline")
                                    }
                                }
                                item("Integration") {
                                    action {
                                        showHelp("integration")
                                    }
                                }
                                item("Customization") {
                                    action {
                                        showHelp("customization")
                                    }
                                }
                                item("About") {
                                    action {
                                        showHelp("about")
                                    }
                                }
                            }
                        }
                    }
                    anchorpane {
                        isMouseTransparent = true
                        hboxConstraints { hGrow = Priority.ALWAYS }
                        fileName = label("No File") {
                            anchorpaneConstraints {
                                rightAnchor = 0.0
                            }
                            textAlignment = TextAlignment.RIGHT
                            isMouseTransparent = true
                            style {
                                padding = box(7.px)
                                textFill = hoverColor
                            }
                        }
                    }
                }
            }
            // First line of controls
            hbox {
                useMaxWidth = true
                vboxConstraints { hgrow = Priority.ALWAYS }
                label("Chord symbol:")
                chordSym = textfield("    ") {
                    tooltip(
                        "Enter a chord symbol: C7, Ab sus, etc.\n" +
                                "See Help->Syntax for more info."
                    )
                    action {
                        chordSymField.parse(text)
                    }
                    prefColumnCount = 40
                }
                label("Notes:")
                noteList = textfield("    ") {
                    useMaxWidth = true
                    hboxConstraints { hGrow = Priority.ALWAYS }
                    tooltip(
                        "Enter a sequence of notes, from the bottom: C Eb G, eg.\n"
                                + "See Help->Syntax for more options."
                    )
                    action {
                        parseNotes(text)
                    }
                }
                hbox {
                    style {
                        padding = box(5.px)
                    }
                    etButton = radiobutton("E.T.   ", modeToggleGroup) {
                        tooltip("Select Equal Temperament")
                        isSelected = true
                        action {
                            isJust.set(false)
                        }
                    }
                    jiButton = radiobutton("J.I  ", modeToggleGroup) {
                        tooltip("Select Just Intonation")
                        action {
                            isJust.set(true)
                        }
                    }
                }
            }
            // Second line
            hbox {
                label("Tune to:") { enableWhen(isJust) }
                tuneTo = textfield("    ") {
                    enableWhen(isJust)
                    useMaxWidth = true
                    hboxConstraints { hGrow = Priority.ALWAYS }
                    tooltip(
                        "Enter a voice ID, or a sequence of voice IDs\n" +
                                "and M:B:T values, to set the voice to tune to in JI mode.\n" +
                                "See Help->Syntax for more."
                    )
                    action {
                        parseTuneTo(text)
                    }
                }
                label("Ratios:") { enableWhen(isJust) }
                ratios = textfield("    ") {
                    enableWhen(isJust)
                    useMaxWidth = true
                    hboxConstraints { hGrow = Priority.ALWAYS }
                    tooltip(
                        "Enter a sequence of integers describing the\n" +
                                "ratios: 4:5:6:7"
                    )
                    action {
                        curItem.setRatios(text)
                        runLater {
                            send(PaintRequestMessage(), meToo = true)
                            send(ArpeggiateRequestMessage())
                        }
                    }
                }
                hbox {
                    style {
                        padding = box(5.px)
                    }
                    altTuningCheckBox = checkbox("Alt. Tuning  ") {
                        tooltip("Select alternate tuning for chord")
                        enableWhen(altTuningIsAvailable)
                        action {
                            altTuning.set(isSelected)
                            curItem.altTuning = isSelected
                            send(AltTuningRequestMessage())
                        }
                    }
                }
                button("Play") {
                    enableWhen { fileLoaded }
                    action {
                        send(PlayRequestMessage())
                    }
                }
                button("Stop") {
                    enableWhen { fileLoaded }
                    action {
                        send(StopRequestMessage())
                    }
                }
                button("Rewind") {
                    enableWhen { fileLoaded }
                    action {
                        if (timeNow.first > 0) {
                            val m = score.items().first()
                            timeNow = Pair(m.measure?.number ?: 0, 0)
                        }
                        send(PaintRequestMessage(), meToo = true)
                    }
                }
                label("Tempo:") {
                    enableWhen { fileLoaded }
                    style {
                        padding = box(5.px, 2.px, 2.px, 10.px)
                    }
                }
                tempoSpinner = spinner(1, 500, 60) {
                    enableWhen { fileLoaded }
                    isEditable = true
                    useMaxWidth = true
                    maxWidth = 8.0 * em
                    tooltip("Enter the tempo (in quarter notes per minute) for playback")
                    valueProperty().addListener { _, _, newValue ->
                        run {
                            send(TempoChangeMessage(newValue))
                        }
                    }
                    editor.focusedProperty().addListener(ChangeListener<Boolean> { _, old, _ ->
                        run {
                            if (old) {
                                val n = editor.text.toIntOrNull() ?:
                                        throw ErrorMessage("Bad value: ${editor.text}")
                                this.valueFactory.value = n
                                runLater {
                                    send(TempoChangeMessage(n))
                                }
                            }
                        }
                    })
                }
                button("  <  ") {
                    enableWhen { fileLoaded }
                    tooltip("Select previous chord in sequence")
                    action {
                        send(BackRequestMessage())
                    }
                }
                tickField = textfield(" ") {
                    maxWidth = 12.0 * font.size
                    enableWhen { fileLoaded }
                    tooltip("Current position in sequence (measure:beat:tick)")
                    action {
                        send(TickChangeMessage(text))
                        send(ArpeggiateRequestMessage())
                    }
                }
                button("  >  ") {
                    enableWhen { fileLoaded }
                    tooltip("Select next chord in sequence")
                    action {
                        send(FwdRequestMessage())
                    }
                }
            }
        }
        center = splitpane {
            orientation = Orientation.VERTICAL
            runLater {
                setDividerPositions(Global.splits[0])
            }
            // Keyboard
            keyboardBox = borderpane {
                useMaxWidth = true
                useMaxHeight = false
                minHeight = 125.0
                prefHeight = 125.0
                hboxConstraints { hGrow = Priority.NEVER }
                vboxConstraints { vGrow = Priority.NEVER }
                center {
                    keyboardPane = pane()
                }
            }
            // Main area
            mainBox = splitpane {
                useMaxWidth = true
                orientation = Orientation.HORIZONTAL
                setDividerPositions(Global.splits[1])
                vboxConstraints {
                    vGrow = Priority.ALWAYS
                }
                splitpane {
                    orientation = Orientation.VERTICAL
                    setDividerPositions(Global.splits[2])
                    staffBox = borderpane {
                        useMaxWidth = true
                        minWidth = 200.0
                        minHeight = 200.0
                        hboxConstraints { hGrow = Priority.ALWAYS }
                        vboxConstraints { vGrow = Priority.ALWAYS }
                        center {
                            staffPane = pane() {
                                background = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY))
                            }
                        }
                    }
                    console = textarea {
                        text = "Console area\n"
                        prefRowCount = 10
                        isEditable = false
                    }
                }
                borderpane {
                    useMaxWidth = true
                    hboxConstraints { hGrow = Priority.ALWAYS }
                    center {
                        scrollpane {
                            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                            isFitToWidth = true
                            isFitToHeight = true
                            chordList = listview {
                                onUserSelect(1) { ci ->
                                    run {
                                        Main.setChordSym(ci)
                                        curItem.chosenChord = curItem.chordList.indexOf(ci)
                                        send(ChordIndexChangeMessage(curItem))
                                    }
                                }
                            }
                        }
                    }
                    bottom = hbox {
                        style {
                            backgroundColor += Color.web("#eeefee")
                            fontWeight = BOLD
                            fontSize = 40.0.px
                        }
                        minHeight = 10.0
                        minWidth = 100.0
                        graphCanvas = canvas(100.0, 10.0) {
                        }
                    }
                }
            }
        }
        bottom = hbox {
            prefWidth = Region.USE_COMPUTED_SIZE
            minWidth = Region.USE_COMPUTED_SIZE
            useMaxWidth = true
            hbox {
                footer = label(" ") {
                    useMaxWidth = true
                    prefWidth = 400.0
                    maxWidth = Region.USE_COMPUTED_SIZE
                    minWidth = Region.USE_PREF_SIZE
                    useMaxWidth = true
                    hboxConstraints { hGrow = Priority.ALWAYS }
                }
                hboxConstraints { hGrow = Priority.ALWAYS }
            }
            hbox {
                prefWidth = Region.USE_COMPUTED_SIZE
                button("  <  ") {
                    tooltip("Select previous MIDI instrument (see File->Instrument)")
                    useMaxWidth = false
                    action {
                        sequencer.prevInst()
                    }
                }
                instName = label {
                    text = "  "
                    prefWidth = 300.0
                    useMaxWidth = false
                }
                button("  >  ") {
                    tooltip("Select next MIDI instrument (see File->Instrument)")
                    action {
                        sequencer.nextInst()
                    }
                    useMaxWidth = false
                }
            }
        }
    }

    private fun savePreferences() {
        val opts = Global.getOptionString()
        val list = ArrayList<Double>()
        getSplitPoints(list)
        val dir = File(getUserDataDirectory())
        if (!dir.exists())
            dir.mkdir()
        val file = File(getUserDataDirectory() + "config")
        if (!file.exists())
            file.createNewFile()
        val w = file.printWriter()
        w.println(opts)
        for (p in list) {
            w.println(p)
        }
        w.println(Global.kbdScale)
        w.println(Global.staffScale)
        w.println(keyboardPanel.getTranslation())
        w.println(staffPanel.getTranslation().first)
        w.println(staffPanel.getTranslation().second)
        w.close()
    }

    private fun showHelp(name: String) {
        val url = helpView.javaClass.getResource("/help/$name.html")
        helpView.load(url.toString())
        helpView.openModal()
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            if (e is ErrorMessage) {
                footer.text = e.localizedMessage
                log(e.localizedMessage)
            } else {
                System.err.println("$e\n" + e.localizedMessage)
                e.printStackTrace(System.err)
                log(e.localizedMessage)
                runLater {
                    footer.text = e.localizedMessage
                }
            }
        }

        restorePreferences()

        subscribe(this, OptionsChange, PaintRequest, TickChange, FileChange, ChordInstanceChange)
        isJust.addListener (ChangeListener<Boolean> { _, _, _: Boolean ->
            run {
                jiButton.isSelected = isJust()
                etButton.isSelected = !isJust()
                send(ModeChangeMessage())
            }
        })
        altTuning.addListener (ChangeListener<Boolean> { _, _, _: Boolean ->
            run {
                send(AltTuningRequestMessage())
            }
        })
        initNotes()
        initQuality()
        notesField = NotesField(noteList)
        chordSymField = ChordSymbolField(chordSym)
        chordListPanel = ChordListPanel(chordList)
        footerPanel = FooterPanel(footer)
        instrumentField = InstrumentText(instName)
        NoteGraphPanel()
        Global.analyzer = ChordAnalyzer()

        val rdr = StringReader("C")
        Symbol(rdr)
        this.title = "JustChords ver. 5.0"
        Main.setMainView(this)
        keyboardPanel.pane(keyboardPane)
        keyboardPanel.setFactor(Global.kbdScale)
        keyboardPanel.setTranslation(Global.kbdOffset)
        staffPanel.pane(staffPane)
        staffPanel.setFactor(Global.staffScale)
        staffPanel.setTranslation(Global.staffTranslations)

        staffBox.widthProperty()?.addListener { _, oldValue, newValue ->
            if (oldValue != newValue) {
                runLater {
                    staffPanel.refresh()
                    lyricsPanel.refresh()
                }
            }
        }
        staffBox.heightProperty()?.addListener { _, oldValue, newValue ->
            if (oldValue != newValue) {
                runLater {
                    staffPanel.refresh()
                    lyricsPanel.refresh()
                }
            }
        }

        keyboardBox.heightProperty()?.addListener { _, oldValue, newValue ->
            if (oldValue != newValue) {
                keyboardPanel.setHeight(newValue as Double)
                keyboardPanel.paint()
            }
        }

        lyricsPanel.setPane(staffPane)

        try {
            val url = getUrl()
            val props = Properties()
            props.load(url.openStream())
            if (props.isEmpty) {
                System.err.println("Empty properties at $url")
                Platform.exit()
            } else {
                readChords(props.getProperty("chords"))
                Global.setInstrument(props.getProperty("instrument"))
                send(VolumeChangeMessage(props.getProperty("volume").toInt()))
                send(DelayChangeMessage(props.getProperty("delay").toLong()))
                send(DurationChangeMessage(props.getProperty("duration").toLong()))
                clickVolume = props.getProperty("clickVolume").toInt()
                noteValue = props.getProperty("noteValue")
                clickNote = props.getProperty("clickNote").toInt()
            }
        } catch (e: IllegalStateException) {
            System.err.println(e.localizedMessage)
            Platform.exit()
        } catch (e: IOException) {
            System.err.println(e.localizedMessage)
            Platform.exit()
        } catch (e: NumberFormatException) {
            System.err.println(e.localizedMessage)
            Platform.exit()
        }


        FX.application.parameters.unnamed.forEach {
            when (it) {
                "--help", "-help", "-h" -> { // can't happen
                    Main.usage()
                    Platform.exit()
                }
                "--dump-stylesheets", "--live-stylesheets", "--live-views", "--dev-mode" -> {
                    // OK
                }
                else -> {
                    val filename = it
                    val file = File(filename)
                    if (file.exists()) {
                        Global.openFile(file)
                    } else {
                        if (it.startsWith("-"))
                            System.err.println("Unrecognized option: $it; try --help")
                        else
                            System.err.println("Cannot open file: $it")
                        Platform.exit()
                    }
                }
            }
        }

        FX.application.parameters.named.forEach {
            when (it.key) {
                "keyboardScaleFactor" -> {
                    val d = it.value.toDoubleOrNull()
                    if (d == null) {
                        System.err.println("Bad vale for keyboardScaleFactor: ${it.value}")
                        Platform.exit()
                    }
                    if (d!! < 0.2 || d > 10.0) {
                        System.err.println("Keyboardtaff scale factor out of range: $d; valid range is 0.2 to 10.0")
                    } else {
                        Global.staffScale = d
                        setKbdScaleFactor(d)
                    }
                }
                "staffScaleFactor" -> {
                    val d = it.value.toDoubleOrNull()
                    if (d == null) {
                        System.err.println("Bad vale for staffScaleFactor: ${it.value}")
                        Platform.exit()
                    }
                    if (d!! < 0.2 || d > 10.0) {
                        System.err.println("Staff scale factor out of range: $d; valid range is 0.2 to 10.0")
                    } else {
                        Global.staffScale = d
                        setStaffScaleFactor(d)
                    }
                }
                "delay" -> {
                    send(DelayChangeMessage(it.value.toLong()))
                }
                "duration" -> {
                    send(DurationChangeMessage(it.value.toLong()))
                }
                "volume" -> {
                    send(VolumeChangeMessage(it.value.toInt()))
                }
                "instrument" -> {
                    Timer().schedule(500){
                        Global.setInstrument(it.value)
                    }
                }
                "device" -> {
                    sequencer.openDevice(it.value)
                }
                "clickVolume" -> {
                    clickVolume = it.value.toInt()
                }
                "noteValue" -> {
                    noteValue = it.value.toLowerCase()
                }
                "clickNote" -> {
                    clickNote = it.value.toInt()
                }
                "legato" -> {
                    legato.value = it.value!!.toBoolean()
                }
                "swing8" -> {
                    p_swing8 = it.value!!.toBoolean()
                }
                "swingDot8" -> {
                    p_swingDot8 = it.value!!.toBoolean()
                }
                "tempo" -> {
                    p_tempo = it.value!!.toInt()
                    tempoSpinner.valueFactory.value = p_tempo
                }
                else -> {
                    System.err.println("Unrecognized option: --${it.key}; try --help")
                    Platform.exit()
                }
            }
        }
        createRootMenu(roots)
        createTypeMenu(types)
        createInversionsMenu(inversions)
        createTransposeMenu(transpose)

        Global.createDevicesMenu(devices)
        Global.createInstrumentMenu(instruments)
        primaryStage.isMaximized = true

        val dir = File(getUserDataDirectory())
        if (dir.exists()) {
            val welcomed = File(dir, "welcomed")
            if (!welcomed.exists()) {
                showWelcome()
            }
        }
    }

    private fun showWelcome() {
        Timer().schedule(1000) {
            runLater {
                showHelp("welcome")
            }
        }
        val dir = File(getUserDataDirectory())
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "welcomed")
        if (!file.exists()) {
            val fos = FileOutputStream(file)
            fos.close()
        }
    }

    private fun getSplitPoints(list: ArrayList<Double>, n: Node = root) {
        if (n is Parent) {
            for (kid in n.childrenUnmodifiable) {
                if (kid is SplitPane) {
//                    Main.log("${kid.dividerPositions[0]}")
                    list.add(kid.dividerPositions[0])
                }
                getSplitPoints(list, kid)
            }
        }
    }

    private fun restorePreferences() {
        val file = File(getUserDataDirectory() + "config")
        if (!file.exists())
            return
        val rdr = file.reader()
        val list = ArrayList<Double>()
        var i = 0
        rdr.forEachLine {
            if (i == 0) {
                Global.parseOptionString(it)
            } else {
                val d = it.toDoubleOrNull() ?: throw ErrorMessage("Cannot parse config file $file; expecticd double at $it")
                list.add(d)
            }
            i++
        }
        rdr.close()
        if (list.size < 3) {
            System.err.println("Expecting three values in config file $file; got ${list.size}")
            return
        }
        for (k in 0..2)
            Global.splits[k] = list[k]

        if (list.size >= 5) {
            if (!FX.application.parameters.named.contains("keyboardScaleFactor")) {
                Global.kbdScale = list[3]
            }
            if (!FX.application.parameters.named.contains("staffScaleFactor")) {
                Global.staffScale = list[4]
            }
        }
        if (list.size >= 6) {
            Global.kbdOffset = list[5]
        }
        if (list.size >= 8) {
            Global.staffTranslations[0] = list[6]
            Global.staffTranslations[1] = list[7]
        }
    }

    private fun getUrl() : URL {
        val dir = File(getUserDataDirectory())
        if (!dir.exists()) {
            dir.mkdir()
        }
        val file = File(dir, "properties")
        if (!file.exists()) {
            // copy properties to config dir
            val res = javaClass.getResource("ChordProps.properties")
            val props = File(res.file)
            val reader = FileReader(props)
            val writer = FileWriter(file)
            reader.forEachLine {
                writer.write(it + "\n")
            }
            reader.close()
            writer.close()
        }
        val arg = FX.application.parameters.named["config"]
        if (arg != null) {
            return URL("file:///$arg")
        }
        return URL("file:///$file")
    }

    private fun createRootItem(n: Note, s: String) : MenuItem {
        val item = MenuItem(s)
        item.style = "-fx-font-family: \"MaestroTimes\"; -fx-font-size: 18px"
        item.onAction = EventHandler {
            run {
                send(RootChangeMessage(n))
            }
        }
        return item
    }

    private fun createRootMenu(m: Menu) {
        m.items.clear()
        for (nat in 'A'..'G') {
            val n = noteLookup(nat.toString())
            val sub = Menu(nat.toString())
            var item = createRootItem(n.flatten()!!.flatten()!!, "${nat}\uF0B2\uF0B2")
            sub.items.add(item)
            item = createRootItem(n.flatten()!!, "${nat}\uF0B2")
            sub.items.add(item)
            item = createRootItem(n,"${nat}\uF0BD")
            sub.items.add(item)
            item = createRootItem(n.sharpen()!!,"${nat}\uF0B3")
            sub.items.add(item)
            item = createRootItem(n.sharpen()!!.sharpen()!!, "${nat}\uF0C5")
            sub.items.add(item)
            m.items.add(sub)
        }
    }

    private fun createChordTypeItem(c: Chord) : MenuItem {
        val s = if (c.suffix == "") "(major)" else c.suffix
        val item = MenuItem(s)
        item.userData = c
        item.onAction = EventHandler {
            run {
                send(ChordTypeChangeMessage(c))
            }
        }
        return item
    }

    private fun createTypeMenu(m: Menu) {
        val m7 = I("m7")
        val M7 = I("M7")
        val M3 = I("M3")
        val m3 = I("m3")
        val intervalMenu = Menu("Interval")
        val simpleMenu = Menu("Simple")
        val compoundMenu = Menu("Compound")
        intervalMenu.items.add(simpleMenu)
        intervalMenu.items.add(compoundMenu)
        m.items.add(intervalMenu)
        val majorMenu = Menu("Major")
        val minorMenu = Menu("Minor")
        val min7Menu = Menu("Minor Seventh")
        val sevenMenu = Menu("Dom. Seventh")
        val maj7Menu = Menu("Major Seventh")
        val otherMenu = Menu("Other")
        m.items.addAll(majorMenu, minorMenu, min7Menu, sevenMenu, maj7Menu, otherMenu)
        for (c in allChords()) {
            val mi = createChordTypeItem(c)
            when {
                c.isInterval -> {
                    // ignore -- handled below
                }
                c.suffix.indexOf(" (no 5)") > 0 -> {
                    // ignore
                }
                else -> {
                    val n = c.intervals
                    when {
                        n.contains(m7) && n.contains(M3) -> sevenMenu.items.add(mi)
                        n.contains(m7) && n.contains(m3) -> min7Menu.items.add(mi)
                        n.contains(M7) && n.contains(M3) -> maj7Menu.items.add(mi)
                        n.contains(M3) && n.contains(P5) -> majorMenu.items.add(mi)
                        n.contains(m3) && n.contains(P5) -> minorMenu.items.add(mi)
                        else -> otherMenu.items.add(mi)
                    }
                }
            }
        }
        for (c in intervalList()) {
            val mi = createChordTypeItem(c)
            // filter out doubly-aug, etc.
            val n = c.intervals[0]
            if (n.isBasic() || n.quality == "diminished" || n.quality == "augmented") {
                if (n.basicSize <= 8 && n.semis > 0)
                    simpleMenu.items.add(mi)
                else if (n.basicSize > 8)
                    compoundMenu.items.add(mi)
            }
        }
        simpleMenu.items.sortByDescending { i -> i.userData as Chord }
        compoundMenu.items.sortByDescending { i -> i.userData as Chord }
        majorMenu.items.sortByDescending { i -> i.userData as Chord }
        minorMenu.items.sortByDescending { i -> i.userData as Chord }
        min7Menu.items.sortByDescending { i -> i.userData as Chord }
        sevenMenu.items.sortByDescending { i -> i.userData as Chord }
        maj7Menu.items.sortByDescending { i -> i.userData as Chord }
        otherMenu.items.sortByDescending { i -> i.userData as Chord }
    }

    private fun intervalList() : ArrayList<Chord> {
        val arr = ArrayList<Chord>()
        for (int in nameMap.values) {
            if (!suffixMap.containsKey(intervalNameOf(int))) {
                create(int)
            }
        }
        for (c in allChords()) {
            if (c.isInterval)
                arr.add(c)
        }
        return arr
    }

    private fun createInversionItem(inv: Int) : MenuItem {
        val str = inversionName(inv)
        val item = MenuItem(str)
        if (inv > 0)
            item.isDisable = true
        item.userData = inv
        item.onAction = EventHandler {
            run {
                send(InversionChangeMessage(inv))
            }
        }

        return item
    }

    private fun createInversionsMenu(m: Menu) {
        var mi = createInversionItem(0)
        mi.text = "Root position"
        m.items.add(mi)
        for (i in 1 until (maxInterval+1)) {
            mi = createInversionItem(i)
            m.items.add(mi)
        }
    }

    private fun createSemisTransposeMenu(m: Menu) {
        for (i in 12 downTo 1) {
            val int = intervalForSemis(i)
            val mi = MenuItem("$i")
            mi.onAction = EventHandler {
                run {
                    send(TransposeChangeMessage(true, int))
                }
            }
            m.items.add(mi)
        }
        m.items.add(SeparatorMenuItem())
        for (i in 1..12) {
            val int = intervalForSemis(i)
            val mi = MenuItem("-$i")
            mi.onAction = EventHandler {
                run {
                    send(TransposeChangeMessage(false, int))
                }
            }
            m.items.add(mi)
        }
    }

    private fun createIntervalTransposeMenu(m: Menu, up: Boolean) {
        val enh = MenuItem("Enharmonically")
        enh.onAction = EventHandler {
            run {
                send(TransposeChangeMessage(up, P1))
            }
        }
        for (c in intervalList()) {
            val int = c.intervals[0]
            if (!int.isBasic() && int.quality != "diminished" && int.quality != "augmented")
                continue // filter out doubly-augmented, etc.
            if (int.semis <= 0)
                continue
            val mi = MenuItem(c.toString())
            mi.userData = c
            mi.onAction = EventHandler {
                run {
                    send(TransposeChangeMessage(up, int))
                }
            }
            m.items.add(mi)
        }
        m.items.sortByDescending { i -> i.userData as Chord }
        m.items.add(0, enh)
    }

    private fun createTransposeMenu(m: Menu) {
        val up = Menu("Up")
        val down = Menu("Down")
        val semis = Menu("Semitones")
        createIntervalTransposeMenu(up, true)
        createIntervalTransposeMenu(down, false)
        createSemisTransposeMenu(semis)
        m.items.add(up)
        m.items.add(down)
        m.items.add(SeparatorMenuItem())
        m.items.add(semis)
    }

    fun getGraphBox() : HBox {
        if (graphBox == null) {
            graphBox = mainBox.lookup("HBox") as HBox
        }
        return graphBox!!
    }

    fun log(vararg args: String) {
        runLater {
            for (s in args) {
                console.appendText(s)
            }
            console.appendText("\n")
            console.scrollTop = console.text.length.toDouble()
        }
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            OptionsChange -> {
                legatoItem.isSelected = legato.value
                swing8Item.isSelected = swing8
                swingDot8Item.isSelected = swingDot8
                tempoSpinner.valueFactory.value = Global.tempo
                for (item in devices.items) {
                    (item as RadioMenuItem).isSelected = item.userData == sequencer.midiOut?.midiDevice?.infoString() ?: ""
                }
                for (item in muteMenu.items) {
                    if (item is CheckMenuItem)
                        item.isSelected = (item.userData as VoicePart).muted
                }
                for (item in soloMenu.items) {
                    if (item is CheckMenuItem)
                        item.isSelected = (item.userData as VoicePart).soloed
                }
            }
            FileChange -> {
                fileName.text = score.file?.canonicalPath ?: "No File"
                VoiceParts.populateMuteMenu(muteMenu)
                VoiceParts.populateSoloMenu(soloMenu)
            }
            PaintRequest -> {
                runLater {
                    tickField.text = mbt
                    altTuningIsAvailable.set(curItem.chord.chord.ratios.size > 1)
                    altTuning.set(curItem.altTuning)
                    altTuningCheckBox.isSelected = curItem.altTuning
                    if (isJust()) {
                        val sb = StringBuilder()
                        for (n in curItem.factors) {
                            sb.append(n.toString())
                            sb.append(" ")
                        }
                        ratios.text = sb.toString()
                    } else {
                        ratios.text = ""
                    }
                }
                if ((curItem.chord.chord.isInterval && intervals[0].semis > 11) || intervals.isEmpty()) {
                    inversions.items.forEachIndexed { n, mi ->
                        mi.isDisable = (n > 0)
                    }
                    return
                }
                val maxVal = intervals.size
                inversions.items.forEachIndexed { n, mi ->
                    run {
                        mi.isDisable = (n > maxVal)
                    }
                }
            }
            else -> {
                // ignore
            }
        }
    }

    fun parseTuneTo(str: String, setField: Boolean = false) {
        val regex = "[0-9][0-9]*:[0-9][0-9]*:[0-9][0-9]*".toRegex()
        var tick = 0L

        tuningSpec.clear()

        if (setField)
            tuneTo.text = str

        if (str == "") {
            tuneTo.text = ""
        } else {
            val st = StringTokenizer(str)
            while (st.hasMoreTokens()) {
                val s = st.nextToken()
                when {
                    s[0] == '(' -> {
                        val arr = s.split("[(/)]".toRegex())
                        val ts = TuningSpec(tick = tick)
                        if (arr.size == 3) {
                            val part = arr[1].toInt()
                            val vp = VoicePart(0, part)
                            ts.vp = vp
                        } else {
                            val part = arr[1].toInt()
                            val voice = arr[2].toInt()
                            val vp = VoiceParts.get(voice, part)
                            ts.vp = vp
                        }
                        tuningSpec.add(ts)
                    }
                    s.matches(regex) -> {
                        val arr = s.split(":")
                        val m = arr[0].toInt()
                        val b = arr[1].toInt()
                        val t = arr[2].toInt()
                        val meas = score.measureNumber(m) ?:
                                    throw ErrorMessage("No such measure: $m")
                        tick = meas.start + (b-1) * meas.ticksPerBeat() + t
                    }
                    s[0] == '-' || s[0] == '+' -> {
                        val cents = s.toDoubleOrNull()
                        if (cents != null) {
                            val ts = TuningSpec(tick = tick, cents = cents)
                            tuningSpec.add(ts)
                        } else {
                            log("Cannot parse value: $s")
                        }
                    }
                    else -> { //must be a note name
                        var cents = 0.0
                        val arr = s.split("[-+]".toRegex())
                        if (arr.size == 2) { // has +cents or -cents
                            var n = s.indexOf('-')
                            if (n < 0) {
                                n = s.indexOf('+')
                            }
                            val cString = s.substring(n)
                            val c = cString.toDoubleOrNull()
                            if (c != null) {
                                cents = c
                            } else {
                                log("Cannot parse cents value: $s")
                            }
                            val note = noteMap[arr[0]]
                            if (note == null) {
                                log("Note name not found: ${arr[0]}")
                            } else {
                                val ts = TuningSpec(tick = tick, note = note, cents = cents)
                                tuningSpec.add(ts)
                            }
                        } else if (arr.size == 1) {
                            // note name, no cents value
                            val note = noteMap[s.trim()]
                            if (note == null) {
                                log("Note name not found: $s")
                            } else {
                                val ts = TuningSpec(tick = tick, note = note)
                                tuningSpec.add(ts)
                            }
                        }
                    }
                }
            }
        }

        Global.tuneTo.clear()
        Global.tuneTo.addAll(tuningSpec)

        send(TuneToRequestMessage())
    }

    override fun send(msg: Message, meToo: Boolean) {
        Global.send(this, msg, meToo)
    }

    private fun analyzeSequence() {
        val scene = AnalysisResults()

        val stage = Stage()
        stage.scene = scene
        stage.title = "Analysis of ${score.file?.name}"

        analyze(scene)

        stage.showAndWait()
    }

    private fun analyze(scene: AnalysisResults) {
        val ta = scene.ta
        val totalChords = score.countChords()
        val totalMeasures = score.countMeasures()
        val totalNotes = score.countNotes()
        var oneNotes = 0
        var twoNotes = 0
        var seven = 0
        var minor7 = 0
        var major7 = 0
        var triad = 0
        var minorTriad = 0
        var sus = 0
        var add = 0
        var other = 0
        var none = 0

        val range = HashMap<VoicePart, Pair<Pitch,Pitch>>()

        for (item in score.items()) {
            for (note in item.pitches) {
                val part = note.voicePart!!
                val pair = range.getOrDefault(part, Pair(Pitch(128),Pitch(0)))
                val low = if (note.n < pair.first.n) note else pair.first
                val high = if (note.n > pair.second.n) note else pair.second
                range[part] = Pair(low, high)
            }
            val ci = item.chord
            when {
                ci.chord === NoChord -> {
                    none++
                }
                ci.chord === OneNote -> {
                    oneNotes++
                }
                ci.chord.isInterval -> {
                    twoNotes++
                }
                ci.chord.intervals.contains(I("M3")) -> {
                    val int = ci.chord.intervals
                    when {
                        int.contains(I("m7")) -> seven++
                        int.contains(I("M7")) -> major7++
                        ci.chord.toString().indexOf("sus") >= 0 -> sus++
                        ci.chord.toString().indexOf("add") >= 0 -> add++
                        ci.chord.suffix == "" -> triad++
                        else -> other++
                    }
                }
                else -> {
                    val int = ci.chord.intervals
                    if (int.contains(I("m3"))) {
                        when {
                            int.contains(I("m7")) -> minor7++
                            ci.chord.toString().indexOf("add") >=0 -> add++
                            else -> minorTriad++
                        }
                    }
                }
            }
        }
        ta.appendText("$totalNotes notes in $totalChords chords in $totalMeasures measures\n\n")
        ta.appendText("Barbershop 7th: $seven\n")
        ta.appendText("Major triad: $triad\n")
        ta.appendText("Minor 7th: $minor7\n")
        ta.appendText("Major 7th: $major7\n")
        ta.appendText("Minor triad: $minorTriad\n")
        ta.appendText("Suspended: $sus\n")
        ta.appendText("\"Add\" chords: $add\n")
        ta.appendText("Two-note \"chord\": $twoNotes\n")
        ta.appendText("One-note \"chord\": $oneNotes\n")
        ta.appendText("Other: $other\n")
        ta.appendText("Unrecognized: $none\n")
        ta.appendText("\nRanges:\n")
        val list = range.toList().sortedBy {
            it.first
        }
        for (item in list) {
            ta.appendText("${item.first}: ${item.second.first.note}${item.second.first.getOctave()} " +
                    "- ${item.second.second.note}${item.second.second.getOctave()}\n")
        }
    }

    fun saveConfig() {
        savePreferences()
    }

    fun setKbdScaleFactor(factor: Number) {
        keyboardPanel.setFactor(factor.toDouble())

    }

    fun setStaffScaleFactor(factor: Number) {
        staffPanel.setFactor(factor.toDouble())
        staffPanel.refresh()
    }

    fun setLyricCanvas(canvas: Canvas) {
        lyricsPanel.setcanvas(canvas)
    }
}

class InstrumentText(private val label: Label) : MessageListener {
    init {
        subscribe(this, InstrumentChange, FileChange)
    }

    override fun receive(o: MessageCreator, msg: Message) {
        when (msg.t) {
            InstrumentChange -> {
                val ic = msg as InstrumentChangeMessage
                label.text = ic.newVal?.desc() ?: "<None>"
            }
            FileChange -> {
                val s = score
                Main.log("${s.countNotes()} notes and ${s.countChords()} chords " +
                        "in ${s.countMeasures()} measures")
            }
            else -> {
                // ignore
            }
        }
    }
}

fun getUserDataDirectory(): String {
    return System.getProperty("user.home") + File.separator + ".justchords" + File.separator
}