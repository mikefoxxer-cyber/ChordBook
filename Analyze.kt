import javafx.scene.Scene
import javafx.scene.control.TextArea
import tornadofx.*

class AnalysisResults(view: ResultsView = ResultsView()) : Scene(view.root) {
    val ta: TextArea = view.results
}

class ResultsView : View() {
    var results: TextArea
    override val root = borderpane {
        center = textarea {
            minHeight = 500.0
        }
    }

    init {
        results = root.center as TextArea
    }
}