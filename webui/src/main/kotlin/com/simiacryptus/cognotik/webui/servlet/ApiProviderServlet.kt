package com.simiacryptus.cognotik.webui.servlet

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

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
    val baseUrl: String?,
    val models: List<ModelInfo>,
    val supportsChat: Boolean,
    val supportsEmbedding: Boolean
  )

  data class ModelInfo(
    val name: String,
    val inputModalities: Set<String> = emptySet(),
    val outputModalities: Set<String> = emptySet()
  )

  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val user =
        UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
      val userSettings = user.userSettings()
      val providers = userSettings.providerInfos()
      val availableProviders = userSettings.getAvailableProviders()

      response.status = HttpServletResponse.SC_OK
      val acceptHeader = request.getHeader("Accept") ?: ""

      val formatParam = request.getParameter("format")
      val apiResponse = ApiProvidersResponse(
        configuredProviders = providers,
        availableProviders = availableProviders
      )
      if (formatParam == "json" || acceptHeader.contains("application/json") || acceptHeader.contains("text/json")) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(JsonUtil.toJson(apiResponse))
        response.writer.flush()
      } else {
        response.contentType = "text/html"
        response.characterEncoding = "UTF-8"
        response.writer.write(generateHtmlResponse(apiResponse))
        response.writer.flush()
      }

    } catch (e: Exception) {
      log.error("Error in ApiProviderServlet", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.contentType = "application/json"
      response.characterEncoding = "UTF-8"
      response.writer.write(JsonUtil.toJson(mapOf("error" to e.message)))
      response.writer.flush()
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
                </li>
                """.trimIndent()
      }

      """
            <div class="provider">
                <h2>${provider.name}</h2>
                <p><strong>Base URL:</strong> ${provider.baseUrl ?: "N/A"}</p>
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
            <script src="/modules/theme.js"></script>
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
                .theme-controls {
                    position: fixed;
                    top: 10px;
                    right: 10px;
                    background: white;
                    padding: 8px 12px;
                    border-radius: 6px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    z-index: 1000;
                }
                .theme-controls label {
                    margin-right: 6px;
                    font-size: 0.9em;
                }
            </style>
        </head>
        <body>
            <div class="theme-controls">
                <label for="theme-selector">Theme:</label>
                <select id="theme-selector">
                    <option value="auto">Auto</option>
                    <option value="light">Light</option>
                    <option value="dark">Dark</option>
                </select>
            </div>
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
            $providersHtml
            <script>
                (function() {
                    if (typeof ThemeManager !== 'undefined') {
                        ThemeManager.init();
                        var sel = document.getElementById('theme-selector');
                        if (sel) ThemeManager.bindSelector(sel);
                    }
                })();
            </script>
        </body>
        </html>
        """.trimIndent()
  }

  companion object {
    private val log = LoggerFactory.getLogger(ApiProviderServlet::class.java)

    @Suppress("LombokKotlinCompilerPlugin")
    fun UserSettings.models(): Map<String, ChatModel> {
      val models = mutableListOf<ChatModel>()

      // Get all registered API providers
      APIProvider.values().forEach { provider ->
        try {
          // Find matching API configuration for this provider
          val apiConfig = apis.find {
            it.provider?.name == provider.name
          }

          if (apiConfig != null && !apiConfig.key?.decrypt.isNullOrEmpty()) {

            models += try {
             (provider.getChatModels(
               key = apiConfig.key!!,
               baseUrl = apiConfig.apiBase
             ) ?: emptyList())
               .filter { !it.deprecated }
               /* Multiple enum aliases can share the same modelId - collapse them */
               .distinctBy { it.modelId }
            } catch (e: Exception) {
              log.warn("Failed to fetch models for provider ${provider.name}", e)
              emptyList()
            }
          }
        } catch (e: Exception) {
          log.error("Error processing provider ${provider.name}", e)
        }
      }
     return models.distinctBy { it.modelId }.associateBy { it.name }
    }

    fun UserSettings.providerInfos(): List<ProviderInfo> {
      val providers = mutableListOf<ProviderInfo>()

      // Get all registered API providers
      APIProvider.values().forEach { provider ->
        try {
          // Find matching API configuration for this provider
          val apiConfig = apis.find {
            it.provider?.name == provider.name
          }

          if (apiConfig != null && !apiConfig.key?.decrypt.isNullOrEmpty()) {
            val models = try {
             (provider.getChatModels(
               key = apiConfig.key!!,
               baseUrl = apiConfig.apiBase
             ) ?: emptyList())
               .filter { !it.deprecated }
               /*
                * A provider can expose several enum constants that resolve to the
                * same wire-level modelId (aliases / dated snapshots / "latest").
                * Keep only the first occurrence of each modelId.
                */
               .distinctBy { it.modelId }
            } catch (e: Exception) {
              log.warn("Failed to fetch models for provider ${provider.name}", e)
              emptyList()
            }

            providers.add(
              ProviderInfo(
                name = provider.name,
                baseUrl = apiConfig.apiBase,
                models = models.map { model ->
                  ModelInfo(
                    name = model.modelId,
                    inputModalities = model.inputModalities.map { it.name }.toSet(),
                    outputModalities = model.outputModalities.map { it.name }.toSet(),
                  )
               }.distinctBy { it.name }.sortedBy { it.name },
                supportsChat = models.isNotEmpty(),
                supportsEmbedding = try {
                  provider.getEmbeddingClient(
                    key = apiConfig.key!!,
                    base = apiConfig.apiBase,
                    workPool = MoreExecutors.newDirectExecutorService(),
                    scheduledPool = MoreExecutors.listeningDecorator(
                      Executors.newScheduledThreadPool(1)
                    )
                  )
                  true
                } catch (e: UnsupportedOperationException) {
                  false
                } catch (e: Exception) {
                  log.warn("Error checking embedding support for ${provider.name}", e)
                  false
                }
              )
            )
          }
        } catch (e: Exception) {
          log.error("Error processing provider ${provider.name}", e)
        }
      }
      return providers
    }
    fun User.userSettings(): UserSettings = ApplicationServices.fileApplicationServices()
      .userSettingsManager.getUserSettings(this)
    fun UserSettings.getAvailableProviders(): List<AvailableProviderInfo> =
      APIProvider.values().map { provider ->
        val isConfigured = apis.any {
          it.provider?.name == provider.name && !it.key?.decrypt.isNullOrEmpty()
        }
        AvailableProviderInfo(
          id = provider.name,
          name = provider.name,
          baseUrl = provider.base,
          isConfigured = isConfigured
        )
      }
  }
}