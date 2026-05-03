package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.defaultUser
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object GitHubSearchTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.Companion.configurePlatform(defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = GitHubSearchTask.GitHubSearch,
      typeConfig = TaskTypeConfig(
        task_type = GitHubSearchTask.GitHubSearch.name
      ),
      executionConfig = GitHubSearchTask.GitHubSearchTaskExecutionConfigData(
        search_query = "kotlin language:kotlin",
        search_type = "repositories",
        per_page = 5,
        sort = "stars",
        order = "desc",
        task_description = "Search for popular Kotlin repositories on GitHub",
      ),
      timeoutMinutes = 10,
      user = defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}