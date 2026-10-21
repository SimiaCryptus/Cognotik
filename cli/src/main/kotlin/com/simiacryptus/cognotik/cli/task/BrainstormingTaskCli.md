# BrainstormingTaskCli

Command line wrapper for `BrainstormingTask`. It generates several solution options for a
problem, analyzes each option independently (pros, cons, feasibility, impact, risks,
requirements), and produces a comparative summary with a top recommendation.

By default it runs against the hosted backend (device login). Pass `--local` to use locally
configured API keys.

## Usage

~~~bash
cognotik brainstorm --problem "How should we reduce build times?" [options]
~~~

| Option                                         | Description                                                                          |
|------------------------------------------------|--------------------------------------------------------------------------------------|
| `--problem <text>`                             | Problem or question to brainstorm (required unless `--problem-file`)                 |
| `--problem-file <path>`                        | Read the problem statement from a file                                               |
| `--file <glob>`                                | Input file/glob (relative to the task workspace root); repeatable or comma-separated |
| `--category <name>`                            | Category/domain to consider; repeatable or comma-separated                           |
| `--constraint <text>`                          | Constraint to consider; repeatable                                                   |
| `--count <n>`                                  | Number of options, 3-20 (default: 7)                                                 |
| `--depth <level>`                              | `brief`, `moderate` (default) or `detailed`                                          |
| `--no-creative`                                | Prefer practical, proven options                                                     |
| `--timeout-minutes <n>`                        | Harness timeout (default: 30)                                                        |
| `--smart-model <name>` / `--fast-model <name>` | Models (default: `GeminiFlash_38`)                                                   |
| `--hosted` / `--local`                         | Hosted backend (default) or local API keys                                           |
| `--help`                                       | Show help                                                                            |

Options accept `--key value` or `--key=value`.

## Example

~~~bash
cognotik brainstorm \
  --problem "Reduce CI build time" \
  --category tooling --category architecture \
  --constraint "No new paid services" \
  --count 5 --depth brief --local
~~~

Exit codes: `0` success, `1` task failure, `2` usage/login/bootstrap error.