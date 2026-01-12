# Game Mechanics Design

**Game Concept:** A deck-building roguelike where cards represent weather phenomena and players must manage atmospheric pressure.

**Started:** 2026-01-09 10:40:15

---

## Core Mechanics

### 1. Barometric Equilibrium

**Description:** Players manage a Pressure Gauge (0 to 100 hPa) where cards have a 'Pressure Delta' that moves the needle. The gauge is divided into Low, Stable, and High zones, which grant bonuses or change card effects.

**Player Actions:**
- Playing cards to move the needle
- Targeting specific pressure zones
- Planning moves ahead to land on specific numbers

**System Response:** The system tracks the needle; hitting 0 or 100 deals 'Structural Damage' to the player and resets the needle to 50.

**Properties:**
- Feedback Type: cumulative
- Skill Expression: high
- Luck Factor: 20.0%

**Interactions:**
- Frontal Synthesis: synergy - Cards used for synthesis gain massive bonuses or different effects based on the current pressure zone.
- Adiabatic Venting: synergy - Venting provides a predictable way to move the needle toward the center to avoid structural damage.

---

### 2. Frontal Synthesis

**Description:** Cards carry 'Front' tags (Warm, Cold, Moist, Dry). Playing cards in specific successions creates 'Frontal Systems' that trigger powerful synthesis effects like AOE damage.

**Player Actions:**
- Sequencing card order
- Hand-tracking
- Deck-building for specific tag combinations

**System Response:** A UI element displays the last tag played; if the next card matches a recipe, a visual explosion and bonus damage occur.

**Properties:**
- Feedback Type: immediate
- Skill Expression: high
- Luck Factor: 30.0%

**Interactions:**
- Barometric Equilibrium: synergy - Synthesis effects can be enhanced by the current pressure zone, such as stripping armor in high pressure.

---

### 3. Adiabatic Venting

**Description:** Players can 'Vent' any card instead of playing it. This exhausts the card and moves the Pressure Gauge by a fixed 10 points toward the center (50 hPa).

**Player Actions:**
- Venting cards to stabilize pressure
- Exhausting cards to dig for combo pieces
- Tactical discarding to mitigate bad draws

**System Response:** The card is removed from the current combat and the Pressure Gauge needle moves toward 50.

**Properties:**
- Feedback Type: immediate
- Skill Expression: medium
- Luck Factor: 10.0%

**Interactions:**
- Isobaric Rupture: synergy - Venting allows for the precise mathematical adjustments needed to hit an enemy's exact Rupture point.

---

### 4. Isobaric Rupture

**Description:** A high-risk finisher triggered by matching the Pressure Gauge to an enemy's specific Stability Threshold. It deals massive damage and stuns but shuffles dead 'Turbulence' cards into the deck.

**Player Actions:**
- Calculating exact pressure values
- Managing long-term deck health
- Timing finishers for maximum impact

**System Response:** Triggers an 'Atmospheric Collapse' dealing 50% of enemy HP and shuffles 3 Turbulence cards into the player's deck.

**Properties:**
- Feedback Type: delayed
- Skill Expression: high
- Luck Factor: 0.0%

**Interactions:**
- Barometric Equilibrium: synergy - The pressure gauge is the primary tool used to reach the Rupture threshold.

---

## Mechanic Interactions

**Summary:** 6 interactions analyzed
- Synergies: 3
- Conflicts: 2
- Neutral: 1

### Synergies

#### Barometric Equilibrium ↔ Adiabatic Venting
Venting acts as the 'brakes' for the Pressure Gauge. Because cards move the needle in specific directions (Deltas), a player can easily find themselves 'red-lining' at 0 or 100 hPa. Venting allows a player to sacrifice a card they can't afford to play to pull the needle back toward the 50 hPa 'Stable' center.

⚠️ **Balance Concern:** If the fixed 10-point move is too efficient, the 'risk' of high-Delta cards is negated. If the 'Stable' zone (50 hPa) is the most powerful zone, Venting becomes the dominant strategy, leading to a 'discard-to-win' playstyle.

