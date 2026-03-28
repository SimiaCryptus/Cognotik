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
import com.simiacryptus.cognotik.webui.application.authenticate
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

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {


    response.contentType = "text/html"
    val user = authenticate(request, response) ?: return response.sendError(
      HttpServletResponse.SC_UNAUTHORIZED
    )
    val action = request.getParameter("action")
    val apiKey = request.getParameter("apiKey")
    val provider = request.getParameter("provider")

    when (action.lowercase(Locale.ROOT)) {
      "edit" -> {
        val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey && it.owner == user.email }
        if (record != null) {
          serveEditPage(request, response, record)
        } else {
          response.writer.write("API Key record not found")
        }
      }

      "delete" -> {

        val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey && it.owner == user.email }
        if (record != null) {
          apiKeyRecords.remove(record)
          saveRecords()
          response.writer.write("API Key record deleted")
        } else {
          response.writer.write("API Key record not found")
        }
      }

      "create" -> {
        val userSettings = fileApplicationServices().userSettingsManager.getUserSettings(user)
        serveEditPage(
          request,
          response,
          ApiKeyRecord(
            owner = user.email,
            apiKey = UUID.randomUUID().toString().encrypt,
            mappedKey = userSettings.apis.firstOrNull { it.provider == APIProvider.valueOf(provider) }?.key
              ?: "".encrypt,
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

        serveInviteConfirmationPage(response, record, user)
      }

      else -> {
        response.writer.write(indexPage(request, response))
      }
    }

  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val action = request.getParameter("action")
    val apiKey = request.getParameter("apiKey")
    val mappedKey = request.getParameter("mappedKey")
    val budget = request.getParameter("budget")?.toDoubleOrNull()
    val comment = request.getParameter("comment")

    val welcomeMessage = request.getParameter("welcomeMessage")
    val user = authenticate(request, response)
    val record = apiKeyRecords.find { it.apiKey.decrypt == apiKey }

    if (action == "acceptInvite") {
      if (apiKey.isNullOrEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "API Key is missing")
      } else if (user == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found")
      } else if (record == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid API Key or User not found")
      } else {
        response.sendRedirect("/")
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
      response.sendRedirect("?action=edit&apiKey=$apiKey&editSuccess=true")
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
      response.sendRedirect(
        "?action=edit&apiKey=${
          URLEncoder.encode(
            apiKey,
            "UTF-8"
          )
        }&creationSuccess=true"
      )

    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input")
    }
  }

  private fun indexPage(request: HttpServletRequest, response: HttpServletResponse): String {
    val user = authenticate(request, response) ?: return ""
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

  private fun serveEditPage(request: HttpServletRequest, response: HttpServletResponse, record: ApiKeyRecord) {
    val userinfo = authenticate(request, response)
    val usageSummary: Map<String, ModelSchema.Usage> =
      ApplicationServices.fileApplicationServices().usageManager.getUserUsageSummary(user = userinfo!!)

    response.writer.write(
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