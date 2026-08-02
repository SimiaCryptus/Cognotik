# Predictive Markov Wordsearch Generator

A wordsearch generator that hides its target words not by making the
grid bigger, but by making the _background_ smarter. The filler letters —
the ones that don't belong to any hidden word — are usually pure random
noise. Here, they're predicted by a language model so that the grid reads
like plausible fragments of real language in every direction. The result is
a puzzle that camouflages its answers rather than merely burying them.

---

## 1. The Idea in a Nutshell

Think about the last wordsearch you solved. The trick your eye actually
performs is subtle: you're not reading, you're _pattern-matching_. A real
word sitting in a sea of random letters — `QXZJKV` and friends — stands out
because the surrounding gibberish never looks like language. The structure
of a genuine word pops against an unstructured background.

So the puzzle poses a question: what if the background looked like language
too?

That's the whole premise. Instead of sprinkling random letters into the
empty cells, this generator fills them with letters that _statistically
resemble_ natural writing — the sorts of letter combinations you'd plausibly
find in real text. When the noise itself reads like fragments of words, the
hidden targets stop standing out. They blend into their surroundings, and the
puzzle becomes genuinely harder to solve — not because there's more to search,
but because everything looks equally word-like.

---

## 2. A Little Background

### Wordsearches

A wordsearch is a grid of single letters with words hidden inside it. Each
hidden word runs in a straight line — horizontally, vertically, or
diagonally, and forwards or backwards — giving eight possible directions in
all. Every cell that isn't part of a hidden word is "filler." The art of a
good puzzle lies almost entirely in how convincingly that filler hides the
targets.

### Markov Models (the gentle version)

To generate language-like filler, the project leans on a classic idea called
a **Markov model**. The intuition is simpler than the name suggests: given
the last few letters you've seen, some next letters are far more likely than
others. After `th`, an `e` is very common; a `q` is almost unheard of. After
`q`, a `u` is nearly guaranteed.

You build such a model just by _counting_. Feed it a chunk of reference text
— a book, an article, whatever texture you'd like the puzzle to mimic — and
it tallies which letters tend to follow which short sequences. Turn those
tallies into probabilities and you have a little engine that can produce
text which _feels_ like the source without copying it. It's the same family
of idea behind the predictive text on your phone, just working one letter at
a time.

---

## 3. Why This Is Interesting

It turns out that generating language-like filler for a wordsearch is
trickier — and more fun — than it first appears, and the reason gets to the
heart of what makes this project distinct.

Ordinary text flows in _one_ direction: left to right. A wordsearch is read
in _eight_. A naive approach that made each row look like language would
still produce nonsense the moment you read a column or a diagonal. So the
real challenge is choosing each letter so that it looks plausible along
_every_ line it happens to sit on, all at once. Each cell has to keep several
directions happy simultaneously.

The generator handles this by asking the model for a prediction in each
direction and then _combining_ those opinions before it commits to a letter.
You can choose how strict that negotiation is:

- **Product** — the demanding option; it favours letters that _all_
  directions agree are plausible.
- **Sum** — more permissive; it averages the directions' preferences.
- **Max** — it goes with the single strongest vote.
- **Vote** — each direction nominates its favourite, and the majority wins.

This multi-directional balancing act is, to me, the genuinely interesting
part. It's what separates the project from both the countless "random letter"
generators and the single-direction Markov toys that only look right along
one axis.

There's also a deliberate design choice worth naming: the filler is meant to
be _plausible-but-not-real_. Some sophisticated crossword-style tools try to
pack the background with genuine dictionary words in every direction, which
is expensive and tends to leak distracting real words. This project aims
instead for the _texture_ of language — cheap to produce, easy to tune, and
camouflaging rather than exhaustively word-packed.

---

## 4. Using It

The app runs entirely in your browser — nothing is sent to a server — and it
installs like a native app for offline use. In practice, using it looks like
this:

1. **Provide a reference text.** This is the source whose "flavour" the filler
   will imitate. Different texts give different textures.
2. **List your target words.** These are the words to hide in the grid.
3. **Tune the difficulty.** A handful of controls shape the result:

| Control   | What it does                                                                 |
| --------- | ---------------------------------------------------------------------------- |
| Grid size | How large the puzzle is (15 × 15 by default).                                |
| Order     | How many previous letters the model considers; higher looks more convincing. |
| Combiner  | How the eight directions negotiate a letter (see above).                     |
| Sampling  | Whether to pick the most likely letter or sample for variety.                |

4. **Generate, regenerate, and export.** Not happy with a layout? Regenerate
   for a fresh one, then export the finished puzzle when it feels right.

The lever I find most satisfying is the combiner together with the model
order: between them they give surprisingly fine, _qualitative_ control over
difficulty — a puzzle can be made harder without adding a single extra word.

---

## 5. Who Might Find This Useful

A few audiences come to mind:

- **Puzzle makers and educators** who want wordsearches with a real
  difficulty dial, or who'd like the background to reflect a particular
  theme, language, or text.
- **Puzzle enthusiasts** who've grown a little too good at spotting words in
  random noise and want a fresh challenge.
- **The curious** — anyone interested in a small, tangible demonstration of
  how simple statistical language models can produce convincing texture, and
  how a familiar pastime changes when you rethink one overlooked detail.

---

In short, this is a small experiment in taking the least-considered part of a
familiar puzzle — the throwaway filler — and treating it as the main event.
It was more rewarding to build than I expected, and I'm looking forward to
seeing what people make with it. Enjoy!
