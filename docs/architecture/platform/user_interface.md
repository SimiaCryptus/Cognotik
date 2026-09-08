---
documents:
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SessionTask.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SocketManager.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/ui/*
specifies: ../site/cognotik.com/session-task-ui.html
---

This guide provides a comprehensive overview of how to manage User Interfaces within the Cognotik framework. The UI
system is **server-driven**, meaning you write Kotlin code to generate HTML, which is pushed to the client via
WebSockets.

The core philosophy is **Task-Based UI**: The interface is built around `SessionTask` objects, which represent a
specific, addressable block in the UI (usually a `div`) where content is appended or updated.

---

## 1. Core Concepts

### SessionTask

The `SessionTask` is your primary canvas. It manages a buffer of HTML content and a unique `messageID`.

* **Lifecycle:** A task starts with a "Spinner" (loading state). When you finish processing, you call `complete()` to
  remove the spinner.
* **Output:** You can stream text, HTML, or specific UI components into a task. Content is appended sequentially.
* **Nesting:** Tasks can contain other tasks (via `newTask`), allowing for complex, nested layouts.
* **Placeholders:** Every task has a `placeholder` property (e.g., `<div message-id="abc"></div>`). This is used to
  inject the task's future content into a parent task or a specific HTML structure.

### SocketManager

The `SocketManager` handles the connection between the server and the browser. You rarely instantiate this directly;
instead, you access it via `task.ui`. It is used to create new tasks, handle file paths, and manage interactivity.
It also provides access to session-specific thread pools via `task.pool` and `task.ui.scheduledThreadPoolExecutor`.

### Creating Sub-Tasks

You can create nested tasks to organize output. There are two main ways to do this:

1. **Inline Sub-Task (`task.newTask()`):**
   Calling `task.newTask()` creates a new task and immediately appends its placeholder to the current task's output.
   **Crucially, this reserves the display order.** You can continue adding content to the parent `task`, but any content
   added to the sub-task will appear in the reserved spot (above the subsequent parent content). This is useful for
   parallel processing or updating a specific section of the UI while the main thread continues.

2. **Manual Placement (Inner Tasks):**
   Calling `task.newTask(false)` creates a "detached" task. It is **not** rendered automatically. Instead, you must
   use the task's `placeholder` property (a string containing a `div` with the specific `messageID`) to place it within
   the UI.
   This is essential for complex layouts, such as putting a streaming task inside a table cell or a specific HTML
   structure.

```kotlin
// 1. Inline: Reserves a spot in the current output stream
val subTask = task.newTask()
// We can add to the parent task immediately
task.add("This appears BELOW the subTask")

// Later, we can update the subTask, and it appears ABOVE the text we just added
subTask.add("I am a sub-task, appearing in my reserved spot")
subTask.complete()

// 2. Manual: Must be placed explicitly
val innerTask = task.newTask(false)
// Inject the placeholder into a custom layout
task.add(innerTask.placeholder)
// Now content added to innerTask appears inside the .custom-box div
innerTask.add("I am inside the box")
```

**Other Options:**

```kotlin
// Create a cancelable task (renders with a close button)
// If the user clicks 'X', the task element is removed from the DOM.
val closableTask = task.newTask(root = true, cancelable = true)
```

---

## 2. Basic Content Rendering

All rendering methods belong to the `SessionTask` class.

### Text and HTML

```kotlin
fun myTask(task: SessionTask) {
  // Add a simple message (wrapped in a div). Supports markdown.
  task.add("Hello, **World**!", markdown = true)

  // Add a header (H1 - H6)
  task.header("Analysis Results", level = 2)

  // Add raw HTML with custom classes
  task.add("<b>Bold Text</b>", additionalClasses = "text-primary")

  // Echo a user-style message (right-aligned usually)
  task.echo("This looks like a user prompt")

  // Mark the task as finished (removes the loading spinner)
  task.complete()
}
// Add a dismissible message (has a close button)
task.hideable("<b>Note:</b> Click the X to remove me.")

// Add verbose output (rendered in a <pre> tag, often used for debug info)
// This is hidden by default via CSS but can be made visible.
task.verbose("Detailed debug info...")
```

### Raw HTML

If you need to append raw HTML without wrapping it in a specific tag (like `div` or `pre`), use `append`.

```kotlin
task.append("<span>Raw Content</span>", showSpinner = true)
```

### CSS Styling

Most rendering methods (like `add`, `header`, `hideable`) accept an `additionalClasses` parameter. This allows you to
inject CSS class names into the container element to style the output.

```kotlin
// Renders: <div class="response-message alert alert-warning">Warning!</div>
task.add("Warning!", additionalClasses = "alert alert-warning")
```

### Dynamic Updates (Updatable Buffers)

Methods like `add()`, `append()`, and `hideable()` return a `StringBuilder` object. This object is a direct reference to
the content stored in the task's memory. You can modify this buffer and call `task.update()` to refresh the UI in
real-time without appending new elements.

```kotlin
// 1. Add a message and keep the reference to the buffer
val statusBuffer = task.add("Starting process...")
// 2. Modify the buffer in a loop
for (i in 1..5) {
  Thread.sleep(500)
  // Clear and update text
  statusBuffer?.setLength(0)
  statusBuffer?.append("Processing step $i/5...")
  // 3. Push changes to the client
  task.update()
}
// Finalize
statusBuffer?.setLength(0)
statusBuffer?.append("<strong>Done!</strong>")
task.update()
task.complete()
```

### Expandable Content

Useful for hiding verbose logs or large context data.

```kotlin
// Collapsed by default
task.expandable("Debug Logs", "<pre>Log content...</pre>")

// Expanded by default
task.expanded("Executive Summary", "<p>The result is positive.</p>")
```

### Error Handling

Display stack traces or error messages gracefully.

```kotlin
try {
  // risky code
} catch (e: Exception) {
  // Renders a red error box with expandable stack trace.
  // Handles specific types like ValidatedObject.ValidationError or FailedToImplementException specially.
  task.error(e) 
}
```

### Images

You can render `BufferedImage` objects directly. The system handles saving the file and generating the `<img>` tag.

```kotlin
val myImage: BufferedImage = ...
task.image(myImage)
```

### Markdown and Mermaid

Cognotik includes utilities to render Markdown and Mermaid diagrams automatically.

```kotlin
import com.simiacryptus.cognotik.util.MarkdownUtil
val rawMarkdown = """
# Title
* List item
"""
// Renders Markdown to HTML. If Mermaid code blocks are found, 
// they are rendered to SVG (requires Mermaid CLI installed).
val html = MarkdownUtil.renderMarkdown(rawMarkdown, ui = task.ui)
task.add(html)
```

---

## 3. Interactivity

Cognotik allows you to bind Kotlin closures to HTML interactions.

### Buttons and Links (`hrefLink`)

Instead of navigating to a URL, links trigger server-side code.

```kotlin
// Creates an <a> tag. When clicked, the lambda executes.
// You can optionally specify a CSS class and an ID.
val linkHtml = task.hrefLink("Click Me") {
  log.info("Link was clicked!")
  // You can trigger new UI updates here
}
task.add("Please $linkHtml to continue.")
```

### Text Input

To get text from the user, use the `SocketManager`.

```kotlin
val inputHtml = task.textInput { userResponse: String ->
  task.add("You typed: $userResponse")
}
task.add(inputHtml)
```

**Note:** The `SocketManager` handles the routing of these events. When a button is clicked, the ID is sent back to the
server, which looks up the registered lambda in `linkTriggers` or `txtTriggers` and executes it.

---

## 4. Layout Components: TabbedDisplay

The `TabbedDisplay` class allows you to organize content into switchable tabs. This is dynamic; you can add or update
tabs programmatically after rendering.

### Basic Usage

```kotlin
val tabs = TabbedDisplay(task)

// Add a tab
tabs["Summary"] = "This is the summary content."

// Add another tab
tabs["Details"] = "<ul><li>Detail 1</li><li>Detail 2</li></ul>"

// Initialize with options
val tabs = TabbedDisplay(
  task = task,
  closable = false, // Disable close buttons
  additionalClasses = "my-custom-tabs"
)

// You must call update() to refresh the UI after modifying tabs manually
// (Note: the operator set[] calls update() automatically)
```

### Dynamic Updates

You can update the content of an existing tab by assigning to the same key.

```kotlin
// Overwrites the "Summary" tab
tabs["Summary"] = "Updated summary content."
```

### Deleting Tabs

```kotlin
tabs.delete("Details")
// Clearing all tabs
tabs.clear()
```

### Streaming Content into Tabs

A powerful pattern in Cognotik is embedding a live, streaming `SessionTask` inside a tab.

While you can manually create a detached task (`newTask(false)`) and assign its `placeholder` to a tab, the
`TabbedDisplay` class provides a helper method `newTask(label)` to do this automatically. **This is the preferred
method.**

```kotlin
val tabs = TabbedDisplay(task)

// Creates a new task, adds a tab named "Live Progress", 
// and places the task's placeholder inside it.
val workerTask = tabs.newTask("Live Progress")

// Writing to workerTask updates the content *inside* the tab
workerTask.add("Step 1 complete...")
workerTask.add("Step 2 complete...")
workerTask.complete()
```

---

## 5. Advanced Workflows

Cognotik provides specialized classes for AI-driven workflows.

### Retryable

`Retryable` extends `TabbedDisplay`. It is designed for operations that might fail or produce poor results (like LLM
generation) and need to be re-run. It automatically adds a "Recycle" (♻) button.

```kotlin
val retryable = Retryable(task) { buffer ->
    // This code runs when initialized and every time the recycle button is clicked.
    // Each retry creates a new tab.
    val result = performExpensiveOperation()
    result // Return the HTML/content to display in the tab
}
```

### Discussable (Human-in-the-Loop)

`Discussable` is a powerful component for the **Generate -> Review -> Revise** loop. It blocks execution until the user
accepts the result.

**Flow:**

1. **Initial Response:** Generates content based on input.
2. **Feedback Form:** Displays the content with a chat box and an "Accept" button.
3. **Revision:** If the user types in the chat box, the `reviseResponse` function is called to generate a new version in
   a new tab.
4. **Acceptance:** When "Accept" is clicked, the function returns the final object.

```kotlin
import com.simiacryptus.cognotik.models.ModelSchema.Role

val finalResult = Discussable(
  task = task,
  heading = "Drafting Email",
  userMessage = { "Draft an email to the team" },
  initialResponse = { prompt ->
    MyObject(llm.generate(prompt))
  },
  outputFn = { design ->
    // Renders the object to HTML for the user to see
    design.toHtml()
  },
  reviseResponse = { history ->
    // history is List<Pair<String, Role>> (User feedback + Assistant responses)
    llm.chat(history)
  }
).call() // Blocks here until user clicks "Accept"

task.add("Final accepted email: $finalResult")
```

---

## 6. File Management

You can save files to the session directory and generate links to them.

### Saving Files

```kotlin
val data = "some content".toByteArray()
// Saves to session_dir/reports/data.txt and returns a relative URL
val fileUrl = task.saveFile("reports/data.txt", data)

task.add("Download report: <a href='$fileUrl'>Click Here</a>")
```

### Creating Log Streams

For debugging, you can create a live-updating log file that is linked in the UI.

```kotlin
val logStream = task.newLogStream("API Debug Log")
logStream.write("Starting process...\n".toByteArray())
// This creates a link in the UI to a .html file viewing the logs
logStream.close()
```

---

## 7. Utilities & Patterns

### Display Map in Tabs

Quickly render a `Map<String, String>` as a tabbed view.

```kotlin
val data = mapOf("File A" to "Content A", "File B" to "Content B")
val html = AgentPatterns.displayMapInTabs(data)
task.add(html)
```

### File Patching & Diffing

If your agent generates code blocks or diffs, you can instrument the response to include "Save" or "Apply Diff" buttons
using `AddApplyFileDiffLinks`. This parses the text for headers (e.g., `### path/to/file.kt`) and code blocks.

```kotlin
val response = llm.generate(prompt)
// Automatically adds "Save" or "Apply" buttons to code blocks with file headers
val instrumentedHtml = AddApplyFileDiffLinks.instrumentFileDiffs(
  self = task.ui,
  root = workingDirectory, // Path object
  response = response,
  handle = { updatedFiles -> /* callback when files are saved/patched */ },
  shouldAutoApply = { path -> false }, // Optional: Predicate to auto-apply changes
  processor = patchProcessor // PatchProcessor instance
)
task.add(instrumentedHtml)
```

### Sub-Sessions

You can spawn independent sessions (e.g., for sub-agents) and link to them.

```kotlin
// Creates a new session and returns a task for it.
// Adds a link to the new session in the current task.
val subTask = task.linkedTask("Open Analysis Agent")

