# sys-wizard

A wizard-style app that generates and iteratively fixes a shell script to accomplish a specified goal.

## Structure

- `goal.md` — Defines the objective the shell script should achieve.
- `code/script.sh` — The generated shell script that implements the goal.
- `code/fix_log.md` — Log of fixes applied during the auto-fix cycle.

## Operations

### code_op
Generates `code/script.sh` based on the goal defined in `goal.md`.

### run_op
Runs `code/script.sh` and automatically fixes any errors encountered, logging results to `code/fix_log.md`. Uses the `AutoFix` task type to iteratively run and repair the script until it succeeds.

## Usage

1. Define your objective in `goal.md`.
2. Run the **code_op** to generate the shell script.
3. Run the **run_op** to execute the script and auto-fix any issues.