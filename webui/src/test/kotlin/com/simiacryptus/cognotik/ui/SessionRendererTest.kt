package com.simiacryptus.cognotik.ui

import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.Ignore

class SessionRendererTest {

  private lateinit var task: ISessionTask
  private lateinit var socketManager: SocketManager
  private lateinit var renderer: SessionRenderer
  private val filepath = Path.of("test.kt")

  @BeforeEach
  fun setup() {
    task = mockk(relaxed = true)
    socketManager = mockk(relaxed = true)
//    every { task.ui } returns socketManager

    val subTask = mockk<ISessionTask>(relaxed = true)
    every { socketManager.newTask(any()) } returns subTask
    every { subTask.placeholder } returns "<placeholder>"

    // Mock hrefLink to return a string
    every { subTask.hrefLink(any(), any(), any(), any()) } answers {
      "<a href='#'>${arg<String>(0)}</a>"
    }
    every { subTask.complete(any<String>()) } answers { StringBuilder(arg<String>(0)) }

    renderer = SessionRenderer(task)
  }

  @Test @Ignore
  fun `renderSaveButton generates HTML`() {
    val html = renderer.renderSaveButton(filepath, "code", "kt") {}
    assertEquals("<placeholder>", html)
  }

  @Test
  fun `renderAutoApplied generates HTML`() {
    val html = renderer.renderAutoApplied(filepath, "<revert>")
    assertTrue(html.contains("Automatically Applied"))
    assertTrue(html.contains("<revert>"))
  }

  @Test
  fun `renderWarning generates HTML`() {
    val html = renderer.renderWarning("Watch out!")
    assertTrue(html.contains("Warning: Watch out!"))
    assertTrue(html.contains("class=\"warning\""))
  }
}