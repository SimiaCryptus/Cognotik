#!/usr/bin/env bash
set -euo pipefail

# Configuration
GHIDRA_VERSION="${GHIDRA_VERSION:-11.2.1}"
GHIDRA_DATE="${GHIDRA_DATE:-20241105}"
INSTALL_BASE_DIR="${INSTALL_BASE_DIR:-/opt}"
GHIDRA_DIR_NAME="ghidra_${GHIDRA_VERSION}_PUBLIC"
GHIDRA_INSTALL_DIR="${INSTALL_BASE_DIR}/${GHIDRA_DIR_NAME}"

echo "=== Installing Prerequisites & Ghidra ${GHIDRA_VERSION} ==="

# Use sudo if not running as root
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  if command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
  else
    echo "Error: This script requires root privileges or sudo to install packages to ${INSTALL_BASE_DIR}."
    exit 1
  fi
fi

# 1. Install Java (JDK 21+ required for Ghidra 11+) and basic utilities
install_dependencies() {
  echo "Checking and installing dependencies (JDK 21, wget, unzip)..."
  if command -v apt-get >/dev/null 2>&1; then
    $SUDO apt-get update
    $SUDO apt-get install -y wget unzip openjdk-21-jdk
  elif command -v dnf >/dev/null 2>&1; then
    $SUDO dnf install -y wget unzip java-21-openjdk-devel
  elif command -v yum >/dev/null 2>&1; then
    $SUDO yum install -y wget unzip java-21-openjdk-devel
  elif command -v pacman >/dev/null 2>&1; then
    $SUDO pacman -Sy --noconfirm wget unzip jdk21-openjdk
  elif command -v brew >/dev/null 2>&1; then
    brew install wget unzip openjdk@21
  else
    echo "Warning: Unsupported package manager. Ensure JDK 21+, wget, and unzip are installed manually."
  fi
}

install_dependencies

# Verify Java installation
if ! command -v java >/dev/null 2>&1; then
  echo "Error: Java is not installed or not in PATH."
  exit 1
fi
java -version

# 2. Download and Extract Ghidra
if [ -d "${GHIDRA_INSTALL_DIR}" ] && [ -f "${GHIDRA_INSTALL_DIR}/support/analyzeHeadless" ]; then
  echo "Ghidra is already installed at ${GHIDRA_INSTALL_DIR}"
else
  DOWNLOAD_URL="https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_${GHIDRA_VERSION}_build/ghidra_${GHIDRA_VERSION}_PUBLIC_${GHIDRA_DATE}.zip"
  TMP_ZIP="/tmp/ghidra_${GHIDRA_VERSION}.zip"

  echo "Downloading Ghidra from ${DOWNLOAD_URL}..."
  wget -q --show-progress -O "${TMP_ZIP}" "${DOWNLOAD_URL}"

  echo "Extracting Ghidra to ${INSTALL_BASE_DIR}..."
  $SUDO mkdir -p "${INSTALL_BASE_DIR}"
  $SUDO unzip -q -o "${TMP_ZIP}" -d "${INSTALL_BASE_DIR}"
  rm -f "${TMP_ZIP}"
fi

# 3. Configure Symlinks and Environment Variables
echo "Configuring environment variables and symlinks..."

# Create a stable symlink at /opt/ghidra
GHIDRA_SYMLINK="${INSTALL_BASE_DIR}/ghidra"
$SUDO ln -sfn "${GHIDRA_INSTALL_DIR}" "${GHIDRA_SYMLINK}"

# Symlink analyzeHeadless to /usr/local/bin so it is available on system PATH
if [ -d "/usr/local/bin" ]; then
  $SUDO ln -sfn "${GHIDRA_SYMLINK}/support/analyzeHeadless" /usr/local/bin/analyzeHeadless
fi

# Persist GHIDRA_INSTALL_DIR in /etc/profile.d if available
if [ -d "/etc/profile.d" ]; then
  $SUDO tee /etc/profile.d/ghidra.sh >/dev/null <<EOF
export GHIDRA_INSTALL_DIR="${GHIDRA_SYMLINK}"
export PATH="\$PATH:${GHIDRA_SYMLINK}/support"
EOF
  $SUDO chmod +x /etc/profile.d/ghidra.sh
fi

echo ""
echo "=== Installation Complete ==="
echo "Ghidra installed at: ${GHIDRA_INSTALL_DIR}"
echo "Symlinked at:        ${GHIDRA_SYMLINK}"
echo "Headless Analyzer:   ${GHIDRA_SYMLINK}/support/analyzeHeadless"
echo ""
echo "To use in your current shell session, run:"
echo "  export GHIDRA_INSTALL_DIR=\"${GHIDRA_SYMLINK}\""
echo "  export PATH=\"\$PATH:${GHIDRA_SYMLINK}/support\""