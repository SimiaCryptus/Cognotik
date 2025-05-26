#!/usr/bin/env python3
"""
Audio generation script for Cypress test narrations.
This script processes the narrations.json file and generates audio files using ChatTTS.
"""

import os
import sys
import json
import argparse
from pathlib import Path
from typing import Dict, Any
import ChatTTS
import numpy as np
import soundfile as sf
from tools.audio import pcm_arr_to_mp3_view

class NarrationAudioGenerator:
    def __init__(self, chattts_path: str = None, output_format: str = "mp3"):
        self.chattts_path = chattts_path
        self.output_format = output_format.lower()
        self.chat = None
        self.speaker = None

    def initialize_chattts(self):
        try:
            self.chat = ChatTTS.Chat()
            print("Initializing ChatTTS...")

            # Load models
            if self.chattts_path and os.path.isdir(self.chattts_path):
                is_load = self.chat.load(source="custom", custom_path=self.chattts_path)
            else:
                is_load = self.chat.load(source="local")

            if not is_load:
                print("Failed to load ChatTTS models")
                return False

            # Sample a speaker
            self.speaker = self.chat.sample_random_speaker()
            print("ChatTTS initialized successfully")
            return True

        except Exception as e:
            print(f"Error initializing ChatTTS: {e}")
            return False

    def generate_audio(self, text: str, output_path: str) -> bool:
        """Generate audio file from text."""
        if not self.chat:
            print("ChatTTS not initialized")
            return False

        try:
            print(f"Generating audio for: {text[:50]}...")

            # Generate audio
            wavs = self.chat.infer(
                [text],
                params_infer_code=ChatTTS.Chat.InferCodeParams(
                    spk_emb=self.speaker,
                ),
            )

            if wavs and len(wavs) > 0:
                wav_data = wavs[0]

                # Save to file
                os.makedirs(os.path.dirname(output_path), exist_ok=True)
                
                if self.output_format == "mp3":
                    # Convert to MP3
                    mp3_data = pcm_arr_to_mp3_view(wav_data)
                    with open(output_path, 'wb') as f:
                        f.write(mp3_data)
                elif self.output_format == "wav":
                    # Save as WAV
                    sf.write(output_path, wav_data, 24000)
                else:
                    print(f"Unsupported output format: {self.output_format}")
                    return False

                print(f"Audio saved to: {output_path}")
                return True
            else:
                print("No audio generated")
                return False

        except Exception as e:
            print(f"Error generating audio: {e}")
            return False


def load_narrations(narrations_file: str) -> Dict[str, Any]:
    """Load narrations from JSON file."""
    try:
        with open(narrations_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading narrations file: {e}")
        return {}


def save_narrations(narrations: Dict[str, Any], narrations_file: str):
    """Save updated narrations to JSON file."""
    try:
        with open(narrations_file, 'w', encoding='utf-8') as f:
            json.dump(narrations, f, indent=2, ensure_ascii=False)
        print(f"Updated narrations saved to: {narrations_file}")
    except Exception as e:
        print(f"Error saving narrations file: {e}")


def main():
    parser = argparse.ArgumentParser(description="Generate audio files for Cypress test narrations")
    parser.add_argument(
        "--narrations-file",
        default="cypress/fixtures/narrations.json",
        help="Path to narrations JSON file"
    )
    parser.add_argument(
        "--audio-dir",
        default="cypress/fixtures/audio",
        help="Directory to save audio files"
    )
    parser.add_argument(
        "--chattts-path",
        help="Custom path to ChatTTS models"
    )
    parser.add_argument(
        "--format",
        choices=["mp3", "wav"],
        default="mp3",
        help="Output audio format (default: mp3)"
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Regenerate existing audio files"
    )
    parser.add_argument(
        "--keys",
        nargs="*",
        help="Only generate audio for specific keys"
    )

    args = parser.parse_args()

    # Resolve paths
    script_dir = Path(__file__).parent
    narrations_file = script_dir / args.narrations_file
    audio_dir = script_dir / args.audio_dir

    if not narrations_file.exists():
        print(f"Narrations file not found: {narrations_file}")
        return 1

    # Load narrations
    narrations = load_narrations(str(narrations_file))
    if not narrations:
        print("No narrations found")
        return 1

    # Initialize audio generator
    generator = NarrationAudioGenerator(args.chattts_path, args.format)
    if not generator.initialize_chattts():
        print("Skipping audio generation - ChatTTS not available")
        return 0

    # Create audio directory
    audio_dir.mkdir(parents=True, exist_ok=True)

    # Process narrations
    updated = False
    for key, narration in narrations.items():
        # Skip if specific keys requested and this isn't one
        if args.keys and key not in args.keys:
            continue

        text = narration.get('text', '')
        if not text:
            continue

        # Generate filename
        audio_filename = f"{key}.{args.format}"
        audio_path = audio_dir / audio_filename

        # Skip if file exists and not forcing
        if audio_path.exists() and not args.force:
            print(f"Audio already exists for {key}, skipping")
            if not narration.get('audio'):
                narration['audio'] = audio_filename
                updated = True
            continue

        # Generate audio
        if generator.generate_audio(text, str(audio_path)):
            narration['audio'] = audio_filename
            updated = True
        else:
            print(f"Failed to generate audio for {key}")

    # Save updated narrations
    if updated:
        save_narrations(narrations, str(narrations_file))

    print("Audio generation complete")
    return 0


if __name__ == "__main__":
    sys.exit(main())