# Game Mechanics Design

**Game Concept:** A deck-building roguelike where cards represent weather phenomena and players must manage atmospheric pressure.

**Started:** 2026-01-02 00:10:13

---

## Core Mechanics

### 1. Barometric Equilibrium

**Description:** The battlefield has a global Pressure Value (PV) ranging from -50 (Deep Low) to +50 (Extreme High). Every card played has a 'Pressure Delta' that shifts this value. Players must manage this sliding scale to stay within safe zones or reach extremes for powerful effects.

**Player Actions:**
- Playing cards to shift the PV
- Targeting specific Optimal Zones for card requirements

**System Response:** If the PV hits -50 or +50, the player takes Structural Damage. Certain 'Supercell' cards only become playable at these extremes.

**Properties:**
- Feedback Type: immediate
- Skill Expression: high
- Luck Factor: 20.0%

**Interactions:**
- Synoptic Outlook: synergy - Moving the PV gauge generates 'Wind Speed,' which is the currency used to manipulate the card queue.
- Isobaric Friction: synergy - The current PV is the primary variable used to calculate the damage differential against enemies.

---

### 2. Isobaric Friction

**Description:** A combat system where damage is calculated based on the pressure differential between the player's PV and the enemy's Core Pressure.

**Player Actions:**
- Using Wind cards to push Enemy Core Pressure
- Using Front cards to lock the player's PV

**System Response:** At the end of the turn, the system calculates the Pressure Gradient Force. A wide gap deals HP damage (Stability loss); a narrow gap triggers enemy counter-attacks (Stagnation debuffs).

**Properties:**
- Feedback Type: delayed
- Skill Expression: high
- Luck Factor: 10.0%

**Interactions:**
- Dew Point Saturation: synergy - Precipitation events from Saturation can multiply the damage calculated by the pressure differential.

---

### 3. Dew Point Saturation

**Description:** A combo mechanic where cards add Humidity to the atmosphere. Reaching 100% allows for a powerful Precipitation Event.

**Player Actions:**
- Playing cards to build Humidity percentage
- Choosing a Precipitation Event (Rain, Hail, or Snow) at 100% saturation

**System Response:** Tracks Humidity across turns. If 100% is reached and not triggered within 2 turns, a 'Flash Flood' deals massive damage to the player and resets Humidity.

**Properties:**
- Feedback Type: cumulative
- Skill Expression: medium
- Luck Factor: 40.0%

**Interactions:**
- Synoptic Outlook: synergy - Players use the visible queue to time their Humidity-building cards to hit 100% exactly when their strongest card is available.

---

### 4. Synoptic Outlook

**Description:** The player's draw pile is replaced by a visible queue of the next 5 cards, allowing for long-term tactical planning.

**Player Actions:**
- Spending Wind Speed to 'Veer' (move card to back of queue)
- Spending Wind Speed to 'Back' (pull card to front of queue)

**System Response:** The queue updates instantly based on player manipulation. Enemies can 'Pollute' the forecast to hide cards or force specific draws.

**Properties:**
- Feedback Type: immediate
- Skill Expression: high
- Luck Factor: 0.0%

**Interactions:**
- Barometric Equilibrium: synergy - The player sculpts their future turns to ensure they have the correct cards to reach the PV zones required for upcoming threats.

---

## Mechanic Interactions

**Summary:** 6 interactions analyzed
- Synergies: 6
- Conflicts: 0
- Neutral: 0

### Synergies

#### Barometric Equilibrium (BE) ↔ Isobaric Friction (IF)
This is the primary engine of the game. BE provides the 'position' on the scale, and IF provides the 'incentive' to move. Players create the largest possible gap between the Global PV and the Enemy Core to maximize damage.

⚠️ **Balance Concern:** If 'Safe Zones' in BE are too restrictive, players may feel punished for doing optimal damage. Conversely, if the damage from IF is too high, players will ignore the 'Safe Zone' penalties entirely to glass-cannon the enemy.

#### Barometric Equilibrium (BE) ↔ Dew Point Saturation (DPS)
These represent the 'State' and the 'Progress' of the atmosphere. Pressure-Gated Humidity forces players to move the PV into specific zones to 'charge' their Precipitation Event.

