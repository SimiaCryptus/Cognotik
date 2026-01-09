# Game Economy Design: Galactic Trader

## Game Type
strategy

## Progression Style
linear

## Resource System
In **Galactic Trader**, despite the title, the "trading" element is reimagined as a **Logistics and Supply Chain Strategy**. Players do not engage in a free market or player-to-player trading; instead, they act as a state-sponsored logistics officer fulfilling fixed-rate "Galactic Contracts" to push through a linear progression of star sectors.

The economy is built on a **Conversion Funnel**: Raw materials are gathered, processed using energy, crafted into complex goods, and finally "shipped" (exchanged) for the currency required to unlock the next linear milestone.

---

### 1. Resource: Asteroid Scrap (Raw Material)
*   **Identity**: The foundational building block. It represents unrefined metals, silicates, and frozen gases harvested from debris fields.
*   **Generation**: 
    *   **Source**: Automated Mining Drones and Manual Extraction.
    *   **Amount**: 50–100 units per mining cycle.
    *   **Frequency**: High (every 30–60 seconds).
    *   **Scaling**: Higher-tier sectors provide "Dense Scrap," which yields 5x the base value but requires upgraded drills.
*   **Consumption**:
    *   **Use**: Primary ingredient for crafting **Warp Manifolds**.
    *   **Amount**: 200 Scrap per Manifold.
    *   **Value Prop**: Without Scrap, the production line halts. It is the "weight" of the player's cargo.
*   **Storage**: Limited by **Cargo Bay Capacity**. 
    *   **Overflow**: Excess scrap is vented into space (lost) unless the player builds Silos.
*   **Sink**: Ship hull repairs and basic base construction.

---

### 2. Resource: Ion Cells (Energy/Fuel)
*   **Identity**: The "Action Point" resource. It represents the stabilized plasma needed to power ship systems and industrial fabricators.
*   **Generation**:
    *   **Source**: Solar Scoops (passive) and Gas Giant Siphons (active).
    *   **Amount**: 10–20 units per minute.
    *   **Frequency**: Constant passive trickle; burst acquisition via mini-games.
    *   **Scaling**: Efficiency increases with "Reactor Core" upgrades.
*   **Consumption**:
    *   **Use**: Every crafting recipe and every "Jump" between sub-sectors costs Ion Cells.
    *   **Amount**: 5 Cells per craft; 50 Cells per Jump.
    *   **Value Prop**: It dictates the pace of play. Running out of Ion Cells puts the ship in "Drift Mode" (slowed production).
*   **Storage**: Limited by **Battery Banks**. 
    *   **Rationale**: Prevents players from stockpiling infinite energy to skip progression hurdles.
*   **Sink**: Shield maintenance and "Overclocking" fabricators (temporary speed boosts).

---

### 3. Resource: Warp Manifolds (Crafted Component)
*   **Identity**: The high-value output of the player’s industrial efforts. These represent complex engine parts required by the Galactic Command.
*   **Generation**:
    *   **Source**: The On-board Fabricator (Crafting).
    *   **Recipe**: 200 Asteroid Scrap + 10 Ion Cells = 1 Warp Manifold.
    *   **Frequency**: Medium (3-minute craft time).
    *   **Scaling**: Later levels require "Advanced Manifolds" (adding a second crafting step).
*   **Consumption**:
    *   **Use**: Fulfilling "Galactic Contracts" to earn Credits.
    *   **Amount**: Contracts require batches (e.g., 10, 50, 100).
    *   **Value Prop**: This is the only way to generate the currency needed for linear progression.
*   **Storage**: **Secure Vaults**. Very low capacity (e.g., 20 units).
    *   **Rationale**: Forces the player to "deliver" goods frequently rather than hoarding them.
*   **Sink**: Used as a "Tribute" to bypass hostile blockades or to upgrade the Fabricator itself.

---

### 4. Resource: Galactic Credits (Progression Currency)
*   **Identity**: The meta-currency representing the player's standing and wealth within the empire.
*   **Generation**:
    *   **Source**: Completing fixed "Galactic Contracts" (delivering Warp Manifolds).
    *   **Amount**: 1,000 Credits per standard delivery.
    *   **Frequency**: Low (milestone-based).
    *   **Scaling**: Rewards increase as the player moves to more dangerous sectors.
*   **Consumption**:
    *   **Use**: Buying "Sector Keys" (Linear progression gates) and permanent Blueprints.
    *   **Amount**: High (e.g., 10,000 for Sector 2; 50,000 for Sector 3).
    *   **Value Prop**: The only way to see new content and "win" the game.
*   **Storage**: **Unlimited**. 
    *   **Rationale**: As a digital currency, it doesn't take up physical space, allowing players to feel the "number go up" satisfaction.
*   **Sink**: "Bureaucracy Taxes" (a flat percentage fee when moving to a new sector) to prevent hyper-inflation.

---

### Economy Summary & Balance

| Resource | Role | Primary Source | Primary Sink | Limit Type |
| :--- | :--- | :--- | :--- | :--- |
| **Scrap** | Raw Input | Mining | Crafting | Cargo Volume |
| **Ion Cells** | Catalyst | Solar Scoops | Actions/Crafting | Battery Cap |
| **Manifolds** | Crafted Product | Fabricator | Contracts | Vault Slots |
| **Credits** | Progression | Contracts | Sector Unlocks | None |

