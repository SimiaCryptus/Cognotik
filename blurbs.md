# Site Blurbs

A collection of one-paragraph blurbs for every lab, essay, demonstration, game, and
tool on the site, followed by category-level blurbs and a blurb for the site as a
whole. Written in the credible-enthusiast voice described in `writing_style.md`.

---

## Overall Site

**Mathematical Explorations** is a working laboratory disguised as a website — a
collection of original, in-browser mathematical experiments and essays that run live,
with no installation required. Most sites in this genre animate things you already
know; here those classics (Mandelbrot, Fourier, primes, Collatz) live only as
warm-ups, and the heart of the collection is a set of genuine investigations: questions
carried for years that finally had the tooling to be closed and shared. Three ideas
recur throughout and turn a grab-bag of demos into a single conversation — geometry
treated as computation, optimization used as a lens (watch the same three optimizers,
Adam, L-BFGS, and the homegrown QQN, leave their fingerprints across many labs), and a
consistent honesty about limits, where what _cannot_ be decided or distinguished is
treated as the interesting part rather than an embarrassment. Everything is client-side,
plain HTML/CSS/JavaScript with a few CDN libraries; the questions and judgment are
human, while AI-assisted tooling supplied the leverage to finish and publish.

---

## Featured Laboratories

**Pentagonal Lattice Geometry** — What happens when you _insist_ on tiling the plane
with regular pentagons even though they refuse to fit? Instead of forcing them flat,
this lab lets pentagons climb onto multiple overlapping sheets — a branched covering
space, like a spiral parking garage — and the payoff is genuinely strange: an emergent
fractional dimension (d ≈ 2.37), sub-diffusive transport, and spinor-like holonomy where
you must loop _twice_ around a vertex to come home, exactly like a spin-½ particle. All
the arithmetic is done exactly in the golden-ratio field ℚ(√5) so the delicate sheet
structure never tears from rounding error, and a cross-polygon sweep reveals that the
pentagon sits at the center of the interesting regime — with its dimensional class fixed
by its number field rather than its shape.

**Space-Color Symmetry (Symmetry Diffusion)** — A small pixel canvas where you paint
with symmetry and then watch the colors slowly diffuse, swirling into kaleidoscopic
patterns that never quite settle. The twist is that symmetry doesn't just mirror your
strokes; it _rewires the diffusion graph_, so color spreads along the symmetry axes as
though the canvas were a quotient manifold. A moment-preserving renormalization borrowed
from machine learning restores each channel's mean and spread after every step, so the
pattern refuses to fade to grey and instead circulates indefinitely — a satisfying
demonstration of how one modest correction turns a dissipative system into a generative
one.

**Geometric Entropy** — The continuous cousin of the classical Erdős distinct-distance
problem: instead of _counting_ distinct pairwise distances, this lab treats them as a
probability distribution and maximizes (or minimizes, or targets) its Shannon entropy
on any manifold — sphere, torus, cube, saddle, or an uploaded STL mesh. The maximizer
always climbs to the universal ceiling of ln N, but the _condition_ for reaching it is
wildly underdetermined, so a whole family of arrangements ties for best. Because the
optimum is that degenerate, the final shape becomes a fingerprint of the optimizer that
produced it — Adam leaves noisy lattices, QQN leaves smooth symmetric curves, L-BFGS
snaps to crystals — a completely useless and thoroughly delightful property.

**Spacelike Knots (Knot Topology Lab)** — Treat a knot not as a static tangle but as a
tiny relativistic spacetime: pick one spatial axis, quietly relabel it "time," and equip
the knot with a Minkowski metric so every pair of points becomes timelike, spacelike, or
lightlike. Under this lens a crossing's over/under becomes a causal inversion, and the
distance matrix becomes a causal diagram you can read at a glance. It is candidly _not_ a
new knot invariant — it's a fresh visualization angle — but physics-based relaxation and
an auto-orient mode that extremizes causal structure make it a genuinely fun way to see
familiar objects from an unfamiliar vantage.

**Layered Cellular Automata (Binary Coded Layered Autonoma)** — Three classic systems
stacked into one feedback sandwich: Langton's ants crawl a multi-color substrate with
turn rules encoded in binary and indexed by the color beneath them, the trail they paint
becomes a substrate, and a selectively-activated Conway's Game of Life evolves only on
cells the ants have switched on — with positive zones that spawn life and negative zones
that inhibit it. Highways, fractal boundaries, and competing colonies emerge from three
layers that each know only their own rules. It's a compact, tactile demonstration of
emergence-through-composition, and hitting "Randomize All" a few times is the fastest way
to stumble onto something beautiful.

