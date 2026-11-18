package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPContent
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Timeout(20)
class McpProtocolTest {

  private val session = Session.newUserID()

  private fun simpleTool(
    name: String,
    description: String = "Tool $name",
    handler: (JsonNode, MCPCallContext) -> MCPToolResult = { _, _ ->
      MCPToolResult(content = listOf(MCPContent.Text("ok:$name")))
    },
  ): MCPTool = object : MCPTool {
    override val definition = MCPToolDefinition(
      name = name,
      description = description,
      inputSchema = McpJson.obj().put("type", "object"),
    )

    override fun call(arguments: JsonNode, context: MCPCallContext): MCPToolResult =
      handler(arguments, context)
  }

  private fun sendRawAndAwaitReply(
    clientTransport: McpTransport,
    request: JsonNode,
    responses: MutableMap<Long, CompletableFuture<JsonNode>>,
  ): JsonNode {
    val id = request.get("id").asLong()
    val future = CompletableFuture<JsonNode>()
    responses[id] = future
    clientTransport.send(request)
    return future.get(5, TimeUnit.SECONDS)
  }

  private fun startRawPeer(transport: McpTransport): MutableMap<Long, CompletableFuture<JsonNode>> {
    val responses = java.util.concurrent.ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()
    transport.start(
      handler = { msg ->
        val idNode = msg.get("id")
        if (idNode != null && idNode.canConvertToLong()) {
          responses.remove(idNode.asLong())?.complete(msg)
        }
      },
      onClosed = {
        val err = IOException("Transport closed")
        responses.values.forEach { it.completeExceptionally(err) }
      },
    )
    return responses
  }

  private class RecordingTransport(
    private val delegate: McpTransport,
  ) : McpTransport {
    val outbound = CopyOnWriteArrayList<JsonNode>()
    val inbound = CopyOnWriteArrayList<JsonNode>()
    @Volatile
    var onOutbound: (JsonNode) -> Unit = {}
    @Volatile
    var onInboundAfterDispatch: (JsonNode) -> Unit = {}

    override fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit) {
      delegate.start(
        handler = { msg ->
          inbound.add(msg)
          handler(msg)
          onInboundAfterDispatch(msg)
        },
        onClosed = onClosed,
      )
    }

    override fun send(message: JsonNode) {
      outbound.add(message)
      onOutbound(message)
      delegate.send(message)
    }