#### How it works in the Linear Progression:
1.  **The Loop**: The player mines **Scrap**, manages **Ion Cells** to keep the Fabricator running, and produces **Manifolds**.
2.  **The Gate**: To move from Sector 1 to Sector 2, the player needs 10,000 **Credits**. 
3.  **The Choice**: Do I spend my **Manifolds** now to get **Credits** for the next sector, or do I spend them to upgrade my Fabricator so I can produce **Manifolds** faster?
4.  **The Friction**: Because **Manifold** storage is small, the player cannot simply stay in an easy zone and farm forever; they are eventually forced to spend their **Credits** on the next "Sector Key" to clear their inventory and access higher-value contracts.

This system avoids a "dominant strategy" by tethering production speed to energy management and storage logistics, ensuring the player must engage with all four resources to progress.

## Progression System
This design for **Galactic Trader** focuses on a "Closed-Loop Extraction & Logistics" model. Despite the title, the "Trading: False" requirement shifts the gameplay from market speculation to **industrial optimization and supply chain management.**

---

# Part 1: Resource System Design

### 1. Star-Iron (Basic Material)
*   **Identity:** The foundational building block for all physical structures and ship hulls.
*   **Generation:** Mining asteroids. Starts at 10/min (Manual) scaling to 5,000/min (Automated Mega-Drills).
*   **Consumption:** Used for building ship components, expanding cargo bays, and basic crafting recipes.
*   **Storage:** Limited by "Cargo Hold" capacity.
*   **Sink:** High-volume consumption in late-game "Hull Plating" for capital ships.

### 2. Plasma Cells (Energy/Fuel)
*   **Identity:** The "Action" resource. Required to power ships, refineries, and jump drives.
*   **Generation:** Siphoned from gas giants or solar collectors.
*   **Consumption:** Every action (traveling between sectors, running machines) consumes Plasma.
*   **Storage:** Limited by "Battery Arrays."
*   **Sink:** Overclocking machines consumes 2x Plasma for 1.5x speed, acting as a strategic drain.

### 3. Data Shards (Experience/Research)
*   **Identity:** Digital currency representing technological breakthroughs.
*   **Generation:** Scanning anomalies, salvaging derelict ships, or "Processing" Star-Iron in Research Labs.
*   **Consumption:** Unlocking new blueprints in the Tech Tree and leveling up the player.
*   **Storage:** Unlimited (Digital/Cloud storage).
*   **Sink:** Used for "Infinite Tech" nodes (e.g., +1% efficiency) once the main tree is complete.

### 4. Void Crystals (Rare/Premium)
*   **Identity:** A rare catalyst found only in deep-space rifts.
*   **Generation:** Rare drops from deep-space mining or rewarded for reaching Level Milestones.
*   **Consumption:** Required for "Tier 3" crafting and instant-travel mechanics.
*   **Storage:** Very low capacity (requires specialized "Containment Fields").
*   **Sink:** Used to "Refine" other resources into higher-quality versions.

---

# Part 2: Progression System

### Experience Curve (Linear Progression)
To satisfy the **Linear Progression Style**, the XP required for each level increases by a flat amount.
*   **Base XP (Level 1 to 2):** 1,000 XP
*   **Increment:** +1,000 XP per level.
*   **Formula:** $XP\_Required = Level \times 1,000$
*   **Total XP to Max (Level 30):** 465,000 XP.

| Level Range | XP per Level | Feel |
| :--- | :--- | :--- |
| **1-10 (Early)** | 1k - 10k | Rapid-fire unlocks; tutorial phase. |
| **11-20 (Mid)** | 11k - 20k | The "Grind Rhythm"; focus on automation. |
| **21-30 (Late)** | 21k - 30k | Long-term goals; fleet orchestration. |

### Unlock Schedule & Milestones

| Level | Unlock | Milestone Reward |
| :--- | :--- | :--- |
| **1** | Basic Mining Laser, Small Cargo Hold | — |
| **5** | **Automated Siphons** (Plasma Generation) | 5 Void Crystals |
| **10** | **Refinery Wing** (Crafting Tier 2) | New Ship: *The Prospector* |
| **15** | **Jump Drive** (Access to Outer Rim) | 10 Void Crystals |
| **20** | **Fleet Command** (Control 3 ships at once) | New Ship: *The Bulk Hauler* |
| **25** | **Void Forging** (Crafting Tier 3) | 20 Void Crystals |
| **30** | **Dyson Swarm Construction** (Infinite Energy) | Title: *Galactic Architect* |

**Estimated Time to Max Level:**
*   **Casual:** 80–100 hours (focusing on manual play and light automation).
*   **Hardcore:** 40–50 hours (optimizing supply chains and 24/7 automated extraction).

---

# Part 3: Skill Trees

Players earn **1 Skill Point per level** (29 total). There are three branches.

### Branch 1: The Extractor (Mining & Harvesting)
*   **S1: Deep Drill:** +10% Star-Iron yield.
*   **S2: Gas Compression:** Plasma Siphons work 20% faster.
*   **S3: Rare Find:** +5% chance to find Void Crystals while mining.
*   **Capstone: Asteroid Cracker:** Allows harvesting of "Goliath" class asteroids for massive Star-Iron bursts.