#### Barometric Equilibrium ↔ Isobaric Rupture
This turns the Pressure Gauge from a passive buff-tracker into an active 'aiming' reticle. The player must use the Deltas of their cards to land exactly on the enemy's Stability Threshold. It transforms the math of the game from 'stay in a range' to 'hit a specific integer.'

⚠️ **Balance Concern:** If an enemy's Stability Threshold is a single number, it may be mathematically impossible to hit based on the cards currently in the player's hand. Consider making the Threshold a small range or ensuring card Deltas are small primes.

#### Adiabatic Venting ↔ Isobaric Rupture
Venting is the most predictable way to move the Gauge. While cards might have awkward Deltas, Venting is always a clean 10 toward the center. Players will likely use their cards to get close to the Stability Threshold and then 'Vent' their remaining useless cards to 'nudge' the needle onto the exact number required for the Rupture.

⚠️ **Balance Concern:** This makes Rupture much easier to trigger than it appears. To balance this, the 'Turbulence' cards shuffled into the deck must be significantly punishing, or Venting must have an additional cost (like HP or Mana).

### Conflicts

#### Barometric Equilibrium ⚔ Frontal Synthesis
This is the primary 'puzzle' of the game. Players want to trigger Frontal Systems (e.g., Warm + Moist), but each of those cards has a Pressure Delta. A player might need a 'Cold' card to finish a synthesis, but playing it might push their Pressure Gauge out of the 'High' zone where they receive a damage buff.

⚠️ **Balance Concern:** If Synthesis damage is significantly higher than Zone bonuses, players will ignore the Gauge entirely. Conversely, if Zone bonuses are too strong, players will only play cards with low Deltas, making the 'Front' tags feel like flavor text.

#### Frontal Synthesis ⚔ Adiabatic Venting
To trigger a Synthesis, you need specific tags in a specific order. Venting exhausts the card. If a player needs a 'Dry' tag to complete a synthesis but their pressure is too high, they face a hard choice: Vent the Dry card to fix the pressure (losing the tag) or play the Dry card to get the Synthesis.

⚠️ **Balance Concern:** This interaction prevents 'deck thinning' from being too powerful. However, if too many cards are exhausted via Venting, the player may find themselves unable to trigger any Synthesis effects in the late game.

### Neutral Interactions

#### Frontal Synthesis ↔ Isobaric Rupture
These are the two primary ways to deal damage. Synthesis is the 'reliable' AOE damage, while Rupture is the 'high-risk' single-target finisher. A skilled player will use Synthesis effects to clear 'adds' while simultaneously using those cards' Deltas to move the Gauge toward the boss's Rupture Threshold.

---

## Progression System

**Summary:** 15 levels designed

| Level | XP Required | Difficulty | Playtime | Unlocks |
|-------|-------------|------------|----------|---------|
| 1 | 0 | 1.0x | 0.0h | 2 |
| 2 | 500 | 1.1x | 0.7h | 2 |
| 3 | 1200 | 1.2x | 2.0h | 2 |
| 4 | 2500 | 1.4x | 4.0h | 2 |
| 5 | 4000 | 1.6x | 7.0h | 3 |
| 6 | 6000 | 1.8x | 10.0h | 2 |
| 7 | 8500 | 2.0x | 14.0h | 2 |
| 8 | 11500 | 2.3x | 18.0h | 2 |
| 9 | 15000 | 2.6x | 23.0h | 2 |
| 10 | 20000 | 3.0x | 28.0h | 3 |
| 11 | 26000 | 3.2x | 32.0h | 3 |
| 12 | 33000 | 3.5x | 36.0h | 3 |
| 13 | 42000 | 3.8x | 41.0h | 2 |
| 14 | 55000 | 4.2x | 46.0h | 2 |
| 15 | 75000 | 5.0x | 55.0h | 5 |

### Detailed Progression

#### Level 1

