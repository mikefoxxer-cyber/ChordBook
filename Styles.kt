import javafx.geometry.Pos
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val hoverColor = c("#0097ca")
    }

    init {
        button {
            borderWidth += box(5.px)
            and(hover) {
                backgroundColor += hoverColor
                textFill = Color.WHITE
            }
        }
        label {
            textAlignment = TextAlignment.CENTER
            padding = box(5.px)
            alignment = Pos.CENTER_LEFT
        }
    }
}