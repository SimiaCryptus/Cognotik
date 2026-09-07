package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.lang.reflect.Proxy

/**
 * Dynamic-proxy stubs for the servlet API so that the pure-function parts of
 * the servlets (link building, capability config, path derivation) can be
 * unit tested without a container or a mocking framework.
 *
 * Unspecified methods return a benign default ("" for String, false/0 for
 * primitives, null otherwise). Attributes are backed by a real map so that
 * [com.simiacryptus.cognotik.fileserver.FileServlet.getUser] caching can be observed.
 */
object ServletStubs {

  fun request(values: Map<String, Any?> = emptyMap()): HttpServletRequest {
    val attributes = HashMap<String, Any?>()
    return Proxy.newProxyInstance(
      HttpServletRequest::class.java.classLoader,
      arrayOf(HttpServletRequest::class.java)
    ) { proxy, method, args ->
      when (method.name) {
        "equals" -> proxy === args?.getOrNull(0)
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "StubHttpServletRequest$values"
        "getAttribute" -> attributes[args?.get(0) as? String]
        "setAttribute" -> {
          attributes[args!![0] as String] = args[1]; null
        }

        "removeAttribute" -> {
          attributes.remove(args!![0] as String); null
        }

        else -> if (values.containsKey(method.name)) values[method.name] else default(method.returnType)
      }
    } as HttpServletRequest
  }

  fun response(): HttpServletResponse = Proxy.newProxyInstance(
    HttpServletResponse::class.java.classLoader,
    arrayOf(HttpServletResponse::class.java)
  ) { proxy, method, args ->
    when (method.name) {
      "equals" -> proxy === args?.getOrNull(0)
      "hashCode" -> System.identityHashCode(proxy)
      "toString" -> "StubHttpServletResponse"
      else -> default(method.returnType)
    }
  } as HttpServletResponse

  private fun default(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    String::class.java -> ""
    else -> null
  }
}