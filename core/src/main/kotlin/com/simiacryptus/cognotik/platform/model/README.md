# Cognotik Platform Model

This module contains core domain model classes used throughout the Cognotik platform, providing
foundational types for session management and user identity.

## Overview

### `Session`

The `Session` class represents a unique session identifier used to track and scope platform activity.
Sessions come in two flavors:

- **Global sessions** — prefixed with `G-`, representing shared/global scope.
- **User sessions** — prefixed with `U-`, representing a scope tied to a specific user.

#### Key Features

- **Validation**: Every `Session` instance validates its `sessionId` against the pattern
  `([GU]-)?\d{8}-[\w+.\-]{4,12}` upon construction, ensuring IDs are well-formed. The special
  `Session.NULL` instance bypasses validation (used as a sentinel/placeholder value).
- **Conversion**: `isGlobal()` checks whether a session is global, and `toGlobal()` converts a
  user session into its corresponding global session (or returns itself if already global).
- **ID Generation**:
  - `Session.newGlobalID()` — creates a new global session ID using today's date and a random suffix.
  - `Session.newUserID()` — creates a new user session ID using today's date and a random suffix.
  - `Session.long64()` — generates a random Base64-encoded 64-bit value, made URL/filesystem-safe
    by replacing `=`, `/`, and `+` characters.
- **Parsing**: `Session.parseSessionID(sessionID: String)` constructs and validates a `Session`
  from a raw string, throwing `IllegalArgumentException` if the format is invalid.
- **Equality**: Two `Session` instances are equal if their `sessionId` values match.

#### Example Usage

```kotlin
val globalSession = Session.newGlobalID()
val userSession = Session.newUserID()

println(userSession.isGlobal()) // false
val converted = userSession.toGlobal()
println(converted.isGlobal()) // true

val parsed = Session.parseSessionID("G-20240101-abcd1234")
```

### `User`

The `User` data class represents a platform user, identified primarily by their `email` address.

#### Key Features

- **Fields**:
  - `email` — the user's email address (also serves as the default `name` and `id` if not
    otherwise specified).
  - `name` — the user's display name (defaults to `email`).
  - `id` — the user's unique identifier (defaults to `email`).
- **Equality**: Two `User` instances are considered equal if their `email` values match, regardless
  of differences in `name` or `id`.
- **Sentinel Value**: `User.NULL` provides a placeholder/null-object user with `id = "0"` and
  `email = "null@localhost"`.
- **Default User**: A top-level `defaultUser` variable (annotated `@JsonIgnore`) provides a default
  user instance (`id = "1"`, `email = "user@localhost"`) for use where no authenticated user is
  otherwise available.

#### Example Usage

```kotlin
val user = User(email = "alice@example.com")
println(user.name) // "alice@example.com"
println(user.id)   // "alice@example.com"

val customUser = User(email = "bob@example.com", name = "Bob", id = "bob-123")
println(customUser == User(email = "bob@example.com")) // true, equality based on email only
```

## Design Notes

- Both `Session` and `User` are designed to be lightweight, immutable (or effectively immutable)
  value objects suitable for use as identifiers across the platform.
- JSON (de)serialization is supported via Jackson annotations (`@JsonProperty`, `@JsonIgnore`) on
  the `User` class.
- `Session` validation is deliberately strict to prevent malformed identifiers from propagating
  through the system, while still allowing an escape hatch (`Session.NULL`) for cases where no
  real session exists yet.

## Package

```
com.simiacryptus.cognotik.platform.model
```