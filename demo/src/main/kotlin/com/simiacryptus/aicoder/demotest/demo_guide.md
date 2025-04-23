# AI Coder Demo Creation Guide

## General Demo Principles

### 1. Structure

- Start with a clear introduction of the feature
- Demonstrate practical use cases
- Show both basic and advanced functionality
- End with clear results and benefits

### 2. Narration Best Practices

```kotlin
// Bad
speak("Now we will do something.")

// Good
speak("Let's explore how the Smart Paste feature automatically converts code between different languages.")
```

### 3. Pacing

```kotlin
// Add appropriate pauses for comprehension
step("Demonstrate feature") {
  speak("First, we'll select our target code.")
  sleep(2000)  // Give viewers time to process
  performAction()
  speak("Notice how the AI analyzes the context before making changes.")
  sleep(3000)  // Longer pause for complex operations
}
```

## Feature-Specific Guidelines

### 1. Code Chat Features

```kotlin
// Example narration flow for Code Chat
speak("Welcome to the Code Chat feature demonstration.")
speak("This powerful tool allows real-time AI assistance while coding.")

step("Open code file") {
  speak("Let's start by opening a complex code file that needs analysis.")
  // ... file opening code ...
}

step("Initialize chat") {
  speak("We can now ask the AI to analyze this code and suggest improvements.")
  // ... chat initialization ...
}
```

### 2. Editor Features

```kotlin
// Smart Paste demonstration
step("Demonstrate Smart Paste") {
  speak("Smart Paste intelligently converts code between languages.")
  speak("Let's convert this JavaScript function to Kotlin.")

  // Show original code
  setClipboardContent(
    """
        function calculateTotal(items) {
            return items.reduce((sum, item) => sum + item.price, 0);
        }
    """.trimIndent()
  )

  // Demonstrate paste
  performSmartPaste()

  speak("Notice how the AI maintains functionality while adopting Kotlin idioms.")
}
```

### 3. Documentation Generation

```kotlin
step("Generate Documentation") {
  speak("The AI can analyze code and generate comprehensive documentation.")
  speak("Watch as it creates detailed documentation including:")
  speak("- Function descriptions")
  speak("- Parameter explanations")
  speak("- Return value details")
  speak("- Usage examples")

  // ... documentation generation code ...

  speak("The generated documentation follows project conventions and best practices.")
}
```

## Error Handling

```kotlin
try {
  step("Feature demonstration") {
    // ... demo code ...
  }
} catch (e: Exception) {
  log.error("Demo failed: ${e.message}")
  speak("We've encountered an issue. In a real scenario, you would...")
  // Explain error recovery
}
```

## Visual Presentation

### 1. Splash Screen Configuration

```kotlin
splashScreenConfig = SplashScreenConfig(
  titleText = "Feature Name Demo",
  containerStyle = """
        background: linear-gradient(145deg, #1E1E1E, #2D2D2D);
        padding: 40px 60px;
        border-radius: 15px;
        box-shadow: 0 20px 40px rgba(0,0,0,0.3);
        animation: slideIn 1.5s ease-out;
    """.trimIndent()
)
```

### 2. Progress Indication

```kotlin
step("Long-running operation") {
  speak("Starting the analysis process...")
  showProgressIndicator()
  // ... operation code ...
  hideProgressIndicator()
  speak("Analysis complete. Let's review the results.")
}
```

## Best Practices for Demo Recording

1. **Preparation**
   ```kotlin
   @Test
   fun demoFeature() {
       // Clear environment
       cleanupPreviousState()
       // Set up demo files
       prepareTestFiles()
       // Start recording
       startRecording()
   }
   ```

2. **Timing**
   ```kotlin
   // Allow time for visual transitions
   sleep(1000) // Short pause
   sleep(3000) // Longer pause for complex operations
   ```

3. **Error Recovery**
   ```kotlin
   step("Critical operation") {
       var attempts = 0
       while (attempts < 3) {
           try {
               performOperation()
               break
           } catch (e: Exception) {
               attempts++
               speak("Retrying operation...")
               sleep(1000)
           }
       }
   }
   ```

## Documentation

```kotlin
/**
 * Demonstrates the [FeatureName] functionality.
 *
 * Key points covered:
 * - Basic usage
 * - Advanced scenarios
 * - Error handling
 * - Best practices
 *
 * Prerequisites:
 * - Clean project state
 * - Required files present
 * - Proper API configuration
 */
@Test
fun demonstrateFeature() {
    // Demo implementation
}
```