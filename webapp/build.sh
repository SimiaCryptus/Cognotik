#!/bin/bash

rm -rf build
rm -rf ../webui/src/main/resources/application/*
rm -rf ../webui/src/main/resources/welcome/static/*

npm install
npm run build

cp -r build/* ../webui/src/main/resources/application/
cp -r build/static/* ../webui/src/main/resources/welcome/static/
