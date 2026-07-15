---
related:
- architecture_overview.md
- ../platform/platform.md
- ../taskplanning.md
- ../extend/extending.md
- ../extend/strategies.md
- ../cognitive/cognitive_modes.md
specifies: ../../site/cognotik.com/diagrams.html
---

# Cognotik Platform & Application Diagrams

This document provides a series of Mermaid diagrams illustrating the Cognotik platform architecture,
its major subsystems, and the flow of control and data through the system.

## 1. System Layer Overview

Cognotik is organized into distinct layers, each with a clear responsibility. Higher layers depend
on lower layers, but not vice versa.

```mermaid
graph TD
  App["Application Layer<br/>(ApplicationServer, SingleTaskApp)"]
  Cog["Cognitive Planning Layer<br/>(CognitiveMode, TaskType, OrchestrationConfig)"]
  Agent["Agent Layer<br/>(BaseAgent, ChatAgent, ParsedAgent, CodeAgent, ProxyAgent)"]
  Model["Model & Provider Layer<br/>(APIProvider, ChatModel, EmbeddingModel, ImageModel)"]
  Platform["Platform Layer<br/>(Session, User, Storage, Auth, ThreadPool)"]
  Web["Web / Transport Layer<br/>(Jetty Servlets, WebSocket, SocketManager, SessionTask)"]

  App --> Cog
  Cog --> Agent
  Agent --> Model
  Model --> Platform
  Platform --> Web

  style App fill:#4A90E2,color:#fff
  style Cog fill:#7ED321,color:#000
  style Agent fill:#F5A623,color:#000
  style Model fill:#BD10E0,color:#fff
  style Platform fill:#50E3C2,color:#000
  style Web fill:#9013FE,color:#fff
```

## 2. Component Interaction

This diagram shows how the major components interact at runtime, from the user's browser
down through the platform services.

```mermaid
graph TD
  User["User (Browser)"]

  subgraph WebLayer["Web / Transport Layer"]
      Servlets["Jetty Servlets"]
      WS["WebSocket / SocketManager"]
      Task["SessionTask (UI)"]
  end

  subgraph AppLayer["Application Layer"]
      App["ApplicationServer / SingleTaskApp"]
      Proxy["SessionProxyServer"]
  end

  subgraph CognitiveLayer["Cognitive Planning Layer"]
      Mode["CognitiveMode"]
      Orchestrator["Orchestrator"]
      Tasks["TaskType Registry"]
  end

  subgraph AgentLayer["Agent Layer"]
      Agents["Agents (Chat/Parsed/Code/Proxy)"]
      Describer["TypeDescriber"]
  end

  subgraph ModelLayer["Model & Provider Layer"]
      Providers["APIProvider"]
      Models["ChatModel / EmbeddingModel / ImageModel"]
  end

  subgraph PlatformLayer["Platform Layer"]
      Storage["Storage / Metadata"]
      Auth["Auth Manager"]
      Pools["Thread Pool Manager"]
      Settings["User Settings"]
  end

  User --> Servlets
  User <--> WS
  Servlets --> App
  WS --> App
  App --> Proxy
  App --> Mode
  Mode --> Orchestrator
  Orchestrator --> Tasks
  Tasks --> Agents
  Agents --> Describer
  Agents --> Models
  Models --> Providers
  Task --> WS
  App --> Storage
  App --> Auth
  App --> Pools
  Agents --> Settings

  style WebLayer fill:#4A90E2,color:#fff
  style CognitiveLayer fill:#7ED321,color:#000
  style AgentLayer fill:#F5A623,color:#000
  style ModelLayer fill:#BD10E0,color:#fff
  style PlatformLayer fill:#50E3C2,color:#000
```

## 3. Agent Layer Type Hierarchy

All LLM interaction flows through `BaseAgent<I, R>`, where `I` is the input type and `R` is
the result type. The following diagram shows the agent inheritance and their input/output types.

