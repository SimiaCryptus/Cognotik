# Academic Paper Formatting Guide & Conversion Tools

## Quick Conversion Pipeline

### Step 1: Markdown to LaTeX Conversion

```bash
# Install pandoc if you haven't already
# On macOS: brew install pandoc
# On Ubuntu: sudo apt-get install pandoc
# On Windows: Download from https://pandoc.org/installing.html

# Convert markdown to LaTeX
pandoc your_paper.md -o paper.tex --bibliography=references.bib --citeproc

# Or with more options:
pandoc your_paper.md \
  -o paper.tex \
  --bibliography=references.bib \
  --citeproc \
  --template=ieee-template.tex \
  --filter pandoc-crossref
```

### Step 2: HTML Results to LaTeX Tables/Figures

```python
# Python script to convert HTML results to LaTeX
from bs4 import BeautifulSoup
import pandas as pd

def html_table_to_latex(html_file, output_file):
    """Convert HTML tables to LaTeX format"""
    with open(html_file, 'r') as f:
        soup = BeautifulSoup(f.read(), 'html.parser')

    tables = soup.find_all('table')
    latex_tables = []

    for i, table in enumerate(tables):
        # Extract table data
        rows = []
        for tr in table.find_all('tr'):
            row = [td.get_text().strip() for td in tr.find_all(['td', 'th'])]
            rows.append(row)

        # Convert to DataFrame for easier LaTeX conversion
        if rows:
            df = pd.DataFrame(rows[1:], columns=rows[0])
            latex_table = df.to_latex(index=False, escape=False)

            # Wrap in proper LaTeX table environment
            latex_output = f"""
\\begin{{table}}[htbp]
\\centering
\\caption{{Results Table {i+1}}}
\\label{{tab:results_{i+1}}}
{latex_table}
\\end{{table}}
"""
            latex_tables.append(latex_output)

    # Save to file
    with open(output_file, 'w') as f:
        f.write('\n'.join(latex_tables))

# Usage
html_table_to_latex('results.html', 'tables.tex')
```

### Step 3: IEEE/ACM/arXiv Template Setup

Choose your target venue:

#### Option A: arXiv (Recommended for your case)

```latex
\documentclass{article}
\usepackage[utf8]{inputenc}
\usepackage{amsmath,amsfonts,amssymb}
\usepackage{graphicx}
\usepackage{hyperref}
\usepackage{booktabs}
\usepackage{algorithm}
\usepackage{algorithmic}

\title{QQN: Revealing the Natural Geometry of Optimization Through Quadratic Interpolation}
\author{Andrew Charneski\\SimiaCryptus Software}
\date{\today}

\begin{document}
\maketitle

% Your content here
\input{converted_content.tex}
\input{tables.tex}

\bibliographystyle{plain}
\bibliography{references}
\end{document}
```

#### Option B: IEEE Format

```latex
\documentclass[conference]{IEEEtran}
\usepackage{cite}
\usepackage{amsmath,amssymb,amsfonts}
\usepackage{algorithmic}
\usepackage{graphicx}
\usepackage{textcomp}
\usepackage{xcolor}

\begin{document}
\title{QQN: Revealing the Natural Geometry of Optimization Through Quadratic Interpolation}
\author{\IEEEauthorblockN{Andrew Charneski}
\IEEEauthorblockA{SimiaCryptus Software\\
Email: andrew@simiacryptus.com}}
\maketitle

% Content here
\end{document}
```

## Automated Conversion Script