⚠️ **Balance Concern:** If the most powerful Pressure Delta cards (BE) also have the highest Humidity values (DPS), the player can reach a 'Supercell' state too quickly. High pressure-shifting cards should have low humidity, and vice versa.

#### Barometric Equilibrium (BE) ↔ Synoptic Outlook (SO)
This interaction removes the 'gambling' aspect. Because the player can see the next 5 cards and their Deltas, they can calculate exactly where the PV will be, allowing for 'Perfect Equilibrium' play.

⚠️ **Balance Concern:** This makes the game a 'solvable' math puzzle. To prevent this from becoming stale, enemies must have 'Pressure Fronts'—abilities that shift the PV outside of the player's turn—to disrupt calculations.

#### Isobaric Friction (IF) ↔ Dew Point Saturation (DPS)
This pair governs the 'Payoff.' IF is sustained damage, while the Precipitation Event (DPS) is the 'Ultimate.' The Precipitation Event's effect can scale based on the current Friction.

⚠️ **Balance Concern:** If both mechanics scale off the same delta, the power spikes could be exponential, potentially bypassing boss phases entirely.

#### Isobaric Friction (IF) ↔ Synoptic Outlook (SO)
SO allows players to time their 'Peak Friction.' Players can plan to use their 'Multi-hit' or 'Critical' cards exactly when the PV is at the furthest point from the enemy’s Core based on the card queue.

⚠️ **Balance Concern:** If the enemy's Core Pressure is static, the player will always find the 'optimal' turn in the queue. Enemies should shift their Core Pressure to force constant re-evaluation.

#### Dew Point Saturation (DPS) ↔ Synoptic Outlook (SO)
This interaction is about 'The Drop.' Players can see exactly which card in the next five will trigger the 100% Humidity threshold, allowing them to ensure the event happens on a specific card with beneficial keywords.

⚠️ **Balance Concern:** Risk of 'Dead Queues' where players feel forced to play 'trash' cards or skip turns to realign the queue to trigger the event on the desired card.

---

## Progression System

**Summary:** 15 levels designed

| Level | XP Required | Difficulty | Playtime | Unlocks |
|-------|-------------|------------|----------|---------|
| 1 | 0 | 1.0x | 0.0h | 2 |
| 2 | 500 | 1.1x | 0.3h | 2 |
| 3 | 1200 | 1.2x | 1.0h | 3 |
| 4 | 2500 | 1.3x | 2.0h | 2 |
| 5 | 4500 | 1.5x | 4.0h | 3 |
| 6 | 7000 | 1.8x | 6.0h | 2 |
| 7 | 10000 | 2.0x | 8.0h | 2 |
| 8 | 14000 | 2.2x | 11.0h | 3 |
| 9 | 19000 | 2.5x | 14.0h | 3 |
| 10 | 25000 | 2.8x | 17.0h | 2 |
| 11 | 32000 | 3.5x | 20.0h | 2 |
| 12 | 40000 | 4.0x | 23.0h | 2 |
| 13 | 50000 | 4.5x | 26.0h | 2 |
| 14 | 65000 | 5.0x | 28.0h | 2 |
| 15 | 85000 | 6.0x | 30.0h | 2 |

### Detailed Progression

#### Level 1

- **XP Required:** 0
- **Difficulty:** 1.0x
- **Estimated Playtime:** 0.0 hours

**Unlocks:**
- Barometric Equilibrium
- Basic 'High Pressure' (Defensive/Slow) and 'Low Pressure' (Offensive/Fast) cards

#### Level 2

- **XP Required:** 500
- **Difficulty:** 1.1x
- **Estimated Playtime:** 0.3 hours

**Unlocks:**
- Isobaric Friction
- Cards that deal damage based on the difference between your current pressure and the enemy’s pressure

#### Level 3

- **XP Required:** 1200
- **Difficulty:** 1.2x
- **Estimated Playtime:** 1.0 hours

**Unlocks:**
- Dew Point Saturation
- A secondary resource bar
- Playing 'Warm Front' cards increases humidity; reaching 100% triggers 'Precipitation' (clears all status effects)

