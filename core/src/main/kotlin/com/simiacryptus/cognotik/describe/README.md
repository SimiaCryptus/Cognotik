# Type Description System

The `com.simiacryptus.cognotik.describe` package provides a robust reflection-based framework for generating structured representations of Kotlin and Java types. These descriptions are primarily used to communicate data structures and API contracts to Large Language Models (LLMs) or to generate documentation.

## Core Components

### TypeDescriber
The abstract base class for all describers. It defines the common logic for:
- **Primitive Mapping**: Standardizes basic types (int, string, boolean, etc.).
- **Recursion Control**: Uses a `stackMax` and a set of `describedTypes` to prevent infinite loops in circular references.
- **Abbreviation**: Automatically truncates descriptions for standard library types (e.g., `java.*`, `kotlin.*`) to keep outputs concise.

### Implementations
- **`JsonDescriber`**: Generates a JSON-based schema representation. It includes a whitelist mechanism to restrict which classes are fully described for security and brevity.
- **`YamlDescriber`**: Produces a YAML representation similar to OpenAPI/Swagger schemas. It supports describing properties, methods, and registered subtypes.
- **`TypeScriptDescriber`**: Generates TypeScript interface and enum definitions, making it ideal for bridging the gap between backend types and frontend/LLM contexts.

### Specialized Describers
- **`AbbrevWhitelistTSDescriber` / `AbbrevWhitelistYamlDescriber`**: These classes allow you to provide a list of package prefixes that should *not* be abbreviated, ensuring detailed descriptions for specific internal modules while keeping others compact.

## Metadata and Annotations

### `@Description`
A runtime annotation that can be applied to classes, properties, or methods. Describers extract this value to provide human-readable (and LLM-readable) documentation within the generated output.

```kotlin
@Description("A user profile object")
data class User(
    @Description("The unique identifier") val id: Int,
    val name: String
)
```

## Advanced Features

### Polymorphism Support
The system supports describing polymorphic interfaces via `registerSubType`. When a parent class is encountered, the describer can include details about its known implementations.

```kotlin
describer.registerSubType(Shape::class.java, Circle::class.java)
describer.registerSubType(Shape::class.java, Square::class.java)
```

### Method Description
Describers can traverse class methods, documenting parameters and return types. This is particularly useful for generating "Tool" or "Function" definitions for LLM agents.

### Dynamic Enums
In addition to standard Java Enums, the system supports `DynamicEnum` and respects `EnabledStrategy` to filter available values at runtime.

## Utilities

- **`DescriptorUtil`**: A collection of reflection helpers for resolving generic types, finding annotations across Kotlin properties and constructor parameters, and handling array component types.
- **`MethodTypeDescriber`**: An interface that allows instances to provide dynamic type overrides for method parameters, useful when runtime types differ from static signatures.

## Usage Example

```kotlin
val describer = TypeScriptDescriber()
val description = describer.describe(MyDataClass::class.java)
println(description)
```