# FileModificationTaskCli

Command line wrapper for `FileModificationTask`: asks an AI model to modify existing
files or create new ones from a natural-language description. It is a sibling of
`CommitAnalyzerTaskCli` and shares its hosted/local bootstrap and model resolution.

## Usage

Run through the launcher:

    cognotik modify --files src/Foo.kt,src/Bar.kt --description "Rename Foo to Baz"

or directly:

    java -cp cognotik-all.jar com.simiacryptus.cognotik.cli.task.FileModificationTaskCli [options]

Options accept `--key value` or `--key=value`.

| Option                      | Description                                                       |
|-----------------------------|-------------------------------------------------------------------|
| `--root <dir>`              | Workspace root; file paths resolve against it (default: `.`)      |
| `--files <a,b,...>`         | Comma-separated files to modify or create (required)              |
| `--description <text>`      | Description of the modification(s)                                |
| `--description-file <path>` | Read the description from a file (use instead of `--description`) |
| `--include-git-diff`        | Include `git diff HEAD` for each input file in the prompt         |
| `--timeout-minutes <n>`     | Harness timeout (default: 30)                                     |
| `--smart-model <name>`      | Smart model (default: `GeminiFlash_38`)                           |
| `--fast-model <name>`       | Fast model (default: `GeminiFlash_38`)                            |
| `--hosted`                  | Use the hosted backend via device login (default)                 |
| `--local`                   | Use locally configured API keys; skip hosted login                |
| `--help`                    | Show usage                                                        |

One of `--description` / `--description-file` and `--files` are required.

## Notes

- Absolute file paths inside `--root` are converted to root-relative paths.
- Files that do not exist yet are allowed; the task may create them.
- Exit codes: `0` success, `1` task failure, `2` usage/login/bootstrap error.