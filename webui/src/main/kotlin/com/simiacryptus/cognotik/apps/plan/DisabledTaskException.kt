package com.simiacryptus.cognotik.apps.plan

class DisabledTaskException(taskType: TaskType<*, *>) : Exception("Task type $taskType is disabled")