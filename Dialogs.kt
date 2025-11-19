import Global.volume
import VoiceParts.allVPs
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*
import kotlin.math.round
import kotlin.properties.Delegates

fun setStyles(d: TextInputDialog, cls: String = "") {
    val sheets = Main.getGraphCanvas().scene.stylesheets
    d.dialogPane.stylesheets.addAll(sheets)
    d.dialogPane.styleClass.add("jcdialog")
    if (cls != "") {
        d.dialogPane.styleClass.add(cls)
    }
}

class DurationDialog(value: String) : TextInputDialog(value) {
    init{
        title = "Duration"
        headerText = "Enter milliseconds"
        contentText = "Duration = "
        setStyles(this, "dg_duration")
    }
}

class DelayDialog(value: String) : TextInputDialog(value) {
    init {
        title = "Delay"
        headerText = "Enter milliseconds"
        contentText = "Delay = "
        setStyles(this, "dg_delay")
    }
}

class VolumeDialog : TextInputDialog() {
    private lateinit var vol: ObjectProperty<Int>
    private lateinit var fac: SpinnerValueFactory.IntegerSpinnerValueFactory
    private lateinit var label2: Label
    private val panel = hbox {
        label2 = label("Volume = ") {
            alignment = Pos.CENTER
        }
        fac = SpinnerValueFactory.IntegerSpinnerValueFactory(5, 125, volume)
        fac.amountToStepBy = 5
        vol = fac.valueProperty()
        spinner(fac) {
            isEditable = true
            editor.focusedProperty().addListener(ChangeListener<Boolean> { _, old, _ ->
                run {
                    if (old) {
                        val d = editor.text.toDoubleOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                        val v = round(d / 5.0).toInt() * 5
                        editor.text = v.toString()
                        vol.set(v)
                    }
                }
            })
            editor.onMouseExited = EventHandler {
                val d = editor.text.toDoubleOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                val v = round(d / 5.0).toInt() * 5
                editor.text = v.toString()
                vol.set(v)
            }
        }
    }

    init {
        title = "Volume"
        headerText = "Select new volume level"
        val dp = dialogPane

        val gridPane = dp.lookupAll("GridPane").toList()[1]
        gridPane.replaceChildren(panel)
        resultConverter = MyResult(volume.toString(), fac)
        setStyles(this, "dg_volume")
    }
}

class MyResult(private val oldValue: String, private val fac: SpinnerValueFactory.IntegerSpinnerValueFactory) : Callback<ButtonType, String> {
    override fun call(bt: ButtonType) : String {
        return if (bt == ButtonType.OK)
            fac.value.toString()
        else
            oldValue
    }
}

class MetronomeDialog : TextInputDialog() {
    private lateinit var clickVolume: ObjectProperty<Int>
    private lateinit var volFac: SpinnerValueFactory.IntegerSpinnerValueFactory
    private lateinit var midiFac: SpinnerValueFactory.IntegerSpinnerValueFactory
    private lateinit var noteValue: ComboBox<String>
    private lateinit var midiNote: ObjectProperty<Int>
    private val savedMidi = Global.clickNote
    private val savedVolume = Global.clickVolume
    private val savedDuration = Global.noteValue
    var selectedNoteValue = SimpleStringProperty()
    private val noteValues: ObservableList<String> = FXCollections.observableArrayList(
        "Auto", "Sixteenth", "Eighth", "Dotted eighth", "Quarter", "Dotted quarter",
        "Half", "Dotted half", "Whole")
    private val panel = gridpane {
        row {
            label("Click volume") {
                alignment = Pos.CENTER
            }
            volFac = SpinnerValueFactory.IntegerSpinnerValueFactory(0, 127)
            volFac.amountToStepBy = 10
            clickVolume = volFac.valueProperty()
            clickVolume.set(Global.clickVolume)
            spinner(volFac) {
                isEditable = true
                editor.focusedProperty().addListener(ChangeListener<Boolean> { _, old, _ ->
                    run {
                        if (old) {
                            val v = editor.text.toIntOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                            editor.text = v.toString()
                            clickVolume.set(v)
                        }
                    }
                }
                )
                editor.onMouseExited = EventHandler {
                    val v = editor.text.toIntOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                    editor.text = v.toString()
                    clickVolume.set(v)
                }
            }
        }
        row {
            label("Note value") {
                alignment = Pos.CENTER
            }
            noteValue =  combobox<String> (selectedNoteValue, noteValues)
            noteValue.selectionModel.select(Global.noteValue.capitalize())
        }
        row {
            label("MIDI note") {
                alignment = Pos.CENTER
            }
            midiFac = SpinnerValueFactory.IntegerSpinnerValueFactory(0, 127)
            midiNote = midiFac.valueProperty()
            spinner(midiFac) {
                isEditable = true
                editor.focusedProperty().addListener(ChangeListener<Boolean> { _, old, _ ->
                    run {
                        if (old) {
                            val d = editor.text.toIntOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                            midiNote.set(d)
                        }
                    }
                }
                )
                editor.onMouseExited = EventHandler {
                    val d = editor.text.toIntOrNull() ?: throw ErrorMessage("Bad value: ${editor.text}")
                    midiNote.set(d)
                }
            }
        }
        row {
            button("Audition") {
                action {
                    Global.click()
                }
                useMaxWidth = true
                gridpaneConstraints { columnSpan = 2 }
            }
        }
    }

