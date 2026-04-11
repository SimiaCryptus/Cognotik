#!/bin/bash
# Run all edit scripts in the video_tour directory

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# --- Step 1: Run all edit scripts to produce edited videos ---

find "$SCRIPT_DIR" -maxdepth 1 -name 'edit_*.sh' -type f -print0 | sort -z | while IFS= read -r -d '' script; do
    echo "=== Running: $(basename "$script") ==="
    bash "$script"
    exit_code=$?
    if [ $exit_code -ne 0 ]; then
        echo "!!! FAILED: $(basename "$script") exited with code $exit_code !!!"
    else
        echo "=== Completed: $(basename "$script") ==="
    fi
    echo ""
done

# --- Step 2: Normalize audio levels across all edited videos ---
echo "=== Running: normalize_audio.sh ==="
bash "${SCRIPT_DIR}/scripts/normalize_audio.sh"
exit_code=$?
if [ $exit_code -ne 0 ]; then
     echo "!!! FAILED: normalize_audio.sh exited with code $exit_code !!!"
else
     echo "=== Completed: normalize_audio.sh ==="
fi