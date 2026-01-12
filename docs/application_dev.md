This guide outlines how to build AI-driven web applications using the Cognotik framework and how to programmatically launch sessions for those applications (e.g., from an IDE plugin).

The framework relies on a central **Session Proxy** that routes incoming web traffic (identified by a Session ID) to specific **Application Server** instances.

---

## Part 1: Creating an Application

There are two main ways to create an application:
1.  **General Application:** Inherit from `ApplicationServer` for full control (interactive chat, custom flows).
2.  **Single Task Application:** Inherit from `SingleTaskApp` for "fire-and-forget" tasks (e.g., "Refactor this file").

### Option A: The General Application (`ApplicationServer`)

Use this when you need an interactive chat interface or complex state management.

```kotlin
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager

class MyCustomChatApp : ApplicationServer(
    applicationName = "My Custom Assistant",
    path = "/my-custom-app",
    showMenubar = true
) {
    // 1. Define UI behavior
    override val stickyInput = true // Keep input box after sending
    override val inputCnt = 1       // Number of inputs allowed

    // 2. Define Settings (Optional)
    data class MySettings(var prompt: String = "You are a helpful assistant")
    override val settingsClass = MySettings::class.java
    override fun <T : Any> initSettings(session: Session): T? = MySettings() as T

    // 3. Handle New Sessions
    override fun newSession(user: User, session: Session): SocketManager {
        // Initialize storage or logging here if needed
        return object : ApplicationSocketManager(
            session = session,
            owner = user,
            dataStorage = dataStorage,
            applicationClass = this@MyCustomChatApp::class.java
        ) {
            // 4. Handle User Messages
            override fun userMessage(
                session: Session,
                user: User,
                userMessage: String,
                socketManager: ApplicationSocketManager
            ) {
                // Create a UI task bubble
                val task = socketManager.newTask()
                try {
                    task.add("You said: $userMessage")
                    // ... Call LLM here ...
                    task.complete("I processed your message.")
                } catch (e: Exception) {
                    task.error(e)
                }
            }
        }
    }
}
```

### Option B: The Single Task Application (`SingleTaskApp`)

Use this for specific, automated workflows (like the `FileModificationTask` in your provided code). This pre-configures the agent to run immediately upon session start.

```kotlin
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel

class MyRefactoringApp(
    taskConfig: TaskExecutionConfig,
    val modelProvider: () -> ApiChatModel
) : SingleTaskApp(
    applicationName = "Refactoring Tool",
    path = "/refactor",
    showMenubar = false,
    taskType = TaskType.Refactoring, // Assuming this type exists
    taskConfig = taskConfig,
    instanceFn = { model -> model.instance()!! }
) {
    // Provide the LLM instance to be used
    override fun instance(model: ApiChatModel) = modelProvider().instance()!!

    // Optional: Handle completion
    override fun onTaskComplete(result: String, task: SessionTask) {
        super.onTaskComplete(result, task)
        // e.g., Refresh IDE file system
    }
}
```

---

## Part 2: Launching an Application Session

Once you have an application class, you don't "start" the app server for every user. Instead, you create a **Session**, instantiate your App for that session, and register it with the global `SessionProxyServer`.

This is typically done in an Action handler (like `FileModificationTaskAction.kt`).

### Step 1: Generate a Session ID and Setup Storage
Generate a unique ID and define where the data for this session lives.

```kotlin
val session = Session.newGlobalID()
val projectRoot = File("/path/to/project")

// Map the session to a physical directory on disk
DataStorage.sessionPaths[session] = projectRoot
```

### Step 2: Configure and Instantiate the App
Create an instance of your application class. If using `SingleTaskApp`, you must write the configuration to the settings file so the app knows what to do when it wakes up.

```kotlin
// 1. Create the App Instance
val app = MyRefactoringApp(
    taskConfig = myTaskConfig,
    modelProvider = { mySelectedModel }
)

// 2. Persist Configuration (Crucial for SingleTaskApp)
// The app reads 'OrchestrationConfig' from disk to start the task.
val config = OrchestrationConfig(
    sessionId = session.sessionId,
    workingDir = projectRoot.absolutePath,
    // ... other config ...
)

// Write config to the standard settings location
app.getSettingsFile(session, UserSettingsManager.defaultUser)
   .writeText(config.toJson())
```

### Step 3: Register with SessionProxyServer
This is the most critical step. The `SessionProxyServer` acts as the router. You must tell it: "When a browser requests `/#<session_id>`, use *this* app instance."

```kotlin
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.AppInfoData

// 1. Register the App instance for this specific session
SessionProxyServer.chats[session] = app

// 2. Register App Metadata (Used by the frontend to render the UI shell)
ApplicationServer.appInfoMap[session] = AppInfoData(
    applicationName = "Refactoring Tool",
    inputCnt = 0,           // 0 for read-only/task view
    stickyInput = false,
    showMenubar = false
)

// 3. Set a human-readable name for the session history
SessionProxyServer.metadataStorage.setSessionName(
    null, // user (null = default)
    session,
    "Refactor Task @ ${SimpleDateFormat("HH:mm").format(Date())}"
)
```

### Step 4: Open the Browser
Construct the URL pointing to the local server with the session hash fragment.

```kotlin
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.util.BrowseUtil

Thread {
    // Give the server a moment to register the map entry if needed
    Thread.sleep(500)

    try {
        // Resolve the base URI and append the session ID fragment
        val uri = CognotikAppServer.getServer().server.uri.resolve("/#$session")

        // Open system browser
        BrowseUtil.browse(uri)
    } catch (e: Throwable) {
        // Handle error
    }
}.start()
```

---

## Summary of Architecture

1.  **`SessionProxyServer`**: The singleton Jetty handler. It holds a map `chats: MutableMap<Session, ChatServer>`.
2.  **`ApplicationServer`**: Your custom logic. It manages the `SocketManager`.
3.  **`SocketManager`**: The bridge between Kotlin code and the HTML/JS frontend. It allows you to push updates (`newTask`, `add`, `complete`) to the browser.
4.  **`DataStorage`**: Manages file persistence. `DataStorage.sessionPaths` maps a logical session ID to a physical folder on your hard drive.

### Checklist for Developers
1.  [ ] Define your App class (extending `ApplicationServer` or `SingleTaskApp`).
2.  [ ] Create a `Session` object.
3.  [ ] Set `DataStorage.sessionPaths[session]`.
4.  [ ] Instantiate your App.
5.  [ ] **Register** the app in `SessionProxyServer.chats[session]`.
6.  [ ] Open `http://localhost:8080/#<session_id>`.
