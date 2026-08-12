# ModelsLab Models

  Access ModelsLab's chat-completion API through Cognotik using your own API key.

  ## Overview

  ModelsLab is supported in Cognotik as a chat-capable provider, exposed via `ModelsLabProvider`
  (`https://modelslab.com/api/v6`). The integration provides a chat client (`ModelsLabChatClient`) for
  conversational completions.

  ## Available Models

  No specific model constants, context window sizes, capability flags, or pricing tables are defined in the
  provided source file (`ModelsLabProvider.kt`). Model selection and configuration for ModelsLab are handled
  at the client/session level rather than as static model definitions in this file.

  | Model Name | Context Window | Capabilities | Pricing |
  |------------|-----------------|---------------|---------|
  | —          | —               | —             | —       |

  ## Pricing

  No pricing information (input/output token rates, cached or thinking token rates) is defined in this source
  file.

  > **Note:** Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates
  > with the provider before relying on them for budgeting.

  ## Usage Example

  ```kotlin
  val provider = ModelsLabProvider()
  val chatClient = provider.getChatClient(
      key = apiKey,
      workPool = workPool,
      logLevel = Level.INFO,
      logStreams = mutableListOf(),
      scheduledPool = scheduledPool,
      session = session,
  )
  ```

  ## Related Links

  - [ModelsLab API](https://modelslab.com/api/v6)