// Content added to subTask appears in the new session/window
subTask.add("Welcome to the sub-agent.")
```

### Linking to Sessions

You can generate a link to the current session using `linkToSession`.

```kotlin
val link = task.linkToSession("Open this session in new tab")
task.add(link)
```

---

## 8. Best Practices

1. **Thread Safety:**

* `SessionTask` methods are generally safe to call from background threads.
* When using `TabbedDisplay`, the `container` updates are synchronized, but if you are doing complex logic involving
  multiple UI updates, ensure you aren't blocking the main UI thread (though Cognotik handles most of this via`pool`).

2. **Completing Tasks:**

* Always call `task.complete()` when a unit of work is done. If you don't, the spinner will spin forever, making the
  UI look unresponsive.

3. **IDs and State:**

* The system relies on `UUID`s and `messageID`s to find DOM elements. Avoid manipulating the DOM manually via raw
  JavaScript injection unless necessary; rely on `task.add` and `TabbedDisplay.update`.
* `SocketManager` maintains a version history of messages to optimize bandwidth, sending only updates when content
  changes.

4. **Blocking vs Non-Blocking:**

* `Discussable` is **blocking**. Do not call it on the main server thread if you are handling high throughput
  synchronously (though usually, you are running inside a `SessionTask` thread pool).
* `Retryable` submits work to a thread pool automatically.
* You can access the session's thread pool via `task.pool` to offload heavy computations.
* For delayed or periodic execution, use `task.ui.scheduledThreadPoolExecutor`.

5. **Security:**

* `SocketManager` checks `ApplicationServices.authorizationManager` before allowing writes or reads. Ensure your
  `AuthorizationInterface` is configured correctly.