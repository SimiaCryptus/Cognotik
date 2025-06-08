#!/usr/bin/env node
/**
 * Audio generation script for Cypress test narrations using Groq API.
 * This script processes the narrations.json file and generates audio files using Groq's text-to-speech.
 */

const fs = require('fs').promises;
const path = require('path');
const https = require('https');
const { createWriteStream } = require('fs');

class GroqNarrationAudioGenerator {
    constructor(options = {}) {
        this.apiKey = options.apiKey || process.env.GROQ_API_KEY;
        this.outputFormat = (options.outputFormat || 'mp3').toLowerCase();
        this.voice = options.voice || 'Arista-PlayAI';
        this.speed = options.speed || 1.0;
        this.normalizeVolume = options.normalizeVolume !== false;
        
        if (!this.apiKey) {
            throw new Error('GROQ_API_KEY environment variable is required');
        }
    }

    async generateAudio(text, outputPath) {
        try {
            console.log(`Generating audio for: ${text.substring(0, 50)}...`);

            const audioBuffer = await this.callGroqTTS(text);
            
            // Ensure output directory exists
            await fs.mkdir(path.dirname(outputPath), { recursive: true });
            
            // Write audio file
            await fs.writeFile(outputPath, audioBuffer);
            
            console.log(`Audio saved to: ${outputPath}`);
            return true;
        } catch (error) {
            console.error(`Error generating audio: ${error.message}`);
            return false;
        }
    }