    fun revert() {
        Global.noteValue = savedDuration
        Global.clickVolume = savedVolume
        Global.clickNote = savedMidi
    }

    init {
        title = "Metronome"
        headerText = "Choose click settings"
        midiNote.set(Global.clickNote)
        val dp = dialogPane
        val gridPane = dp.lookupAll("GridPane").toList()[1]
        gridPane.replaceChildren(panel)
        resultConverter = MyResult(clickVolume.toString(), volFac)
        clickVolume.addListener(ChangeListener<Number> { _, _, new ->
            run {
                Global.clickVolume = new.toInt()
                Global.click()
            }
        }
        )
        midiNote.addListener(ChangeListener<Number> { _, _, new ->
            run {
                Global.clickNote = new.toInt()
                Global.click()
            }
        }
        )
        setStyles(this, "dg_metronome")
    }
}

class SplitDialog(val arr: ArrayList<String>) : TextInputDialog() {
    private val checkBoxes = ArrayList<CheckBox>()
    private val panel = gridpane {
        row {
            label("Split?")
            label("Label") {
                gridpaneConstraints { marginLeftRight(5.0) }
            }
            label("Parts")
        }
    }

    init {
        title = "Split Staves"
        headerText = "Choose Staves to Split"
        for (i in arr.indices) {
            val label = getStaffLabel(arr[i])
            val sb = StringBuilder()
            val id = VoiceParts.get(arr[i])!!
            for (vp in allVPs) {
                if (vp.part == id.part && vp.voice >= 0) {
                    sb.append("${vp.shortName} ")
                }
            }
            val parts = sb.toString()
            val cb = CheckBox(arr[i])
            cb.userData = arr[i]
            checkBoxes.add(cb)
            cb.isSelected = true
            val lab = Label(label)
            lab.gridpaneConstraints { marginLeftRight(5.0) }
            panel.addRow(i + 1,
                cb,
                lab,
                Label(parts))
        }
        val gridPane = dialogPane.lookupAll("GridPane").toList()[1]
        gridPane.replaceChildren(panel)
        resultConverter = SplitResultConverter(checkBoxes)
        setStyles(this, "dg_split")
    }
}

class SplitResultConverter(val arr: ArrayList<CheckBox>) : Callback<ButtonType, String> {
    override fun call(bt: ButtonType): String {
        return if (bt == ButtonType.OK) {
            val sb = StringBuilder()
            for (cb in arr) {
                if (cb.isSelected) {
                    sb.append(cb.userData as String)
                    sb.append(";")
                }
            }
            if (sb.isNotEmpty())
                sb.setLength(sb.length - 1)
            sb.toString()
        } else {
            ""
        }
    }
}

class ScaleDialog : TextInputDialog() {
    private lateinit var slider1: Slider
    private lateinit var slider2: Slider
    private lateinit var valLabel1: Label
    private lateinit var valLabel2: Label
    private var oldVal1 by Delegates.notNull<Double>()
    private var oldVal2 by Delegates.notNull<Double>()
    private val panel = gridpane {
        row {
            label("Keyboard scale factor: ") {
                alignment = Pos.CENTER
            }
            valLabel1 = label(String.format("%5.2f", 0.0))
            slider1 = slider(0.2, 10.0, 1.0) {
                blockIncrement = 0.1
                isShowTickMarks = true
                isShowTickLabels = true
                majorTickUnit = 1.0
                useMaxWidth = true
                gridpaneColumnConstraints {
                    hgrow = Priority.ALWAYS
                }
            }
        }
        row {
            label("Staff scale factor: ") {
                alignment = Pos.CENTER
            }
            valLabel2 = label(String.format("%5.2f", 0.0))
            slider2 = slider(0.2, 10.0, 1.0) {
                blockIncrement = 0.1
                isShowTickMarks = true
                isShowTickLabels = true
                majorTickUnit = 1.0
                useMaxWidth = true
                gridpaneColumnConstraints {
                    hgrow = Priority.ALWAYS
                }
            }
        }
        useMaxWidth = true
        gridpaneColumnConstraints {
            hgrow = Priority.ALWAYS
        }
    }
    init {
        title = "Scale Factors"
        headerText = "Set scale factor for keyboard and staff"
        oldVal1 = Global.kbdScale
        oldVal2 = Global.staffScale
        valLabel1.text = String.format("%5.2f", oldVal1)
        valLabel2.text = String.format("%5.2f", oldVal2)
        slider1.value = oldVal1
        slider2.value = oldVal2
        val dp = dialogPane
        val gridPane = dp.lookupAll("GridPane").toList()[1]
        gridPane.replaceChildren(*panel.children.toTypedArray())
        resultConverter = Callback { bt ->
            if (bt == ButtonType.OK)
                "${slider1.value}:${slider2.value}"
            else
                "${oldVal1}:${oldVal2}"
        }
        slider1.valueProperty().addListener(ChangeListener<Number> { _, _, new ->
            run {
                valLabel1.text = String.format("%5.2f", new)
                Main.setKbdScaleFactor(new)
            }
        })
        slider2.valueProperty().addListener(ChangeListener<Number> { _, _, new ->
            run {
                valLabel2.text = String.format("%5.2f", new)
                Main.setStaffScaleFactor(new)
            }
        })
        setStyles(this, "dg_scale")
    }
}