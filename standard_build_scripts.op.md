---
specifies: build.sh
related:
  - build.gradle.kts
  - gradle.properties
  - settings.gradle.kts
---

Create a unified, environment-agnostic build script.

* For starters, assume Ubuntu 22. This may be extended; code for extensibility
* Support these sub-commands:
  * setup: Ensure all dependencies are installed, including system packages and language runtimes.
  * compile: Compile the source code and generate any necessary artifacts.
  * test: Run the test suite to verify the correctness of the code.
