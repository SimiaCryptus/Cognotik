#!/bin/bash

# Stop script on error
set -e

echo "Starting installation of tools defined in ToolProvider.kt..."
echo "Note: This requires sudo privileges and a significant amount of disk space."

# 1. Update Repositories and Enable Universe (needed for some math packages)
echo "--- Updating Repositories ---"
sudo apt update
sudo apt install -y software-properties-common
sudo add-apt-repository -y universe
sudo apt update

# 2. Core Build Tools & System Utilities
# Covers: Git, SSH, Gcc, Make, Cmake, Bash, Zsh
echo "--- Installing Core Build Tools ---"
sudo apt install -y \
    git \
    openssh-client \
    build-essential \
    cmake \
    zsh \
    curl \
    wget \
    unzip

# 3. Programming Languages (APT)
# Covers: Python, Rust, Jdk, Go, Ruby, Octave, Julia, Prolog, Gap, Coq, Agda
echo "--- Installing Programming Languages (APT) ---"
sudo apt install -y \
    python3 python3-pip python3-venv \
    rustc cargo \
    default-jdk \
    golang-go \
    php-cli \
    ruby-full \
    octave \
    swi-prolog \
    gap \
    coq \
    agda \
    ghc \
    ocaml opam

#curl -fsSL https://install.julialang.org | sh

#curl https://elan.lean-lang.org/elan-init.sh -sSf | sh

# 4. Build Systems & Package Managers
# Covers: Gradle, Maven, Ant
echo "--- Installing Build Systems ---"
sudo apt install -y \
    gradle \
    maven \
    ant

# 5. Node.js ecosystem
# Covers: Node, NPM
echo "--- Installing Node.js ---"
sudo apt install -y nodejs npm

# 6. Scientific & Math Tools
# Covers: Latex, Dot (Graphviz), Gnuplot, Pandoc, Ffmpeg, PariGP, Z3, Maxima, Singular, Sage
echo "--- Installing Scientific & Math Tools ---"
# Note: texlive-full is huge, using recommended to save space.
# Change to texlive-full if you need every package.
sudo apt install -y \
    texlive-latex-recommended texlive-pictures texlive-latex-extra \
    graphviz \
    gnuplot \
    pandoc \
    ffmpeg \
    pari-gp \
    z3 \
    maxima \
    singular
#    sudo apt install -y sagemath

# 7. Cloud & Modern CLI Tools (via Snap)
# Covers: Powershell, Terraform, Kubectl, Gcloud, Aws, Docker
echo "--- Installing Cloud Tools (Snap) ---"
# Check if snap is available
if command -v snap &> /dev/null; then
    sudo snap install powershell --classic
    sudo snap install terraform --classic
    sudo snap install kubectl --classic
    sudo snap install google-cloud-cli --classic
    sudo snap install aws-cli --classic
    sudo snap install docker
else
    echo "Snap not found. Skipping Snap packages (Powershell, Terraform, Cloud CLIs)."
fi

# 8. Language Servers (LSP)
# Covers items in the 'LanguageServer' object
echo "--- Installing Language Servers ---"

# Python LSP
#pip3 install --user "python-lsp-server[all]"

# Node/Typescript/Bash/Docker/Yaml LSPs (via NPM)
# Note: Using sudo for global npm install is common in scripts but check permissions in production
sudo npm install -g \
    typescript \
    typescript-language-server \
    bash-language-server \
    dockerfile-language-server-nodejs \
    yaml-language-server

# C/C++ LSP (Clangd)
sudo apt install -y clangd

# Go LSP
export PATH=$PATH:$(go env GOPATH)/bin
go install golang.org/x/tools/gopls@latest

# 9. Manual/Complex Installs (Placeholders)
echo "--- Notes on Complex Installs ---"
echo "The following tools from ToolProvider.kt were skipped due to complex manual installation requirements or lack of standard packages:"
echo "1. CVC5: Download binary from https://cvc5.github.io/"
echo "2. Lean: Install via elan (https://leanprover.github.io/elan/)"
echo "3. Isabelle: Download from https://isabelle.in.tum.de/"
echo "4. Kotlin Language Server: Usually installed via VSCode extension or built manually."

# 10. Post-Install Setup
echo "--- Post-Install Setup ---"
# Add user to docker group to run docker without sudo
if getent group docker > /dev/null; then
    sudo usermod -aG docker $USER
    echo "Added user to 'docker' group. You may need to log out and back in for this to take effect."
fi

echo "========================================"
echo "Installation Complete!"
echo "========================================"