- **XP Required:** 0
- **Difficulty:** 1.0x
- **Estimated Playtime:** 0.0 hours

**Unlocks:**
- Core Mechanics: Barometric Equilibrium
- Basic 'High Pressure' (Attack) and 'Low Pressure' (Defend) cards

#### Level 2

- **XP Required:** 500
- **Difficulty:** 1.1x
- **Estimated Playtime:** 0.7 hours

**Unlocks:**
- Frontal Synthesis: Unlocks the ability to 'collide' cards
- Supercell (Bonus Damage)

#### Level 3

- **XP Required:** 1200
- **Difficulty:** 1.2x
- **Estimated Playtime:** 2.0 hours

**Unlocks:**
- Adiabatic Venting: New card type: Vents
- Spend Pressure to draw cards or reduce incoming 'Heat' (debuffs)

#### Level 4

- **XP Required:** 2500
- **Difficulty:** 1.4x
- **Estimated Playtime:** 4.0 hours

**Unlocks:**
- Isobaric Rupture: The 'Limit Break'
- Cards that weaponize the failure state

#### Level 5

- **XP Required:** 4000
- **Difficulty:** 1.6x
- **Estimated Playtime:** 7.0 hours

**Unlocks:**
- Feature: The Stratosphere Lab
- Equipment slots
- Permanent atmospheric modifiers (e.g., Jet Stream)

#### Level 6

- **XP Required:** 6000
- **Difficulty:** 1.8x
- **Estimated Playtime:** 10.0 hours

**Unlocks:**
- Archetype: The Cyclonic
- New starting deck focused on 'Rotation' (re-ordering draw pile)

#### Level 7

- **XP Required:** 8500
- **Difficulty:** 2.0x
- **Estimated Playtime:** 14.0 hours

**Unlocks:**
- Advanced Synthesis: Triple-card combos
- Tornado combo

#### Level 8

- **XP Required:** 11500
- **Difficulty:** 2.3x
- **Estimated Playtime:** 18.0 hours

**Unlocks:**
- Feature: Microburst Events
- Random mid-combat shifts

#### Level 9

- **XP Required:** 15000
- **Difficulty:** 2.6x
- **Estimated Playtime:** 23.0 hours

**Unlocks:**
- Archetype: The Anticyclone
- High-difficulty deck focused on 'Perfect Vacuum' (0 Pressure)

#### Level 10

- **XP Required:** 20000
- **Difficulty:** 3.0x
- **Estimated Playtime:** 28.0 hours

**Unlocks:**
- The Eye of the Storm
- True Final Boss
- Third act of the run

#### Level 11

- **XP Required:** 26000
- **Difficulty:** 3.2x
- **Estimated Playtime:** 32.0 hours

**Unlocks:**
- Pressure Tier I (Ascension): 'Unstable Atmosphere'
- Enemies gain 10% HP
- Rupture threshold decreases by 5%

#### Level 12

- **XP Required:** 33000
- **Difficulty:** 3.5x
- **Estimated Playtime:** 36.0 hours

**Unlocks:**
- Pressure Tier II: 'Thermal Inversion'
- Adiabatic Venting costs 20% more Pressure
- New 'Rare' card tier unlocked

#### Level 13

- **XP Required:** 42000
- **Difficulty:** 3.8x
- **Estimated Playtime:** 41.0 hours

**Unlocks:**
- Pressure Tier III: 'Coriolis Chaos'
- Frontal Synthesis requires specific card positioning in hand

#### Level 14

- **XP Required:** 55000
- **Difficulty:** 4.2x
- **Estimated Playtime:** 46.0 hours

**Unlocks:**
- Archetype: The Singularity
- Glitch deck treating Isobaric Rupture as primary win condition

#### Level 15

- **XP Required:** 75000
- **Difficulty:** 5.0x
- **Estimated Playtime:** 55.0 hours

**Unlocks:**
- The Absolute Zero
- Custom Seeds
- Daily Pressure Challenges
- Global leaderboards
- Permanent cosmetic 'Aura'

