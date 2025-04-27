package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManagerBase
import com.simiacryptus.jopenai.describe.Description
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

open class ApplicationInterface(val socketManager: SocketManagerBase?) {

    @Description("Returns html for a link that will trigger the given handler when clicked.")
    open fun hrefLink(
        @Description("The text to display in the link")
        linkText: String,
        @Description("The css class to apply to the link")
        classname: String = """href-link""",
        @Description("The id to apply to the link")
        id: String? = null,
        @Description("The handler to trigger when the link is clicked")
        handler: Consumer<Unit>,
    ) = socketManager!!.hrefLink(linkText, classname, id, oneAtATime(handler))

    @Description("Returns html for a text input form that will trigger the given handler when submitted.")
    open fun textInput(
        @Description("The handler to trigger when the form is submitted")
        handler: Consumer<String>
    ): String = socketManager!!.textInput(oneAtATime(handler))

    @Description("Creates a new 'task' that can be used to display the progress of a long-running operation.")
    open fun newTask(
        root: Boolean = true
    ): SessionTask = socketManager!!.newTask(cancelable = false, root = root)

    companion object {
        fun <T> oneAtATime(handler: Consumer<T>): Consumer<T> {
            val guard = AtomicBoolean(false)
            return Consumer { t ->
                if (guard.getAndSet(true)) return@Consumer
                handler.accept(t)
                guard.set(false)
            }
        }
    }

}