```mermaid
classDiagram
  class BaseAgent~I, R~ {
      +ChatInterface model
      +double temperature
      +String prompt
      +String name
      +respond(input, messages) R
      +answer(input) R
      +chatMessages(input) ChatMessage[]
      +withModel(model) BaseAgent
  }

  class ChatAgent {
      Input: List~String~
      Output: String
  }
  class ParsedAgent~T~ {
      Input: List~String~
      Output: ParsedResponse~T~
  }
  class CodeAgent {
      Input: CodeRequest
      Output: CodeResult
  }
  class ImageGenerationAgent {
      Input: List~String~
      Output: ImageAndText
  }
  class ImageProcessingAgent {
      Input: List~ImageAndText~
      Output: ImageAndText
  }
  class ParsedImageAgent~T~ {
      Input: List~ImageAndText~
      Output: ParsedResponse~T~
  }

  BaseAgent <|-- ChatAgent
  BaseAgent <|-- ParsedAgent
  BaseAgent <|-- CodeAgent
  BaseAgent <|-- ImageGenerationAgent
  BaseAgent <|-- ImageProcessingAgent
  BaseAgent <|-- ParsedImageAgent

  note for BaseAgent "ProxyAgent~T~ does NOT extend BaseAgent.<br/>It creates a dynamic proxy for an interface."
```

## 4. The Planning Cycle

The cognitive planning layer decomposes high-level goals into executable tasks. This sequence
diagram shows the general planning cycle used by cognitive modes.

```mermaid
sequenceDiagram
  participant User
  participant Mode as CognitiveMode
  participant LLM as LLM (Planner)
  participant Task as TaskType (Instance)
  participant Transcript as Audit Transcript

  User->>Mode: Enters Goal
  loop Cognitive Cycle
      Mode->>LLM: Context (Goal + Task Descriptions + History)
      LLM-->>Mode: Plan (JSON: TaskType + Config)
      Mode->>Transcript: Log Plan
      Mode->>Task: Instantiate(TaskExecutionConfig)
      Mode->>Task: run()
      Task-->>Mode: resultFn("Summary of work done")
      Mode->>Mode: Update State (Prune Tokens)
  end
  Mode->>User: Task Complete
```

## 5. Cognitive Modes Overview

Cognotik provides several cognitive modes, each representing a different strategy for how the
AI thinks, plans, and executes tasks.

```mermaid
graph TD
  Root["CognitiveMode"]

  subgraph Conversational["Conversational Modes"]
      Chat["Conversational<br/>(one task per message)"]
      Persona["Persona Chat<br/>(specialized strategy)"]
      Coding["Coding<br/>(REPL assistant)"]
  end

  subgraph Planning["Planning & Execution Modes"]
      Waterfall["Waterfall<br/>(plan-ahead, review)"]
      Adaptive["Adaptive Planning<br/>(think-act-reflect)"]
      Hierarchical["Hierarchical<br/>(goal tree)"]
  end

  subgraph Advanced["Advanced Orchestration Modes"]
      Council["Council<br/>(multi-persona voting)"]
      Protocol["Protocol<br/>(state machine + referee)"]
      Parallel["Parallel<br/>(batch processing)"]
  end

  Root --> Conversational
  Root --> Planning
  Root --> Advanced

  style Root fill:#4A90E2,color:#fff
  style Conversational fill:#7ED321,color:#000
  style Planning fill:#F5A623,color:#000
  style Advanced fill:#BD10E0,color:#fff
```

## 6. Adaptive Planning Mode: Think-Act-Reflect Loop

The Adaptive Planning Mode operates as an autonomous agent in a cyclical loop.

```mermaid
stateDiagram-v2
  [*] --> Initialize
  Initialize --> Think: Initial understanding of goal
  Think --> Act: Nominate up to 5 tasks
  Act --> Reflect: Execute tasks in parallel
  Reflect --> Think: Update reasoning state
  Reflect --> Done: Goal achieved
  Done --> [*]

  note right of Think
      Updates Reasoning State:
      Goals, Facts, Hypotheses
  end note
  note right of Act
      Executes tasks using
      selected strategies
  end note
  note right of Reflect
      Analyzes results and
      updates the plan
  end note
```

## 7. Platform Layer: Storage Structure

The platform provides file-based storage with a well-defined directory structure for
global sessions, user sessions, and user settings.

```mermaid
graph TD
  Data["data/"]
  Global["global/<br/>(Global sessions)"]
  UserSessions["user-sessions/<br/>(User sessions)"]
  Users["users/<br/>(User settings)"]

  Data --> Global
  Data --> UserSessions
  Data --> Users

  Global --> GDate["2023-12-15/"]
  GDate --> GId["AbC1/"]
  GId --> GMsg["messages/"]
  GId --> GConfig["config.json"]

  UserSessions --> UEmail["user@example.com/"]
  UEmail --> UDate["2023-12-15/"]
  UDate --> UId["XyZ2/"]

  Users --> USettings["user@example.com.json"]

  style Data fill:#50E3C2,color:#000
  style Global fill:#4A90E2,color:#fff
  style UserSessions fill:#4A90E2,color:#fff
  style Users fill:#4A90E2,color:#fff
```

