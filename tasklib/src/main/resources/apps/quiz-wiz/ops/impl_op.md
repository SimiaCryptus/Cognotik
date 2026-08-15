---
specifies: ../code/
related: 
  - ../idea.md
  - ../design/*.*
---

# Task

Render this quiz game idea into a working codebase:

* Use the design documents to implement the quiz game logic, user interface, and data handling
* Ensure that the code is modular, maintainable, and well-documented
* The code should be organized in a way that allows for easy updates and additions of new questions and features

# Format

Create the following files in the `code/` folder to implement the quiz game:

* `index.html` - The main HTML file that serves as the entry point for the quiz game. It should include the necessary structure and elements for the user interface.
* `style.css` - The CSS file that defines the visual styling of the quiz game,
* `script.js` - The JavaScript file that contains the logic for the quiz game, including handling user interactions, managing game state, and processing quiz data.

# Game data

Game data can be read from the `gamedata` folder.
The files in the folder can be listed by requesting `gamedata/_files.json`, which will return with a response similar to:

```json
{
  "path": "gamedata",
  "totalFiles": 2,
  "totalFolders": 0,
  "entries": [
    {"name": "quiz_questions_1.json", "type": "file", "size": 4358, "lastModified": 1777640056802, "mimeType": "application/json"},
    {"name": "quiz_questions_2.json", "type": "file", "size": 103, "lastModified": 1777569622048, "mimeType": "application/json"}
  ]
}
```

The UI should allow the user to select the json file they want to load.

# Results

Results should be saved to the `results` folder, with a timestamped filename ending in `.json` (e.g., `results_2024-06-30T12-00-00.json`), containing the user's quiz performance metrics and feedback.
This file can be saved via a PUT request to the `results` folder with the desired filename and JSON content in the request body.