```bash
#!/bin/bash
# academic_converter.sh - Complete conversion pipeline

echo "Academic Paper Converter"
echo "========================"

# Check if required tools are installed
command -v pandoc >/dev/null 2>&1 || { echo "Please install pandoc first"; exit 1; }

# Create working directory
mkdir -p academic_paper
cd academic_paper

# Copy source files
cp ../your_paper.md .
cp ../results.html .
cp -r ../figures . 2>/dev/null || echo "No figures directory found"

# Convert markdown to LaTeX
echo "Converting markdown to LaTeX..."
pandoc your_paper.md -o content.tex \
  --bibliography=../references.bib \
  --citeproc \
  --wrap=preserve \
  --top-level-division=section

# Create main LaTeX file
cat > paper.tex << 'EOF'
\documentclass{article}
\usepackage[utf8]{inputenc}
\usepackage[margin=1in]{geometry}
\usepackage{amsmath,amsfonts,amssymb}
\usepackage{graphicx}
\usepackage{hyperref}
\usepackage{booktabs}
\usepackage{longtable}
\usepackage{algorithm}
\usepackage{algorithmic}
\usepackage{natbib}
\usepackage{url}

% Fix any Unicode issues
\DeclareUnicodeCharacter{2212}{-}
\DeclareUnicodeCharacter{2264}{\leq}
\DeclareUnicodeCharacter{2265}{\geq}

\title{QQN: Revealing the Natural Geometry of Optimization Through Quadratic Interpolation}
\author{Andrew Charneski\\SimiaCryptus Software}
\date{\today}

\begin{document}
\maketitle

\input{content.tex}

\bibliographystyle{plainnat}
\bibliography{references}

\end{document}
EOF

# Convert HTML results to LaTeX tables (requires Python script above)
python3 << 'EOF'
from bs4 import BeautifulSoup
import re

def clean_html_to_latex(html_file):
    with open(html_file, 'r') as f:
        content = f.read()

    soup = BeautifulSoup(content, 'html.parser')

    # Extract tables and convert to LaTeX
    tables = soup.find_all('table')
    latex_content = []

    for i, table in enumerate(tables):
        rows = []
        for tr in table.find_all('tr'):
            cells = []
            for td in tr.find_all(['td', 'th']):
                text = td.get_text().strip()
                # Clean up text for LaTeX
                text = text.replace('%', '\\%')
                text = text.replace('_', '\\_')
                text = text.replace('#', '\\#')
                cells.append(text)
            if cells:
                rows.append(' & '.join(cells) + ' \\\\')

        if rows:
            ncols = len(rows[0].split(' & ')) if rows else 1
            col_spec = 'l' * ncols

            latex_table = f"""
\\begin{{table}}[htbp]
\\centering
\\caption{{Experimental Results Table {i+1}}}
\\label{{tab:results_{i+1}}}
\\begin{{tabular}}{{{col_spec}}}
\\toprule
{chr(10).join(rows[:1])}
\\midrule
{chr(10).join(rows[1:])}
\\bottomrule
\\end{{tabular}}
\\end{{table}}
"""
            latex_content.append(latex_table)

    # Save tables
    with open('tables.tex', 'w') as f:
        f.write('\n'.join(latex_content))

try:
    clean_html_to_latex('results.html')
    print("HTML tables converted to LaTeX")
except Exception as e:
    print(f"HTML conversion failed: {e}")
EOF

# Compile LaTeX
echo "Compiling LaTeX..."
pdflatex paper.tex
bibtex paper || echo "No bibliography to process"
pdflatex paper.tex
pdflatex paper.tex

echo "Done! Check paper.pdf"
echo "Files created:"
echo "- paper.tex (main LaTeX file)"
echo "- content.tex (converted content)"
echo "- tables.tex (converted tables)"
echo "- paper.pdf (final PDF)"
```

## Manual Cleanup Checklist

After automated conversion, manually fix:

### 1. Citations

```latex
% Convert from markdown citations
[Smith et al. 2020] → \citep{smith2020}
[@smith2020] → \citep{smith2020}
```

### 2. Math Equations

```latex
% Ensure equations are properly formatted
$$f(x) = x^2$$ →
\begin{equation}
f(x) = x^2
\label{eq:example}
\end{equation}
```

### 3. Figures

```latex
% Replace markdown figures with LaTeX
![Caption](figure.png) →
\begin{figure}[htbp]
\centering
\includegraphics[width=0.8\textwidth]{figure.png}
\caption{Your caption here}
\label{fig:example}
\end{figure}
```

### 4. Algorithm Blocks

```latex
\begin{algorithm}
\caption{QQN Algorithm}
\label{alg:qqn}
\begin{algorithmic}
\STATE Initialize L-BFGS memory
\WHILE{not converged}
    \STATE Compute gradient $g_k = \nabla f(x_k)$
    \STATE Compute L-BFGS direction $d_{LBFGS}$
    \STATE Define path: $d(t) = t(1-t)(-g_k) + t^2 d_{LBFGS}$
    \STATE Find $t^* = \arg\min_{t \in [0,1]} f(x_k + d(t))$
    \STATE Update: $x_{k+1} = x_k + d(t^*)$
\ENDWHILE
\end{algorithmic}
\end{algorithm}
```

## Quick Commands

```bash
# One-liner for basic conversion
pandoc paper.md -o paper.pdf --bibliography=refs.bib --citeproc

# For arXiv submission
pandoc paper.md -o paper.tex --bibliography=refs.bib --citeproc
# Then manually clean up and compile

# For HTML to PDF (if you want to skip LaTeX)
pandoc paper.md -o paper.pdf --css=academic.css --self-contained
```

## File Structure for Submission

```
paper_submission/
├── paper.tex           # Main LaTeX file
├── paper.pdf           # Compiled PDF
├── references.bib      # Bibliography
├── figures/            # All figures
│   ├── convergence.png
│   ├── performance.pdf
│   └── ...
├── tables.tex          # Converted tables
└── supplementary/      # Additional materials
    ├── code.zip
    └── data.csv
```

This should get you from markdown + HTML to a properly formatted academic paper ready for submission to arXiv or any conference/journal.
