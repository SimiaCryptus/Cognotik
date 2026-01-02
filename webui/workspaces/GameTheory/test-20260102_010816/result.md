# Game Theory Analysis: Two suspects are arrested for a crime. The police have insufficient evidence for a conviction on the principal charge, but enough to convict both on a lesser charge. 
The police offer each prisoner a bargain:
- If A and B both betray each other, each of them serves 5 years in prison.
- If A betrays B but B remains silent, A is set free and B serves 10 years in prison.
- If A remains silent but B betrays A, A serves 10 years in prison and B is set free.
- If A and B both remain silent, both of them serve 1 year in prison.

## Players
Suspect A, Suspect B

## Game Type
non-cooperative

## Game Structure
This analysis examines the provided scenario, which is the classic formulation of the **Prisoner’s Dilemma**, a fundamental model in game theory that illustrates why two completely rational individuals might not cooperate, even if it appears in their best interest to do so.

### 1. Identify the Game Structure
*   **Game Type**: 
    *   **Non-cooperative**: The players make decisions independently, and there is no enforceable agreement or contract between them.
    *   **Non-zero-sum**: The total benefit (or cost) to the players is not constant. The total years served by both suspects varies from 2 years (both silent) to 20 years (one betrays, one silent) to 10 years (both betray).
    *   **Simultaneous**: Both players make their decisions at the same time without knowing the choice of the other.
*   **Repetition**: This is a **one-shot game**. There are no future rounds or iterations mentioned, meaning players do not need to worry about retaliation or reputation in subsequent interactions.
*   **Information**: 
    *   **Imperfect Information**: At the moment of decision-making, neither player knows the action chosen by the other.
    *   **Complete Information**: Both players have full knowledge of the payoff matrix, the strategies available, and the objectives of the other player.
*   **Symmetry**: The game is **symmetric**. The payoffs for Suspect A are the same as the payoffs for Suspect B if their strategies are swapped.

### 2. Define Strategy Spaces
*   **Available Strategies**: Each player has a discrete strategy set: $S = \{Betray, Silent\}$.
    *   **Betray (Defect)**: Informing on the partner to receive a lighter sentence.
    *   **Silent (Cooperate)**: Refusing to speak to the police to protect the partnership.
*   **Nature of Strategies**: The strategies are **discrete** and binary.
*   **Constraints**: The primary constraint is the **lack of communication**. The suspects are isolated, preventing them from coordinating their choices or making binding promises.

### 3. Characterize Payoffs
*   **Objectives**: Each player’s objective is to **maximize their own utility**, which in this context means **minimizing their own prison sentence**.
*   **Payoff Matrix (Years in Prison)**:
    The payoffs can be represented as $(A, B)$, where the numbers represent years served (lower is better):
    *   (Silent, Silent) $\rightarrow$ (-1, -1)
    *   (Silent, Betray) $\rightarrow$ (-10, 0)
    *   (Betray, Silent) $\rightarrow$ (0, -10)
    *   (Betray, Betray) $\rightarrow$ (-5, -5)
*   **Transferability**: Payoffs are **non-transferable**. One suspect cannot "take" years from the other's sentence or compensate them with a side payment to influence their decision.

### 4. Identify Key Features
*   **Dominant Strategy**: For both players, **"Betray" is a strictly dominant strategy**. 
    *   If B is silent, A is better off betraying (0 years) than being silent (1 year).
    *   If B betrays, A is better off betraying (5 years) than being silent (10 years).
    *   Since betraying yields a better outcome regardless of the opponent's choice, a rational player will always choose to betray.
*   **Nash Equilibrium**: The Nash Equilibrium is **(Betray, Betray)**. In this state, neither player can improve their outcome by unilaterally changing their strategy. Both serve 5 years.
*   **Pareto Inefficiency**: The Nash Equilibrium is Pareto inefficient. There exists another outcome—**(Silent, Silent)**—where *both* players would be better off (serving only 1 year), but this outcome is unstable because both players have an individual incentive to cheat (betray) to reach 0 years.
*   **Timing and Signaling**: 
    *   **Timing**: Moves are simultaneous.
    *   **Signaling**: There are no opportunities for signaling or pre-play communication. Even if communication were allowed, "cheap talk" (non-binding promises to remain silent) would likely be ignored because the incentive to betray remains dominant.
