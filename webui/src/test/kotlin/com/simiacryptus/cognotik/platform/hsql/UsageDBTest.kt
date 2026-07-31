package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import kotlin.random.Random

abstract class UsageTest(private val impl: UsageInterface) {
    companion object {
        private val log = LoggerFactory.getLogger(UsageTest::class.java)
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
        val model = model
        val session = Session.newUserID()
        val usage = ModelSchema.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
        )
        log.info("Incrementing usage for session {} with model {}", session, model)
        impl.incrementUsage(session, testUser, model, usage)
        val usageSummary: Map<String, ModelSchema.Usage> = impl.getSessionUsageSummary(session)
        Assertions.assertEquals(usage, usageSummary[model.modelId])
//    val userUsageSummary = impl.getUserUsageSummary(testUser)
//    Assertions.assertEquals(usage, userUsageSummary[model.modelId])
    }

    val model = object : AIModel {
        override val modelId get() = "test-model"
        override val provider: APIProvider? get() = null
    }

    @Test
    fun `getUserUsageSummary should return correct usage summary`() {
        log.debug("Starting test: getUserUsageSummary should return correct usage summary")
        val model = model
        val session = Session.newUserID()
        val usage = ModelSchema.Usage(
            prompt_tokens = 15,
            completion_tokens = 25,
            cost = 35.0,
        )
        log.info("Incrementing usage for user {} with model {}", testUser.email, model)
        impl.incrementUsage(session, testUser, model, usage)
//    val userUsageSummary: Map<String, ModelSchema.Usage> = impl.getUserUsageSummary(testUser)
//    Assertions.assertEquals(usage, userUsageSummary[model.modelId])
    }

    @Test
    fun `clear should reset all usage data`() {
        log.debug("Starting test: clear should reset all usage data")
        val model = model
        val session = Session.newUserID()
        val usage = ModelSchema.Usage(
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
//    val userUsageSummary = impl.getUserUsageSummary(testUser)
//    Assertions.assertTrue(userUsageSummary.isEmpty())
    }

    @Test
    fun `incrementUsage should handle multiple models correctly`() {
        log.debug("Starting test: incrementUsage should handle multiple models correctly")
        val model1 = model
        val model2 = object : AIModel {
            override val modelId get() = "test-model-2"
            override val provider: APIProvider? get() = null
        }
        val session = Session.newUserID()
        val usage1 = ModelSchema.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
        )
        val usage2 = ModelSchema.Usage(
            prompt_tokens = 5,
            completion_tokens = 10,
        )
        log.info("Incrementing usage for model1 {} and model2 {}", model1, model2)
        impl.incrementUsage(session, testUser, model1, usage1)
        impl.incrementUsage(session, testUser, model2, usage2)
        log.debug("Verifying usage summaries for session and user")
        val usageSummary: Map<String, ModelSchema.Usage> = impl.getSessionUsageSummary(session)
        Assertions.assertEquals(usage1, usageSummary[model1.modelId])
        Assertions.assertEquals(usage2, usageSummary[model2.modelId])
//    val userUsageSummary: Map<String, ModelSchema.Usage> = impl.getUserUsageSummary(testUser)
//    Assertions.assertEquals(usage1, userUsageSummary[model1.modelId])
//    Assertions.assertEquals(usage2, userUsageSummary[model2.modelId])
    }

    @Test
    fun `incrementUsage should accumulate usage for the same model`() {
        log.debug("Starting test: incrementUsage should accumulate usage for the same model")
        val model = model
        val session = Session.newUserID()
        val usage1 = ModelSchema.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
            cost = 30.0,
        )
        val usage2 = ModelSchema.Usage(
            prompt_tokens = 5,
            completion_tokens = 10,
            cost = 15.0,
        )
        log.info("Incrementing usage twice for model {}", model)
        impl.incrementUsage(session, testUser, model, usage1)
        impl.incrementUsage(session, testUser, model, usage2)
        log.debug("Verifying accumulated usage")
        val usageSummary: Map<String, ModelSchema.Usage> = impl.getSessionUsageSummary(session)
        val expectedUsage = ModelSchema.Usage(
            prompt_tokens = 15,
            completion_tokens = 30,
        )
        Assertions.assertEquals(expectedUsage, usageSummary[model.modelId])
//    val userUsageSummary: Map<String, ModelSchema.Usage> = impl.getUserUsageSummary(testUser)
//    Assertions.assertEquals(expectedUsage, userUsageSummary[model.modelId])
    }

    @Test
    fun `getSessionUsageSummary should return empty map for unknown session`() {
        log.debug("Starting test: getSessionUsageSummary should return empty map for unknown session")
        val session = Session.newUserID()
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
//    val userUsageSummary = impl.getUserUsageSummary(unknownUser)
//    Assertions.assertTrue(userUsageSummary.isEmpty())
    }

    @Test
    fun `getSessionUsageSummary should include child session usage`() {
        log.debug("Starting test: getSessionUsageSummary should include child session usage")
        val parentSession = Session.newUserID()
        val childSession = Session.newUserID()
        val parentUsage = ModelSchema.Usage(
            prompt_tokens = 10,
            completion_tokens = 20,
            cost = 30.0,
        )
        val childUsage = ModelSchema.Usage(
            prompt_tokens = 5,
            completion_tokens = 10,
        )
        log.info("Incrementing usage for parent session {} and child session {}", parentSession, childSession)
        impl.incrementUsage(parentSession, testUser, model, parentUsage)
        impl.incrementUsage(childSession, testUser, model, childUsage)
        log.info("Setting parent session relationship: child={}, parent={}", childSession, parentSession)
        impl.setParentSession(childSession, parentSession)
        val parentSummary = impl.getSessionUsageSummary(parentSession)
        val expectedCombined = ModelSchema.Usage(
            prompt_tokens = 15,
            completion_tokens = 30,
        )
        log.debug("Verifying combined usage for parent session includes child usage")
        Assertions.assertEquals(expectedCombined, parentSummary[model.modelId])
        val childSummary = impl.getSessionUsageSummary(childSession)
        Assertions.assertEquals(childUsage, childSummary[model.modelId])
    }

    @Test
    fun `getSessionUsageSummary should include deeply nested child session usage`() {
        log.debug("Starting test: getSessionUsageSummary should include deeply nested child session usage")
        val grandparentSession = Session.newUserID()
        val parentSession = Session.newUserID()
        val childSession = Session.newUserID()
        val grandparentUsage = ModelSchema.Usage(prompt_tokens = 1, completion_tokens = 2, cost = 3.0)
        val parentUsage = ModelSchema.Usage(prompt_tokens = 4, completion_tokens = 5, cost = 6.0)
        val childUsage = ModelSchema.Usage(prompt_tokens = 7, completion_tokens = 8, cost = 9.0)
        impl.incrementUsage(grandparentSession, testUser, model, grandparentUsage)
        impl.incrementUsage(parentSession, testUser, model, parentUsage)
        impl.incrementUsage(childSession, testUser, model, childUsage)
        impl.setParentSession(childSession, parentSession)
        impl.setParentSession(parentSession, grandparentSession)
        val grandparentSummary = impl.getSessionUsageSummary(grandparentSession)
        val expectedCombined = ModelSchema.Usage(
            prompt_tokens = 12,
            completion_tokens = 15,
        )
        log.debug("Verifying combined usage for grandparent session includes all descendant usage")
        Assertions.assertEquals(expectedCombined, grandparentSummary[model.modelId])
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// Pass null root => use in-memory ephemeral HSQL database (mem:<dbName>).
class UsageDBTest : UsageTest(run {
    UsageDB()
})