#### Level 4

- **XP Required:** 2500
- **Difficulty:** 1.3x
- **Estimated Playtime:** 2.0 hours

**Unlocks:**
- Synoptic Outlook
- The ability to see the enemy’s pressure intent for the next 2 turns

#### Level 5

- **XP Required:** 4500
- **Difficulty:** 1.5x
- **Estimated Playtime:** 4.0 hours

**Unlocks:**
- Cyclogenesis
- A new card type: 'Vortex' cards
- These persist on the field and grow in power every time Barometric Equilibrium shifts

#### Level 6

- **XP Required:** 7000
- **Difficulty:** 1.8x
- **Estimated Playtime:** 6.0 hours

**Unlocks:**
- Adiabatic Cooling
- A passive mechanic where rapid pressure drops now freeze enemies (Stun), but rapid rises cause 'Heat Exhaustion' (Self-damage)

#### Level 7

- **XP Required:** 10000
- **Difficulty:** 2.0x
- **Estimated Playtime:** 8.0 hours

**Unlocks:**
- Microburst Events
- Rare, high-cost cards that instantly set Dew Point to 100% but discard your hand

#### Level 8

- **XP Required:** 14000
- **Difficulty:** 2.2x
- **Estimated Playtime:** 11.0 hours

**Unlocks:**
- Isobaric Tightening
- Enemies now gain 'Friction Armor'
- You must match their pressure exactly to deal full damage

#### Level 9

- **XP Required:** 19000
- **Difficulty:** 2.5x
- **Estimated Playtime:** 14.0 hours

**Unlocks:**
- The Jet Stream
- A new slot in the UI
- Every 3rd card played is 'carried' by the stream and played again for free next turn

#### Level 10

- **XP Required:** 25000
- **Difficulty:** 2.8x
- **Estimated Playtime:** 17.0 hours

**Unlocks:**
- Supercell Formation
- If Dew Point Saturation and Barometric Equilibrium are both at maximum, cards transform into 'Apocalyptic' versions

#### Level 11

- **XP Required:** 32000
- **Difficulty:** 3.5x
- **Estimated Playtime:** 20.0 hours

**Unlocks:**
- Coriolis Effect
- Every card played now shifts the pressure gauge in the opposite direction of its intended effect unless 'Synoptic Outlook' is active

#### Level 12

- **XP Required:** 40000
- **Difficulty:** 4.0x
- **Estimated Playtime:** 23.0 hours

**Unlocks:**
- Stratospheric Breach
- A new endgame biome with 'Vacuum' conditions (Pressure starts at 0 and constantly drains)

#### Level 13

- **XP Required:** 50000
- **Difficulty:** 4.5x
- **Estimated Playtime:** 26.0 hours

**Unlocks:**
- Entropy Cards
- Cards that permanently alter the deck for the remainder of the run (e.g., 'Climate Change': All High Pressure cards become Low Pressure)

#### Level 14

- **XP Required:** 65000
- **Difficulty:** 5.0x
- **Estimated Playtime:** 28.0 hours

**Unlocks:**
- Perfect Equilibrium
- If you finish a combat at exactly 50% Pressure and 0% Dew Point, you gain a 'Singularity' card

#### Level 15

- **XP Required:** 85000
- **Difficulty:** 6.0x
- **Estimated Playtime:** 30.0 hours

**Unlocks:**
- The Mesoscale Challenge
- Unlocks a 'Daily Seed' with randomized atmospheric constants

---

## Economy System

**Summary:** 4 resource types designed

### Resources

#### Thermal Joules (TJ)

**Generation:**
- Base 3 per turn
- Bonus +1 TJ if ending turn in Equilibrium (PV between -10 and +10)
- Rate: 3 per turn (base)

**Consumption:**
- Manifesting phenomena (playing cards)
- High-Pressure Cards (defensive stability)
- Low-Pressure Cards (volatile/hard to control)
- Rate: 0 to 4 TJ per card

#### Coriolis Force (CF)

**Generation:**
- Triggering a Precipitation Event (reaching 100% Humidity)
- Playing specific 'Jet Stream' cards
- Rate: 1 per event or card play