## 8. Session ID Format

Sessions are uniquely identified and can be either global or user-specific.

```mermaid
graph LR
  Session["Session ID"]
  Global["Global Session<br/>G-YYYY-MM-DD-XXXX<br/>(accessible to all)"]
  UserS["User Session<br/>U-YYYY-MM-DD-XXXX<br/>(user-specific)"]
  Legacy["Legacy Format<br/>YYYY-MM-DD-XXXX<br/>(treated as global)"]

  Session --> Global
  Session --> UserS
  Session --> Legacy

  style Session fill:#4A90E2,color:#fff
  style Global fill:#7ED321,color:#000
  style UserS fill:#F5A623,color:#000
  style Legacy fill:#9B9B9B,color:#fff
```

## 9. Extensibility Model: DynamicEnum Pattern

Cognotik's extension system is built on the `DynamicEnum` pattern, providing a runtime-extensible
alternative to Java enums.

```mermaid
graph TD
  DynamicEnum["DynamicEnum&lt;T&gt;<br/>(Registration, Lookup, Serialization)"]

  Providers["APIProvider<br/>(OpenAI, Anthropic, Gemini, ...)"]
  Tools["ToolProvider<br/>(Git, Python, Docker, ...)"]
  Runtimes["CodeRuntimes<br/>(Kotlin, Python, Node, ...)"]
  Modes["CognitiveModeType<br/>(Chat, Adaptive, Waterfall, ...)"]
  Schemas["CognitiveSchemaStrategy<br/>(ProjectManager, Scientist, ...)"]
  Tasks["TaskType<br/>(FileModification, RunCode, ...)"]

  DynamicEnum --> Providers
  DynamicEnum --> Tools
  DynamicEnum --> Runtimes
  DynamicEnum --> Modes
  DynamicEnum --> Schemas
  DynamicEnum --> Tasks

  style DynamicEnum fill:#4A90E2,color:#fff
  style Providers fill:#7ED321,color:#000
  style Tools fill:#7ED321,color:#000
  style Runtimes fill:#7ED321,color:#000
  style Modes fill:#F5A623,color:#000
  style Schemas fill:#F5A623,color:#000
  style Tasks fill:#F5A623,color:#000
```

## 10. Multiplicative Strategy Scaling

Orthogonal strategy families combine multiplicatively, so adding one strategy expands
capability across all others.

```mermaid
graph LR
  subgraph Dimensions["Orthogonal Strategy Families"]
      P["API Providers<br/>(8 options)"]
      M["Chat Models<br/>(12 options)"]
      T["Task Types<br/>(26 options)"]
      Pr["Processing<br/>(5 options)"]
  end

  Combos["Total Combinations<br/>8 × 12 × 26 × 5<br/>= 12,480 configurations"]

  P --> Combos
  M --> Combos
  T --> Combos
  Pr --> Combos

  style P fill:#4A90E2,color:#fff
  style M fill:#7ED321,color:#000
  style T fill:#F5A623,color:#000
  style Pr fill:#BD10E0,color:#fff
  style Combos fill:#50E3C2,color:#000
```

## 11. Web Request Routing

The `SessionProxyServer` acts as a router, dispatching incoming requests to the correct
`ApplicationServer` instance based on session ID.

```mermaid
sequenceDiagram
  participant Browser
  participant Proxy as SessionProxyServer
  participant App as ApplicationServer
  participant Socket as SocketManager
  participant Task as SessionTask

  Browser->>Proxy: Request (with session ID)
  Proxy->>Proxy: Lookup chats[session]
  Proxy->>App: Dispatch to registered app
  Browser<<->>Socket: WebSocket connection
  Socket->>App: Route incoming events
  App->>Task: Update UI block
  Task-->>Socket: Push HTML/Markdown
  Socket-->>Browser: Server-driven UI update
```

## 12. IO Discipline: The Four Audiences

A defining architectural convention is that code communicates with four distinct audiences
simultaneously, each with its own output channel and format.

```mermaid
graph TD
  Code["Application Code"]

  User["User<br/>Channel: task.ui<br/>Format: HTML (Markdown)<br/>Purpose: Real-time feedback"]
  Auditor["Auditor<br/>Channel: task.transcript()<br/>Format: Markdown + details<br/>Purpose: Audit trail"]
  Developer["Developer<br/>Channel: SLF4J log<br/>Format: Single-line text<br/>Purpose: System health"]
  LLM["LLM<br/>Channel: resultFn() / context<br/>Format: Concise Markdown<br/>Purpose: Inter-agent context"]

  Code --> User
  Code --> Auditor
  Code --> Developer
  Code --> LLM

  style Code fill:#4A90E2,color:#fff
  style User fill:#7ED321,color:#000
  style Auditor fill:#F5A623,color:#000
  style Developer fill:#BD10E0,color:#fff
  style LLM fill:#50E3C2,color:#000
```

