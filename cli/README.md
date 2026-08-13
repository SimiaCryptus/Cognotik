# docops CLI (reference implementation)

A minimal, dependency-light front end for the `docops` engine. It exists to show the smallest correct way to drive
`DocOps` from a terminal.

```shell
curl -sSL -o fileserver https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/fileserver
chmod +x fileserver
./fileserver
```

## Lifecycle

  ```
  parse args
    └─ bootstrap platform (dynamic enums, local auth, OrchestrationConfig.instanceFn)
       └─ DocProcessor(root, docsFolder, updateMode, models, user, templateVarOverrides)
          ├─ getAll(...)            -> WorkPlan          (pure: no writes, no server)
          ├─ [plan]  print + exit
          └─ [run]
             ├─ initializeStatus(plan)                   (docops.status.json = PENDING)
             ├─ EphemeralMonitorServer.start()           -> prints "Monitor: http://host:port/"
             ├─ runAll(plan, pool, cancelFlag) { println monitor url per session }
             ├─ monitor.close()                          (finally + shutdown hook)
             └─ print status, exitProcess(0|1)
  ```

## Why the server is ephemeral

Task execution registers each session in the process-wide `SessionProxyServer` maps, so a web UI is only needed for
*observation*. The CLI therefore:

* starts Jetty **lazily** - never for `plan`, `status`, `vars`, `models`, or an empty plan;
* binds an unused port by default so concurrent invocations do not collide;
* stops it in `finally` **and** from a shutdown hook (Ctrl-C also sets the cancel flag);
* writes no PID file and spawns no detached process.

Use `--serverless` for fully headless execution (implies `--no-monitor`).

## Exit codes

| Code | Meaning                  |
|------|--------------------------|
| 0    | success                  |
| 1    | one or more tasks failed |
| 2    | bad usage                |

## Examples

  ```sh
  cognotik docops plan                                   # what would happen?
  cognotik docops vars docs/api.md                       # which {{ VARS }} exist?
  cognotik docops run docs/api.md --var MODULE=billing   # execute one document
  cognotik docops run --mode ForceUpdate -c 8 --open     # rebuild everything, watch in browser
  cognotik docops status                                 # last recorded run
  ```