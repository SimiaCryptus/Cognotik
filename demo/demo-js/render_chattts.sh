PYTHONPATH=../../../ChatTTS python3 ./scripts/generate-narration-audio.py \
  --narrations-file ../cypress/fixtures/narrations.json \
  --audio-dir ../../../desktop/src/main/resources/welcome/audio \
  --format mp3

PYTHONPATH=../../../ChatTTS python3 ./scripts/generate-narration-audio.py \
  --narrations-file ../../src/main/resources/narrations/command-autofix.json \
  --audio-dir ../../src/main/resources/narrations/command-autofix/ \
  --format wav



