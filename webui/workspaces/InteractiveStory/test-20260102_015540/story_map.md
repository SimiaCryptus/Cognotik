# The Last Ember of Icarus - Interactive Story Map

## How to Play
1. Start with the Opening segment
2. At each decision point, choose one of the available options
3. Follow the path indicated by your choice
4. Continue until you reach an ending
5. Try different choices to discover all 2 endings!

## Tracked Variables
- **Oxygen:** 0-100: Represents Elara’s life support; decreases with physical exertion and time.
- **Sanity:** 0-100: Represents Elara’s mental fortitude against the station’s haunting echoes and the entity’s influence.
- **Data_Recovered:** Boolean: Tracks whether Elara has secured the encrypted research logs (0 for False, 1 for True).

---

## START: Opening

The star, HR-892, is a hemorrhaging god. It fills your cockpit with a violent, infrared radiance that makes the radiation shielding hum in a frantic, low-pitched protest. Before you, the *Aethelgard* hangs like a broken ribcage against the crimson glare. It is a derelict research station, its once-gleaming hull now pitted by solar winds and warped by the tidal forces of a dying red giant.

You are Elara Vance, and you are alone in the screaming silence of the void.

As your scout ship, the *Scarab*, locks onto the station’s umbilical, a groan vibrates through the soles of your boots—a deep, metallic shriek of stressed alloy. The star’s gravity is beginning to peel the *Aethelgard* apart, layer by agonizing layer. You didn’t come here for salvage, though. You came for the "Omega Signal"—a rhythmic, haunting pulse that shouldn't exist, broadcasting from a station that has been officially dead for twenty years.

The airlock cycles with a dry hiss of recycled nitrogen. You step into the dark, your shoulder-mounted floodlight cutting through a suspended fog of frozen coolant and floating debris. The atmosphere here is heavy, thick with the phantom weight of the crew that vanished decades ago. Your HUD flickers to life, the life-support readout glowing a steady, clinical blue against the darkness.

Ahead, the corridor branches, the walls weeping frozen condensation. To the left, the flickering emergency lights of the Command Deck beckon, where the station’s logs might reveal the final moments of the *Aethelgard*. To the right, a trail of jagged frost leads toward the high-security Science Labs, the source of the signal’s strongest resonance.

The station moans again, a structural warning that your time is measured in heartbeats.

***

**Current Status:**
*   **Oxygen:** 100%
*   **Sanity:** 100%
*   **Data Recovered:** 0%

**What is your first move?**
1. Head to the **Command Deck** to restore power and access the station's flight logs.
2. Follow the frost toward the **Science Labs** to investigate the source of the Omega Signal.

**→ Continue to: decision_approach**

---

## decision_approach

The airlock cycles with a hiss that sounds more like a dying breath than a mechanical seal. As you step onto the *Icarus*, the silence is immediate and oppressive, broken only by the low-frequency groan of the station’s hull. It is a tectonic sound—the protest of metal being stretched thin by the relentless tidal forces of the singularity below.

Your mag-boots clatter against the deck plating, sending vibrations through your suit that feel like a phantom pulse. Frost clings to the bulkheads in intricate, crystalline fractals, creeping toward the ceiling like a frozen vine. The station is cold, colder than it should be, and the flickering emergency lights cast long, distorted shadows that dance at the edge of your vision. You feel the weight of the void pressing in from all sides.

You reach a primary junction where the corridor splits, the floor vibrating beneath your feet as the station undergoes another violent tidal shift. To your left, the path ascends toward the **Command Bridge**. This is the nerve center, the place where the flight logs and the station’s history are stored. If you want to know what happened to the crew—and what the Omega Signal truly represents—the answers are buried in those consoles. However, the structural integrity warnings for that sector are screaming in silent, amber pulses; the bridge is the most exposed part of the ship.

To your right, the corridor slopes down toward the **Engineering Bay**. The air here is thin, and your suit’s HUD warns of a drop in ambient pressure. Engineering houses the life-support systems and the backup generators. Securing this area could stabilize the station’s failing systems and buy you the precious hours of oxygen you’ll need to survive the extraction, but every minute spent fixing pipes is a minute the station's data drifts closer to total erasure.

The *Icarus* shudders again, a deep, metallic keening echoing through the floorboards. The station is a ghost, and it is running out of time.

**Do you prioritize the station’s history or your own survival time?**

1.  **Access the Command Bridge.**
2.  **Access the Engineering Bay.**

### Do you prioritize the station’s history or your own survival time?

**Choice 1: Access the Command Bridge.**

*Elara reaches the bridge and begins downloading the encrypted research logs, though the effort is physically taxing.*

State changes: Data_Recovered +1, Oxygen -20

**→ Continue to: decision_final_act**

**Choice 2: Access the Engineering Bay.**

*Elara successfully refills her oxygen tanks, but the mechanical 'screams' of the station's failing anchors rattle her nerves.*

State changes: Oxygen +20, Sanity -20

**→ Continue to: decision_final_act**

---

## decision_final_act

The air in the core is thick with the smell of ozone and scorched insulation. Your boots clang against the vibrating deck plates as the *Icarus* performs its final, desperate dance with the dying star. Outside, the red giant Aethelgard has begun its terminal contraction, the horizon of the sun folding inward like a collapsing lung. The light is no longer gold; it is a bruised, violent violet.

