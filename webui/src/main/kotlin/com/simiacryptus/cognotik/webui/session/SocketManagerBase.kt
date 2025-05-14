package com.simiacryptus.cognotik.webui.session

import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.clientManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

abstract class SocketManagerBase(
    protected val session: Session,
    val dataStorage: StorageInterface?,
    protected val owner: User? = null,
    private val applicationClass: Class<*>,
) : SocketManager {
    private val messageStates: LinkedHashMap<String, String> = dataStorage?.getMessages(owner, session) ?: LinkedHashMap()
    private val messageTimestamps = HashMap<String, Long>()
    // messageStates is initialized from dataStorage or as a new LinkedHashMap. Access needs synchronization.
    private val sockets: MutableMap<ChatSocket, org.eclipse.jetty.websocket.api.Session> = ConcurrentHashMap()
    private val sendQueues: MutableMap<ChatSocket, Deque<String>> = ConcurrentHashMap()
    private val messageVersions = ConcurrentHashMap<String, AtomicInteger>() // Thread-safe for its own operations
    val pool get() = clientManager.getPool(session, owner)
    val scheduledThreadPoolExecutor get() = clientManager.getScheduledPool(session, owner, dataStorage)

    override fun removeSocket(socket: ChatSocket) {
        log.debug("Removing socket: {}", socket)
        trafficLog.info("Removing socket: {}, user: {}", socket, socket.user?.name ?: "anonymous")
        sockets.remove(socket)?.close()
        trafficLog.debug("Socket removed, remaining connections: {}", sockets.size)
    }

    override fun addSocket(socket: ChatSocket, session: org.eclipse.jetty.websocket.api.Session) {
        val user = getUser(session)
        log.debug("Adding socket: {} for user: {}", socket, user)
        trafficLog.info("Adding socket: {}, user: {}, remote: {}", socket, user?.name ?: "anonymous", session.remoteAddress)
        if (!ApplicationServices.authorizationManager.isAuthorized(
                applicationClass = applicationClass,
                user = user,
                operationType = OperationType.Read
            )
        ) throw IllegalArgumentException("Unauthorized")
        sockets[socket] = session
        trafficLog.debug("Socket added, active connections: {}", sockets.size)
    }

    fun newTask(
        cancelable: Boolean = false,
        root: Boolean = true
    ): SessionTask {
        val operationID = randomID(root)
        var responseContents = divInitializer(operationID, cancelable)
        log.debug("Creating new task with operationID: {}", operationID)
        trafficLog.debug("Creating new task with operationID: {}", operationID)
        send(responseContents)
        return SessionTaskImpl(operationID, responseContents, SessionTask.spinner)
    }

    private inner class SessionTaskImpl(
        operationID: String,
        responseContents: String,
        spinner: String = SessionTask.spinner,
        buffer: MutableList<StringBuilder> = mutableListOf(StringBuilder(responseContents))
    ) : SessionTask(
        messageID = operationID, buffer = buffer, spinner = spinner
    ) {

        override fun hrefLink(
            linkText: String,
            classname: String,
            id: String?,
            handler: Consumer<Unit>
        ): String {
            log.debug("Creating href link with text: {}", linkText)
            trafficLog.trace("Creating href link with text: {}", linkText)
            val operationID = randomID()
            linkTriggers[operationID] = handler
            return """<a class="$classname" data-id="$operationID"${
                when {
                    id != null -> """ id="$id""""
                    else -> ""
                }
            }>$linkText</a>"""
        }

        override fun send(html: String) = this@SocketManagerBase.send(html)
        override fun saveFile(relativePath: String, data: ByteArray): String {
            log.debug("Saving file at path: {}", relativePath)
            trafficLog.debug("Saving file at path: {}", relativePath)
            dataStorage?.getSessionDir(owner, session)?.let { dir ->
                dir.mkdirs()
                val resolve = dir.resolve(relativePath)
                resolve.parentFile.mkdirs()
                resolve.writeBytes(data)
            }
            return "fileIndex/$session/$relativePath"
        }

        override fun createFile(relativePath: String): Pair<String, File?> {
            log.debug("Saving file at path: {}", relativePath)
            trafficLog.debug("Creating file at path: {}", relativePath)
            return Pair("fileIndex/$session/$relativePath", dataStorage?.getSessionDir(owner, session)?.let { dir ->
                dir.mkdirs()
                val resolve = dir.resolve(relativePath)
                resolve.parentFile.mkdirs()
                resolve
            })
        }
    }

    fun send(out: String) {
        try {

            val split = out.split(',', ignoreCase = false, limit = 2)
            val messageID = split[0]
            var newValue = split[1]
            if (newValue == "null") {
                newValue = ""
            }
            if (setMessage(messageID, newValue) < 0) {
                log.debug("Skipping duplicate message - Key: {}, Value: {} bytes", messageID, newValue.length)
                trafficLog.trace("Skipping duplicate message - Key: {}, Value: {} bytes", messageID, newValue.length)
                return
            }
            if (out.isEmpty()) {
                log.debug("Skipping empty message - Key: {}, Value: {} bytes", messageID, newValue.length)
                trafficLog.trace("Skipping empty message - Key: {}, Value: {} bytes", messageID, newValue.length)
                return
            }
            try {
                val ver = messageVersions[messageID]?.get()
                val v = messageStates[messageID]
                trafficLog.debug(
                    //"Sending message - Key: {}, Version: {}, Size: {} bytes\n\t{}", messageID, ver, v?.length ?: 0, v?.indent("\t") ?: ""
                    String.format(
                        "Sending message - Key: %s, Version: %d, Size: %d bytes\n\t%s",
                        messageID,
                        ver,
                        v?.length ?: 0,
                        v?.indent("\t") ?: ""
                    ), RuntimeException()
                )

                sockets.keys.toTypedArray<ChatSocket>().forEach<ChatSocket> { chatSocket ->
                    try {
                        val deque = sendQueues.computeIfAbsent(chatSocket) { ConcurrentLinkedDeque() }
                        deque.add("$messageID,$ver,$v")
                        ioPool.submit {
                            try {
                                while (deque.isNotEmpty()) {
                                    var msg = deque.poll() ?: break
                                    if (msg.length > 100000) {
                                        log.warn("Message too long - Key: {}, Value: {} bytes", messageID, msg.length)
                                        trafficLog.warn("Message too long - Key: {}, Value: {} bytes", messageID, msg.length)
                                        msg = msg.substring(0, 100000)
                                    }
                                    // The message 'msg' from the deque is already in the format "messageID,version,value"
                                    // and represents the state at the time of queuing.
                                    synchronized(chatSocket) {
                                        trafficLog.trace(
                                            "Sending to socket: {}, message: {}...",
                                            chatSocket,
                                            msg.take(100) + (if (msg.length > 100) "..." else "")
                                        )
                                        chatSocket.remote.sendString(msg)
                                    }
                                }
                                chatSocket.remote.flush()
                            } catch (e: Exception) {
                                log.info("Error sending message", e)
                                trafficLog.error("Error sending message to socket: {}, error: {}", chatSocket, e.message)
                                trafficLog.error("Error sending message to socket: {}, error: {}", chatSocket, e.message)
                            }
                        }
                    } catch (e: Exception) {
                        log.info("Error sending message", e)
                        trafficLog.error("Error preparing message for socket: {}, error: {}", chatSocket, e.message)
                    }
                }
            } catch (e: Exception) {
                log.info("$session - $out", e)
                trafficLog.error("Error in send process for session: {}, error: {}", session, e.message)
            }
        } catch (e: Exception) {
            log.info("$session - $out", e)
            trafficLog.error("Error in send method for session: {}, error: {}", session, e.message)
        }
    }
    private val stateLock = Any() // Lock for messageStates, messageTimestamps, and related version logic


    final override fun getReplay(since: Long): List<String> {
        log.debug("Getting replay messages")
        trafficLog.debug("Getting replay messages since: {}", since)
        return synchronized(stateLock) {
            messageStates.entries
                .filter { (messageTimestamps[it.key] ?: 0L) > since }
                // computeIfAbsent on ConcurrentHashMap is safe. Initial version for replay is 1.
                .map { "${it.key},${messageVersions.computeIfAbsent(it.key) { AtomicInteger(1) }.get()},${it.value}" }
        }.also { trafficLog.debug("Returning {} replay messages", it.size) }
    }

    private fun setMessage(key: String, value: String): Int {


        return synchronized(stateLock) {
            if (messageStates.containsKey(key)) {
                if (messageStates[key] == value) {
                    return@synchronized -1 // Message content is identical, do not update version or timestamp
                }
            }
            // Persist first, then update in-memory state
            dataStorage?.updateMessage(owner, session, key, value)
            messageStates[key] = value // Using [] syntax for put
            messageTimestamps[key] = System.currentTimeMillis()
            // getOrPut on ConcurrentHashMap is atomic. AtomicInteger operations are atomic.
            // This ensures the version is incremented for new or changed messages.
            val newVersion = messageVersions.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
            newVersion
        }
    }

    final override fun onWebSocketText(socket: ChatSocket, message: String) {

        log.debug("Received WebSocket message: {} from socket: {}", message, socket)
        trafficLog.debug(
            "Received WebSocket message from socket: {}, user: {}, message: {}...",
            socket, socket.user?.name ?: "anonymous", message.take(100) + (if (message.length > 100) "..." else "")
        )

        val trimmed = message.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (trimmed.contains("\"type\":\"pong\"")) {
                log.debug("Received heartbeat pong - updating heartbeat timestamp.")
                trafficLog.trace("Received heartbeat pong from socket: {}", socket)

                return
            }
            if (trimmed.contains("\"type\":\"ping\"") || trimmed.contains("\"type\":\"heartbeat\"")) {
                log.debug("Received heartbeat ping - sending pong response.")
                trafficLog.trace("Received heartbeat ping from socket: {}, sending pong", socket)
                try {
                    socket.remote.sendString("{\"type\":\"pong\"}")
                } catch (e: Exception) {
                    log.info("Error sending pong response", e)
                    trafficLog.error("Error sending pong to socket: {}, error: {}", socket, e.message)
                }
                return
            }
        }
        if (canWrite(socket.user)) pool.submit {
            try {
                val opCmdPattern = """![a-z]{3,7},.*""".toRegex()
                if (opCmdPattern.matches(message)) {
                    val id = message.substring(1, message.indexOf(","))
                    val code = message.substring(id.length + 2)
                    trafficLog.debug("Processing command - ID: {}, Code: {}", id, code)
                    onCmd(id, code)
                } else {
                    trafficLog.debug("Processing user message from socket: {}, length: {}", socket, message.length)
                    onRun(message, socket)
                }
            } catch (e: Throwable) {
                log.error("$session - Error processing message: $message", e)
                trafficLog.error("Error processing message from socket: {}, error: {}", socket, e.message)
                send("""${randomID()},<div class="error">${MarkdownUtil.renderMarkdown(e.message ?: "")}</div>""")
            }
        } else {
            log.warn("$session - Unauthorized message: $message")
            trafficLog.warn("Unauthorized message from socket: {}, user: {}", socket, socket.user?.name ?: "anonymous")
            send("""${randomID()},<div class="error">Unauthorized message</div>""")
        }
    }

    open fun canWrite(user: User?) = ApplicationServices.authorizationManager.isAuthorized(
        applicationClass = applicationClass,
        user = user,
        operationType = OperationType.Write
    )

    private val linkTriggers = mutableMapOf<String, Consumer<Unit>>()
    private val txtTriggers = mutableMapOf<String, Consumer<String>>()
    private fun onCmd(id: String, code: String) {
        log.debug("Processing command - ID: {}, Code: {}", id, code)
        if (code == "link") {
            val consumer = linkTriggers[id]
            consumer ?: throw IllegalArgumentException("No link handler found")
            trafficLog.debug("Executing link handler for ID: {}", id)
            consumer.accept(Unit)
        } else if (code.startsWith("userTxt,")) {
            val consumer = txtTriggers[id]
            consumer ?: throw IllegalArgumentException("No input handler found")
            val text = code.substringAfter("userTxt,")
            val unencoded = URLDecoder.decode(text, "UTF-8")
            trafficLog.debug("Executing text input handler for ID: {}, text length: {}", id, unencoded.length)
            consumer.accept(unencoded)
        } else {
            trafficLog.warn("Unknown command received: {}", code)
            throw IllegalArgumentException("Unknown command: $code")
        }
    }

    fun hrefLink(
        linkText: String,
        classname: String = "href-link",
        id: String? = null,
        handler: Consumer<Unit>
    ): String {
        log.debug("Creating href link with text: {}", linkText)
        trafficLog.trace("Creating href link with text: {}", linkText)
        val operationID = randomID()
        linkTriggers[operationID] = handler
        return """<a class="$classname" data-id="$operationID"${
            when {
                id != null -> """ id="$id""""
                else -> ""
            }
        }>$linkText</a>"""
    }

    fun textInput(handler: Consumer<String>): String {
        log.debug("Creating text input")
        trafficLog.trace("Creating text input field")
        val operationID = randomID()
        txtTriggers[operationID] = handler

        return """<div class="reply-form">
                   <textarea class="reply-input" data-id="$operationID" rows="3" placeholder="Type a message"></textarea>
                   <button class="text-submit-button" data-id="$operationID">Send</button>
               </div>""".trimIndent()
    }

    protected abstract fun onRun(
        userMessage: String,
        socket: ChatSocket,
    )
    override fun getActiveSockets(): List<ChatSocket> {
        log.debug("Getting active sockets, count: {}", sockets.size)
        trafficLog.debug("Getting active sockets, count: {}", sockets.size)
        return sockets.keys.toList()
    }


    companion object {
        private val log = LoggerFactory.getLogger(SocketManagerBase::class.java)
        private val trafficLog = LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.session")
        private val ioPool = Executors.newCachedThreadPool()
        private val range1 = ('a'..'y').toList().toTypedArray()
        private val range2 = range1 + 'z'

        fun randomID(root: Boolean = true): String {
            val random = Random()
            val joinToString = (if (root) range1[random.nextInt(range1.size)] else "z").toString() +
                    (0..4).map { range2[random.nextInt(range2.size)] }.joinToString("")
            return joinToString
        }

        fun divInitializer(operationID: String = randomID(), cancelable: Boolean): String =
            if (!cancelable) """$operationID,""" else
                """$operationID,<button class="cancel-button" data-id="$operationID">&times;</button>"""

        fun getUser(session: org.eclipse.jetty.websocket.api.Session): User? {
            log.debug("Getting user from session: {}", session)
            trafficLog.trace("Getting user from session: {}", session.remoteAddress)
            return session.upgradeRequest.cookies?.find { it.name == AuthenticationInterface.AUTH_COOKIE }?.value.let {
                ApplicationServices.authenticationManager.getUser(it)
            }
        }
    }
}