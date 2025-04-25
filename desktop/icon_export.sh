#!/bin/bash

# This script runs the icon_export.js node script to generate .ico files from the SVG icon

node desktop/icon_export.js

if [ $? -eq 0 ]; then
  echo "Icon export completed successfully."
else
  echo "Icon export failed."
fi