package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.LLMPollSimulationTask.*
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object LLMPollSimulationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    val questions = listOf(
      SurveyQuestion(
        id = "q1",
        text = "How likely are you to recommend our AI services to a colleague?",
        type = QuestionType.LIKERT_SCALE,
        min = 1,
        max = 5
      ),
      SurveyQuestion(
        id = "q2",
        text = "Which feature do you find most valuable?",
        type = QuestionType.SINGLE_CHOICE,
        options = listOf("Speed", "Accuracy", "Ease of Use", "Cost")
      ),
      SurveyQuestion(
        id = "q3",
        text = "What is the primary reason for your rating in the first question?",
        type = QuestionType.OPEN_ENDED
      )
    )

    val profiles = listOf(
      RespondentProfile(
        id = "tech_lead",
        description = "A senior technical lead at a mid-sized software company",
        demographics = mapOf(
          "age" to "35-44",
          "gender" to "Male",
          "location" to "Urban",
          "education" to "Master's"
        ),
        characteristics = listOf("Data-driven", "Pragmatic", "Early adopter"),
        background_context = "Has been using AI tools for 2 years to improve team productivity."
      ),
      RespondentProfile(
        id = "student",
        description = "A university student studying humanities",
        demographics = mapOf(
          "age" to "18-24",
          "gender" to "Female",
          "location" to "Suburban",
          "education" to "Some College"
        ),
        characteristics = listOf("Creative", "Skeptical of automation", "Budget-conscious"),
        background_context = "Uses AI occasionally for research but worries about academic integrity."
      )
    )

    TaskHarness(
      taskType = LLMPollSimulationTask.LLMPollSimulation,
      typeConfig = TaskTypeConfig(
        task_type = LLMPollSimulationTask.LLMPollSimulation.name
      ),
      executionConfig = LLMPollSimulationTaskExecutionConfigData(
        questions = questions,
        respondent_profiles = profiles,
        respondents_per_profile = 3, // Small number for testing
        include_demographics = true,
        cross_tabulation = true,
        sentiment_analysis = true,
        bias_detection = true,
        temperature = 0.7
      ),
      timeoutMinutes = 15,
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}