/**
* audio.js - AudioEngine (ES module)
  * Procedural chiptune sound effects and music
 * Uses Web Audio API to generate all sounds without external files
 */
import { SFX, MUSIC } from './constants.js';

export class AudioEngine {
    constructor() {
        this.audioContext = null;
        this.masterGain = null;
        this.musicGain = null;
        this.sfxGain = null;
        this.musicPlaying = false;
        this.musicNodes = [];
        this.currentTheme = null;
        this.musicScheduler = null;
        this.enabled = true;
        this.musicEnabled = true;
        this.initialized = false;

        // Volume levels
         this.masterVolume = 0.7;
         this.musicVolume = 0.5;
         this.sfxVolume = 0.8;
    }

    /**
     * Initialize the audio context (must be called after user interaction)
     */
    init() {
        if (this.initialized) return;
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            this.audioContext = new AudioContext();

            // Master gain node
            this.masterGain = this.audioContext.createGain();
            this.masterGain.gain.setValueAtTime(this.masterVolume, this.audioContext.currentTime);
            this.masterGain.connect(this.audioContext.destination);

            // Music gain node
            this.musicGain = this.audioContext.createGain();
            this.musicGain.gain.setValueAtTime(this.musicVolume, this.audioContext.currentTime);
            this.musicGain.connect(this.masterGain);

            // SFX gain node
            this.sfxGain = this.audioContext.createGain();
            this.sfxGain.gain.setValueAtTime(this.sfxVolume, this.audioContext.currentTime);
            this.sfxGain.connect(this.masterGain);

            this.initialized = true;
        } catch (e) {
            console.warn('Web Audio API not supported:', e);
            this.enabled = false;
        }
    }

    /**

     * Create an oscillator with given parameters
     */
    createOscillator(type, frequency, startTime, duration, gainValue, destination) {
        if (!this.initialized || !this.enabled) return null;

        const oscillator = this.audioContext.createOscillator();
        const gainNode = this.audioContext.createGain();

        oscillator.type = type;
        oscillator.frequency.setValueAtTime(frequency, startTime);

        gainNode.gain.setValueAtTime(gainValue, startTime);
        gainNode.gain.exponentialRampToValueAtTime(0.001, startTime + duration);

        oscillator.connect(gainNode);
        gainNode.connect(destination || this.sfxGain);

        oscillator.start(startTime);
        oscillator.stop(startTime + duration + 0.01);

        return { oscillator, gainNode };
    }

    /**
     * Play a sound effect by name
     */
    play(soundName) {
        if (!this.enabled) return;
        if (!this.initialized) this.init();
        this.resume();

        const sounds = {
             [SFX.JUMP]:          () => this.playJump(),
             [SFX.COIN]:          () => this.playCoin(),
             [SFX.POWERUP]:       () => this.playPowerUp(),
             [SFX.POWERUP_SPAWN]: () => this.playPowerUpAppear(),
             [SFX.STOMP]:         () => this.playStomp(),
             [SFX.KICK]:          () => this.playKick(),
             [SFX.DEATH]:         () => this.playDeath(),
             [SFX.STAGE_CLEAR]:   () => this.playLevelComplete(),
             [SFX.GAME_OVER]:     () => this.playGameOver(),
             [SFX.BREAK_BLOCK]:   () => this.playBreakBlock(),
             [SFX.BUMP]:          () => this.playBump(),
             [SFX.PIPE]:          () => this.playPipe(),
             [SFX.FLAGPOLE]:      () => this.playFlagpole(),
             [SFX.ONE_UP]:        () => this.playExtraLife(),
             [SFX.FIREBALL]:      () => this.playFireball(),
             [SFX.PAUSE]:         () => this.playPause(),
        };

        if (sounds[soundName]) {
            sounds[soundName]();
        } else {
            console.warn(`Unknown sound: ${soundName}`);
        }
    }

    /**
      * Resume audio context if suspended (browser autoplay policy)
      */
     resume() {
         if (this.audioContext && this.audioContext.state === 'suspended') {
             this.audioContext.resume();
         }
         if (!this.initialized) this.init();
     }

     /**
      * Start overworld music (convenience method used by game.js)
      */
     startMusic() {
         this.playMusic('overworld');
     }

     /**
      * Stop music (alias)
      */
     stopMusic() {
         this.musicPlaying = false;
         this.currentTheme = null;

         if (this.musicScheduler) {
             clearTimeout(this.musicScheduler);
             this.musicScheduler = null;
         }

         this.musicNodes.forEach(node => {
             try { node.stop(); } catch (e) { /* already stopped */ }
         });
         this.musicNodes = [];
     }

     // ── Convenience SFX methods used by game.js / entities ──
      jump()       { this.play(SFX.JUMP); }
      coin()       { this.play(SFX.COIN); }
      stomp()      { this.play(SFX.STOMP); }
      powerUp()    { this.play(SFX.POWERUP); }
      oneUp()      { this.play(SFX.ONE_UP); }
      death()      { this.play(SFX.DEATH); }
      gameOver()   { this.play(SFX.GAME_OVER); }
      flagpole()   { this.play(SFX.FLAGPOLE); }
      blockBump()  { this.play(SFX.BUMP); }
      brickBreak() { this.play(SFX.BREAK_BLOCK); }

     /**
      * Play background music theme
     */
    playMusic(theme) {
        if (!this.musicEnabled) return;
        if (!this.initialized) this.init();
        this.resume();

        if (this.currentTheme === theme && this.musicPlaying) return;

        this.stopMusic();
        this.currentTheme = theme;
        this.musicPlaying = true;

        switch (theme) {
            case MUSIC.OVERWORLD:
                this.playOverworldTheme();
                break;
            case MUSIC.UNDERGROUND:
                this.playUndergroundTheme();
                break;
            case MUSIC.STARMAN:
                this.playStarmanTheme();
                break;
            case MUSIC.HURRY:
                this.playHurryTheme();
                break;
            default:
                this.playOverworldTheme();
        }
    }

    /**
     * Stop all music
     */
    stopMusic() {
        this.musicPlaying = false;
        this.currentTheme = null;

        if (this.musicScheduler) {
            clearTimeout(this.musicScheduler);
            this.musicScheduler = null;
        }

        // Stop all scheduled music nodes
        this.musicNodes.forEach(node => {
            try {
                node.stop();
            } catch (e) { /* already stopped */ }
        });
        this.musicNodes = [];
    }

    /**
     * Set master volume (0-1)
     */
    setMasterVolume(vol) {
        this.masterVolume = Math.max(0, Math.min(1, vol));
        if (this.masterGain) {
            this.masterGain.gain.setValueAtTime(this.masterVolume, this.audioContext.currentTime);
        }
    }

    /**
     * Toggle music on/off
     */
    toggleMusic() {
        this.musicEnabled = !this.musicEnabled;
        if (!this.musicEnabled) {
            this.stopMusic();
        }
        return this.musicEnabled;
    }

    /**
     * Toggle SFX on/off
     */
    toggleSFX() {
        this.enabled = !this.enabled;
        return this.enabled;
    }
     // ── Convenience aliases used by game.js and entity modules ──
      startMusic() { this.playMusic(MUSIC.OVERWORLD); }

    // =========================================================
    //  SOUND EFFECTS
    // =========================================================

    /**
     * Jump sound - rising frequency sweep
     */
    playJump() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'square';
        osc.frequency.setValueAtTime(300, now);
        osc.frequency.exponentialRampToValueAtTime(600, now + 0.1);

        gain.gain.setValueAtTime(0.3, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.16);
    }

    /**
     * Coin collect - two-tone chime
     */
    playCoin() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        // First note
        const osc1 = ctx.createOscillator();
        const gain1 = ctx.createGain();
        osc1.type = 'square';
        osc1.frequency.setValueAtTime(988, now);
        gain1.gain.setValueAtTime(0.3, now);
        gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.08);
        osc1.connect(gain1);
        gain1.connect(this.sfxGain);
        osc1.start(now);
        osc1.stop(now + 0.09);

        // Second note (higher)
        const osc2 = ctx.createOscillator();
        const gain2 = ctx.createGain();
        osc2.type = 'square';
        osc2.frequency.setValueAtTime(1319, now + 0.08);
        gain2.gain.setValueAtTime(0.3, now + 0.08);
        gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
        osc2.connect(gain2);
        gain2.connect(this.sfxGain);
        osc2.start(now + 0.08);
        osc2.stop(now + 0.26);
    }

    /**
     * Power-up collect - ascending arpeggio
     */
    playPowerUp() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;
        const notes = [523, 659, 784, 1047]; // C5, E5, G5, C6
        const duration = 0.08;

        notes.forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + i * duration;

            osc.type = 'square';
            osc.frequency.setValueAtTime(freq, t);
            gain.gain.setValueAtTime(0.3, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + duration + 0.05);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + duration + 0.06);
        });
    }

    /**
     * Power-up appear - rising sweep
     */
    playPowerUpAppear() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'square';
        osc.frequency.setValueAtTime(200, now);
        osc.frequency.exponentialRampToValueAtTime(800, now + 0.4);

        gain.gain.setValueAtTime(0.2, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.46);
    }

    /**
     * Enemy stomp - low thud
     */
    playStomp() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'square';
        osc.frequency.setValueAtTime(400, now);
        osc.frequency.exponentialRampToValueAtTime(100, now + 0.1);

        gain.gain.setValueAtTime(0.4, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.13);
    }

    /**
     * Kick shell - sharp hit
     */
    playKick() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(300, now);
        osc.frequency.exponentialRampToValueAtTime(150, now + 0.08);

        gain.gain.setValueAtTime(0.35, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.11);
    }

    /**
     * Mario death - descending melody
     */
    playDeath() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        // Stop music first
        this.stopMusic();

        const notes = [
            { freq: 494, time: 0.00, dur: 0.10 },
            { freq: 523, time: 0.10, dur: 0.10 },
            { freq: 494, time: 0.20, dur: 0.10 },
            { freq: 370, time: 0.35, dur: 0.15 },
            { freq: 311, time: 0.55, dur: 0.15 },
            { freq: 277, time: 0.75, dur: 0.30 },
        ];

        notes.forEach(note => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + note.time;

            osc.type = 'square';
            osc.frequency.setValueAtTime(note.freq, t);
            gain.gain.setValueAtTime(0.3, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + note.dur);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + note.dur + 0.01);
        });
    }

    /**
     * Level complete fanfare - triumphant ascending melody
     */
    playLevelComplete() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        this.stopMusic();

        // Classic Mario level clear jingle approximation
        const melody = [
            { freq: 523, time: 0.00, dur: 0.10 },
            { freq: 659, time: 0.10, dur: 0.10 },
            { freq: 784, time: 0.20, dur: 0.10 },
            { freq: 1047, time: 0.30, dur: 0.10 },
            { freq: 784, time: 0.40, dur: 0.10 },
            { freq: 1047, time: 0.50, dur: 0.40 },
            { freq: 880, time: 0.50, dur: 0.40 },
            { freq: 698, time: 0.50, dur: 0.40 },
        ];

        melody.forEach(note => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + note.time;

            osc.type = 'square';
            osc.frequency.setValueAtTime(note.freq, t);
            gain.gain.setValueAtTime(0.25, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + note.dur);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + note.dur + 0.01);
        });
    }

    /**
     * Game over jingle - somber descending melody
     */
    playGameOver() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        this.stopMusic();

        const melody = [
            { freq: 392, time: 0.00, dur: 0.30 },
            { freq: 349, time: 0.35, dur: 0.30 },
            { freq: 330, time: 0.70, dur: 0.60 },
            { freq: 262, time: 1.40, dur: 0.80 },
        ];

        melody.forEach(note => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + note.time;

            osc.type = 'square';
            osc.frequency.setValueAtTime(note.freq, t);
            gain.gain.setValueAtTime(0.3, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + note.dur);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + note.dur + 0.01);
        });
    }

    /**
     * Break block - crunchy noise burst
     */
    playBreakBlock() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        // Noise via multiple detuned oscillators
        [180, 220, 270].forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + i * 0.01;

            osc.type = 'sawtooth';
            osc.frequency.setValueAtTime(freq, t);
            osc.frequency.exponentialRampToValueAtTime(freq * 0.3, t + 0.12);

            gain.gain.setValueAtTime(0.2, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.14);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + 0.15);
        });
    }

    /**
     * Bump block - low thump
     */
    playBump() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'square';
        osc.frequency.setValueAtTime(200, now);
        osc.frequency.exponentialRampToValueAtTime(80, now + 0.08);

        gain.gain.setValueAtTime(0.3, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.11);
    }

    /**
     * Pipe entry - descending whoosh
     */
    playPipe() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(600, now);
        osc.frequency.exponentialRampToValueAtTime(100, now + 0.5);

        gain.gain.setValueAtTime(0.25, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.55);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.56);
    }

    /**
     * Flagpole - descending scale
     */
    playFlagpole() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const notes = [784, 740, 698, 659, 622, 587, 554, 523];
        const dur = 0.1;

        notes.forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + i * dur;

            osc.type = 'square';
            osc.frequency.setValueAtTime(freq, t);
            gain.gain.setValueAtTime(0.25, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + dur + 0.02);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + dur + 0.03);
        });
    }

    /**
     * Extra life - happy ascending arpeggio
     */
    playExtraLife() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const notes = [523, 659, 784, 1047, 1319];
        const dur = 0.07;

        notes.forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + i * dur;

            osc.type = 'square';
            osc.frequency.setValueAtTime(freq, t);
            gain.gain.setValueAtTime(0.3, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + dur + 0.05);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + dur + 0.06);
        });
    }

    /**
     * Fireball - quick buzz
     */
    playFireball() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(800, now);
        osc.frequency.exponentialRampToValueAtTime(200, now + 0.08);

        gain.gain.setValueAtTime(0.2, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1);

        osc.connect(gain);
        gain.connect(this.sfxGain);
        osc.start(now);
        osc.stop(now + 0.11);
    }

    /**
     * Explosion - noise burst for enemy death
     */
    playExplosion() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        // Simulate noise with multiple detuned oscillators
        const freqs = [100, 150, 200, 250, 300];
        freqs.forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();

            osc.type = 'sawtooth';
            osc.frequency.setValueAtTime(freq + Math.random() * 50, now);
            osc.frequency.exponentialRampToValueAtTime(50, now + 0.3);

            gain.gain.setValueAtTime(0.15, now);
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(now);
            osc.stop(now + 0.31);
        });
    }

    /**
     * Pause - two-tone blip
     */
    playPause() {
        const ctx = this.audioContext;
        const now = ctx.currentTime;

        [440, 880].forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            const t = now + i * 0.06;

            osc.type = 'square';
            osc.frequency.setValueAtTime(freq, t);
            gain.gain.setValueAtTime(0.2, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.05);

            osc.connect(gain);
            gain.connect(this.sfxGain);
            osc.start(t);
            osc.stop(t + 0.06);
        });
    }

    // =========================================================
    //  MUSIC THEMES
    // =========================================================

    /**
     * Schedule a sequence of notes for music
     * @param {Array} sequence - Array of {freq, dur, type} objects
     * @param {number} startTime - AudioContext time to start
     * @param {number} tempo - BPM
     * @param {GainNode} destination - Output gain node
     * @returns {Array} - Array of oscillator nodes
     */
    scheduleSequence(sequence, startTime, tempo, destination) {
        const beatDur = 60 / tempo;
        const nodes = [];
        let t = startTime;

        sequence.forEach(note => {
            if (note.freq > 0) {
                const osc = this.audioContext.createOscillator();
                const gain = this.audioContext.createGain();

                osc.type = note.type || 'square';
                osc.frequency.setValueAtTime(note.freq, t);

                const noteDur = beatDur * note.dur * 0.9;
                gain.gain.setValueAtTime(note.vol || 0.15, t);
                gain.gain.exponentialRampToValueAtTime(0.001, t + noteDur);

                osc.connect(gain);
                gain.connect(destination);
                osc.start(t);
                osc.stop(t + noteDur + 0.01);

                nodes.push(osc);
                this.musicNodes.push(osc);
            }
            t += beatDur * note.dur;
        });

        return { nodes, endTime: t };
    }

    /**
     * Overworld theme - simplified Super Mario Bros main theme
     */
    playOverworldTheme() {
        if (!this.musicPlaying) return;
        const ctx = this.audioContext;
        const tempo = 200; // BPM
        const now = ctx.currentTime + 0.05;

        // Simplified overworld melody (approximation of the iconic theme)
        const melody = [
            // Intro phrase
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 523, dur: 0.25 }, { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.75 },
            { freq: 392, dur: 0.25 }, { freq: 0, dur: 0.75 },

            // Main phrase
            { freq: 523, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 392, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 330, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 440, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 494, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 466, dur: 0.25 }, { freq: 440, dur: 0.25 }, { freq: 0, dur: 0.25 },

            { freq: 392, dur: 0.33 }, { freq: 659, dur: 0.33 }, { freq: 784, dur: 0.33 },
            { freq: 880, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 698, dur: 0.25 }, { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 523, dur: 0.25 }, { freq: 587, dur: 0.25 }, { freq: 494, dur: 0.25 },
            { freq: 0, dur: 0.5 },
        ];

        // Bass line
        const bass = [
            { freq: 0, dur: 1 },
            { freq: 262, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 262, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 196, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 262, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 196, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 165, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 220, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 247, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 233, dur: 0.5 }, { freq: 220, dur: 0.5 },
            { freq: 196, dur: 0.5 }, { freq: 262, dur: 0.5 }, { freq: 330, dur: 0.5 },
            { freq: 392, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 330, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 262, dur: 0.5 }, { freq: 0, dur: 0.5 },
        ];

        const melodyResult = this.scheduleSequence(
            melody.map(n => ({ ...n, type: 'square', vol: 0.12 })),
            now, tempo, this.musicGain
        );

        this.scheduleSequence(
            bass.map(n => ({ ...n, type: 'triangle', vol: 0.1 })),
            now, tempo, this.musicGain
        );

        // Loop the theme
        if (this.musicPlaying) {
            const loopDelay = (melodyResult.endTime - now) * (60 / tempo) * 1000;
            const loopTime = Math.max(100, (melodyResult.endTime - ctx.currentTime) * 1000);
            this.musicScheduler = setTimeout(() => {
                if (this.musicPlaying && this.currentTheme === MUSIC.OVERWORLD) {
                    this.playOverworldTheme();
                }
            }, loopTime - 100);
        }
    }

    /**
     * Underground theme - darker, minor key
     */
    playUndergroundTheme() {
        if (!this.musicPlaying) return;
        const ctx = this.audioContext;
        const tempo = 180;
        const now = ctx.currentTime + 0.05;

        const melody = [
            { freq: 392, dur: 0.25 }, { freq: 370, dur: 0.25 },
            { freq: 349, dur: 0.25 }, { freq: 330, dur: 0.25 },
            { freq: 311, dur: 0.5 }, { freq: 0, dur: 0.25 },
            { freq: 330, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 277, dur: 0.5 }, { freq: 0, dur: 0.25 },
            { freq: 294, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 262, dur: 0.75 }, { freq: 0, dur: 0.25 },

            { freq: 392, dur: 0.25 }, { freq: 370, dur: 0.25 },
            { freq: 349, dur: 0.25 }, { freq: 330, dur: 0.25 },
            { freq: 311, dur: 0.5 }, { freq: 0, dur: 0.25 },
            { freq: 330, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 440, dur: 0.5 }, { freq: 0, dur: 0.5 },
            { freq: 440, dur: 0.5 }, { freq: 0, dur: 0.5 },
        ];

        const result = this.scheduleSequence(
            melody.map(n => ({ ...n, type: 'square', vol: 0.12 })),
            now, tempo, this.musicGain
        );

        const loopTime = Math.max(100, (result.endTime - ctx.currentTime) * 1000);
        this.musicScheduler = setTimeout(() => {
            if (this.musicPlaying && this.currentTheme === MUSIC.UNDERGROUND) {
                this.playUndergroundTheme();
            }
        }, loopTime - 100);
    }

    /**
     * Starman theme - fast, exciting
     */
    playStarmanTheme() {
        if (!this.musicPlaying) return;
        const ctx = this.audioContext;
        const tempo = 240;
        const now = ctx.currentTime + 0.05;

        const melody = [
            { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 659, dur: 0.25 }, { freq: 784, dur: 0.25 },
            { freq: 0, dur: 0.25 }, { freq: 698, dur: 0.25 },
            { freq: 0, dur: 0.25 }, { freq: 698, dur: 0.25 },
            { freq: 0, dur: 0.25 }, { freq: 659, dur: 0.25 },
            { freq: 0, dur: 0.25 }, { freq: 587, dur: 0.25 },
            { freq: 0, dur: 0.25 }, { freq: 523, dur: 0.25 },
            { freq: 587, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 523, dur: 0.25 }, { freq: 494, dur: 0.25 },
            { freq: 523, dur: 0.5 }, { freq: 0, dur: 0.5 },
        ];

        const result = this.scheduleSequence(
            melody.map(n => ({ ...n, type: 'square', vol: 0.13 })),
            now, tempo, this.musicGain
        );

        const loopTime = Math.max(100, (result.endTime - ctx.currentTime) * 1000);
        this.musicScheduler = setTimeout(() => {
            if (this.musicPlaying && this.currentTheme === MUSIC.STARMAN) {
                this.playStarmanTheme();
            }
        }, loopTime - 100);
    }

    /**
     * Hurry theme - faster version of overworld
     */
    playHurryTheme() {
        if (!this.musicPlaying) return;
        const ctx = this.audioContext;
        const tempo = 280; // Much faster
        const now = ctx.currentTime + 0.05;

        // Same melody as overworld but faster
        const melody = [
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 523, dur: 0.25 }, { freq: 659, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 784, dur: 0.25 }, { freq: 0, dur: 0.75 },
            { freq: 392, dur: 0.25 }, { freq: 0, dur: 0.75 },
            { freq: 523, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 392, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 330, dur: 0.25 }, { freq: 0, dur: 0.5 },
            { freq: 440, dur: 0.25 }, { freq: 0, dur: 0.25 },
            { freq: 494, dur: 0.25 }, { freq: 0, dur: 0.5 },
        ];

        const result = this.scheduleSequence(
            melody.map(n => ({ ...n, type: 'square', vol: 0.13 })),
            now, tempo, this.musicGain
        );

        const loopTime = Math.max(100, (result.endTime - ctx.currentTime) * 1000);
        this.musicScheduler = setTimeout(() => {
            if (this.musicPlaying && this.currentTheme === MUSIC.HURRY) {
                this.playHurryTheme();
            }
        }, loopTime - 100);
    }
}