package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.clientManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.RecordingThreadFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class SessionThreadsServlet : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    resp.contentType = "text/html"
    resp.status = HttpServletResponse.SC_OK
    if (req.parameterMap.containsKey("sessionId")) {
      val session = Session(req.getParameter("sessionId"))
      val user = authenticationManager.getUser(req.getCookie())
      val pool = clientManager.getPool(session, user)
      // Output all pool stack traces
      //language=HTML
      resp.writer.write(
        """
            <html>
            <head>
                <title>Session Threads</title>
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                    }
            
                    .pool-stats, .pool-threads {
                        border: 1px solid #ddd;
                        padding: 15px;
                        margin-bottom: 20px;
                        border-radius: 4px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
            
                    .thread {
                        margin-bottom: 10px;
                        padding: 10px;
                        border-radius: 4px;
                    }
            
                    .thread-name {
                        font-weight: bold;
                    }
            
                    .stack-element {
                        padding: 5px;
                        margin: 2px 0;
                        border-radius: 2px;
                        font-family: 'Courier New', monospace;
                        font-size: 0.9em;
                    }
            
                    p {
                        line-height: 1.6;
                    }
            
                    a {
                        text-decoration: none;
                    }
            
                    a:hover {
                        text-decoration: underline;
                    }
            
                    .pool-stats p, .pool-threads p {
                        margin: 5px 0;
                    }
            
                    .pool-stats p:first-child, .pool-threads p:first-child {
                        margin-top: 0;
                    }
            
                    .pool-stats p:last-child, .pool-threads p:last-child {
                        margin-bottom: 0;
                    }
                </style>
            
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            </head>
            <body>
            <div class='pool-stats'>
            <h1>Pool Stats</h1>
            <p>Session: """.trimIndent() + session + """</p>
            <p>User: """.trimIndent() + user + """</p>
            <p>Pool: """.trimIndent() + pool + """</p>
            </div>
            <div class='pool-threads'>
            <h1>Thread Stacks</h1>
            """.trimIndent() + (pool.threadFactory as RecordingThreadFactory).threads.filter { it.isAlive }
          .joinToString("<br/>") { thread ->
            """
            <div class='thread'>
            <div class='thread-name'>${thread.name}</div>
            <div class='stack-trace'>${
              thread.stackTrace.joinToString(separator = "\n")
              { stackTraceElement -> "<div class='stack-element'>$stackTraceElement</div>" }
            }</div>
            </div>
            """.trimIndent()
          } + """
            </div>
            </body>
            </html>
            """.trimIndent()
      )
    } else {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Session ID is required")
    }
  }
}