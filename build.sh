#!/usr/bin/env bash

set -euo pipefail

# -----------------------------------------------------------------------------
# Cognotik Unified Build Script
# Supports sub-commands: setup, compile, test
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for terminal output formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Detect operating system and distribution
detect_os() {
    local os_type="unknown"
    local distro="unknown"

    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        os_type="linux"
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            distro="${ID:-unknown}"
        fi
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        os_type="macos"
        distro="macos"
    fi

    echo "${os_type}:${distro}"
}

# Ensure Gradle wrapper exists and has execute permissions
ensure_gradle_wrapper() {
    if [[ -f "./gradlew" ]]; then
        chmod +x ./gradlew
        return 0
    fi

    log_info "Gradle wrapper not found. Attempting to generate wrapper using installed Gradle..."
    if command -v gradle &>/dev/null; then
        gradle wrapper
        chmod +x ./gradlew
    else
        log_error "Neither './gradlew' nor system 'gradle' command was found."
        log_error "Please ensure Gradle is installed or wrapper files exist."
        exit 1
    fi
}

# Sub-command: setup
# Ensures system packages and runtime dependencies are available
setup() {
    log_info "Setting up environment and dependencies..."

    local os_info
    os_info=$(detect_os)
    local os_type="${os_info%%:*}"
    local distro="${os_info#*:}"

    log_info "Detected OS environment: OS=$os_type, Distro=$distro"

    case "$os_type" in
        linux)
            case "$distro" in
                ubuntu|debian)
                    log_info "Installing system packages via apt..."
                    SUDO=""
                    if command -v sudo &>/dev/null && [ "$(id -u)" -ne 0 ]; then
                        SUDO="sudo"
                    fi
                    $SUDO apt-get update -qq
                    $SUDO apt-get install -y -qq \
                        curl \
                        git \
                        unzip \
                        zip \
                        ca-certificates \
                        findutils \
                        build-essential \
                        openjdk-21-jdk || {
                            log_warn "openjdk-21-jdk package not available directly via standard apt repositories."
                            log_warn "Gradle Foojay Toolchain plugin will auto-provision JDK 21 during build execution."
                        }
                    ;;
                fedora|rhel|centos)
                    log_info "Installing system packages via dnf..."
                    SUDO=""
                    if command -v sudo &>/dev/null && [ "$(id -u)" -ne 0 ]; then
                        SUDO="sudo"
                    fi
                    $SUDO dnf install -y curl git unzip zip ca-certificates java-21-openjdk-devel || true
                    ;;
                *)
                    log_warn "Unsupported Linux distribution ($distro). Please ensure JDK 21, git, and curl are installed."
                    ;;
            esac
            ;;
        macos)
            log_info "macOS detected. Verifying dependencies..."
            if command -v brew &>/dev/null; then
                brew list openjdk@21 &>/dev/null || brew install openjdk@21 || true
            else
                log_warn "Homebrew not found. Ensure JDK 21 and build tools are installed manually."
            fi
            ;;
        *)
            log_warn "Unrecognized OS type: $os_type. Proceeding with existing system setup."
            ;;
    esac

    ensure_gradle_wrapper
    log_info "Setup completed successfully."
}

# Sub-command: compile
# Compiles all modules and builds artifacts
compile() {
    log_info "Compiling source code and generating artifacts..."
    ensure_gradle_wrapper
    ./gradlew assemble --no-daemon
    log_info "Compilation completed successfully."
}

# Sub-command: test
# Runs test suites across subprojects
test_suite() {
    log_info "Running project test suite..."
    ensure_gradle_wrapper
    ./gradlew test --no-daemon
    log_info "Tests completed successfully."
}

# Usage instruction display
usage() {
    echo "Usage: $0 {setup|compile|test}"
    echo ""
    echo "Sub-commands:"
    echo "  setup    Install system packages, toolchains, and environment dependencies."
    echo "  compile  Compile all module sources and build artifacts."
    echo "  test     Run unit and integration tests."
    exit 1
}

main() {
    if [[ $# -lt 1 ]]; then
        usage
    fi

    case "$1" in
        setup)
            setup
            ;;
        compile)
            compile
            ;;
        test)
            test_suite
            ;;
        *)
            log_error "Unknown sub-command: $1"
            usage
            ;;
    esac
}

main "$@"