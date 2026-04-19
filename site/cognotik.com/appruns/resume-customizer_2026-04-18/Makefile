# Makefile for building resume PDFs/DOCs from TEX files
# Place this in a subdirectory containing TEX files

# Find all JSON files in current directory
JSON_FILES := $(shell find . -name '*.json')
TEX_FILES := $(shell find . -name '*.tex')
# Generate target file lists
PDF_FILES := $(TEX_FILES:.tex=.pdf)
DOCX_FILES := $(TEX_FILES:.tex=.docx)
# Default target: build all PDFs
.PHONY: all
all: $(PDF_FILES)
# Build PDFs from TEX files
%.pdf: %.tex
	@echo "Building $@ from $<..."
	-pdflatex -interaction=nonstopmode -output-directory=$(dir $<) $<
	-pdflatex -interaction=nonstopmode -output-directory=$(dir $<) $<
	-@rm -f $(dir $<)$*.log $(dir $<)$*.out $(dir $<)$*.aux
# Build DOCX from PDF (requires pandoc)
%.docx: %.tex
	@echo "Building $@ from $<..."
	-pandoc $< -o $(dir $<)$(notdir $@)
# Build all DOCX files
.PHONY: docx
pdf: $(PDF_FILES)
docx: $(DOCX_FILES)


# Clean generated files
.PHONY: clean
clean:
	@echo "Cleaning generated files..."
	@find . -name '*.pdf' -delete
	@find . -name '*.aux' -delete
	@find . -name '*.log' -delete
	@find . -name '*.out' -delete
	@find . -name '*.docx' -delete
	@find . -name '*.fls' -delete
	@find . -name '*.fdb_latexmk' -delete
	@find . -name '*.synctex.gz' -delete