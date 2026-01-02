# Game Theory Analysis: A repeated Cournot competition between two firms in a duopoly market.

## Players
Firm A, Firm B

## Game Type
non-cooperative

## Game Structure
This analysis examines the strategic interaction of a **Repeated Cournot Duopoly**, a foundational model in industrial organization and game theory used to understand how firms compete on quantity over time.

---

### 1. Identify the Game Structure
*   **Game Type:** 
    *   **Non-cooperative:** Each firm makes decisions independently to maximize its own profit, even if their actions affect one another.
    *   **Non-zero-sum:** The total profit in the market is not fixed; both firms can be profitable, or both can suffer losses (e.g., in a price war).
*   **Temporal Dimension:** 
    *   **Repeated Game:** This is not a one-off interaction. The stage game (Cournot competition) is played multiple times (either over a finite or infinite horizon). This allows for the development of reputations and "trigger strategies."
*   **Information Structure:** 
    *   **Imperfect Information (within stages):** In each period, firms choose their quantities simultaneously without knowing the other’s choice for that specific period.
    *   **Perfect Information (across stages):** Firms have "perfect recall," meaning they observe the actual quantities produced by their competitor in all previous periods.
*   **Asymmetries:** 
    *   In a standard model, firms are often assumed to be **symmetric** (identical marginal costs and products). However, asymmetries can exist in cost structures ($C_A \neq C_B$) or production capacities.

### 2. Define Strategy Spaces
*   **Available Strategies:** 
    *   In the **stage game**, the strategy is the choice of quantity ($q_i$).
    *   In the **repeated game**, a strategy is a complete contingent plan that specifies the quantity to produce in period $t$ based on the history of play in periods $1$ through $t-1$.
*   **Discrete vs. Continuous:** 
    *   Strategies are typically **continuous**. Firms can choose any quantity $q_i$ within a range $[0, Q_{max}]$.
*   **Constraints:** 
    *   **Non-negativity:** $q_i \ge 0$.
    *   **Capacity Constraints:** Firms may be limited by physical plant size or resource availability ($q_i \le K_i$).

### 3. Characterize Payoffs
*   **Objectives:** The primary objective is the maximization of the **Present Discounted Value (PDV)** of all current and future profits: 
    $$\sum_{t=0}^{\infty} \delta^t \pi_{it}$$
    where $\delta$ is the discount factor (representing the time value of money and the probability of the game continuing).
*   **Outcome Dependence:** 
    *   Payoffs are highly interdependent. The market price $P$ is determined by the aggregate quantity $Q = q_A + q_B$. 
    *   If Firm B increases production, the market price drops, which reduces Firm A’s profit even if Firm A’s production remains constant.
*   **Transferability:** 
    *   Payoffs are generally **non-transferable**. In a non-cooperative duopoly, firms cannot legally engage in "side payments" (direct cash transfers) to balance profits, though they can coordinate outcomes through their production choices.

### 4. Identify Key Features
*   **Commitment and Signaling:** 
    *   In a repeated setting, a firm’s current quantity choice serves as a **signal** of its future intent. 
    *   Firms can use "Trigger Strategies" (e.g., **Grim Trigger** or **Tit-for-Tat**) to commit to a punishment phase if the other firm deviates from a tacitly agreed-upon collusive quantity.
*   **Communication and Coordination:** 
    *   While explicit communication (cartels) is often illegal under antitrust laws, repeated interaction allows for **tacit collusion**. 
    *   Firms may "coordinate" on the monopoly quantity (splitting the highest possible industry profit) without ever speaking, using the threat of a price war as a deterrent.
*   **Timing of Moves:** 
    *   **Simultaneous within stages:** In each round, firms commit to a production volume at the same time.
    *   **Sequential across stages:** The game moves forward in discrete time steps ($t=1, 2, 3...$).
*   **The Role of the Discount Factor ($\delta$):** 
    *   The stability of cooperation depends on $\delta$. If firms value future profits highly (high $\delta$), they are less likely to "cheat" for a short-term gain today, as the future punishment (returning to the Cournot-Nash equilibrium) would be too costly.

---

### Summary Table: Cournot Duopoly Analysis

