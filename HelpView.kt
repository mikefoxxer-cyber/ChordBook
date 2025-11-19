import javafx.scene.layout.Priority
import javafx.scene.web.WebView
import tornadofx.*
import java.io.File
import java.net.URL

class HelpView(val t: String) : Fragment() {
    private lateinit var view : WebView
    override val root = vbox {
        view = webview {
            vboxConstraints { vGrow = Priority.ALWAYS }
        }
    }

    init {
        title = t
        val dir = File(getUserDataDirectory())
        val file = File(dir, "style.css")
        if (file.exists())
            view.engine.userStyleSheetLocation = fileToURL(file).toString()
        else
            view.engine.userStyleSheetLocation = javaClass.getResource("style.css").path
        view.engine.titleProperty().addListener { _, _, newValue ->
            title = newValue ?: "Help"
        }
    }

    fun load(url: String) {
        view.engine.load(url)
        view.show()
    }
}

fun fileToURL(file: File) : URL {
    val path = file.path.replace('\\', '/')
    if (file.path.contains('\\'))
        return URL("file:///$path")
    else
        return URL("file://$path")
}