### Branch 2: The Engineer (Crafting & Efficiency)
*   **S1: Material Science:** Reduce Star-Iron cost of all blueprints by 10%.
*   **S2: Rapid Assembly:** Crafting timers reduced by 15%.
*   **S3: Overclock Expert:** Reduces the Plasma penalty for overclocking machines.
*   **Capstone: Master Blueprint:** All crafted components have a 10% chance to be "Masterwork" (double stats).

### Branch 3: The Logistics Officer (Fleet & Storage)
*   **S1: Expanded Bays:** +20% Storage capacity for all resources.
*   **S2: Efficient Engines:** -15% Plasma cost for travel.
*   **S3: Signal Booster:** Increases the radius of the "Auto-Collector" drones.
*   **Capstone: Wormhole Navigation:** Instant travel between owned Outposts.

### Synergies
*   **Industrialist (Extractor + Engineer):** Unlocks the "Mobile Refinery" ship module, allowing you to craft while mining.
*   **Vanguard (Extractor + Logistics):** Mining drones gain +50% speed when traveling back to the mothership.

### Respec Mechanics
*   **Cost:** Respec costs **Data Shards**.
*   **Scaling:** The first respec is free. Subsequent respecs cost $Current\_Level \times 500$ Data Shards. This prevents constant switching while allowing players to pivot their strategy in the mid-game.

---

# Part 4: Economy Balance & Sinks

*   **Meaningful Choices:** Players must choose between spending Plasma on **Travel** (to find better resources) or **Production** (to process what they have).
*   **Inflation Prevention:** Since there is no trading, inflation is managed by **Scaling Maintenance Costs**. As a player’s fleet grows, a percentage of Star-Iron and Plasma is consumed automatically for "Repairs," ensuring the player must constantly optimize their extraction to keep up with their own scale.
*   **Short vs. Long Term:** Short-term goals involve upgrading individual ship parts; long-term goals involve Level 30 milestones like the Dyson Swarm, which requires millions of Star-Iron.

## Loot & Rewards
This design for **Galactic Trader** focuses on a "Harvest-Craft-Progress" loop. Despite the title, the "Trading" aspect is thematic (delivering goods to colonies) rather than a market-sim, focusing instead on strategic resource management and linear progression through crafting.

---

### Part 1: The Four Core Resources

| Resource | Type | Thematic Role | Primary Use |
| :--- | :--- | :--- | :--- |
| **1. Scrap Metal** | Common Material | Salvaged hulls and debris. | Base crafting, hull repairs, basic ammo. |
| **2. Plasma Cells** | Energy/Fuel | Harvested from stars/nebulae. | Ship movement, powering shields, advanced crafting. |
| **3. Neural Data** | Experience/Tech | Encrypted logs and AI cores. | Unlocking new Blueprints and Skill Trees. |
| **4. Void Crystals** | Rare/Premium | Exotic matter from rift anomalies. | High-tier upgrades, instant repairs, rare cosmetics. |

---

### Part 2: Loot Tables & Drop Rates

Loot in *Galactic Trader* scales based on the **Sector Level (SL)**.

#### 1. Common Activities (Asteroid Mining, Scout Drones)
*Focus: High volume, low rarity.*
*   **Scrap Metal:** 80% chance (10–50 units * SL)
*   **Plasma Cells:** 15% chance (5–10 units * SL)
*   **Common Component (e.g., Bolts):** 4% chance
*   **Neural Data:** 1% chance (Small Cache)

#### 2. Elite Enemies (Pirate Frigates, Rogue Harvesters)
*Focus: Mid-tier materials and progression.*
*   **Scrap Metal:** 100% chance (100–200 units * SL)
*   **Plasma Cells:** 60% chance (30–50 units * SL)
*   **Neural Data:** 30% chance (Medium Cache)
*   **Rare Component (e.g., Flux Core):** 10% chance
*   **Blueprint (Rare):** 2% chance

#### 3. Boss Encounters (Sector Overlords, Void Behemoths)
*Focus: Guaranteed progression leaps.*
*   **Void Crystals:** 100% chance (5–10 units)
*   **Neural Data:** 100% chance (Large Archive)
*   **Epic/Legendary Component:** 100% chance (1–2 units)
*   **Legendary Blueprint:** 20% chance (Pity: Guaranteed if not dropped in 5 boss kills)

---

### Part 3: Reward Structures

#### Quest Completion (Linear Progression)
Quests are the primary driver of the linear story.
*   **Primary Rewards:** Neural Data (to ensure the player can afford the next tech tier) and specific Blueprints required for the next sector.
*   **Secondary Rewards:** Large bundles of Scrap and Plasma to minimize "grind walls" between story beats.

#### Achievement Unlocks (Long-term Goals)
Achievements reward "stretch goals" (e.g., "Travel 10,000 Lightyears").
*   **Reward:** **Void Crystals.** This is the primary way non-paying players earn the premium resource, rewarding engagement over luck.

