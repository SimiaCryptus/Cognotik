#!/bin/bash
    set -euo pipefail

    # Builds the v2 client and stages it where the JVM webui module serves it.
    # Mirrors the (deprecated) webapp/build.sh contract.

    rm -rf build
    npm install
    npm run build

    TARGET_APP=../webui/src/main/resources/application
    TARGET_WELCOME=../webui/src/main/resources/welcome/static

    mkdir -p "$TARGET_APP" "$TARGET_WELCOME"
    rm -rf "$TARGET_APP"/* "$TARGET_WELCOME"/*

    cp -r build/* "$TARGET_APP"/
    cp -r build/static/* "$TARGET_WELCOME"/