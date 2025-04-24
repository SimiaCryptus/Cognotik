# JOpenAI Core Module

This module provides core functionality for interacting with various AI models and services, primarily focusing on OpenAI-compatible APIs. It is designed as a foundational component within a larger project ecosystem.

## Features

- **API Client Management**: Robust HTTP client management with support for retries, timeouts, and error handling tailored for AI service interactions.
- **Model Definitions**: Comprehensive definitions and pricing models for a wide range of AI models including OpenAI, Anthropic, AWS, Google, Groq, Mistral, Perplexity, ModelsLab, and DeepSeek.
- **Audio Processing**: Utilities for audio recording, processing, and transcription including advanced silence discrimination and audio feature extraction.
- **Text Processing**: Efficient text compression and tokenization using GPT-4 compatible tokenizers and suffix arrays.
- **Dynamic Enum Support**: Flexible dynamic enumeration handling with JSON serialization/deserialization support.
- **Proxy and Reflection-Based API**: Dynamic proxy generation for API interfaces with validation and retry mechanisms.
- **Description Utilities**: Type describers for JSON, YAML, and TypeScript to generate API documentation and facilitate introspection.
- **Exception Handling**: Custom exceptions for AI service errors including rate limits, quota issues, moderation flags, and invalid models.
- **Optimization Tools**: Genetic prompt optimization utilities and distance metrics for embedding comparisons.

## Key Components

- `OpenAIClient` and `ChatClient`: Clients for making requests to AI APIs with built-in reliability and performance logging.
- `AudioPacket`, `AudioRecorder`, `DictationManager`: Classes for capturing and processing audio input.
- `GPT4Tokenizer`: Tokenizer compatible with GPT-4 and Codex models.
- `TextCompressor`: Utility for compressing text by identifying and abbreviating repeated subsequences.
- `DynamicEnum`: Base class for dynamic enumerations with JSON support.
- `TypeDescriber` and its implementations: Tools for generating structured descriptions of types and methods.
- `GPTProxyBase` and `ChatProxy`: Proxy-based API interface implementations with automatic JSON serialization and deserialization.
- `PercentileTool` and `TrainedSilenceDiscriminator`: Tools for statistical analysis and silence detection in audio streams.
- `PromptOptimization`: Framework for genetic algorithm-based prompt tuning.