| Feature | Description |
| :--- | :--- |
| **Primary Variable** | Quantity ($q$) |
| **Equilibrium (One-shot)** | Cournot-Nash Equilibrium (where $MC = MR$ for both) |
| **Equilibrium (Repeated)** | Range of outcomes from Cournot-Nash to Tacit Collusion |
| **Key Risk** | The "Incentive to Cheat" (producing more to capture market share) |
| **Punishment Mechanism** | Reverting to high-production/low-profit levels in future rounds |
| **Market Outcome** | Price is lower than Monopoly but higher than Perfect Competition |

## Nash Equilibria
This analysis explores the strategic interaction of a **Repeated Cournot Duopoly**. In this game, two firms (A and B) compete by choosing quantities ($q$) of a homogeneous product simultaneously in each period.

---

### Part 1: Game Structure Analysis

**1. Game Type:**
*   **Non-Cooperative:** While firms may reach a "collusive-like" outcome, there is no binding, legally enforceable contract to coordinate.
*   **Repeated Game:** This is not a one-shot interaction. The firms play the same stage game over multiple periods ($t = 1, 2, \dots, T$ or $\infty$).
*   **Simultaneous Moves:** In each period, firms choose their quantities without knowing the other's choice for that specific period.
*   **Information:** Usually characterized by **Perfect Information** (firms observe each other's past quantity choices) but **Imperfect Information** within the stage (simultaneous moves).

**2. Strategy Spaces:**
*   **Continuous:** In the standard Cournot model, firms can choose any quantity $q_i \in [0, \infty)$.
*   **Repeated Game Strategies:** Strategies are not just quantities, but "plans of action" that dictate what quantity to produce based on the history of the game (e.g., "Grim Trigger," "Tit-for-Tat").

**3. Characterize Payoffs:**
*   **Objective:** Maximize the present value of the sum of expected profits over the game's horizon.
*   **Payoff Function:** $\pi_i(q_i, q_j) = [P(q_i + q_j) - c] \cdot q_i$, where $P$ is the market price and $c$ is the marginal cost.
*   **Non-Transferable:** Profits are earned individually based on market sales.

**4. Key Features:**
*   **Timing:** Simultaneous moves within each period; sequential across periods.
*   **Folk Theorem:** In an infinitely repeated game with a sufficiently high discount factor ($\delta$), any payoff that is better for both players than the stage-game Nash equilibrium can be sustained as a Subgame Perfect Nash Equilibrium (SPNE).

---

### Part 2: Nash Equilibrium Analysis

In a repeated Cournot game, there are two primary types of Nash Equilibria: the **Stage-Game Nash** (repeated) and **Collusive Equilibria** (sustained by trigger strategies).

#### 1. The Repeated Stage-Game Nash Equilibrium
*   **Strategy Profile:** Both firms produce the Cournot quantity $q^C = \frac{a-c}{3b}$ in every period, regardless of the other firm's past behavior.
*   **Why it’s a Nash Equilibrium:** In any single period, if Firm B produces $q^C$, Firm A’s best response is to produce $q^C$. Since this holds for every period, neither firm has an incentive to deviate unilaterally.
*   **Classification:** Pure Strategy Equilibrium.
*   **Stability and Likelihood:** **Highly Stable.** This is the "default" outcome. It does not require trust or a high discount factor. It is the likely outcome if the game is finitely repeated with a known end date (due to backward induction).

#### 2. The Collusive (Cooperative) Equilibrium (Trigger Strategy)
*   **Strategy Profile (Grim Trigger):** 
    *   **Period 1:** Produce the monopoly share $q^M/2 = \frac{a-c}{4b}$.
    *   **Period $t$:** Produce $q^M/2$ if the opponent has always produced $q^M/2$ in the past. If the opponent has ever deviated, produce the Cournot quantity $q^C$ forever (punishment).
*   **Why it’s a Nash Equilibrium:** If the discount factor $\delta$ (the value of future money) is high enough, the long-term benefit of maintaining high profits outweighs the one-time gain from "cheating" (producing more to capture the market). Specifically, it is an equilibrium if:
    $$\frac{\pi_{collusion}}{1-\delta} \geq \pi_{cheat} + \frac{\delta \cdot \pi_{Nash}}{1-\delta}$$
*   **Classification:** Pure Strategy (Subgame Perfect Nash Equilibrium).
*   **Stability and Likelihood:** **Conditionally Stable.** It depends entirely on the firms' patience ($\delta$) and the ability to monitor each other. If the market is volatile or the end of the game is near, this equilibrium collapses.

---

### Part 3: Discussion of Multiple Equilibria

Because of the **Folk Theorem**, an infinitely repeated Cournot game has an infinite number of Nash Equilibria (any quantity between the monopoly quantity and the Cournot quantity can be sustained if $\delta$ is high enough).

**1. Which is most likely to occur?**
*   **The Stage-Game Nash** is the most likely in "low-trust" environments or when the firms are not sure how long the interaction will last.
*   **The Collusive Equilibrium** is more likely in mature, stable industries with few competitors (duopolies) where firms recognize their mutual interdependence.

**2. Coordination Problems:**
*   Firms face a coordination problem regarding *which* cooperative outcome to target. Should they split the monopoly profit 50/50? What if one firm has slightly lower costs? 
*   There is also the "Focal Point" problem: firms must agree on what constitutes a "deviation" and how long the punishment should last (e.g., Grim Trigger vs. Tit-for-Tat).

**3. Pareto Dominance:**
*   The **Collusive Equilibrium Pareto-dominates** the Stage-Game Nash Equilibrium. Both firms earn strictly higher profits under collusion than they do under the standard Cournot competition.
*   However, the Collusive Equilibrium is **not** a global optimum for society, as it results in higher prices and lower quantities for consumers (Deadweight Loss).

**Summary Table:**

| Equilibrium Type | Strategy | Profitability | Stability |
| :--- | :--- | :--- | :--- |
| **Repeated Nash** | Constant $q^C$ | Moderate | High (Default) |
| **Collusive (SPNE)** | $q^M/2$ + Trigger | High | Fragile (Depends on $\delta$) |
| **War of Attrition** | Over-production | Low/Negative | Unstable |

## Dominant Strategies
This analysis examines a **Repeated Cournot Competition** between two firms (Firm A and Firm B). In this model, firms compete on the quantity of a homogeneous product produced, and the market price is determined by the total supply.

---

### Part 1: Game Structure Analysis

1.  **Game Type**: 
    *   **Non-cooperative**: Each firm seeks to maximize its own profit without a binding, enforceable agreement.
    *   **Simultaneous (within rounds)**: In each period, firms choose their quantities without knowing the other's choice for that specific period.
    *   **Repeated**: The stage game is played multiple times (either finitely or infinitely), allowing for history-dependent strategies.

2.  **Strategy Spaces**:
    *   **Continuous**: Firms can choose any quantity $q_i \in [0, \infty)$, though practically limited by capacity or the point where price equals marginal cost.
    *   **Complex (Repeated context)**: In a repeated game, a "strategy" is not just a single quantity, but a complete plan of action for every possible history of play (e.g., "Trigger strategies" or "Tit-for-Tat").

3.  **Characterize Payoffs**:
    *   **Objective**: Maximization of the present value of a stream of profits: $\sum_{t=0}^{T} \delta^t \pi_{it}$, where $\delta$ is the discount factor.
    *   **Interdependence**: Firm A’s profit depends on its own quantity ($q_A$) and Firm B’s quantity ($q_B$). As total quantity $Q = q_A + q_B$ increases, the market price $P(Q)$ decreases.

4.  **Key Features**:
    *   **Information**: Imperfect information within each round (simultaneous moves), but perfect information regarding the history of previous rounds.
    *   **Timing**: Simultaneous moves in each stage; sequential across stages.
    *   **Signaling**: Firms can use current production levels to signal future intent or to punish/reward the competitor for past behavior.

---

### Part 2: Dominant Strategy Analysis

In a Cournot competition, the optimal choice for one firm is inherently tied to the choice of the other. This leads to the following findings:

#### 1. Strictly Dominant Strategies
*   **None.**
*   In Cournot models, there is no single quantity that is best regardless of the opponent's choice. If Firm B produces a very small amount, Firm A should produce a large amount (approaching the monopoly quantity). If Firm B produces a very large amount, Firm A should produce a small amount to avoid crashing the market price. Because the "Best Response" changes based on the opponent's move, no strictly dominant strategy exists.

#### 2. Weakly Dominant Strategies
*   **None.**
*   For the same reasons as above, no strategy is "at least as good" as all others across all possible moves by the opponent. The payoff matrix (or function) for Cournot competition is characterized by **strategic substitutes**: as one firm increases production, the other's marginal profit decreases.

#### 3. Dominated Strategies
While there are no dominant strategies, there are many **dominated** strategies:
*   **Quantities exceeding the "Perfect Competition" level**: Any quantity $q_i$ that results in a price below marginal cost ($P < MC$) is dominated by producing $q_i = 0$.
*   **Quantities exceeding the Monopoly quantity ($q_m$)**: For a firm in a duopoly, producing more than the total monopoly quantity of the entire market is dominated. Even if the opponent produces zero, the firm would make more profit by producing the monopoly quantity than by producing more and driving the price down excessively.

#### 4. Iteratively Eliminated Strategies
Through the process of **Iterated Deletion of Strictly Dominated Strategies (IDSDS)**, we can narrow the strategy space:
*   **Step 1**: Eliminate any $q_i$ that is higher than the monopoly quantity $q_m$. A rational firm knows its opponent will not produce more than $q_m$.
*   **Step 2**: Given that $q_B \leq q_m$, Firm A will never produce less than the best response to $q_m$.
*   **Convergence**: In a standard linear Cournot model, if you infinitely iterate this process of "I know that you know that I won't produce $X$," the strategy space collapses to a single point: the **Cournot-Nash Equilibrium**.

---

### Strategic Implications

1.  **The "Folk Theorem" in Repeated Play**: Because this is a *repeated* game, the lack of a dominant strategy in the stage game allows for the emergence of **collusion**. If the discount factor ($\delta$) is high enough (i.e., firms value future profits sufficiently), they can sustain the monopoly outcome by using "Trigger Strategies." They agree to produce half the monopoly quantity each, with the threat that if one cheats, the other will revert to the Cournot-Nash quantity (or a "price war") forever.

2.  **Interdependence and Monitoring**: Since there is no dominant strategy, firms are forced to be "reactive." The strategic implication is that monitoring the opponent's output becomes the most critical activity. In a repeated game, the history of play serves as a coordination mechanism.

3.  **Stability of the Nash Equilibrium**: In a one-shot game, firms are stuck at the Cournot-Nash equilibrium because they cannot trust each other. In a repeated game, the Nash equilibrium of the stage game acts as a "threat point" or punishment level that facilitates higher-profit cooperative outcomes.

4.  **Incentive to Cheat**: Even though collusion is possible in repeated play, the absence of a dominant strategy means the **incentive to deviate** is always present. If Firm A believes Firm B will produce the low "collusive" quantity, Firm A's *best response* is to produce slightly more to capture more market share, which is the fundamental tension in any duopoly.

## Key Recommendations
This analysis explores the strategic dynamics of a **Repeated Cournot Duopoly**. In this scenario, two firms (A and B) compete on quantity ($q$) rather than price, and they interact over multiple periods, allowing for the development of reputations and the possibility of tacit collusion.

---

### Part 1: Game Structure Analysis

1.  **Identify the Game Structure**:
    *   **Type**: Non-cooperative (no legally binding contracts), simultaneous move (within each period).
    *   **Repetition**: Repeated game (infinitely repeated or with an uncertain end date).
    *   **Information**: Imperfect information within a round (moves are simultaneous), but perfect information regarding past actions (history is observable).
    *   **Asymmetries**: Usually modeled as symmetric (identical marginal costs), though asymmetries in production capacity or cost structures can exist.

2.  **Define Strategy Spaces**:
    *   **Strategies**: Continuous. Each firm chooses a quantity $q_i \in [0, \infty)$.
    *   **Choices**: Firms can choose the **Cournot-Nash quantity** (competitive), the **Monopoly/Collusive quantity** (restricted), or a **Defection quantity** (over-producing to capture market share).

3.  **Characterize Payoffs**:
    *   **Objective**: Maximize the present value of the stream of profits over time.
    *   **Payoff Function**: $\pi_i = (P(q_A + q_B) - c)q_i$.
    *   **Dynamics**: The "Folk Theorem" suggests that if players are sufficiently patient (high discount factor $\delta$), any payoff better than the Nash equilibrium can be sustained.

4.  **Key Features**:
    *   **Timing**: Simultaneous moves in each period.
    *   **Signaling**: Past quantity choices serve as signals of intent.
    *   **Punishment**: The ability to "punish" a defector in future rounds is the primary mechanism for maintaining stability.

---

### Part 2: Strategic Recommendations

Since the firms are in a symmetric duopoly, the recommendations apply to both **Firm A** and **Firm B**.

#### 1. Optimal Strategy: Tacit Collusion (The "Cooperative" Quantity)
*   **Recommendation**: Aim to produce exactly **half of the monopoly quantity** ($q_m / 2$).
*   **Why**: In a one-shot Cournot game, firms produce more than the monopoly level, leading to lower prices and lower total profits. By restricting output to the collusive level, both firms maximize their long-term joint profits, which is superior to the Cournot-Nash outcome.

#### 2. Contingent Strategies: "Grim Trigger" or "Tit-for-Tat"
*   **Recommendation**: Adopt a **Grim Trigger** strategy if the market is stable, or **Tit-for-Tat** if the market is volatile.
    *   **Grim Trigger**: Start by cooperating. If the opponent ever over-produces (cheats), revert to the Cournot-Nash quantity *forever*. This is a powerful deterrent.
    *   **Tit-for-Tat**: Start by cooperating. In the next round, mimic the opponent’s previous move. This is more "forgiving" and prevents a permanent collapse of profits due to a single misunderstanding or external shock.

#### 3. Risk Assessment
*   **Detection Lag**: There is a risk that the opponent cheats in Period $T$, but you don't realize it until Period $T+1$. The opponent gains a "one-period windfall."
*   **Discount Factor Risk**: If your opponent is "impatient" (needs cash now or fears the market will soon disappear), they will value the immediate gain of cheating more than future cooperation.
*   **Demand Volatility**: If market demand drops, an opponent might produce the same quantity, which looks like "cheating" because prices fall, potentially triggering an accidental price war.

#### 4. Coordination Opportunities
*   **Focal Points**: Use "round numbers" or historical production levels to signal a desired equilibrium without explicit (illegal) communication.
*   **Industry Benchmarking**: Publicly announcing capacity constraints or "commitment to value over volume" can signal to the competitor that you do not intend to flood the market.

#### 5. Information Considerations
*   **Transparency**: High transparency regarding market prices helps both firms monitor each other. If prices stay high, it’s a signal that both are cooperating.
*   **Signal Jamming**: Be wary of the opponent trying to hide their true production volume through third-party distributors or secondary markets.

---

### Part 3: Overall Strategic Insights

*   **The Shadow of the Future**: Cooperation is only possible if the game is expected to continue. If a firm knows the "game" ends next month, they have a dominant strategy to cheat (Selten’s Theorem).
*   **Credibility of Punishment**: For a trigger strategy to work, your opponent must believe you *will* actually increase production and tank the price if they cheat. If you are perceived as "weak," they will exploit you.
*   **The "Middle Ground" Trap**: Producing slightly more than the collusive level but less than the Nash level often results in the worst of both worlds: you trigger a punishment from your rival without gaining the full windfall of a total defection.

### Part 4: Potential Pitfalls

*   **Over-reacting to Noise**: Mistaking a temporary drop in market demand for "cheating" by the rival and launching a permanent punishment (Grim Trigger).
*   **Predictability**: Being too predictable in your punishment can allow a sophisticated rival to calculate the exact "cost of cheating" and exploit you periodically.
*   **Ignoring Asymmetry**: If Firm B has much lower costs than Firm A, they have less incentive to collude because their Cournot-Nash profit is already high.

### Part 5: Implementation Guidance

1.  **Establish the "Status Quo"**: Begin the relationship by producing at a stable, moderate level. Avoid aggressive "market share grabs" in the early stages.
2.  **Monitor Market Signals**: Closely track market clearing prices. If the price falls below the expected "collusive price" given the current demand, investigate if the rival has increased quantity.
3.  **Communicate via Action**: If the rival over-produces by 10%, respond by over-producing by 10% in the next period (Tit-for-Tat). This communicates: *"I am monitoring you, and I will match your aggression, but I am willing to return to cooperation if you do."*
4.  **Maintain "Dry Powder"**: Ensure you have the excess capacity to flood the market if a punishment phase is required. The *threat* of capacity is often more effective than the *use* of capacity.

---
**Analysis completed in 114s**
