### Phase 1 — Server: FS API v1 core
* **IMPLEMENTED** — `FilesystemServlet` (a v2 `FileServlet`) intercepts
`{mount}/[<session>/].fsapi/v<N>/<op>` in `service()` and dispatches to
`FsApiHandler`; `FsPath` performs lexical normalization plus canonical
containment (symlink-escape safe); `FsErrors`/`FsErrorCode` provide the
uniform errno envelope; `FsStat` synthesises `Stats`; `EtagUtil`/`RangeUtil`
supply conditional requests and byte ranges; `FsWatchHandler` (SSE),
`FsExecHandler` (allowlisted), `FsResolveHandler` and `FsSnapshotHandler`
cover watch/exec/resolve/snapshot. `MiniJson` avoids a JSON dependency.
* `FsApiHandler` + routing under `{mount}/.fsapi/v1`; reuse `PathUtils`,
`FileAccessControl`, `FileChannelCache`.
* `meta`, `stat`(+batch), `dir`, `file` GET (with `Range`, `ETag`, `Content-Length`),
`file` PUT (flags + conditional), `mkdir`, `rm`, `rename`, `copy`, `truncate`,
`realpath`, `batch`.
* Quotas, symlink containment, CSRF header requirement.
* **Done when:** JUnit matrix green; `curl` can perform every op; existing UI unaffected.