#!/bin/bash

# Initialize a local venv
python3 -m venv .venv
source .venv/bin/activate
# Install the required dependencies
pip install piper-tts
