#!/bin/bash
# Build paper.pdf from paper.tex.
#
#   ./build.sh [input.tex]
#
# Prefers `tectonic` (self-contained), then `xelatex`, then `pdflatex`.
# Everything printed here is captured into build.log.md by ops/render_pdf_op.md.

set -u

INPUT="${1:-paper.tex}"

if [ ! -f "$INPUT" ]; then
  echo "Error: '$INPUT' not found. Run the LaTeX lens first."
  exit 1
fi

DIR="$(cd "$(dirname "$INPUT")" && pwd)"
BASENAME="$(basename "$INPUT" .tex)"
OUTPUT="${DIR}/${BASENAME}.pdf"

echo "== Building ${BASENAME}.pdf from ${INPUT} =="

if command -v tectonic >/dev/null 2>&1; then
  echo "-- engine: tectonic"
  tectonic --keep-logs --outdir "$DIR" "$INPUT" || exit 1
elif command -v xelatex >/dev/null 2>&1; then
  echo "-- engine: xelatex (two passes)"
  xelatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT" || exit 1
  xelatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT" || exit 1
elif command -v pdflatex >/dev/null 2>&1; then
  echo "-- engine: pdflatex (two passes)"
  pdflatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT" || exit 1
  pdflatex -interaction=nonstopmode -output-directory="$DIR" "$INPUT" || exit 1
else
  echo "Error: no LaTeX engine found (tried tectonic, xelatex, pdflatex)."
  exit 127
fi

rm -f "${DIR}/${BASENAME}.aux" "${DIR}/${BASENAME}.out" "${DIR}/${BASENAME}.toc"

if [ -f "$OUTPUT" ]; then
  echo "== Done: ${BASENAME}.pdf =="
else
  echo "Error: engine finished but ${BASENAME}.pdf was not produced."
  exit 1
fi