package com.simiacryptus.cognotik.plan

class DisabledTaskException(taskType: TaskType<*, *>) : Exception("Task type $taskType is disabled")