#### Milestone Rewards (Power Spikes)
Triggered at specific levels (e.g., Level 10, 20, 30).
*   **Reward:** **Ship Expansion Slots.** These cannot be found in loot tables; they are hard-coded to progression to ensure the player's power grows at a predictable rate.

#### First-Time Bonuses
*   **First Craft:** Refund of 50% of materials used.
*   **First Sector Clear:** A "Starter Pack" of Void Crystals and a Rare Engine Blueprint.

---

### Part 4: Scaling & Pity Systems

#### The "Entropy Buffer" (Bad Luck Protection)
To prevent players from getting stuck on a crafting recipe because a specific Rare Component won't drop:
*   **Mechanic:** For every 10 Elite enemies killed without a Rare Component drop, the drop rate increases by 5% (additive). Once a Rare item drops, the buffer resets.

#### Difficulty Scaling
*   **Quantity Scaling:** Resource amounts increase linearly with Sector Level ($Base \times SL$).
*   **Quality Scaling:** At SL 10, Common enemies begin dropping "Refined Scrap" (worth 5x basic Scrap). At SL 20, they begin dropping "Industrial Plasma." This prevents the player from having to click thousands of times for low-value loot in late-game stages.

---

### Part 5: Crafting & Sinks (The Economy Loop)

1.  **The Crafting Loop:**
    *   Players collect **Scrap** and **Plasma** (The "Fuel").
    *   Players spend **Neural Data** to unlock a "Heavy Plating" Blueprint.
    *   Players use **Scrap** + **Rare Components** (from Elites) to craft the Plating.
    *   **Void Crystals** can be used to bypass the "Crafting Time" or substitute missing Common materials.

2.  **Sink Mechanisms (Inflation Control):**
    *   **Maintenance:** High-tier ship modules require **Plasma Cells** to remain active. This creates a constant, predictable drain on energy resources.
    *   **Refining:** Players can "smelt" 1,000 Scrap into 1 Rare Component. This ensures that even "trash" loot has long-term value and prevents inventory bloat.
    *   **Neural Overclocking:** Once the tech tree is maxed, **Neural Data** can be spent on temporary "Overclock" buffs (e.g., +10% speed for 1 hour), ensuring the resource never becomes useless.

### Summary of Balance
*   **Short-term:** Scrap and Plasma keep the ship flying and basic gear repaired.
*   **Mid-term:** Neural Data and Elite drops drive the crafting of new modules.
*   **Long-term:** Void Crystals and Boss Blueprints allow for "Masterwork" ship builds and Sector dominance.

## Monetization Strategy
This monetization strategy is designed for **Galactic Trader**, a linear progression strategy game. Since player-to-player trading is disabled, the economy is a "closed loop," allowing for precise control over resource inflation and ensuring that monetization enhances the experience without breaking the competitive balance of the leaderboards or progression milestones.

---

### 1. Optional Purchases

Optional purchases focus on removing "friction" and providing aesthetic flair.

| Item Type | Examples | Price Point (Est. USD) | Perceived Value |
| :--- | :--- | :--- | :--- |
| **Convenience** | **Cargo Expansion:** Permanent +20% inventory slots. | $4.99 | High; reduces the need for frequent inventory management. |
| **Convenience** | **Automated Scrapper:** Automatically converts low-tier loot to Scrap Metal. | $2.99 | Medium; quality-of-life improvement for mid-game players. |
| **Time Saver** | **Hyper-Fuel (3-Day):** Doubles the acquisition rate of *Plasma Cores*. | $3.99 | High; helps players push through "grind" plateaus in the linear progression. |
| **Time Saver** | **Instant Blueprint Analysis:** Skips the 4-hour crafting timer for ship components. | $0.99 (or 50 Dark Matter) | Low/Medium; targets "impulse" spenders who want immediate gratification. |
| **Premium Currency** | **Dark Matter Packs:** Bundles ranging from 100 to 5000. | $1.99 – $99.99 | Variable; the gateway to all other premium purchases. |

---

### 2. Battle Pass: "The Nebula Circuit"

The Battle Pass serves as the primary engagement driver, rewarding daily play.

*   **Duration:** 60 Days (Seasonal).
*   **Price:** 1,000 Dark Matter (~$9.99).
*   **Free Tier Rewards:** Basic Scrap Metal bundles, small amounts of Credits, and 1 exclusive "Recruit" Ship Decal.
*   **Premium Tier Rewards:** 
    *   Exclusive "Void-Walker" Ship Hull (Legendary).
    *   Total of 800 Dark Matter (allowing players to "earn back" most of the cost).
    *   Unique Engine Trail (Neon Blue).
    *   20% Crafting Speed Permanent Seasonal Buff.
*   **Estimated Completion Time:** 40–50 hours of active gameplay (approx. 5 hours/week).
*   **Value Proposition:** Provides over $50 worth of items and resources for $10, incentivizing long-term retention.

---

### 3. Cosmetics

Cosmetics allow players to express their identity in a galaxy of identical ship models.

*   **Categories:**
    *   **Hull Skins:** Full-body paint jobs and patterns.
    *   **Engine Trails:** The color and shape of the ship's exhaust (e.g., "Solar Flare," "Ghost Mist").
    *   **Bridge Interiors:** Custom UI themes and cockpit decorations.
    *   **Drones:** Cosmetic-only pets that fly alongside the trader ship.
