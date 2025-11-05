package com.simiacryptus.cognotik.webui.session

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.threadPoolManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

abstract class SocketManager(
    val sessionId: Session,
    val dataStorage: StorageInterface? = null,
    val owner: User? = null,
    private val applicationClass: Class<*>,
) {
    private val messageStates = Collections.synchronizedMap(
        try {
            dataStorage?.getMessages(owner, sessionId) ?: LinkedHashMap()
        } catch (e: Exception) {
            log.error("Failed to load messages from storage for session: {}, using empty map", sessionId, e)
            LinkedHashMap()
        }
    )

    @Suppress("unused")
    private val createdBy = Thread.currentThread().stackTrace
    private val messageTimestamps = HashMap<String, Long>()
    private val sockets: MutableMap<ChatSocket, org.eclipse.jetty.websocket.api.Session> = ConcurrentHashMap()
    private val sendQueues: MutableMap<ChatSocket, Deque<String>> = ConcurrentHashMap()
    private val queueProcessing: MutableSet<ChatSocket> = ConcurrentHashMap.newKeySet()
    private val messageVersions = ConcurrentHashMap<String, AtomicInteger>()
    val pool get() = threadPoolManager.getPool(sessionId, owner)
    val scheduledThreadPoolExecutor get() = threadPoolManager.getScheduledPool(sessionId, owner)

    fun removeSocket(socket: ChatSocket) {
        log.debug("Removing socket: {} (id: {})", socket, System.identityHashCode(socket))
        trafficLog.info(
            "Removing socket: {} (id: {}), user: {}",
            socket,
            System.identityHashCode(socket),
            socket.user?.name ?: "anonymous"
        )

        try {

            sendQueues.remove(socket) // Clean up the send queue
            sockets.remove(socket)
            queueProcessing.remove(socket)
        } catch (e: Exception) {
            log.error("Error during socket cleanup for socket: {}", socket, e)
            trafficLog.error("Error during socket cleanup: {}", e.message)
        }
        trafficLog.debug("Socket removed, remaining connections: {}", sockets.size)
    }

    fun addSocket(socket: ChatSocket, session: org.eclipse.jetty.websocket.api.Session) {

        val user = getUser(session)
        log.debug("Adding socket: {} (id: {}) for user: {}", socket, System.identityHashCode(socket), user)
        trafficLog.info(
            "Adding socket: {} (id: {}), user: {}, remote: {}",
            socket,
            System.identityHashCode(socket),
            user?.name ?: "anonymous",
            session.remoteAddress
        )

        if (!ApplicationServices.authorizationManager.isAuthorized(
                applicationClass = applicationClass,
                user = user,
                operationType = OperationType.Read
            )
        ) {
            log.warn(
                "Unauthorized access attempt from user: {}, remote: {}",
                user?.name ?: "anonymous",
                session.remoteAddress
            )
            throw IllegalArgumentException("Unauthorized")
        }

        try {
            sockets[socket] = session
            sendQueues[socket] = ConcurrentLinkedDeque()
        } catch (e: Exception) {
            log.error("Error adding socket for user: {}", user?.name ?: "anonymous", e)
            throw e
        }
        trafficLog.debug("Socket added, active connections: {}", sockets.size)
    }

    fun newTask(
        root: Boolean = true,
        cancelable: Boolean = false,
    ): SessionTask {
        try {
            val operationID = randomID(root)
            val responseContents = divInitializer(operationID, cancelable)
//            log.debug(
//                "Creating new task with operationID: {}\n\t{}",
//                operationID, Thread.currentThread().stackTrace.joinToString("\n\t")
//            )
            trafficLog.debug("Creating new task with operationID: {}", operationID)
            send(responseContents)
            return SessionTask(
                messageID = operationID,
                buffer = mutableListOf(StringBuilder(responseContents)),
                spinner = SessionTask.spinner,
                ui = this,
            ).apply {
                add("")
            }
        } catch (e: Exception) {
            log.error("Failed to create new task", e)
            trafficLog.error("Failed to create new task: {}", e.message)
            throw e
        }
    }

    fun send(out: String) {
        if (out.isBlank()) {
            log.warn("Attempted to send an empty message")
            return
        }

        try {
            log.debug("Processing send message ({} bytes)", out.length)
            trafficLog.trace(
                "Processing send message ({} bytes): {}...",
                out.length,
                out.take(100) + (if (out.length > 100) "..." else "")
            )

            val split = out.split(',', ignoreCase = false, limit = 2)
            if (split.size < 2) {
                log.warn(
                    "Invalid message format ({} bytes), expected 'id,content' but got: {}",
                    out.length,
                    out.take(100)
                )
                trafficLog.warn("Invalid message format received, length: {} bytes", out.length)
                return
            }
            val messageID = split[0]
            if (messageID.isBlank()) {
                log.warn("Message ID cannot be blank")
                return
            }
            var newValue = split[1]
            if (newValue == "null") newValue = ""

            log.debug("Setting message - Key: {}, Content size: {} bytes", messageID, newValue.length)
            val version = setMessage(messageID, newValue)
            if (version < 0) {
                log.debug("Skipping duplicate message - Key: {}, Content size: {} bytes", messageID, newValue.length)
                return
            }
            if (newValue.isEmpty()) {
                log.debug("Skipping empty message - Key: {}, Content size: {} bytes", messageID, newValue.length)
                return
            }

            val (ver, v) = synchronized(stateLock) {
                val version = messageVersions[messageID]?.get()
                val value = messageStates[messageID]
                Pair(version, value)
            }

            trafficLog.debug(
                "Sending message - Key: {}, Version: {}, Content size: {} bytes",
                messageID, ver, v?.length ?: 0
            )

            sockets.keys.forEach { chatSocket ->
                try {
                    val deque = sendQueues.computeIfAbsent(chatSocket) { ConcurrentLinkedDeque() }
                    val queueMessage = "$messageID,$ver,$v"
                    deque.add(queueMessage)

                    log.trace(
                        "Queuing message for socket {} (id: {}): Key: {}, Queue message size: {} bytes",
                        chatSocket, System.identityHashCode(chatSocket), messageID, queueMessage.length
                    )

                    if (queueProcessing.add(chatSocket)) {
                        try {
                            ioPool.submit { processQueue(chatSocket) }
                        } catch (e: Exception) {
                            log.error(
                                "Failed to submit queue processing task for socket: {} (id: {})",
                                chatSocket, System.identityHashCode(chatSocket), e
                            )
                            queueProcessing.remove(chatSocket)
                        }
                    }
                } catch (e: Exception) {
                    log.error(
                        "Error preparing message for socket: {} (id: {})",
                        chatSocket, System.identityHashCode(chatSocket), e
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error in send method for session: {}", sessionId, e)
            trafficLog.error("Error in send method for session: {}, error: {}", sessionId, e.message)
        }
    }

    private fun processQueue(chatSocket: ChatSocket) {
        try {
            val deque = sendQueues[chatSocket] ?: return
            var msg: String?
            while (deque.poll().also { msg = it } != null) {
                val message = msg!!
                if (message.isBlank()) {
                    continue
                }

                val messageID = message.substringBefore(',')
                var processedMsg = message

                val maxMessageSize = 100000
                if (message.length > maxMessageSize) {
                    log.warn(
                        "Message too long - Key: {}, Content size: {} bytes, truncating to {} bytes",
                        messageID, message.length, maxMessageSize
                    )
                    processedMsg = message.substring(0, maxMessageSize)
                }

                try {
                    synchronized(chatSocket) {
                        if (!sockets.containsKey(chatSocket)) {
                            return
                        }
                        chatSocket.remote.sendString(processedMsg)
                    }
                } catch (e: Exception) {
                    log.error(
                        "Error sending message to socket: {} (id: {})",
                        chatSocket, System.identityHashCode(chatSocket), e
                    )
                    removeSocket(chatSocket)
                    return
                }
            }

            try {
                chatSocket.remote.flush()
            } catch (e: Exception) {
                log.error("Error flushing socket: {} (id: {})", chatSocket, System.identityHashCode(chatSocket), e)
                removeSocket(chatSocket)
            }
        } finally {
            queueProcessing.remove(chatSocket)
        }
    }

    private val stateLock = Any()


    fun getReplay(since: Long): List<String> {
        log.debug("Getting replay messages since: {}", since)
        trafficLog.debug("Getting replay messages since: {}", since)
        return synchronized(stateLock) {
            messageStates.entries
                .filter { (messageTimestamps[it.key] ?: 0L) > since }
                .map {
                    val version = messageVersions[it.key]?.get() ?: 1
                    "${it.key},$version,${it.value}"
                }
        }.also {
            val totalSize = it.sumOf { msg -> msg.length }
            trafficLog.debug("Returning {} replay messages, total size: {} bytes", it.size, totalSize)
        }
    }

    private fun setMessage(key: String, value: String) = synchronized(stateLock) {
        val existingValue = messageStates[key] ?: ""
        if (existingValue == value) {
            log.debug("Skipping update for key: {}, content is identical ({} bytes)", key, value.length)
            return@synchronized -1 // Message content is identical, do not update version or timestamp
        }
        if (existingValue.length == value.length) {
            log.debug("Odd update for key: {}, content length unchanged ({} bytes)", key, value.length)
        }
        try {
            log.debug("Updating message - Key: {}, Content size: {} bytes", key, value.length)
            dataStorage?.updateMessage(owner, sessionId, key, value)
            messageStates[key] = value // Using [] syntax for put
            messageTimestamps[key] = System.currentTimeMillis()
            messageVersions.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        } catch (e: Exception) {
            log.error("Error updating message state for key: $key", e)
            trafficLog.error("Error updating message state for key: {}, error: {}", key, e.message)
            -1 // Return error code
        }
    }

    fun onWebSocketText(socket: ChatSocket, message: String) {

//        log.debug(
//            "Received WebSocket message ({} bytes): {} from socket: {} (id: {})",
//            message.length,
//            message,
//            socket,
//            System.identityHashCode(socket)
//        )

        val maxMessageLength = 1000000
        if (message.length > maxMessageLength) {
            log.warn(
                "Message too long from socket: {}, length: {} bytes, limit: {} bytes",
                socket, message.length, maxMessageLength
            )
            send("""${randomID()},<div class="error">Message too long (${message.length} bytes). Maximum allowed: $maxMessageLength bytes.</div>""")
            return
        }


        val trimmed = message.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (trimmed.contains("\"type\":\"pong\"")) {
                log.debug("Received heartbeat pong - updating heartbeat timestamp.")

                return
            }
            if (trimmed.contains("\"type\":\"ping\"") || trimmed.contains("\"type\":\"heartbeat\"")) {
                //log.debug("Received heartbeat ping - sending pong response.")
                try {
                    socket.remote.sendString("{\"type\":\"pong\"}")
                } catch (e: Exception) {
                    log.error(
                        "Error sending pong response to socket: {} (id: {})",
                        socket, System.identityHashCode(socket), e
                    )
                    removeSocket(socket)
                }
                return
            }
        }

        if (!canWrite(socket.user)) {
            log.warn(
                "Unauthorized message from socket: {} (id: {}), user: {}",
                socket, System.identityHashCode(socket), socket.user?.name ?: "anonymous"
            )
            send("""${randomID()},<div class="error">Unauthorized message</div>""")
            return
        }

        try {
            pool.submit { processUserMessage(message, socket) }
        } catch (e: Exception) {
            log.error(
                "Failed to submit message processing task for socket: {} (id: {})",
                socket, System.identityHashCode(socket), e
            )
            send("""${randomID()},<div class="error">Failed to process message: ${e.message}</div>""")
        }
    }

    private fun processUserMessage(message: String, socket: ChatSocket) {
        try {
            log.debug(
                "Processing user message from socket: {} (id: {}), size: {} bytes",
                socket,
                System.identityHashCode(socket),
                message.length
            )
            val opCmdPattern = """![a-z]{3,7},.*""".toRegex()
            if (opCmdPattern.matches(message)) {
                val commaIndex = message.indexOf(",")
                if (commaIndex == -1 || commaIndex == message.length - 1) {
                    log.warn("Invalid command format from socket: {} (id: {})", socket, System.identityHashCode(socket))
                    return
                }
                val id = message.substring(1, commaIndex)
                val code = message.substring(commaIndex + 1)
                if (id.isBlank()) {
                    log.warn("Empty command ID from socket: {} (id: {})", socket, System.identityHashCode(socket))
                    return
                }
                trafficLog.debug("Processing command - ID: {}, Code size: {} bytes, Code: {}", id, code.length, code)
                onCmd(id, code)
            } else {
                trafficLog.debug(
                    "Processing user message from socket: {} (id: {}), size: {} bytes",
                    socket,
                    System.identityHashCode(socket),
                    message.length
                )
                onRun(message, socket)
            }
        } catch (e: Throwable) {
            log.error(
                "Error processing message from socket: {} (id: {}), message: {}",
                socket,
                System.identityHashCode(socket),
                message.take(100),
                e
            )
            trafficLog.error(
                "Error processing message from socket: {} (id: {}), error: {}",
                socket,
                System.identityHashCode(socket),
                e.message
            )
            try {
                send("""${randomID()},<div class="error">${MarkdownUtil.renderMarkdown(e.message ?: "Unknown error")}</div>""")
            } catch (sendError: Exception) {
                log.error("Failed to send error message", sendError)
            }
        }
    }

    open fun canWrite(user: User?) = ApplicationServices.authorizationManager.isAuthorized(
        applicationClass = applicationClass,
        user = user,
        operationType = OperationType.Write
    )

    val linkTriggers = mutableMapOf<String, Consumer<Unit>>()
    private val txtTriggers = mutableMapOf<String, Consumer<String>>()

    private fun onCmd(id: String, code: String) {
        require(id.isNotBlank()) { "Command ID cannot be blank" }
        require(code.isNotBlank()) { "Command code cannot be blank" }

        log.debug("Processing command - ID: {}, Code size: {} bytes, Code: {}", id, code.length, code)

        when {
            code == "link" -> {
                val consumer = linkTriggers.remove(id)
                    ?: throw IllegalArgumentException("No link handler found for ID: $id")
                trafficLog.debug("Executing link handler for ID: {}", id)
                consumer.accept(Unit)
            }

            code.startsWith("userTxt,") -> {
                val consumer = txtTriggers.remove(id)
                    ?: throw IllegalArgumentException("No input handler found for ID: $id")
                val text = code.substringAfter("userTxt,")
                val unencoded = try {
                    URLDecoder.decode(text, "UTF-8")
                } catch (e: Exception) {
                    log.error("Failed to decode user text for ID: {}", id, e)
                    text
                }
                trafficLog.debug(
                    "Executing text input handler for ID: {}, text size: {} bytes",
                    id, unencoded.length
                )
                consumer.accept(unencoded)
            }

            else -> {
                log.warn("Unknown command received: {} for ID: {}", code, id)
                throw IllegalArgumentException("Unknown command: $code")
            }
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

    /**
     * Creates a linked SocketManager for a new session that shares the same configuration
     * but operates independently with its own message state and socket connections.
     */
    open fun createLinkedManager(newSession: Session): SocketManager {
        log.debug("Creating linked manager for session: {}", newSession)
        trafficLog.info("Creating linked manager for session: {}, owner: {}", newSession, owner?.name ?: "anonymous")
        return ReadonlySocketManager(newSession, dataStorage, owner, applicationClass)
    }


    protected abstract fun onRun(
        userMessage: String,
        socket: ChatSocket,
    )

    fun getActiveSockets(): List<ChatSocket> {
        log.debug("Getting active sockets, count: {}", sockets.size)
        trafficLog.debug("Getting active sockets, count: {}", sockets.size)
        return try {
            sockets.keys.toList()
        } catch (e: Exception) {
            log.error("Error getting active sockets", e)
            emptyList()
        }
    }


    fun linkToSession(label: String): String =
        """<a href="#${sessionId}" target="_blank" class="linked-task-link">${label}</a>"""

    companion object {
        private val log = LoggerFactory.getLogger(SocketManager::class.java)
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
            return try {
                ApplicationServices.authenticationManager.getUser(
                    session.upgradeRequest?.cookies
                        ?.find { it.name == AuthenticationInterface.AUTH_COOKIE }
                        ?.value)
            } catch (e: Exception) {
                log.error("Error getting user from session", e)
                null
            }
        }
    }
}


class ReadonlySocketManager(
    newSession: Session,
    storageInterface: StorageInterface?,
    owner: User?,
    clazz: Class<*>
) : SocketManager(
    sessionId = newSession,
    dataStorage = storageInterface,
    owner = owner,
    applicationClass = clazz
) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        throw UnsupportedOperationException("onRun not implemented in linked manager")
    }

    override fun canWrite(user: User?): Boolean {
        return false
    }

}