---

## Economy System

**Summary:** 4 resource types designed

### Resources

#### Silver Iodide (AgI)

**Generation:**
- End of combat performance rewards
- Venting Rare rarity cards
- Rate: 15–30 per combat; 2–5 per Rare card vented

**Consumption:**
- Buying cards at The Eye (Shop)
- Purchasing Relics/Artifacts
- Seeding (Upgrading card Pressure Delta or damage)
- Rate: 25–150+ per transaction

#### Latent Heat (LH)

**Generation:**
- Automatic reset at start of turn
- Triggering Warm Front synthesis
- Rate: 3 per turn (base); +1 per Warm Front synthesis

**Consumption:**
- Playing cards
- Rate: 1–2 per card

#### Condensation Nuclei (CN)

**Generation:**
- Playing Moist or Dry front cards
- Maintaining Stable zone pressure at end of turn
- Rate: 1 per Moist/Dry card; 2 per turn in Stable zone

**Consumption:**
- Scrubbing (Exhausting Turbulence cards from hand)
- Precision Tuning (Moving Pressure Gauge by 1 hPa)
- Rate: 3–5 per utility action

#### Thermal Gradient (TG)

**Generation:**
- Triggering Frontal Synthesis
- Ending turn in Low or High pressure zones
- Rate: 1 per Frontal Synthesis; 2 per turn in extreme zones

**Consumption:**
- Supercell Activation (Elite card requirements)
- Isobaric Rupture damage multiplier
- Rate: 5+ or total consumption

### Flow Analysis

The economy operates on two levels: Tactical and Strategic. In-combat, players convert Latent Heat into cards, which in turn generate Condensation Nuclei and Thermal Gradient. This creates a loop where immediate action builds the resources needed for utility (CN) and finishers (TG). Post-combat, Silver Iodide acts as the bridge, allowing players to mitigate the negative side effects of high-power moves (Turbulence) or invest in better resource generation for future encounters.

### Sink Mechanisms

- Friction of Turbulence: A card draw sink where players must spend CN or AgI to remove dead cards generated by Ruptures.
- Pressure Maintenance Tax: A card-economy sink requiring players to Vent (lose) cards to maintain safe pressure levels against enemy interference.
- Exponential Seeding: An AgI sink where the cost of upgrading a specific card doubles with each subsequent upgrade.

### Balance Assessment

The economy is balanced for a fast-paced 20-minute loop, favoring high-velocity spending over hoarding. It provides stability through the Condensation Nuclei buffer, ensuring players have a non-RNG path to deck management. Meaningful choices are driven by the tension between immediate survival (Venting cards for pressure/AgI) and long-term power (saving cards and AgI for upgrades and relics).

---

## Balance Analysis

### Metrics

| Metric | Value | Assessment |
|--------|-------|------------|
| Win Rate Variance | 0.10 | ✅ Good |
| Strategy Diversity | 0.50 | ❌ Limited |
| Skill Expression | 88/100 | - |
| Luck Factor | 15/100 | - |
| Viable Strategies | 2 | - |
| Skill Ceiling | high | - |

### ⚠️ Dominant Strategies

- The Venting Rupture Loop

### Recommendations

- ⚠️ Address dominant strategies to improve balance
- ⚠️ Consider adding more viable strategic options

---

## Playtesting Predictions

**Summary:** 2 scenarios simulated

### The 'Frontal Synthesis' High (Synergy-Focused Run)

**Engagement Curve:**
Varies by skill level: Low/Moderate for new players, High for intermediate, and Very High/Flow State for experts calculating barometric output.

**Retention Points:**
- ✅ The 'Aha!' Moment: Realizing Frontal Synthesis clears the board and resets pressure.
- ✅ Visual/Audio Feedback: Satisfying lightning storm sounds and rapid pressure gauge drops.
- ✅ Build Variety: Exploring differences between Polar and Tropical Front synthesis.