    async callGroqTTS(text) {
        return new Promise((resolve, reject) => {
            const postData = JSON.stringify({
                model: 'playai-tts',
                input: text,
                voice: this.voice,
                response_format: this.outputFormat,
                speed: this.speed
            });

            const options = {
                hostname: 'api.groq.com',
                port: 443,
                path: '/openai/v1/audio/speech',
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.apiKey}`,
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(postData)
                }
            };

            const req = https.request(options, (res) => {
                const chunks = [];

                if (res.statusCode !== 200) {
                    let errorData = '';
                    res.on('data', chunk => errorData += chunk);
                    res.on('end', () => {
                        reject(new Error(`Groq API error: ${res.statusCode} - ${errorData}`));
                    });
                    return;
                }

                res.on('data', (chunk) => {
                    chunks.push(chunk);
                });

                res.on('end', () => {
                    const audioBuffer = Buffer.concat(chunks);
                    resolve(audioBuffer);
                });
            });

            req.on('error', (error) => {
                reject(new Error(`Request error: ${error.message}`));
            });

            req.write(postData);
            req.end();
        });
    }
}

async function loadNarrations(narrationsFile) {
    try {
        const data = await fs.readFile(narrationsFile, 'utf-8');
        return JSON.parse(data);
    } catch (error) {
        console.error(`Error loading narrations file: ${error.message}`);
        return {};
    }
}

async function saveNarrations(narrations, narrationsFile) {
    try {
        await fs.writeFile(narrationsFile, JSON.stringify(narrations, null, 2), 'utf-8');
        console.log(`Updated narrations saved to: ${narrationsFile}`);
    } catch (error) {
        console.error(`Error saving narrations file: ${error.message}`);
    }
}

function parseArgs() {
    const args = process.argv.slice(2);
    const options = {
        narrationsFile: 'cypress/fixtures/narrations.json',
        audioDir: 'cypress/fixtures/audio',
        format: 'mp3',
        voice: 'Arista-PlayAI',
        speed: 1.0,
        force: false,
        keys: null,
        noNormalize: false
    };

    for (let i = 0; i < args.length; i++) {
        const arg = args[i];
        const nextArg = args[i + 1];

        switch (arg) {
            case '--narrations-file':
                options.narrationsFile = nextArg;
                i++;
                break;
            case '--audio-dir':
                options.audioDir = nextArg;
                i++;
                break;
            case '--format':
                options.format = nextArg;
                i++;
                break;
            case '--voice':
                options.voice = nextArg;
                i++;
                break;
            case '--speed':
                options.speed = parseFloat(nextArg);
                i++;
                break;
            case '--force':
                options.force = true;
                break;
            case '--no-normalize':
                options.noNormalize = true;
                break;
            case '--keys':
                options.keys = [];
                i++;
                while (i < args.length && !args[i].startsWith('--')) {
                    options.keys.push(args[i]);
                    i++;
                }
                i--; // Adjust for the outer loop increment
                break;
            case '--help':
                console.log(`
Usage: node generate-narration-audio-groq.js [options]

Options:
  --narrations-file <path>    Path to narrations JSON file (default: cypress/fixtures/narrations.json)
  --audio-dir <path>          Directory to save audio files (default: cypress/fixtures/audio)
  --format <format>           Output audio format: mp3, wav, flac, opus (default: mp3)
  --voice <voice>             Voice to use: alloy, echo, fable, onyx, nova, shimmer (default: alloy)
  --speed <speed>             Speech speed 0.25-4.0 (default: 1.0)
  --force                     Regenerate existing audio files
  --no-normalize              Disable volume normalization
  --keys <key1> <key2>...     Only generate audio for specific keys
  --help                      Show this help message

Environment Variables:
  GROQ_API_KEY               Required: Your Groq API key
                `);
                process.exit(0);
                break;
        }
    }

    return options;
}

async function main() {
    try {
        const options = parseArgs();
        
        // Resolve paths
        const scriptDir = path.dirname(__filename);
        const narrationsFile = path.resolve(scriptDir, options.narrationsFile);
        const audioDir = path.resolve(scriptDir, options.audioDir);

        // Check if narrations file exists
        try {
            await fs.access(narrationsFile);
        } catch {
            console.error(`Narrations file not found: ${narrationsFile}`);
            process.exit(1);
        }

        // Load narrations
        const narrations = await loadNarrations(narrationsFile);
        if (Object.keys(narrations).length === 0) {
            console.error('No narrations found');
            process.exit(1);
        }

        // Initialize audio generator
        const generator = new GroqNarrationAudioGenerator({
            outputFormat: options.format,
            voice: options.voice,
            speed: options.speed,
            normalizeVolume: !options.noNormalize
        });

        console.log(`Using Groq TTS with voice: ${options.voice}, speed: ${options.speed}`);

        // Create audio directory
        await fs.mkdir(audioDir, { recursive: true });

        // Process narrations
        let updated = false;
        const keys = options.keys || Object.keys(narrations);
        
        for (const key of keys) {
            if (!narrations[key]) {
                console.warn(`Narration key '${key}' not found, skipping`);
                continue;
            }

            const narration = narrations[key];
            const text = narration.text;
            
            if (!text) {
                console.warn(`No text found for key '${key}', skipping`);
                continue;
            }

            // Generate filename
            const audioFilename = `${key}.${options.format}`;
            const audioPath = path.join(audioDir, audioFilename);

            // Skip if file exists and not forcing
            try {
                await fs.access(audioPath);
                if (!options.force) {
                    console.log(`Audio already exists for ${key}, skipping`);
                    if (!narration.audio) {
                        narration.audio = audioFilename;
                        updated = true;
                    }
                    continue;
                }
            } catch {
                // File doesn't exist, continue with generation
            }

            // Generate audio
            if (await generator.generateAudio(text, audioPath)) {
                narration.audio = audioFilename;
                updated = true;
            } else {
                console.error(`Failed to generate audio for ${key}`);
            }
        }

        // Save updated narrations
        if (updated) {
            await saveNarrations(narrations, narrationsFile);
        }

        console.log('Audio generation complete');
        process.exit(0);
    } catch (error) {
        console.error(`Fatal error: ${error.message}`);
        process.exit(1);
    }
}

if (require.main === module) {
    main();
}

module.exports = { GroqNarrationAudioGenerator };