# Symbol Extraction Action

The `SymbolExtractionAction` is an IntelliJ IDEA action responsible for analyzing the project's source code to build and maintain a comprehensive symbol graph. This graph maps definitions and references across the codebase, providing a foundation for advanced code navigation and analysis features.

## Features

- **Incremental Analysis**: The action tracks file timestamps and only re-processes files that have changed since the last analysis, significantly improving performance on large projects.
- **Symbol Definition Tracking**: Identifies named elements (classes, methods, variables, etc.) using the IntelliJ PSI (Program Structure Interface).
- **Reference Resolution**: Resolves cross-references between symbols to build a directed graph of dependencies.
- **VCS Integration**: Leverages version control information to associate specific symbols with their last modification dates.
- **Background Processing**: Runs as a background task with a progress indicator, ensuring the IDE remains responsive during analysis.
- **Persistence**: Serializes the resulting symbol graph to a `symbol_graph.json` file located in the project's base directory.

## Implementation Details

### Core Components
- **SymbolGraphService**: The underlying service used to manage the graph data structure, handle serialization, and perform pruning of stale data.
- **PsiRecursiveElementVisitor**: Used to traverse the PSI tree of each file to extract symbols and their references.
- **ReadAction**: Ensures that code analysis is performed safely on the IntelliJ read thread.

### Workflow
1. **Initialization**: Loads the existing `symbol_graph.json` if present.
2. **Cleanup**: Identifies and removes files from the graph that no longer exist in the project.
3. **Scanning**: Iterates through all content source roots.
4. **Processing**: For each modified file:
    - Clears old outgoing references.
    - Visits every PSI element to find definitions (`PsiNamedElement`).
    - Resolves references to determine target symbols.
    - Captures metadata such as line numbers, offsets, and VCS timestamps.
5. **Pruning**: Removes symbols from the graph that are no longer present in the updated files.
6. **Serialization**: Saves the updated graph back to disk.

## Usage

This action is typically triggered via the IDE's action system (e.g., via a menu item or keyboard shortcut). Upon completion, a notification is displayed indicating the location of the saved symbol graph.

## Potential Impacts

- **Disk I/O**: Writing the `symbol_graph.json` file can be intensive for very large projects.
- **Memory Usage**: The symbol graph is held in memory during the analysis process.
- **VCS Performance**: Requesting annotations for every file can be slow depending on the VCS provider (e.g., Git, SVN).