#!/bin/bash

# Strict mode
set -euo pipefail

# --- Configuration ---
INPUT_SVG="logo.svg"       # Input SVG file name
OUTPUT_DIR="public/icons"  # Directory to save generated icons
# PNG sizes to generate (in pixels)
PNG_SIZES=(16 32 48 64 128 192 256 512)
# Sizes to include in the favicon.ico (must be a subset of PNG_SIZES)
ICO_SIZES=(16 32 48)
# --- End Configuration ---

# --- Dependency Checks ---
command -v rsvg-convert >/dev/null 2>&1 || { echo >&2 "Error: rsvg-convert is required but not installed. Please install librsvg (e.g., 'sudo apt install librsvg2-bin' or 'brew install librsvg')."; exit 1; }
command -v convert >/dev/null 2>&1 || { echo >&2 "Error: convert (ImageMagick) is required but not installed. Please install ImageMagick (e.g., 'sudo apt install imagemagick' or 'brew install imagemagick')."; exit 1; }

# --- Input File Check ---
if [ ! -f "$INPUT_SVG" ]; then
  echo >&2 "Error: Input SVG file '$INPUT_SVG' not found in the current directory."
  exit 1
fi

# --- Directory Setup ---
mkdir -p "$OUTPUT_DIR"
echo "Output directory: ${OUTPUT_DIR}"

# --- Generate PNG Files ---
echo "Generating PNG icons from ${INPUT_SVG}..."
generated_pngs=()
for size in "${PNG_SIZES[@]}"; do
  output_png="${OUTPUT_DIR}/icon-${size}x${size}.png"
  echo "  Generating ${output_png}..."
  rsvg-convert \
    --width="$size" \
    --height="$size" \
    --format=png \
    --output="$output_png" \
    "$INPUT_SVG"
  generated_pngs+=("$output_png") # Keep track for potential cleanup or listing
done
echo "PNG generation complete."

# --- Generate favicon.ico ---
echo "Generating favicon.ico..."
ico_input_files=()
output_ico="${OUTPUT_DIR}/favicon.ico"

# Check if ICO_SIZES are valid subsets of PNG_SIZES and collect file paths
for ico_size in "${ICO_SIZES[@]}"; do
  found=0
  for png_size in "${PNG_SIZES[@]}"; do
    if [ "$ico_size" -eq "$png_size" ]; then
      ico_input_files+=("${OUTPUT_DIR}/icon-${ico_size}x${ico_size}.png")
      found=1
      break
    fi
  done
  if [ "$found" -eq 0 ]; then
    echo >&2 "Warning: Size ${ico_size} requested for favicon.ico was not generated as a PNG. Skipping."
  fi
done

if [ ${#ico_input_files[@]} -eq 0 ]; then
  echo >&2 "Error: No valid PNG files found to create favicon.ico based on ICO_SIZES."
  exit 1
fi

echo "  Using: ${ico_input_files[*]}"
convert "${ico_input_files[@]}" "$output_ico"

echo "Favicon generation complete: ${output_ico}"
echo "-------------------------------------"
echo "Icon generation process finished successfully."
echo "Generated files are in the '${OUTPUT_DIR}' directory."

exit 0