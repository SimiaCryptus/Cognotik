# GameTheoryTaskCli

Command line wrapper around `GameTheoryTask`, which analyzes a strategic interaction using game theory (game structure,
payoff matrix, Nash equilibria, dominant strategies, Pareto optimality, repeated games,
and per-player recommendations).

By default the CLI runs against the hosted backend (device login). Pass `--local` to use locally configured API keys.

## Launching

```bash
cli/bin/cognotik gametheory [options]
# or via the alias
cli/bin/cognotik game [options]
```

## Options

| Option                                         | Description                                                                      |
|------------------------------------------------|----------------------------------------------------------------------------------|
| `--scenario <text>`                            | Strategic situation to analyze (required unless `--scenario-file`)               |
| `--scenario-file <path>`                       | Read the scenario from a file                                                    |
| `--players <a,b,...>`                          | Comma-separated players (required)                                               |
| `--strategies <spec>`                          | Known strategies, e.g. `"A=coop,defect;B=coop,defect"`                           |
| `--game-type <type>`                           | `cooperative`, `non-cooperative` (default), `zero-sum`, `repeated`, `sequential` |
| `--no-payoff-matrix`                           | Skip payoff matrix                                                               |
| `--no-nash`                                    | Skip Nash equilibria                                                             |
| `--no-dominant`                                | Skip dominant strategy analysis                                                  |
| `--no-pareto`                                  | Skip Pareto optimality                                                           |
| `--no-recommendations`                         | Skip recommendations                                                             |
| `--repeated`                                   | Also analyze as a repeated game (implied by `--game-type repeated`)              |
| `--iterations <n>`                             | Repeated game iterations (default 10)                                            |
| `--context <text>`                             | Additional context or constraints                                                |
| `--files <glob,...>`                           | Files or glob patterns to include as input                                       |
| `--timeout-minutes <n>`                        | Harness timeout (default 30)                                                     |
| `--smart-model <name>` / `--fast-model <name>` | Model selection (default `GeminiFlash_38`)                                       |
| `--hosted` / `--local`                         | Backend selection                                                                |
| `--help`                                       | Show usage                                                                       |

Options accept `--key value` or `--key=value`.

## Example

```bash
cli/bin/cognotik gametheory \
  --scenario "Two firms decide whether to cut prices" \
  --players "FirmA,FirmB" \
  --strategies "FirmA=cut,hold;FirmB=cut,hold" \
  --repeated --iterations 20
```