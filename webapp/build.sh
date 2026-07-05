#!/bin/bash
# This script is now deprecated - use Gradle build instead
echo "Warning: This script is deprecated. Use 'gradle build' from the webui directory instead."
echo "Continuing with legacy build..."


rm -rf build
rm -rf ../webui/src/main/resources/application/*
rm -rf ../webui/src/main/resources/welcome/static/*

npm install
npm run build

cp -r build/* ../webui/src/main/resources/application/
cp -r build/static/* ../webui/src/main/resources/welcome/static/