    override fun close() = delegate.close()
  }

  // -------------------------------------------------------------------------
  // 1. Initialize, protocol version negotiation, notifications/initialized
  // -------------------------------------------------------------------------

  @Test
  fun `initialize negotiates supported and fallback protocol versions`() {
    val (clientSide, serverSide) = InMemoryTransport.pair()
    val host = McpServerHost(
      tools = listOf(simpleTool("echo")),
      transport = serverSide,
      session = session,
      serverName = "test-server",
      serverVersion = "2.5.0",
      listChanged = true,
    ).start()
    val responses = startRawPeer(clientSide)

    try {
      val supportedReply = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(1, "initialize", McpJson.obj().put("protocolVersion", "2025-03-26")),
        responses,
      )
      assertEquals("2025-03-26", supportedReply.path("result").path("protocolVersion").asText())
      assertEquals("2025-03-26", host.negotiatedProtocolVersion)
      assertTrue(supportedReply.path("result").path("capabilities").path("tools").path("listChanged").asBoolean())
      assertEquals("test-server", supportedReply.path("result").path("serverInfo").path("name").asText())
      assertEquals("2.5.0", supportedReply.path("result").path("serverInfo").path("version").asText())

      val fallbackReply = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(2, "initialize", McpJson.obj().put("protocolVersion", "1999-01-01")),
        responses,
      )
      assertEquals(McpJson.LATEST_PROTOCOL_VERSION, fallbackReply.path("result").path("protocolVersion").asText())
      assertEquals(McpJson.LATEST_PROTOCOL_VERSION, host.negotiatedProtocolVersion)
    } finally {
      clientSide.close()
      host.close()
    }
  }

  @Test
  fun `client connect negotiates protocol and rejects unsupported server version`() {
    McpClientToolProvider.loopback("loopback-init", listOf(simpleTool("ping_tool")), session).use { client ->
      client.connect()
      assertEquals(McpJson.LATEST_PROTOCOL_VERSION, client.protocolVersion)
      assertEquals("cognotik", client.serverInfo?.path("name")?.asText())
    }

    val fakeTransport = object : McpTransport {
      private var handler: ((JsonNode) -> Unit)? = null
      override fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit) {
        this.handler = handler
      }

      override fun send(message: JsonNode) {
        if (message.path("method").asText() == "initialize") {
          val reply = McpJson.result(
            message.get("id"),
            McpJson.obj()
              .put("protocolVersion", "1999-01-01")
              .set<ObjectNode>("capabilities", McpJson.obj())
              .set<ObjectNode>("serverInfo", McpJson.obj().put("name", "bad-server")),
          )
          handler?.invoke(reply)
        }
      }

      override fun close() {}
    }

    McpClientToolProvider("bad", fakeTransport).use { client ->
      val ex = assertThrows(MCPException::class.java) { client.connect() }
      assertEquals(MCPException.INVALID_REQUEST, ex.code)
    }
  }

  @Test
  fun `host rejects uninitialized calls and enforces notifications initialized when required`() {
    val (clientSide, serverSide) = InMemoryTransport.pair()
    val host = McpServerHost(
      tools = listOf(simpleTool("echo")),
      transport = serverSide,
      session = session,
      requireInitializedNotification = true,
    ).start()
    val responses = startRawPeer(clientSide)

    try {
      // ping is allowed before initialize
      val pingBefore = sendRawAndAwaitReply(clientSide, McpJson.request(1, "ping"), responses)
      assertTrue(pingBefore.has("result"))

      // notifications/initialized before initialize is ignored
      clientSide.send(McpJson.notification("notifications/initialized"))

      // tools/list and tools/call before initialize must be rejected
      val listBeforeInit = sendRawAndAwaitReply(clientSide, McpJson.request(2, "tools/list"), responses)
      assertEquals(MCPException.INVALID_REQUEST, listBeforeInit.path("error").path("code").asInt())

      val callBeforeInit = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(3, "tools/call", McpJson.obj().put("name", "echo")),
        responses,
      )
      assertEquals(MCPException.INVALID_REQUEST, callBeforeInit.path("error").path("code").asInt())
      assertFalse(host.clientInitialized)

      // initialize alone is insufficient when requireInitializedNotification = true
      val initReply = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(4, "initialize", McpJson.obj().put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)),
        responses,
      )
      assertTrue(initReply.has("result"))
      assertFalse(host.clientInitialized)

      val listBeforeNotif = sendRawAndAwaitReply(clientSide, McpJson.request(5, "tools/list"), responses)
      assertEquals(MCPException.INVALID_REQUEST, listBeforeNotif.path("error").path("code").asInt())

      // After notifications/initialized, capability calls succeed
      clientSide.send(McpJson.notification("notifications/initialized"))
      val listAfterNotif = sendRawAndAwaitReply(clientSide, McpJson.request(6, "tools/list"), responses)
      assertTrue(host.clientInitialized)
      assertEquals(1, listAfterNotif.path("result").path("tools").size())
    } finally {
      clientSide.close()
      host.close()
    }
  }

  // -------------------------------------------------------------------------
  // 2. Paginated tools/list, invalid cursor, notifications/tools/list_changed
  // -------------------------------------------------------------------------

  @Test
  fun `paginated tools list traverses cursors and rejects invalid cursors`() {
    val tools = (1..5).map { simpleTool("tool-$it", "Desc $it") }
    val (clientSide, serverSide) = InMemoryTransport.pair()
    val host = McpServerHost(
      tools = tools,
      transport = serverSide,
      session = session,
      pageSize = 2,
    ).start()
    val responses = startRawPeer(clientSide)

    try {
      sendRawAndAwaitReply(
        clientSide,
        McpJson.request(1, "initialize", McpJson.obj().put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)),
        responses,
      )

      val page1 = sendRawAndAwaitReply(clientSide, McpJson.request(2, "tools/list"), responses).path("result")
      assertEquals(listOf("tool-1", "tool-2"), page1.path("tools").map { it.path("name").asText() })
      assertEquals("2", page1.path("nextCursor").asText())

      val page2 = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(3, "tools/list", McpJson.obj().put("cursor", "2")),
        responses,
      ).path("result")
      assertEquals(listOf("tool-3", "tool-4"), page2.path("tools").map { it.path("name").asText() })
      assertEquals("4", page2.path("nextCursor").asText())

      val page3 = sendRawAndAwaitReply(
        clientSide,
        McpJson.request(4, "tools/list", McpJson.obj().put("cursor", "4")),
        responses,
      ).path("result")
      assertEquals(listOf("tool-5"), page3.path("tools").map { it.path("name").asText() })
      assertFalse(page3.has("nextCursor"))

      for ((idx, badCursor) in listOf("abc", "-1", "6").withIndex()) {
        val errReply = sendRawAndAwaitReply(
          clientSide,
          McpJson.request(10L + idx, "tools/list", McpJson.obj().put("cursor", badCursor)),
          responses,
        )
        assertEquals(MCPException.INVALID_PARAMS, errReply.path("error").path("code").asInt())
      }
    } finally {
      clientSide.close()
      host.close()
    }

    // Client transparent pagination across all pages
    val (c2, s2) = InMemoryTransport.pair()
    val host2 = McpServerHost(tools, s2, session, pageSize = 2).start()
    McpClientToolProvider("paginated-client", c2).use { client ->
      val listed = client.listTools()
      assertEquals(listOf("tool-1", "tool-2", "tool-3", "tool-4", "tool-5"), listed.map { it.name })
    }
    host2.close()
  }

  @Test
  fun `tools list caches results until notifications tools list_changed invalidates cache`() {
    val (rawClientSide, serverSide) = InMemoryTransport.pair()
    val recordingClientSide = RecordingTransport(rawClientSide)
    val host = McpServerHost(
      tools = listOf(simpleTool("alpha")),
      transport = serverSide,
      session = session,
      listChanged = true,
    ).start()

    McpClientToolProvider("cache-client", recordingClientSide).use { client ->
      assertEquals(listOf("alpha"), client.listTools().map { it.name })
      assertEquals(listOf("alpha"), client.listTools().map { it.name })
      assertEquals(1, recordingClientSide.outbound.count { it.path("method").asText() == "tools/list" })

      val latch1 = CountDownLatch(1)
      recordingClientSide.onInboundAfterDispatch = { msg ->
        if (msg.path("method").asText() == "notifications/tools/list_changed") latch1.countDown()
      }
      host.registerTool(simpleTool("beta"))
      assertTrue(latch1.await(5, TimeUnit.SECONDS))
      assertEquals(listOf("alpha", "beta"), client.listTools().map { it.name })
      assertEquals(2, recordingClientSide.outbound.count { it.path("method").asText() == "tools/list" })

      val latch2 = CountDownLatch(1)
      recordingClientSide.onInboundAfterDispatch = { msg ->
        if (msg.path("method").asText() == "notifications/tools/list_changed") latch2.countDown()
      }
      assertTrue(host.unregisterTool("alpha"))
      assertTrue(latch2.await(5, TimeUnit.SECONDS))
      assertEquals(listOf("beta"), client.listTools().map { it.name })

      val latch3 = CountDownLatch(1)
      recordingClientSide.onInboundAfterDispatch = { msg ->
        if (msg.path("method").asText() == "notifications/tools/list_changed") latch3.countDown()
      }
      host.setTools(listOf(simpleTool("gamma")))
      assertTrue(latch3.await(5, TimeUnit.SECONDS))
      assertEquals(listOf("gamma"), client.listTools().map { it.name })
    }
    host.close()
  }

  // -------------------------------------------------------------------------
  // 3. tools/call, structuredContent, all MCPContent variants, errors
  // -------------------------------------------------------------------------

  @Test
  fun `local provider enforces unique tool names and maps errors appropriately`() {
    assertThrows(IllegalArgumentException::class.java) {
      LocalMCPToolProvider(listOf(simpleTool("dup"), simpleTool("dup")))
    }

    val local = LocalMCPToolProvider(
      listOf(
        simpleTool("ok") { args, _ -> MCPToolResult(listOf(MCPContent.Text(args.path("x").asText()))) },
        simpleTool("boom") { _, _ -> throw IllegalStateException("bad state") },
        simpleTool("mcpErr") { _, _ -> throw MCPException(MCPException.INVALID_PARAMS, "bad arg") },
      )
    )
    val ctx = MCPCallContext(session, null, "local-1")

    val ok = local.callTool("ok", McpJson.obj().put("x", "hi"), ctx)
    assertFalse(ok.isError)
    assertEquals("hi", (ok.content.single() as MCPContent.Text).text)

    val unknownEx = assertThrows(MCPException::class.java) {
      local.callTool("missing", McpJson.obj(), ctx)
    }
    assertEquals(MCPException.INVALID_PARAMS, unknownEx.code)

    val boomRes = local.callTool("boom", McpJson.obj(), ctx)
    assertTrue(boomRes.isError)
    assertTrue((boomRes.content.single() as MCPContent.Text).text.contains("IllegalStateException: bad state"))

    val mcpEx = assertThrows(MCPException::class.java) {
      local.callTool("mcpErr", McpJson.obj(), ctx)
    }
    assertEquals(MCPException.INVALID_PARAMS, mcpEx.code)
  }

  @Test
  fun `tools call round-trips all MCPContent variants, structuredContent, and error mappings`() {
    val seenSessionId = AtomicReference<String?>()
    val seenRequestId = AtomicReference<String?>()

    val tools = listOf(
      simpleTool("rich") { args, ctx ->
        seenSessionId.set(ctx.session.sessionId)
        seenRequestId.set(ctx.callId)
        MCPToolResult(
          content = listOf(
            MCPContent.Text("text:${args.path("input").asText()}"),
            MCPContent.Image(data = "aW1hZ2U=", mimeType = "image/png"),
            MCPContent.Audio(data = "YXVkaW8=", mimeType = "audio/wav"),
            MCPContent.ResourceLink(uri = "file:///doc.md", name = "doc.md", mimeType = "text/markdown"),
            MCPContent.Resource(uri = "file:///data.txt", text = "hello-resource", blob = null, mimeType = "text/plain"),
            MCPContent.Resource(uri = "file:///bin.dat", text = null, blob = "AQID", mimeType = "application/octet-stream"),
          ),
          structuredContent = McpJson.obj().put("count", 6).put("ok", true),
          isError = false,
        )
      },
      simpleTool("throwsRuntime") { _, _ -> throw RuntimeException("unexpected crash") },
      simpleTool("throwsMcp") { _, _ -> throw MCPException(-32099, "custom mcp failure") },
    )

    McpClientToolProvider.loopback("rich-loopback", tools, session).use { client ->
      val ctx = MCPCallContext(session, null, "ignored-client-side")

      val rich = client.callTool("rich", McpJson.obj().put("input", "hello"), ctx)
      assertFalse(rich.isError)
      assertEquals(6, rich.content.size)
      assertEquals(MCPContent.Text("text:hello"), rich.content[0])
      assertEquals(MCPContent.Image("aW1hZ2U=", "image/png"), rich.content[1])
      assertEquals(MCPContent.Audio("YXVkaW8=", "audio/wav"), rich.content[2])
      assertEquals(MCPContent.ResourceLink("file:///doc.md", "doc.md", "text/markdown"), rich.content[3])
      assertEquals(MCPContent.Resource("file:///data.txt", "hello-resource", null, "text/plain"), rich.content[4])
      assertEquals(MCPContent.Resource("file:///bin.dat", null, "AQID", "application/octet-stream"), rich.content[5])
      assertEquals(6, rich.structuredContent?.path("count")?.asInt())
      assertEquals(true, rich.structuredContent?.path("ok")?.asBoolean())
      assertEquals(session.sessionId, seenSessionId.get())
      assertNotNull(seenRequestId.get())

      val unknown = client.callTool("does_not_exist", McpJson.obj(), ctx)
      assertTrue(unknown.isError)
      val unknownMsg = (unknown.content.single() as MCPContent.Text).text
      assertTrue(unknownMsg.startsWith("MCP error ${MCPException.INVALID_PARAMS}:"))

      val runtimeErr = client.callTool("throwsRuntime", McpJson.obj(), ctx)
      assertTrue(runtimeErr.isError)
      assertTrue((runtimeErr.content.single() as MCPContent.Text).text.contains("RuntimeException: unexpected crash"))

      val mcpErr = client.callTool("throwsMcp", McpJson.obj(), ctx)
      assertTrue(mcpErr.isError)
      val mcpMsg = (mcpErr.content.single() as MCPContent.Text).text
      assertTrue(mcpMsg.startsWith("MCP error -32099: custom mcp failure"))
    }
  }

  // -------------------------------------------------------------------------
  // 4. Timeouts, notifications/cancelled, and server-side interruption
  // -------------------------------------------------------------------------

  @Test
  fun `client timeout sends notifications cancelled and interrupts slow server tool`() {
    val toolStarted = CountDownLatch(1)
    val toolInterrupted = CountDownLatch(1)

    val slowTool = simpleTool("slow") { _, _ ->
      toolStarted.countDown()
      try {
        Thread.sleep(10_000)
        MCPToolResult(listOf(MCPContent.Text("completed")))
      } catch (e: InterruptedException) {
        toolInterrupted.countDown()
        throw e
      }
    }

    val (rawClientSide, serverSide) = InMemoryTransport.pair()
    val recordingClient = RecordingTransport(rawClientSide)
    val cancelledSent = CompletableFuture<JsonNode>()
    recordingClient.onOutbound = { msg ->
      if (msg.path("method").asText() == "notifications/cancelled") {
        cancelledSent.complete(msg)
      }
    }

    val host = McpServerHost(listOf(slowTool), serverSide, session).start()
    McpClientToolProvider(
      name = "timeout-client",
      transport = recordingClient,
      requestTimeout = Duration.ofMillis(200),
    ).use { client ->
      client.connect()
      val result = client.callTool("slow", McpJson.obj(), MCPCallContext(session, null, "r1"))
      assertTrue(result.isError)
      val errText = (result.content.single() as MCPContent.Text).text
      assertTrue(errText.contains("MCP error ${MCPException.REQUEST_TIMEOUT}"))
      assertTrue(errText.contains("Timed out"))

      assertTrue(toolStarted.await(2, TimeUnit.SECONDS))
      val cancelMsg = cancelledSent.get(3, TimeUnit.SECONDS)
      assertEquals("timeout", cancelMsg.path("params").path("reason").asText())
      assertTrue(cancelMsg.path("params").has("requestId"))
      assertTrue(toolInterrupted.await(3, TimeUnit.SECONDS))
    }
    host.close()
  }

  @Test
  fun `server notifications cancelled suppresses reply and interrupts in-flight tool`() {
    val toolStarted = CountDownLatch(1)
    val toolInterrupted = CountDownLatch(1)
    val slowTool = simpleTool("slow") { _, _ ->
      toolStarted.countDown()
      try {
        Thread.sleep(10_000)
        MCPToolResult(listOf(MCPContent.Text("done")))
      } catch (e: InterruptedException) {
        toolInterrupted.countDown()
        throw e
      }
    }

    val (clientSide, serverSide) = InMemoryTransport.pair()
    val host = McpServerHost(listOf(slowTool), serverSide, session).start()
    val responses = startRawPeer(clientSide)

    try {
      sendRawAndAwaitReply(
        clientSide,
        McpJson.request(1, "initialize", McpJson.obj().put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)),
        responses,
      )

      val cancelledFuture = CompletableFuture<JsonNode>()
      responses[2L] = cancelledFuture
      clientSide.send(McpJson.request(2, "tools/call", McpJson.obj().put("name", "slow")))
      assertTrue(toolStarted.await(3, TimeUnit.SECONDS))

      clientSide.send(
        McpJson.notification(
          "notifications/cancelled",
          McpJson.obj().put("requestId", 2).put("reason", "user_abort"),
        )
      )
      assertTrue(toolInterrupted.await(3, TimeUnit.SECONDS))

      // Host remains responsive to subsequent requests and never responds to cancelled id=2
      val pingReply = sendRawAndAwaitReply(clientSide, McpJson.request(3, "ping"), responses)
      assertTrue(pingReply.has("result"))
      assertFalse(cancelledFuture.isDone)
    } finally {
      clientSide.close()
      host.close()
    }
  }

  @Test
  fun `server handles json-rpc batch requests and rejects empty batch`() {
    val (clientSide, serverSide) = InMemoryTransport.pair()
    val host = McpServerHost(listOf(simpleTool("one")), serverSide, session).start()
    val inbound = CopyOnWriteArrayList<JsonNode>()
    val batchLatch = CountDownLatch(2)
    clientSide.start(handler = { msg ->
      inbound.add(msg)
      batchLatch.countDown()
    })

    try {
      val initFuture = CompletableFuture<JsonNode>()
      // Initialize via single request first
      val initLatch = CountDownLatch(1)
      val allMsgs = CopyOnWriteArrayList<JsonNode>()
      val (c2, s2) = InMemoryTransport.pair()
      val host2 = McpServerHost(listOf(simpleTool("one")), s2, session).start()
      c2.start(handler = { msg ->
        allMsgs.add(msg)
        if (msg.isObject && msg.path("id").asLong() == 1L) initFuture.complete(msg)
        initLatch.countDown()
      })
      c2.send(McpJson.request(1, "initialize", McpJson.obj().put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)))
      assertTrue(initFuture.get(3, TimeUnit.SECONDS).has("result"))

      val batchReceived = CompletableFuture<JsonNode>()
      val emptyBatchError = CompletableFuture<JsonNode>()
      val (c3, s3) = InMemoryTransport.pair()
      val host3 = McpServerHost(listOf(simpleTool("one")), s3, session).start()
      c3.start(handler = { msg ->
        when {
          msg.isArray -> batchReceived.complete(msg)
          msg.isObject && msg.has("error") && msg.get("id").isNull -> emptyBatchError.complete(msg)
          msg.isObject && msg.path("id").asLong() == 1L -> {}
        }
      })
      c3.send(McpJson.request(1, "initialize", McpJson.obj().put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)))
      c3.send(McpJson.mapper.createArrayNode())
      val emptyErr = emptyBatchError.get(3, TimeUnit.SECONDS)
      assertEquals(MCPException.INVALID_REQUEST, emptyErr.path("error").path("code").asInt())

      val batch = McpJson.mapper.createArrayNode().apply {
        add(McpJson.request(10, "ping"))
        add(McpJson.notification("notifications/initialized"))
        add(McpJson.request(11, "tools/list"))
      }
      c3.send(batch)
      val batchReply = batchReceived.get(3, TimeUnit.SECONDS)
      assertEquals(2, batchReply.size())
      assertEquals(10L, batchReply[0].path("id").asLong())
      assertEquals(11L, batchReply[1].path("id").asLong())
      assertEquals("one", batchReply[1].path("result").path("tools")[0].path("name").asText())

      c2.close()
      host2.close()
      c3.close()
      host3.close()
    } finally {
      clientSide.close()
      host.close()
    }
  }

  // -------------------------------------------------------------------------
  // 5. StdioTransport over piped streams & InMemoryTransport.pair() loopback
  // -------------------------------------------------------------------------

  @Test
  fun `stdio transport frames newline-delimited messages over piped streams and handles close`() {
    val clientToServerIn = PipedInputStream(65536)
    val clientToServerOut = PipedOutputStream(clientToServerIn)
    val serverToClientIn = PipedInputStream(65536)
    val serverToClientOut = PipedOutputStream(serverToClientIn)

    val serverClosed = CountDownLatch(1)
    val clientClosed = CountDownLatch(1)
    val serverTransport = StdioTransport(clientToServerIn, serverToClientOut, "test-server-stdio") {
      serverClosed.countDown()
    }
    val clientTransport = StdioTransport(serverToClientIn, clientToServerOut, "test-client-stdio") {
      clientClosed.countDown()
    }

    val host = McpServerHost(
      tools = listOf(simpleTool("greet") { args, _ ->
        MCPToolResult(listOf(MCPContent.Text("hello ${args.path("name").asText()}")))
      }),
      transport = serverTransport,
      session = session,
    ).start()

    val client = McpClientToolProvider("stdio-client", clientTransport)
    try {
      client.connect()
      // Inject blank and malformed lines into wire stream; reader must skip them without breaking
      synchronized(clientToServerOut) {
        clientToServerOut.write("\nnot-valid-json\n".toByteArray(Charsets.UTF_8))
        clientToServerOut.flush()
      }

      val tools = client.listTools()
      assertEquals(listOf("greet"), tools.map { it.name })

      val res = client.callTool("greet", McpJson.obj().put("name", "world"), MCPCallContext(session, null, "s1"))
      assertFalse(res.isError)
      assertEquals("hello world", (res.content.single() as MCPContent.Text).text)
    } finally {
      client.close()
      host.close()
    }

    assertTrue(clientClosed.await(3, TimeUnit.SECONDS))
    assertTrue(serverClosed.await(3, TimeUnit.SECONDS))
    assertThrows(IOException::class.java) {
      clientTransport.send(McpJson.notification("ping"))
    }
  }

  @Test
  fun `in-memory transport pair round-trips wire JSON and propagates close`() {
    val (a, b) = InMemoryTransport.pair()
    val aClosed = CountDownLatch(1)
    val bClosed = CountDownLatch(1)
    val bReceived = CompletableFuture<JsonNode>()

    a.start(handler = {}, onClosed = { aClosed.countDown() })
    b.start(handler = { bReceived.complete(it) }, onClosed = { bClosed.countDown() })

    a.send(McpJson.request(7, "ping", McpJson.obj().put("nested", "ok")))
    val delivered = bReceived.get(3, TimeUnit.SECONDS)
    assertEquals(7L, delivered.path("id").asLong())
    assertEquals("ok", delivered.path("params").path("nested").asText())

    a.close()
    assertTrue(aClosed.await(3, TimeUnit.SECONDS))
    assertTrue(bClosed.await(3, TimeUnit.SECONDS))
    assertThrows(IOException::class.java) {
      b.send(McpJson.notification("ping"))
    }
  }

  // -------------------------------------------------------------------------
  // 6. StreamableHttpTransport against embedded HTTP/SSE server
  // -------------------------------------------------------------------------

  @Test
  fun `streamable http transport handles json responses, session headers, auth, and delete on close`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val httpExecutor = Executors.newCachedThreadPool()
    server.executor = httpExecutor

    val seenAuth = CopyOnWriteArrayList<String?>()
    val seenCustom = CopyOnWriteArrayList<String?>()
    val seenSessionIds = CopyOnWriteArrayList<String?>()
    val seenProtocolVersions = CopyOnWriteArrayList<String?>()
    val deleteCalled = CountDownLatch(1)
    val deleteSessionId = AtomicReference<String?>()

    server.createContext("/mcp") { exchange ->
      when (exchange.requestMethod) {
        "POST" -> {
          seenAuth.add(exchange.requestHeaders.getFirst("Authorization"))
          seenCustom.add(exchange.requestHeaders.getFirst("X-Custom"))
          seenSessionIds.add(exchange.requestHeaders.getFirst(StreamableHttpTransport.HEADER_SESSION_ID))
          seenProtocolVersions.add(exchange.requestHeaders.getFirst(StreamableHttpTransport.HEADER_PROTOCOL_VERSION))

          val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
          val msg = McpJson.decode(body)
          val method = msg.path("method").asText()
          val id = msg.get("id")

          when (method) {
            "initialize" -> {
              exchange.responseHeaders.add(StreamableHttpTransport.HEADER_SESSION_ID, "sess-123")
              val reply = McpJson.result(
                id,
                McpJson.obj()
                  .put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)
                  .set<ObjectNode>("capabilities", McpJson.obj().set("tools", McpJson.obj()))
                  .set("serverInfo", McpJson.obj().put("name", "embedded-http").put("version", "1.0")),
              )
              sendJson(exchange, 200, McpJson.encode(reply))
            }

            "notifications/initialized" -> sendEmpty(exchange, 202)

            "tools/list" -> {
              val reply = McpJson.result(
                id,
                McpJson.obj().set(
                  "tools",
                  McpJson.mapper.createArrayNode().add(
                    McpJson.definitionToJson(simpleTool("http_echo").definition)
                  ),
                ),
              )
              sendJson(exchange, 200, McpJson.encode(reply))
            }

            "tools/call" -> {
              val arg = msg.path("params").path("arguments").path("msg").asText()
              val reply = McpJson.result(
                id,
                McpJson.resultToJson(MCPToolResult(listOf(MCPContent.Text("http:$arg")))),
              )
              sendJson(exchange, 200, McpJson.encode(reply))
            }

            else -> sendEmpty(exchange, 404)
          }
        }

        "GET" -> sendEmpty(exchange, 405)

        "DELETE" -> {
          deleteSessionId.set(exchange.requestHeaders.getFirst(StreamableHttpTransport.HEADER_SESSION_ID))
          deleteCalled.countDown()
          sendEmpty(exchange, 200)
        }

        else -> sendEmpty(exchange, 405)
      }
    }
    server.start()

    try {
      val url = "http://127.0.0.1:${server.address.port}/mcp"
      val transport = StreamableHttpTransport(
        endpointUrl = url,
        headers = mapOf("X-Custom" to "custom-val"),
        bearerToken = "secret-token",
        mode = HttpTransportMode.STREAMABLE_HTTP,
      )
      McpClientToolProvider("http-json", transport).use { client ->
        val tools = client.listTools()
        assertEquals(listOf("http_echo"), tools.map { it.name })

        val result = client.callTool("http_echo", McpJson.obj().put("msg", "hi"), MCPCallContext(session, null, "h1"))
        assertFalse(result.isError)
        assertEquals("http:hi", (result.content.single() as MCPContent.Text).text)
        assertEquals("sess-123", transport.sessionId)
        assertEquals(McpJson.LATEST_PROTOCOL_VERSION, transport.protocolVersion)
      }

      assertTrue(deleteCalled.await(3, TimeUnit.SECONDS))
      assertEquals("sess-123", deleteSessionId.get())
      assertTrue(seenAuth.all { it == "Bearer secret-token" })
      assertTrue(seenCustom.all { it == "custom-val" })
      // Requests after initialize must include Mcp-Session-Id and MCP-Protocol-Version
      assertNull(seenSessionIds[0])
      assertTrue(seenSessionIds.drop(1).all { it == "sess-123" })
      assertNull(seenProtocolVersions[0])
      assertTrue(seenProtocolVersions.drop(1).all { it == McpJson.LATEST_PROTOCOL_VERSION })
    } finally {
      server.stop(0)
      httpExecutor.shutdownNow()
    }
  }

  @Test
  fun `streamable http transport handles sse post responses and get stream notifications`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val httpExecutor = Executors.newCachedThreadPool()
    server.executor = httpExecutor

    val toolCallCount = AtomicInteger(0)
    val firstListDone = CountDownLatch(1)
    val getCount = AtomicInteger(0)
    val lastEventIdSeen = CompletableFuture<String?>()

    server.createContext("/mcp") { exchange ->
      when (exchange.requestMethod) {
        "POST" -> {
          val msg = McpJson.decode(exchange.requestBody.bufferedReader(Charsets.UTF_8).readText())
          val method = msg.path("method").asText()
          val id = msg.get("id")
          when (method) {
            "initialize" -> {
              exchange.responseHeaders.add(StreamableHttpTransport.HEADER_SESSION_ID, "sse-sess")
              val reply = McpJson.result(
                id,
                McpJson.obj()
                  .put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)
                  .set<ObjectNode>(
                    "capabilities",
                    McpJson.obj().set("tools", McpJson.obj().put("listChanged", true)),
                  )
                  .set("serverInfo", McpJson.obj().put("name", "sse-http").put("version", "1.0")),
              )
              sendJson(exchange, 200, McpJson.encode(reply))
            }

            "notifications/initialized" -> sendEmpty(exchange, 202)

            "tools/list" -> {
              val count = toolCallCount.incrementAndGet()
              val arr = McpJson.mapper.createArrayNode().apply {
                add(McpJson.definitionToJson(simpleTool("tool-a").definition))
                if (count > 1) add(McpJson.definitionToJson(simpleTool("tool-b").definition))
              }
              val reply = McpJson.result(id, McpJson.obj().set("tools", arr))
              sendJson(exchange, 200, McpJson.encode(reply))
            }

            "tools/call" -> {
              exchange.responseHeaders.add("Content-Type", "text/event-stream")
              exchange.sendResponseHeaders(200, 0)
              val reply = McpJson.result(
                id,
                McpJson.resultToJson(MCPToolResult(listOf(MCPContent.Text("sse-ok")))),
              )
              exchange.responseBody.use { out ->
                out.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
                out.write("event: message\ndata: ${McpJson.encode(reply)}\n\n".toByteArray(Charsets.UTF_8))
                out.flush()
              }
            }

            else -> sendEmpty(exchange, 404)
          }
        }

        "GET" -> {
          val attempt = getCount.incrementAndGet()
          if (attempt == 1) {
            firstListDone.await(5, TimeUnit.SECONDS)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val notif = McpJson.encode(McpJson.notification("notifications/tools/list_changed"))
            exchange.responseBody.use { out ->
              out.write("id: evt-42\nevent: message\ndata: $notif\n\n".toByteArray(Charsets.UTF_8))
              out.flush()
            }
          } else {
            lastEventIdSeen.complete(exchange.requestHeaders.getFirst(StreamableHttpTransport.HEADER_LAST_EVENT_ID))
            sendEmpty(exchange, 405)
          }
        }

        "DELETE" -> sendEmpty(exchange, 200)

        else -> sendEmpty(exchange, 405)
      }
    }
    server.start()

    try {
      val url = "http://127.0.0.1:${server.address.port}/mcp"
      val transport = StreamableHttpTransport(endpointUrl = url, mode = HttpTransportMode.STREAMABLE_HTTP)
      McpClientToolProvider("http-sse", transport).use { client ->
        assertEquals(listOf("tool-a"), client.listTools().map { it.name })
        val callRes = client.callTool("tool-a", McpJson.obj(), MCPCallContext(session, null, "c1"))
        assertEquals("sse-ok", (callRes.content.single() as MCPContent.Text).text)

        firstListDone.countDown()
        // Waiting for the reconnect GET guarantees the client already processed evt-42 (list_changed)
        assertEquals("evt-42", lastEventIdSeen.get(5, TimeUnit.SECONDS))
        assertEquals(listOf("tool-a", "tool-b"), client.listTools().map { it.name })
      }
    } finally {
      server.stop(0)
      httpExecutor.shutdownNow()
    }
  }

  @Test
  fun `streamable http auto mode falls back to legacy http sse on 405`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val httpExecutor = Executors.newCachedThreadPool()
    server.executor = httpExecutor

    val legacyStreamOut = CompletableFuture<OutputStream>()
    val serverStopped = CountDownLatch(1)

    server.createContext("/sse") { exchange ->
      when (exchange.requestMethod) {
        "POST" -> sendEmpty(exchange, 405) // triggers AUTO fallback to LEGACY_SSE
        "GET" -> {
          exchange.responseHeaders.add("Content-Type", "text/event-stream")
          exchange.sendResponseHeaders(200, 0)
          val out = exchange.responseBody
          synchronized(out) {
            out.write("event: endpoint\ndata: /messages?sid=legacy1\n\n".toByteArray(Charsets.UTF_8))
            out.flush()
          }
          legacyStreamOut.complete(out)
          serverStopped.await(10, TimeUnit.SECONDS)
          runCatching { out.close() }
        }

        else -> sendEmpty(exchange, 405)
      }
    }

    server.createContext("/messages") { exchange ->
      if (exchange.requestMethod != "POST") {
        sendEmpty(exchange, 405)
        return@createContext
      }
      val msg = McpJson.decode(exchange.requestBody.bufferedReader(Charsets.UTF_8).readText())
      val method = msg.path("method").asText()
      val id = msg.get("id")
      sendEmpty(exchange, 202)

      val reply: JsonNode? = when (method) {
        "initialize" -> McpJson.result(
          id,
          McpJson.obj()
            .put("protocolVersion", "2024-11-05")
            .set<ObjectNode>("capabilities", McpJson.obj().set("tools", McpJson.obj()))
            .set("serverInfo", McpJson.obj().put("name", "legacy-sse")),
        )

        "notifications/initialized" -> null

        "tools/list" -> McpJson.result(
          id,
          McpJson.obj().set(
            "tools",
            McpJson.mapper.createArrayNode().add(McpJson.definitionToJson(simpleTool("legacy_tool").definition)),
          ),
        )

        "tools/call" -> McpJson.result(
          id,
          McpJson.resultToJson(MCPToolResult(listOf(MCPContent.Text("from-legacy")))),
        )

        else -> McpJson.error(id, MCPException.METHOD_NOT_FOUND, "Unknown $method")
      }

      if (reply != null) {
        val out = legacyStreamOut.get(5, TimeUnit.SECONDS)
        synchronized(out) {
          out.write("event: message\ndata: ${McpJson.encode(reply)}\n\n".toByteArray(Charsets.UTF_8))
          out.flush()
        }
      }
    }
    server.start()

    try {
      val url = "http://127.0.0.1:${server.address.port}/sse"
      val transport = StreamableHttpTransport(endpointUrl = url, mode = HttpTransportMode.AUTO)
      McpClientToolProvider("legacy-auto", transport).use { client ->
        client.connect()
        assertEquals("2024-11-05", client.protocolVersion)
        assertEquals(listOf("legacy_tool"), client.listTools().map { it.name })
        val res = client.callTool("legacy_tool", McpJson.obj(), MCPCallContext(session, null, "l1"))
        assertFalse(res.isError)
        assertEquals("from-legacy", (res.content.single() as MCPContent.Text).text)
      }
    } finally {
      serverStopped.countDown()
      server.stop(0)
      httpExecutor.shutdownNow()
    }
  }

  private fun sendJson(exchange: HttpExchange, status: Int, json: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun sendEmpty(exchange: HttpExchange, status: Int) {
    exchange.sendResponseHeaders(status, -1)
    exchange.close()
  }
}