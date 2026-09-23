---
specifies: SessionServerCli.kt
---

* Similar to [FileServerCli.kt](FileServerCli.kt)
* Launches a session based on the logic in [DocOpsApp.kt](../../../../../../../../stdtools/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/DocOpsApp.kt)
  * However, this takes a new argument, a url to a zip, and grabs that zip to initialize the ephemeral working directory
* work for this session is done in a temporary working folder created with a timestamped folder name, by default out of tempdir but that should be configurable