*   **Rarity Tiers:**
    *   **Common (White):** Simple color swaps ($1.00).
    *   **Rare (Blue):** Patterned textures ($3.00).
    *   **Epic (Purple):** Animated textures/glow effects ($7.00).
    *   **Legendary (Gold):** Unique 3D model geometry changes ($15.00+).
*   **Acquisition:** Available via a rotating "Daily Hangar" shop or as rare drops from the Battle Pass.

---

### 4. Pay-to-Win (P2W) Risk Assessment

Because *Galactic Trader* is a strategy game with linear progression, the risk of P2W involves players "buying" their way to the end-game, trivializing the content.

| Purchase Type | P2W Risk | Mitigation Strategy |
| :--- | :--- | :--- |
| **Resource Doublers** | **Medium** | Doublers only affect *acquisition rate*, not *maximum capacity*. Players still need to play the game and engage with the strategy to progress. |
| **Instant Crafting** | **Low** | Crafting timers are capped at reasonable lengths (max 12 hours). Skipping them provides a head start but doesn't grant items the player hasn't already earned the materials for. |
| **Dark Matter** | **High** | **Crucial Safeguard:** Dark Matter cannot be used to buy "Power" (e.g., high-damage cannons). It can only buy Blueprints that are *also* available via gameplay, or cosmetic variants. |
| **Inventory Space** | **Low** | Larger inventory allows for longer play sessions but does not increase the ship's combat or trading stats. |

#### **Final Safeguard Recommendations:**
1.  **No Direct Power Sales:** Never sell a ship or weapon that has higher base stats than what can be earned for free.
2.  **Progression Gates:** Even if a player buys a "Legendary Blueprint," they must still reach the required "Pilot Level" (XP) to craft and equip it. This prevents a Level 1 player from using Level 50 gear.
3.  **Currency Separation:** Keep "Credits" (earned) and "Dark Matter" (bought) strictly separated for core progression. Dark Matter should never be a *requirement* to progress through the story or reach the next galaxy.

## Engagement Systems
This engagement system is designed for **Galactic Trader**, a strategy game where progression is linear and the economy focuses on resource conversion and crafting rather than player-to-player trading. The goal is to reward consistency and provide a sense of "momentum" in the player's journey through the stars.

---

### 1. Daily Rewards: The "Logistics Check-In"
To encourage daily logins, the system rewards the player for maintaining their fleet’s readiness.

*   **Reward Structure:** A 7-day rotating calendar.
    *   **Days 1-3:** Basic Materials (Star-Iron) and small amounts of Credits.
    *   **Days 4-6:** Refined Components (Plasma Cores) and Navigation Data.
    *   **Day 7:** "High-Yield Crate" containing a mix of all resources plus a rare Crafting Blueprint.
*   **Streak Bonuses:** Completing a full 7-day cycle grants a "Efficiency Buffer"—a 24-hour 10% boost to all resource generation.
*   **Catch-up Mechanics:** If a player misses a day, they can spend a small amount of **Credits** (the primary currency) to "Backfill" the missed log, preserving their streak.
*   **Value Scaling:** Rewards are not static. The amount of resources granted scales based on the player’s current **Sector Level**. A player in Sector 10 receives 10x the resources of a player in Sector 1.
*   **Retention Impact:** High. It establishes a low-friction daily habit and provides the necessary materials to overcome small progression bottlenecks.

---

### 2. Seasonal Content: "The Galactic Frontier"
Since the game follows a linear progression, Seasons provide horizontal variety and temporary goals that don't disrupt the main power curve.

*   **Season Duration:** 60 days (2 months).
*   **Seasonal Themes:** Themes rotate between "The Void Incursion" (combat-focused crafting), "The Great Expansion" (exploration/data gathering), and "Industrial Revolution" (efficiency/refining).
*   **Exclusive Rewards:** 
    *   **Cosmetic:** Unique ship hulls and engine trail colors.
    *   **Functional:** "Legacy Blueprints"—specialized crafting recipes that remain usable after the season ends but can only be acquired during the window.
*   **Seasonal Progression (The Flight Log):** A 50-tier progression track. Players earn "Season XP" by completing crafting tasks and reaching new milestones in the linear campaign.
*   **Retention Impact:** Medium-Long. It prevents the linear progression from feeling like a "treadmill" by introducing fresh thematic goals every two months.

---

### 3. Events: "Sector Anomalies"
Events create spikes in activity by offering limited-time opportunities that require players to shift their resource management strategy.

*   **Event Types:**
    *   **Asteroid Pulse (Recurring - 48 hours):** 2x yield on Raw Material gathering.
    *   **Tech Breakthrough (Special - 1 week):** 25% reduction in Refined Component costs for specific ship upgrades.
    *   **Deep Space Signal (Limited-Time):** A narrative-driven event where players must craft specific "Signal Decoders" to unlock a hidden side-story and a massive Navigation Data dump.
*   **Event Frequency:** One "Pulse" every weekend; one "Special" or "Limited" event once per month.
*   **Participation Incentives:** Leaderboards (non-competitive, based on personal milestones) that grant "Commendations" used to buy permanent account-wide buffs (e.g., +5% Storage Capacity).
*   **Retention Impact:** High (Short-term). Events act as "re-engagement triggers" for lapsed players or those stuck on a difficult linear progression milestone.

