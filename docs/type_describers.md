# Type Describers

**Cognotik** relies heavily on reflection to describe code structures to Large Language Models (LLMs). The `TypeDescriber` system is responsible for converting Java/Kotlin classes, methods, and interfaces into text-based schemas (YAML, JSON, or TypeScript) that LLMs can understand and utilize.

This is the engine behind `ParsedAgent` (for output formatting) and `CodeAgent`/`ProxyAgent` (for tool/API definition).

---

## 1. The `TypeDescriber` Abstraction
**File:** `TypeDescriber.kt`

The abstract base class for all describers. It handles:
*   **Recursion Detection:** Prevents infinite loops when describing self-referencing types.
*   **Generics Resolution:** Unwraps `List<T>`, `Map<K,V>`, and other parameterized types.
*   **Method Filtering:** Excludes common methods like `toString`, `hashCode`, `equals`.
*   **Polymorphism:** Allows registering subtypes so abstract types in a schema include their concrete implementations.

### Key Configuration
*   **`coverMethods` (Boolean):** Whether to include method signatures in the description. Default is `true`.
*   **`stackMax` (Int):** Depth limit for recursion. Default is usually 10.

---

## 2. Implementations

### `YamlDescriber`
**File:** `YamlDescriber.kt`
**Format:** YAML

The default describer for most agents. YAML is preferred because it is token-efficient and highly readable for LLMs.

**Example Output:**
```yaml
type: object
class: com.example.UserProfile
properties:
  username:
    type: string
    description: "Unique identifier"
  roles:
    type: array
    items:
      type: enum
      values:
        - ADMIN
        - USER
methods:
  updateEmail:
    operationId: updateEmail
    parameters:
      - name: newEmail
        type: string
```

### `JsonDescriber`
**File:** `JsonDescriber.kt`
**Format:** JSON

Produces a JSON Schema-like structure. Useful for models that are fine-tuned on JSON or when strict JSON output is required.

**Features:**
*   **Whitelist:** Accepts a set of allowed class names. Types outside this whitelist are described as `{"allowed": false}` to prevent leaking internal API details.

### `TypeScriptDescriber`
**File:** `TypeScriptDescriber.kt`
**Format:** TypeScript Interfaces

Generates TypeScript interface definitions. This is particularly effective for `CodeAgent` or coding-specialized LLMs, as they are often heavily trained on TypeScript definitions.

**Example Output:**
```typescript
interface UserProfile {
  username: string; /* Unique identifier */
  roles: string[];
  updateEmail(newEmail: string): void;
}
```

---

## 3. Advanced Usage

### Abbreviation & Whitelisting
When describing complex object graphs, you often want to hide implementation details of third-party libraries or deep internal structures.

*   **`AbbrevWhitelistYamlDescriber`** & **`AbbrevWhitelistTSDescriber`**
    *   These classes take a list of package prefixes in their constructor.
    *   **Logic:** If a type's name does *not* start with one of the provided prefixes, it is "abbreviated" (shown only as `type: object, class: ClassName` without properties/methods).
    *   This focuses the LLM's attention on your specific domain objects.

### Polymorphism (Subtypes)
If your API returns an interface (e.g., `Shape`), the LLM needs to know about `Circle` and `Square` to construct or interpret them correctly.

```kotlin
val describer = YamlDescriber()
// Register concrete implementations
describer.registerSubTypes(Shape::class.java, Circle::class.java, Square::class.java)
```

The generated schema will now include a `subtypes` section listing the structures of `Circle` and `Square`.

### Annotations
The system recognizes the `@Description` annotation to inject semantic meaning into the schema.

*   **On Properties:** Describes the field.
*   **On Methods:** Describes the operation.
*   **On Parameters:** Describes the argument.

```kotlin
data class Request(
    @Description("The target URL to fetch")
    val url: String
)
```

### Dynamic Enums
Support for `DynamicEnum` allows describing extensible enumerations that aren't standard Java enums, filtering values based on `EnabledStrategy`.
