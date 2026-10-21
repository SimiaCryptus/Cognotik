# MultiPerspectiveAnalysisTaskCli

Command line wrapper around `MultiPerspectiveAnalysisTask`. It analyzes a subject from several
viewpoints (technical, business, ethical, ...) and optionally synthesizes the results into a
unified conclusion.

By default the CLI runs against the hosted backend (device login). Pass `--local` to use locally
configured API keys instead.

## Usage

    cognotik perspectives --subject "Migrate the build from Maven to Gradle" \
        --perspectives "technical,business,risk" \
        --files "pom.xml,**/*.kts"

Options accept either `--key value` or `--key=value`.

| Option                         | Description                                                    | Default                                   |
|--------------------------------|----------------------------------------------------------------|-------------------------------------------|
| `--subject <text>`             | Topic or problem to analyze (required unless `--subject-file`) |                                           |
| `--subject-file <path>`        | Read the subject from a file                                   |                                           |
| `--perspectives <a,b,c>`       | Comma-separated perspectives                                   | `technical,business,user experience,risk` |
| `--files <p1,p2,...>`          | Comma-separated input files or glob patterns used as context   | none                                      |
| `--no-synthesize`              | Skip the synthesis step                                        | synthesize                                |
| `--consensus-threshold <0..1>` | Minimum perspective agreement threshold                        | `0.7`                                     |
| `--timeout-minutes <n>`        | Harness timeout                                                | `30`                                      |
| `--smart-model <name>`         | Smart model                                                    | `GeminiFlash_38`                          |
| `--fast-model <name>`          | Fast model                                                     | `GeminiFlash_38`                          |
| `--hosted`                     | Use hosted backend via device login                            | default                                   |
| `--local`                      | Use local API keys; skip hosted login                          |                                           |
| `--help`                       | Show help                                                      |                                           |

## Exit codes

- `0` – analysis completed
- `1` – the task failed
- `2` – invalid arguments, login failure, or platform bootstrap failure