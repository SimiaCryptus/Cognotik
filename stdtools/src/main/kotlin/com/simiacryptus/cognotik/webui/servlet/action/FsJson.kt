package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.webui.servlet.handler.FsErrorCode
    import com.simiacryptus.cognotik.webui.servlet.handler.FsErrors
    import com.simiacryptus.cognotik.webui.servlet.handler.FsException
    import jakarta.servlet.http.HttpServletResponse

    /**
     * Minimal JSON writing shared by the extended FS API actions. Deliberately hand-rolled:
     * the shapes are tiny and fixed, and the actions must not drag a JSON binding into the
     * request path.
     */
    object FsJson {

      fun write(resp: HttpServletResponse, status: Int, body: String) {
        resp.status = status
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        resp.writer.write(body)
      }

      fun fail(resp: HttpServletResponse, code: FsErrorCode, syscall: String, message: String) {
        FsErrors.write(resp, FsException(code, syscall, null, message))
      }

      fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) when {
          c == '"' -> sb.append("\\\"")
          c == '\\' -> sb.append("\\\\")
          c == '\n' -> sb.append("\\n")
          c == '\r' -> sb.append("\\r")
          c == '\t' -> sb.append("\\t")
          c < ' ' -> sb.append(String.format("\\u%04x", c.code))
          else -> sb.append(c)
        }
        return sb.toString()
      }
    }