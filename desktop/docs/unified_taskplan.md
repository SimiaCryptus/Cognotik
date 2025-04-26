Homepage: Folder action launchpad

# Initialize session

The application will open `$domainName/#${folder.urlEncode()}` when invoked on a folder of interest.
(This folder should be used for the workingDir setting below)

## Tab 1 - PlanTask settings

Saved with POST to `http://localhost:7681/singleTask/settings`

```
sessionId: U-20250420-92wb
action: save
settings: {
  "defaultModel" : "GPT4o",
  "parsingModel" : "GPT4oMini",
  "shellCmd" : [ "bash" ],
  "temperature" : 0.2,
  "budget" : 2.0,
  "taskSettings" : {
    "SoftwareGraphPlanningTask" : {
      "task_type" : "SoftwareGraphPlanningTask",
      "enabled" : false
    },
    "SoftwareGraphModificationTask" : {
      "task_type" : "SoftwareGraphModificationTask",
      "enabled" : false
    },
    "SoftwareGraphGenerationTask" : {
      "task_type" : "SoftwareGraphGenerationTask",
      "enabled" : false
    },
    "DataTableCompilationTask" : {
      "task_type" : "DataTableCompilationTask",
      "enabled" : false
    },
    "CommandAutoFixTask" : {
      "task_type" : "CommandAutoFixTask",
      "enabled" : false
    },
    "InquiryTask" : {
      "task_type" : "InquiryTask",
      "enabled" : true
    },
    "FileSearchTask" : {
      "task_type" : "FileSearchTask",
      "enabled" : false
    },
    "CrawlerAgentTask" : {
      "task_type" : "CrawlerAgentTask",
      "enabled" : false
    },
    "EmbeddingSearchTask" : {
      "task_type" : "EmbeddingSearchTask",
      "enabled" : false
    },
    "FileModificationTask" : {
      "task_type" : "FileModificationTask",
      "enabled" : true
    },
    "RunShellCommandTask" : {
      "task_type" : "RunShellCommandTask",
      "enabled" : false
    },
    "ForeachTask" : {
      "task_type" : "ForeachTask",
      "enabled" : false
    },
    "TaskPlanningTask" : {
      "task_type" : "TaskPlanningTask",
      "enabled" : false
    },
    "GitHubSearchTask" : {
      "task_type" : "GitHubSearchTask",
      "enabled" : false
    },
    "KnowledgeIndexingTask" : {
      "task_type" : "KnowledgeIndexingTask",
      "enabled" : false
    },
    "SeleniumSessionTask" : {
      "task_type" : "SeleniumSessionTask",
      "enabled" : false
    },
    "CommandSessionTask" : {
      "task_type" : "CommandSessionTask",
      "enabled" : false
    }
  },
  "autoFix" : false,
  "env" : { },
  "workingDir" : ".",
  "language" : "bash",
  "maxTaskHistoryChars" : 10000,
  "maxTasksPerIteration" : 3,
  "maxIterations" : 10,
  "enableExpansionSyntax" : false
}
```

## Tab 2 - settings

POST to `http://localhost:7681/userSettings/`

```json
action: save
settings: {
  "apiKeys" : {
    "Google" : "",
    "OpenAI" : "...",
    "Anthropic" : "",
    "AWS" : "",
    "Groq" : "",
    "Perplexity" : "",
    "ModelsLab" : "",
    "Mistral" : "",
    "DeepSeek" : ""
  },
  "apiBase" : {
    "Google" : "https://generativelanguage.googleapis.com",
    "OpenAI" : "https://api.openai.com/v1",
    "Anthropic" : "https://api.anthropic.com/v1",
    "AWS" : "https://api.openai.aws",
    "Groq" : "https://api.groq.com/openai/v1",
    "Perplexity" : "https://api.perplexity.ai",
    "ModelsLab" : "https://modelslab.com/api/v6",
    "Mistral" : "https://api.mistral.ai/v1",
    "DeepSeek" : "https://api.deepseek.com"
  }
}
```

OK: Redirect to app session page e.g. `http://localhost:7681/autoPlan/#U-20250420-92wb`