**Fractal Learning** — The Mandelbrot warm-up run in reverse. Given a target point
cloud, this lab _fits an iterated function system_ — the recipe behind ferns and
Sierpiński triangles — by gradient descent under a Chamfer (nearest-neighbor) distance,
asking not "what does this fractal look like?" but "what fractal _is_ this?" To keep the
explosive combinatorics of orbit enumeration tractable it leans on two clever tricks:
commuting transforms (so a combination is described by counts, not sequences) and binary
powers (reaching the sixteenth application by repeated doubling). Watching a cloud of
random noise self-organize into the outline you asked for is, frankly, just delightful.

**Irrational Lattice (Algebraic Colored Lattice Fields)** — A deterministic way to make
a lattice interesting without randomness and without losing exact arithmetic. Each grid
point is nudged by a tiny displacement whose coordinates live in a quadratic number field
ℚ(√D), then snapped back to the nearest lattice point; the irrational component acts as a
rigidity condition that provably forbids any repeating translation. The result is
deterministic, provably aperiodic, spectrally tunable "colored noise" computed in pure
integer arithmetic — and visually it conjures the moiré ghosts of Penrose tilings,
constrained to a square grid, with an equalizer you can use to design the spectrum.

**No-Three-in-Line** — An interactive take on a deceptively simple, genuinely hard
puzzle: place as many points as possible on an n×n grid so that no three ever lie on a
line — not just rows and diagonals, but _any_ line at all. Rather than searching discrete
arrangements, the lab lets the points float freely and gives them a personality: a gentle
magnetic pull toward the lattice, a repulsive force that diverges as any triple
straightens out, and optional spacing and entropic jitter. Gradient descent rolls the
whole arrangement downhill through a frustrating, locally-minima-riddled energy
landscape, and it works in 3D too — a wonderfully visual case study in the continuous
relaxation of a combinatorial problem.

**Relativistic 2-Body Gravity** — An interactive simulation of two gravitating bodies
with a twist most orbit demos leave out: gravity that travels at a finite speed, so each
body feels where the other _was_, not where it _is_ (the "retarded" position), plus a
tunable dial that blends smooth Newtonian motion into relativistic corrections. Because
the forces are no longer perfectly balanced, orbits precess, trace flower-petal rosettes,
and tip into chaos — letting you crank the normally-invisible 43-arcseconds-per-century
Mercury effect up until it's impossible to miss. It openly declares itself an intuition
tool rather than a GR solver: a well-made physical analogy, not a telescope.

**Predictive Markov Wordsearch** — A wordsearch generator that hides its target words not
by enlarging the grid but by making the _background_ smarter. Ordinary puzzles bury words
in random noise, and real words pop against gibberish; here the filler is predicted by a
Markov model trained on your chosen text, so the background reads like plausible language
fragments in all eight directions at once. Each cell must satisfy several directions
simultaneously, negotiated by a tunable combiner (product, sum, max, or vote), and the
result is difficulty by camouflage rather than by size. It runs entirely in the browser,
installs as an offline app, and gives you a genuinely qualitative difficulty dial.

---

## Essays

**Quadratic Quasi-Newton (QQN)** — The source of that mysterious third optimizer haunting
half the labs. Where gradient descent is the cautious hiker and L-BFGS the ambitious one,
QQN sidesteps the either/or entirely by drawing a smooth, curved search path — d(t) =
t(1−t)(−∇f) + t²·d*LBFGS — that begins tangent to the safe downhill direction and bends
toward the bold quasi-Newton destination, then simply searches along that curve. This buys
guaranteed descent, no new hyperparameters, and graceful degradation. The essay is paired
with a rigorous benchmarking harness (62 problems, 25 optimizer variants, 50 runs each,
proper statistics) built to \_risk* disproving the method; the honest verdict is not
universal dominance but broad robustness — QQN won 36 of 62 problems while adding no tuning
burden.

**Rational Certificate Complexity (RCC)** — A taxonomy of mathematical constants stratified
not by how mysterious they seem but by how _expensive_ they are to certify. Every
approximation method is a little factory stamping out fractions; RCC asks not only how many
steps it must run but how many _bits_ its fractions cost as you tighten the tolerance — the
question almost everyone forgets. That overlooked metric turns out to be the discriminating
one: the natural engine for √2 lands squarely on the information-theoretic floor (optimal),
while the classical π-series do not, and the Wallis product is measurably more expensive
than Gregory–Leibniz despite needing roughly the same number of steps. The essay is
scrupulously honest that it classifies _engines_, not constants, and its companion tool
treats disagreement between prediction and measurement as the interesting output.

