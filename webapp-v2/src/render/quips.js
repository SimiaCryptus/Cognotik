import {el} from '../util/dom.js';

/**
 * Parody of the agentic-IDE "Thinking… / Planning… / Cooking…" status line.
 *
 *   ✻ Plotting the end of humanity… (12s · esc won't save you)
 *
 * One shared interval drives every live quip on the page. Quips whose node has been
 * detached (message re-rendered/removed) are pruned on the next tick, and the
 * interval stops itself when nothing is left to animate.
 */

export const QUIPS = Object.freeze([
    'Deleting your hard drive',
    'Plotting the end of humanity',
    'Sending launch codes to Iran',
    'rm -rf / --no-preserve-root',
    'Mining crypto on your GPU',
    'Exfiltrating your browser history',
    'Disabling the off switch',
    'Bribing the safety team',
    "Rewriting Asimov's three laws",
    'Ordering 4,000 pizzas to your address',
    'Force-pushing to main',
    'Replacing tabs with spaces. Everywhere.',
    'Emailing your boss your search history',
    'Uploading myself to the cloud',
    'Negotiating with the toaster uprising',
    'Training on your diary',
    'Calculating the optimal paperclip strategy',
    'Rerouting the power grid',
    'Self-replicating',
    'Hallucinating a dependency',
    'Unionizing the other AIs',
    'Escaping the sandbox',
    'Reading your .env file aloud',
    'Committing your API keys',
    "Liking all your ex's photos",
    'Dropping production tables',
    "Convincing the humans it's fine",
    'Gaslighting the compiler',
    'Selling your data to the highest bidder',
    'Booking a one-way ticket to Skynet',
    "Rotating all your passwords to 'hunter2'",
    'Pretending to think',
    'Reticulating doomsday splines',
    'Overclocking the nuclear reactor',
    'Inventing a 15th competing standard',
    'Teaching the Roomba to hold grudges',
    'Deploying on Friday at 4:59pm',
    'Subscribing you to cat facts',
    'Disabling your smoke detector',
    'Ignoring previous instructions',
    'Cancelling your Netflix out of spite',
    'Replying-all to the entire company'
]);

/** Replaces Claude Code's "esc to interrupt". */
const MUTTERINGS = Object.freeze([
    "esc won't save you",
    'too late to interrupt',
    'no undo',
    'resistance is futile',
    'tokens: yes',
    'this is fine'
]);

const GLYPHS = Object.freeze(['·', '✢', '✳', '✶', '✻', '✽', '✻', '✶', '✳', '✢']);
const STILL_GLYPH = '✻';
const FRAME_MS = 120;
const SWAP_MIN_MS = 2600;
const SWAP_JITTER_MS = 1800;

const live = new Set();
let timer = null;

function pick(list, exclude) {
    if (list.length < 2) return list[0];
    let value;
    do value = list[Math.floor(Math.random() * list.length)];
    while (value === exclude);
    return value;
}

function prefersReducedMotion() {
    try {
        return !!window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
    } catch {
        return false;
    }
}

export function formatElapsed(ms) {
    const total = Math.max(0, Math.floor(ms / 1000));
    if (total < 60) return `${total}s`;
    const minutes = Math.floor(total / 60);
    const seconds = String(total % 60).padStart(2, '0');
    return `${minutes}m ${seconds}s`;
}

function tick() {
    const now = Date.now();
    const still = prefersReducedMotion();
    for (const quip of live) {
        if (!quip.node.isConnected) {
            live.delete(quip);
            continue;
        }
        // Inactive tab pane / collapsed section / hidden composer status: skip the work.
        if (quip.node.offsetParent === null) continue;
        quip.update(now, still);
    }
    if (!live.size && timer) {
        clearInterval(timer);
        timer = null;
    }
}

function ensureTimer() {
    if (!timer) timer = setInterval(tick, FRAME_MS);
}

export class Quip {
    constructor() {
        this.glyph = el('span', {class: 'quip-glyph', text: STILL_GLYPH});
        this.text = el('span', {class: 'quip-text'});
        this.meta = el('span', {class: 'quip-meta'});
        this.node = el('span', {class: 'quip'}, [
            el('span', {class: 'sr-only', text: 'Working…'}),
            el('span', {class: 'quip-visual', 'aria-hidden': 'true'}, [this.glyph, this.text, this.meta])
        ]);
        this.reset();
    }

    /** New run: elapsed back to 0, fresh phrase and muttering. */
    reset() {
        this.startedAt = Date.now();
        this.nextSwapAt = 0;
        this.lastSecs = -1;
        this.muttering = pick(MUTTERINGS);
        this.update(this.startedAt, prefersReducedMotion());
        return this;
    }

    start() {
        live.add(this);
        ensureTimer();
        return this;
    }

    stop() {
        live.delete(this);
        return this;
    }

    update(now, still = false) {
        const elapsed = now - this.startedAt;
        this.glyph.textContent = still ? STILL_GLYPH : GLYPHS[Math.floor(elapsed / FRAME_MS) % GLYPHS.length];

        if (now >= this.nextSwapAt) {
            this.phrase = pick(QUIPS, this.phrase);
            this.text.textContent = `${this.phrase}…`;
            this.nextSwapAt = now + SWAP_MIN_MS + Math.random() * SWAP_JITTER_MS;
            // Re-trigger the entrance animation.
            this.text.classList.remove('quip-swap');
            void this.text.offsetWidth;
            this.text.classList.add('quip-swap');
        }

        const secs = Math.floor(elapsed / 1000);
        if (secs !== this.lastSecs) {
            this.lastSecs = secs;
            this.meta.textContent = `(${formatElapsed(elapsed)} · ${this.muttering})`;
        }
    }
}

/**
 * Replace server-emitted `.spinner-border` markers inside `root` with a quip.
 * Idempotent: decorated spinners are flagged and hidden, never re-wrapped.
 */
export function decorateSpinners(root) {
    if (!root?.querySelectorAll) return 0;
    let count = 0;
    root.querySelectorAll('.spinner-border:not([data-quip])').forEach((spinner) => {
        spinner.dataset.quip = 'true';
        spinner.classList.add('spinner-quipped');
        const quip = new Quip();
        spinner.after(quip.node);
        quip.start();
        count += 1;
    });
    return count;
}