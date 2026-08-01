# `fileserver` — Cognotik CLI file server launcher

A single self-contained Bash launcher that resolves, downloads, verifies and runs
the published `cli-<version>-all.jar` from the
[Cognotik GitHub releases](https://github.com/SimiaCryptus/Cognotik/releases).

The jar is stored **next to the script**, so a checkout/copy of this one file is
enough to bootstrap the file server on any machine with Bash, Java and
`curl` (or `wget`).

---

## Quick start

```shell
# Fetch the launcher
curl -fsSLO https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/2.1.20/cli/dist/fileserver
chmod +x fileserver

# Run it (downloads the latest release jar on first use)
./fileserver --port 8080 /srv/files
```

Show the launcher's own help:

```shell
./fileserver --help
```

Show the *application's* help (note the `--` separator, so `--help` is not
consumed by the launcher):

```shell
./fileserver -- --help
```

---

## Launcher options

| Option | Description |
| --- | --- |
| `--version <v>` | Pin a release version (e.g. `2.1.19`). `--version=<v>` and a leading `v` are also accepted. |
| `--update` | Re-resolve the latest release and re-download the jar. |
| `--offline` | Never touch the network; require a jar already on disk. |
| `--repo <owner/repo>` | GitHub repository to fetch releases from (default `SimiaCryptus/Cognotik`). |
| `--jar <path>` | Use this jar verbatim; skips all resolution and downloading. |
| `--print-jar` | Resolve (and fetch, unless `--offline`) then print the jar path and exit. |
| `--no-verify` | Skip SHA-256 verification against the published `.sha256` sidecar. |
| `--quiet` | Suppress informational messages (warnings/errors still print). |
| `-h`, `--help` | Show help and exit. |

Option parsing **stops at the first non-option argument** or at `--`.
Everything after that point is forwarded verbatim to
`com.simiacryptus.cognotik.cli.FileServerCli`.

---

## Environment variables

| Variable | Meaning |
| --- | --- |
| `COGNOTIK_VERSION` | Same as `--version`. |
| `COGNOTIK_REPO` | Same as `--repo`. |
| `COGNOTIK_JAR_DIR` | Directory used to store/find jars (default: the script's directory). |
| `COGNOTIK_CACHE_TTL` | Seconds to cache the "latest version" lookup (default `86400`). |
| `GITHUB_TOKEN` | Optional token used **only** for the GitHub API lookup, to avoid anonymous rate limiting. |
| `JAVA_HOME` | If set and `$JAVA_HOME/bin/java` is executable, it is used. |
| `JAVA_OPTS` | Extra JVM options; word-split on whitespace (e.g. `-Xmx2g -Dfoo=bar`). |

---

## Examples

```shell
# Serve a directory on a specific port
./fileserver --port 8080 /srv/files

# Pin a version and force a fresh download
./fileserver --version 2.1.19 --update -- --help

# Air-gapped host: use whatever jar is already present, never hit the network
./fileserver --offline /srv/files

# Give the JVM more heap
JAVA_OPTS="-Xmx2g" ./fileserver /srv/files

# Print the resolved jar path (useful for scripting / packaging)
./fileserver --print-jar

# Use a locally built jar
./fileserver --jar ../build/libs/cli-2.1.20-all.jar /srv/files
```

---

## How it works

1. **Self-location** — resolves `$0` through symlinks (with a loop guard) so the
 script works from any working directory and via `PATH` symlinks.
2. **Version resolution** — unless `--version`/`COGNOTIK_VERSION` is given:
 * `GET https://api.github.com/repos/<repo>/releases/latest` and read `tag_name`;
 * fallback: follow the `releases/latest` redirect and take the final path segment;
 * fallback: highest-numbered `cli-*-all.jar` already on disk (`sort -V`);
 * fallback: a hard-coded `FALLBACK_VERSION`.
 The resolved value is cached in `.cognotik-latest-version` for `COGNOTIK_CACHE_TTL`
 seconds (bypassed by `--update`).
3. **Download** — to `cli-<version>-all.jar.part.XXXXXX`, then:
 * reject anything that isn't a ZIP archive (`PK` magic bytes);
 * verify the SHA-256 against `<asset>.sha256` if that sidecar exists
   (skipped with `--no-verify`, or if no hashing tool is available);
 * atomically `mv` into place.
 A `cli-<version>-all.jar.lock` directory serializes concurrent launches;
 locks older than 30 minutes are treated as stale.
 Temp files and locks are removed on `EXIT`/`INT`/`TERM`.
4. **Exec** — `exec java $JAVA_OPTS -cp <jar> com.simiacryptus.cognotik.cli.FileServerCli "$@"`,
 so the JVM replaces the shell and signal handling / exit codes pass straight through.

### Files created next to the script

```
cli-<version>-all.jar             # the downloaded runtime
.cognotik-latest-version          # cached "latest release" lookup
cli-<version>-all.jar.lock/       # transient download lock (auto-removed)
cli-<version>-all.jar.part.*      # transient partial download (auto-removed)
```

Point `COGNOTIK_JAR_DIR` elsewhere (e.g. `~/.cache/cognotik`) if the script lives
in a read-only location.

---

## Requirements

* Bash 3.2+ (macOS system Bash works)
* Java 17+ (older JVMs only produce a warning, not an error)
* `curl` **or** `wget` — only needed the first time, or with `--update`
* Optional: `sha256sum` / `shasum` / `openssl` for checksum verification

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `no 'java' on PATH and JAVA_HOME is not usable` | Install a JDK 17+ or set `JAVA_HOME`. |
| `cannot write to <dir>` | Set `COGNOTIK_JAR_DIR` to a writable directory. |
| `could not resolve latest release` | Network/API rate limit — set `GITHUB_TOKEN`, or pin `--version`. |
| `checksum mismatch` | Retry; if it persists, the release asset or your proxy is at fault. `--no-verify` bypasses (not recommended). |
| `timed out waiting for another fileserver` | A stale lock: remove `cli-*-all.jar.lock` in the jar directory. |
| `--offline given but no cli-*-all.jar found` | Copy a jar into the jar directory first, or drop `--offline`. |

---

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success (or `--help` / `--print-jar`). |
| `1` | Launcher error (see the `fileserver: error:` message). |
| `130` / `143` | Interrupted (`SIGINT` / `SIGTERM`) before `exec`. |
| other | Propagated from the JVM / `FileServerCli`. |