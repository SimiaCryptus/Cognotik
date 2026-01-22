package cognotik.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.BrowseUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.servlet.SymbolGraphServlet
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File

class SymbolGraphAction : BaseAction() {
    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val root = File(project.basePath ?: return)
        Thread {
            try {
                val server = CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                )
                server.context!!.addServlet(ServletHolder(SymbolGraphServlet(SymbolGraphService().apply {
                    load(root.resolve("symbol_graph.json"))
                })), "/symbol_index/*")
                BrowseUtil.browse(server.server.uri.resolve("/symbol_index"))
            } catch (e: Throwable) {
                log.warn("Error launching Symbol Graph", e)
            }
        }.start()
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        val project = event.project ?: return false
        val root = File(project.basePath ?: return false)
        val graphFile = root.resolve("symbol_graph.json")
        if (!graphFile.exists()) return false
        return super.isEnabled(event)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SymbolGraphAction::class.java)
    }
}