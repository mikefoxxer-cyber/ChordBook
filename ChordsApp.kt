import javafx.application.Application
import javafx.scene.text.Font
import tornadofx.*
import java.io.File
import java.io.FileReader
import kotlin.system.exitProcess

class ChordsApp: App(ChordsMain::class) {
    init {
        val userDir = getUserDataDirectory()
        val file = File(userDir, "style.css")
        if (file.exists()) {
            importStylesheet(file.toPath())
        } else {
            importStylesheet("/style.css")
            val dir = File(getUserDataDirectory())
            if (!dir.exists())
                dir.mkdir()
            file.createNewFile()
            val w = file.printWriter()
            val res = javaClass.getResource("style.css")
            val r = FileReader(File(res.file))
            r.forEachLine {
                w.write(it + "\n")
            }
            r.close()
            w.close()
        }
    }

    override fun stop() {
        Main.saveConfig()
        exitProcess(0)
    }
}

fun main(args: Array<String>) {
    if (args.contains("--help") ||
        args.contains("-help") ||
        args.contains("-h")) {
        Main.usage()
        exitProcess(0)
    } else if (args.contains("--overview")) {
        Main.overview()
        exitProcess(0)
    }
    Font.loadFont(ChordsApp::class.java.getResource("MAESTRO_.TTF").toExternalForm(), 24.0)
    Font.loadFont(ChordsApp::class.java.getResource("MAESTRO_.TTF").toExternalForm(), 36.0)
    Font.loadFont(ChordsApp::class.java.getResource("MAESTRO_.TTF").toExternalForm(), 42.0)
    Font.loadFont(ChordsApp::class.java.getResource("MaestroTimes.ttf").toExternalForm(), 18.0)
    Application.launch(ChordsApp::class.java, *args)
}