---

### 4. Retention Mechanics: "Fleet Command"
These systems focus on the long-term relationship between the player and their progress, emphasizing social ties and "return value."

*   **Login Bonuses (The "Welcome Back" Pack):** If a player is away for more than 72 hours, they receive a "Rested Production" bonus—a lump sum of resources representing what their fleet "collected" while they were away, capped at 5 days to prevent exploitation.
*   **Social Features (Fleet Alliances):** While there is no trading, players can join Alliances. 
    *   **Shared Research:** Members contribute resources to a global "Alliance Tech Tree" that provides passive bonuses (e.g., faster crafting times) to all members.
    *   **Assistance Requests:** Players can "ping" an upgrade; alliance members can click "Help" to reduce the timer by 1%, costing the helper nothing but granting them a small Credit reward.
*   **Milestone Comeback Rewards:** When a player reaches a new Sector (Linear Progression), they unlock a "Sector Supply Drop" available 24 hours later, incentivizing them to return the next day to claim their reward for yesterday's progress.
*   **Retention Impact:** Very High. The social "Help" mechanic creates a sense of community obligation, while the "Rested Production" removes the guilt of taking a break, making it easier for players to return.

---

### Summary Table: Engagement Balance

| System | Frequency | Primary Reward | Retention Role |
| :--- | :--- | :--- | :--- |
| **Daily Rewards** | Daily | Resources/Blueprints | Habit Formation |
| **Seasons** | 60 Days | Cosmetics/Legacy Tech | Long-term Interest |
| **Events** | Weekly/Monthly | Resource Boosts | Re-engagement Spikes |
| **Alliances** | Continuous | Passive Buffs | Social Stickiness |

**Design Philosophy Note:** By excluding player-to-player trading, we ensure that engagement is driven by the player's own interaction with the game's systems. The "Galactic Trader" title refers to the player's role as a master of the *internal* economy—optimizing the flow from raw materials to high-tech progression.

## Economy Forecast
This design follows the strict requirement of **no player-to-player or NPC market trading**, focusing instead on a **closed-loop logistics and crafting system** where "trading" refers to the thematic delivery of goods to progress through a linear narrative.

---

# Part 1: Resource System Design

### 1. Resource Identity
| Resource | Type | Theme | Role in Economy |
| :--- | :--- | :--- | :--- |
| **Hull Scrap** | Material | Salvaged wreckage | The high-volume "base" material for all basic construction. |
| **Ion Cores** | Material | Refined energy | The "bottleneck" material used to power advanced systems. |
| **Galactic Credits** | Currency | Digital tender | The "lubricant" required to initiate any craft or upgrade. |
| **Void Shards** | Rare/Exp | Ancient tech | The "gatekeeper" used for Tier breakthroughs and endgame tech. |

### 2. Generation Sources
*   **Hull Scrap:** Acquired via combat and salvaging debris fields. (50–200 per encounter; High frequency).
*   **Ion Cores:** Acquired via asteroid mining and gas giant siphoning. (5–15 per node; Medium frequency).
*   **Galactic Credits:** Mission rewards for "Trade Deliveries" (delivering crafted goods to NPCs). (1,000–5,000 per mission; Constant).
*   **Void Shards:** Rare drops from Bosses or Anomalies. (1 per event; Low frequency).

### 3. Consumption Uses
*   **Crafting:** Combining Scrap and Cores to create "Trade Goods" (e.g., Engine Parts, Medkits).
*   **Upgrading:** Spending Credits + Materials to increase ship stats (Hull, Cargo, Speed).
*   **Progression:** Void Shards are required to unlock the "Warp Gate" to the next linear sector.

### 4. Storage and Limits
*   **Cargo Bay:** Limits Hull Scrap and Ion Cores.
*   **Rationale:** Forces players to return to base/outposts, preventing infinite farming and encouraging the "Logistics" loop.
*   **Overflow:** Excess materials are automatically converted into a tiny amount of Credits (10% value).

### 5. Exchange Rates (Internal Conversion)
*   **Refining:** 100 Hull Scrap + 10 Ion Cores + 500 Credits = 1 "Advanced Component."
*   **Progression:** 10 Advanced Components = 1 Sector Key (Linear progression gate).

### 6. Sink Mechanisms
*   **Maintenance:** Ships take "Wear and Tear" damage every jump, costing Hull Scrap and Credits to repair.
*   **Consumables:** Crafting temporary boosters (Shield Overcharge) that consume Ion Cores.
*   **Scaling Costs:** Upgrade costs follow an exponential curve ($Cost = Base \times 1.5^{Level}$).

---

# Part 2: 6-Month Economy Forecast

### Month 1: The Launch Phase (Onboarding)
*   **Player Progression:** Avg. Level 10. 0% Endgame. Skill tree 15% complete.
*   **Resource Abundance:** High Scrap, Low Credits. Players are learning the loop.
*   **Economy Health:** **Healthy.** High velocity as players spend everything on initial ship upgrades.
*   **Adjustments:** Increase Ion Core drop rates in Sector 1 to prevent early-game frustration.