## 13. DocProcessor Pipeline

The DocProcessor is a frontmatter-driven documentation-as-specification pipeline that keeps
docs and source code in sync.

```mermaid
graph TD
  MD["Markdown File<br/>(with YAML frontmatter)"]
  Parse["Parse Frontmatter"]
  Vars["Substitute Template Variables"]

  subgraph Specs["Specification Types"]
      Specifies["specifies<br/>(doc drives code)"]
      Documents["documents<br/>(code drives doc)"]
      Transforms["transforms<br/>(regex mapping)"]
      Generates["generates<br/>(explicit outputs)"]
  end

  Resolve["Resolve Targets<br/>(glob expansion)"]
  Sort["Topological Sort<br/>(dependency ordering)"]
  Execute["Execute Modification Tasks"]
  Status["Update docops.status.json"]

  MD --> Parse
  Parse --> Vars
  Vars --> Specs
  Specifies --> Resolve
  Documents --> Resolve
  Transforms --> Resolve
  Generates --> Resolve
  Resolve --> Sort
  Sort --> Execute
  Execute --> Status

  style MD fill:#4A90E2,color:#fff
  style Specs fill:#F5A623,color:#000
  style Execute fill:#7ED321,color:#000
  style Status fill:#50E3C2,color:#000
```

## 14. PatchProcessor Strategy Selection

PatchProcessor provides multiple matching strategies optimized for different use cases.

```mermaid
graph TD
  Patch["PatchProcessor"]

  Fuzzy["Fuzzy<br/>(balanced default)"]
  Strict["Strict<br/>(exact matching only)"]
  Lenient["Lenient<br/>(maximum flexibility)"]
  Python["Python<br/>(indentation-aware)"]
  Thermo["Thermodynamic<br/>(physics-based)"]
  Full["FullReplacement<br/>(complete rewrite)"]

  Patch --> Fuzzy
  Patch --> Strict
  Patch --> Lenient
  Patch --> Python
  Patch --> Thermo
  Patch --> Full

  style Patch fill:#4A90E2,color:#fff
  style Fuzzy fill:#7ED321,color:#000
  style Strict fill:#F5A623,color:#000
  style Lenient fill:#F5A623,color:#000
  style Python fill:#BD10E0,color:#fff
  style Thermo fill:#9013FE,color:#fff
  style Full fill:#9B9B9B,color:#fff
```

## 15. FuzzyPatchMatcher Multi-Phase Algorithm

The primary patch matcher uses a sophisticated multi-phase matching algorithm.

```mermaid
graph TD
  Start["Source + Patch Lines"]
  Phase1["Phase 1: Unique Line Matching<br/>(high-confidence anchors)"]
  Phase2["Phase 2: Adjacent Line Propagation<br/>(expand from anchors)"]
  Phase3["Phase 3: Recursive Subsequence Linking<br/>(process remaining gaps)"]
  Fuzzy["Fuzzy Matching<br/>(Levenshtein + structural checks)"]
  Move["Move Detection<br/>(delete + add)"]
  Truncate["Context Truncation<br/>(collapse with ...)"]
  Annihilate["No-op Annihilation<br/>(remove redundant pairs)"]
  Result["Final Diff / Applied Patch"]

  Start --> Phase1
  Phase1 --> Phase2
  Phase2 --> Phase3
  Phase3 --> Fuzzy
  Fuzzy --> Move
  Move --> Truncate
  Truncate --> Annihilate
  Annihilate --> Result

  style Start fill:#4A90E2,color:#fff
  style Phase1 fill:#7ED321,color:#000
  style Phase2 fill:#7ED321,color:#000
  style Phase3 fill:#7ED321,color:#000
  style Result fill:#50E3C2,color:#000
```

## 16. Crawler Task Pipeline

The web crawler combines seed, fetch, and processing strategies in a continuation loop.

