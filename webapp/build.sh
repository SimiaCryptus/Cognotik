#!/bin/bash

npm install
rm -rf build
npm run build
cp -r build/* ../webui/src/main/resources/application/
cp -r build/static/* ../webui/src/main/resources/welcome/static/