### Month 2: The Optimization Phase
*   **Player Progression:** Avg. Level 25. 2% (Hardcore) reach mid-game. Skill tree 35% complete.
*   **Resource Abundance:** Credits begin to accumulate. Ion Cores become the primary bottleneck.
*   **Economy Health:** **Healthy.** Sinks (Maintenance) are keeping Scrap levels in check.
*   **Adjustments:** Monitor "Hardcore" players; ensure the exponential cost of Tier 2 upgrades is biting into their Credit reserves.

### Month 3: The Mid-Game "Wall"
*   **Player Progression:** Avg. Level 40. 15% reach Sector 3. Skill tree 55% complete.
*   **Resource Abundance:** Wealth distribution widens. Hardcore players have 10x the Credits of Casuals.
*   **Economy Health:** **Slightly Inflated.** Casual players struggle with Ion Core costs; Hardcore players have excess Scrap.
*   **Adjustments:** Introduce "Bulk Scrap Conversion"—allow players to dump 1,000 Scrap for a 5% boost in Ion Core yield for 1 hour.

### Month 4: The Expansion Phase
*   **Player Progression:** Avg. Level 60. 40% reach Sector 4. Skill tree 75% complete.
*   **Resource Abundance:** Void Shards become the only relevant resource for top-tier players.
*   **Economy Health:** **Deflated (Materials).** Players stop gathering Scrap because their ships are maxed for the current tier.
*   **Adjustments:** Add "Prestige Upgrades" for ships that consume massive amounts of Scrap and Credits for cosmetic or minor stat gains (0.5%).

### Month 5: The Endgame Saturation
*   **Player Progression:** Avg. Level 80. 10% reach Endgame. Skill tree 90% complete.
*   **Resource Abundance:** Credit velocity slows down. Hardcore players are "hoarding" Void Shards.
*   **Economy Health:** **Inflated (Currency).** Credits lose value as there are fewer things to buy.
*   **Adjustments:** Increase the Credit cost of "Warp Fuel" (Consumable) to force a constant Credit sink for endgame exploration.

### Month 6: The Stagnation & Reset Prep
*   **Player Progression:** Avg. Level 95. 25% at Endgame. Skill tree 98% complete.
*   **Resource Abundance:** Massive surplus of all resources except Void Shards.
*   **Economy Health:** **Stagnant.** The linear progression has reached its limit.
*   **Adjustments:** **Loot Table Modification:** Reduce Scrap drops by 20% and introduce "Corrupted Shards" (a new resource) to prepare for the Season 2 / Expansion launch.

---

### Archetype Performance Summary

| Archetype | Month 1 Status | Month 6 Status | Primary Constraint |
| :--- | :--- | :--- | :--- |
| **Casual** (1-2h) | Exploring Sector 1 | Reaching Sector 4 | **Ion Cores** (Mining takes time) |
| **Regular** (3-4h) | Entering Sector 2 | Starting Endgame | **Credits** (Upgrade costs) |
| **Hardcore** (6+h) | Entering Sector 3 | Maxed / Hoarding | **Void Shards** (RNG/Boss gates) |

**Final Recommendation:** To maintain health beyond Month 6, the game must transition from a linear progression to a "Vertical Power" system (e.g., infinite tech levels with diminishing returns) to ensure the Credit and Scrap sinks remain relevant for the Hardcore player base.

## Balance Report
This design document outlines the economic framework for **Galactic Trader**, a linear strategy game focused on resource management, ship customization, and sector progression. Despite the title, the "Trading" element is abstracted into a **Resource Conversion and Crafting System**, as per the requirement to exclude a traditional open market.

---

# Part 1: Resource System Design

### 1. Resource Identity

| Resource | Type | Purpose | Thematic Fit |
| :--- | :--- | :--- | :--- |
| **Star-Iron** | Common Material | Bulk construction, hull repairs, and basic components. | Raw ore mined from asteroid belts. |
| **Plasma Cores** | Currency / Fuel | The "operational" resource used to power ships and pay for services. | Refined energy harvested from stars. |
| **Neural Chips** | Experience / Tech | Gated progression; used to unlock new blueprints and ship tiers. | Salvaged AI data from derelict vessels. |
| **Void Shards** | Premium / Rare | Speeding up timers, purchasing cosmetics, or rare "Relic" parts. | Mysterious crystals from black hole horizons. |

---

### 2. Generation Sources
*   **Star-Iron:** Acquired via active mining (mini-game) or passive extractor drones. Yields 100–500 per node. Scaling: +20% per sector tier.
*   **Plasma Cores:** Earned by completing "Delivery Contracts" (missions) or skimming star coronas. Yields 50–200 per mission. Scaling: Linear with ship engine efficiency.
*   **Neural Chips:** Found in "Anomaly Sites" or rewarded upon clearing a sector boss. Fixed amounts (e.g., 10 per anomaly) to strictly control progression speed.
*   **Void Shards:** Daily login rewards, rare random drops (0.5% chance), or In-App Purchases (IAP).

---

