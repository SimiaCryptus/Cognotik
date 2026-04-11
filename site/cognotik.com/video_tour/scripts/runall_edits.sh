#!/bin/bash

# Run all edit scripts in the video_tour directory
# - Captures logs for each task
# - Continues running even when a task fails
# - Displays a summary at the end
# - Preserves failed tasks' logs

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "$LOG_DIR"

# Arrays to track results
declare -a TASK_NAMES=()
declare -a TASK_RESULTS=()
declare -a TASK_LOGFILES=()

# --- Step 1: Run all edit scripts to produce edited videos ---

while IFS= read -r -d '' script; do
    task_name="$(basename "$script")"
    log_file="${LOG_DIR}/${task_name}.log"

    TASK_NAMES+=("$task_name")
    TASK_LOGFILES+=("$log_file")

    echo "=== Running: ${task_name} ==="
    bash "$script" > "$log_file" 2>&1
    exit_code=$?
    TASK_RESULTS+=("$exit_code")

    if [ $exit_code -ne 0 ]; then
        echo "!!! FAILED: ${task_name} exited with code $exit_code (log: $log_file) !!!"
    else
        echo "=== Completed: ${task_name} ==="
    fi
    echo ""
done < <(find "$SCRIPT_DIR" -maxdepth 1 -name 'edit_*.sh' -type f -print0 | sort -z)

# --- Step 2: Normalize audio levels across all edited videos ---

task_name="normalize_audio.sh"
log_file="${LOG_DIR}/${task_name}.log"

TASK_NAMES+=("$task_name")
TASK_LOGFILES+=("$log_file")

echo "=== Running: ${task_name} ==="
bash "${SCRIPT_DIR}/scripts/normalize_audio.sh" > "$log_file" 2>&1
exit_code=$?
TASK_RESULTS+=("$exit_code")

if [ $exit_code -ne 0 ]; then
    echo "!!! FAILED: ${task_name} exited with code $exit_code (log: $log_file) !!!"
else
    echo "=== Completed: ${task_name} ==="
fi
echo ""

# --- Summary ---

echo "========================================"
echo "           RUN SUMMARY"
echo "========================================"

total=${#TASK_NAMES[@]}
passed=0
failed=0
declare -a FAILED_TASKS=()
declare -a FAILED_LOGS=()

for i in "${!TASK_NAMES[@]}"; do
    if [ "${TASK_RESULTS[$i]}" -ne 0 ]; then
        status="FAILED (exit code ${TASK_RESULTS[$i]})"
        failed=$((failed + 1))
        FAILED_TASKS+=("${TASK_NAMES[$i]}")
        FAILED_LOGS+=("${TASK_LOGFILES[$i]}")
    else
        status="OK"
        passed=$((passed + 1))
        # Remove logs for successful tasks
        rm -f "${TASK_LOGFILES[$i]}"
    fi
    printf "  %-40s %s\n" "${TASK_NAMES[$i]}" "$status"
done

echo "----------------------------------------"
echo "  Total: ${total}  |  Passed: ${passed}  |  Failed: ${failed}"
echo "========================================"

if [ $failed -gt 0 ]; then
    echo ""
    echo "Failed task logs preserved:"
    for i in "${!FAILED_TASKS[@]}"; do
        echo "  ${FAILED_TASKS[$i]} -> ${FAILED_LOGS[$i]}"
    done
    exit 1
else
    echo ""
    echo "All tasks completed successfully."
    exit 0
fi