#!/bin/bash
# This script is now deprecated - use Gradle build instead
echo "Warning: This script is deprecated. Use 'gradle build' from the webui directory instead."
echo "Continuing with legacy build..."
# Check if Node.js and npm are properly installed
if ! command -v node &> /dev/null; then
    echo "Error: Node.js is not installed or not in PATH"
    exit 1
fi
if ! command -v npm &> /dev/null; then
    echo "Error: npm is not installed or not in PATH"
    exit 1
fi
# Check Node.js version compatibility
NODE_VERSION=$(node --version | cut -d'v' -f2)
REQUIRED_VERSION="12.0.0"
if ! node -e "process.exit(require('semver').gte('$NODE_VERSION', '$REQUIRED_VERSION') ? 0 : 1)" 2>/dev/null; then
    echo "Warning: Node.js version $NODE_VERSION may not be compatible. Required: >= $REQUIRED_VERSION"
fi
# Clear npm cache to resolve potential corruption
echo "Clearing npm cache..."
npm cache clean --force || echo "Warning: Failed to clean npm cache"
# Verify npm configuration
echo "Verifying npm configuration..."
npm config list || echo "Warning: npm config verification failed"


rm -rf build
rm -rf ../webui/src/main/resources/application/*
rm -rf ../webui/src/main/resources/welcome/static/*

# Install dependencies with error handling
echo "Installing dependencies..."
if ! npm install; then
    echo "Error: npm install failed. Trying with --legacy-peer-deps..."
    if ! npm install --legacy-peer-deps; then
        echo "Error: npm install failed even with legacy peer deps. Trying to reinstall npm..."
        # Try to fix npm installation
        npm install -g npm@latest || echo "Failed to update npm"
        npm install --legacy-peer-deps || {
            echo "Error: All npm install attempts failed"
            exit 1
        }
    fi
fi

# Build with error handling
echo "Building application..."
if ! npm run build; then
    echo "Error: npm run build failed"
    exit 1
fi

# Verify build output exists
if [ ! -d "build" ]; then
    echo "Error: Build directory was not created"
    exit 1
fi

cp -r build/* ../webui/src/main/resources/application/
cp -r build/static/* ../webui/src/main/resources/welcome/static/
echo "Build completed successfully!"