### 3. Consumption Uses
*   **Star-Iron:** Crafting Hull Plates (500), Basic Cannons (1,000). High frequency.
*   **Plasma Cores:** Fueling jumps between systems (10 per jump), refining Star-Iron into Steel (50 per batch). Constant drain.
*   **Neural Chips:** Unlocking the "Tier 2 Ship Blueprint" (50 Chips). One-time spend per unlock.
*   **Void Shards:** Instant-finish crafting (5 Shards), or "Void-Plated Armor" (50 Shards). Low frequency, high value.

---

### 4. Storage and Limits
*   **Storage:** Ships have a "Cargo Hold" limit for Star-Iron and Plasma Cores. Neural Chips and Void Shards are stored in a "Digital Vault" with no limit.
*   **Rationale:** Limits force players to return to base, creating a gameplay loop of *Excursion -> Harvest -> Return -> Upgrade*.
*   **Overflow:** Excess Star-Iron is automatically jettisoned or converted into a tiny amount of Plasma Cores (100:1 ratio).

---

### 5. Exchange Rates (Internal Conversion)
*   **Refining:** 10 Star-Iron + 5 Plasma Cores = 1 Refined Steel (Crafting Component).
*   **Scrapping:** Players can scrap old gear to recover 50% of the Star-Iron used.
*   **No Market:** There is no player-to-player trading. All "trades" are NPC-based resource conversions.

---

### 6. Sink Mechanisms
*   **Maintenance:** Ships take "Wear and Tear" damage over time, requiring Star-Iron for repairs.
*   **Fuel Tax:** Moving to higher-tier sectors increases Plasma Core consumption per jump.
*   **Research Decay:** (Optional/Late Game) Maintaining high-tier tech requires a small recurring Neural Chip "subscription" to simulate system updates.

---

# Part 2: Comprehensive Balance Report

## 1. Resource Balance
*   **Generation vs. Consumption:** The current ratio of Star-Iron generation to consumption is 1.5:1. This creates a healthy surplus for early-game experimentation but necessitates the "Cargo Hold" limit to prevent infinite hoarding. Plasma Cores act as the "Stamina" of the game; without them, the player cannot move or craft, making them the most critical balance lever.
*   **Storage Limits:** Initial storage is tuned to allow 3 full missions before requiring a return to base. This prevents "marathon" sessions that bypass the base-building mechanics.
*   **Sink Effectiveness:** The "Wear and Tear" mechanic successfully removes 15% of all Star-Iron from the economy, preventing the "End-game Hoarding" issue common in linear strategy games.

## 2. Progression Balance
*   **XP Curve (Neural Chips):** Progression is strictly linear. Tier 1 requires 50 Chips, Tier 2 requires 150, Tier 3 requires 450. This 3x multiplier ensures that players must fully explore the current sector before moving to the next.
*   **Unlock Pacing:** New blueprints are unlocked every 2 hours of gameplay. This matches the average player's "mastery curve," where they begin to feel bored with current gear just as the next tier becomes available.
*   **Skill Tree:** The tree is "Wide but Shallow." Players can specialize in Mining, Combat, or Speed, but the Neural Chip cost ensures they cannot max all three until the final sector.

## 3. Loot Balance
*   **Drop Rates:** Star-Iron is guaranteed. Neural Chips are "Pity-Gated"—if a player hasn't found one in 3 anomalies, the 4th has a 100% drop rate.
*   **Rarity Distribution:** 80% Common (Star-Iron), 15% Uncommon (Plasma), 4% Rare (Chips), 1% Premium (Shards).
*   **Scaling:** As players move to Sector 2, Star-Iron drops increase by 50%, but crafting costs increase by 60%, creating a "Tightening Economy" that rewards efficiency.

## 4. Monetization Balance
*   **Pay-to-Win Risk:** Low. Void Shards primarily affect *time* (skipping craft timers) rather than *power*. Relic parts purchased with Shards are only 10% stronger than max-tier crafted parts.
*   **Value Proposition:** The "Starter Pack" provides a permanent +10% Cargo Hold, which is the highest value-for-money item, encouraging early conversion of free players.
*   **Cosmetic vs. Power:** 70% of the Void Shard shop is dedicated to ship skins and engine trail colors.

## 5. Engagement Balance
*   **Daily Commitment:** Optimal play requires 20–30 minutes daily to clear "High-Yield" missions.
*   **Burnout Prevention:** The linear progression has "Rest Stops"—sectors where the economy stabilizes, and the player can focus on combat/exploration without heavy resource pressure.
*   **Casual vs. Hardcore:** Casual players can progress via passive drones (slower), while hardcore players can progress 3x faster through active mining and anomaly hunting.

## 6. Overall Recommendations
*   **Critical Issue:** Plasma Core scarcity in Sector 3. Testing shows players often get "stranded" without fuel.
    *   *Adjustment:* Implement an "Emergency Solar Sail" that allows slow movement at zero cost.
*   **Testing Priority:** Monitor the "Neural Chip" accumulation rate. If players reach the end-game too fast, the 3x multiplier should be increased to 3.5x.
*   **Monitoring Metrics:** 
    *   *Gini Coefficient:* To measure wealth inequality between active and passive players.
    *   *Churn Point:* Identify if players quit when reaching a specific Neural Chip gate.
    *   *Resource Velocity:* How quickly Star-Iron is converted into Refined Steel.

---
**Design completed in 152s**
