# Image Generation Clients

This package provides a unified interface and implementations for generating images using various AI providers, specifically Google Gemini (Imagen) and OpenAI (DALL-E).

## Core Components

### [ImageClientInterface.kt](./ImageClientInterface.kt)
The base interface for all image generation clients. It defines two primary methods:
- `createImage(request: ImageGenerationRequest)`: Generates images based on the provided prompt and configuration.
- `getModels()`: Returns a list of supported image models for the client.

### [ImageModel.kt](./ImageModel.kt)
A data class representing an image generation model. It includes:
- `modelName`: The technical identifier for the API.
- `provider`: The `APIProvider` (e.g., OpenAI, Gemini).
- `maxPrompt`: Maximum allowed prompt length.
- `pricingFunction`: A logic block to calculate costs based on image dimensions and quality.

## Implementations

### [GeminiImageClient.kt](./GeminiImageClient.kt)
An implementation using the Google Gemini SDK.
- **Features**: Supports both standard Gemini API and Vertex AI (Google Cloud).
- **Models**: Integrated with Imagen 3 and Imagen 4.
- **Reliability**: Uses `HttpClientManager` for performance logging and reliability wrappers.

### [OpenAIImageClient.kt](./OpenAIImageClient.kt)
An implementation using OpenAI's REST API.
- **Features**: Supports DALL-E 2 and DALL-E 3.
- **Customization**: Allows setting custom API bases and handles authorization via the `APIProvider` pattern.

## Supported Models

### [Gemini Image Models](./GeminiImageModels.kt)
Provides access to Google's Imagen series:
- **Imagen 3**: Standard generation.
- **Imagen 4**: Includes Preview, Ultra, and Fast variants with different pricing tiers ($0.03 - $0.10 per image).

### [OpenAI Image Models](./OpenAIImageModels.kt)
Provides access to OpenAI's DALL-E series:
- **DALL-E 2**: Supports various sizes (256x256 to 1024x1024).
- **DALL-E 3**: Supports standard and HD quality, with landscape/portrait aspect ratios.

## Usage Tracking
Both clients support usage tracking via an `onUsage` callback, which provides information about the model used and the calculated cost based on the specific model's pricing logic.