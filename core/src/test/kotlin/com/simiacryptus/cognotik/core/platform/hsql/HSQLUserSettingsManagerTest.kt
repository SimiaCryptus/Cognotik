package com.simiacryptus.cognotik.platform.hsql

import java.nio.file.Files

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.OpenAIModels
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

abstract class UsageTest(private val impl: UsageInterface) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(UsageTest::class.java)
    }

    private val testUser = User(
        email = "test@example.com",
        name = "Test User",
        id = Random.nextInt().toString()
    )

    @BeforeEach
    fun setup() {
        log.info("Setting up UsageTest: Clearing all usage data")
        impl.clear()
    }

    @Test
    fun `incrementUsage should increment usage for session`() {
        log.debug("Starting test: incrementUsage should increment usage for session")
        val model = OpenAIModels.GPT4oMini
        val session = Session.newGlobalID()
        val usage = ApiModel.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
            cost = 30.0,
        )
        log.info("Incrementing usage for session {} with model {}", session, model)
        impl.incrementUsage(session, testUser, model, usage)
        val usageSummary = impl.getSessionUsageSummary(session)
        Assertions.assertEquals(usage, usageSummary[model])
        val userUsageSummary = impl.getUserUsageSummary(testUser)
        Assertions.assertEquals(usage, userUsageSummary[model])
    }

    @Test
    fun `getUserUsageSummary should return correct usage summary`() {
        log.debug("Starting test: getUserUsageSummary should return correct usage summary")
        val model = OpenAIModels.GPT4oMini
        val session = Session.newGlobalID()
        val usage = ApiModel.Usage(
            prompt_tokens = 15,
            completion_tokens = 25,
            cost = 35.0,
        )
        log.info("Incrementing usage for user {} with model {}", testUser.email, model)
        impl.incrementUsage(session, testUser, model, usage)
        val userUsageSummary = impl.getUserUsageSummary(testUser)
        Assertions.assertEquals(usage, userUsageSummary[model])
    }

    @Test
    fun `clear should reset all usage data`() {
        log.debug("Starting test: clear should reset all usage data")
        val model = OpenAIModels.GPT4oMini
        val session = Session.newGlobalID()
        val usage = ApiModel.Usage(
            prompt_tokens = 20,
            completion_tokens = 30,
            cost = 40.0,
        )
        log.info("Incrementing usage before clearing")
        impl.incrementUsage(session, testUser, model, usage)
        log.info("Clearing all usage data")
        impl.clear()
        val usageSummary = impl.getSessionUsageSummary(session)
        Assertions.assertTrue(usageSummary.isEmpty())
        val userUsageSummary = impl.getUserUsageSummary(testUser)
        Assertions.assertTrue(userUsageSummary.isEmpty())
    }

    @Test
    fun `incrementUsage should handle multiple models correctly`() {
        log.debug("Starting test: incrementUsage should handle multiple models correctly")
        val model1 = OpenAIModels.GPT4oMini
        val model2 = OpenAIModels.GPT4Turbo
        val session = Session.newGlobalID()
        val usage1 = ApiModel.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
            cost = 30.0,
        )
        val usage2 = ApiModel.Usage(
            prompt_tokens = 5,
            completion_tokens = 10,
            cost = 15.0,
        )
        log.info("Incrementing usage for model1 {} and model2 {}", model1, model2)
        impl.incrementUsage(session, testUser, model1, usage1)
        impl.incrementUsage(session, testUser, model2, usage2)
        log.debug("Verifying usage summaries for session and user")
        val usageSummary = impl.getSessionUsageSummary(session)
        Assertions.assertEquals(usage1, usageSummary[model1])
        Assertions.assertEquals(usage2, usageSummary[model2])
        val userUsageSummary = impl.getUserUsageSummary(testUser)
        Assertions.assertEquals(usage1, userUsageSummary[model1])
        Assertions.assertEquals(usage2, userUsageSummary[model2])
    }

    @Test
    fun `incrementUsage should accumulate usage for the same model`() {
        log.debug("Starting test: incrementUsage should accumulate usage for the same model")
        val model = OpenAIModels.GPT4oMini
        val session = Session.newGlobalID()
        val usage1 = ApiModel.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
            cost = 30.0,
        )
        val usage2 = ApiModel.Usage(
            prompt_tokens = 5,
            completion_tokens = 10,
            cost = 15.0,
        )
        log.info("Incrementing usage twice for model {}", model)
        impl.incrementUsage(session, testUser, model, usage1)
        impl.incrementUsage(session, testUser, model, usage2)
        log.debug("Verifying accumulated usage")
        val usageSummary = impl.getSessionUsageSummary(session)
        val expectedUsage = ApiModel.Usage(
            prompt_tokens = 15,
            completion_tokens = 30,
            cost = 45.0,
        )
        Assertions.assertEquals(expectedUsage, usageSummary[model])
        val userUsageSummary = impl.getUserUsageSummary(testUser)
        Assertions.assertEquals(expectedUsage, userUsageSummary[model])
    }

    @Test
    fun `getSessionUsageSummary should return empty map for unknown session`() {
        log.debug("Starting test: getSessionUsageSummary should return empty map for unknown session")
        val session = Session.newGlobalID()
        log.info("Retrieving usage summary for unknown session {}", session)
        val usageSummary = impl.getSessionUsageSummary(session)
        Assertions.assertTrue(usageSummary.isEmpty())
    }

    @Test
    fun `getUserUsageSummary should return empty map for unknown user`() {
        log.debug("Starting test: getUserUsageSummary should return empty map for unknown user")
        val unknownUser = User(
            email = "unknown@example.com",
            name = "Unknown User",
            id = Random.nextInt().toString()
        )
        log.info("Retrieving usage summary for unknown user {}", unknownUser.email)
        val userUsageSummary = impl.getUserUsageSummary(unknownUser)
        Assertions.assertTrue(userUsageSummary.isEmpty())
    }
}
class HSQLUsageManagerTest : UsageTest(HSQLUsageManager(Files.createTempDirectory("usageManager").toFile()))
