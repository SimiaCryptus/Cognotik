#!/bin/bash

# Set your Groq API key
export GROQ_API_KEY="your-groq-api-key-here"

# Generate audio for Cypress fixtures
node ./scripts/generate-narration-audio-groq.js \
  --narrations-file ../cypress/fixtures/narrations.json \
  --audio-dir ../../../desktop/src/main/resources/welcome/audio \
  --format mp3 \
  --voice alloy \
  --speed 1.0

# Generate audio for command autofix narrations
node ./scripts/generate-narration-audio-groq.js \
  --narrations-file ../../src/main/resources/narrations/command-autofix.json \
  --audio-dir ../../src/main/resources/narrations/command-autofix/ \
  --format wav \
  --voice nova \
  --speed 0.9