package com.simiacryptus.cognotik.webui.servlet

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.authenticate
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
    val maxTokens: Int? = null
  )

  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val user = authenticate(request, response) ?: return
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
                    ${model.maxTokens?.let { " (max tokens: $it)" } ?: ""}
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
              provider.getChatModels(
                key = apiConfig.key,
                baseUrl = apiConfig.apiBase
              )?.filter { !it.deprecated } ?: emptyList()
            } catch (e: Exception) {
              log.warn("Failed to fetch models for provider ${provider.name}", e)
              emptyList()
            }
          }
        } catch (e: Exception) {
          log.error("Error processing provider ${provider.name}", e)
        }
      }
      return models.associateBy { it.name }
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
              provider.getChatModels(
                key = apiConfig.key,
                baseUrl = apiConfig.apiBase
              )?.filter { !it.deprecated } ?: emptyList()
            } catch (e: Exception) {
              log.warn("Failed to fetch models for provider ${provider.name}", e)
              emptyList()
            }

            providers.add(
              ProviderInfo(
                name = provider.name,
                baseUrl = apiConfig.apiBase
                  ?: throw IllegalArgumentException("No API found for provider: ${apiConfig.provider?.name}"),
                models = models.map { model ->
                  ModelInfo(
                    name = model.modelId,
                    maxTokens = model.maxTotalTokens
                  )
                },
                supportsChat = models.isNotEmpty(),
                supportsEmbedding = try {
                  provider.getEmbeddingClient(
                    key = apiConfig.key,
                    base = apiConfig.apiBase
                      ?: throw IllegalArgumentException("No API found for provider: ${apiConfig.provider?.name}"),
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