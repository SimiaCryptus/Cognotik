package com.simiacryptus.aicoder.demotest

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JEditorPane
import javax.swing.JFrame

data class SplashScreenConfig(
    val fontFamily: String = "JetBrains Mono",
    val titleColor: String = "#00B4E7",
    val subtitleColor: String = "#6B7C93",
    val timestampColor: String = "#A9B7C6",
    val titleText: String = "Test Recording",
    //val dateFormat: String = "dddd, MMMM d, yyyy 'at' h:mm:ss a",
    val dateFormat: String = "MMMM d, yyyy",
    val containerStyle: String = """
      background: linear-gradient(145deg, #2B2B2B 0%, #3C3F41 100%);
      padding: 40px 60px;
      border-radius: 12px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
      border: 1px solid #4D4D4D;
      animation: slideIn 1.5s cubic-bezier(0.16, 1, 0.3, 1);
    """.trimIndent(),
    val bodyStyle: String = """
      margin: 0;
      padding: 20px;
      background: #1E1E1E;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100vh;
      text-align: center;
      position: relative;
      overflow: hidden;
      &::before {
        content: '';
        position: absolute;
        top: -50%;
        left: -50%;
        width: 200%;
        height: 200%;
        background: repeating-linear-gradient(
          45deg,
          #2B2B2B 0%,
          #2B2B2B 10%,
          #1E1E1E 10%,
          #1E1E1E 20%
        );
        opacity: 0.1;
        animation: backgroundMove 20s linear infinite;
      }
    """.trimIndent(),
    val subtitle: String = "🔮🤖 Simiacryptus AI Coding Assistant 🤖🔮"
) {
    private val cssStyles = """
        @import url('https://fonts.googleapis.com/css2?family=${fontFamily}:wght@300;400;700&display=swap');
        body { font-family: '${fontFamily}', sans-serif; ${bodyStyle} }
        .container { ${containerStyle} }
        h1 { font-size: 48px; color: ${titleColor}; margin-bottom: 0; font-weight: 700; }
        h2 { font-size: 36px; color: ${subtitleColor}; margin-bottom: 0; font-weight: 400; }
        p { font-size: 24px; color: ${timestampColor}; font-weight: 300; }
      """.trimIndent()

    fun splashPage(): String {
        val screenSize = getScreenSize()
        return """
          <!DOCTYPE html>
          <html>
          <head><style>${cssStyles}</style></head>
          <body>
            <div style="height: ${screenSize.height / 3}px"></div>
            <div class="container" style="height: ${screenSize.height / 3}px">
              <h1>${titleText}</h1>
              <h2>$subtitle</h2>
              <p>${SimpleDateFormat(dateFormat).format(Date())}</p>
            </div>
          </body>
          </html>
        """.trimIndent()
    }

    fun getScreenSize() = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds.run {
            Rectangle(width, height)
        }

}

fun SplashScreenConfig.toSplashDialog(page: String) = JFrame().apply {
    val screenSize = getScreenSize()
    isUndecorated = true
    isAlwaysOnTop = true
    extendedState = JFrame.MAXIMIZED_BOTH
    size = screenSize.size
    contentPane.add(JEditorPane().apply {
        contentType = "text/html"
        text = page
        isEditable = false
        size = screenSize.size
    })
    isVisible = true
}
