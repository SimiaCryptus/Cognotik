# Cognotik Actions

This document outlines the various actions provided by the Cognotik plugin, organized by the context in which they
appear.

## Tools Menu

Actions available from the main "Tools" menu under the "Cognotik" submenu.

| Text             | Description                                                                                                                                      |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| 💬 Cognotik Chat | Initiate a general-purpose chat session to discuss coding concepts, get assistance with programming tasks, or explore software development ideas |
| 🧠 Task Planning | Choose from multiple planning strategies in a unified interface with customizable task settings                                                  |

## Editor Context Menu

Actions available when right-clicking within the code editor, under the "Cognotik" submenu.

| Text             | Description                                                                                                               |
|------------------|---------------------------------------------------------------------------------------------------------------------------|
| 💬 Code Chat     | Start an interactive dialogue about your selected code, offering insights, explanations, and suggestions for improvements |
| 🛠️ Patch Chat   | Initiate an interactive session to discuss and apply patches to your code, with intelligent suggestions for modifications |
| ⚡ _Paste         | Paste with transformations to match the current file's programming language                                               |
| ✏️ _Edit Code... | Edit code...                                                                                                              |

## Project View Context Menu

Actions available when right-clicking on files or folders in the Project view, under the "Cognotik" submenu.

| Text                           | Description                                                                                                                          |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| 💬 Code Chat                   | Initiate an interactive dialogue session to discuss and analyze multiple code files simultaneously                                   |
| 📝 Modify Files                | Analyze and generate patches for multiple files simultaneously, considering the broader project context                              |
| 🔧 Apply Patch                 | Intelligently apply a provided patch to the selected file, with automatic conflict resolution and error handling                     |
| 📄 Generate Related File       | Intelligently create a new file related to the selected one, suggesting appropriate content and file location                        |
| ✨ Create File from Description | Create a new file with appropriate content based on a natural language description, intelligently determining file type and location |

### Macros Submenu

| Text                      | Description                                                                                                               |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------|
| 🔨 Review Files           | Analyze and patch multiple code files while considering markdown documentation files for context and standards            |
| 📚 Generate Documentation | Automatically generate comprehensive documentation for selected files or entire project, with customizable output formats |
| 🔨 Process Filesets       | Analyze and patch multiple code files while considering markdown documentation files for context and standards            |

### Agents Submenu

| Text               | Description                                                                                              |
|--------------------|----------------------------------------------------------------------------------------------------------|
| 🧠 Task Planning   | Choose from multiple planning strategies in a unified interface with customizable task settings          |
| 🔄 Run ... and Fix | Intelligent analysis and automatic resolution of build or test errors, with customizable fix suggestions |

### Knowledge Submenu

| Text                 | Description                                                                                    |
|----------------------|------------------------------------------------------------------------------------------------|
| 📚 Index Files       | Index files for semantic search and knowledge retrieval                                        |
| 🔍 Search Embeddings | Search through indexed files using semantic similarity                                         |
| 📽️ Index Projector  | Create a visual representation of the knowledge index for easier exploration and understanding |

## VCS Log Context Menu

Actions available when right-clicking on a commit in the Git log, under the "Cognotik" submenu.

| Text                           | Description                                                         |
|--------------------------------|---------------------------------------------------------------------|
| 💬 Chat with Commit            | Chat with Commit                                                    |
| 🔄 Chat with Diff (x..HEAD)    | Chat with Commit Diff                                               |
| 📝 Chat with Working Copy Diff | Open a chat session with the diff between HEAD and the working copy |
| 🔄 Replicate Commit            | Replicate Commit                                                    |

## Other Contextual Actions

### Find Usages Popup Menu

Actions available from the "Find Usages" results popup, under the "Cognotik" submenu.

| Text                    | Description                          |
|-------------------------|--------------------------------------|
| Modify Find Results     | Modify files based on find results   |
| Chat About Find Results | Start a code chat about find results |

### Test Runner Context Menu

| Text                            | Description                                             |
|---------------------------------|---------------------------------------------------------|
| 🔍 Cognotik Analyze Test Result | Open a chat session to analyze the selected test result |

### Problems View Context Menu

| Text                        | Description                                                             |
|-----------------------------|-------------------------------------------------------------------------|
| 🔧 Cognotik Analyze Problem | Open a chat session to analyze and potentially fix the selected problem |