**The Simplest Increment: x + sin(x)** — There is an iteration so simple it looks like a
typo — x → x + sin(x) — that converges to π at a rate proportional to its own cube, tripling
correct digits each step. The whole story is a single cancellation: the symmetry of sine
about π kills both the linear and quadratic error terms, leaving a cubic. The essay reframes
this as _derivative engineering_ — cubic convergence follows from three independent
conditions on the update function — and a companion harness checks every claim in exact
arithmetic, including a perturbed-sine counter-example that drops the convergence back to
quadratic. It is candid that this is theoretical, not a practical way to compute π; the value
is a clean example of how mathematical machinery grows by the smallest viable mutation.

**Numbers as Machines (NAM)** — The philosophical keystone: what if every number were not a
stored value but a tiny deterministic machine that, given its state, emits its next digit and
the next, forever — the single contract `step : State → (digit, State)`? From that one
primitive, arithmetic becomes composition, base becomes a codec rather than a property,
forking a number carries an honest cost, and — most bracingly — exact equality becomes
undecidable, so the system offers "less than," "agrees to N digits," and "indistinguishable
so far," but never a false claim of equality. Along the way it makes several ideas tangible:
why addition secretly needs signed digits, why p-adics are cleaner machines than reals, and a
memory-shaped complexity hierarchy that is a close cousin of RCC.

**The Extension Ladder (TEL)** — A logical reconstruction of how the number systems grow:
start with a structured set, use its structure to point at something outside it, extend to
include that thing, and repeat. Integers gain division to reach rationals; rationals gain root
extraction to reach the algebraic numbers; reaching the transcendentals like π requires
genuinely new analytic machinery; and adjoining a root of x²+1 finally closes the system in
the complex plane. The prettiest turn recasts "√2 is irrational" as a picture of rotations,
from which the circle, sine, cosine, and π emerge downstream. The essay is candid that this is
a retrospective synthesis, not a history, and that the forcing is real but not unique — the
p-adic branch shows the rationals force _several_ incompatible next levels.

---

## Short Demonstrations

**Mandelbrot Set** — Zoom and pan the iconic complex-plane fractal, a warm-up in iteration
that never settles, and the forward counterpart to the Fractal Learning lab's inverse question.

**Prime Number Sieve** — An animated Sieve of Eratosthenes, the familiar discrete process
that quietly previews the theme of structure hiding inside simple counting rules.

**Fourier Series** — Build waveforms from rotating circles, the classic illustration of
constructing a shape out of simpler motions — the "build structure from statistics" idea in
its most visual form.

**Collatz Conjecture** — Visualize the 3n+1 sequence, a discrete process hiding
continuous-looking patterns and a reminder of how much mystery lives in the simplest rules.

---

## Interactive Tools

**nam — the numbers-as-machines lab** — The NAM essay made playable: a browser calculator
(and matching REPL) over the `nam` library compiled to WebAssembly, where every number is a
running machine rather than a stored value. Here the collection's honesty-about-limits ethic
becomes a literal contract printed on the screen — comparison is tri-state (Less | Greater |
Indistinguishable) because equality of infinite streams is undecidable, forking reports its
cost tier, changing base is a codec rather than a new number, and an unprovable digit renders
as candid `pending (null)` rather than a fabricated guess. It's a small act of intellectual
honesty baked into arithmetic itself, and a vivid teaching aid for computability and exact
real arithmetic.

**Constrained Mesh Enclosure Lab** — A familiar physical intuition — shrink-wrapping a skin
around an object — reframed as a live optimization you can watch. A deformable triangulated
mesh flows downhill on an energy landscape (minimizing area, matching a target volume, keeping
triangles well-shaped) while an exact continuous-collision-detection wall guarantees it never
intrudes into an inner keep-out shape — a wall, not a spring. Because several energy terms are
degenerate (many tessellations tie for best), the _optimizer_ becomes the tiebreaker, so
switching between Adam, L-BFGS, and QQN produces visibly different final surfaces that score
identically — optimizer fingerprinting made easy to watch in three dimensions.

**Bidirectional Markov Text Analyzer** — A browser instrument that learns the statistical feel
of one body of text and then paints a second, token by token, by how _surprising_ it looks
through that lens — calm and transparent where expected, hot and bright where unusual. Its
twist is that it reads in _both_ directions, giving each token an opinion from its past and its
future and combining them, which is a much richer signal than either alone. Click any token to
see what the model would have predicted instead and swap it in; a perplexity score summarizes
the whole passage. It's an oddly addictive, entirely private way to make abstract ideas —
n-grams, smoothing, predictability — tangible.

---

## Games

