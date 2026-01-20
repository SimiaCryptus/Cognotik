# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/ApiKeyServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.platform.model.toApiList
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.net.URLEncoder
import java.util.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

class ApiKeyServlet : HttpServlet() {

    data class ApiKeyRecord(
        val owner: String,
        val apiKey: SecureString,
        val mappedKey: SecureString,
        val budget: Double,
        val comment: String,
        val welcomeMessage: String = "Welcome to our service!"
    )

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {


        resp.contentType = "text/html"
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie()) ?: return resp.sendError(
            HttpServletResponse.SC_UNAUTHORIZED
        )
        val action = req.getParameter("action")
        val apiKey = req.getParameter("apiKey")
        val provider = req.getParameter("provider")

        when (action.lowercase(Locale.ROOT)) {
            "edit" -> {
                val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey && it.owner == user.email }
                if (record != null) {
                    serveEditPage(req, resp, record)
                } else {
                    resp.writer.write("API Key record not found")
                }
            }

            "delete" -> {

                val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey && it.owner == user.email }
                if (record != null) {
                    apiKeyRecords.remove(record)
                    saveRecords()
                    resp.writer.write("API Key record deleted")
                } else {
                    resp.writer.write("API Key record not found")
                }
            }

            "create" -> {
                val userSettings = fileApplicationServices().userSettingsManager.getUserSettings(user)
                serveEditPage(
                    req,
                    resp,
                    ApiKeyRecord(
                      owner = user.email,
                      apiKey = UUID.randomUUID().toString().encrypt,
                      mappedKey = userSettings.apis.firstOrNull { it.provider == APIProvider.valueOf(provider) }?.key ?: "".encrypt,
                      budget = 0.0,
                      comment = ""
                    )
                )
            }

            "invite" -> {
                val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey /*&& it.owner != user.email*/ }
                if (record == null) {
                    throw IllegalArgumentException("API Key record not found, or you do not have permission to access it, or you are the owner.")
                }

                serveInviteConfirmationPage(resp, record, user)
            }

            else -> {
                resp.writer.write(indexPage(req))
            }
        }

    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val action = req.getParameter("action")
        val apiKey = req.getParameter("apiKey")
        val mappedKey = req.getParameter("mappedKey")
        val budget = req.getParameter("budget")?.toDoubleOrNull()
        val comment = req.getParameter("comment")

        val welcomeMessage = req.getParameter("welcomeMessage")
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey }

        if (action == "acceptInvite") {
            if (apiKey.isNullOrEmpty()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "API Key is missing")
            } else if (user == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found")
            } else if (record == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid API Key or User not found")
            } else {
                fileApplicationServices().userSettingsManager.updateUserSettings(
                    user, UserSettings(
                        toApiList(
                            apiKeys = mapOf(APIProvider.OpenAI to apiKey),
                            apiBase = mapOf(APIProvider.OpenAI to "https://apps.simiacrypt.us/proxy"),
                        ),
                        mutableListOf()
                    )
                )
                resp.sendRedirect("/")

            }
        } else if (record != null && budget != null && user == null) {

            apiKeyRecords.remove(record)
            apiKeyRecords.add(
                record.copy(
                    mappedKey = mappedKey.encrypt ?: record.mappedKey,
                    budget = budget,
                    comment = comment ?: ""
                )
            )
            saveRecords()
            resp.sendRedirect("?action=edit&apiKey=$apiKey&editSuccess=true")
        } else if (apiKey != null && budget != null) {

            val newRecord = ApiKeyRecord(
                owner = user?.email ?: "",
                apiKey = apiKey.encrypt,
                mappedKey = mappedKey.encrypt,
                budget = budget,
                comment = comment ?: "",
                welcomeMessage = welcomeMessage ?: "Welcome to our service!"
            )
            apiKeyRecords.add(newRecord)
            saveRecords()
            resp.sendRedirect(
                "?action=edit&apiKey=${
                    URLEncoder.encode(
                        apiKey,
                        "UTF-8"
                    )
                }&creationSuccess=true"
            )

        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input")
        }
    }

    private fun indexPage(req: HttpServletRequest): String {
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie()) ?: return ""
        return """
          <html>
          <head>
              <title>API Key Records</title>
              <style>
                  body { font-family: Arial, sans-serif; margin: 20px; }
                  .records { margin-bottom: 20px; }
                  .record { margin: 10px 0; }
                  a { text-decoration: none; color: #007BFF; }
              </style>
          </head>
          <body>
              <h1>API Key Records</h1>
              <div class='records'>
                  ${
            apiKeyRecords.filter { it.owner == user.email }.joinToString("\n") { record ->
                "<div class='record'><a href='?action=edit&apiKey=${record.apiKey}'>${record.apiKey}</a></div>"
            }
        }
              </div>
              <a href="?action=create">Create New API Key Record</a>
          </body>
          </html>
      """.trimIndent()
    }

    private fun serveInviteConfirmationPage(resp: HttpServletResponse, record: ApiKeyRecord, user: User) {

        resp.writer.write(
            """
    <html>
    <head>
        <title>Accept API Key Invitation</title>
    </head>
    <body>
    <h1>Accept API Key Invitation</h1>
    <h2>${record.welcomeMessage}</h2>
    <p>You have been invited to use the API Key: ${record.apiKey}</p>
    <form action='/apiKeys/' method="post">
        <input type="hidden" name="apiKey" value="${record.apiKey}">
        <input type="hidden" name="action" value="acceptInvite">
        <input type="submit" value="Accept Invite">
    </form>
    </body>
    </html>
    """.trimIndent()
        )
    }

    private fun serveEditPage(req: HttpServletRequest, resp: HttpServletResponse, record: ApiKeyRecord) {
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val usageSummary: Map<String, ModelSchema.Usage> =
            ApplicationServices.fileApplicationServices().usageManager.getUserUsageSummary(user = userinfo!!)

        resp.writer.write(
            """
      <html>
      <head>
          <title>Edit API Key Record: ${record.apiKey}</title>
          <style>
              body {
                  font-family: Arial, sans-serif;
                  margin: 20px;
              }

              form > label {
                  display: block;
                  margin-top: 10px;
              }

              form > input[type="text"], textarea {
                  margin-bottom: 10px;
                  display: block;
                  width: 100%;
                  box-sizing: border-box;
              }

              form > input[type="text"]#mappedKey {
                  width: 50%;
              }

              textarea {
                  height: 100px;
              }

              form > input[type="submit"] {
                  margin-top: 10px;
              }

              form {
                  max-width: 600px;
              }

              h2 {
                  margin-top: 20px;
              }

              div {
                  margin-bottom: 10px;
              }
               .invite-link {
                   margin-top: 20px;
               }
          </style>
      </head>
      <body>
      <h1>Edit API Key Record: ${record.apiKey}</h1>
      <form action="edit" method="post">
          <input type="hidden" name="apiKey" value="${record.apiKey}">
          <label for="mappedKey">Mapped Key:</label>
          <input type="text" id="mappedKey" name="mappedKey" value="${record.mappedKey}" style="width: 100%;">
          <label for="budget">Budget:</label>
          <input type="text" id="budget" name="budget" value="${record.budget}">
          <label for="comment">Description:</label>
          <textarea id="comment" name="comment">${record.comment}</textarea>
          <label for="welcomeMessage">Welcome Message:</label>
          <textarea id="welcomeMessage" name="welcomeMessage">${record.welcomeMessage}</textarea>
          <input type="submit" value="Submit">
      </form>
      <!-- Usage Summary -->
      <h2>Usage Summary</h2>
      ${
                usageSummary.entries.joinToString { (model: String, usage: ModelSchema.Usage) ->
                    """
          <div>
            <h3>${model}</h3>
            <p>total_tokens: ${usage.total_tokens}</p>
            <p>Cost: ${usage.cost}</p>
          </div>
          """
                }
            }
       <!-- Invite Link -->
       <div class="invite-link">
           <h2>Invite Link</h2>
           <p>Share this link to invite others to use this API Key:</p>
           <a href="?action=invite&apiKey=${URLEncoder.encode(record.apiKey.decrypt, "UTF-8")}">Invite Link</a>
       </div>
      </body>
      </html>
        """.trimIndent()
        )
    }

    companion object {
        private val userRoot by lazy {
            dataStorageRoot.resolve("apiKeys").apply { mkdirs() }
        }

        val apiKeyRecords by lazy {
            val file = File(userRoot, "apiKeys.json")
            if (file.exists()) try {
                return@lazy JsonUtil.fromJson(file.readText(), typeOf<List<ApiKeyRecord>>().javaType)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            mutableListOf<ApiKeyRecord>()
        }

        private fun saveRecords() {
            File(userRoot, "apiKeys.json").writeText(JsonUtil.toJson(apiKeyRecords))
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/ApiProviderServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory

class ApiProviderServlet : HttpServlet() {
    data class ApiProvidersResponse(
        val configuredProviders: List<ProviderInfo>,
        val availableProviders: List<AvailableProviderInfo>
    )

    data class AvailableProviderInfo(
        val id: String,
        val name: String,
        val baseUrl: String,
        val isConfigured: Boolean
    )

    data class ProviderInfo(
        val name: String,
        val baseUrl: String,
        val models: List<ModelInfo>,
        val supportsChat: Boolean,
        val supportsEmbedding: Boolean
    )

    data class ModelInfo(
        val name: String,
        val maxTokens: Int? = null
    )

    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())

            val userSettings = ApplicationServices.fileApplicationServices()
                .userSettingsManager.getUserSettings(userinfo)
            // Get all available providers (including unconfigured)
            val availableProviders = APIProvider.values().map { provider ->
                val isConfigured = userSettings.apis.any {
                    it.provider?.name == provider.name && !it.key?.decrypt.isNullOrEmpty()
                }
                AvailableProviderInfo(
                    id = provider.name,
                    name = provider.name,
                    baseUrl = provider.base,
                    isConfigured = isConfigured
                )
            }


            val providers = mutableListOf<ProviderInfo>()

            // Get all registered API providers
            APIProvider.values().forEach { provider ->
                try {
                    // Find matching API configuration for this provider
                    val apiConfig = userSettings.apis.find {
                        it.provider?.name == provider.name
                    }

                    if (apiConfig != null && !apiConfig.key?.decrypt.isNullOrEmpty()) {
                        val models = try {
                            provider.getChatModels(
                                key = apiConfig.key,
                                baseUrl = apiConfig.baseUrl
                            ).map { model ->
                                ModelInfo(
                                    name = model.modelName,
                                    maxTokens = model.maxTotalTokens
                                )
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to fetch models for provider ${provider.name}", e)
                            emptyList()
                        }

                        val supportsEmbedding = try {
                            provider.getEmbeddingClient(
                                key = apiConfig.key,
                                base = apiConfig.baseUrl,
                                workPool = com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService(),
                                scheduledPool = com.google.common.util.concurrent.MoreExecutors.listeningDecorator(
                                    java.util.concurrent.Executors.newScheduledThreadPool(1)
                                )
                            )
                            true
                        } catch (e: UnsupportedOperationException) {
                            false
                        } catch (e: Exception) {
                            log.warn("Error checking embedding support for ${provider.name}", e)
                            false
                        }

                        providers.add(
                            ProviderInfo(
                                name = provider.name,
                                baseUrl = apiConfig.baseUrl,
                                models = models,
                                supportsChat = models.isNotEmpty(),
                                supportsEmbedding = supportsEmbedding
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.error("Error processing provider ${provider.name}", e)
                }
            }
            val response = ApiProvidersResponse(
                configuredProviders = providers,
                availableProviders = availableProviders
            )


            resp.status = HttpServletResponse.SC_OK
            val acceptHeader = req.getHeader("Accept") ?: ""

            if (acceptHeader.contains("application/json")) {
                resp.contentType = "application/json"
                resp.writer.write(JsonUtil.toJson(response))
            } else {
                resp.contentType = "text/html"
                resp.writer.write(generateHtmlResponse(response))
            }

        } catch (e: Exception) {
            log.error("Error in ApiProviderServlet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write(JsonUtil.toJson(mapOf("error" to e.message)))
        }
    }

    private fun generateHtmlResponse(response: ApiProvidersResponse): String {
        val availableProvidersHtml = response.availableProviders.joinToString("\n") { provider ->
            """
           <tr>
               <td>${provider.name}</td>
               <td>${provider.baseUrl}</td>
               <td>${if (provider.isConfigured) "✓ Yes" else "✗ No"}</td>
           </tr>
           """.trimIndent()
        }

        val providersHtml = response.configuredProviders.joinToString("\n") { provider ->
            val modelsHtml = provider.models.joinToString("\n") { model ->
                """
                <li>
                    ${model.name}
                    ${model.maxTokens?.let { " (max tokens: $it)" } ?: ""}
                </li>
                """.trimIndent()
            }

            """
            <div class="provider">
                <h2>${provider.name}</h2>
                <p><strong>Base URL:</strong> ${provider.baseUrl}</p>
                <p><strong>Supports Chat:</strong> ${if (provider.supportsChat) "Yes" else "No"}</p>
                <p><strong>Supports Embedding:</strong> ${if (provider.supportsEmbedding) "Yes" else "No"}</p>
                <h3>Available Models:</h3>
                <ul>
                    $modelsHtml
                </ul>
            </div>
            """.trimIndent()
        }

        return """
        <html>
        <head>
            <title>API Providers</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    margin: 20px;
                    background-color: #f5f5f5;
                }
                .provider {
                    background-color: white;
                    padding: 20px;
                    margin-bottom: 20px;
                    border-radius: 8px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
               table {
                   width: 100%;
                   border-collapse: collapse;
                   background-color: white;
                   margin-bottom: 20px;
                   border-radius: 8px;
                   overflow: hidden;
                   box-shadow: 0 2px 4px rgba(0,0,0,0.1);
               }
               th, td {
                   padding: 12px;
                   text-align: left;
                   border-bottom: 1px solid #eee;
               }
               th {
                   background-color: #0066cc;
                   color: white;
                   font-weight: bold;
               }
               tr:last-child td {
                   border-bottom: none;
               }
               table {
                   width: 100%;
                   border-collapse: collapse;
                   background-color: white;
                   margin-bottom: 20px;
                   border-radius: 8px;
                   overflow: hidden;
                   box-shadow: 0 2px 4px rgba(0,0,0,0.1);
               }
               th, td {
                   padding: 12px;
                   text-align: left;
                   border-bottom: 1px solid #eee;
               }
               th {
                   background-color: #0066cc;
                   color: white;
                   font-weight: bold;
               }
               tr:last-child td {
                   border-bottom: none;
               }
                h1 {
                    color: #333;
                }
                h2 {
                    color: #0066cc;
                    margin-top: 0;
                }
                h3 {
                    color: #666;
                }
                ul {
                    list-style-type: none;
                    padding-left: 0;
                }
                li {
                    padding: 5px 0;
                    border-bottom: 1px solid #eee;
                }
                li:last-child {
                    border-bottom: none;
                }
            </style>
        </head>
        <body>
            <h1>Available API Providers</h1>
           <h2>All Available Providers</h2>
           <table>
               <thead>
                   <tr>
                       <th>Provider Name</th>
                       <th>Base URL</th>
                       <th>Configured</th>
                   </tr>
               </thead>
               <tbody>
                   $availableProvidersHtml
               </tbody>
           </table>
           <h2>Configured Providers with Models</h2>
           <h2>All Available Providers</h2>
           <table>
               <thead>
                   <tr>
                       <th>Provider Name</th>
                       <th>Base URL</th>
                       <th>Configured</th>
                   </tr>
               </thead>
               <tbody>
                   $availableProvidersHtml
               </tbody>
           </table>
           <h2>Configured Providers with Models</h2>
            $providersHtml
        </body>
        </html>
        """.trimIndent()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiProviderServlet::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/AppInfoServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppInfoServlet<T>(val info: (String?) -> T) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val session = req.getParameter("session")
        resp.contentType = "text/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(JsonUtil.objectMapper().writeValueAsString(info(session)))
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/CancelThreadsServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.threadPoolManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class CancelThreadsServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        if (req.parameterMap.containsKey("sessionId")) {
            val session = Session(req.getParameter("sessionId"))

            resp.writer.write(
                """
        <html>
        <head>
            <title>Cancel Session</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
        </head>
        <body>
        <form action="""".trimIndent() + req.contextPath + """/cancel" method="post">
            <input type="hidden" name="sessionId" value="""".trimIndent() + session + """"/>
            CONFIRM: <input type='text' name="confirm" placeholder="Type 'confirm' to cancel" />
            <input type="submit" value="Cancel"/>
        </form>
        </body>
        </html>
        """.trimIndent()
            )
        } else {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Session ID is required")
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        require(req.getParameter("confirm").lowercase() == "confirm") { "Confirmation text is required" }
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        if (!req.parameterMap.containsKey("sessionId")) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Session ID is required")
        } else {
            val session = Session(req.getParameter("sessionId"))
            val user = authenticationManager.getUser(req.getCookie())
            require(
                ApplicationServices.authorizationManager.isAuthorized(
                    javaClass,
                    user,
                    AuthorizationInterface.OperationType.Delete
                )
            )
            { "User $user is not authorized to cancel sessions" }
            if (session.isGlobal()) {
                require(
                    ApplicationServices.authorizationManager.isAuthorized(
                        javaClass,
                        user,
                        AuthorizationInterface.OperationType.Public
                    )
                )
                { "User $user is not authorized to cancel global sessions" }
            }
            val pool = threadPoolManager.getPool(session, user)
            pool.shutdownNow()
            resp.sendRedirect("/")
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/CognitiveConfigServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

class CognitiveConfigServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val cognitiveModes = CognitiveModeType.values().map { modeType ->
            mapOf(
                "id" to modeType.name,
                "name" to (modeType.name.replace(Regex("([a-z])([A-Z]+)"), "$1 $2") + " Mode"),
                "description" to (modeType.description ?: ""),
                "configFields" to getConfigFields(modeType.configClass.kotlin)
            )
        }

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(JsonUtil.toJson(cognitiveModes))
    }

    private fun getConfigFields(kClass: KClass<out CognitiveModeConfig>): List<Map<String, Any>> {
        val instance = try {
            kClass.createInstance()
        } catch (e: Throwable) {
            null
        }
        return kClass.memberProperties
            .filter { it.name !in setOf("type") }
            .mapNotNull { prop ->
                val description = prop.annotations.filterIsInstance<Description>().firstOrNull()?.value
                val type = when (prop.returnType.classifier) {
                    Boolean::class -> "checkbox"
                    Int::class, Long::class, Double::class -> "number"
                    String::class -> if (prop.name.contains("code", true) || prop.name.contains(
                            "prompt",
                            true
                        )
                    ) "textarea" else "text"

                    else -> {
                        if ((prop.returnType.classifier as? KClass<*>)?.java?.isEnum == true) {
                            "select"
                        } else if (DynamicEnum::class.java.isAssignableFrom((prop.returnType.classifier as? KClass<*>)?.java)) {
                            "select"
                        } else {
                            null
                        }
                    }
                }

                if (type != null) {
                    val field = mutableMapOf<String, Any>(
                        "id" to prop.name,
                        "label" to prop.name
                            .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                            .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
                        "type" to type
                    )
                    if (description != null) field["tooltip"] = description

                    if (type == "select") {
                        val enumClass = (prop.returnType.classifier as KClass<*>).java
                        val enumConstants = enumClass.enumConstants
                        if (null != enumConstants && enumConstants.isNotEmpty()) {
                            field["options"] = enumConstants.map { it.toString() }
                            field["default"] = enumConstants.firstOrNull()?.toString() ?: ""
                        } else if (DynamicEnum::class.java.isAssignableFrom(enumClass)) {
                            val dynamicEnumCompanion = enumClass.getDeclaredField("Companion").get(null)
                            val valuesMethod = dynamicEnumCompanion.javaClass.getMethod("values")
                            val dynamicEnumValues = valuesMethod.invoke(dynamicEnumCompanion) as List<DynamicEnum<*>>
                            field["options"] = dynamicEnumValues.map { it.name }
                            field["default"] = dynamicEnumValues.firstOrNull()?.name ?: ""
                        }
                    }
                    if (instance != null) {
                        try {
                            val value = prop.getter.call(instance)
                            if (value != null) {
                                field["default"] = if (type == "select") value.toString() else value
                            }
                        } catch (e: Throwable) {
                            // Ignore
                        }
                    }

                    field
                } else null
            }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/CorsFilter.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.*
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

@WebFilter(asyncSupported = true, urlPatterns = ["/*"])
class CorsFilter : Filter {
    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig?) {

    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest?, response: ServletResponse, chain: FilterChain) {
        if (!(request as HttpServletRequest).requestURI.endsWith("/ws")) {
            val httpServletResponse = response as HttpServletResponse
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*")
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT")
            httpServletResponse.setHeader("Access-Control-Max-Age", "3600")
            httpServletResponse.setHeader(
                "Access-Control-Allow-Headers",
                "Content-Type, x-requested-with, authorization"
            )
        }
        try {
            chain.doFilter(request, response)
        } catch (e: Exception) {
            log.warn("Error in filter", e)
            throw e
        }
    }

    override fun destroy() {

    }

    companion object {
        private val log = LoggerFactory.getLogger(CorsFilter::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/DeleteSessionServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class DeleteSessionServlet(
    private val server: ApplicationServer,
) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        if (req.parameterMap.containsKey("sessionId")) {
            val session = Session(req.getParameter("sessionId"))

            resp.writer.write(
                """
        <html>
        <head>
            <title>Delete Session</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
        </head>
        <body>
        <form action="${req.contextPath}/delete" method="post">
            <input type="hidden" name="sessionId" value="$session"/>
            CONFIRM: <input type='text' name="confirm" placeholder="Type 'confirm' to delete" />
            <input type="submit" value="Delete"/>
        </form>
        </body>
        </html>
        """.trimIndent()
            )
        } else {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Session ID is required")
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        require(req.getParameter("confirm").lowercase() == "confirm") { "Confirmation text is required" }
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        if (!req.parameterMap.containsKey("sessionId")) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("Session ID is required")
        } else {
            val session = Session(req.getParameter("sessionId"))
            val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
            require(authorizationManager.isAuthorized(javaClass, user, OperationType.Delete))
            { "User $user is not authorized to delete sessions" }
            if (session.isGlobal()) {
                require(authorizationManager.isAuthorized(javaClass, user, OperationType.Public))
                { "User $user is not authorized to delete global sessions" }
            }
            server.dataStorage.deleteSession(user, session)
            resp.sendRedirect("/")
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.markdownToHtml
import jakarta.servlet.WriteListener
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part
import org.eclipse.jetty.http.MimeTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
abstract class FileServlet : HttpServlet() {

    abstract fun getDir(
        req: HttpServletRequest,
    ): File?

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received GET request for path: ${req.pathInfo ?: req.servletPath}")
        val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
        val dir = getDir(req)
        val file = dir?.let { getFile(it, pathSegments, req) }
        when {
            false == file?.exists() -> {
                // Check if this is a request for HTML or PDF with an equivalent .md file
                val fileName = file.name
                val extension = fileName.split(".").lastOrNull()
                extension?.let { extension ->
                    log.info("File does not exist: ${file.absolutePath}, checking for markdown alternative for extension: $extension")
                }
                when {
                    setOf("html", "pdf", "txt").contains(extension) -> {
                        val mdFile = File(file.parentFile, fileName.substringBeforeLast(".") + ".md")
                        if (mdFile.exists() && mdFile.isFile) {
                            log.info("Found markdown file, rendering: ${mdFile.absolutePath}")
                            if (extension == "txt") {
                                resp.contentType = "text/plain"
                                resp.characterEncoding = "UTF-8"
                                resp.status = HttpServletResponse.SC_OK
                                resp.writer.write(mdFile.readText())
                                return
                            }
                            renderMarkdown(mdFile, resp, fileName.endsWith(".pdf"))
                        } else {
                            log.warn("File not found: ${file.absolutePath}")
                            resp.status = HttpServletResponse.SC_NOT_FOUND
                            resp.writer.write("File not found")
                        }
                    }

                    else -> {
                        log.warn("File not found: ${file.absolutePath}")
                        resp.status = HttpServletResponse.SC_NOT_FOUND
                        resp.writer.write("File not found")
                    }
                }
            }

            true == file?.isFile -> {
                log.info("File found: ${file.absolutePath}")
                var channel = channelCache.get(file)
                while (!channel.isOpen) {
                    log.warn("FileChannel is not open, refreshing cache for file: ${file.absolutePath}")
                    channelCache.refresh(file)
                    channel = channelCache.get(file)
                }
                try {
                    if (channel.size() > 1024 * 1024 * 1) {
                        log.info("File is large, using writeLarge method for file: ${file.absolutePath}")
                        writeLarge(channel, resp, file, req)
                    } else {
                        log.info("File is small, using writeSmall method for file: ${file.absolutePath}")
                        writeSmall(channel, resp, file, req)
                    }
                } finally {

                }
            }

            req.pathInfo?.endsWith("/") == false -> {
                log.info("Redirecting to directory path: ${req.requestURI + "/"}")
                resp.sendRedirect(req.requestURI + "/")
            }

            else -> {
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.status = HttpServletResponse.SC_OK
                val currentPathString = pathSegments.drop(1).joinToString("/")
                val servletPathBase =
                    req.contextPath + req.servletPath.removeSuffix("/*")
                        .removeSuffix("/") + "/" + req.pathInfo.split("/").firstOrNull { it.isNotBlank() }

                val (files, folders) = listContents(file, req)
                resp.writer.write(
                    directoryHTML(
                        currentPathString,
                        servletPathBase,
                        getZipLink(req, currentPathString),
                        folders,
                        files
                    )
                )
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received POST request for file upload at path: ${req.pathInfo ?: req.servletPath}")
        try {
            val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
            val dir = getDir(req)
            val targetDir = dir?.let { getFile(it, pathSegments, req) }
            if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory) {
                log.warn("Target directory does not exist or is not a directory: ${targetDir?.absolutePath}")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid target directory")
                return
            }
            val filePart: Part? = req.getPart("file")
            if (filePart == null) {
                log.warn("No file part found in upload request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("No file uploaded")
                return
            }
            val fileName = getSubmittedFileName(filePart)
            if (fileName.isNullOrBlank()) {
                log.warn("No filename provided in upload request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("No filename provided")
                return
            }
            // Validate filename for security
            if (!isValidFileName(fileName)) {
                log.warn("Invalid filename attempted: $fileName")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid filename")
                return
            }
            val targetFile = File(targetDir, fileName)
            // Check if file already exists - no overwriting allowed
            if (targetFile.exists()) {
                log.warn("File already exists, overwriting not allowed: ${targetFile.absolutePath}")
                resp.status = HttpServletResponse.SC_CONFLICT
                resp.writer.write("File already exists. Overwriting is not allowed.")
                return
            }
            // Save the uploaded file
            filePart.inputStream.use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            log.info("File uploaded successfully: ${targetFile.absolutePath}")
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "File uploaded successfully", "filename": "$fileName"}""")
        } catch (e: Exception) {
            log.error("Error during file upload", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error uploading file: ${e.message}")
        }
    }

    private fun getSubmittedFileName(part: Part): String? {
        val contentDisposition = part.getHeader("content-disposition")
        if (contentDisposition != null) {
            for (token in contentDisposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    return token.substring(token.indexOf('=') + 1).trim().trim('"')
                }
            }
        }
        return null
    }

    private fun isValidFileName(fileName: String): Boolean {
        // Reject path traversal attempts and invalid characters
        return !fileName.contains("..") &&
                !fileName.contains("/") &&
                !fileName.contains("\\") &&
                !fileName.contains(":") &&
                !fileName.contains("~") &&
                fileName.isNotBlank() &&
                fileName.all { it.code >= 32 && it.code <= 126 }
    }


    open fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
        val files = file?.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.joinToString("") {
                val fileName = it.name
                val baseLink = """<a class="item-link" href="${fileName}"><span class="icon">📄</span>${fileName}</a>"""
                val htmlLink = if (fileName.endsWith(".md")) {
                    val htmlFileName = fileName.substringBeforeLast(".") + ".html"
                    """ <a class="item-link" href="${htmlFileName}" style="margin-left: 0.5rem; font-size: 0.85rem;"><span class="icon">🌐</span>View as HTML</a>"""
                } else {
                    ""
                }
                """<li style="display: flex; align-items: center;">$baseLink$htmlLink</li>"""
            } ?: ""
        val folders = file?.listFiles()
            ?.filter { !it.isFile }
            ?.sortedBy { it.name }
            ?.joinToString("") {
                """<li><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a></li>"""
            } ?: ""
        return Pair(files, folders)
    }
    // getFile should construct the file path using all pathSegments relative to the base dir

    open fun getFile(dir: File, pathSegments: List<String>, req: HttpServletRequest) =
        File(dir, pathSegments.drop(1).joinToString("/"))

    private fun writeSmall(channel: FileChannel, resp: HttpServletResponse, file: File, req: HttpServletRequest) {
        log.info("Writing small file: ${file.absolutePath}")
        resp.contentType = getMimeType(file.name)
        resp.status = HttpServletResponse.SC_OK
        val async = req.startAsync()
        resp.outputStream.apply {
            setWriteListener(object : WriteListener {
                val buffer = ByteArray(16 * 1024)
                val byteBuffer = ByteBuffer.wrap(buffer)
                override fun onWritePossible() {
                    while (isReady) {
                        byteBuffer.clear()
                        val readBytes = channel.read(byteBuffer)
                        if (readBytes == -1) {
                            log.info("Completed writing small file: ${file.absolutePath}")
                            async.complete()
                            channelCache.put(file, channel)
                            return
                        }
                        write(buffer, 0, readBytes)
                    }
                }

                override fun onError(throwable: Throwable) {
                    log.error("Error writing small file: ${file.absolutePath}", throwable)
                    channelCache.put(file, channel)
                }
            })
        }
    }

    private fun writeLarge(
        channel: FileChannel,
        resp: HttpServletResponse,
        file: File,
        req: HttpServletRequest
    ) {
        log.info("Writing large file: ${file.absolutePath}")
        val mappedByteBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        resp.contentType = getMimeType(file.name)
        resp.status = HttpServletResponse.SC_OK
        val async = req.startAsync()
        resp.outputStream.apply {
            setWriteListener(object : WriteListener {
                val buffer = ByteArray(256 * 1024)
                override fun onWritePossible() {
                    while (isReady) {
                        val start = mappedByteBuffer.position()
                        val attemptedReadSize = buffer.size.coerceAtMost(mappedByteBuffer.remaining())
                        mappedByteBuffer.get(buffer, 0, attemptedReadSize)
                        val end = mappedByteBuffer.position()
                        val readBytes = end - start
                        if (readBytes == 0) {
                            log.info("Completed writing large file: ${file.absolutePath}")
                            async.complete()
                            channelCache.put(file, channel)
                            return
                        }
                        write(buffer, 0, readBytes)
                    }
                }

                override fun onError(throwable: Throwable) {
                    log.error("Error writing large file: ${file.absolutePath}", throwable)
                    channelCache.put(file, channel)
                }
            })
        }
    }

    private fun renderMarkdown(mdFile: File, resp: HttpServletResponse, asPdf: Boolean) {
        try {
            val markdownContent = mdFile.readText()
            val html = markdownContent.markdownToHtml()

            val fullHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <title>${mdFile.name}</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        pre { background-color: #f4f4f4; padding: 10px; border-radius: 4px; }
        code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 2px; }
    </style>
</head>
<body>
    $html
</body>
</html>
"""

            if (asPdf) {
                val outputStream = ByteArrayOutputStream()
                val baseUri = mdFile.parentFile.toURI().toString()

                PdfRendererBuilder()
                    .withHtmlContent(fullHtml, baseUri)
                    .toStream(outputStream)
                    .run()

                val byteArray = outputStream.toByteArray()
                resp.contentType = "application/pdf"
                resp.status = HttpServletResponse.SC_OK
                resp.outputStream.write(byteArray)
            } else {
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.status = HttpServletResponse.SC_OK
                resp.writer.write(fullHtml)
            }
        } catch (e: Exception) {
            log.error("Error rendering markdown file: ${mdFile.absolutePath}", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error rendering markdown: ${e.message}")
        }
    }


    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".js") -> "application/javascript"
            fileName.endsWith(".mjs") -> "application/javascript"
            fileName.endsWith(".log") -> "text/plain"
            else -> MimeTypes.getDefaultMimeByExtension(fileName) ?: "application/octet-stream"
        }
    }

    open fun getZipLink(
        req: HttpServletRequest,
        filePath: String
    ): String = ""


    private fun generateBreadcrumbs(currentPath: String, servletBaseHref: String): String {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val breadcrumbs = StringBuilder()
        val rootLink = if (servletBaseHref.endsWith("/")) servletBaseHref else "$servletBaseHref/"

        // Root breadcrumb
        if (parts.isEmpty()) {
            breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">Root</li>""")
        } else {
            breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink" style="color: #0d6efd; text-decoration:none;">Root</a></li>""")
        }

        var accumulatedPath = ""
        for ((index, part) in parts.withIndex()) {
            accumulatedPath += "$part/"
            // Separator
            if (index >= 0) { // Always add separator if there are parts after Root
                breadcrumbs.append("""<li style="padding-right: .5rem; color: #6c757d;">/</li>""")
            }

            if (index < parts.size - 1) {
                breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink$accumulatedPath" style="color: #0d6efd; text-decoration:none;">$part</a></li>""")
            } else {
                breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">$part</li>""")
            }
        }
        return breadcrumbs.toString()
    }

    private fun directoryHTML(
        currentPath: String,
        servletBaseHref: String,
        zipLink: String,
        folders: String,
        files: String
    ) = """
    |<!DOCTYPE html>
    |<html lang="en">
    |<head>
    |    <meta charset="UTF-8">
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <title>Directory Listing: /$currentPath</title>
    |    <style>
    |        body {
    |            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
    |            background-color: #f0f2f5; /* Light gray background */
    |            color: #1c1e21; /* Dark gray text */
    |            margin: 0;
    |            padding: 0;
    |            line-height: 1.5;
    |        }
    |        .navbar {
    |            background-color: #ffffff;
    |            padding: 1rem 1.5rem;
    |            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    |            margin-bottom: 1.5rem;
    |            display: flex;
    |            align-items: center;
    |            justify-content: space-between;
    |            flex-wrap: wrap; /* Allow wrapping for smaller screens */
    |        }
    |        .navbar-title {
    |            font-size: 1.4rem;
    |            font-weight: 600;
    |            color: #343a40; /* Darker title color */
    |            margin-right: 1rem; /* Space before ZIP link */
    |        }
    |        .zip-link {
    |            display: inline-block;
    |            padding: 0.5rem 1rem;
    |            font-size: 0.9rem;
    |            font-weight: 500;
    |            color: #fff;
    |            background-color: #0d6efd; /* Primary blue */
    |            border: none;
    |            border-radius: 0.25rem;
    |            text-decoration: none;
    |            transition: background-color 0.15s ease-in-out;
    |            white-space: nowrap;
    |        }
    |        .zip-link:hover {
    |            background-color: #0b5ed7; /* Darker blue on hover */
    |        }
    |        .upload-section {
    |            background-color: #ffffff;
    |            border: 1px solid #dee2e6;
    |            border-radius: 0.375rem;
    |            margin-bottom: 1.5rem;
    |            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    |        }
    |        .upload-form {
    |            display: flex;
    |            gap: 0.75rem;
    |            align-items: center;
    |            flex-wrap: wrap;
    |        }
    |        .file-input {
    |            flex: 1;
    |            min-width: 200px;
    |            padding: 0.5rem;
    |            border: 1px solid #ced4da;
    |            border-radius: 0.25rem;
    |            font-size: 0.9rem;
    |        }
    |        .upload-button {
    |            padding: 0.5rem 1.5rem;
    |            font-size: 0.9rem;
    |            font-weight: 500;
    |            color: #fff;
    |            background-color: #198754; /* Success green */
    |            border: none;
    |            border-radius: 0.25rem;
    |            cursor: pointer;
    |            transition: background-color 0.15s ease-in-out;
    |        }
    |        .upload-button:hover {
    |            background-color: #157347; /* Darker green on hover */
    |        }
    |        .upload-button:disabled {
    |            background-color: #6c757d;
    |            cursor: not-allowed;
    |        }
    |        .upload-message {
    |            margin-top: 0.5rem;
    |            padding: 0.5rem;
    |            border-radius: 0.25rem;
    |            font-size: 0.9rem;
    |        }
    |        .upload-message.success {
    |            background-color: #d1e7dd;
    |            color: #0f5132;
    |            border: 1px solid #badbcc;
    |        }
|        .upload-message.error {
    |            background-color: #f8d7da;
    |            color: #842029;
    |            border: 1px solid #f5c2c7;
    |        }
        .drop-zone {
            border: 2px dashed #ced4da;
            border-radius: 0.25rem;
            padding: 2rem;
            text-align: center;
            transition: all 0.3s ease;
            cursor: pointer;
            background-color: #f8f9fa;
        }
        .drop-zone.drag-over {
            border-color: #0d6efd;
            background-color: #e7f1ff;
        }
        .drop-zone-text {
            color: #6c757d;
            font-size: 0.95rem;
            margin-bottom: 0.5rem;
        }
        .drop-zone-hint {
            color: #adb5bd;
            font-size: 0.85rem;
        }
    |        .container {
    |            max-width: 960px;
    |            margin: 0 auto;
    |            padding: 0 1rem 1.5rem 1rem;
    |        }
    |        .breadcrumb-nav {
    |            margin-bottom: 1.5rem;
    |            padding: 0.75rem 1rem;
    |            background-color: #ffffff;
    |            border-radius: 0.25rem;
    |            box-shadow: 0 1px 2px rgba(0,0,0,0.04);
    |        }
    |        .breadcrumb {
    |            padding: 0; margin:0; list-style:none; display:flex; flex-wrap:wrap;
    |        }
    |        .section {
    |            background-color: #ffffff;
    |            border: 1px solid #dee2e6; /* Light border */
    |            border-radius: 0.375rem; /* Bootstrap-like radius */
    |            margin-bottom: 1.5rem;
    |            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    |        }
    |        .section-header {
    |            padding: 0.75rem 1.25rem;
    |            margin-bottom: 0;
    |            background-color: #f8f9fa; /* Very light gray for header */
    |            border-bottom: 1px solid #dee2e6;
    |            border-top-left-radius: calc(0.375rem - 1px);
    |            border-top-right-radius: calc(0.375rem - 1px);
    |        }
    |        .section-title {
    |            font-size: 1.2rem;
    |            font-weight: 500;
    |            color: #343a40; /* Darker text for titles */
    |            margin: 0;
    |        }
    |        .section-content {
    |            padding: 1.25rem;
    |        }
    |        .item-list {
    |            list-style: none;
    |            padding: 0;
    |            margin: 0;
    |        }
    |        .item-list li {
    |            margin-bottom: 0.25rem;
    |        }
    |        .item-list li:last-child { margin-bottom: 0; }
    |        .item-link {
    |            color: #0d6efd; /* Primary blue for links */
    |            text-decoration: none;
    |            display: flex;
    |            align-items: center;
    |            padding: 0.45rem 0.75rem; /* Slightly more padding */
    |            border-radius: 0.25rem;
    |            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
    |        }
    |        .item-link:hover {
    |            background-color: #e9ecef; /* Light gray hover for links */
    |            color: #0a58ca; /* Darker blue on hover */
    |        }
    |        .item-link .icon {
    |            margin-right: 0.7em; /* More space for icon */
    |            width: 1.2em; 
    |            text-align: center;
    |            color: #495057; /* Neutral icon color */
    |        }
    |        .item-link:hover .icon { color: #0a58ca; } /* Icon color on hover */
|        .empty-state {
    |            color: #6c757d; /* Secondary text color */
    |            padding: 0.5rem 0.75rem;
    |            font-style: italic;
    |        }
|    </style>
    |    <script>
        // Drag and drop functionality
        function setupDropZone() {
            const dropZone = document.getElementById('drop-zone');
            const fileInput = document.getElementById('file-input');
            dropZone.addEventListener('click', () => {
                fileInput.click();
            });
            dropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('drag-over');
            });
            dropZone.addEventListener('dragleave', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('drag-over');
            });
            dropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('drag-over');
                const files = e.dataTransfer.files;
                if (files.length > 0) {
                    fileInput.files = files;
                    updateFileInputDisplay(files[0].name);
                }
            });
            fileInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    updateFileInputDisplay(e.target.files[0].name);
                }
            });
        }
        // Clipboard paste functionality
        function setupClipboardPaste() {
            document.addEventListener('paste', (e) => {
                const items = e.clipboardData.items;
                for (let i = 0; i < items.length; i++) {
                    if (items[i].kind === 'file') {
                        e.preventDefault();
                        const file = items[i].getAsFile();
                        const fileInput = document.getElementById('file-input');
                        const dataTransfer = new DataTransfer();
                        dataTransfer.items.add(file);
                        fileInput.files = dataTransfer.files;
                        updateFileInputDisplay(file.name);
                        showMessage('File pasted from clipboard: ' + file.name, 'success');
                        break;
                    }
                }
            });
        }
        function updateFileInputDisplay(fileName) {
            const dropZoneText = document.querySelector('.drop-zone-text');
            dropZoneText.innerHTML = '<strong>Selected:</strong> ' + fileName;
        }
        // Initialize on page load
        window.addEventListener('DOMContentLoaded', () => {
            setupDropZone();
            setupClipboardPaste();
        });
        
    |        async function uploadFile(event) {
    |            event.preventDefault();
    |            const form = event.target;
    |            const fileInput = document.getElementById('file-input');
    |            const submitButton = form.querySelector('button[type="submit"]');
    |            const messageDiv = document.getElementById('upload-message');
    |            
    |            if (!fileInput.files || fileInput.files.length === 0) {
    |                showMessage('Please select a file to upload', 'error');
    |                return;
    |            }
    |            
    |            const formData = new FormData(form);
    |            submitButton.disabled = true;
    |            submitButton.textContent = 'Uploading...';
    |            messageDiv.textContent = '';
    |            messageDiv.className = 'upload-message';
    |            
    |            try {
    |                const response = await fetch(window.location.href, {
    |                    method: 'POST',
    |                    body: formData
    |                });
    |                
    |                const text = await response.text();
    |                
|                if (response.ok) {
    |                    showMessage('File uploaded successfully!', 'success');
    |                    fileInput.value = '';
                    const dropZoneText = document.querySelector('.drop-zone-text');
                    dropZoneText.innerHTML = 'Click to select, drag & drop, or paste (Ctrl+V) a file here';
    |                    // Reload the page after a short delay to show the new file
    |                    setTimeout(() => window.location.reload(), 1500);
    |                } else {
    |                    showMessage(text || 'Upload failed', 'error');
    |                }
    |            } catch (error) {
    |                showMessage('Upload failed: ' + error.message, 'error');
    |            } finally {
    |                submitButton.disabled = false;
    |                submitButton.textContent = 'Upload';
    |            }
    |        }
    |        
    |        function showMessage(message, type) {
    |            const messageDiv = document.getElementById('upload-message');
    |            messageDiv.textContent = message;
    |            messageDiv.className = 'upload-message ' + type;
    |        }
    |    </script>
    |</head>
    |<body>
    |    <div class="navbar">
    |        <span class="navbar-title"> File Browser</span>
    |        ${if (zipLink.isNotBlank()) """<a href="$zipLink" class="zip-link">Download Current Directory as ZIP</a>""" else ""}
    |    </div>
    |    <div class="container">
    |        <nav class="breadcrumb-nav" aria-label="breadcrumb">
    |           <ol class="breadcrumb">
    |               ${generateBreadcrumbs(currentPath, servletBaseHref)}
    |           </ol>
    |        </nav>
    |
|        <div class="section upload-section">
    |            <div class="section-header"><h2 class="section-title">Upload File</h2></div>
    |            <div class="section-content">
    |                <form class="upload-form" onsubmit="uploadFile(event)" enctype="multipart/form-data">
    |                    <div id="drop-zone" class="drop-zone">
    |                        <div class="drop-zone-text">Click to select, drag & drop, or paste (Ctrl+V) a file here</div>
    |                        <div class="drop-zone-hint">Maximum file size: 50MB</div>
    |                    </div>
    |                    <input type="file" name="file" id="file-input" class="file-input" required style="display: none;">
    |                    <button type="submit" class="upload-button">Upload</button>
    |                </form>
    |                <div id="upload-message" class="upload-message"></div>
    |            </div>
    |        </div>
    |
    |        <div class="section">
    |            <div class="section-header"><h2 class="section-title">Folders</h2></div>
    |            <div class="section-content">
    |                ${if (folders.isBlank()) "<p class=\"empty-state\">No sub-folders found.</p>" else "<ul class=\"item-list\">$folders</ul>"}
    |            </div>
    |        </div>
    |
    |        <div class="section">
    |            <div class="section-header"><h2 class="section-title">Files</h2></div>
    |            <div class="section-content">
    |                ${if (files.isBlank()) "<p class=\"empty-state\">No files found.</p>" else "<ul class=\"item-list\">$files</ul>"}
    |            </div>
    |        </div>
    |    </div>
    |</body>
    |</html>
    """.trimMargin()

    companion object {
        val log = LoggerFactory.getLogger(FileServlet::class.java)
        fun parsePath(path: String): List<String> {
            val pathSegments = path.split("/").filter { it.isNotBlank() }
            pathSegments.forEach {
                when {
                    it == ".." -> throw IllegalArgumentException("Invalid path")
                    it.any {
                        when {
                            it == ':' -> true
                            it == '/' -> true
                            it == '~' -> true
                            it == '\\' -> true
                            it.code < 32 -> true
                            it.code > 126 -> true
                            else -> false
                        }
                    } -> throw IllegalArgumentException("Invalid path")
                }
            }
            return pathSegments
        }

        val channelCache: LoadingCache<File, FileChannel> = CacheBuilder
            .newBuilder().maximumSize(100)
            .expireAfterAccess(10, java.util.concurrent.TimeUnit.SECONDS)
            .removalListener(RemovalListener<File, FileChannel> { notification ->
                log.info("Closing FileChannel for file: ${notification.key}")
                try {
                    val channel = notification.value
                    if (channel == null) {
                        log.error("FileChannel is null for file: ${notification.key}")
                    } else {
                        channel.close()
                        log.info("Successfully closed FileChannel for file: ${notification.key}")
                    }
                } catch (e: Throwable) {
                    log.error("Error closing FileChannel for file: ${notification.key}", e)
                }
            }).build(object : CacheLoader<File, FileChannel>() {
                override fun load(key: File): FileChannel {
                    log.info("Opening FileChannel for file: ${key.absolutePath}")
                    return FileChannel.open(key.toPath(), StandardOpenOption.READ)
                }
            })
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/LogoutServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val cookie = req.getCookie()
        val user = ApplicationServices.authenticationManager.getUser(cookie)
        if (null == user) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            ApplicationServices.authenticationManager.logout(cookie ?: "", user)
            resp.sendRedirect("/")
        }
    }

    companion object
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/NewSessionServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.Session
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class NewSessionServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val sessionId = Session.newGlobalID()
        resp.contentType = "text/plain"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(sessionId.toString())
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/OAuthBase.kt

```
package com.simiacryptus.cognotik.webui.servlet

import org.eclipse.jetty.webapp.WebAppContext

abstract class OAuthBase(val redirectUri: String) {
    abstract fun configure(context: WebAppContext, addFilter: Boolean = true): WebAppContext
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/OAuthGoogle.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.oauth2.Oauth2
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

open class OAuthGoogle(
    redirectUri: String,
    val applicationName: String,
    key: () -> InputStream?
) : OAuthBase(redirectUri) {

    override fun configure(context: WebAppContext, addFilter: Boolean): WebAppContext {
        context.addServlet(ServletHolder("googleLogin", LoginServlet()), "/login")
        context.addServlet(ServletHolder("googleLogin", LoginServlet()), "/googleLogin")
        context.addServlet(ServletHolder("oauth2callback", CallbackServlet()), "/oauth2callback")
        if (addFilter) context.addFilter(FilterHolder(SessionIdFilter({ request ->
            setOf("/googleLogin", "/oauth2callback").none { request.requestURI.startsWith(it) }
        }, "/googleLogin")), "/*", EnumSet.of(DispatcherType.REQUEST))
        return context
    }

    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val flow = key()?.let { inputStream ->
        GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jsonFactory,
            GoogleClientSecrets.load(
                jsonFactory,
                InputStreamReader(inputStream)
            ),
            scopes
        ).build()
    } ?: throw IllegalArgumentException("No key provided")

    private inner class LoginServlet : HttpServlet() {
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            val redirect = req.getParameter("redirect") ?: ""
            val state = URLEncoder.encode(redirect, StandardCharsets.UTF_8.toString())
            val authorizationUrl =

                GoogleAuthorizationCodeRequestUrl(
                    /* authorizationServerEncodedUrl = */ flow.authorizationServerEncodedUrl,
                    /* clientId = */ flow.clientId,
                    /* redirectUri = */ redirectUri,
                    /* scopes = */ flow.scopes
                )
                    .setAccessType(flow.accessType)
                    .setApprovalPrompt(flow.approvalPrompt)
                    .setState(state)
                    .build()
            resp.sendRedirect(authorizationUrl)
        }
    }

    private inner class CallbackServlet : HttpServlet() {
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            val code = req.getParameter("code")
            if (code != null) {
                val credential = flow.createAndStoreCredential(
                    flow.newTokenRequest(code).setRedirectUri(redirectUri).execute(), null
                )
                val userInfo = Oauth2.Builder(
                    httpTransport,
                    jsonFactory,
                    credential
                ).setApplicationName(applicationName).build().userinfo().get().execute()
                val user = User(
                    id = userInfo.id,
                    email = userInfo.email,
                    name = userInfo.name,
                    picture = userInfo.picture,
                    credential = credential,
                )
                val sessionID = UUID.randomUUID().toString()
                ApplicationServices.authenticationManager.putUser(accessToken = sessionID, user = user)
                log.info("User $user logged in with session $sessionID")
                val sessionCookie = Cookie(AuthenticationInterface.AUTH_COOKIE, sessionID)
                sessionCookie.path = "/"
                sessionCookie.isHttpOnly = true
                sessionCookie.secure = true
                sessionCookie.maxAge = TimeUnit.DAYS.toSeconds(1).toInt()
                sessionCookie.comment = "Authentication Session ID"
                resp.addCookie(sessionCookie)
                val redirect = req.getParameter("state")?.urlDecode()
                resp.sendRedirect(redirect ?: "/")
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Authorization code not found")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OAuthGoogle::class.java)

        fun String.urlDecode(): String = try {
            URLDecoder.decode(this, StandardCharsets.UTF_8.toString())
        } catch (e: UnsupportedEncodingException) {
            this
        }

        var scopes = listOf(
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
        ).toMutableSet()

    }

}


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/ProxyHttpServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.util.Timeout
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * A simple reverse proxy that supports the OpenAI API
 */
open class ProxyHttpServlet(
    private val targetUrl: String = "https://api.openai.com/"
) : HttpServlet() {

    open val asyncClient: CloseableHttpAsyncClient by lazy {
        HttpAsyncClientBuilder.create()
            .setRetryStrategy(DefaultHttpRequestRetryStrategy(0, Timeout.ofSeconds(1)))
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(5, TimeUnit.MINUTES))
                    .setResponseTimeout(Timeout.of(5, TimeUnit.MINUTES))
                    .build()
            )
            .setConnectionManager(with(PoolingAsyncClientConnectionManager()) {
                defaultMaxPerRoute = 1000
                maxTotal = 1000
                this
            }).build().apply {
                start()
            }
    }

    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        val asyncContext = req.startAsync()
        asyncContext.timeout = 0
        val requestKey = req.getHeaders("Authorization").nextElement().removePrefix("Bearer ")
        val proxyKey = ApiKeyServlet.apiKeyRecords.find { it.apiKey.decrypt == requestKey }
        val path = (req.servletPath ?: "").removePrefix("/")
        val proxyRequest = getProxyRequest(req)
        if (null != proxyKey) proxyRequest.addHeader("Authorization", "Bearer " + proxyKey.mappedKey)
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie(AuthenticationInterface.AUTH_COOKIE))
        val totalUsage =
            ApplicationServices.fileApplicationServices().usageManager.getUserUsageSummary(user!!).values.sumOf {
                it.cost ?: 0.0
            }
        if (totalUsage > (proxyKey?.budget ?: 0.0)) {
            resp.status = 402
            resp.contentType = "text/plain"
            resp.writer.println("Budget exceeded")
            asyncContext.complete()
            return
        }
        asyncClient.execute(proxyRequest, object : FutureCallback<SimpleHttpResponse> {
            override fun completed(proxyResponse: SimpleHttpResponse?) {
                resp.status = proxyResponse?.code ?: 500
                proxyResponse?.headers?.forEach { header ->
                    resp.addHeader(header.name, header.value)
                }
                val proxyResponseBody = proxyResponse?.bodyBytes ?: ByteArray(0)

                resp.outputStream.write(
                    onResponse(
                        req,
                        path,
                        proxyResponse,
                        proxyResponseBody,
                        proxyKey,
                        proxyRequest.body?.bodyBytes
                    )
                )
                asyncContext.complete()
            }

            override fun failed(exception: Exception?) {
                resp.status = 500
                resp.contentType = "text/plain"
                resp.writer.println(exception?.message)
                asyncContext.complete()
            }

            override fun cancelled() {
                resp.status = 500
                resp.contentType = "text/plain"
                resp.writer.println("Cancelled")
                asyncContext.complete()
            }

        })
    }

    private fun getProxyRequest(req: HttpServletRequest): SimpleHttpRequest {
        val path = (req.servletPath ?: "").removePrefix("/")
        val url = URI(targetUrl).resolve(path).toString()
        val proxyRequest = SimpleHttpRequest(req.method, url)
        val headers = req.headerNames.toList().filter {
            when (it) {
                "Authorization" -> false

                "Connection" -> false
                "Host" -> false
                "Keep-Alive" -> false
                "Transfer-Encoding" -> false
                "Upgrade" -> false
                else -> true
            }
        }.associateWith { req.getHeaders(it).asSequence() }.toMutableMap()
        headers.forEach { (key, values) ->
            values.forEach { value -> proxyRequest.addHeader(key, value) }
        }
        val bytes = req.inputStream.readAllBytes()
        proxyRequest.setBody(onRequest(req, bytes), ContentType.create(req.contentType ?: "text/plain"))
        return proxyRequest
    }

    @OptIn(ExperimentalStdlibApi::class)
    open fun onResponse(
        req: HttpServletRequest,
        path: String,
        proxyResponse: SimpleHttpResponse?,
        bodyBytes: ByteArray,
        proxyKey: ApiKeyServlet.ApiKeyRecord?,
        requestBody: ByteArray?
    ): ByteArray {
        val body = JsonUtil.fromJson<Map<String, Any>>(
            String(GZIPInputStream(bodyBytes.inputStream()).readAllBytes()),
            typeOf<Map<String, Any>>().javaType
        )
        val parsedRequest = JsonUtil.fromJson<Map<String, Any>>(
            String(requestBody ?: ByteArray(0)),
            typeOf<Map<String, Any>>().javaType
        )
        when (path) {
            "moderations" -> {
                log.info(
                    "Proxy $path\nRequest: ${
                        JsonUtil.toJson(parsedRequest).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }\nResponse: ${
                        JsonUtil.toJson(body).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }"
                )
            }

            "chat/completions" -> {
                log.info(
                    "Proxy $path\nRequest: ${
                        JsonUtil.toJson(parsedRequest).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }\nResponse: ${
                        JsonUtil.toJson(body).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }"
                )
            }

            else -> {
                log.info(
                    "Proxy $path\nRequest: ${
                        JsonUtil.toJson(parsedRequest).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }\nResponse: ${
                        JsonUtil.toJson(body).lineSequence()
                            .map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "  ".length -> "  "
                                            else -> it
                                        }
                                    }

                                    else -> "  " + it
                                }
                            }
                            .joinToString("\n")
                    }"
                )
            }
        }
        return bodyBytes
    }

    open fun onRequest(req: HttpServletRequest, bytes: ByteArray?): ByteArray? {
        return bytes
    }

    companion object {
        val log = LoggerFactory.getLogger(ProxyHttpServlet::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            test()
        }

        fun test() {

            val server = Server(8080)
            val contextHandlerCollection = ContextHandlerCollection()
            val servletHandler = ServletHandler()
            servletHandler.server = server
            servletHandler.addServletWithMapping(ServletHolder(ProxyHttpServlet("http://localhost:8080")), "/proxy/*")
            servletHandler.addServletWithMapping(ServletHolder(object : HttpServlet() {
                override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
                    resp.writer.println("Hello, world!")
                }
            }), "/test")
            contextHandlerCollection.addHandler(servletHandler)
            server.handler = contextHandlerCollection
            server.start()

            val connection = URL("http://localhost:8080/proxy/test").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.doOutput = true
            connection.doInput = true
            connection.connect()
            val inputStream = connection.inputStream
            val outputStream = System.out
            inputStream.copyTo(outputStream)
            connection.disconnect()
            server.stop()
        }
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionFileServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import java.io.File

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {
    override fun getDir(req: HttpServletRequest): File? {
        val pathInfo = req.pathInfo ?: req.servletPath
        val pathSegments = parsePath(pathInfo ?: "/")
        val session = Session(parsePath(pathInfo ?: "/").first())
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionDir = dataStorage.getSessionDir(user, session)
        val dataDir = dataStorage.getDataDir(user, session)
        val dirs = if (sessionDir.absolutePath != dataDir.absolutePath) {
            listOf(sessionDir, dataDir)
        } else {
            listOf(sessionDir)
        }

        // First, try to find the exact file
        val exactMatch = dirs.firstOrNull { getFile(it, pathSegments, req).exists() }
        if (exactMatch != null) return exactMatch

        // If not found, check if this is a request for HTML/PDF with an equivalent .md file
        val requestedFile = getFile(dirs.first(), pathSegments, req)
        val fileName = requestedFile.name
        if (fileName.endsWith(".html") || fileName.endsWith(".pdf") || fileName.endsWith(".txt")) {
            val mdFileName = fileName.substringBeforeLast(".") + ".md"
            val mdMatch = dirs.firstOrNull {
                val mdFile = File(getFile(it, pathSegments, req).parentFile, mdFileName)
                mdFile.exists() && mdFile.isFile
            }
            if (mdMatch != null) return mdMatch
        }
        return null
    }

    override fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
        file?.let { return super.listContents(it, req) }
        val pathInfo = req.pathInfo ?: req.servletPath
        val session = Session(parsePath(pathInfo ?: "/").first())
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionPair = listContents(dataStorage.getSessionDir(user, session), req)
        val dataPair = listContents(dataStorage.getDataDir(user, session), req)
        return Pair(sessionPair.first + dataPair.first, sessionPair.second + dataPair.second)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionIdFilter.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLEncoder

class SessionIdFilter(
    val isSecure: (HttpServletRequest) -> Boolean,
    private val loginRedirect: String
) : Filter {

    override fun init(filterConfig: FilterConfig?) {}

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequest && response is HttpServletResponse) {
            if (isSecure(request)) {
                val sessionIdCookie = request.getCookie()
                if (sessionIdCookie == null || null == ApplicationServices.authenticationManager.getUser(
                        sessionIdCookie
                    )
                ) {
                    val queryString = request.queryString
                    val originalUrl =
                        if (queryString != null) "${request.requestURL}?${queryString}" else request.requestURL.toString()
                    val encodedUrl = URLEncoder.encode(originalUrl, "UTF-8")
                    val redirectUrl = "$loginRedirect?redirect=$encodedUrl"
                    response.sendRedirect(redirectUrl)
                    return
                }
            }
        }
        chain.doFilter(request, response)
    }

    override fun destroy() {}
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionListServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.text.SimpleDateFormat

class SessionListServlet(
    private val dataStorage: StorageInterface,
    private val prefix: String,
    private val applicationServer: ApplicationServer
) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK
        val user = authenticationManager.getUser(req.getCookie())
        val sessions = dataStorage.listSessions(user, req.contextPath)
        val sessionRows = sessions.joinToString("") { session ->
            val sessionName = dataStorage.getSessionName(user, session)
            val sessionTime = dataStorage.getSessionTime(user, session) ?: return@joinToString ""
            val sessionTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(sessionTime)
            """
            <tr class="session-row" onclick="window.location.href='$prefix#$session'">

                    <td><a href="$prefix#$session" class="session-link">$sessionName</a></td>
                    <td><a href="$prefix#$session" class="session-link">$sessionTimeStr</a></td>
            </tr>
            """.trimIndent()
        }
        val title = """Sessions"""

        resp.writer.write(
            """
            <html>
            <head>
            <title>$title</title>
            <style>
                body { font-family: Arial, sans-serif; }
                table { width: 100%; border-collapse: collapse; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                tr:hover { background-color: #ddd; }
                a { text-decoration: none; color: #333; }
            </style>
            </head>
            <body>
            <div id='app-description'>
            ${applicationServer.description}
            </div>
            <table class='applist' id='session-list'>
                <tr>
                    <th>Session Name</th>
                    <th>Created</th>
                </tr>
                ${sessionRows.indent("    ")}
            </table>
            </body>
            </html>
            """.trimIndent()
        )
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionSettingsServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

class SessionSettingsServlet(
    private val server: ApplicationServer,
) : HttpServlet() {
    private val logger = LoggerFactory.getLogger(SessionSettingsServlet::class.java)
    val settingsClass = Map::class.java


    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            logger.info("Handling GET request from ${req.remoteAddr} with parameters: ${req.parameterMap}")
            resp.contentType = "text/html"
            resp.status = HttpServletResponse.SC_OK

            if (req.parameterMap.containsKey("sessionId")) {
                val sessionId = req.getParameter("sessionId")
                logger.debug("Processing request for session: $sessionId")
                val session = Session(sessionId)
                val cookie = req.getCookie()
                val user = authenticationManager.getUser(cookie)
                logger.debug("User identified: ${user?.id ?: "anonymous"}")

                try {
                    val settings = server.getSettings(session, user, settingsClass)
                    val json = if (settings != null) JsonUtil.toJson(settings) else ""
                    logger.debug("Retrieved settings for session $sessionId: ${json.take(100)}${if (json.length > 100) "..." else ""}")

                    if (req.parameterMap.containsKey("raw") && req.getParameter("raw") == "true") {
                        logger.debug("Returning raw JSON response")
                        resp.contentType = "application/json"
                        resp.writer.write(json)
                        return
                    }

                    resp.writer.write(
                        """
            <html>
            <head>
                <title>Settings</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            </head>
            <body>
            <form action="""".trimIndent() + req.contextPath + """/settings" method="post">
                <input type="hidden" name="sessionId" value="""" + session + """"/>
                <input type="hidden" name="action" value="save"/>
                <textarea name="settings" style="width: 100%; height: 100px;">""" + json + """</textarea>
                <input type="submit" value="Save"/>
            </form>
            </body>
            </html>
          """.trimIndent()
                    )
                } catch (e: Exception) {
                    logger.error("Error retrieving settings for session $sessionId", e)
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.writer.write("Error retrieving settings: ${e.message}")
                }
            } else {
                logger.warn("Request missing required sessionId parameter")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Session ID is required")
            }
        } catch (e: Exception) {
            logger.error("Unhandled exception in doGet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Internal server error: ${e.message}")
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            logger.info("Handling POST request from ${req.remoteAddr}")
            resp.contentType = "text/html"
            resp.status = HttpServletResponse.SC_OK

            if (!req.parameterMap.containsKey("sessionId")) {
                logger.warn("POST request missing required sessionId parameter")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Session ID is required")
            } else {
                val sessionId = req.getParameter("sessionId")
                logger.debug("Processing POST request for session: $sessionId")
                val session = Session(sessionId)

                try {
                    val settingsJson = if (req.parameterNames.toList().contains("settings")) {
                        val paramSettings = req.getParameter("settings")
                        logger.debug("Using settings from parameter: ${paramSettings.take(100)}${if (paramSettings.length > 100) "..." else ""}")
                        paramSettings
                    } else {
                        val bodySettings = req.reader.readText()
                        logger.debug("Using settings from request body: ${bodySettings.take(100)}${if (bodySettings.length > 100) "..." else ""}")
                        bodySettings
                    }

                    val settings = JsonUtil.fromJson<Any>(settingsJson, settingsClass)
                    val cookie = req.getCookie()
                    val user = authenticationManager.getUser(cookie)
                    logger.debug("User identified for settings update: ${user?.id ?: "anonymous"}")

                    val settingsFile = server.getSettingsFile(session, user ?: UserSettingsManager.defaultUser)
                    settingsFile.parentFile.mkdirs()
                    logger.debug("Saving settings to file: ${settingsFile.absolutePath}")

                    try {
                        settingsFile.writeText(JsonUtil.toJson(settings))
                        logger.info("Successfully saved settings for session $sessionId")
                        resp.sendRedirect("${req.contextPath}/#$session")
                    } catch (e: IOException) {
                        logger.error("Failed to write settings to file: ${settingsFile.absolutePath}", e)
                        resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        resp.writer.write("Failed to save settings: ${e.message}")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing settings for session $sessionId", e)
                    resp.status = HttpServletResponse.SC_BAD_REQUEST
                    resp.writer.write("Invalid settings format: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Unhandled exception in doPost", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Internal server error: ${e.message}")
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionThreadsServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.ApplicationServices.threadPoolManager
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
            val pool = threadPoolManager.getPool(session, user)


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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SymbolGraphServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class SymbolGraphServlet(private val service: SymbolGraphService) : HttpServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo ?: "/"
        log.info("SymbolGraphServlet GET $path")

        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"

        try {
            when {
                path == "/search" -> {
                    val query = req.getParameter("q")
                    if (query.isNullOrBlank()) {
                        resp.writer.write("[]")
                    } else {
                        val symbols = service.search(query)
                        writeSymbols(resp, symbols)
                    }
                }
                path == "/symbol" -> {
                    val id = req.getParameter("id")
                    if (id == null) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.writer.write("""{"error": "Missing id parameter"}""")
                    } else {
                        val symbol = service.getSymbol(id)
                        if (symbol == null) {
                            resp.status = HttpServletResponse.SC_NOT_FOUND
                            resp.writer.write("""{"error": "Symbol not found"}""")
                        } else {
                            writeSymbol(resp, symbol, detailed = true)
                        }
                    }
                }
                path == "/files" -> writeSymbols(resp, service.getFiles())
                path == "/languages" -> writeStrings(resp, service.listLanguages())
                path == "/libraries" -> writeStrings(resp, service.listLibraries())
                path == "/packages" -> writeStrings(resp, service.listPackages())

                path.startsWith("/file/") -> {
                    val fileId = path.substring("/file/".length)
                    writeSymbols(resp, service.getSymbolsByFile(fileId))
                }
                path.startsWith("/language/") -> {
                    val lang = path.substring("/language/".length)
                    writeSymbols(resp, service.getSymbolsByLanguage(lang))
                }
                path.startsWith("/library/") -> {
                    val lib = path.substring("/library/".length)
                    writeSymbols(resp, service.getSymbolsByLibrary(lib))
                }
                path.startsWith("/package/") -> {
                    val pkg = path.substring("/package/".length)
                    writeSymbols(resp, service.getSymbolsByPackage(pkg))
                }

                else -> {
                    resp.writer.write("""{
                        "endpoints": [
                            "/search?q=<query>",
                            "/symbol?id=<id>",
                            "/files",
                            "/languages",
                            "/libraries",
                            "/packages",
                            "/file/<fileId>",
                            "/language/<language>",
                            "/library/<library>",
                            "/package/<package>"
                        ]
                    }""")
                }
            }
        } catch (e: Exception) {
            log.error("Error in SymbolGraphServlet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("""{"error": "${escapeJson(e.message ?: "Unknown error")}"}""")
        }
    }

    private fun writeSymbols(resp: HttpServletResponse, symbols: List<SymbolGraphService.Symbol>) {
        resp.writer.write("[")
        symbols.forEachIndexed { index, symbol ->
            if (index > 0) resp.writer.write(",")
            writeSymbol(resp, symbol, detailed = false)
        }
        resp.writer.write("]")
    }

    private fun writeSymbol(resp: HttpServletResponse, symbol: SymbolGraphService.Symbol, detailed: Boolean) {
        val props = symbol.properties.toMutableMap()
        if (detailed) {
            symbol.file()?.let { props["file_obj"] = it.properties }
            symbol.language()?.let { props["language"] = it }
            symbol.packageName()?.let { props["package"] = it }
            symbol.libraryName()?.let { props["library"] = it }
        }
        resp.writer.write(toJson(props))
    }

    private fun writeStrings(resp: HttpServletResponse, list: List<String>) {
        resp.writer.write("[")
        list.forEachIndexed { index, s ->
            if (index > 0) resp.writer.write(",")
            resp.writer.write("\"${escapeJson(s)}\"")
        }
        resp.writer.write("]")
    }

    private fun toJson(map: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"${escapeJson(k)}\":")
            when (v) {
                is Number -> sb.append(v)
                is Boolean -> sb.append(v)
                is Map<*, *> -> sb.append(toJson(v as Map<String, Any>))
                else -> sb.append("\"${escapeJson(v.toString())}\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
    }

    companion object {
        val log = LoggerFactory.getLogger(SymbolGraphServlet::class.java)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/TaskConfigServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

class TaskConfigServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val taskTypes = TaskType.values().map { taskType ->
            mapOf(
                "id" to taskType.name,
                "name" to (taskType.name.replace(Regex("([a-z])([A-Z]+)"), "$1 $2") + " Task"),
                "description" to (taskType.description ?: ""),
                "category" to taskType.category,
                "configFields" to getConfigFields(taskType.taskSettingsClass.kotlin)
            )
        }

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(JsonUtil.toJson(taskTypes))
    }

    private fun getConfigFields(kClass: KClass<out TaskTypeConfig>): List<Map<String, Any>> {
        val instance = try {
            kClass.createInstance()
        } catch (e: Throwable) {
            null
        }
        return kClass.memberProperties
            .filter { it.name !in setOf("task_type", "name", "model") }
            .mapNotNull { prop ->
                val description = prop.annotations.filterIsInstance<Description>().firstOrNull()?.value
                val type = when (prop.returnType.classifier) {
                    Boolean::class -> "checkbox"
                    Int::class, Long::class, Double::class -> "number"
                    String::class -> if (prop.name.contains("code", true) || prop.name.contains(
                            "prompt",
                            true
                        )
                    ) "textarea" else "text"

                    else -> {
                        if ((prop.returnType.classifier as? KClass<*>)?.java?.isEnum == true) {
                            "select"
                        } else if (DynamicEnum::class.java.isAssignableFrom((prop.returnType.classifier as? KClass<*>)?.java)) {
                            "select"
                        } else {
                            null
                        }
                    }
                }

                if (type != null) {
                    val field = mutableMapOf<String, Any>(
                        "id" to prop.name,
                        "label" to prop.name
                            .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                            .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
                        "type" to type
                    )
                    if (description != null) field["tooltip"] = description

                    if (type == "select") {
                        val enumClass = (prop.returnType.classifier as KClass<*>).java
                        val enumConstants = enumClass.enumConstants
                        if (null != enumConstants && enumConstants.isNotEmpty()) {
                            // Handle standard enums
                            field["options"] = enumConstants.map { it.toString() }
                            field["default"] = enumConstants.firstOrNull()?.toString() ?: ""
                        } else if (DynamicEnum::class.java.isAssignableFrom(enumClass)) {
                            // Handle DynamicEnum types
                            val dynamicEnumCompanion = enumClass.getDeclaredField("Companion").get(null)
                            val valuesMethod = dynamicEnumCompanion.javaClass.getMethod("values")
                            val dynamicEnumValues = valuesMethod.invoke(dynamicEnumCompanion) as List<DynamicEnum<*>>
                            field["options"] = dynamicEnumValues.map { it.name }
                            field["default"] = dynamicEnumValues.firstOrNull()?.name ?: ""
                        }
                    }
                    if (instance != null) {
                        try {
                            val value = prop.getter.call(instance)
                            if (value != null) {
                                field["default"] = if (type == "select") value.toString() else value
                            }
                        } catch (e: Throwable) {
                            // Ignore
                        }
                    }


                    field
                } else null
            }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/UsageServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UsageServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK

        val usageManager = ApplicationServices.fileApplicationServices().usageManager
        if (req.parameterMap.containsKey("sessionId")) {
            val session = Session(req.getParameter("sessionId"))
            serve(resp, usageManager.getSessionUsageSummary(session))
        } else {
            val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
            if (null == userinfo) {
                resp.status = HttpServletResponse.SC_BAD_REQUEST
            } else {
                val usage = usageManager.getUserUsageSummary(userinfo)
                serve(resp, usage)
            }
        }
    }

    private fun serve(
        resp: HttpServletResponse,
        usage: Map<String, ModelSchema.Usage>
    ) {
        val totalPromptTokens = usage.values.sumOf { it.prompt_tokens }
        val totalCompletionTokens = usage.values.sumOf { it.completion_tokens }
        val totalCost = usage.entries.sumOf { (_, count) -> count.cost ?: 0.0 }

        resp.writer.write(
            """
            <html>
            <head>
                <title>Usage</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                <style>
                    body { font-family: Arial, sans-serif; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    tr:nth-child(even) { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
            <table class="usage-table">
                <tr class="table-header">
                    <th>Model</th>
                    <th>Prompt</th>
                    <th>Completion</th>
                    <th>Cost</th>
                </tr>
                ${
                usage.entries.joinToString("\n") { (model, count) ->
                    """
                        <tr class="table-row">
                            <td class="model-cell">$model</td>
                            <td class="prompt-cell">${count.prompt_tokens}</td>
                            <td class="completion-cell">${count.completion_tokens}</td>
                            <td class="cost-cell">${"%.4f".format(count.cost ?: 0.0)}</td>
                        </tr>
                        """.trimIndent()
                }
            }
            <tr class="table-row">
                <td class="model-cell">Total</td>
                <td class="prompt-cell">$totalPromptTokens</td>
                <td class="completion-cell">$totalCompletionTokens</td>
                <td class="cost-cell">${"%.4f".format(totalCost)}</td>
            </tr>
            </table>
            </body>
            </html>
            """.trimIndent())
    }

    companion object
}


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/UserInfoServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UserInfoServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/json"
        resp.status = HttpServletResponse.SC_OK
        val user: User? = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == user) {
            resp.writer.write("{}")
        } else {
            resp.writer.write(JsonUtil.objectMapper().writeValueAsString(user))
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/UserSettingsServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.encrypt
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val mask = "********"

class UserSettingsServlet : HttpServlet() {
    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_OK
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == userinfo) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            try {
                val settings =
                    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(userinfo)
                val visibleSettings = UserSettings(
                    apis = settings.apis.map { apiData ->
                        ApiData(
                            key = when (apiData.key?.decrypt) {
                                "" -> ""
                                null -> null
                                else -> mask
                            }?.encrypt,
                            baseUrl = apiData.baseUrl,
                            provider = apiData.provider
                        )//.validate()
                    }.toMutableList(),
                    tools = settings.tools.toMutableList(),
                    etc = settings.etc.toMutableMap()
                )
                val json = JsonUtil.toJson(visibleSettings)
                val acceptHeader = req.getHeader("Accept") ?: ""
                if (acceptHeader.contains("application/json")) {
                    resp.contentType = "application/json"
                    resp.writer.write(json)
                } else {
                    resp.contentType = "text/html"
                    resp.writer.write(
                        """
                          <html>
                          <head>
                              <title>Settings</title>
                              <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                          </head>
                          <body>
                          <form action="/userSettings/" method="post">
                              <input type="hidden" name="action" value="save"/>
                              <textarea name="settings" style="width: 100%; height: 100px;">""".trimIndent() + json + """</textarea>
                              <input type="submit" value="Save"/>
                          </form>
                          </body>
                          </html>
                        """.trimIndent()
                    )
                }
            } catch (e: Exception) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR

                resp.writer.write(
                    """
                          <html>
                          <head>
                              <title>Error</title>
                              <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                          </head>
                          <body>
                          <h1>Error</h1>
                          <pre>""".trimIndent() + e.message + """</pre>
                          </body>
                          </html>
                    """.trimIndent()
                )
                resp
            }
        }
    }

    public override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val userinfo = ApplicationServices.authenticationManager.getUser(req.getCookie())
        if (null == userinfo) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
        } else {
            val settings = JsonUtil.fromJson<UserSettings>(req.getParameter("settings"), UserSettings::class.java)
            val prevSettings =
                ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(userinfo)
            val reconstructedApis = settings.apis.mapIndexed { index, apiData ->
                val prevApiData = prevSettings.apis.getOrNull(index)
                ApiData(
                    key = when (apiData.key?.decrypt) {
                        mask -> prevApiData?.key ?: "".encrypt
                        else -> apiData.key
                    },
                    baseUrl = apiData.baseUrl,
                    provider = apiData.provider
                ).validate()
            }.toMutableList()
            val reconstructedSettings = UserSettings(
                apis = reconstructedApis,
                tools = (prevSettings.tools + settings.tools).distinctBy { it.provider?.name }.toMutableList(),
                etc = settings.etc
            )
            ApplicationServices.fileApplicationServices().userSettingsManager.updateUserSettings(
                userinfo,
                reconstructedSettings
            )
            resp.sendRedirect("/")
        }
    }

    companion object
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/WelcomeServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import java.nio.file.NoSuchFileException

open class WelcomeServlet(private val parent: ApplicationDirectory) :
    HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        log.debug("Received GET request for path: ${req?.servletPath}")
        val path = req?.servletPath ?: "/"
        when {
            path == "/" || path == "/index.html" -> {
                log.info("Serving static welcome page for path: $path")
                serveStaticPage(resp)
            }

            path == "/user" -> {
                log.info("Serving user info for path: $path")
                serveUserInfo(req!!, resp!!)
            }

            path == "/apps" -> {
                log.info("Serving app list for path: $path")
                serveAppList(req!!, resp)
            }

            else -> {
                log.info("Serving resource for path: $path")
                serveResource(req, resp, path)
            }
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
        log.debug("Received POST request for URI: ${req?.requestURI}")
        val requestURI = req?.requestURI ?: "/"
        when {
            requestURI.startsWith("/userSettings") -> {
                log.info("Delegating POST request to userSettingsServlet for URI: $requestURI")
                parent.userSettingsServlet.service(req!!, resp!!)
            }

            else -> {
                log.warn("POST request not found, sending 404 for URI: $requestURI")
                resp?.sendError(404)
            }
        }
    }

    private fun serveStaticPage(resp: HttpServletResponse?) {
        log.debug("Starting to serve static welcome page")
        resp?.contentType = "text/html"
        val inputStream = this::class.java.getResourceAsStream("/welcome/welcome.html")
        if (inputStream != null) {
            log.debug("Successfully loaded welcome.html resource")
            inputStream.copyTo(resp?.outputStream!!)
            log.debug("Successfully served static welcome page")
        } else {
            log.error("Failed to load welcome.html resource")
            resp?.sendError(500, "Welcome page not found")
        }
    }

    private fun serveUserInfo(req: HttpServletRequest, resp: HttpServletResponse) {
        log.debug("Starting to serve user info")
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        log.debug("Retrieved user: ${user?.email ?: "anonymous"}")
        val mapper = jacksonObjectMapper()
        resp.contentType = "application/json"
        try {
            mapper.writeValue(resp.outputStream, user)
            log.debug("Successfully served user info for user: ${user?.email ?: "anonymous"}")
        } catch (e: Exception) {
            log.error("Error serving user info for user: ${user?.email ?: "anonymous"}", e)
            resp.sendError(500, "Error retrieving user information")
        }
    }

    private fun serveAppList(req: HttpServletRequest, resp: HttpServletResponse?) {
        log.debug("Starting to serve app list")
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        log.debug("Retrieved user for app list: ${user?.email ?: "anonymous"}")
        log.debug("Total child web apps available: ${parent.childWebApps.size}")
        val authorizedApps = parent.childWebApps.filter {
            val isAuthorized = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
            log.debug("App ${it.server.applicationName} authorization for user ${user?.email ?: "anonymous"}: $isAuthorized")
            isAuthorized
        }.map {
            val canRead = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
            val canWrite = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Write)
            val canWritePublic = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Public)
            log.debug("App ${it.server.applicationName} permissions - Read: $canRead, Write: $canWrite, Public: $canWritePublic")

            mapOf(
                "path" to it.path,
                "thumbnail" to it.thumbnail,
                "applicationName" to it.server.applicationName,
                "javaClass" to it.server.javaClass,
                "canRead" to canRead,
                "canWrite" to canWrite,
                "canWritePublic" to canWritePublic,
            )
        }
        log.info("Serving ${authorizedApps.size} authorized apps for user: ${user?.email ?: "anonymous"}")
        val mapper = jacksonObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        resp?.contentType = "application/json"
        lateinit var valueAsString: String
        try {
            valueAsString = mapper.writeValueAsString(authorizedApps)
            resp?.outputStream?.write(valueAsString.toByteArray())
            log.debug("Successfully served app list")
        } catch (e: Exception) {
            log.error("Error serving app list: $valueAsString", e)
            resp?.sendError(500, "Error retrieving application list")
        }
    }

    private fun serveResource(req: HttpServletRequest?, resp: HttpServletResponse?, requestURI: String) {
        log.debug("Starting to serve resource: $requestURI")
        when {
            requestURI.startsWith("/userInfo") -> {
                log.info("Delegating to userInfoServlet for URI: $requestURI")
                parent.userInfoServlet.service(req, resp!!)
            }

            else -> try {
                resp ?: throw IllegalStateException("Response is null")
                resp.contentType = MimeTypes.getDefaultMimeByExtension(requestURI.split("/").last())
                log.info("Serving resource: $requestURI as ${resp.contentType}")
                val inputStream = parent.welcomeResources.addPath(requestURI)?.inputStream
                if (inputStream != null) {
                    inputStream.copyTo(resp.outputStream!!)
                    log.debug("Successfully served resource: $requestURI")
                } else {
                    log.warn("Resource not found: $requestURI")
                    resp.sendError(404)
                }
            } catch (e: NoSuchFileException) {
                log.warn("Resource not found: $requestURI", e)
                resp?.sendError(404)
            } catch (e: Exception) {
                log.error("Error serving resource: $requestURI", e)
                resp?.sendError(500, "Error serving resource")
            }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(WelcomeServlet::class.java)
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/ZipServlet.kt

```
package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipServlet(val dataStorage: StorageInterface) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val session = Session(req.getParameter("session"))
        val path = req.parameterMap.get("path")?.find { it.isNotBlank() } ?: "/"
        val sessionDir = dataStorage.getSessionDir(
            ApplicationServices.authenticationManager.getUser(req.getCookie()), session
        )
        val file = File(sessionDir, path)
        val zipFile = File.createTempFile("cognotik", ".zip")
        try {
            zipFile.deleteOnExit()
            zipFile.outputStream().use { outputStream ->
                val zip = ZipOutputStream(outputStream)
                write(file, file, zip)
                zip.close()
            }
            resp.contentType = "application/zip"
            resp.status = HttpServletResponse.SC_OK
            resp.outputStream.write(zipFile.readBytes())
        } finally {
            zipFile.delete()
        }
    }

    private fun write(basePath: File, file: File, zip: ZipOutputStream) {
        if (file.isFile) {
            val path = basePath.toURI().relativize(file.toURI()).path
            zip.putNextEntry(ZipEntry(path))
            zip.write(file.readBytes())
            zip.closeEntry()
        } else {
            file.listFiles()?.filter { !it.name.startsWith(".") }
                ?.forEach { write(basePath, it, zip) }
        }
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.webui.servlet` package, summarizing the various servlets and filters that power the Cognotik web interface.

### webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/README.md
```markdown
# WebUI Servlets and Filters

This package contains the server-side components (Servlets and Filters) that handle HTTP requests for the Cognotik web interface. These components manage authentication, session lifecycle, file access, API proxying, and configuration metadata.

## Core Components

### Authentication & User Management
*   **`OAuthGoogle`**: Implements the Google OAuth2 login flow. It handles the redirection to Google and the callback to establish a user session.
*   **`LogoutServlet`**: Terminates the user session and clears authentication cookies.
*   **`UserInfoServlet`**: Provides a JSON endpoint returning the current authenticated user's profile information.
*   **`UserSettingsServlet`**: Manages user-specific settings, including API keys for various providers and enabled tools. It includes logic to mask sensitive keys in the UI.
*   **`SessionIdFilter`**: A security filter that ensures requests to protected resources are authenticated, redirecting to the login page if necessary.

### Session Management
*   **`SessionListServlet`**: Renders an HTML list of all sessions available to the user, including metadata like creation time.
*   **`NewSessionServlet`**: Generates and returns a new unique global session ID.
*   **`DeleteSessionServlet`**: Provides an interface and endpoint for deleting a session and its associated data.
*   **`SessionSettingsServlet`**: Allows viewing and editing session-specific configuration (stored as JSON).
*   **`CancelThreadsServlet`**: Provides a mechanism to forcefully shut down the thread pool associated with a specific session.
*   **`ZipServlet`**: Packages a session's directory into a ZIP file for download.

### API & Proxy Services
*   **`ProxyHttpServlet`**: Acts as a reverse proxy for OpenAI-compatible APIs. It injects user-managed API keys and enforces budget constraints by tracking usage costs.
*   **`ApiKeyServlet`**: Manages internal API keys used for the proxy service, including budget management and an invitation system for sharing keys.
*   **`ApiProviderServlet`**: Lists available and configured AI providers, including details about supported models and capabilities (chat, embeddings).
*   **`UsageServlet`**: Displays a detailed breakdown of token usage and costs per model for a session or user.

### File Serving
*   **`FileServlet`**: A base class for serving files and directory listings. It includes support for:
    *   Large file streaming using memory-mapped buffers.
    *   On-the-fly Markdown rendering to HTML or PDF.
    *   File uploads via drag-and-drop or clipboard paste.
*   **`SessionFileServlet`**: Specialization of `FileServlet` that scopes file access to specific session and data directories.

### Configuration & Metadata
*   **`CognitiveConfigServlet`**: Dynamically generates configuration UI metadata for different "Cognitive Modes" based on Kotlin class properties and annotations.
*   **`TaskConfigServlet`**: Similar to the cognitive config, it provides metadata for configuring various task types and tools.
*   **`AppInfoServlet`**: A generic servlet for providing application-specific metadata as JSON.

### Diagnostics & Specialized Tools
*   **`SessionThreadsServlet`**: Provides a diagnostic view of active threads within a session's pool, including real-time stack traces.
*   **`SymbolGraphServlet`**: An API endpoint for the `SymbolGraphService`, allowing for searching and exploring code symbols, files, and packages.
*   **`WelcomeServlet`**: Serves the main landing page, static resources, and the list of authorized applications available to the user.

### Infrastructure
*   **`CorsFilter`**: Adds Cross-Origin Resource Sharing (CORS) headers to responses to allow the WebUI to interact with the server from different origins.
*   **`OAuthBase`**: An abstract base class for implementing different OAuth providers.

## Implementation Details

*   **Asynchronous Processing**: Many servlets (like `ProxyHttpServlet` and `FileServlet`) use Servlet 3.0+ asynchronous processing to handle long-running requests (like AI generation or large file transfers) without blocking server threads.
*   **JSON Integration**: Uses Jackson and internal `JsonUtil` for seamless serialization of data models.
*   **Security**: Authentication is enforced via `SessionIdFilter`, and authorization checks are performed within individual servlets using the `AuthorizationManager`.
*   **Dynamic UI**: Servlets like `TaskConfigServlet` and `CognitiveConfigServlet` allow the frontend to build complex configuration forms dynamically based on backend code structures.
```

### Summary of Changes
- Created a comprehensive `README.md` in the `com.simiacryptus.cognotik.webui.servlet` package.
- Categorized all 25 provided Kotlin files into logical functional groups (Authentication, Session Management, API/Proxy, etc.).
- Provided brief, descriptive summaries for each servlet and filter based on the source code logic.
- Added a section on implementation details highlighting the use of asynchronous processing, JSON handling, and security patterns.
- Ensured the documentation reflects the specific features found in the code, such as Markdown-to-PDF rendering in `FileServlet` and budget tracking in `ProxyHttpServlet`.
</details>

                - <a href='fileIndex/G-20260120-6kpA/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/README.md</a> Updated

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-6kpA/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/README.md</a> Updated