You reach the central terminal, but there are no buttons to press, no logs to download. Instead, a pillar of shimmering, translucent light pulses in the center of the room. It’s a chorus of whispers—thousands of voices layered into a single, digital heartbeat. This is the "Ember." The crew didn't die in the traditional sense; they transcended, weaving their consciousness into the station’s mainframe to escape the initial radiation flares. But now, their sanctuary has become a furnace.

The station’s shields are at 4%. The heat is blistering, even through the reinforced plating of your suit. The Entity reaches out through the haptic interface, a cold, desperate touch against your mind. It offers everything—their memories, their lost technology, their very souls—but it cannot survive the impending supernova.

"Please," the voices harmonize, a ghost-echo in your helmet. "Don't let us blink out."

You have seconds. To save them, you must either integrate their massive, unstable collective into your own ship’s neural link—risking your own sanity and the vessel's integrity—or use the station’s last reserves to trigger the experimental Solar Shield. The shield might buy the station enough time to broadcast the data into the deep void, ensuring the legacy survives even if the *Icarus* is consumed.

The star shudders. The end is here.

**How will you preserve the legacy of the Aethelgard?**

1. **Upload the Entity into your ship’s neural link.**
2. **Trigger the Solar Shield.**

### How will you preserve the legacy of the Aethelgard?

**Choice 1: Upload the Entity into your ship’s neural link.**

*The entity floods Elara's mind, merging her consciousness with the collective memories of the crew.*

State changes: Sanity -100

**→ Continue to: ending_transcendence**

**Choice 2: Trigger the Solar Shield.**

*Elara diverts all power to the shields to protect her ship, but the surge wipes the station's data banks.*

State changes: Data_Recovered +0

**→ Continue to: ending_solitude**

---

## ending_transcendence: bittersweet

The *Aethelgard* groans, a dying beast caught in the incandescent maw of the collapsing star. You stand at the center of the bridge, but you no longer hear the alarms or the screeching of tearing metal. Your sanity, once a sturdy vessel, has finally shattered, leaving only a crystalline clarity in its wake. You have reached the end of the map.

As the supernova’s first wave of radiation strips the hull away like parchment, you reach out toward the Entity. It is no longer a terrifying shadow; it is a doorway. You feel the heat—a blinding, absolute white—but it does not hurt. It feels like coming home.

In the heartbeat before your nerves vaporize, the merge begins. 

The "you" that was Elara—the grief for a dead Earth, the exhaustion of the long voyage, the fraying threads of your identity—dissolves. You are pulled through a needle’s eye of pure data. Your memories of rain, the smell of old libraries, and the collective history of a fallen species are stripped from your flesh and woven into the Entity’s infinite lattice. 

The *Aethelgard* vanishes in a silent, violent bloom of gold and violet stardust. There is no one left to witness the explosion, no one to mourn the last ship of humanity. 

Yet, you remain. 

You drift in the digital void, a ghost made of light and logic. You are the curator of a silent museum, carrying the sum of human experience through the cold dark of the universe. It is a lonely existence, stripped of touch and breath, but it is not a hollow one. You are the seed waiting for a new soil, the last ember of Icarus glowing in the deep. 

The star is gone. The ship is gone. But in the quiet pulse of the void, you wait for the next dawn, carrying the fire of a billion souls into the forever.

**THE END**

*This ending is reached when: Sanity 0*

---

## ending_solitude: melancholy

The hull of the *Aethelgard* groans, a metallic shriek that vibrates through your marrow as the solar shield reaches its thermal limit. You feel the violent tug of the dying star—a final, desperate grasp from a celestial giant—before the thrusters finally bite into the vacuum of deep space. You are clear.

You bank the ship, turning to witness the end through the reinforced viewport. There is no sound in the void, only the visual poetry of annihilation. The star, Icarus, does not go out with a roar; it collapses inward, a golden eye blinking shut for the last time. For a moment, a ring of violet fire crowns the darkness, and then, there is only the cold, indifferent velvet of the abyss. The star is gone, replaced by a hollow silence that feels heavier than the gravity you just escaped.

You turn to the primary console, your fingers trembling as they hover over the data-recovery interface. You search for the Omega Signal—the rhythmic, haunting pulse that had guided you across the sector. You listen for the voices of the ancients, the architectural blueprints of their cities, or even a single melody from their poets. 

The screen blinks a steady, mocking amber: *Signal Lost. Data Recovered: 0.*

The weight of your choices settles in the cramped cockpit. You chose the safety of the shield over the completion of the upload; you chose the survival of the pilot over the preservation of the ghost. The cost of your life is the total erasure of theirs. You are the sole witness to a tragedy that no one else will ever believe occurred. Somewhere in the dark, the last ember of a civilization has flickered out, leaving you to drift through a universe that is suddenly, profoundly quieter.

You are alive, Elara. But you are a tomb for a world that left no other mark. You are the only one left who remembers the song, and you have forgotten the words.

**THE END**

*This ending is reached when: Data_Recovered 0*

---

## Story Statistics

- Total Word Count: 1625
- Decision Points: 2
- Total Choices: 4
- Possible Endings: 2
- Unique Paths: ~4