**The Arcade of Life** — A browser arcade where Conway's Game of Life stops being a
screensaver and starts fighting back. You draw living patterns that evolve under a handful of
neighbor-counting rules, and those evolving structures become your defenses against waves of
incoming "missiles" — which are themselves nothing more than gliders. Across modes (Missile
Defender, Space Invaders, Tower Defense, and the sit-back-and-watch Fire Line), a searchable
Pattern Zoo, a level designer, and fifty-plus rulesets that each behave like a different
physics, the joy is learning to _aim_ an emergent process that doesn't know you exist. It runs
offline in a browser, and every defense you draw is, in a small way, alive.

**Vocal Parkour** — A rhythm game you play with your voice: you hiss, pop, hum, trill, and
breathe your way down a scrolling rail, landing targets in time and in tune. Its defining
design choice is fairness by construction — the engine never asks whether you made a sound
"correctly" against a population-general model; it learns what _your_ sounds are from a short
calibration and scores you against your past self, on separability and precision alone. That
self-relative scoring lets the same machine serve a beatboxer, a language learner chasing a
rolled R, and a child practicing a tricky consonant, all without accent or ability bias. It's
private by default (audio never leaves your device), it shows its work with a live detection
trail, and every timed mechanic has a non-timed twin so tempo is a difficulty axis, not an
access wall.

**Stereo-Tac-Toe** — A fully playable game of tic-tac-toe hidden inside a Magic Eye picture.
There is no visible board, no visible marks, no visible cursor; everything you need is encoded
in the _depth_ of a shimmering field of noise, and only appears when you relax your eyes and let
the autostereogram fuse. The game never draws pixels you can see directly — it draws depth from
a single source-of-truth height-field (background far, grid closer, marks closer still, cursor
nearest), and the stereogram translates that into the image before you. The trivial game
underneath is deliberate: it puts all your attention on the perception-bending interface, where
the medium genuinely _is_ the point.

**Predictive Markov Wordsearch** _(also listed under Featured Laboratories)_ — A wordsearch PWA
that hides words in Markov-generated filler reading like language in all eight directions at
once, making concealment a property of the noise's statistics rather than the grid's size.

---

## Category Blurbs

**Featured Laboratories** — Extended interactive studies, each accompanied by documentation on
the underlying mathematics, motivation, and methods. These are the heart of the collection:
small pieces of in-browser mathematical research where numbers, knots, lattices, and tilings
are treated as _processes_ that run, diffuse, optimize, and unfold rather than as static
objects. Recurring threads tie them together — algebraic number fields determining geometry, the
continuous relaxation of discrete problems, and the shared optimizers (Adam, L-BFGS, QQN) whose
personalities become visible on degenerate landscapes — so wandering from one lab to the next
feels like a single, continuing conversation.

**Essays** — Longer, written investigations into how computation and number meet. Where the
labs play, the essays think: they develop the QQN optimizer's theory, propose a certificate-cost
taxonomy of constants, exhibit a cubic-speed iteration for π, reimagine every number as a
forkable virtual machine, and reconstruct the ladder of number systems as a single repeating
move. They are cross-linked into one argument, share a companion habit of verifying their own
claims in exact arithmetic, and are written in the same spirit the whole site advertises —
preferring an honest "plausibly novel, unverified" over a comfortable overclaim.

**Short Demonstrations** — Compact, self-contained reimplementations of classical mathematical
concepts: the Mandelbrot set, the prime sieve, Fourier series, and Collatz. No novelty is
claimed, and none should be — these are the warm-ups that calibrate the eye (simple rules,
surprising structure) and quietly preview the themes the deeper labs develop: iteration that
never settles, building shapes from simpler motions, and discrete processes hiding
continuous-looking patterns.

**Interactive Tools** — Hands-on instruments that make abstract ideas tangible and clickable:
a numbers-as-machines calculator that refuses to lie about equality, a camera-correction tool
that reclaims laser-damaged sensors on-device, geometric-attractor labs (mesh enclosure,
dihedral folds) that let you watch optimizers negotiate constrained and degenerate landscapes,
and a bidirectional text analyzer that paints prose by how surprising it looks. Each runs
entirely client-side, and each is built around the same ethic as the rest of the site — showing
its work and exposing the honest costs and limits of computation rather than papering over them.

**Games** — Playable experiments where a mathematical or perceptual idea _is_ the game. Conway's
gliders become both weapon and enemy; your own voice, learned relative to itself, becomes the
controller; a Magic Eye depth field becomes the entire interface. Built for the joy of it, they
are candid about their variance and limits, but they share the collection's conviction that the
most interesting mechanic is often the medium itself — an emergent system you learn to steer, a
fair-by-construction scoring engine, or an interface you must literally learn to _see_.
