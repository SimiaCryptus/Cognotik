## 1. Prerequisites & System Impact
*   **Privileges:** You must have `sudo` (administrator) access to run this script.
*   **Disk Space:** This installation is massive. It installs full LaTeX distributions, multiple compiler toolchains, and heavy cloud CLIs. Ensure you have at least **10GB+** of free space.
*   **Time:** Depending on your internet connection, this script may take 15–45 minutes to complete.

---

## 2. Core Build Tools & Utilities
These are the fundamental tools required to build software and manage the system.

| Tool                | Description                                                                                   | Verification Command |
|:--------------------|:----------------------------------------------------------------------------------------------|:---------------------|
| **Git**             | The standard for version control.                                                             | `git --version`      |
| **OpenSSH**         | Client for connecting to remote servers securely.                                             | `ssh -V`             |
| **Build Essential** | A meta-package that installs `gcc`, `g++`, and `make`. Required for compiling C/C++ software. | `gcc --version`      |
| **CMake**           | A cross-platform tool used to control the software compilation process (generates Makefiles). | `cmake --version`    |
| **Zsh**             | An extended shell (alternative to Bash) often used for its theming and plugin capabilities.   | `zsh --version`      |
| **Curl / Wget**     | Command-line tools for downloading files from the internet.                                   | `curl --version`     |

---

## 3. General Programming Languages
The script installs a wide variety of language runtimes and compilers via the APT package manager (and a custom script for Julia).

| Language | Description |
| :--- | :--- |
| **Python 3** | Includes `pip` (package manager) and `venv` (virtual environments). Used for scripting, AI, and web dev. |
| **Rust** | Installs `rustc` (compiler) and `cargo` (package manager). Known for memory safety and performance. |
| **Java (JDK)** | Installs the default Java Development Kit. Required for Java, Kotlin, and Scala development. |
| **Go (Golang)** | Google's systems language. Great for cloud infrastructure and microservices. |
| **Ruby** | Dynamic, open-source programming language (famous for Ruby on Rails). |
| **Julia** | *Note: Installed via official script, not APT.* High-performance language for technical computing. |

---

## 4. Scientific, Math & Formal Logic Tools
This section sets this environment apart from a standard web-dev setup. It includes tools for academic research, theorem proving, and advanced mathematics.

### Mathematical Computation
*   **Octave:** A high-level language, primarily intended for numerical computations (open-source alternative to MATLAB).
*   **GAP:** System for computational discrete algebra (Group theory).
*   **Pari/GP:** Computer algebra system designed for fast computations in number theory.
*   **Maxima:** A system for the manipulation of symbolic and numerical expressions.
*   **Singular:** A Computer Algebra System for polynomial computations.
*   **SageMath:** *Note: This is currently commented out in the script and will not be installed.*

### Logic & Theorem Provers
*   **SWI-Prolog:** A comprehensive Prolog implementation (logic programming).
*   **Coq:** A formal proof management system.
*   **Agda:** A dependently typed functional programming language and proof assistant.
*   **Z3:** A high-performance theorem prover from Microsoft Research.

### Document & Media Processing
*   **LaTeX (TexLive):** The standard for scientific typesetting. The script installs the "recommended" set plus extra pictures/latex packages.
*   **Graphviz:** Graph visualization software (converts text descriptions to diagrams).
*   **Pandoc:** The "Swiss-army knife" of document conversion (e.g., Markdown to PDF).
*   **FFmpeg:** A complete solution to record, convert and stream audio and video.

---

## 5. Build Systems (Java Ecosystem)
These tools automate the creation of executable applications from source code, primarily for the JVM.

*   **Gradle:** Flexible build automation (standard for Android).
*   **Maven:** Project management and comprehension tool (standard for Enterprise Java).
*   **Ant:** An older Java library and command-line tool for driving processes.

---

## 6. Node.js Ecosystem
*   **Node.js:** JavaScript runtime built on Chrome's V8 engine.
*   **NPM:** Node Package Manager.

---

## 7. Cloud & DevOps (Snap Packages)
The script switches to using `snap` (a universal package manager) for modern cloud tools to ensure newer versions than what APT usually provides.

| Tool | Description |
| :--- | :--- |
| **PowerShell** | Microsoft's task automation and configuration management framework (Cross-platform). |
| **Terraform** | Infrastructure as Code (IaC) tool to provision cloud resources safely. |
| **Kubectl** | The command line tool for communicating with a Kubernetes cluster control plane. |
| **Google Cloud CLI** | Command-line interface for Google Cloud Platform products. |
| **AWS CLI** | Command-line interface for Amazon Web Services. |
| **Docker** | Platform for developing, shipping, and running applications in containers. |

---

## 8. Language Servers (LSP)
The script installs Language Server Protocols. These are backend tools that power IDEs (like VS Code, Neovim, or Emacs) to provide features like **Auto-complete, Go-to-definition, and Error highlighting**.

*   **TypeScript/JS:** `typescript-language-server`
*   **Bash:** `bash-language-server`
*   **Docker:** `dockerfile-language-server-nodejs`
*   **YAML:** `yaml-language-server`
*   **C/C++:** `clangd`
*   **Go:** `gopls` (Installed via `go install`)

---

## 9. Manual Steps & Post-Install
The script attempts to automate as much as possible, but some things require manual intervention.

### The "Skipped" Tools
The script explicitly mentions tools it **did not** install because they are complex or lack package managers. You must install these manually if needed:
1.  **CVC5:** An SMT solver.
2.  **Lean:** A theorem prover (install via `elan`).
3.  **Isabelle:** A generic proof assistant.
4.  **Kotlin LS:** Kotlin Language Server.

### Docker Permissions
At the very end, the script adds your current user to the `docker` group.
*   **Action Required:** You must **log out and log back in** (or restart your computer) for this to take effect.
*   **Benefit:** This allows you to run `docker run ...` without typing `sudo` every time.

### Python LSP
The line `#pip3 install --upgrade python-lsp-server` is commented out. If you need Python autocompletion in your editor, you will need to uncomment this or run it manually.