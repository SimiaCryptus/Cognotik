#!/bin/bash

# Copy all pdf and md files from /home/andrew/code/qqn-optimizer to the current directory, preserving the directory structure.

SOURCE_DIR="/home/andrew/code/qqn-optimizer"
DEST_DIR="$(pwd)"

# Check if source directory exists
if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source directory '$SOURCE_DIR' does not exist."
    exit 1
fi

echo "Copying .pdf and .md files from '$SOURCE_DIR' to '$DEST_DIR'..."

# Find all .pdf and .md files and copy them preserving directory structure
find "$SOURCE_DIR" -type f \( -name "*.pdf" -o -name "*.md" \) -print0 | while IFS= read -r -d '' file; do
    # Compute the relative path from the source directory
    rel_path="${file#$SOURCE_DIR/}"
    dest_path="$DEST_DIR/$rel_path"

    # Create the destination directory if it doesn't exist
    mkdir -p "$(dirname "$dest_path")"

    # Copy the file
    cp -p "$file" "$dest_path"
    echo "Copied: $rel_path"
done

echo "Done."