package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.SoftwareDesignDocumentTask.SoftwareDesignDocumentTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object SoftwareDesignDocumentTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = SoftwareDesignDocumentTask.SoftwareDesignDocument,
            typeConfig = TaskTypeConfig(
                task_type = SoftwareDesignDocumentTask.SoftwareDesignDocument.name
            ),
            executionConfig = SoftwareDesignDocumentTaskExecutionConfigData(
                project_name = "Task Management System",
                system_description = """
                    A web-based application for managing tasks, projects, and team collaboration. 
                    The system should support user authentication, task creation, assignment, 
                    status tracking (Kanban style), and basic reporting. 
                    It needs to handle multiple projects and teams.
                """.trimIndent(),
                target_audience = "Project managers and software developers",
                stakeholders = listOf("Project Managers", "Developers", "QA Engineers", "Product Owners"),
                technology_stack = listOf("Kotlin", "Spring Boot", "React", "PostgreSQL", "Docker"),
                generate_use_cases = true,
                generate_requirements = true,
                generate_architecture = true,
                generate_data_model = true,
                generate_flow_diagrams = true,
                generate_test_plan = true,
                generate_phase_plan = true,
                generate_project_data = true,
                sprint_count = 4,
                sprint_duration_weeks = 2
            ),
            timeoutMinutes = 15,
        ).run()
    }
}