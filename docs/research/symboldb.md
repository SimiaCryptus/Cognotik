---
documents:
  - ../intellij/src/main/kotlin/cognotik/actions/analysis/SymbolExtractionAction.kt
  - ../intellij/src/main/kotlin/cognotik/actions/SymbolGraphAction.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/apps/SymbolGraphService.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SymbolGraphServlet.kt
specifies: ../site/cognotik.com/symboldb.html
---

# Symbol Database

The Symbol Database (SymbolDB) is a graph-based code analysis and browsing system that extracts, stores, and provides
navigation for code symbols across your project. It uses Apache TinkerGraph to maintain relationships between files,
symbols, packages, libraries, and programming languages.

## Overview

The Symbol Database consists of three main components:

1. **Symbol Extraction Action** - An IntelliJ action that analyzes your project and extracts symbol information
2. **Symbol Graph Service** - A graph-based storage and query service using TinkerGraph
3. **Symbol Graph Servlet** - A web interface for browsing and searching the symbol database

## Features

- **Multi-language Support**: Automatically detects and categorizes symbols by programming language (Kotlin, Java,
  JavaScript, TypeScript, Python, Ruby, Go, Rust, C++, C, C#)
- **Hierarchical Organization**: Symbols are organized by file, package, library, and language
- **Reference Tracking**: Tracks which symbols reference other symbols
- **Containment Relationships**: Tracks parent-child relationships (e.g., methods within classes)
- **Incremental Updates**: Only re-analyzes files that have changed since the last extraction
- **VCS Integration**: Captures last-modified timestamps from version control annotations when available
- **Source Code Preview**: Displays source code with syntax highlighting in the web interface

## Graph Structure

### Vertex Types

| Type         | Description                                         |
|--------------|-----------------------------------------------------|
| **Symbol**   | A named code element (class, function, field, etc.) |
| **File**     | A source file in the project                        |
| **Language** | A programming language                              |
| **Library**  | A library or module containing symbols              |
| **Package**  | A package or namespace                              |

### Edge Types

| Edge           | From   | To       | Description                         |
|----------------|--------|----------|-------------------------------------|
| **DEFINED_IN** | Symbol | File     | Symbol is defined in the file       |
| **REFERENCES** | Symbol | Symbol   | Symbol references another symbol    |
| **WRITTEN_IN** | Symbol | Language | Symbol is written in the language   |
| **IN_LIBRARY** | Symbol | Library  | Symbol belongs to the library       |
| **IN_PACKAGE** | Symbol | Package  | Symbol belongs to the package       |
| **CONTAINS**   | Symbol | Symbol   | Parent symbol contains child symbol |

### Symbol Properties

| Property       | Type   | Description                                                       |
|----------------|--------|-------------------------------------------------------------------|
| `name`         | String | The symbol's name                                                 |
| `file`         | String | Path to the containing file                                       |
| `startOffset`  | Int    | Character offset where the symbol starts                          |
| `endOffset`    | Int    | Character offset where the symbol ends                            |
| `line`         | Int    | Line number of the symbol definition                              |
| `visibility`   | String | Visibility modifier (public, private, protected, package)         |
| `modifiers`    | String | Comma-separated list of modifiers (static, final, abstract, etc.) |
| `annotations`  | String | Comma-separated list of annotation names                          |
| `lastModified` | Long   | Timestamp when the symbol was last modified                       |
| `nodeType`     | String | The type of symbol (class, function, field, etc.)                 |

### Node Types

The system automatically categorizes symbols into types based on PSI element analysis:

- `class` - Class definitions
- `interface` - Interface definitions
- `enum` - Enumeration definitions
- `function` - Functions and methods
- `field` - Fields and properties
- `variable` - Local variables
- `parameter` - Function/method parameters
- `constructor` - Constructor definitions
- `object` - Object declarations (Kotlin)
- `annotation` - Annotation definitions

## Usage

### Extracting Symbols

1. Open your project in IntelliJ IDEA
2. Run the **Symbol Extraction** action from the Cognotik menu
3. Wait for the background task to complete
4. The symbol graph is saved to `symbol_graph.json` in your project root
   The extraction process:

- Scans all source roots in your project
- Analyzes each file using IntelliJ's PSI (Program Structure Interface)
- Extracts named elements and their references
- Stores the graph in GraphSON format

### Browsing Symbols

1. Ensure `symbol_graph.json` exists in your project root
2. Run the **Symbol Graph** action from the Cognotik menu
3. A web browser opens to the symbol browser interface
   The Symbol Graph action is only enabled when a `symbol_graph.json` file exists in the project.

## Web Interface

The Symbol Graph Servlet provides both HTML and JSON interfaces for browsing the symbol database.

### Endpoints

| Endpoint                      | Description                                      |
|-------------------------------|--------------------------------------------------|
| `/`                           | Home page with navigation and search             |
| `/search?q=<query>&limit=<n>` | Search symbols by name (default limit: 100)      |
| `/symbol?id=<id>`             | Get detailed information about a specific symbol |
| `/files`                      | Browse files hierarchically by folder            |
| `/files/<path>`               | Browse a specific folder                         |
| `/languages`                  | List all programming languages                   |
| `/libraries`                  | List all libraries                               |
| `/packages`                   | Browse packages hierarchically                   |
| `/packages/<path>`            | Browse a specific package path                   |
| `/types`                      | List all symbol types                            |
| `/file/<fileId>`              | List symbols in a specific file                  |
| `/language/<language>`        | List symbols by language                         |
| `/library/<library>`          | List symbols by library                          |
| `/package/<package>`          | List symbols by package                          |
| `/type/<nodeType>`            | List symbols by type                             |

### Response Formats

- **HTML**: Default format, provides a styled web interface
- **JSON**: Add `?format=json` parameter or use `Accept: application/json` header

### Symbol Detail View

The symbol detail page shows:

- Symbol properties (name, file, line, visibility, etc.)
- Source code preview with syntax highlighting
- Parent symbol (if contained within another symbol)
- Child symbols (symbols contained within this symbol)
- Outgoing references (symbols this symbol references)
- Incoming references (symbols that reference this symbol)

## API Usage

### SymbolGraphService

The `SymbolGraphService` class provides programmatic access to the symbol graph:

```kotlin
val service = SymbolGraphService()
// Load existing graph
service.load(File("symbol_graph.json"))
// Search for symbols
val results = service.search("MyClass", limit = 50)
// Get symbol by ID
val symbol = service.getSymbol("/path/to/file.kt::MyClass")
// Get symbols by various criteria
val fileSymbols = service.getSymbolsByFile("/path/to/file.kt")
val kotlinSymbols = service.getSymbolsByLanguage("Kotlin")
val packageSymbols = service.getSymbolsByPackage("com.example")
val classSymbols = service.getSymbolsByNodeType("class")
// Navigate relationships
symbol?.references()      // Symbols this symbol references
symbol?.referencedBy()    // Symbols that reference this symbol
symbol?.contains()        // Child symbols
symbol?.containedBy()     // Parent symbol
symbol?.file()            // Containing file
symbol?.language()        // Programming language
symbol?.packageName()     // Package name
symbol?.libraryName()     // Library name
// Get hierarchical views
val folderTree = service.getFolderHierarchy()
val packageTree = service.getPackageHierarchy()
// Save graph
service.save("symbol_graph.json")
```

### Symbol Properties

Access symbol properties through the `Symbol` wrapper class:

```kotlin
val symbol = service.getSymbol(id)
symbol?.id           // Unique identifier
symbol?.name         // Symbol name
symbol?.fileId       // Containing file path
symbol?.startOffset  // Start character offset
symbol?.endOffset    // End character offset
symbol?.line         // Line number
symbol?.visibility   // Visibility modifier
symbol?.modifiers    // Other modifiers
symbol?.annotations  // Annotations
symbol?.lastModified // Last modified timestamp
symbol?.nodeType     // Symbol type
symbol?.properties   // All properties as a map
```

## File Format

The symbol graph is stored in GraphSON format, a JSON-based graph serialization format used by Apache TinkerPop. The
file contains:

- Vertices with their labels, IDs, and properties
- Edges with their labels, source/target vertex IDs

## Language Detection

Languages are detected based on file extensions:
| Extension | Language |
|-----------|----------|
| `.kt` | Kotlin |
| `.java` | Java |
| `.js` | JavaScript |
| `.ts` | TypeScript |
| `.py` | Python |
| `.rb` | Ruby |
| `.go` | Go |
| `.rs` | Rust |
| `.cpp` | C++ |
| `.c` | C |
| `.cs` | C# |
| `.class` | Bytecode |

## Library and Package Detection

The system automatically extracts library and package information from file paths:

### JAR Files

For files within JAR archives (e.g., `/path/to/library.jar!/com/example/Class.class`):

- **Library**: Extracted from the JAR filename
- **Package**: Extracted from the path within the JAR

### Source Files

For source files with standard project structure (e.g., `/project/src/main/kotlin/com/example/Class.kt`):

- **Library**: Extracted from the directory containing `src`
- **Package**: Extracted from the path after `src/main/<language>/` or `src/test/<language>/`