**Frustration Triggers:**
- ⚠️ RNG Screw: Not seeing a 'Cold Front' card for three consecutive floors, rendering 'Warm Fronts' dead weight.
- ⚠️ Complexity Ceiling: Math required for 'Barometric Equilibrium' may be too punishing for a 20-minute game.

**Replayability Factors:**
- 🔄 The 'God Run' Chase: Recreating massive synthesis chains that clear bosses in one turn.
- 🔄 Meta-Progression: Unlocking new types of Fronts (Occluded or Stationary) that change rules.

**Assessment:**
This scenario rewards strategic planning and appeals to 'Johnny/Jenny' players who love finding broken combos. The short 20-minute loop encourages optimization.

---

### The 'Isobaric Rupture' Tightrope (High-Risk Survival)

**Engagement Curve:**
Erratic for new players due to early deaths; high tension for intermediate players; maximum engagement and adrenaline for experts treating the threshold as a resource.

**Retention Points:**
- ✅ Clutch Saves: Using 'Adiabatic Venting' with 1 HP remaining to survive pressure spikes.
- ✅ High Stakes: The looming threat of 'Isobaric Rupture' makes every card play feel consequential.
- ✅ Mastery Display: The feeling of total control over a chaotic system.

**Frustration Triggers:**
- ⚠️ Mechanical Opacity: Deaths caused by hidden calculations or confusing UI elements.
- ⚠️ Punishment vs. Reward: If high-pressure play doesn't provide enough damage, the risk feels 'not worth it'.

**Replayability Factors:**
- 🔄 Skill Expression: Hardcore players replaying to prove they can handle highest pressure levels without venting.
- 🔄 Leaderboards: Comparing 'Max Pressure Sustained' or 'Fastest Clear' with other players.

**Assessment:**
This scenario rewards tactical execution and nerve, appealing to 'Spike' players. The 20-minute duration is ideal for the high intensity of 'red-lining' the pressure gauge.

---

## Tuning Guide

### Difficulty Settings

- **Level 1 (Stable):** 40–60 hPa Safe Zone, 100% Visibility, Standard fluctuations
- **Level 2 (Unsettled):** 45–55 hPa Safe Zone, 100% Visibility, Pressure leaks (±2/turn)
- **Level 3 (Isobaric):** 48–52 hPa Safe Zone, 50% Visibility, Pressure Spike abilities
- **Level 4 (Cataclysmic):** No Safe Zone (must stay at 50 hPa), Hidden Visibility

### Reward Multipliers

- **Eye of the Storm Bonus:** 1.50x
- **Kinetic Efficiency:** 1.20x
- **Low Entropy Multiplier:** 2.00x
- **Perfect Forecast:** 1.30x

### Progression Speed

Horizontal progression focusing on options over power. Unlock new card archetypes every 3–5 runs. Progression unlocks advanced tooltips and starting conditions (Barometers) via Thermal Energy meta-currency.

### Economy Adjustments

- Card removal cost follows Fibonacci sequence (25, 40, 65, 105...) to force selective drafting.
- Shops guaranteed to offer one Stabilizer (passive control) and one Turbulence (high-risk/reward) item.
- Dynamic pricing: 20% discount on cards of the opposite pressure type to encourage mid-run strategy pivots.

### Additional Recommendations

- Implement 'Pressure Buffer' for lower levels to prevent instant death, removed at Level 3.
- Add 'Vent' mechanic: discard 2 cards to adjust Pressure by ±5 hPa to mitigate dead draws.
- Use deterministic enemy scaling with fixed damage values modified by pressure, removing crit chances.
- Trigger 'Supercell' event at 0 or 100 hPa: board clear but leaves player at 1 HP.
- Implement 'Frontal Systems' chain reactions for weather card combos (e.g., Cold Front + Warm Front = Thunderstorm).
- Balance archetypes (Vacuum, Compression, Equilibrium) using Pressure Cost instead of damage nerfs to preserve feel.

---



## Design Complete

**Total Time:** 241.935s

**Completed:** 2026-01-09 10:44:17