```mermaid
graph TD
  Start["Start Crawler Task"]

  subgraph Seeding["Phase 1: Seeding"]
      SelectSeed["Select Seed Strategy"]
      GetSeeds["Get Initial URLs"]
  end

  subgraph Fetching["Phase 2: Fetching"]
      SelectFetch["Select Fetch Strategy<br/>(Selenium / HttpClient)"]
      Fetch["Fetch Content"]
  end

  subgraph Processing["Phase 3: Processing"]
      SelectProcess["Select Processing Strategy"]
      Process["Process Page + Extract Data"]
  end

  subgraph Continuation["Phase 4: Continuation"]
      Decision{Should Continue?}
      Finalize["Generate Final Output"]
  end

  Start --> SelectSeed
  SelectSeed --> GetSeeds
  GetSeeds --> SelectFetch
  SelectFetch --> Fetch
  Fetch --> SelectProcess
  SelectProcess --> Process
  Process --> Decision
  Decision -->|Yes| Fetch
  Decision -->|No| Finalize

  style Start fill:#4A90E2,color:#fff
  style Seeding fill:#7ED321,color:#000
  style Fetching fill:#F5A623,color:#000
  style Processing fill:#BD10E0,color:#fff
  style Continuation fill:#50E3C2,color:#000
```

## 17. Building Applications

Developers build on Cognotik in two primary ways, both of which use the same session
launch mechanism.

```mermaid
graph TD
  Dev["Developer"]

  GenApp["General Application<br/>(extends ApplicationServer)"]
  SingleApp["Single-Task Application<br/>(extends SingleTaskApp)"]

  Launch["Session Launch Mechanism"]
  CreateSession["Create Session"]
  MapStorage["Map Storage"]
  Instantiate["Instantiate App"]
  Register["Register with SessionProxyServer"]
  Browser["Open Browser to Session URL"]

  Dev --> GenApp
  Dev --> SingleApp
  GenApp --> Launch
  SingleApp --> Launch
  Launch --> CreateSession
  CreateSession --> MapStorage
  MapStorage --> Instantiate
  Instantiate --> Register
  Register --> Browser

  style Dev fill:#4A90E2,color:#fff
  style GenApp fill:#7ED321,color:#000
  style SingleApp fill:#F5A623,color:#000
  style Launch fill:#50E3C2,color:#000
```

## 18. Document Reader Hierarchy

The document reading module provides a unified interface for extracting text and rendering
images from various document formats.

```mermaid
classDiagram
  class DocumentReader {
      <<interface>>
      +getText() String
      +close()
  }
  class PaginatedDocumentReader {
      <<interface>>
      +getPageCount() int
      +getText(startPage, endPage) String
  }
  class RenderableDocumentReader {
      <<interface>>
      +getPageCount() int
      +renderImage(pageIndex, dpi) BufferedImage
  }

  DocumentReader <|-- PaginatedDocumentReader
  DocumentReader <|-- RenderableDocumentReader

  PaginatedDocumentReader <|.. PDFReader
  RenderableDocumentReader <|.. PDFReader
  PaginatedDocumentReader <|.. HTMLReader
  PaginatedDocumentReader <|.. TextReader

  DocumentReader <|.. DocxReader
  DocumentReader <|.. XlsxReader
  DocumentReader <|.. PptxReader
  DocumentReader <|.. EmlReader
  DocumentReader <|.. OdtReader
  DocumentReader <|.. RtfReader
```

## 19. Task Status Lifecycle

The DocProcessor tracks the status of each target generation task through a defined lifecycle.

```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> RUNNING: Task begins
  RUNNING --> COMPLETED: Success
  RUNNING --> FAILED: Error occurs
  RUNNING --> CANCELLED: User cancels
  COMPLETED --> [*]
  FAILED --> [*]
  CANCELLED --> [*]

  note right of RUNNING
      Status written atomically to
      docops.status.json
      (temp file then rename)
  end note
```

## 20. End-to-End Request Flow

This diagram ties together the full flow from a user goal to task execution and result delivery.

```mermaid
sequenceDiagram
  participant User
  participant Web as Web/Transport Layer
  participant App as Application Layer
  participant Mode as Cognitive Mode
  participant Agent as Agent Layer
  participant Model as Model/Provider
  participant Platform as Platform Layer

  User->>Web: Submit goal via browser
  Web->>App: Route request (SessionProxyServer)
  App->>Platform: Load session/user context
  App->>Mode: Initialize cognitive mode
  Mode->>Agent: Request plan generation
  Agent->>Model: Send prompt (via APIProvider)
  Model-->>Agent: LLM response
  Agent-->>Mode: Structured plan/result
  Mode->>Mode: Execute tasks (loop)
  Mode->>Platform: Persist results
  Mode-->>App: Task complete
  App-->>Web: Push UI updates (SessionTask)
  Web-->>User: Server-driven UI (WebSocket)
```