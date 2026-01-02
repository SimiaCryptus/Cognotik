package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.GameTheoryTask.GameTheoryTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object GameTheoryTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testPrisonersDilemma() {
        TaskTestHarness(
            taskType = GameTheoryTask.GameTheory,
            typeConfig = TaskTypeConfig(
                task_type = GameTheoryTask.GameTheory.name
            ),
            executionConfig = GameTheoryTaskExecutionConfigData(
                game_scenario = """
                    Two suspects are arrested for a crime. The police have insufficient evidence for a conviction on the principal charge, but enough to convict both on a lesser charge. 
                    The police offer each prisoner a bargain:
                    - If A and B both betray each other, each of them serves 5 years in prison.
                    - If A betrays B but B remains silent, A is set free and B serves 10 years in prison.
                    - If A remains silent but B betrays A, A serves 10 years in prison and B is set free.
                    - If A and B both remain silent, both of them serve 1 year in prison.
                """.trimIndent(),
                players = listOf("Suspect A", "Suspect B"),
                player_strategies = mapOf(
                    "Suspect A" to listOf("Betray", "Silent"),
                    "Suspect B" to listOf("Betray", "Silent")
                ),
                game_type = "non-cooperative",
                build_payoff_matrix = true,
                find_nash_equilibria = true,
                analyze_dominant_strategies = true,
                find_pareto_optimal = true,
                provide_recommendations = true
            ),
            timeoutMinutes = 10,
        ).run()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testRepeatedGame() {
        TaskTestHarness(
            taskType = GameTheoryTask.GameTheory,
            typeConfig = TaskTypeConfig(
                task_type = GameTheoryTask.GameTheory.name
            ),
            executionConfig = GameTheoryTaskExecutionConfigData(
                game_scenario = "A repeated Cournot competition between two firms in a duopoly market.",
                players = listOf("Firm A", "Firm B"),
                game_type = "non-cooperative",
                repeated_game_analysis = true,
                iterations = 5,
                provide_recommendations = true
            ),
            timeoutMinutes = 10,
        ).run()
    }
}