# Pipeline

## Steps

### 1. code_op — Implement Script
- **Specifies:** `code/script.sh`
- **Related:** `goal.md`
- Implement the goal as a shell script

### 2. run_op — Run and Fix Script
- **Type:** AutoFix
- **Folder:** `code/`
- **Specifies:** `code/fix_log.md`
- Run and fix `./script.sh`