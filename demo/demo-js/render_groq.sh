#!/bin/bash

# Set your Groq API key by reading .env file
if [ -f .env ]; then
  export $(cat .env | xargs)
else
  echo ".env file not found. Please create it with your Groq API key."
  exit 1
fi

# Generate audio for Cypress fixtures
node ./scripts/generate-narration-audio-groq.js \
  --narrations-file ../cypress/fixtures/narrations.json \
  --audio-dir ../../../desktop/src/main/resources/welcome/audio \
  --format mp3 \
  --voice $GROQ_VOICE \
  --speed $GROQ_SPEED

# Generate audio for command autofix narrations
node ./scripts/generate-narration-audio-groq.js \
  --narrations-file ../../src/main/resources/narrations/command-autofix.json \
  --audio-dir ../../src/main/resources/narrations/command-autofix/ \
  --format mp3 \
  --voice $GROQ_VOICE \
  --speed $GROQ_SPEED
