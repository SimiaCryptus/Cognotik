# util.kt

Utility extension functions for working with chat messages in the `com.simiacryptus.cognotik.util` package.

## Overview

This file provides small Kotlin extension functions on `String` to simplify the creation of
`ModelSchema.ChatMessage` and `ModelSchema.ContentPart` instances, reducing boilerplate when
constructing chat messages from plain text.

## Package

```
package com.simiacryptus.cognotik.util
```

## Dependencies

```kotlin
import com.simiacryptus.cognotik.models.ModelSchema
```

## API

### `String.toContentList()`

```kotlin
fun String.toContentList() = listOf(this).map { ModelSchema.ContentPart(text = it) }
```

Converts a `String` into a `List<ModelSchema.ContentPart>` containing a single `ContentPart`
wrapping the string as its `text` value.

**Returns:** `List<ModelSchema.ContentPart>` — a single-element list.

**Example:**

```kotlin
val parts = "Hello, world!".toContentList()
// parts == listOf(ModelSchema.ContentPart(text = "Hello, world!"))
```

### `String.toChatMessage(role: ModelSchema.Role = ModelSchema.Role.user)`

```kotlin
fun String.toChatMessage(role: ModelSchema.Role = ModelSchema.Role.user) =
  ModelSchema.ChatMessage(role = role, content = toContentList())
```

Converts a `String` into a `ModelSchema.ChatMessage`, using `toContentList()` internally to
build the message content. Defaults to `ModelSchema.Role.user` if no role is specified.

**Parameters:**
- `role: ModelSchema.Role` — the role associated with the chat message (defaults to `user`).

**Returns:** `ModelSchema.ChatMessage` — a chat message with the given role and the string
wrapped as content.

**Example:**

```kotlin
val userMessage = "What is the capital of France?".toChatMessage()
// userMessage.role == ModelSchema.Role.user

val systemMessage = "You are a helpful assistant.".toChatMessage(ModelSchema.Role.system)
// systemMessage.role == ModelSchema.Role.system
```

## Usage Notes

- These extensions are intended to make it easy to quickly wrap plain strings as chat messages
  when interacting with APIs or components that expect `ModelSchema.ChatMessage` objects.
- Since `toChatMessage` relies on `toContentList`, any change to content wrapping logic should
  be made in `toContentList` to keep behavior consistent across both functions.