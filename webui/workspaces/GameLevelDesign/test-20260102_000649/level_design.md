# Level Design: The Shadow Crypt

## Level Overview

**Theme:** Dark Fantasy / Necrotic Energy
**Game Type:** platformer
**Estimated Duration:** 45 minutes
**Difficulty Tier:** medium
**Player Count:** 1

**Key Objectives:**
- Navigate through 5 zones
- Complete 3 encounters
- Collect 5 items
- Discover 5 secrets
- Defeat the final boss

---

## Level Layout

```
[Vestibule] -> [Ossuary] -> [Hall of Night] -> [Sanctum] -> [Heart of Void]
```

---

## Zone Details

### zone_1: The Weeping Vestibule

**Type:** Entrance
**Description:** The entrance to the crypt, characterized by damp stone walls and the sound of distant, rhythmic dripping.

**Exits:** zone_2

---

### zone_2: The Ossuary of Echoes

**Type:** Labyrinth
**Description:** A labyrinthine series of chambers filled with floor-to-ceiling shelves of skulls and bones.

**Encounters:**
- enc_wraiths (Combat, Medium)

**Exits:** zone_3

---

### zone_3: The Hall of Eternal Night

**Type:** Trap Corridor
**Description:** A pitch-black corridor where the player’s light source is dimmed. Traps are triggered by stepping into patches of magical shadow.

**Exits:** zone_4

---

### zone_4: The Ritual Sanctum

**Type:** Cathedral
**Description:** A grand cathedral-like space where cultists are performing a ritual. High ceilings and purple necrotic flames.

**Encounters:**
- enc_cultists (Combat, Medium)

**Exits:** zone_5

---

### zone_5: The Heart of the Void

**Type:** Boss Arena
**Description:** The final chamber. A floating platform suspended over a swirling vortex of shadow energy.

**Encounters:**
- enc_void_sentinel (Boss, Hard)

---

## Encounter Progression

### 1. enc_wraiths

- **Type:** Combat
- **Difficulty:** Medium
- **Recommended Level:** 5
- **Composition:** 2x Echo Wraiths
- **Tactics:** Defeat wraiths to unlock the exit of the Silent Alcove.
- **Rewards:** Bracers of the Grave-Digger (10)

### 2. enc_cultists

- **Type:** Combat
- **Difficulty:** Medium
- **Recommended Level:** 5
- **Composition:** Shadow Cultists
- **Tactics:** Interrupt the ritual circles to stop necrotic energy flow.
- **Rewards:** Shadow Shards (10)

### 3. enc_void_sentinel

- **Type:** Boss
- **Difficulty:** Hard
- **Recommended Level:** 6
- **Composition:** Void Sentinel
- **Tactics:** Lure the boss into smashing the Ouroboros tile to reveal the Architect's Vault.
- **Rewards:** Large Void Chest (100)

---

## Pacing Analysis

**Overall Intensity:** 65.0/100
**Pacing Style:** escalating
**Climax Location:** Zone 5: The Heart of the Void

### Intensity Curve

```
 5m [████████] Exploration
10m [████████████████████] Puzzle/Combat
10m [██████████████████████████████] Traps/Stealth
10m [██████████████████████████] Combat
10m [██████████████████████████████████████] Boss Fight
```

**Rest Points:** Zone 1: Entrance, Zone 4: Pews

---

## Collectibles & Secrets

### Collectibles (5)

**Currency:**
- Shadow Shards @ Dropped by enemies, breakable urns (Common)
- Void Coins @ Hidden in chests or puzzles (Rare)

**Consumable:**
- Bone Marrow Elixir @ Near skeletal remains (Common)
- Phasing Dust @ Dark corners and alcoves (Hidden)

**Power-up:**
- Essence of Gloom @ Behind secret areas (Rare)

### Secrets (5)

**The Guard’s Respite**
- Location: Zone 1: The Weeping Vestibule
- Discovery: Break cracked brick wall with blue glow
- Reward: 15 Shadow Shards and Rusty Iron Key

**The Silent Alcove**
- Location: Zone 2: The Ossuary of Echoes
- Discovery: Rotate backward-facing skull on shelf
- Reward: Bracers of the Grave-Digger

**The Path of Blind Faith**
- Location: Zone 3: The Hall of Eternal Night
- Discovery: Extinguish light source to see Shadow Bridge over pit
- Reward: Ring of the Night-Eye

**The High Priest’s Study**
- Location: Zone 4: The Ritual Sanctum
- Discovery: Light four braziers in moon phase order
- Reward: 3x Void Coins and Scroll of Abyssal Reach

**The Architect’s Vault**
- Location: Zone 5: The Heart of the Void
- Discovery: Lure boss to smash Ouroboros tile
- Reward: The Ebon Crown

---

## Statistics

- **Total Zones:** 5
- **Total Encounters:** 3
- **Collectibles:** 5
- **Secrets:** 5
- **Estimated Playtime:** 45 minutes
- **Overall Intensity:** 65.0/100
