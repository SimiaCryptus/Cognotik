#!/bin/bash
    set -euo pipefail

    # Builds the v2 client and stages it where the JVM webui module serves it.
    # Mirrors the (deprecated) webapp/build.sh contract.
     NODE_VER="$(node --version 2>/dev/null || echo none)"
     if ! node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit((a===20&&b>=19)||(a===22&&b>=12)||a>22?0:1)' 2>/dev/null; then
       echo "error: Node.js ^20.19.0 || >=22.12.0 required (found $NODE_VER). Try: nvm install 22" >&2
       exit 1
     fi


    rm -rf build
    npm install
    npm run build

    TARGET_APP=../webui/src/main/resources/application
    TARGET_WELCOME=../webui/src/main/resources/welcome/static

    mkdir -p "$TARGET_APP" "$TARGET_WELCOME"
    rm -rf "$TARGET_APP"/* "$TARGET_WELCOME"/*

    cp -r build/* "$TARGET_APP"/
    cp -r build/static/* "$TARGET_WELCOME"/