Here's a comprehensive best practices guide for developing new task types in this system:

# Best Practices Guide for Developing New Task Types

## 1. Task Structure and Inheritance

### Base Classes
- Extend either `AbstractTask` or one of its specialized subclasses like `AbstractFileTask` or `AbstractAnalysisTask`
- Use `AbstractFileTask` for tasks that primarily work with files
- Use `AbstractAnalysisTask` for tasks that analyze code/content and provide recommendations

```kotlin
class NewTask(
    planSettings: PlanSettings,
    planTask: NewTaskConfigData?
) : AbstractFileTask<NewTask.NewTaskConfigData>(planSettings, planTask) {
    // Implementation
}
```

### Configuration Classes
- Create a nested `ConfigData` class extending appropriate base config class
- Use descriptive `@Description` annotations for all configuration fields
- Group related configuration parameters logically
- Provide sensible defaults where appropriate

```kotlin
class NewTaskConfigData(
    @Description("Clear description of what this parameter does")
    val parameterName: Type = defaultValue,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    input_files: List<String>? = null,
    output_files: List<String>? = null,
    state: TaskState? = null
) : FileTaskConfigBase(
    task_type = TaskType.NewTask.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    input_files = input_files,
    output_files = output_files,
    state = state
)
```

## 2. Task Registration

### TaskType Registration
- Add new task type to `TaskType` companion object
- Provide clear description and detailed tooltipHtml
- Register constructor in initialization block
- Define appropriate task settings class

```kotlin
val NewTask = TaskType(
    "NewTask",
    NewTaskConfigData::class.java,
    TaskSettingsBase::class.java,
    "Clear description of task purpose",
    """
    Detailed HTML tooltip with:
    <ul>
        <li>Key features</li>
        <li>Use cases</li>
        <li>Important considerations</li>
    </ul>
    """
)

init {
    registerConstructor(NewTask) { settings, task -> NewTask(settings, task) }
}
```

## 3. Implementation Best Practices

### Prompt Design
- Provide clear, detailed prompts for AI interactions
- Include examples and formatting requirements
- Specify input/output expectations
- Document any special considerations

```kotlin
override val actorPrompt = """
    Clear instructions for the AI about:
    1. What needs to be analyzed/generated
    2. Expected format of response
    3. Important considerations
    4. Examples if helpful

    Format requirements:
    - Use specific markdown formatting
    - Include necessary headers
    - Show diffs in specific way
""".trimIndent()
```

### Error Handling
- Validate configuration in a dedicated method
- Use descriptive error messages
- Handle edge cases gracefully
- Log errors appropriately

```kotlin
protected fun validateConfig() {
    requireNotNull(taskConfig) { "Task configuration is required" }
    require(!taskConfig.input_files.isNullOrEmpty()) { "At least one input file must be specified" }
    // Additional validation
}
```

### Resource Management
- Clean up resources in finally blocks
- Use appropriate timeouts
- Handle concurrent operations safely
- Consider memory usage for large operations

## 4. Output Formatting

### Consistent Formatting
- Use markdown for structured output
- Include clear section headers
- Format code blocks appropriately
- Use diffs for code changes

```kotlin
private fun formatResults(results: List<Result>): String = buildString {
    appendLine("# Task Results")
    appendLine()
    results.forEach { result ->
        appendLine("## ${result.title}")
        appendLine("```${result.language}")
        appendLine(result.code)
        appendLine("```")
        appendLine()
    }
}
```

## 5. Integration Considerations

### Dependencies
- Clearly specify task dependencies
- Handle dependency results appropriately
- Consider parallel execution possibilities
- Document integration requirements

### State Management
- Update task state appropriately
- Handle interruptions gracefully
- Preserve important state information
- Clean up temporary state

## 6. Documentation

### Code Documentation
- Document public APIs thoroughly
- Explain complex logic
- Document configuration parameters
- Include usage examples

### User Documentation
- Provide clear promptSegment
- Document configuration options
- Include example use cases
- List limitations and requirements

```kotlin
override fun promptSegment() = """
    NewTask - Clear description of task purpose
      ** Required configuration item 1
      ** Required configuration item 2
      ** Optional configuration items
""".trimIndent()
```

## 7. Testing Considerations

- Test with various input configurations
- Verify error handling
- Test resource cleanup
- Validate output formatting
- Test integration with other tasks
- Consider performance implications

## 8. Maintenance

- Keep prompts updated with system capabilities
- Monitor for AI model changes
- Update documentation as needed
- Review and optimize performance
- Handle deprecated features gracefully

Following these best practices will help ensure new tasks are reliable, maintainable, and integrate well with the existing system.