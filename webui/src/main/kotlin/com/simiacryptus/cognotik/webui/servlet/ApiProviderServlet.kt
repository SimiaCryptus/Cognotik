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
            if (null == userinfo) {
                resp.status = HttpServletResponse.SC_UNAUTHORIZED
                resp.writer.write(JsonUtil.toJson(mapOf("error" to "Unauthorized")))
                return
            }
            
            val userSettings = ApplicationServices.fileApplicationServices()
                .userSettingsManager.getUserSettings(userinfo)
            
            val providers = mutableListOf<ProviderInfo>()
            
            // Get all registered API providers
            APIProvider.values().forEach { provider ->
                try {
                    // Find matching API configuration for this provider
                    val apiConfig = userSettings.apis.find { 
                        it.provider?.name == provider.name 
                    }
                    
                    if (apiConfig != null && !apiConfig.key.isNullOrEmpty()) {
                        val models = try {
                            provider.getChatModels(
                                key = apiConfig.key ?: "",
                                baseUrl = apiConfig.baseUrl ?: provider.base
                            ).map { model ->
                                ModelInfo(
                                    name = model.modelName!!,
                                    maxTokens = model.maxTotalTokens
                                )
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to fetch models for provider ${provider.name}", e)
                            emptyList()
                        }
                        
                        val supportsEmbedding = try {
                            provider.getEmbeddingClient(
                                key = apiConfig.key ?: "",
                                base = apiConfig.baseUrl ?: provider.base,
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
                                baseUrl = apiConfig.baseUrl ?: provider.base,
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
            
            resp.status = HttpServletResponse.SC_OK
            val acceptHeader = req.getHeader("Accept") ?: ""
            
            if (acceptHeader.contains("application/json")) {
                resp.contentType = "application/json"
                resp.writer.write(JsonUtil.toJson(providers))
            } else {
                resp.contentType = "text/html"
                resp.writer.write(generateHtmlResponse(providers))
            }
            
        } catch (e: Exception) {
            log.error("Error in ApiProviderServlet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write(JsonUtil.toJson(mapOf("error" to e.message)))
        }
    }
    
    private fun generateHtmlResponse(providers: List<ProviderInfo>): String {
        val providersHtml = providers.joinToString("\n") { provider ->
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
            $providersHtml
        </body>
        </html>
        """.trimIndent()
    }
    
    companion object {
        private val log = LoggerFactory.getLogger(ApiProviderServlet::class.java)
    }
}