# Level Design: The Shadow Crypt

## Level Overview

**Theme:** Atmospheric shadow mechanics and escalating tension.
**Game Type:** platformer
**Estimated Duration:** 45 minutes
**Difficulty Tier:** medium
**Player Count:** 1

**Key Objectives:**
- Navigate through 5 zones
- Complete 2 encounters
- Collect 5 items
- Discover 6 secrets
- Defeat the final boss

---

## Level Layout

```
[Z1] -> [Z2] -> [Z3] -> [Z4] -> [Z5]
```

---

## Zone Details

### zone_1: The Whispering Foyer

**Type:** Entrance
**Description:** The entrance to the crypt is cold, lit by dying embers in wall-mounted braziers. The shadows here are long but stationary.

**Exits:** zone_2

---

### zone_2: The Hall of Flickering Veils

**Type:** Puzzle Corridor
**Description:** A long corridor where the ceiling is lined with swinging lanterns. The light moves rhythmically, creating shifting safe zones.

**Exits:** zone_3

---

### zone_3: The Ossuary of Echoes

**Type:** Stealth Combat
**Description:** A wide chamber filled with the bones of the forgotten. Sound plays a role here; heavy footsteps attract enemies.

**Encounters:**
- enc_lurkers (Stealth/Combat, Medium)

**Exits:** zone_4

---

### zone_4: The Penumbra Labyrinth

**Type:** Maze
**Description:** A disorienting maze of shifting walls and magical darkness. The player's light source begins to flicker.

**Exits:** zone_5

---

### zone_5: The Heart of the Void

**Type:** Boss Arena
**Description:** The final sanctum. A massive circular arena surrounded by an endless abyss.

**Encounters:**
- enc_boss (Boss Fight, Hard)

---

## Encounter Progression

### 1. enc_lurkers

- **Type:** Stealth/Combat
- **Difficulty:** Medium
- **Recommended Level:** 5
- **Composition:** Shadow Lurkers
- **Tactics:** Move quietly to avoid attracting lurkers; use light to reveal them.
- **Rewards:** Umbral Shards (50)

### 2. enc_boss

- **Type:** Boss Fight
- **Difficulty:** Hard
- **Recommended Level:** 10
- **Composition:** The Void Boss
- **Tactics:** Light the four corner torches to weaken the boss and create safe zones.
- **Rewards:** Essence of Night (1)

---

## Pacing Analysis

**Overall Intensity:** 65.0/100
**Pacing Style:** escalating
**Climax Location:** Zone 5: The Heart of the Void

### Intensity Curve

```
 5m [████████] Introduction
10m [████████████████] Puzzle
10m [████████████████████████] Stealth
10m [██████████████████████████████] Navigation
10m [████████████████████████████████████████] Boss Fight
```

**Rest Points:** Zone 1: The Whispering Foyer

---

## Collectibles & Secrets

### Collectibles (5)

**Currency:**
- Umbral Shards @ Zone 1 (Visible)

**Consumable:**
- Vial of Liquid Light @ Zone 1 (Prominent)

**Equipment:**
- Luminous Pebble @ Zone 2 (Hidden in niche)

**Rare Currency:**
- Engraved Skull @ Zone 3 (On pedestal)

**Resource:**
- Woven Shadow-Thread @ Zone 4 (On statue)

### Secrets (6)

**The Hidden Alcove**
- Location: Zone 1
- Discovery: Behind a thick, moth-eaten tapestry on the right wall.
- Reward: Old Guard’s Ring (Equipment)

**The Brazier Puzzle**
- Location: Zone 1
- Discovery: Lighting the fourth cold brazier with a torch.
- Reward: 3x Void Salts (Consumable)

**The Leap of Faith**
- Location: Zone 2
- Discovery: Walking through an illusory wall section with no shadow.
- Reward: Cloak of the Unseen (Equipment)

**The Bell-Ringer’s Vault**
- Location: Zone 3
- Discovery: Hitting three hidden bells in the order of the echoes (Low, High, Mid).
- Reward: Echoing Blade (Equipment)

**The Blind Man’s Path**
- Location: Zone 4
- Discovery: Dousing own light source to reveal glowing runes leading through a wall.
- Reward: Eye of the Abyss (Permanent Power-up)

**The Architect’s Final Gift**
- Location: Zone 5
- Discovery: Defeating the boss while keeping all four corner torches lit.
- Reward: The Eclipse Plate (Legendary Equipment)

---

## Statistics

- **Total Zones:** 5
- **Total Encounters:** 2
- **Collectibles:** 5
- **Secrets:** 6
- **Estimated Playtime:** 45 minutes
- **Overall Intensity:** 65.0/100
