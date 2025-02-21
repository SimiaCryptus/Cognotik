package com.simiacryptus.skyenet.webui.session

import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.ApplicationServices.clientManager
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.chat.ChatSocket
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
  protected val dataStorage: StorageInterface?,
  protected val owner: User? = null,
  private val messageStates: LinkedHashMap<String, String> = dataStorage?.getMessages(
    owner, session
  ) ?: LinkedHashMap(),
  private val applicationClass: Class<*>,
) : SocketManager {
  private val sockets: MutableMap<ChatSocket, org.eclipse.jetty.websocket.api.Session> = ConcurrentHashMap()
  private val sendQueues: MutableMap<ChatSocket, Deque<String>> = ConcurrentHashMap()
  private val messageVersions = HashMap<String, AtomicInteger>()
  val pool get() = clientManager.getPool(session, owner)
  val scheduledThreadPoolExecutor get() = clientManager.getScheduledPool(session, owner, dataStorage)

  override fun removeSocket(socket: ChatSocket) {
    log.debug("Removing socket: {}", socket)
    sockets.remove(socket)?.close()
  }

  override fun addSocket(socket: ChatSocket, session: org.eclipse.jetty.websocket.api.Session) {
    val user = getUser(session)
    log.debug("Adding socket: {} for user: {}", socket, user)
    if (!ApplicationServices.authorizationManager.isAuthorized(
        applicationClass = applicationClass,
        user = user,
        operationType = OperationType.Read
      )
    ) throw IllegalArgumentException("Unauthorized")
    sockets[socket] = session
  }

  fun newTask(
    cancelable: Boolean = false,
    root: Boolean = true
  ): SessionTask {
    val operationID = randomID(root)
    var responseContents = divInitializer(operationID, cancelable)
    log.debug("Creating new task with operationID: {}", operationID)
    send(responseContents)
    return SessionTaskImpl(operationID, responseContents, SessionTask.spinner)
  }


  private inner class SessionTaskImpl(
    operationID: String,
    responseContents: String,
    spinner: String = SessionTask.spinner,
    private val buffer: MutableList<StringBuilder> = mutableListOf(StringBuilder(responseContents))
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
      return Pair("fileIndex/$session/$relativePath", dataStorage?.getDataDir(owner, session)?.let { dir ->
        dir.mkdirs()
        val resolve = dir.resolve(relativePath)
        resolve.parentFile.mkdirs()
        resolve
      })
    }
  }

  fun send(out: String) {
    try {
      //log.debug("Sending message: {}", out)
      val split = out.split(',', ignoreCase = false, limit = 2)
      val messageID = split[0]
      var newValue = split[1]
      if (newValue == "null") {
        newValue = ""
      }
      if (setMessage(messageID, newValue) < 0) {
        log.debug("Skipping duplicate message - Key: {}, Value: {} bytes", messageID, newValue.length)
        return
      }
      if (out.isEmpty()) {
        log.debug("Skipping empty message - Key: {}, Value: {} bytes", messageID, newValue.length)
        return
      }
      try {
        val ver = messageVersions[messageID]?.get()
        val v = messageStates[messageID]
//        log.debug("Publish Msg: {} - {} - {} - {} bytes", session, messageID, ver, v?.length)
        sockets.keys.toTypedArray<ChatSocket>().forEach<ChatSocket> { chatSocket ->
          try {
            val deque = sendQueues.computeIfAbsent(chatSocket) { ConcurrentLinkedDeque() }
            deque.add("$messageID,$ver,$v")
            ioPool.submit {
              try {
                while (deque.isNotEmpty()) {
                  var msg = deque.poll() ?: break
                  try {
                    val (messageID, _, _) = msg.split(',', ignoreCase = false, limit = 3)
                    val ver = messageVersions[messageID]?.get()
                    val v = messageStates[messageID]
                    msg = "$messageID,$ver,$v"
                  } finally {
//                    log.debug("Sending message: {} to socket: {}", msg, chatSocket)
                    synchronized(chatSocket) {
                      chatSocket.remote.sendString(msg)
                    }
                  }
                }
                chatSocket.remote.flush()
              } catch (e: Exception) {
                log.info("Error sending message", e)
              }
            }
          } catch (e: Exception) {
            log.info("Error sending message", e)
          }
        }
      } catch (e: Exception) {
        log.info("$session - $out", e)
      }
    } catch (e: Exception) {
      log.info("$session - $out", e)
    }
  }

  final override fun getReplay(): List<String> {
    log.debug("Getting replay messages")
    return messageStates.entries.map {
      "${it.key},${messageVersions.computeIfAbsent(it.key) { AtomicInteger(1) }.get()},${it.value}"
    }
  }

  private fun setMessage(key: String, value: String): Int {
//    log.debug("Setting message - Key: {}, Value: {}", key, value)
    if (messageStates.containsKey(key)) {
      if (messageStates[key] == value) {
        return -1
      }
    }
    dataStorage?.updateMessage(owner, session, key, value)
    messageStates.put(key, value)
    val incrementAndGet = synchronized(messageVersions)
    { messageVersions.getOrPut(key) { AtomicInteger(0) } }.incrementAndGet()
//    log.debug("Setting message - Key: {}, v{}, Value: {} bytes", key, incrementAndGet, value.length)
    return incrementAndGet
  }

  final override fun onWebSocketText(socket: ChatSocket, message: String) {
//      log.debug("{} - Received message: {}", session, message)
    log.debug("Received WebSocket message: {} from socket: {}", message, socket)
    // Intercept and properly handle heartbeat ping/pong messages
    val trimmed = message.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      if (trimmed.contains("\"type\":\"pong\"")) {
        log.debug("Received heartbeat pong - updating heartbeat timestamp.")
        // Optionally update a heartbeat timestamp here if needed
        return
      }
      if (trimmed.contains("\"type\":\"ping\"")) {
        log.debug("Received heartbeat ping - sending pong response.")
        try {
          socket.remote.sendString("{\"type\":\"pong\"}")
        } catch (e: Exception) {
          log.info("Error sending pong response", e)
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
          onCmd(id, code)
        } else {
          onRun(message, socket)
        }
      } catch (e: Throwable) {
        log.error("$session - Error processing message: $message", e)
        send("""${randomID()},<div class="error">${MarkdownUtil.renderMarkdown(e.message ?: "")}</div>""")
      }
    } else {
      log.warn("$session - Unauthorized message: $message")
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
      consumer.accept(Unit)
    } else if (code.startsWith("userTxt,")) {
      val consumer = txtTriggers[id]
      consumer ?: throw IllegalArgumentException("No input handler found")
      val text = code.substringAfter("userTxt,")
      val unencoded = URLDecoder.decode(text, "UTF-8")
      consumer.accept(unencoded)
    } else {
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
    val operationID = randomID()
    txtTriggers[operationID] = handler
    //language=HTML
    return """<div class="reply-form">
                   <textarea class="reply-input" data-id="$operationID" rows="3" placeholder="Type a message"></textarea>
                   <button class="text-submit-button" data-id="$operationID">Send</button>
               </div>""".trimIndent()
  }


  protected abstract fun onRun(
    userMessage: String,
    socket: ChatSocket,
  )

  companion object {
    private val log = LoggerFactory.getLogger(SocketManagerBase::class.java)

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
      return session.upgradeRequest.cookies?.find { it.name == AuthenticationInterface.AUTH_COOKIE }?.value.let {
        ApplicationServices.authenticationManager.getUser(it)
      }
    }
  }
}