#!/bin/bash

# Build a PDF from a TEX file
# Usage: ./build.sh [input.tex]
# Defaults to "doc.tex" if no argument is provided

INPUT="${1:-doc.tex}"

if [ ! -f "$INPUT" ]; then
  echo "Error: File '$INPUT' not found."
  exit 1
fi

DIR="$(dirname "$INPUT")"
BASENAME="$(basename "$INPUT" .tex)"
OUTPUT="${DIR}/${BASENAME}.pdf"

echo "Building ${OUTPUT} from ${INPUT}..."

pdflatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT"
pdflatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT"

rm -f "${DIR}/${BASENAME}.log" "${DIR}/${BASENAME}.out" "${DIR}/${BASENAME}.aux"

echo "Done."