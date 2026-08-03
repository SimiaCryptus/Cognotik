package com.simiacryptus.cognotik.webui.servlet
    
    import com.simiacryptus.cognotik.platform.ApplicationServices.threadPoolManager
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.ThreadPoolManager
    import com.simiacryptus.cognotik.webui.application.UserProviderImpl
    import jakarta.servlet.http.HttpServlet
    import jakarta.servlet.http.HttpServletRequest
    import jakarta.servlet.http.HttpServletResponse
    
    class SessionThreadsServlet : HttpServlet() {
        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            response.contentType = "text/html"
            response.status = HttpServletResponse.SC_OK
            if (request.parameterMap.containsKey("sessionId")) {
                val session = Session(request.getParameter("sessionId"))
                val user = UserProviderImpl().authenticate(request, response)
                  ?: throw IllegalStateException("Authentication failed")
                val pool = threadPoolManager.getPool(session, user)
    
    
                response.writer.write(
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
    
                        .theme-selector-container {
                            position: fixed;
                            top: 10px;
                            right: 10px;
                            z-index: 1000;
                        }
                    </style>
    
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    <script src="/modules/theme.js"></script>
                    <script>
                        (function() {
                            if (window.ThemeManager && typeof window.ThemeManager.init === 'function') {
                                window.ThemeManager.init();
                            }
                            document.addEventListener('DOMContentLoaded', function() {
                                var selector = document.getElementById('theme-selector');
                                if (selector && window.ThemeManager && typeof window.ThemeManager.bindSelector === 'function') {
                                    window.ThemeManager.bindSelector(selector);
                                }
                            });
                        })();
                    </script>
                </head>
                <body>
                <div class="theme-selector-container">
                    <label for="theme-selector">Theme:</label>
                    <select id="theme-selector">
                        <option value="auto">Auto</option>
                        <option value="light">Light</option>
                        <option value="dark">Dark</option>
                    </select>
                </div>
                <div class='pool-stats'>
                <h1>Pool Stats</h1>
                <p>Session: """.trimIndent() + session + """</p>
                <p>User: """.trimIndent() + user + """</p>
                <p>Pool: """.trimIndent() + pool + """</p>
                </div>
                <div class='pool-threads'>
                <h1>Thread Stacks</h1>
                """.trimIndent() + (pool.threadFactory as ThreadPoolManager.RecordingThreadFactory).threads.filter { it.isAlive }
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
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.writer.write("Session ID is required")
            }
        }
    }