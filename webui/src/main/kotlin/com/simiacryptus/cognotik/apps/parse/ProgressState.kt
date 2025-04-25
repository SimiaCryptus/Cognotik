package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.session.SessionTask

data class ProgressState(
  var progress: Double = 0.0,
  var max: Double = 0.0,
  val onUpdate: MutableList<(ProgressState) -> Unit> = mutableListOf(),
) {
  fun add(progress: Double, max: Double) {
    this.progress += progress
    this.max += max
    onUpdate.forEach { it(this) }
  }

  companion object {
    fun progressBar(
      task: SessionTask,
    ): ProgressState {
      val stringBuilder = task.add(
        """
                    <style>
                    .progress {
                        width: 100%;
                        background-color: #f0f0f0;
                        border-radius: 5px;
                        margin: 10px 0;
                    }
                    .progress-bar {
                        height: 20px;
                        background-color: #4CAF50;
                        border-radius: 5px;
                        transition: width 0.5s ease-in-out;
                    }
                    </style>
                    <div class="progress">
                      <div class="progress-bar" role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div>
                    </div>
                """
      )!!
      return ProgressState(0.0, 0.0).apply {
        onUpdate += {
          val progress = it.progress / it.max
          stringBuilder.set(
            """
                    <style>
                    .progress {
                        width: 100%;
                        background-color: #f0f0f0;
                        border-radius: 5px;
                        margin: 10px 0;
                    }
                    .progress-bar {
                        height: 20px;
                        background-color: #4CAF50;
                        border-radius: 5px;
                        transition: width 0.5s ease-in-out;
                    }
                    </style>
                    <div class="progress">
                      <div class="progress-bar" role="progressbar" style="width: ${progress * 100}%" aria-valuenow="${progress * 100}" aria-valuemin="0" aria-valuemax="100"></div>
                    </div>
                """.trimIndent()
          )
          task.update()
        }
      }
    }
  }
}