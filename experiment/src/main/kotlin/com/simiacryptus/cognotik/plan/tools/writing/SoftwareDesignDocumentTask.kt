package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SoftwareDesignDocumentTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SoftwareDesignDocumentTaskExecutionConfigData?
) : AbstractTask<SoftwareDesignDocumentTask.SoftwareDesignDocumentTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class SoftwareDesignDocumentTaskExecutionConfigData(
    @Description("The name/title of the software project")
    var project_name: String? = null,
    @Description("High-level description of the software system to design")
    var system_description: String? = null,
    @Description("Target audience for the software (e.g., 'enterprise users', 'mobile consumers')")
    var target_audience: String? = null,
    @Description("Key stakeholders and their roles")
    var stakeholders: List<String>? = null,
    @Description("Whether to generate use case diagrams and documentation")
    var generate_use_cases: Boolean = true,
    @Description("Whether to generate functional and non-functional requirements")
    var generate_requirements: Boolean = true,
    @Description("Whether to generate architectural diagrams (C4, component, deployment)")
    var generate_architecture: Boolean = true,
    @Description("Whether to generate data model and ERD diagrams")
    var generate_data_model: Boolean = true,
    @Description("Whether to generate sequence and activity diagrams for key flows")
    var generate_flow_diagrams: Boolean = true,
    @Description("Whether to generate test plan and test case documentation")
    var generate_test_plan: Boolean = true,
    @Description("Whether to generate phase planning with milestones")
    var generate_phase_plan: Boolean = true,
    @Description("Whether to generate the project data JSON file with tasks, epics, sprints, etc.")
    var generate_project_data: Boolean = true,
    @Description("Number of sprints to plan (default: 6)")
    var sprint_count: Int = 6,
    @Description("Sprint duration in weeks (default: 2)")
    var sprint_duration_weeks: Int = 2,
    @Description("Technology stack constraints or preferences")
    var technology_stack: List<String>? = null,
    @Description("Known constraints or limitations")
    var constraints: List<String>? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for context")
    var related_files: List<String>? = null,
    @Description("Description of the task")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
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
    @Description("The name of the project")
    var project_name: String = "",
    @Description("High-level description of the project")
    var description: String = "",
    @Description("ISO date-time when the project data was created")
    var created_date: String = "",
    @Description("List of epics in the project")
    var epics: List<Epic> = emptyList(),
    @Description("List of planned releases")
    var releases: List<Release> = emptyList(),
    @Description("List of sprints in the project")
    var sprints: List<Sprint> = emptyList(),
    @Description("List of tasks in the project")
    var tasks: List<Task> = emptyList(),
    @Description("List of project milestones")
    var milestones: List<Milestone> = emptyList(),
    @Description("List of dependencies between project items")
    var dependencies: List<Dependency> = emptyList()
  )

  data class Epic(
    @Description("Unique identifier for the epic (e.g., EPIC-001)")
    var id: String = "",
    @Description("Name of the epic")
    var name: String = "",
    @Description("Description of the epic's scope and goals")
    var description: String = "",
    @Description("Priority level: Critical, High, Medium, or Low")
    var priority: String = "Medium",
    @Description("Current status: Planned, In Progress, or Done")
    var status: String = "Planned",
    @Description("Estimated story points for the epic")
    var story_points: Int? = null
  )

  data class Release(
    @Description("Unique identifier for the release (e.g., REL-001)")
    var id: String = "",
    @Description("Name of the release")
    var name: String = "",
    @Description("Semantic version number (e.g., 1.0.0)")
    var version: String = "",
    @Description("Target release date in ISO format")
    var target_date: String = "",
    @Description("Description of the release scope")
    var description: String = "",
    @Description("List of epic IDs included in this release")
    var epic_ids: List<String> = emptyList(),
    @Description("Current status: Planned, In Progress, or Released")
    var status: String = "Planned"
  )

  data class Sprint(
    @Description("Unique identifier for the sprint (e.g., SPRINT-1)")
    var id: String = "",
    @Description("Name of the sprint")
    var name: String = "",
    @Description("Sprint number in sequence")
    var number: Int = 0,
    @Description("Sprint start date in ISO format")
    var start_date: String = "",
    @Description("Sprint end date in ISO format")
    var end_date: String = "",
    @Description("List of sprint goals")
    var goals: List<String> = emptyList(),
    @Description("Total capacity in story points")
    var capacity_points: Int = 0,
    @Description("List of task IDs assigned to this sprint")
    var task_ids: List<String> = emptyList(),
    @Description("Current status: Planned, Active, or Completed")
    var status: String = "Planned"
  )

  data class Task(
    @Description("Unique identifier for the task (e.g., TASK-001)")
    var id: String = "",
    @Description("Title of the task")
    var title: String = "",
    @Description("Detailed description of the task")
    var description: String = "",
    @Description("Type of task: Feature, Bug, Chore, or Spike")
    var type: String = "Feature",
    @Description("ID of the parent epic, if any")
    var epic_id: String? = null,
    @Description("ID of the sprint this task is assigned to, if any")
    var sprint_id: String? = null,
    @Description("Priority level: Low, Medium, High, or Critical")
    var priority: String = "Medium",
    @Description("Estimated story points (1, 2, 3, 5, 8, 13)")
    var story_points: Int? = null,
    @Description("Current status: Backlog, To Do, In Progress, Done")
    var status: String = "Backlog",
    @Description("List of acceptance criteria for the task")
    var acceptance_criteria: List<String>? = null,
    @Description("Labels or tags for categorization")
    var labels: List<String>? = null
  )

  data class Milestone(
    @Description("Unique identifier for the milestone (e.g., MS-1)")
    var id: String = "",
    @Description("Name of the milestone")
    var name: String = "",
    @Description("Target date in ISO format")
    var target_date: String = "",
    @Description("Description of the milestone")
    var description: String = "",
    @Description("List of deliverables expected at this milestone")
    var deliverables: List<String> = emptyList(),
    @Description("Current status: Planned, Reached, or Missed")
    var status: String = "Planned"
  )

  data class Dependency(
    @Description("Unique identifier for the dependency (e.g., DEP-1)")
    var id: String = "",
    @Description("ID of the source item")
    var source_id: String = "",
    @Description("Type of the source item: task, epic, or milestone")
    var source_type: String = "",
    @Description("ID of the target item")
    var target_id: String = "",
    @Description("Type of the target item: task, epic, or milestone")
    var target_type: String = "",
    @Description("Type of dependency: blocks, depends_on, or relates_to")
    var dependency_type: String = ""
  )

  override fun promptSegment(): String = buildString {
    appendLine("SoftwareDesignDocument - Generate comprehensive software design documentation")
    appendLine("  ** Specify the project name and system description")
    appendLine("  ** Generate use case diagrams and actor documentation")
    appendLine("  ** Create functional and non-functional requirements")
    appendLine("  ** Produce architectural diagrams (C4, component, deployment)")
    appendLine("  ** Design data models with ERD diagrams")
    appendLine("  ** Create sequence and activity diagrams for key flows")
    appendLine("  ** Generate test plans and test case documentation")
    appendLine("  ** Plan development phases with milestones")
    appendLine("  ** Output project data JSON with tasks, epics, sprints, releases")
    appendLine("  ** All diagrams use Mermaid syntax for easy rendering")
    appendLine("  ** Useful for:")
    appendLine("     - Project kickoff documentation")
    appendLine("     - Technical specification creation")
    appendLine("     - Sprint and release planning")
    appendLine("     - Stakeholder communication")
    appendLine("     - Development team onboarding")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {

    val config = executionConfig ?: run {
      val errorMsg = "CONFIGURATION ERROR: No execution config provided"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }
    val projectName = config.project_name ?: "Unnamed Project"
    log.info("Task 'SoftwareDesignDocument' started for project: $projectName")
    val startTime = System.currentTimeMillis()

    val systemDescription = config.system_description
    if (systemDescription.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No system description specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val tabs = TabbedDisplay(task)
    val overviewTask = tabs.newTask("Overview")

    task.pool.submit {
      val transcript = task.newUserFileStream(transcriptFile())
      try {
        val api = orchestrationConfig.defaultSmart.getChildClient(task)
        overviewTask.header("Software Design Document: $projectName")
        val checklist = mutableMapOf<String, String>()
        fun updateChecklist() {
          val content = buildString {
            appendLine("### Generation Progress")
            checklist.forEach { (name, status) -> appendLine("- $status $name") }
          }
          overviewTask.add(content.renderMarkdown(true))
        }

        if (config.generate_use_cases) checklist["Use Cases & Actors"] = "⏳"
        if (config.generate_requirements) checklist["Requirements Specification"] = "⏳"
        if (config.generate_architecture) checklist["Architecture Diagrams"] = "⏳"
        if (config.generate_data_model) checklist["Data Model & ERD"] = "⏳"
        if (config.generate_flow_diagrams) checklist["Flow Diagrams"] = "⏳"
        if (config.generate_test_plan) checklist["Test Plan"] = "⏳"
        if (config.generate_phase_plan) checklist["Phase Planning"] = "⏳"
        if (config.generate_project_data) checklist["Project Data JSON"] = "⏳"


        val targetAudience = config.target_audience ?: "general users"

        overviewTask.add(
          buildString {
            appendLine("**System:** ${systemDescription.take(200)}${if (systemDescription.length > 200) "..." else ""}")
            appendLine()
            appendLine("**Target Audience:** $targetAudience")
          }.renderMarkdown(true)
        )
        val statusBuffer = overviewTask.add("**Status:** 🔄 Gathering context...".renderMarkdown(true))
        updateChecklist()

        transcript?.write(
          buildString {
            appendLine("# Software Design Document: $projectName")
            appendLine()
            appendLine("**System:** $systemDescription")
            appendLine()
            appendLine(
              "**Generated:** ${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
              }"
            )
            appendLine()
            appendLine("---")
            appendLine()
          }.toByteArray()
        )
        task.update()

        // Gather context
        log.debug("Gathering context from prior tasks and input files")
        val priorContext = getPriorCode(agent.executionState)
        val inputFileContext = getInputFileCode()
        transcript?.write(
          buildString {
            appendLine("<details>")
            appendLine("<summary>Input Context Data</summary>")
            appendLine()
            appendLine("### Prior Task Context")
            appendLine(priorContext)
            appendLine()
            appendLine("### Input File Context")
            appendLine(inputFileContext)
            appendLine()
            appendLine("</details>")
          }.toByteArray()
        )

        // Initialize design agent
        log.info("Initializing software design agent")
        val designAgent = ChatAgent(
          prompt = buildDesignPrompt(
            projectName = projectName,
            systemDescription = systemDescription,
            targetAudience = targetAudience,
            stakeholders = config.stakeholders ?: emptyList(),
            techStack = config.technology_stack ?: emptyList(),
            constraints = config.constraints ?: emptyList(),
            priorContext = priorContext,
            inputFileContext = inputFileContext
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
        if (config.generate_use_cases) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Generating Use Cases...".renderMarkdown(true))
          log.debug("Generating use cases and actor documentation")
          val useCaseTask = tabs.newTask("Use Cases")

          val useCaseBuffer =
            useCaseTask.add("## Use Cases\n\n🔄 Analyzing actors and use cases...".renderMarkdown(true))
          task.update()

          val useCaseAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate comprehensive use case documentation:")
                appendLine()
                appendLine("1. **Actor Identification**")
                appendLine("   - List all actors (users, systems, external services)")
                appendLine("   - Describe each actor's role and goals")
                appendLine("   - Identify actor relationships")
                appendLine()
                appendLine("2. **Use Case Catalog**")
                appendLine("   For each major use case:")
                appendLine("   - UC-ID and Name")
                appendLine("   - Primary Actor")
                appendLine("   - Preconditions")
                appendLine("   - Main Success Scenario (numbered steps)")
                appendLine("   - Alternative Flows")
                appendLine("   - Postconditions")
                appendLine("   - Business Rules")
                appendLine()
                appendLine("3. **Use Case Diagram** (Mermaid)")
                appendLine("   Create a use case diagram showing actors and their interactions with the system.")
                appendLine("   Use this format:")
                appendLine("   ```mermaid")
                appendLine("   graph LR")
                appendLine("       subgraph Actors")
                appendLine("           A1[Actor 1]")
                appendLine("           A2[Actor 2]")
                appendLine("       end")
                appendLine("       subgraph System")
                appendLine("           UC1((Use Case 1))")
                appendLine("           UC2((Use Case 2))")
                appendLine("       end")
                appendLine("       A1 --> UC1")
                appendLine("       A1 --> UC2")
                appendLine("       A2 --> UC2")
                appendLine("   ```")
                appendLine()
                appendLine("4. **Actor-Use Case Matrix**")
                appendLine("   Show which actors participate in which use cases.")
                appendLine()
                appendLine("Provide detailed, actionable use case documentation.")
              }
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
            }.renderMarkdown(true)
          )
          useCaseTask.update()
          transcript?.write("## Use Cases & Actors\n\n$useCaseAnalysis\n\n---\n\n".toByteArray())

          // Extract epics from use cases
          extractEpicsFromUseCases(useCaseAnalysis, collectedEpics)
          checklist["Use Cases & Actors"] = "✅"
          updateChecklist()
        }

        // Section 2: Requirements
        if (config.generate_requirements) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Defining Requirements...".renderMarkdown(true))
          log.debug("Generating requirements specification")
          val requirementsTask = tabs.newTask("Requirements")

          val requirementsBuffer =
            requirementsTask.add(
              "## Requirements\n\n🔄 Defining functional and non-functional requirements...".renderMarkdown(
                true
              )
            )
          task.update()

          val requirementsAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate comprehensive requirements documentation:")
                appendLine()
                appendLine("1. **Functional Requirements**")
                appendLine("   For each requirement:")
                appendLine("   - FR-ID: Unique identifier")
                appendLine("   - Description: Clear, testable statement")
                appendLine("   - Priority: Must Have / Should Have / Could Have / Won't Have (MoSCoW)")
                appendLine("   - Source: Which use case or stakeholder")
                appendLine("   - Acceptance Criteria: Specific, measurable criteria")
                appendLine()
                appendLine("2. **Non-Functional Requirements**")
                appendLine("   Categories to cover:")
                appendLine("   - Performance (response times, throughput)")
                appendLine("   - Scalability (users, data volume)")
                appendLine("   - Security (authentication, authorization, data protection)")
                appendLine("   - Reliability (uptime, recovery)")
                appendLine("   - Usability (accessibility, UX standards)")
                appendLine("   - Maintainability (code quality, documentation)")
                appendLine("   - Compatibility (browsers, devices, integrations)")
                appendLine()
                appendLine("3. **Requirements Traceability Matrix**")
                appendLine("   Show relationships between:")
                appendLine("   - Use Cases → Requirements")
                appendLine("   - Requirements → Test Cases (placeholder IDs)")
                appendLine()
                appendLine("4. **Requirements Dependency Diagram** (Mermaid)")
                appendLine("   ```mermaid")
                appendLine("   graph TD")
                appendLine("       FR1[FR-001: User Login] --> FR2[FR-002: Session Management]")
                appendLine("       FR2 --> FR3[FR-003: Access Control]")
                appendLine("       NFR1[NFR-001: Response Time] -.-> FR1")
                appendLine("   ```")
                appendLine()
                appendLine("Provide detailed, prioritized requirements.")
              }
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
            }.renderMarkdown(true)
          )
          requirementsTask.update()
          transcript?.write("## Requirements Specification\n\n$requirementsAnalysis\n\n---\n\n".toByteArray())

          checklist["Requirements Specification"] = "✅"
          updateChecklist()
        }

        // Section 3: Architecture
        if (config.generate_architecture) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Designing Architecture...".renderMarkdown(true))
          log.debug("Generating architectural diagrams")
          val architectureTask = tabs.newTask("Architecture")

          val architectureBuffer =
            architectureTask.add("## Architecture\n\n🔄 Designing system architecture...".renderMarkdown(true))
          task.update()

          val architectureAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate comprehensive architecture documentation:")
                appendLine()
                appendLine("1. **System Context Diagram (C4 Level 1)**")
                appendLine("   Show the system and its relationships with users and external systems.")
                appendLine("   ```mermaid")
                appendLine("   graph TB")
                appendLine("       subgraph External")
                appendLine("           U[Users]")
                appendLine("           ES1[External System 1]")
                appendLine("       end")
                appendLine("       S[System Name]")
                appendLine("       U --> S")
                appendLine("       S --> ES1")
                appendLine("   ```")
                appendLine()
                appendLine("2. **Container Diagram (C4 Level 2)**")
                appendLine("   Show high-level technology choices and container responsibilities.")
                appendLine("   ```mermaid")
                appendLine("   graph TB")
                appendLine("       subgraph System")
                appendLine("           WA[Web Application<br/>React]")
                appendLine("           API[API Server<br/>Node.js]")
                appendLine("           DB[(Database<br/>PostgreSQL)]")
                appendLine("           CACHE[(Cache<br/>Redis)]")
                appendLine("       end")
                appendLine("       WA --> API")
                appendLine("       API --> DB")
                appendLine("       API --> CACHE")
                appendLine("   ```")
                appendLine()
                appendLine("3. **Component Diagram (C4 Level 3)**")
                appendLine("   For key containers, show internal components.")
                appendLine()
                appendLine("4. **Deployment Diagram**")
                appendLine("   Show infrastructure and deployment topology.")
                appendLine("   ```mermaid")
                appendLine("   graph TB")
                appendLine("       subgraph Cloud Provider")
                appendLine("           subgraph Production")
                appendLine("               LB[Load Balancer]")
                appendLine("               subgraph App Tier")
                appendLine("                   APP1[App Server 1]")
                appendLine("                   APP2[App Server 2]")
                appendLine("               end")
                appendLine("               subgraph Data Tier")
                appendLine("                   DB1[(Primary DB)]")
                appendLine("                   DB2[(Replica DB)]")
                appendLine("               end")
                appendLine("           end")
                appendLine("       end")
                appendLine("       LB --> APP1")
                appendLine("       LB --> APP2")
                appendLine("       APP1 --> DB1")
                appendLine("       APP2 --> DB1")
                appendLine("       DB1 --> DB2")
                appendLine("   ```")
                appendLine()
                appendLine("5. **Technology Stack Summary**")
                appendLine("   - Frontend technologies")
                appendLine("   - Backend technologies")
                appendLine("   - Data storage")
                appendLine("   - Infrastructure")
                appendLine("   - DevOps tools")
                appendLine()
                appendLine("6. **Architecture Decision Records (ADRs)**")
                appendLine("   Document key architectural decisions with:")
                appendLine("   - Context")
                appendLine("   - Decision")
                appendLine("   - Consequences")
                appendLine()
                appendLine("Provide detailed architecture documentation with all diagrams.")
              }
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
            }.renderMarkdown(true)
          )
          architectureTask.update()
          transcript?.write("## System Architecture\n\n$architectureAnalysis\n\n---\n\n".toByteArray())

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
          checklist["Architecture Diagrams"] = "✅"
          updateChecklist()
        }

        // Section 4: Data Model
        if (config.generate_data_model) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Designing Data Model...".renderMarkdown(true))
          log.debug("Generating data model and ERD")
          val dataModelTask = tabs.newTask("Data Model")

          val dataModelBuffer =
            dataModelTask.add("## Data Model\n\n🔄 Designing data structures...".renderMarkdown(true))
          task.update()

          val dataModelAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate comprehensive data model documentation:")
                appendLine()
                appendLine("1. **Entity-Relationship Diagram**")
                appendLine("   ```mermaid")
                appendLine("   erDiagram")
                appendLine("       USER ||--o{ ORDER : places")
                appendLine("       USER {")
                appendLine("           int id PK")
                appendLine("           string email UK")
                appendLine("           string name")
                appendLine("           datetime created_at")
                appendLine("       }")
                appendLine("       ORDER ||--|{ ORDER_ITEM : contains")
                appendLine("       ORDER {")
                appendLine("           int id PK")
                appendLine("           int user_id FK")
                appendLine("           decimal total")
                appendLine("           string status")
                appendLine("           datetime created_at")
                appendLine("       }")
                appendLine("       ORDER_ITEM {")
                appendLine("           int id PK")
                appendLine("           int order_id FK")
                appendLine("           int product_id FK")
                appendLine("           int quantity")
                appendLine("           decimal price")
                appendLine("       }")
                appendLine("       PRODUCT ||--o{ ORDER_ITEM : \"ordered in\"")
                appendLine("       PRODUCT {")
                appendLine("           int id PK")
                appendLine("           string name")
                appendLine("           string description")
                appendLine("           decimal price")
                appendLine("           int stock")
                appendLine("       }")
                appendLine("   ```")
                appendLine()
                appendLine("2. **Entity Descriptions**")
                appendLine("   For each entity:")
                appendLine("   - Purpose and business meaning")
                appendLine("   - Attributes with types and constraints")
                appendLine("   - Relationships and cardinality")
                appendLine("   - Indexes and performance considerations")
                appendLine()
                appendLine("3. **Data Dictionary**")
                appendLine("   | Entity | Attribute | Type | Constraints | Description |")
                appendLine("   |--------|-----------|------|-------------|-------------|")
                appendLine("   | User | id | INT | PK, AUTO | Unique identifier |")
                appendLine()
                appendLine("4. **Data Flow Diagram**")
                appendLine("   Show how data moves through the system.")
                appendLine()
                appendLine("5. **Data Validation Rules**")
                appendLine("   Business rules for data integrity.")
                appendLine()
                appendLine("6. **Data Migration Considerations**")
                appendLine("   If migrating from existing systems.")
                appendLine()
                appendLine("Provide complete data model documentation.")
              }
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
            }.renderMarkdown(true)
          )
          dataModelTask.update()
          transcript?.write("## Data Model & ERD\n\n$dataModelAnalysis\n\n---\n\n".toByteArray())
          checklist["Data Model & ERD"] = "✅"
          updateChecklist()
        }

        // Section 5: Flow Diagrams
        if (config.generate_flow_diagrams) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Mapping Flow Diagrams...".renderMarkdown(true))
          log.debug("Generating sequence and activity diagrams")
          val flowTask = tabs.newTask("Flow Diagrams")

          val flowBuffer = flowTask.add("## Flow Diagrams\n\n🔄 Mapping system flows...".renderMarkdown(true))
          task.update()

          val flowAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate flow diagrams for key system interactions:")
                appendLine()
                appendLine("1. **Sequence Diagrams**")
                appendLine("   For 3-5 critical user journeys, create sequence diagrams:")
                appendLine("   ```mermaid")
                appendLine("   sequenceDiagram")
                appendLine("       participant U as User")
                appendLine("       participant W as Web App")
                appendLine("       participant A as API Server")
                appendLine("       participant D as Database")
                appendLine("       ")
                appendLine("       U->>W: Login Request")
                appendLine("       W->>A: POST /auth/login")
                appendLine("       A->>D: Query User")
                appendLine("       D-->>A: User Data")
                appendLine("       A->>A: Validate Credentials")
                appendLine("       A->>A: Generate JWT")
                appendLine("       A-->>W: JWT Token")
                appendLine("       W-->>U: Login Success")
                appendLine("   ```")
                appendLine()
                appendLine("2. **Activity Diagrams**")
                appendLine("   For complex business processes:")
                appendLine("   ```mermaid")
                appendLine("   graph TD")
                appendLine("       A[Start] --> B{User Authenticated?}")
                appendLine("       B -->|Yes| C[Load Dashboard]")
                appendLine("       B -->|No| D[Show Login]")
                appendLine("       D --> E[Enter Credentials]")
                appendLine("       E --> F{Valid?}")
                appendLine("       F -->|Yes| C")
                appendLine("       F -->|No| G[Show Error]")
                appendLine("       G --> D")
                appendLine("       C --> H[End]")
                appendLine("   ```")
                appendLine()
                appendLine("3. **State Diagrams**")
                appendLine("   For entities with complex state transitions:")
                appendLine("   ```mermaid")
                appendLine("   stateDiagram-v2")
                appendLine("       [*] --> Draft")
                appendLine("       Draft --> Submitted: Submit")
                appendLine("       Submitted --> UnderReview: Assign Reviewer")
                appendLine("       UnderReview --> Approved: Approve")
                appendLine("       UnderReview --> Rejected: Reject")
                appendLine("       Rejected --> Draft: Revise")
                appendLine("       Approved --> [*]")
                appendLine("   ```")
                appendLine()
                appendLine("4. **Integration Flow Diagrams**")
                appendLine("   Show data flow between systems.")
                appendLine()
                appendLine("5. **Error Handling Flows**")
                appendLine("   Document how errors propagate and are handled.")
                appendLine()
                appendLine("Provide detailed flow documentation for all critical paths.")
              }
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
            }.renderMarkdown(true)
          )
          flowTask.update()
          transcript?.write("## Flow Diagrams\n\n$flowAnalysis\n\n---\n\n".toByteArray())
          checklist["Flow Diagrams"] = "✅"
          updateChecklist()
        }

        // Section 6: Test Plan
        if (config.generate_test_plan) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Creating Test Plan...".renderMarkdown(true))
          log.debug("Generating test plan")
          val testPlanTask = tabs.newTask("Test Plan")

          val testPlanBuffer = testPlanTask.add("## Test Plan\n\n🔄 Creating test strategy...".renderMarkdown(true))
          task.update()

          val testPlanAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate comprehensive test plan documentation:")
                appendLine()
                appendLine("1. **Test Strategy Overview**")
                appendLine("   - Testing objectives")
                appendLine("   - Testing scope (in-scope/out-of-scope)")
                appendLine("   - Testing approach")
                appendLine("   - Entry/Exit criteria")
                appendLine()
                appendLine("2. **Test Levels**")
                appendLine("   - Unit Testing: Coverage targets, frameworks")
                appendLine("   - Integration Testing: API testing, component integration")
                appendLine("   - System Testing: End-to-end scenarios")
                appendLine("   - Acceptance Testing: UAT criteria")
                appendLine()
                appendLine("3. **Test Case Catalog**")
                appendLine("   | TC-ID | Requirement | Description | Steps | Expected Result | Priority |")
                appendLine("   |-------|-------------|-------------|-------|-----------------|----------|")
                appendLine("   | TC-001 | FR-001 | User login with valid credentials | 1. Navigate to login... | User is authenticated | High |")
                appendLine()
                appendLine("4. **Test Coverage Matrix**")
                appendLine("   ```mermaid")
                appendLine("   graph LR")
                appendLine("       subgraph Requirements")
                appendLine("           FR1[FR-001]")
                appendLine("           FR2[FR-002]")
                appendLine("           FR3[FR-003]")
                appendLine("       end")
                appendLine("       subgraph Test Cases")
                appendLine("           TC1[TC-001]")
                appendLine("           TC2[TC-002]")
                appendLine("           TC3[TC-003]")
                appendLine("           TC4[TC-004]")
                appendLine("       end")
                appendLine("       FR1 --> TC1")
                appendLine("       FR1 --> TC2")
                appendLine("       FR2 --> TC3")
                appendLine("       FR3 --> TC4")
                appendLine("   ```")
                appendLine()
                appendLine("5. **Non-Functional Test Cases**")
                appendLine("   - Performance test scenarios")
                appendLine("   - Security test scenarios")
                appendLine("   - Usability test scenarios")
                appendLine()
                appendLine("6. **Test Environment Requirements**")
                appendLine("   - Hardware/software requirements")
                appendLine("   - Test data requirements")
                appendLine("   - Tool requirements")
                appendLine()
                appendLine("7. **Test Schedule**")
                appendLine("   Timeline for test phases.")
                appendLine()
                appendLine("8. **Risk Assessment**")
                appendLine("   Testing risks and mitigation strategies.")
                appendLine()
                appendLine("Provide actionable test documentation.")
              }
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
            }.renderMarkdown(true)
          )
          testPlanTask.update()
          transcript?.write("## Test Plan\n\n$testPlanAnalysis\n\n---\n\n".toByteArray())

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
          checklist["Test Plan"] = "✅"
          updateChecklist()
        }

        // Section 7: Phase Planning
        if (config.generate_phase_plan) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Planning Phases...".renderMarkdown(true))
          log.debug("Generating phase plan")
          val phasePlanTask = tabs.newTask("Phase Plan")

          val phasePlanBuffer =
            phasePlanTask.add("## Phase Plan\n\n🔄 Planning development phases...".renderMarkdown(true))
          task.update()
          val sprintCount = config.sprint_count
          val sprintDuration = config.sprint_duration_weeks

          val phasePlanAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate development phase planning:")
                appendLine()
                appendLine("1. **Project Timeline Overview**")
                appendLine("   ```mermaid")
                appendLine("   gantt")
                appendLine("       title Project Timeline")
                appendLine("       dateFormat  YYYY-MM-DD")
                appendLine("       section Phase 1: Foundation")
                appendLine("       Architecture Setup    :a1, 2024-01-01, 2w")
                appendLine("       Core Infrastructure   :a2, after a1, 2w")
                appendLine("       section Phase 2: Core Features")
                appendLine("       User Management       :b1, after a2, 3w")
                appendLine("       Core Business Logic   :b2, after b1, 4w")
                appendLine("       section Phase 3: Integration")
                appendLine("       External Integrations :c1, after b2, 3w")
                appendLine("       API Development       :c2, after b2, 3w")
                appendLine("       section Phase 4: Polish")
                appendLine("       UI/UX Refinement      :d1, after c1, 2w")
                appendLine("       Performance Tuning    :d2, after c2, 2w")
                appendLine("       section Phase 5: Launch")
                appendLine("       UAT                   :e1, after d1, 2w")
                appendLine("       Production Deployment :e2, after e1, 1w")
                appendLine("   ```")
                appendLine()
                appendLine("2. **Phase Descriptions**")
                appendLine("   For each phase:")
                appendLine("   - Phase name and duration")
                appendLine("   - Objectives and deliverables")
                appendLine("   - Key activities")
                appendLine("   - Dependencies")
                appendLine("   - Success criteria")
                appendLine("   - Risks and mitigations")
                appendLine()
                appendLine("3. **Milestone Schedule**")
                appendLine("   | Milestone | Target Date | Deliverables | Success Criteria |")
                appendLine("   |-----------|-------------|--------------|------------------|")
                appendLine("   | M1: Architecture Complete | Week 4 | Architecture docs, infra setup | All diagrams approved |")
                appendLine()
                appendLine("4. **Resource Allocation**")
                appendLine("   Team structure and responsibilities per phase.")
                appendLine()
                appendLine("5. **Sprint Planning Overview**")
                appendLine("   For $sprintCount sprints of $sprintDuration weeks each:")
                appendLine("   - Sprint goals")
                appendLine("   - Capacity planning")
                appendLine("   - Key deliverables")
                appendLine()
                appendLine("6. **Release Plan**")
                appendLine("   - Release versions and dates")
                appendLine("   - Features per release")
                appendLine("   - Release criteria")
                appendLine()
                appendLine("7. **Risk Timeline**")
                appendLine("   When risks are highest and mitigation windows.")
                appendLine()
                appendLine("Provide detailed phase planning with realistic timelines.")
              }
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
            }.renderMarkdown(true)
          )
          phasePlanTask.update()
          transcript?.write("## Phase Plan\n\n$phasePlanAnalysis\n\n---\n\n".toByteArray())

          // Extract milestones from phase plan
          extractMilestonesFromPhasePlan(phasePlanAnalysis, collectedMilestones)
          checklist["Phase Planning"] = "✅"
          updateChecklist()
        }

        // Section 8: Project Data JSON
        if (config.generate_project_data) {
          statusBuffer?.setLength(0)
          statusBuffer?.append("**Status:** 🔄 Generating Project Data...".renderMarkdown(true))
          log.debug("Generating project data JSON")
          val projectDataTask = tabs.newTask("Project Data")

          val projectDataBuffer =
            projectDataTask.add("## Project Data\n\n🔄 Generating structured project data...".renderMarkdown(true))
          task.update()
          val sprintCount = config.sprint_count
          val sprintDuration = config.sprint_duration_weeks

          // Generate detailed tasks and sprints
          val projectDataAnalysis = designAgent.answer(
            listOf(


              buildString {
                appendLine("Generate a detailed breakdown of all project work items. For each item provide:")
                appendLine()
                appendLine("1. **Epics** (high-level features/initiatives)")
                appendLine("   - ID (EPIC-XXX format)")
                appendLine("   - Name")
                appendLine("   - Description")
                appendLine("   - Priority (Critical/High/Medium/Low)")
                appendLine("   - Estimated story points")
                appendLine()
                appendLine("2. **User Stories/Tasks** (for each epic)")
                appendLine("   - ID (TASK-XXX format)")
                appendLine("   - Title")
                appendLine("   - Description")
                appendLine("   - Type (story/task/spike/bug)")
                appendLine("   - Parent Epic ID")
                appendLine("   - Priority")
                appendLine("   - Story points (1, 2, 3, 5, 8, 13)")
                appendLine("   - Acceptance criteria (list)")
                appendLine("   - Labels/tags")
                appendLine()
                appendLine("3. **Sprint Assignments**")
                appendLine("   Distribute tasks across $sprintCount sprints, each $sprintDuration weeks.")
                appendLine("   Consider:")
                appendLine("   - Dependencies between tasks")
                appendLine("   - Team velocity (assume ~40 points per sprint)")
                appendLine("   - Risk distribution")
                appendLine()
                appendLine("4. **Releases**")
                appendLine("   - ID (REL-XXX format)")
                appendLine("   - Version number")
                appendLine("   - Target date")
                appendLine("   - Included epics")
                appendLine("   - Release notes summary")
                appendLine()
                appendLine("5. **Dependencies**")
                appendLine("   List all dependencies between tasks, epics, and milestones.")
                appendLine("   Format: SOURCE_ID blocks/depends_on/relates_to TARGET_ID")
                appendLine()
                appendLine("Provide comprehensive, realistic project breakdown.")
              }
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
          val baseFileName = getOutputFile(".md")?.let {
            if (it.endsWith(".md")) it.removeSuffix(".md") else null
          }
          val jsonFileName = baseFileName?.let { "${it}.project_data.json" }
            ?: "${projectName.replace(" ", "_").lowercase()}_project_data.json"

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
            }.renderMarkdown(true)
          )
          projectDataTask.update()
          transcript?.write(
            buildString {
              appendLine("## Project Data")
              appendLine("Generated JSON file: $jsonFileName")
              appendLine("<details>")
              appendLine("<summary>Raw JSON Content</summary>")
              appendLine()
              appendLine("```json")
              appendLine(jsonContent)
              appendLine("```")
              appendLine()
              appendLine("</details>")
            }.toByteArray()
          )
          checklist["Project Data JSON"] = "✅"
          updateChecklist()
        }

        // Final summary
        val duration = System.currentTimeMillis() - startTime
        log.info("SoftwareDesignDocumentTask completed: project='$projectName', duration=${duration}ms")

        statusBuffer?.setLength(0)
        statusBuffer?.append(
          buildString {
            appendLine("---")
            appendLine("### ✅ Document Generation Complete")
            appendLine("**Total Time:** ${duration / 1000.0}s | **Tasks:** ${collectedTasks.size} | **Epics:** ${collectedEpics.size}")
          }.renderMarkdown(true)
        )
        overviewTask.complete()
        task.update()

        val transcriptPath = transcriptFile()
        val transcriptLink = task.linkTo(transcriptPath)
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
          if (config.generate_use_cases) appendLine("- ✅ Use Cases & Actors")
          if (config.generate_requirements) appendLine("- ✅ Requirements Specification")
          if (config.generate_architecture) appendLine("- ✅ Architecture Diagrams")
          if (config.generate_data_model) appendLine("- ✅ Data Model & ERD")
          if (config.generate_flow_diagrams) appendLine("- ✅ Flow Diagrams")
          if (config.generate_test_plan) appendLine("- ✅ Test Plan")
          if (config.generate_phase_plan) appendLine("- ✅ Phase Planning")
          if (config.generate_project_data) appendLine("- ✅ Project Data JSON (${collectedTasks.size} tasks, ${collectedEpics.size} epics)")
        }
        resultFn(finalResult)

      } catch (e: Exception) {
        // Triple Log Rule: UI, SLF4J, Transcript
        task.error(e)
        log.error("SoftwareDesignDocumentTask failed for project: $projectName", e)
        transcript?.write(
          buildString {
            appendLine("## Error")
            appendLine("<details>")
            appendLine("<summary>Stack Trace</summary>")
            appendLine()
            appendLine("```")
            appendLine(e.stackTraceToString())
            appendLine("```")
            appendLine()
            appendLine("</details>")
          }.toByteArray()
        )
        overviewTask.add(
          buildString {
            appendLine("## ❌ Error Occurred")
            appendLine()
            appendLine("**Error:** ${e.message}")
            appendLine()
            appendLine("**Type:** ${e.javaClass.simpleName}")
          }.renderMarkdown(true)
        )
        overviewTask.update()
        task.update()

        resultFn("Error generating software design document: ${e.message}")
      } finally {
        transcript?.flush()
        transcript?.close()
      }
    }
  }

  private fun buildDesignPrompt(
    projectName: String,
    systemDescription: String,
    targetAudience: String,
    stakeholders: List<String> = emptyList(),
    techStack: List<String> = emptyList(),
    constraints: List<String> = emptyList(),
    priorContext: String,
    inputFileContext: String


  ): String = buildString {
    appendLine("You are an expert software architect and technical writer. Your role is to create comprehensive software design documentation with detailed Mermaid diagrams.")
    appendLine()
    appendLine("## Project: $projectName")
    appendLine()
    appendLine("## System Description:")
    appendLine(systemDescription)
    appendLine()
    appendLine("## Target Audience:")
    appendLine(targetAudience)
    appendLine()
    if (stakeholders.isNotEmpty()) {
      appendLine("## Stakeholders:")
      stakeholders.forEach { appendLine("- $it") }
      appendLine()
    }
    if (techStack.isNotEmpty()) {
      appendLine("## Technology Stack:")
      techStack.forEach { appendLine("- $it") }
      appendLine()
    }
    if (constraints.isNotEmpty()) {
      appendLine("## Constraints:")
      constraints.forEach { appendLine("- $it") }
      appendLine()
    }
    if (priorContext.isNotBlank()) {
      appendLine("## Context from Prior Tasks:")
      appendLine(priorContext)
      appendLine()
    }
    if (inputFileContext.isNotBlank()) {
      appendLine("## Input Files:")
      appendLine(inputFileContext)
      appendLine()
    }
    appendLine("## Documentation Standards:")
    appendLine("1. Use clear, professional technical writing")
    appendLine("2. Include Mermaid diagrams for all visual representations")
    appendLine("3. Use consistent ID formats (FR-XXX, UC-XXX, TC-XXX, etc.)")
    appendLine("4. Provide actionable, specific details")
    appendLine("5. Consider scalability, security, and maintainability")
    appendLine("6. Include traceability between artifacts")
    appendLine("7. Make all acceptance criteria testable")
    appendLine()
    appendLine("## Mermaid Diagram Types to Use:")
    appendLine("- `graph TD/LR` for flowcharts and architecture")
    appendLine("- `sequenceDiagram` for interactions")
    appendLine("- `erDiagram` for data models")
    appendLine("- `stateDiagram-v2` for state machines")
    appendLine("- `gantt` for timelines")
    appendLine("- `classDiagram` for class structures")
    appendLine()
    appendLine("Provide comprehensive, production-ready documentation.")
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

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isIgnored(it.toPath()) -> false
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

  companion object {
    private val log: Logger = getLogger(SoftwareDesignDocumentTask::class.java)

    @JvmStatic
    val SoftwareDesignDocument = TaskType(
      name = "SoftwareDesignDocument",
      category = "Writing",
      taskClass = SoftwareDesignDocumentTask::class.java,
      executionConfigClass = SoftwareDesignDocumentTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate comprehensive software design documentation",
      tooltipHtml = "Creates complete software design documentation with Mermaid diagrams." +
          "<ul>" +
          "<li>Use case diagrams and actor documentation</li>" +
          "<li>Functional and non-functional requirements</li>" +
          "<li>Architecture diagrams (C4, component, deployment)</li>" +
          "<li>Data model and ERD diagrams</li>" +
          "<li>Sequence and activity flow diagrams</li>" +
          "<li>Test plan and test case documentation</li>" +
          "<li>Phase planning with Gantt charts</li>" +
          "<li>Project data JSON with tasks, epics, sprints, releases</li>" +
          "<li>All diagrams use Mermaid syntax</li>" +
          "</ul>",
    )
  }
}