**Consumption:**
- Shift: Move a card in the 5-card queue forward or backward (1 CF)
- Discharge: Discard a card from the queue and draw (2 CF)
- Forecast: Look at the next 3 cards behind the current queue (3 CF)
- Rate: 1 to 3 CF per action

#### Latent Heat (LH)

**Generation:**
- Crossing the Pressure Value (PV) zero-point (moving from Low to High or vice versa)
- Rate: 10 LH per threshold crossing

**Consumption:**
- Supercell: Double the Pressure Delta of the next card played (50 LH)
- Flash Freeze: Instantly set Humidity to 0% and deal damage (50 LH)
- Stabilize: Reset PV to 0 and heal 1 HP per 10 LH (All LH)
- Rate: 50 to 100 LH per use

#### Silver Iodide (AgI)

**Generation:**
- Awarded at end of combat based on efficiency (turns taken)
- Cloud Seeding: Siphon 50% of current Humidity at end of battle
- Rate: Variable based on combat performance

**Consumption:**
- Buying new cards or Instruments (Relics) at the Weather Station
- Calibration: Removing a card from the deck
- Pressure Suit Upgrades: Increasing max HP or starting TJ
- Rate: Variable (scaling costs for Calibration)

### Flow Analysis

The resources flow in a circular, interdependent loop: Thermal Joules are spent to play cards, which shifts Pressure and increases Humidity. Shifting Pressure across the zero-line generates Latent Heat, while hitting 100% Humidity triggers a Precipitation Event, generating Coriolis Force. Coriolis Force is used to manipulate the queue to ensure the player can keep crossing the zero-line or hitting 100% Humidity. Silver Iodide is the byproduct of this efficiency, used between nodes to make the cards more powerful.

### Sink Mechanisms

- Atmospheric Decay: Latent Heat decays by 20% at the end of every combat to prevent hoarding.
- The Friction Tax: Coriolis Force action costs increase as the deck grows (e.g., Shifting costs 2 CF instead of 1 after Floor 1).
- Silver Iodide Scaling: The cost of removing cards (Calibration) scales exponentially (50, 100, 200, 400...).

### Balance Assessment

The economy provides strategic depth through the tension between spending Humidity for immediate power (CF) versus long-term scaling (AgI). It respects the 20-minute core loop constraint by limiting queue manipulation resources, preventing analysis paralysis. The Latent Heat mechanic creates a high-risk/high-reward dynamic by encouraging players to swing their Pressure values to extremes.

---

## Balance Analysis

### Metrics

| Metric | Value | Assessment |
|--------|-------|------------|
| Win Rate Variance | 0.20 | ⚠️ Fair |
| Strategy Diversity | 0.85 | ✅ Excellent |
| Skill Expression | 88/100 | - |
| Luck Factor | 18/100 | - |
| Viable Strategies | 4 | - |
| Skill Ceiling | high | - |

### ⚠️ Dominant Strategies

- The 'Perfect Forecast' (SO + BE)
- The 'Friction Burn' (IF + BE)

### Recommendations

- ⚠️ Address dominant strategies to improve balance

---

## Playtesting Predictions

**Summary:** 2 scenarios simulated

### Scenario 1: The 'Precision Barometer' (Deterministic/Engine-Building)

**Engagement Curve:**
New Players: High initial curiosity followed by a sharp 'complexity wall.' Engagement dips around minute 10 as they realize they can't just play cards; they must balance the meter. Intermediate: Engagement rises steadily. They begin to see the 'Synoptic Outlook' as a tool to solve the puzzle 3 turns in advance. Expert: A 'sawtooth' curve. High engagement during the mid-run 'optimization phase,' peaking at the final boss where their 'Dew Point' engine must fire perfectly.

**Retention Points:**
- ✅ The 'Aha!' Moment: Realizing that Isobaric Friction isn't just a penalty, but a way to slow down the enemy's deck cycle.
- ✅ The Perfect Forecast: Successfully using Synoptic Outlook to set up a 5-card combo that hits exactly at Dew Point Saturation.

