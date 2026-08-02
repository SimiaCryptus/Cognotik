# Vocal Parkour

**Leap from sound to sound.** Vocal Parkour is a rhythm game you play with
your voice — you hiss, pop, hum, trill, and breathe your way down a
scrolling rail, in time and in tune. Everything runs on your own device;
your microphone audio never leaves your machine.

---

## The idea in a nutshell

Instead of tapping buttons, you make **vocal gestures** — a snake-like hiss,
a windy breath, a lip pop, a steady hum — and the game listens through your
microphone and scores how well you hit each note. Targets scroll toward a
hit line; you make the matching sound at the right moment to land them, chain
combos, and climb the grade ladder.

It's part rhythm game, part playful speech workout. You can play the built-in
sounds, train your own, or design your own stages — and, notably, the whole
thing learns _your_ voice rather than expecting you to match someone else's.

---

## A little background

Most voice technology is built to answer "what did you say?" — and, implicitly,
"did you say it _correctly_?" That framing carries a lot of baggage; it tends
to reward voices that sound like the majority of the training data and quietly
penalize accents, ages, and atypical voices.

Vocal Parkour deliberately takes a different stance. The engine never asks
whether you made a sound "correctly." Instead, it learns what _your_ hiss,
_your_ pop, and _your_ hum sound like from a short calibration, and then it
measures two things: whether your sounds are cleanly **separable** from one
another, and how **precisely and consistently** you reproduce each one
relative to your own baseline. In other words, the leaderboard you compete on
is your past self.

That design choice — self-relative scoring rather than a canonical "right
answer" — is what lets the same machine serve a beatboxer chasing a tighter
kick drum and a child practicing a tricky consonant, without privileging one
voice over another.

---

## How you actually play it

### Setup comes first

On first launch you'll calibrate your voice through a friendly, guided flow
built around a **bouncing-ball** minigame: a ball glides back and forth, and
you make sound while it's inside the highlighted window and stay quiet while
it's outside. Along the way the game measures your microphone, learns what
your quiet room sounds like (so background hum doesn't get mistaken for a
gesture), and captures a handful of examples of each sound. Any sound that's
hard for you to make can simply be **skipped**.

### The rail

Once you're calibrated, notes scroll from the right toward a glowing **hit
line** on the left. Each sound has its own lane, marked with a glyph and
color:

- **Tap notes** (percussive sounds like pops) are circles — make the sound as
  the note crosses the line.
- **Sustain notes** (held sounds like hums) are bars — start the sound as the
  bar reaches the line and _keep holding_ until it fills.

As you make sounds, a **detection trail** streaks down your lane so you can
literally _see_ your voice being recognized and flowing alongside the notes
you need to hit. This turns out to be one of the more delightful details: the
game isn't a black box grading you from afar; it shows you its work.

### Verdicts, combos, and grades

Every landed note earns a verdict — **PERFECT**, **EARLY/LATE**, **NICE**, or
**MISS** — and the heads-up display tracks your run in real time: score,
combo multiplier, and a set of quality meters (precision, steadiness,
recovery, and an overall "agility" score). When a run ends you get a letter
grade, a breakdown of how you did, and a celebratory banner if you beat your
record. Your bests are saved locally between sessions.

Crucially, **every timed mechanic has a non-timed twin** scored on precision
and consistency alone. Tempo is a difficulty axis, not an access wall — if the
clock isn't for you, you can still play the whole game.

### Build your own

The **Level Designer** lets you place notes on a beat grid — perfect for
drilling a specific sequence, like spelling out a word one sound at a time,
and looping it several times to build muscle memory. Stages export to JSON, so
you can share them or back them up.

---

## Why it's interesting

A few things make this more than a novelty, in my (admittedly biased) view:

1. **It's fair by construction.** Because no sound is ever compared to a
   population-general "correct" value, the scoring can't smuggle in an
   accent, dialect, or ability bias. The only thing held constant is
   _separability between your sounds_; the _value_ inside each sound is always
   your own.
2. **It's private by default.** All analysis happens client-side, in your
   browser; nothing is uploaded, and there's no account to create. For a tool
   people might use with children or in a clinic, that's not a footnote — it's
   the whole ballgame.
3. **It quietly teaches hard sounds.** Several of the shipped sound libraries
   deliberately target gestures many English speakers find difficult — rolled
   and trilled Rs, click consonants, ejectives, pharyngeals, tonal contours —
   turning a rhythm game into gentle pronunciation practice.
4. **It shows its work.** The detection trail and the (optional) diagnostics
   view make the underlying signal processing legible; you can watch the
   features that distinguish a hiss from a hum move in real time. It's a
   surprisingly good "see how your voice works" demo.

### Honest caveats

In the spirit of not overclaiming: some sound libraries push into genuinely
tricky acoustic territory. Distinguishing four _click consonants_ by ear is
hard, and distinguishing four _tone contours_ — the same sung syllable
differing only in pitch trajectory — is harder still; that library ships
flagged as **experimental** for exactly that reason. This is a game for
practice and delight, not a diagnostic or clinical instrument, and it's best
thought of as something that _complements_ a teacher or therapist rather than
replacing one.

---

## Who might find it useful

Because the core loop is "produce a specific, repeatable vocal gesture on cue
and get instant, objective feedback," the same machine serves play, practice,
and therapy-adjacent use. A few audiences I have in mind:

- **Kids learning to pronounce sounds** — the hardest part of articulation
  practice is doing the reps, and rhythm games are built to make reps feel
  good. (Practice and reinforcement, to _complement_ a licensed speech
  therapist — not a clinical tool.)
- **Language learners** — pronunciation is usually the least gamified,
  least feedback-rich part of learning a new language; the trill, click, and
  tone libraries fill that gap, and calibration sidesteps the "the app
  doesn't understand my accent" problem.
- **Actors, singers, speakers, and especially beatboxers** — the rhythm rail
  is a practice metronome that _listens back_, with a consistency metric that
  maps directly to "can you hit this reliably, take after take?"
- **Anyone keeping their voice sharp** — a quick daily "vocal fitness"
  warm-up, a small counterweight to a world of silent scrolling.
- **Educators, dialect coaches, and the curious** — custom, shareable sound
  libraries mean the same engine can host lesson-specific drills, bespoke
  accent work, or just a fun way to see what your voice is made of.

---

## Getting started

You'll need a microphone and a modern browser with Web Audio support. Run
**Setup** first so the game learns your voice, then jump into **Play**, tune
the difficulty to taste, and — if you're so inclined — head to the **Level
Designer** to build a stage of your own.

Have fun, and let your voice do the running. 🎤
