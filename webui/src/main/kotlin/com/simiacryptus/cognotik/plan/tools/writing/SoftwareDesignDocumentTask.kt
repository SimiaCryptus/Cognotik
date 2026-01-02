package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SoftwareDesignDocumentTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SoftwareDesignDocumentTaskExecutionConfigData?
) : AbstractTask<SoftwareDesignDocumentTask.SoftwareDesignDocumentTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxDescriptionLength = 2000

    class SoftwareDesignDocumentTaskExecutionConfigData(
        @Description("The name/title of the software project")
        val project_name: String? = null,
        @Description("High-level description of the software system to design")
        val system_description: String? = null,
        @Description("Target audience for the software (e.g., 'enterprise users', 'mobile consumers')")
        val target_audience: String? = null,
        @Description("Key stakeholders and their roles")
        val stakeholders: List<String>? = null,
        @Description("Whether to generate use case diagrams and documentation")
        val generate_use_cases: Boolean = true,
        @Description("Whether to generate functional and non-functional requirements")
        val generate_requirements: Boolean = true,
        @Description("Whether to generate architectural diagrams (C4, component, deployment)")
        val generate_architecture: Boolean = true,
        @Description("Whether to generate data model and ERD diagrams")
        val generate_data_model: Boolean = true,
        @Description("Whether to generate sequence and activity diagrams for key flows")
        val generate_flow_diagrams: Boolean = true,
        @Description("Whether to generate test plan and test case documentation")
        val generate_test_plan: Boolean = true,
        @Description("Whether to generate phase planning with milestones")
        val generate_phase_plan: Boolean = true,
        @Description("Whether to generate the project data JSON file with tasks, epics, sprints, etc.")
        val generate_project_data: Boolean = true,
        @Description("Number of sprints to plan (default: 6)")
        val sprint_count: Int = 6,
        @Description("Sprint duration in weeks (default: 2)")
        val sprint_duration_weeks: Int = 2,
        @Description("Technology stack constraints or preferences")
        val technology_stack: List<String>? = null,
        @Description("Known constraints or limitations")
        val constraints: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for context")
        val input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = SoftwareDesignDocument.name,
        task_description = task_description
            ?: "Generate software design document for: ${project_name ?: system_description?.take(50)}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    // Data classes for project planning JSON output
    data class ProjectData(
        val project_name: String = "",
        val description: String = "",
        val created_date: String = "",
        val epics: List<Epic> = emptyList(),
        val releases: List<Release> = emptyList(),
        val sprints: List<Sprint> = emptyList(),
        val tasks: List<Task> = emptyList(),
        val milestones: List<Milestone> = emptyList(),
        val dependencies: List<Dependency> = emptyList()
    )

    data class Epic(
        val id: String = "",
        val name: String = "",
        val description: String = "",
        val priority: String = "Medium",
        val status: String = "Planned",
        val story_points: Int? = null
    )

    data class Release(
        val id: String = "",
        val name: String = "",
        val version: String = "",
        val target_date: String = "",
        val description: String = "",
        val epic_ids: List<String> = emptyList(),
        val status: String = "Planned"
    )

    data class Sprint(
        val id: String = "",
        val name: String = "",
        val number: Int = 0,
        val start_date: String = "",
        val end_date: String = "",
        val goals: List<String> = emptyList(),
        val capacity_points: Int = 0,
        val task_ids: List<String> = emptyList(),
        val status: String = "Planned"
    )

    data class Task(
        val id: String = "",
        val title: String = "",
        val description: String = "",
        val type: String = "Feature", // "Feature", "Bug", "Chore", "Spike"
        val epic_id: String? = null,
        var sprint_id: String? = null,
        val priority: String = "Medium", // "Low", "Medium", "High", "Critical"
        val story_points: Int? = null,
        val status: String = "Backlog",
        val acceptance_criteria: List<String>? = null,
        val labels: List<String>? = null
    )

    data class Milestone(
        val id: String = "",
        val name: String = "",
        val target_date: String = "",
        val description: String = "",
        val deliverables: List<String> = emptyList(),
        val status: String = "Planned"
    )

    data class Dependency(
        val id: String = "",
        val source_id: String = "",
        val source_type: String = "", // "task", "epic", "milestone"
        val target_id: String = "",
        val target_type: String = "", // "task", "epic", "milestone"
        val dependency_type: String = "" // "blocks", "depends_on", "relates_to"
    )

    override fun promptSegment(): String {
        return """
SoftwareDesignDocument - Generate comprehensive software design documentation
  ** Specify the project name and system description
  ** Generate use case diagrams and actor documentation
  ** Create functional and non-functional requirements
  ** Produce architectural diagrams (C4, component, deployment)
  ** Design data models with ERD diagrams
  ** Create sequence and activity diagrams for key flows
  ** Generate test plans and test case documentation
  ** Plan development phases with milestones
  ** Output project data JSON with tasks, epics, sprints, releases
  ** All diagrams use Mermaid syntax for easy rendering
  ** Useful for:
     - Project kickoff documentation
     - Technical specification creation
     - Sprint and release planning
     - Stakeholder communication
     - Development team onboarding
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val startTime = System.currentTimeMillis()
        log.info("Starting SoftwareDesignDocumentTask for project: '${executionConfig?.project_name}'")
        var transcriptStream: FileOutputStream? = null

        val projectName = executionConfig?.project_name ?: "Unnamed Project"
        val systemDescription = executionConfig?.system_description
        if (systemDescription.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No system description specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val api = defaultSmart.getChildClient(task)

        val tabs = TabbedDisplay(task)
        transcriptStream = initializeTranscript(task, projectName)

        val overviewTask = tabs.newTask("Overview")
        var overviewStatusBuffer: StringBuilder? = null
        try {

            val targetAudience = executionConfig.target_audience ?: "general users"
            val stakeholders = executionConfig.stakeholders ?: emptyList()
            val techStack = executionConfig.technology_stack ?: emptyList()
            val constraints = executionConfig.constraints ?: emptyList()
            val sprintCount = executionConfig.sprint_count
            val sprintDuration = executionConfig.sprint_duration_weeks
            overviewTask.header("Software Design Document Generation")

            overviewTask.add(
                buildString {
                    appendLine("**Project:** $projectName")
                    appendLine()
                    appendLine("**System:** ${systemDescription.take(200)}${if (systemDescription.length > 200) "..." else ""}")
                    appendLine()
                    appendLine("**Target Audience:** $targetAudience")
                    appendLine()
                    appendLine("**Document Sections:**")
                    if (executionConfig.generate_use_cases) appendLine("- ✅ Use Cases & Actors")
                    if (executionConfig.generate_requirements) appendLine("- ✅ Requirements Specification")
                    if (executionConfig.generate_architecture) appendLine("- ✅ Architecture Diagrams")
                    if (executionConfig.generate_data_model) appendLine("- ✅ Data Model & ERD")
                    if (executionConfig.generate_flow_diagrams) appendLine("- ✅ Flow Diagrams")
                    if (executionConfig.generate_test_plan) appendLine("- ✅ Test Plan")
                    if (executionConfig.generate_phase_plan) appendLine("- ✅ Phase Planning")
                    if (executionConfig.generate_project_data) appendLine("- ✅ Project Data JSON")
                    appendLine()
                    appendLine(
                        "**Started:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            overviewStatusBuffer = overviewTask.add("**Status:** 🔄 Gathering context...".renderMarkdown)
            transcriptStream?.write(
                "# Software Design Document: $projectName\n\n**System:** $systemDescription\n\n**Generated:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n---\n\n".toByteArray()
            )
            task.update()

            // Gather context
            log.debug("Gathering context from prior tasks and input files")
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContext = getInputFileCode()

            // Initialize design agent
            log.info("Initializing software design agent")
            val designAgent = ChatAgent(
                prompt = buildDesignPrompt(
                    projectName, systemDescription, targetAudience,
                    stakeholders, techStack, constraints, priorContext, inputFileContext
                ),
                model = api,
                temperature = 0.5
            )

            // Collected data for project JSON
            val collectedEpics = mutableListOf<Epic>()
            val collectedTasks = mutableListOf<Task>()
            val collectedMilestones = mutableListOf<Milestone>()
            val collectedDependencies = mutableListOf<Dependency>()

            // Section 1: Use Cases
            if (executionConfig.generate_use_cases) {
                log.debug("Generating use cases and actor documentation")
                val useCaseTask = tabs.newTask("Use Cases")

                val useCaseBuffer =
                    useCaseTask.add("## Use Cases\n\n🔄 Analyzing actors and use cases...".renderMarkdown)
                task.update()

                val useCaseAnalysis = designAgent.answer(
                    listOf(
                        """
Generate comprehensive use case documentation:

1. **Actor Identification**
   - List all actors (users, systems, external services)
   - Describe each actor's role and goals
   - Identify actor relationships

2. **Use Case Catalog**
   For each major use case:
   - UC-ID and Name
   - Primary Actor
   - Preconditions
   - Main Success Scenario (numbered steps)
   - Alternative Flows
   - Postconditions
   - Business Rules

3. **Use Case Diagram** (Mermaid)
   Create a use case diagram showing actors and their interactions with the system.
   Use this format:
   ```mermaid
   graph LR
       subgraph Actors
           A1[Actor 1]
           A2[Actor 2]
       end
       subgraph System
           UC1((Use Case 1))
           UC2((Use Case 2))
       end
       A1 --> UC1
       A1 --> UC2
       A2 --> UC2
   ```

4. **Actor-Use Case Matrix**
   Show which actors participate in which use cases.

Provide detailed, actionable use case documentation.
                        """.trimIndent()
                    )
                )

                useCaseBuffer?.setLength(0)
                useCaseBuffer?.append(
                    buildString {
                        appendLine("## Use Cases & Actors")
                        appendLine()
                        appendLine("✅ Analysis complete")
                        appendLine()
                        appendLine(useCaseAnalysis)
                    }.renderMarkdown
                )
                useCaseTask.update()
                transcriptStream?.write("## Use Cases & Actors\n\n$useCaseAnalysis\n\n---\n\n".toByteArray())

                // Extract epics from use cases
                extractEpicsFromUseCases(useCaseAnalysis, collectedEpics)
            }

            // Section 2: Requirements
            if (executionConfig.generate_requirements) {
                log.debug("Generating requirements specification")
                val requirementsTask = tabs.newTask("Requirements")

                val requirementsBuffer =
                    requirementsTask.add("## Requirements\n\n🔄 Defining functional and non-functional requirements...".renderMarkdown)
                task.update()

                val requirementsAnalysis = designAgent.answer(
                    listOf(
                        """
Generate comprehensive requirements documentation:

1. **Functional Requirements**
   For each requirement:
   - FR-ID: Unique identifier
   - Description: Clear, testable statement
   - Priority: Must Have / Should Have / Could Have / Won't Have (MoSCoW)
   - Source: Which use case or stakeholder
   - Acceptance Criteria: Specific, measurable criteria

2. **Non-Functional Requirements**
   Categories to cover:
   - Performance (response times, throughput)
   - Scalability (users, data volume)
   - Security (authentication, authorization, data protection)
   - Reliability (uptime, recovery)
   - Usability (accessibility, UX standards)
   - Maintainability (code quality, documentation)
   - Compatibility (browsers, devices, integrations)

3. **Requirements Traceability Matrix**
   Show relationships between:
   - Use Cases → Requirements
   - Requirements → Test Cases (placeholder IDs)

4. **Requirements Dependency Diagram** (Mermaid)
   ```mermaid
   graph TD
       FR1[FR-001: User Login] --> FR2[FR-002: Session Management]
       FR2 --> FR3[FR-003: Access Control]
       NFR1[NFR-001: Response Time] -.-> FR1
   ```

Provide detailed, prioritized requirements.
                        """.trimIndent()
                    )
                )

                requirementsBuffer?.setLength(0)
                requirementsBuffer?.append(
                    buildString {
                        appendLine("## Requirements Specification")
                        appendLine()
                        appendLine("✅ Requirements defined")
                        appendLine()
                        appendLine(requirementsAnalysis)
                    }.renderMarkdown
                )
                requirementsTask.update()
                transcriptStream?.write("## Requirements Specification\n\n$requirementsAnalysis\n\n---\n\n".toByteArray())

                // Extract tasks from requirements
//               extractTasksFrom requirements(requirementsAnalysis, collectedTasks, collectedEpics)
            }

            // Section 3: Architecture
            if (executionConfig.generate_architecture) {
                log.debug("Generating architectural diagrams")
                val architectureTask = tabs.newTask("Architecture")

                val architectureBuffer =
                    architectureTask.add("## Architecture\n\n🔄 Designing system architecture...".renderMarkdown)
                task.update()

                val architectureAnalysis = designAgent.answer(
                    listOf(
                        """
Generate comprehensive architecture documentation:

1. **System Context Diagram (C4 Level 1)**
   Show the system and its relationships with users and external systems.
   ```mermaid
   graph TB
       subgraph External
           U[Users]
           ES1[External System 1]
       end
       S[System Name]
       U --> S
       S --> ES1
   ```

2. **Container Diagram (C4 Level 2)**
   Show high-level technology choices and container responsibilities.
   ```mermaid
   graph TB
       subgraph System
           WA[Web Application<br/>React]
           API[API Server<br/>Node.js]
           DB[(Database<br/>PostgreSQL)]
           CACHE[(Cache<br/>Redis)]
       end
       WA --> API
       API --> DB
       API --> CACHE
   ```

3. **Component Diagram (C4 Level 3)**
   For key containers, show internal components.

4. **Deployment Diagram**
   Show infrastructure and deployment topology.
   ```mermaid
   graph TB
       subgraph Cloud Provider
           subgraph Production
               LB[Load Balancer]
               subgraph App Tier
                   APP1[App Server 1]
                   APP2[App Server 2]
               end
               subgraph Data Tier
                   DB1[(Primary DB)]
                   DB2[(Replica DB)]
               end
           end
       end
       LB --> APP1
       LB --> APP2
       APP1 --> DB1
       APP2 --> DB1
       DB1 --> DB2
   ```

5. **Technology Stack Summary**
   - Frontend technologies
   - Backend technologies
   - Data storage
   - Infrastructure
   - DevOps tools

6. **Architecture Decision Records (ADRs)**
   Document key architectural decisions with:
   - Context
   - Decision
   - Consequences

Provide detailed architecture documentation with all diagrams.
                        """.trimIndent()
                    )
                )

                architectureBuffer?.setLength(0)
                architectureBuffer?.append(
                    buildString {
                        appendLine("## System Architecture")
                        appendLine()
                        appendLine("✅ Architecture designed")
                        appendLine()
                        appendLine(architectureAnalysis)
                    }.renderMarkdown
                )
                architectureTask.update()
                transcriptStream?.write("## System Architecture\n\n$architectureAnalysis\n\n---\n\n".toByteArray())

                // Add architecture epic
                collectedEpics.add(
                    Epic(
                        id = "EPIC-ARCH",
                        name = "Architecture & Infrastructure",
                        description = "Set up system architecture and infrastructure",
                        priority = "High",
                        story_points = 21
                    )
                )
            }

            // Section 4: Data Model
            if (executionConfig.generate_data_model) {
                log.debug("Generating data model and ERD")
                val dataModelTask = tabs.newTask("Data Model")

                val dataModelBuffer =
                    dataModelTask.add("## Data Model\n\n🔄 Designing data structures...".renderMarkdown)
                task.update()

                val dataModelAnalysis = designAgent.answer(
                    listOf(
                        """
Generate comprehensive data model documentation:

1. **Entity-Relationship Diagram**
   ```mermaid
   erDiagram
       USER ||--o{ ORDER : places
       USER {
           int id PK
           string email UK
           string name
           datetime created_at
       }
       ORDER ||--|{ ORDER_ITEM : contains
       ORDER {
           int id PK
           int user_id FK
           decimal total
           string status
           datetime created_at
       }
       ORDER_ITEM {
           int id PK
           int order_id FK
           int product_id FK
           int quantity
           decimal price
       }
       PRODUCT ||--o{ ORDER_ITEM : "ordered in"
       PRODUCT {
           int id PK
           string name
           string description
           decimal price
           int stock
       }
   ```

2. **Entity Descriptions**
   For each entity:
   - Purpose and business meaning
   - Attributes with types and constraints
   - Relationships and cardinality
   - Indexes and performance considerations

3. **Data Dictionary**
   | Entity | Attribute | Type | Constraints | Description |
   |--------|-----------|------|-------------|-------------|
   | User | id | INT | PK, AUTO | Unique identifier |

4. **Data Flow Diagram**
   Show how data moves through the system.

5. **Data Validation Rules**
   Business rules for data integrity.

6. **Data Migration Considerations**
   If migrating from existing systems.

Provide complete data model documentation.
                        """.trimIndent()
                    )
                )

                dataModelBuffer?.setLength(0)
                dataModelBuffer?.append(
                    buildString {
                        appendLine("## Data Model & ERD")
                        appendLine()
                        appendLine("✅ Data model designed")
                        appendLine()
                        appendLine(dataModelAnalysis)
                    }.renderMarkdown
                )
                dataModelTask.update()
                transcriptStream?.write("## Data Model & ERD\n\n$dataModelAnalysis\n\n---\n\n".toByteArray())
            }

            // Section 5: Flow Diagrams
            if (executionConfig.generate_flow_diagrams) {
                log.debug("Generating sequence and activity diagrams")
                val flowTask = tabs.newTask("Flow Diagrams")

                val flowBuffer = flowTask.add("## Flow Diagrams\n\n🔄 Mapping system flows...".renderMarkdown)
                task.update()

                val flowAnalysis = designAgent.answer(
                    listOf(
                        """
Generate flow diagrams for key system interactions:

1. **Sequence Diagrams**
   For 3-5 critical user journeys, create sequence diagrams:
   ```mermaid
   sequenceDiagram
       participant U as User
       participant W as Web App
       participant A as API Server
       participant D as Database
       
       U->>W: Login Request
       W->>A: POST /auth/login
       A->>D: Query User
       D-->>A: User Data
       A->>A: Validate Credentials
       A->>A: Generate JWT
       A-->>W: JWT Token
       W-->>U: Login Success
   ```

2. **Activity Diagrams**
   For complex business processes:
   ```mermaid
   graph TD
       A[Start] --> B{User Authenticated?}
       B -->|Yes| C[Load Dashboard]
       B -->|No| D[Show Login]
       D --> E[Enter Credentials]
       E --> F{Valid?}
       F -->|Yes| C
       F -->|No| G[Show Error]
       G --> D
       C --> H[End]
   ```

3. **State Diagrams**
   For entities with complex state transitions:
   ```mermaid
   stateDiagram-v2
       [*] --> Draft
       Draft --> Submitted: Submit
       Submitted --> UnderReview: Assign Reviewer
       UnderReview --> Approved: Approve
       UnderReview --> Rejected: Reject
       Rejected --> Draft: Revise
       Approved --> [*]
   ```

4. **Integration Flow Diagrams**
   Show data flow between systems.

5. **Error Handling Flows**
   Document how errors propagate and are handled.

Provide detailed flow documentation for all critical paths.
                        """.trimIndent()
                    )
                )

                flowBuffer?.setLength(0)
                flowBuffer?.append(
                    buildString {
                        appendLine("## Flow Diagrams")
                        appendLine()
                        appendLine("✅ Flows documented")
                        appendLine()
                        appendLine(flowAnalysis)
                    }.renderMarkdown
                )
                flowTask.update()
                transcriptStream?.write("## Flow Diagrams\n\n$flowAnalysis\n\n---\n\n".toByteArray())
            }

            // Section 6: Test Plan
            if (executionConfig.generate_test_plan) {
                log.debug("Generating test plan")
                val testPlanTask = tabs.newTask("Test Plan")

                val testPlanBuffer = testPlanTask.add("## Test Plan\n\n🔄 Creating test strategy...".renderMarkdown)
                task.update()

                val testPlanAnalysis = designAgent.answer(
                    listOf(
                        """
Generate comprehensive test plan documentation:

1. **Test Strategy Overview**
   - Testing objectives
   - Testing scope (in-scope/out-of-scope)
   - Testing approach
   - Entry/Exit criteria

2. **Test Levels**
   - Unit Testing: Coverage targets, frameworks
   - Integration Testing: API testing, component integration
   - System Testing: End-to-end scenarios
   - Acceptance Testing: UAT criteria

3. **Test Case Catalog**
   | TC-ID | Requirement | Description | Steps | Expected Result | Priority |
   |-------|-------------|-------------|-------|-----------------|----------|
   | TC-001 | FR-001 | User login with valid credentials | 1. Navigate to login... | User is authenticated | High |

4. **Test Coverage Matrix**
   ```mermaid
   graph LR
       subgraph Requirements
           FR1[FR-001]
           FR2[FR-002]
           FR3[FR-003]
       end
       subgraph Test Cases
           TC1[TC-001]
           TC2[TC-002]
           TC3[TC-003]
           TC4[TC-004]
       end
       FR1 --> TC1
       FR1 --> TC2
       FR2 --> TC3
       FR3 --> TC4
   ```

5. **Non-Functional Test Cases**
   - Performance test scenarios
   - Security test scenarios
   - Usability test scenarios

6. **Test Environment Requirements**
   - Hardware/software requirements
   - Test data requirements
   - Tool requirements

7. **Test Schedule**
   Timeline for test phases.

8. **Risk Assessment**
   Testing risks and mitigation strategies.

Provide actionable test documentation.
                        """.trimIndent()
                    )
                )

                testPlanBuffer?.setLength(0)
                testPlanBuffer?.append(
                    buildString {
                        appendLine("## Test Plan")
                        appendLine()
                        appendLine("✅ Test plan created")
                        appendLine()
                        appendLine(testPlanAnalysis)
                    }.renderMarkdown
                )
                testPlanTask.update()
                transcriptStream?.write("## Test Plan\n\n$testPlanAnalysis\n\n---\n\n".toByteArray())

                // Add testing epic
                collectedEpics.add(
                    Epic(
                        id = "EPIC-TEST",
                        name = "Quality Assurance",
                        description = "Testing and quality assurance activities",
                        priority = "High",
                        story_points = 13
                    )
                )
            }

            // Section 7: Phase Planning
            if (executionConfig.generate_phase_plan) {
                log.debug("Generating phase plan")
                val phasePlanTask = tabs.newTask("Phase Plan")

                val phasePlanBuffer =
                    phasePlanTask.add("## Phase Plan\n\n🔄 Planning development phases...".renderMarkdown)
                task.update()

                val phasePlanAnalysis = designAgent.answer(
                    listOf(
                        """
Generate development phase planning:

1. **Project Timeline Overview**
   ```mermaid
   gantt
       title Project Timeline
       dateFormat  YYYY-MM-DD
       section Phase 1: Foundation
       Architecture Setup    :a1, 2024-01-01, 2w
       Core Infrastructure   :a2, after a1, 2w
       section Phase 2: Core Features
       User Management       :b1, after a2, 3w
       Core Business Logic   :b2, after b1, 4w
       section Phase 3: Integration
       External Integrations :c1, after b2, 3w
       API Development       :c2, after b2, 3w
       section Phase 4: Polish
       UI/UX Refinement      :d1, after c1, 2w
       Performance Tuning    :d2, after c2, 2w
       section Phase 5: Launch
       UAT                   :e1, after d1, 2w
       Production Deployment :e2, after e1, 1w
   ```

2. **Phase Descriptions**
   For each phase:
   - Phase name and duration
   - Objectives and deliverables
   - Key activities
   - Dependencies
   - Success criteria
   - Risks and mitigations

3. **Milestone Schedule**
   | Milestone | Target Date | Deliverables | Success Criteria |
   |-----------|-------------|--------------|------------------|
   | M1: Architecture Complete | Week 4 | Architecture docs, infra setup | All diagrams approved |

4. **Resource Allocation**
   Team structure and responsibilities per phase.

5. **Sprint Planning Overview**
   For $sprintCount sprints of $sprintDuration weeks each:
   - Sprint goals
   - Capacity planning
   - Key deliverables

6. **Release Plan**
   - Release versions and dates
   - Features per release
   - Release criteria

7. **Risk Timeline**
   When risks are highest and mitigation windows.

Provide detailed phase planning with realistic timelines.
                        """.trimIndent()
                    )
                )

                phasePlanBuffer?.setLength(0)
                phasePlanBuffer?.append(
                    buildString {
                        appendLine("## Phase Plan")
                        appendLine()
                        appendLine("✅ Phases planned")
                        appendLine()
                        appendLine(phasePlanAnalysis)
                    }.renderMarkdown
                )
                phasePlanTask.update()
                transcriptStream?.write("## Phase Plan\n\n$phasePlanAnalysis\n\n---\n\n".toByteArray())

                // Extract milestones from phase plan
                extractMilestonesFromPhasePlan(phasePlanAnalysis, collectedMilestones)
            }

            // Section 8: Project Data JSON
            if (executionConfig.generate_project_data) {
                log.debug("Generating project data JSON")
                val projectDataTask = tabs.newTask("Project Data")

                val projectDataBuffer =
                    projectDataTask.add("## Project Data\n\n🔄 Generating structured project data...".renderMarkdown)
                task.update()

                // Generate detailed tasks and sprints
                val projectDataAnalysis = designAgent.answer(
                    listOf(
                        """
Generate a detailed breakdown of all project work items. For each item provide:

1. **Epics** (high-level features/initiatives)
   - ID (EPIC-XXX format)
   - Name
   - Description
   - Priority (Critical/High/Medium/Low)
   - Estimated story points

2. **User Stories/Tasks** (for each epic)
   - ID (TASK-XXX format)
   - Title
   - Description
   - Type (story/task/spike/bug)
   - Parent Epic ID
   - Priority
   - Story points (1, 2, 3, 5, 8, 13)
   - Acceptance criteria (list)
   - Labels/tags

3. **Sprint Assignments**
   Distribute tasks across $sprintCount sprints, each $sprintDuration weeks.
   Consider:
   - Dependencies between tasks
   - Team velocity (assume ~40 points per sprint)
   - Risk distribution

4. **Releases**
   - ID (REL-XXX format)
   - Version number
   - Target date
   - Included epics
   - Release notes summary

5. **Dependencies**
   List all dependencies between tasks, epics, and milestones.
   Format: SOURCE_ID blocks/depends_on/relates_to TARGET_ID

Provide comprehensive, realistic project breakdown.
                        """.trimIndent()
                    )
                )

                // Parse the analysis and build structured data
                parseProjectDataFromAnalysis(
                    projectDataAnalysis,
                    collectedEpics,
                    collectedTasks,
                    collectedMilestones,
                    collectedDependencies,
                    sprintCount,
                    sprintDuration
                )

                // Build sprints
                val sprints = buildSprints(collectedTasks, sprintCount, sprintDuration)

                // Build releases
                val releases = buildReleases(collectedEpics, sprints)

                // Create project data object
                val projectData = ProjectData(
                    project_name = projectName,
                    description = systemDescription,
                    created_date = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                    epics = collectedEpics,
                    releases = releases,
                    sprints = sprints,
                    tasks = collectedTasks,
                    milestones = collectedMilestones,
                    dependencies = collectedDependencies
                )

                // Write JSON file
                val jsonContent = JsonUtil.toJson(projectData)
                val jsonFileName = "${projectName.replace(" ", "_").lowercase()}_project_data.json"

                val jsonLink = task.saveFile(jsonFileName, jsonContent.toByteArray())
                projectDataBuffer?.setLength(0)
                projectDataBuffer?.append(
                    buildString {
                        appendLine("## Project Data")
                        appendLine()
                        appendLine("✅ Project data generated")
                        appendLine()
                        appendLine("### Summary")
                        appendLine()
                        appendLine("| Category | Count |")
                        appendLine("|----------|-------|")
                        appendLine("| Epics | ${collectedEpics.size} |")
                        appendLine("| Tasks | ${collectedTasks.size} |")
                        appendLine("| Sprints | ${sprints.size} |")
                        appendLine("| Releases | ${releases.size} |")
                        appendLine("| Milestones | ${collectedMilestones.size} |")
                        appendLine("| Dependencies | ${collectedDependencies.size} |")
                        appendLine()
                        appendLine("### Download")
                        appendLine()
                        appendLine("📥 [Download Project Data JSON]($jsonLink)")
                        appendLine()
                        appendLine("### Preview")
                        appendLine()
                        appendLine("```json")
                        appendLine(jsonContent.take(2000))
                        if (jsonContent.length > 2000) appendLine("... (truncated)")
                        appendLine("```")
                    }.renderMarkdown
                )
                projectDataTask.update()
                transcriptStream?.write("## Project Data\n\nGenerated JSON file: $jsonFileName\n\n```json\n$jsonContent\n```\n\n---\n\n".toByteArray())
            }

            // Final summary
            val duration = System.currentTimeMillis() - startTime
            log.info("SoftwareDesignDocumentTask completed: project='$projectName', duration=${duration}ms")

            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append(
                buildString {
                    appendLine("## ✅ Document Generation Complete")
                    appendLine()
                    appendLine("**Total Time:** ${duration / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Sections Generated:** ${
                            listOfNotNull(
                                if (executionConfig.generate_use_cases) "Use Cases" else null,
                                if (executionConfig.generate_requirements) "Requirements" else null,
                                if (executionConfig.generate_architecture) "Architecture" else null,
                                if (executionConfig.generate_data_model) "Data Model" else null,
                                if (executionConfig.generate_flow_diagrams) "Flow Diagrams" else null,
                                if (executionConfig.generate_test_plan) "Test Plan" else null,
                                if (executionConfig.generate_phase_plan) "Phase Plan" else null,
                                if (executionConfig.generate_project_data) "Project Data" else null
                            ).size
                        }"
                    )
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            overviewTask.update()
            transcriptStream?.write(
                "\n\n## Document Generation Complete\n\n**Total Time:** ${duration / 1000.0}s\n\n**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )
            task.update()

            val relativePath = "${
                projectName.replace(" ", "_").lowercase()
            }_design_document_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (transcriptLink, _) = Pair(task.linkTo(relativePath), task.resolveUserFile(relativePath))
            task.safeComplete(
                "Software design document generated in ${duration / 1000}s. " +
                        "View document: <a href='$transcriptLink' target='_blank'>markdown</a> " +
                        "<a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${transcriptLink.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>",
                log
            )

            val finalResult = buildString {
                appendLine("# Software Design Document: $projectName")
                appendLine()
                appendLine("**System:** ${systemDescription.truncateForDisplay(500)}")
                appendLine()
                appendLine("## Generated Sections")
                if (executionConfig.generate_use_cases) appendLine("- ✅ Use Cases & Actors")
                if (executionConfig.generate_requirements) appendLine("- ✅ Requirements Specification")
                if (executionConfig.generate_architecture) appendLine("- ✅ Architecture Diagrams")
                if (executionConfig.generate_data_model) appendLine("- ✅ Data Model & ERD")
                if (executionConfig.generate_flow_diagrams) appendLine("- ✅ Flow Diagrams")
                if (executionConfig.generate_test_plan) appendLine("- ✅ Test Plan")
                if (executionConfig.generate_phase_plan) appendLine("- ✅ Phase Planning")
                if (executionConfig.generate_project_data) appendLine("- ✅ Project Data JSON (${collectedTasks.size} tasks, ${collectedEpics.size} epics)")
            }
            resultFn(finalResult)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            transcriptStream?.write("\n\n## Error Occurred\n\n**Error:** ${e.message}\n\n**Type:** ${e.javaClass.simpleName}\n".toByteArray())
            log.error("SoftwareDesignDocumentTask failed after ${duration}ms for project: $projectName", e)
            task.error(e)

            if (overviewStatusBuffer != null) {
                overviewStatusBuffer.setLength(0)
                overviewStatusBuffer.append(
                    buildString {
                        appendLine("## ❌ Error Occurred")
                        appendLine()
                        appendLine("**Error:** ${e.message}")
                        appendLine()
                        appendLine("**Type:** ${e.javaClass.simpleName}")
                    }.renderMarkdown
                )
            } else {
                overviewTask.add(
                    buildString {
                        appendLine("## ❌ Error Occurred")
                        appendLine()
                        appendLine("**Error:** ${e.message}")
                        appendLine()
                        appendLine("**Type:** ${e.javaClass.simpleName}")
                    }.renderMarkdown
                )
            }
            overviewTask.update()
            task.update()

            resultFn("Error generating software design document: ${e.message}")
        } finally {
            transcriptStream?.flush()
            transcriptStream?.close()
        }
    }

    private fun buildDesignPrompt(
        projectName: String,
        systemDescription: String,
        targetAudience: String,
        stakeholders: List<String>,
        techStack: List<String>,
        constraints: List<String>,
        priorContext: String,
        inputFileContext: String
    ): String {
        return """
You are an expert software architect and technical writer. Your role is to create comprehensive software design documentation with detailed Mermaid diagrams.

## Project: $projectName

## System Description:
$systemDescription

## Target Audience:
$targetAudience

${if (stakeholders.isNotEmpty()) "## Stakeholders:\n${stakeholders.joinToString("\n") { "- $it" }}\n" else ""}

${if (techStack.isNotEmpty()) "## Technology Stack:\n${techStack.joinToString("\n") { "- $it" }}\n" else ""}

${if (constraints.isNotEmpty()) "## Constraints:\n${constraints.joinToString("\n") { "- $it" }}\n" else ""}

${if (priorContext.isNotBlank()) "## Context from Prior Tasks:\n$priorContext\n" else ""}

${if (inputFileContext.isNotBlank()) "## Input Files:\n$inputFileContext\n" else ""}

## Documentation Standards:
1. Use clear, professional technical writing
2. Include Mermaid diagrams for all visual representations
3. Use consistent ID formats (FR-XXX, UC-XXX, TC-XXX, etc.)
4. Provide actionable, specific details
5. Consider scalability, security, and maintainability
6. Include traceability between artifacts
7. Make all acceptance criteria testable

## Mermaid Diagram Types to Use:
- `graph TD/LR` for flowcharts and architecture
- `sequenceDiagram` for interactions
- `erDiagram` for data models
- `stateDiagram-v2` for state machines
- `gantt` for timelines
- `classDiagram` for class structures

Provide comprehensive, production-ready documentation.
        """.trimIndent()
    }

    private fun extractEpicsFromUseCases(analysis: String, epics: MutableList<Epic>) {
        // Extract use case groups as epics
        val ucPattern = "UC-\\d+".toRegex()
        val matches = ucPattern.findAll(analysis)
        val ucCount = matches.count()

        if (ucCount > 0) {
            epics.add(
                Epic(
                    id = "EPIC-UC",
                    name = "User Features",
                    description = "Core user-facing functionality based on use cases",
                    priority = "High",
                    story_points = ucCount * 5
                )
            )
        }
    }

    private fun extractMilestonesFromPhasePlan(analysis: String, milestones: MutableList<Milestone>) {
        val milestonePattern = "M\\d+:?\\s*([^|\\n]+)".toRegex()
        val matches = milestonePattern.findAll(analysis)

        matches.forEachIndexed { index, match ->
            milestones.add(
                Milestone(
                    id = "MS-${index + 1}",
                    name = match.groupValues[1].trim().take(50),
                    target_date = LocalDateTime.now().plusWeeks((index + 1) * 4L).format(DateTimeFormatter.ISO_DATE),
                    description = "Project milestone ${index + 1}",
                    deliverables = listOf("Phase ${index + 1} deliverables complete")
                )
            )
        }
    }

    private fun parseProjectDataFromAnalysis(
        analysis: String,
        epics: MutableList<Epic>,
        tasks: MutableList<Task>,
        milestones: MutableList<Milestone>,
        dependencies: MutableList<Dependency>,
        sprintCount: Int,
        sprintDuration: Int
    ) {
        // Parse EPIC patterns
        val epicPattern = "EPIC-(\\w+)".toRegex()
        epicPattern.findAll(analysis).forEach { match ->
            val epicId = match.value
            if (epics.none { it.id == epicId }) {
                epics.add(
                    Epic(
                        id = epicId,
                        name = "Epic $epicId",
                        description = "Auto-extracted epic from analysis",
                        priority = "Medium",
                        story_points = 13
                    )
                )
            }
        }

        // Parse TASK patterns
        val taskPattern = "TASK-(\\d+)".toRegex()
        taskPattern.findAll(analysis).forEach { match ->
            val taskId = match.value
            if (tasks.none { it.id == taskId }) {
                tasks.add(
                    Task(
                        id = taskId,
                        title = "Task $taskId",
                        description = "Auto-extracted task from analysis",
                        type = "task",
                        epic_id = epics.firstOrNull()?.id,
                        sprint_id = null,
                        priority = "Medium",
                        story_points = 3,
                        acceptance_criteria = listOf("Task completed successfully"),
                        labels = listOf("auto-generated")
                    )
                )
            }
        }

        // Parse dependency patterns
        val depPattern = "(TASK-\\d+|EPIC-\\w+)\\s+(blocks|depends_on|relates_to)\\s+(TASK-\\d+|EPIC-\\w+)".toRegex()
        depPattern.findAll(analysis).forEachIndexed { index, match ->
            dependencies.add(
                Dependency(
                    id = "DEP-${index + 1}",
                    source_id = match.groupValues[1],
                    source_type = if (match.groupValues[1].startsWith("EPIC")) "epic" else "task",
                    target_id = match.groupValues[3],
                    target_type = if (match.groupValues[3].startsWith("EPIC")) "epic" else "task",
                    dependency_type = match.groupValues[2]
                )
            )
        }

        // Ensure minimum tasks if none extracted
        if (tasks.isEmpty()) {
            val defaultTasks = listOf(
                "Project Setup", "Architecture Design", "Database Schema",
                "API Development", "Frontend Development", "Integration Testing",
                "Documentation", "Deployment Setup"
            )
            defaultTasks.forEachIndexed { index, title ->
                tasks.add(
                    Task(
                        id = "TASK-${index + 1}",
                        title = title,
                        description = "Default task: $title",
                        type = "task",
                        epic_id = epics.firstOrNull()?.id,
                        sprint_id = null,
                        priority = if (index < 3) "High" else "Medium",
                        story_points = listOf(2, 3, 5, 8).random(),
                        acceptance_criteria = listOf("$title completed"),
                        labels = listOf("default")
                    )
                )
            }
        }
    }

    private fun buildSprints(tasks: MutableList<Task>, sprintCount: Int, sprintDuration: Int): List<Sprint> {
        val sprints = mutableListOf<Sprint>()
        val tasksPerSprint = (tasks.size / sprintCount).coerceAtLeast(1)
        var taskIndex = 0
        val startDate = LocalDateTime.now()

        for (i in 1..sprintCount) {
            val sprintTasks = mutableListOf<String>()
            var sprintPoints = 0

            while (taskIndex < tasks.size && sprintPoints < 40 && sprintTasks.size < tasksPerSprint + 2) {
                val task = tasks[taskIndex]
                task.sprint_id = "SPRINT-$i"
                sprintTasks.add(task.id)
                sprintPoints += task.story_points ?: 3
                taskIndex++
            }

            val sprintStart = startDate.plusWeeks(((i - 1) * sprintDuration).toLong())
            val sprintEnd = sprintStart.plusWeeks(sprintDuration.toLong())

            sprints.add(
                Sprint(
                    id = "SPRINT-$i",
                    name = "Sprint $i",
                    number = i,
                    start_date = sprintStart.format(DateTimeFormatter.ISO_DATE),
                    end_date = sprintEnd.format(DateTimeFormatter.ISO_DATE),
                    goals = listOf("Complete sprint $i deliverables"),
                    capacity_points = 40,
                    task_ids = sprintTasks
                )
            )
        }

        return sprints
    }

    private fun buildReleases(epics: List<Epic>, sprints: List<Sprint>): List<Release> {
        val releases = mutableListOf<Release>()
        val sprintsPerRelease = (sprints.size / 2).coerceAtLeast(1)

        releases.add(
            Release(
                id = "REL-1",
                name = "MVP Release",
                version = "1.0.0",
                target_date = sprints.getOrNull(sprintsPerRelease - 1)?.end_date
                    ?: LocalDateTime.now().plusMonths(2).format(DateTimeFormatter.ISO_DATE),
                description = "Minimum Viable Product release with core functionality",
                epic_ids = epics.take(epics.size / 2).map { it.id }
            )
        )

        if (sprints.size > sprintsPerRelease) {
            releases.add(
                Release(
                    id = "REL-2",
                    name = "Feature Complete Release",
                    version = "1.1.0",
                    target_date = sprints.lastOrNull()?.end_date
                        ?: LocalDateTime.now().plusMonths(4).format(DateTimeFormatter.ISO_DATE),
                    description = "Full feature release with all planned functionality",
                    epic_ids = epics.map { it.id }
                )
            )
        }

        return releases
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .take(10) // Limit files
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = file.readText()
                "# $relativePath\n\n```\n${content.truncateForDisplay(1000)}\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun initializeTranscript(task: SessionTask, projectName: String): FileOutputStream? {
        return try {
            val relativePath = "${
                projectName.replace(" ", "_").lowercase()
            }_design_document_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, file) = Pair(task.linkTo(relativePath), task.resolveUserFile(relativePath))
            val transcriptStream = file?.outputStream()
            task.complete(
                "Writing document to <a href='$link' target='_blank'>$link</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
            )
            log.info("Initialized transcript file: $link")
            transcriptStream
        } catch (e: Exception) {
            log.error("Failed to initialize transcript", e)
            null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SoftwareDesignDocumentTask::class.java)
        val SoftwareDesignDocument = TaskType(
            "SoftwareDesignDocument",
            "Writing",
            SoftwareDesignDocumentTask::class.java,
            SoftwareDesignDocumentTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate comprehensive software design documentation",
            """
              Creates complete software design documentation with Mermaid diagrams.
              <ul>
                <li>Use case diagrams and actor documentation</li>
                <li>Functional and non-functional requirements</li>
                <li>Architecture diagrams (C4, component, deployment)</li>
                <li>Data model and ERD diagrams</li>
                <li>Sequence and activity flow diagrams</li>
                <li>Test plan and test case documentation</li>
                <li>Phase planning with Gantt charts</li>
                <li>Project data JSON with tasks, epics, sprints, releases</li>
                <li>All diagrams use Mermaid syntax</li>
              </ul>
            """,
        )
    }
}