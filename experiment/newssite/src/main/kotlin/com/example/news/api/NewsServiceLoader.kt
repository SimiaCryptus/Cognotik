package com.example.news.api

import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.actors.ProxyAgent
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.util.JsonUtil
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * A loader class to create a proxy for the NewsApiService that is backed by an LLM.
 */
object NewsServiceLoader {

  /**
   * Creates a new instance of the NewsApiService proxy.
   * It uses an LLM model to generate responses.
   *
   * @return A proxy instance of [NewsApiService].
   */
  fun createNewsService(
    clazz: Class<NewsApiService> = NewsApiService::class.java,
    model: ChatInterface = chatInterface(),
    temperature: Double = 0.8,
    validation: Boolean = true,
    maxRetries: Int = 1,
    describer: AbbrevWhitelistYamlDescriber = describer()
  ): NewsApiService = ProxyAgent(
    clazz = clazz,
    model = model,
    temperature = temperature,
    validation = validation,
    maxRetries = maxRetries,
    describer = describer
  ).create()

  fun describer() = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "com.simiacryptus", "com.example.news.api"
  ) {
    override val includeMethods: Boolean get() = false
  }

  fun chatInterface() = AnthropicModels.Claude45Haiku.instance(
    key = NewsServiceLoader::class.java.getResourceAsStream("/anthropic.key")?.bufferedReader()?.readText()?.trim()
      ?: throw IllegalStateException("API key not found in resources. Please create src/main/resources/anthropic.key"),
    workPool = Executors.newFixedThreadPool(4),
    scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
    onUsage = { model, usage ->
      println("Usage for model ${model.modelName}: $usage")
    }
  )

  /**
   * A test entry point to demonstrate the usage of the NewsApiService proxy.
   * This will make live calls to the configured LLM.
   * Ensure you have a valid API key in `src/main/resources/anthropic.key`.
   */
  @JvmStatic
  fun main(args: Array<String>) = try {
    runBlocking {
      val newsApi = createNewsService()

      println("Fetching top story IDs...")
      val topStoryIds = newsApi.getTopStoryIds().ids
      println("Top story IDs: ${JsonUtil.toJson(topStoryIds)}")

      if (topStoryIds.isNotEmpty()) {
        val firstStoryId = topStoryIds.first()
        println("\nFetching details for story ID: $firstStoryId")
        val storyDetails = newsApi.getStoryDetails(firstStoryId)
        println("Story details: ${JsonUtil.toJson(storyDetails)}")

        if (storyDetails.author.isNotBlank()) {
          println("\nFetching details for user: ${storyDetails.author}")
          val userDetails = newsApi.getUserDetails(storyDetails.author)
          println("User details: ${JsonUtil.toJson(userDetails)}")
        }
      }
    }
  } finally {
    exitProcess(0)
  }
}