**Frustration Triggers:**
- ⚠️ Math Fatigue: If the UI doesn't clearly show the result of playing a card on the Barometric Equilibrium, players may feel like they need a calculator.
- ⚠️ Death by 1%: Losing a run because the pressure was 101.4 instead of 101.3 feels 'cheap' in a deterministic setting.

**Replayability Factors:**
- 🔄 Build Diversity: Trying a 'High-Pressure Heatwave' build vs. a 'Low-Pressure Cyclone' build.
- 🔄 Meta-Progression: Unlocking new 'Instruments' (relics) that change how Dew Point is calculated.

**Assessment:**
This scenario appeals to the 'Spike' or 'Optimizer' player. It is highly addictive for those who love Slay the Spire or Monster Train on the highest difficulty. The 20-minute loop feels like a 'sprint of logic.'

---

### Scenario 2: The 'Chaos Front' (Reactive/High-Volatility)

**Engagement Curve:**
New Players: Very high. The visual effects of storms and the 'gambling' aspect of Dew Point Saturation are exciting. Intermediate: A 'frustration dip.' They understand the mechanics but feel the RNG of the Synoptic Outlook (the forecast) is too punishing. Expert: High and stable. They treat the game like poker—calculating probabilities of 'Pressure Bursts' and knowing when to fold a turn.

**Retention Points:**
- ✅ The 'Clutch' Save: Surviving a massive pressure spike with 1 HP through a lucky Isobaric Friction proc.
- ✅ Visual/Auditory Feedback: The visceral feeling of a 'Supercell' card clearing the board.

**Frustration Triggers:**
- ⚠️ Forecast Failure: When the Synoptic Outlook predicts a 'Clear Sky' but a 'Flash Flood' occurs due to a hidden modifier.
- ⚠️ Unavoidable Damage: Hardcore players hate feeling like there was no 'correct' play to avoid a loss.

**Replayability Factors:**
- 🔄 Emergent Narrative: 'Remember that run where the pressure stayed at 105 for ten turns?'
- 🔄 High Stakes: The 20-minute duration makes losing a high-volatility run less painful, encouraging 'just one more go.'

**Assessment:**
This scenario appeals to the 'Gambler' or 'Survivalist.' It feels more like Noita or FTL. The engagement is driven by adrenaline and the relief of surviving chaos.

---

## Tuning Guide

### Difficulty Settings

- **Tier 1 (Gale):** +15% Pressure buildup from cards, +10% Damage, -1 Card Draw on Turn 1
- **Tier 2 (Storm):** Pressure Safe Zone shrinks by 20%, +20% HP, Healing reduced by 25%
- **Tier 3 (Typhoon):** Pressure overflow deals 2x damage, Elite enemies spawn +15%, Card removal costs +50%
- **Tier 4 (Supercell):** Random Fronts every 3 turns, Bosses have 2 phases, Max Pressure reduced by 10, Equilibrium maintenance required

### Reward Multipliers

- **Perfect Equilibrium:** 1.50x
- **Extreme Conditions:** 2.00x
- **Flash Flood:** 1.25x

### Progression Speed

Horizontal progression focusing on variety over power; 10-run archetype unlock rule, 15% meta-progression stat cap, and 40% of unlocks tied to specific skill-based feats.

### Economy Adjustments

- Exponential Card Removal scaling (50, 100, 200, 400) to prevent deck bloating
- Pressure Venting Service in shops to adjust starting pressure for upcoming combats
- Barometer Merchant: Trade-In Rare cards for Weather Station Relics to allow mid-run pivots
- Final Act Scarcity: 30% reduction in currency drops with increased shop item quality

### Additional Recommendations

- Pressure Valve Mechanic: 15% of cards triple effect in red zone but are exhausted
- Forecast UI: Display next 3 Environmental Shifts to shift gameplay from reactive to proactive
- Enemy Archetypes: Include Anemometer (anti-spam) and Thermal (anti-high pressure) types to ensure meta diversity
- Synergy Weighting: Use weighted card drafting to increase relevant synergy appearance rates by 10% based on early picks

---



## Design Complete

**Total Time:** 185.39s

**Completed:** 2026-01-02 00:13:18
