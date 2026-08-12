# AWS Bedrock

Chat access to models hosted on Amazon Bedrock via Cognotik's Bring-Your-Own-Key (BYOK) integration.

## Overview

The `BedrockProvider` in Cognotik exposes AWS Bedrock as a chat provider (`APIProvider` id `AWS`, API base
`https://api.openai.aws`). Unlike some other provider files, this integration does not define a fixed catalog of
model constants in code — it delegates chat requests through `AwsChatClient`, which is configured to talk to
whichever Bedrock-hosted model you specify at runtime. Supported capabilities are therefore determined by the
underlying model you select on AWS Bedrock (e.g. Anthropic Claude, Meta Llama, Amazon Titan, Mistral, etc.), not by
a Cognotik-defined list.

## Available Models

No hard-coded model constants, context windows, capability flags, or pricing tables are defined in
`BedrockProvider.kt`. Model selection and pricing are governed entirely by AWS Bedrock and the specific
foundation model you choose to invoke.

| Model Name | Context Window | Capabilities | Pricing |
|------------|-----------------|--------------|---------|
| — | — | — | — |

> Because this provider is a pass-through to AWS Bedrock, consult AWS's own model catalog and pricing page for the
> specific model you intend to use.

## Usage Example

```kotlin
val provider = APIProvider.AWS // BedrockProvider

// Model id is passed through to the underlying AwsChatClient / Bedrock API,
// e.g. an Anthropic model hosted on Bedrock:
val model = "anthropic.claude-3-sonnet-20240229-v1:0"
```

```json
{
  "provider": "AWS",
  "model": "anthropic.claude-3-sonnet-20240229-v1:0"
}
```

## Related Links

- [AWS Bedrock — Model Catalog](https://aws.amazon.com/bedrock/)
- [AWS Bedrock Pricing](https://aws.amazon.com/bedrock/pricing/)

Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with the
provider before relying on them for budgeting. In this case, no pricing is defined in Cognotik itself — always
check AWS Bedrock's pricing page directly for the model you use.