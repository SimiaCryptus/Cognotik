---
specifies: mcp_feature_plan.md
---

We need to add support for MCP-based tools provided by this application;
the application should provide an implementation of an MCPTool interface, and the LLM is then able to invoke it.
We are not designing the tools yet, just the plumbing.


* This requires speaking the actual MCP protocol (JSON-RPC over stdio/HTTP) as client and/or server; this is for interactive tool use by the llm. Merely emitting tool calls is useless to us and provides little to no additional utility.
* We want to support reasoning+mcp; Carry thinking blocks through the loop
* Concurrency and cancellation: We want to support this, but this can be a phase 2 for immediate followup after proving the simpler case
* `MCPToolProvider` instances should be provided as an optional collection in ChatRequest
* 


Plan out the change and note any questions/issues, including a specific list of code you were missing in this analysis, if any


related:
* [ChatClientInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ChatClientInterface.kt)
* [ChatModel.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ChatModel.kt)
* [ModelSchema.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ModelSchema.kt)
* [ChatClientBase.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/chat/ChatClientBase.kt)
* [AnthropicChatClient.kt](../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/AnthropicChatClient.kt)
* [ChatInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/ChatInterface.kt)
* [MetricsInterface.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/service/MetricsInterface.kt)
* [Metrics.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/Metrics.kt)
* [HttpClientManager.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/HttpClientManager.kt)
* [JsonUtil.kt](../lwcore/src/main/kotlin/com/simiacryptus/cognotik/util/JsonUtil.kt)
* [JsonDescriber.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/describe/JsonDescriber.kt)
* [ReasoningVariants.kt](src/main/kotlin/com/simiacryptus/cognotik/platform/model/ReasoningVariants.kt)
* [GeminiSdkChatClient.kt](../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/GeminiSdkChatClient.kt)
* [TypeDescriber.kt](../core/src/main/kotlin/com/simiacryptus/cognotik/describe/TypeDescriber.kt)

