package com.simiacryptus.cognotik.plan.tools

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.util.*
import java.io.File

@JsonDeserialize(using = TaskTypeDeserializer::class)
@JsonSerialize(using = TaskTypeSerializer::class)
class TaskType<out T : TaskExecutionConfig, out U : TaskTypeConfig>(
  name: String,
  val category: String,
  val taskClass: Class<out AbstractTask<out T, out U>>,
  val executionConfigClass: Class<out T>,
  val taskSettingsClass: Class<out U>,
  val description: String? = null,
  val tooltipHtml: String? = null,
) : DynamicEnum<TaskType<*, *>>(name) {
  companion object {

    private val log = LoggerFactory.getLogger(TaskType::class.java)
    val _taskConstructors: MutableMap<TaskType<*, *>, (OrchestrationConfig, TaskExecutionConfig?) -> AbstractTask<out TaskExecutionConfig, TaskTypeConfig>> =
      mutableMapOf()

    inline fun <reified T : TaskExecutionConfig, U : TaskTypeConfig> registerTaskType(
      taskType: TaskType<T, U>
    ) {
      try {
        val constructor = taskType.getConstructor()
        _taskConstructors[taskType] = { settings: OrchestrationConfig, task: TaskExecutionConfig? ->
          try {
            constructor(settings, task?.jsonCast<T>()) as AbstractTask<TaskExecutionConfig, TaskTypeConfig>
          } catch (e: ClassCastException) {
            throw RuntimeException(
              "Failed to create task instance for task type: ${taskType.name}. Ensure that the task execution config class and task class are correctly paired.",
              e
            )
          }
        }
        register(taskType)
      } catch (e: NoSuchMethodException) {
        throw RuntimeException(
          "Failed to register task type: ${taskType.name}. Ensure that the task class has a constructor with parameters (OrchestrationConfig, ${taskType.executionConfigClass.name})",
          e
        )
      }

    }

    val taskConstructors get() = _taskConstructors.toMap()

    fun values(): List<TaskType<*, *>> {
      @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
      return values(TaskType::class.java)
    }

    fun OrchestrationConfig.getImpl(
      planTask: TaskExecutionConfig?
    ) = getImpl(
      taskType = planTask?.task_type?.let { valueOf(it) } ?: throw RuntimeException("Task type not specified"),
      cfg = planTask)


    fun <T : TaskExecutionConfig, U : TaskTypeConfig> OrchestrationConfig.getImpl(
      taskType: TaskType<T, U>, cfg: TaskExecutionConfig? = null
    ): AbstractTask<out T, U> {
      val constructor = taskConstructors[taskType]
      if (constructor == null) {
        throw RuntimeException("Unknown task type: ${taskType.name}")
      }
      val executionConfig: TaskExecutionConfig = cfg ?: try {
        taskType.executionConfigClass.getDeclaredConstructor().newInstance() as TaskExecutionConfig
      } catch (e: NoSuchMethodException) {
        throw RuntimeException(
          "Task execution config class ${taskType.executionConfigClass.name} does not have a no-arg constructor. Please provide a planTask instance.",
          e
        )
      }
      try {
        val task = constructor(this, executionConfig)
        return task as AbstractTask<out T, U>
      } catch (e: ClassCastException) {
        throw RuntimeException(
          "Failed to create task instance for task type: ${taskType.name}. Ensure that the task execution config class and task class are correctly paired.",
          e
        )
      }
    }

    fun getAvailableTaskTypes(orchestrationConfig: OrchestrationConfig): List<TaskType<*, *>> {
      @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
      return orchestrationConfig.taskSettings.mapNotNull { x ->
        valueOf(
          x.value.task_type ?: return@mapNotNull null
        )
      }
    }

    fun valueOf(name: String): TaskType<*, *> {
      @Suppress("SENSELESS_COMPARISON") require(taskConstructors != null) { "Task constructors not initialized" } // Trigger lazy initialization
      return valueOf(TaskType::class.java, name)
    }

    fun register(taskType: TaskType<*, *>) = register(TaskType::class.java, taskType)
  }

  fun getConstructor(): (OrchestrationConfig, @UnsafeVariance T?) -> AbstractTask<out T, out U> =
    taskClass.let { cls ->
      require(AbstractTask::class.java.isAssignableFrom(cls)) { "Task class ${cls.name} must be a subclass of AbstractTask" }
      val method = cls.getDeclaredConstructor(OrchestrationConfig::class.java, executionConfigClass)
        .apply { isAccessible = true }!!
      { settings, task ->
        require(OrchestrationConfig::class.java.isAssignableFrom(settings.javaClass)) { "Settings must be an instance of OrchestrationConfig" }
        var task = task
        if (task != null && !executionConfigClass.isAssignableFrom(task.javaClass)) {
          //"Task execution config must be an instance of ${executionConfigClass.name} or null: found ${task?.javaClass?.name}"
          task = JsonUtil.fromJson<T>(task.toJson(), executionConfigClass)
        }
        method.newInstance(settings, task) as AbstractTask<T, U>
      }
    }
}

class TaskTypeSerializer : DynamicEnumSerializer<TaskType<*, *>>(TaskType::class.java)

class TaskTypeDeserializer : DynamicEnumDeserializer<TaskType<*, *>>(TaskType::class.java)