*   **Information Asymmetry**: There is no information asymmetry regarding the rules or payoffs; however, there is a "strategic uncertainty" regarding the other player's actual choice.

## Nash Equilibria
This scenario is the classic **Prisoner's Dilemma**, a fundamental model in game theory that demonstrates why two completely rational individuals might not cooperate, even if it appears that it is in their best interest to do so.

### 1. Game Structure Analysis

*   **Type**: Non-cooperative, simultaneous-move, symmetric game.
*   **Information**: Imperfect information (players choose without knowing the other's choice) but complete information (payoffs are known).
*   **Payoff Matrix (Years in Prison)**:
    *   *Note: Lower numbers are preferred.*

| Suspect A \ Suspect B | Silent (Cooperate) | Betray (Defect) |
| :--- | :--- | :--- |
| **Silent (Cooperate)** | (-1, -1) | (-10, 0) |
| **Betray (Defect)** | (0, -10) | **(-5, -5)** |

---

### 2. Nash Equilibrium Analysis

In this game, there is exactly **one Nash Equilibrium**.

#### **The Strategy Profile: (Betray, Betray)**

**1. Description of the Strategy Profile**
In this equilibrium, Suspect A chooses to **Betray** Suspect B, and Suspect B chooses to **Betray** Suspect A. Both players receive a sentence of 5 years.

**2. Why it is a Nash Equilibrium**
A Nash Equilibrium occurs when no player can improve their payoff by changing their strategy unilaterally.
*   **Suspect A’s Perspective**: If A believes B will Betray, A’s best response is to Betray (5 years vs. 10 years). If A believes B will remain Silent, A’s best response is still to Betray (0 years vs. 1 year). Since Betraying is better regardless of B's choice, A has no incentive to deviate.
*   **Suspect B’s Perspective**: The logic is identical for B. Regardless of A's choice, B minimizes their prison time by Betraying.
*   Because neither player can reduce their sentence by switching to "Silent" while the other remains at "Betray," the profile (Betray, Betray) is stable.

**3. Classification**
*   **Pure Strategy Equilibrium**: This is a pure strategy Nash Equilibrium because both players choose a single specific action with 100% probability.
*   **Dominant Strategy Equilibrium**: More specifically, this is a **Strictly Dominant Strategy Equilibrium**. A strategy is dominant if it is the best choice regardless of what the opponent does. Since "Betray" is the dominant strategy for both, the intersection of these strategies is the inevitable equilibrium.

**4. Stability and Likelihood**
*   **Stability**: This equilibrium is highly stable. In a one-shot, non-cooperative game, rational players are logically compelled to this outcome.
*   **Likelihood**: In the absence of external enforcement (e.g., organized crime "omertà" codes or future retaliation), this is the only predicted outcome.

---

### 3. Discussion of Outcomes and Efficiency

#### **Pareto Dominance and the "Dilemma"**
The most striking feature of this game is the relationship between the Nash Equilibrium and **Pareto Efficiency**:
*   **Pareto Dominance**: The outcome **(Silent, Silent)**, where both serve 1 year, **Pareto dominates** the Nash Equilibrium **(Betray, Betray)**. An outcome Pareto dominates another if at least one player is better off and no player is worse off. Here, both players are significantly better off with (1, 1) than with (5, 5).
*   **The Conflict**: Despite (Silent, Silent) being a better collective outcome, it is **unstable**. If the players were at (Silent, Silent), both would have a massive individual incentive to "cheat" and Betray the other to reduce their own sentence from 1 year to 0.

#### **Coordination and Communication**
*   **Coordination Problems**: There are no coordination problems in the traditional sense (like in a "Battle of the Sexes" game) because there is only one equilibrium. The players do not need to coordinate to find the Nash Equilibrium; their individual rationality leads them there automatically.
*   **Communication**: Even if the suspects were allowed to communicate and "pinky swear" to remain silent, the Nash Equilibrium would likely remain (Betray, Betray). In a one-shot game, talk is "cheap." Without a way to enforce the agreement, the incentive to betray remains, and each player would fear the other is lying to get the 0-year sentence.

### Summary
The only Nash Equilibrium is **(Betray, Betray)**. While it results in a worse outcome for both players than mutual silence, it is the only logically stable point because "Betray" is a strictly dominant strategy for both individuals.

## Dominant Strategies
This scenario is the classic **Prisoner's Dilemma**. Below is the formal analysis of the game structure and the dominant strategy analysis.

### Payoff Matrix (Years in Prison)
To analyze the strategies, we first represent the payoffs in a matrix. Note that in this game, **lower numbers are better** (fewer years in prison).

| Suspect A \ Suspect B | Betray (Defect) | Silent (Cooperate) |
| :--- | :--- | :--- |
| **Betray (Defect)** | (-5, -5) | (0, -10) |
| **Silent (Cooperate)** | (-10, 0) | (-1, -1) |

---

### 1. Strictly Dominant Strategies
A strategy is **strictly dominant** if it provides a better payoff than any other strategy, regardless of what the opponent chooses.

*   **Suspect A**: 
    *   If B chooses *Silent*, A gets **0** years by Betraying vs. **1** year by staying Silent. (Betray is better)
    *   If B chooses *Betray*, A gets **5** years by Betraying vs. **10** years by staying Silent. (Betray is better)
    *   **Result**: **Betray** is the strictly dominant strategy for Suspect A.
*   **Suspect B**: 
    *   If A chooses *Silent*, B gets **0** years by Betraying vs. **1** year by staying Silent. (Betray is better)
    *   If A chooses *Betray*, B gets **5** years by Betraying vs. **10** years by staying Silent. (Betray is better)
    *   **Result**: **Betray** is the strictly dominant strategy for Suspect B.

### 2. Weakly Dominant Strategies
A strategy is **weakly dominant** if it is at least as good as any other strategy in all cases and strictly better in at least one.

*   Since "Betray" is **strictly** dominant for both players, it is by definition also their weakly dominant strategy. There are no other weakly dominant strategies in this game.

### 3. Dominated Strategies
A strategy is **dominated** if there is another strategy that always yields a better payoff.

*   **Suspect A**: **Silent** is strictly dominated by **Betray**. No matter what B does, A is always worse off by staying silent.
*   **Suspect B**: **Silent** is strictly dominated by **Betray**. No matter what A does, B is always worse off by staying silent.

### 4. Iteratively Eliminated Strategies
Iterated Elimination of Strictly Dominated Strategies (IESDS) is a process used to simplify a game by removing strategies that a rational player would never choose.

1.  **Round 1**: We eliminate the "Silent" strategy for Suspect A because it is strictly dominated by "Betray."
2.  **Round 2**: Knowing that a rational Suspect A will never choose "Silent," Suspect B looks at their remaining options. "Silent" remains strictly dominated for B as well. We eliminate "Silent" for Suspect B.
3.  **Outcome**: The only remaining strategy profile is **(Betray, Betray)**.

---

### Strategic Implications

The analysis of dominant strategies reveals the core tension of the Prisoner's Dilemma:

1.  **The Nash Equilibrium**: The strategy profile **(Betray, Betray)** is the unique Nash Equilibrium. Neither player can improve their outcome by changing their strategy unilaterally. If A switches to Silent while B Betrays, A goes from 5 years to 10 years.
2.  **Individual vs. Collective Rationality**: The dominant strategy leads to a "sub-optimal" outcome for the group. If both players remained silent, they would each serve only 1 year (2 years total). However, because they cannot communicate or enforce an agreement (non-cooperative), individual rationality compels them both to betray, resulting in a total of 10 years served between them.
3.  **The Incentive to Defect**: Even if the players could communicate and agree to stay silent, the "Betray" strategy remains strictly dominant. Each player would have a 0-year incentive to break the agreement and betray the other, making a "Silent-Silent" agreement unstable in a one-shot game.
4.  **Pareto Inefficiency**: The Nash Equilibrium (5, 5) is **Pareto inefficient**. There exists another outcome (1, 1) where *both* players would be better off, but they cannot reach it because of the individual incentive to betray.

## Key Recommendations
This analysis provides strategic recommendations for **Suspect A** and **Suspect B** based on the classic **Prisoner’s Dilemma** structure identified in the game analysis.

---

### **Strategic Recommendations for Suspect A & Suspect B**

Since the game is symmetric, the strategic logic applies equally to both players.

#### **1. Optimal Strategy: Betray (Defect)**
*   **Recommendation**: Both players should choose to **Betray**.
*   **Why**: In game theory, "Betray" is a **strictly dominant strategy**. This means that regardless of what the other player chooses, betraying always results in a better individual outcome:
    *   If Suspect B stays silent, Suspect A gets 0 years (by betraying) instead of 1 year (by staying silent).
    *   If Suspect B betrays, Suspect A gets 5 years (by betraying) instead of 10 years (by staying silent).
*   The Nash Equilibrium is (Betray, Betray), as neither player can improve their situation by unilaterally changing their mind.

#### **2. Contingent Strategies**
*   **If you believe the opponent is Rational**: You must **Betray**. A rational opponent will recognize their dominant strategy is to betray you. Staying silent would result in the "Sucker’s Payoff" (10 years).
*   **If you believe the opponent is Altruistic/Naive**: You should still **Betray**. While it feels counter-intuitive, if they stay silent, your payoff improves from 1 year to 0 years by betraying them.
*   **If there is an external threat (e.g., Organized Crime)**: If an outside party will kill you for "snitching," the payoffs change. In that specific context, **Silent** becomes the rational choice because the external cost of betrayal exceeds 10 years.

#### **3. Risk Assessment**
*   **Risk of Betraying**: The primary risk is the **Collective Sub-optimal Outcome**. By both players following their individual rational interest, you both end up with 5 years instead of the 1 year you could have achieved through mutual silence.
*   **Risk of Silence**: This is the **Maximum Risk**. If you stay silent and the other betrays, you receive the maximum penalty (10 years). In a one-shot game with no communication, this risk is generally considered unacceptable.

#### **4. Coordination Opportunities**
*   **Pre-existing Agreements**: In a one-shot, non-cooperative game, coordination is nearly impossible. Even if the suspects agreed to stay silent before being arrested, the incentive to "cheat" on that agreement once in the interrogation room is overwhelming.
*   **Binding Contracts**: Coordination only works if there is a **binding mechanism** (e.g., a legal contract or a credible threat of retaliation) that changes the payoffs, making betrayal more expensive than silence.

#### **5. Information Considerations**
*   **Information Asymmetry**: The police rely on your lack of information about the other's choice. 
*   **Signaling**: In a simultaneous game, you cannot signal. However, if you can project an image of "unwavering loyalty" or "irrationality" through your reputation, you might influence the other player to stay silent—though, strategically, you would still benefit most by betraying that trust.

---

### **Overall Strategic Insights**
*   **Individual vs. Collective Rationality**: This game highlights the fundamental tension where individual pursuit of self-interest leads to a worse outcome for the group.
*   **The Power of the "Default"**: Without a way to enforce cooperation, the game "gravitates" toward mutual betrayal.
*   **Stability**: The (Betray, Betray) outcome is stable because neither player regrets their choice *given what the other person did*. Even if they both get 5 years, Suspect A knows that if they had stayed silent, they would have gotten 10.

### **Potential Pitfalls**
*   **The "Trust Trap"**: Assuming the other player will stay silent because of friendship or a "pact." In a high-stakes one-shot game, the structural incentive to betray usually overrides personal sentiment.
*   **Miscalculating Payoffs**: Failing to realize that "0 years" is better than "1 year." Some players mistakenly view (1,1) as the "best" outcome, forgetting that (0,10) is better for the individual who betrays.
*   **Ignoring the One-Shot Nature**: Treating this like a repeated interaction. If you will never see this person again, there is no "shadow of the future" to punish your betrayal.

### **Implementation Guidance**
1.  **Analyze the Payoff Matrix**: Confirm that the police have not changed the years offered.
2.  **Assume Rationality**: Operate under the assumption that Suspect B is also analyzing the matrix and sees that Betrayal is their best move.
3.  **Execute the Dominant Strategy**: Choose **Betray**. It protects you against the worst-case scenario (10 years) and offers the best-case scenario (0 years).
4.  **Maintain Silence regarding Strategy**: Do not attempt to negotiate with the police or the other suspect in a way that reveals your fear; simply execute the strategy that minimizes your sentence.

---
**Analysis completed in 80s**
