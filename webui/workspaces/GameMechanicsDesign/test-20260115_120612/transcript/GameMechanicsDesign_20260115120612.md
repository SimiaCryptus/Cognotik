# Game Mechanics Design

**Game Concept:** A deck-building roguelike where cards represent weather phenomena and players must manage atmospheric pressure.

**Started:** 2026-01-15 12:06:12

---

## Core Mechanics

### 1. Isobaric Equilibrium

**Description:** Instead of traditional 'Mana,' players manage a bi-directional Pressure Gauge ranging from -50 (Deep Low) to +50 (Extreme High). Every card has a 'Pressure Delta' (e.g., a Thunderstorm card might subtract 15 pressure). If the gauge hits either extreme, the player suffers a 'Structural Collapse,' taking massive damage and resetting the gauge to zero.

**Player Actions:**
- Choosing cards for their effect
- Moving the needle toward the 'Stable Zone' (0)
- Moving toward 'Extreme Zones' for power bonuses

**System Response:** The system calculates the new pressure total after every card play. Being in the 'High Pressure' zone grants defensive buffs but limits card draw; 'Low Pressure' increases damage but makes cards more expensive.

**Properties:**
- Feedback Type: immediate
- Skill Expression: high
- Luck Factor: 20.0%

**Interactions:**
- Frontal Convergence: synergy - The player uses Isobaric Equilibrium to stay alive while trying to trigger Frontal Convergence for maximum efficiency.
- Thermodynamic Instability: synergy - Swinging the pressure gauge back and forth across the zero-point builds Instability stacks.

---

### 2. Frontal Convergence

**Description:** Cards are tagged with 'Fronts' (Warm, Cold, or Occluded). Playing cards in specific sequences creates 'Frontal Systems' that trigger secondary, high-impact effects without costing extra pressure. For example, playing a Cold Front card immediately after a Warm Front card triggers 'Supercell,' doubling the damage of the next card.

**Player Actions:**
- Sequencing the order of card plays
- Maximizing 'Convergence' triggers
- Hand-sculpting to find specific tags

**System Response:** When a valid sequence is detected, the UI highlights the 'Convergence' effect, applying a powerful global modifier to the current turn.

**Properties:**
- Feedback Type: immediate
- Skill Expression: high
- Luck Factor: 40.0%

**Interactions:**
- Dew Point Saturation: synergy - Using the zero-cost turn of Saturation to play complex Frontal sequences for massive damage.

---

### 3. Thermodynamic Instability

**Description:** Every time the player switches the pressure gauge from Positive to Negative (or vice versa), they gain a stack of 'Instability.' Instability acts as a global damage multiplier for both the player and the enemy. This ensures that 20-minute runs escalate quickly and don't stall into defensive stalemates.

**Player Actions:**
- Intentionally 'swinging' the pressure gauge back and forth across the zero-point to build offensive momentum

**System Response:** The 'Instability' counter increments, visually distorting the screen and increasing all numerical values on cards.

**Properties:**
- Feedback Type: cumulative
- Skill Expression: medium
- Luck Factor: 10.0%

**Interactions:**
- Dew Point Saturation: synergy - Leveraging built-up Instability to ensure the Dew Point Saturation finisher is lethal enough to end the fight.

---

### 4. Dew Point Saturation

**Description:** As cards are played, 'Humidity' builds up in a secondary reservoir. Once Humidity reaches 100%, the player can enter 'Saturation State.' In this state, all cards cost 0 Pressure for one turn, but at the end of the turn, the player’s Pressure Gauge is set to a random extreme (+50 or -50), forcing an immediate 'Structural Collapse' unless they have a specific 'Vent' card.

**Player Actions:**
- Activating 'Saturation' to dump a hand of high-damage cards for a finishing blow
- Calculating if the turn will kill the enemy before the collapse kills the player

**System Response:** The UI turns deep blue/grey; card costs are ignored; a 'Countdown to Collapse' timer appears.

**Properties:**
- Feedback Type: delayed
- Skill Expression: high
- Luck Factor: 30.0%

**Interactions:**
- Isobaric Equilibrium: conflict - Saturation bypasses pressure costs but results in a gauge extreme and subsequent Structural Collapse.

---

## Mechanic Interactions

**Summary:** 6 interactions analyzed
- Synergies: 4
- Conflicts: 2
- Neutral: 0

### Synergies

#### Isobaric Equilibrium ↔ Frontal Convergence
Isobaric Equilibrium acts as the 'bottleneck' for Frontal Convergence. The gauge dictates if a combo is safe to perform; a player at +40 Pressure might be unable to play a Warm Front card needed to start a combo without triggering a Structural Collapse.

⚠️ **Balance Concern:** If the Pressure Deltas on Front cards are too high, players may find it impossible to complete complex sequences. Conversely, if combos always move the gauge toward zero, they become 'auto-includes' because they provide power while managing resources.

#### Isobaric Equilibrium ↔ Thermodynamic Instability
Thermodynamic Instability rewards the player for crossing the zero-point (the 'Equilibrium'). This encourages a playstyle where the player intentionally swings the gauge from -20 to +20 rather than hovering safely at zero.

⚠️ **Balance Concern:** Since Instability increases damage for both the player and the enemy, a player who crosses the zero-point too often might make the 'Structural Collapse' damage from Isobaric Equilibrium instantly lethal.

#### Frontal Convergence ↔ Thermodynamic Instability
Frontal Convergence provides multipliers (like Supercell’s 2x damage), and Thermodynamic Instability provides a global multiplier. Timing a 'Supercell' combo to occur exactly when crossing the zero-point spikes damage output exponentially.

⚠️ **Balance Concern:** This could lead to 'One-Turn Kills' (OTKs). If the multipliers stack multiplicatively, a late-game player might deal millions of damage in one sequence, trivializing boss mechanics.

#### Frontal Convergence ↔ Dew Point Saturation
During 'Saturation State,' the 0-pressure cost allows the player to chain 5, 6, or 7 Front cards in a row, allowing for 'Mega-Fronts' or multiple 'Supercells' in a single turn.

⚠️ **Balance Concern:** This interaction makes 'Humidity' the most valuable secondary stat. Players will likely build decks that generate Humidity as fast as possible to enter Saturation, turning the game into a 'waiting for the big turn' loop.

### Conflicts

#### Isobaric Equilibrium ⚔ Dew Point Saturation
Dew Point Saturation provides a temporary reprieve from Isobaric Equilibrium (0 cost cards) but concludes with a guaranteed 'Structural Collapse'. This turns the Pressure Gauge into a ticking time bomb.

⚠️ **Balance Concern:** The 'Vent' card becomes the most important card in the game. If a player draws a Vent card, the downside of DPS is negated. Without a Vent card, the damage from Structural Collapse must be significant enough that players only use Saturation as a 'finisher'.

#### Thermodynamic Instability ⚔ Dew Point Saturation
Thermodynamic Instability increases the damage the player takes, while Dew Point Saturation ends in a mandatory Structural Collapse. High stacks of Instability make the 'Structural Collapse' at the end of Saturation potentially lethal.

⚠️ **Balance Concern:** This creates a natural 'soft cap' on run length. As Instability rises, using the Saturation mechanic becomes increasingly suicidal, preventing players from stalling indefinitely.

---

## Progression System

**Summary:** 13 levels designed

| Level | XP Required | Difficulty | Playtime | Unlocks |
|-------|-------------|------------|----------|---------|
| 1 | 0 | 1.0x | 0.0h | 2 |
| 2 | 1000 | 1.1x | 0.3h | 3 |
| 3 | 2500 | 1.2x | 1.0h | 1 |
| 4 | 4500 | 1.4x | 2.5h | 3 |
| 5 | 7000 | 1.6x | 4.0h | 3 |
| 6 | 10000 | 1.8x | 6.0h | 2 |
| 7 | 14000 | 2.0x | 8.5h | 2 |
| 8 | 19000 | 2.5x | 11.0h | 2 |
| 9 | 25000 | 3.0x | 14.0h | 1 |
| 10 | 32000 | 3.5x | 17.5h | 2 |
| 11 | 40000 | 4.0x | 21.0h | 2 |
| 12 | 50000 | 5.0x | 25.0h | 2 |
| 13 | 65000 | 6.0x | 30.0h | 1 |

### Detailed Progression

#### Level 1

- **XP Required:** 0
- **Difficulty:** 1.0x
- **Estimated Playtime:** 0.0 hours

**Unlocks:**
- Isobaric Equilibrium
- Basic 'High Pressure' (Shielding) and 'Low Pressure' (Damage) cards

#### Level 2

- **XP Required:** 1000
- **Difficulty:** 1.1x
- **Estimated Playtime:** 0.3 hours

**Unlocks:**
- Dew Point Saturation
- Cards now apply 'Humidity'
- Precipitation bonus effect

#### Level 3

- **XP Required:** 2500
- **Difficulty:** 1.2x
- **Estimated Playtime:** 1.0 hours

**Unlocks:**
- New Starter Deck: 'The Maritime Polar'

#### Level 4

- **XP Required:** 4500
- **Difficulty:** 1.4x
- **Estimated Playtime:** 2.5 hours

**Unlocks:**
- Frontal Convergence
- Cold or Warm tags
- Frontal Boundary

#### Level 5

- **XP Required:** 7000
- **Difficulty:** 1.6x
- **Estimated Playtime:** 4.0 hours

**Unlocks:**
- Thermodynamic Instability
- CAPE (Convective Available Potential Energy)
- Static (dead cards)

#### Level 6

- **XP Required:** 10000
- **Difficulty:** 1.8x
- **Estimated Playtime:** 6.0 hours

**Unlocks:**
- New Starter Deck: 'The Continental Tropical'
- Drought mechanics

#### Level 7

- **XP Required:** 14000
- **Difficulty:** 2.0x
- **Estimated Playtime:** 8.5 hours

**Unlocks:**
- Vorticity Mechanics
- Rotating cards

#### Level 8

- **XP Required:** 19000
- **Difficulty:** 2.5x
- **Estimated Playtime:** 11.0 hours

**Unlocks:**
- Elite Enemy Variants
- Jet Streams

#### Level 9

- **XP Required:** 25000
- **Difficulty:** 3.0x
- **Estimated Playtime:** 14.0 hours

**Unlocks:**
- Legendary 'Supercell' Cards

#### Level 10

- **XP Required:** 32000
- **Difficulty:** 3.5x
- **Estimated Playtime:** 17.5 hours

**Unlocks:**
- New Starter Deck: 'The Arctic Oscillation'
- Freezing mechanics

#### Level 11

- **XP Required:** 40000
- **Difficulty:** 4.0x
- **Estimated Playtime:** 21.0 hours

**Unlocks:**
- Global Modifiers
- El Niño or La Niña modifiers

#### Level 12

- **XP Required:** 50000
- **Difficulty:** 5.0x
- **Estimated Playtime:** 25.0 hours

**Unlocks:**
- The 'Pressure Gradient' Difficulty System
- 10 additional 'Heat' levels (Ascension)

#### Level 13

- **XP Required:** 65000
- **Difficulty:** 6.0x
- **Estimated Playtime:** 30.0 hours

**Unlocks:**
- The True Final Boss: 'The Anthropocene'

---



## Error Occurred

**Error:** No response

<details><summary>Stack Trace</summary>

```
java.lang.RuntimeException: No response
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.<init>(ParsedAgent.kt:82)
	at com.simiacryptus.cognotik.agents.ParsedAgent.respond(ParsedAgent.kt:205)
	at com.simiacryptus.cognotik.agents.ParsedAgent.respond(ParsedAgent.kt:10)
	at com.simiacryptus.cognotik.agents.BaseAgent.answer(BaseAgent.kt:24)
	at com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask.run$lambda$1(GameMechanicsDesignTask.kt:875)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
	at java.base/java.util.concurrent.FutureTask.run$$$capture(FutureTask.java:317)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java)
	at --- Async.Stack.Trace --- (captured by IntelliJ IDEA debugger)
	at java.base/java.util.concurrent.FutureTask.<init>(FutureTask.java:151)
	at java.base/java.util.concurrent.AbstractExecutorService.newTaskFor(AbstractExecutorService.java:98)
	at java.base/java.util.concurrent.AbstractExecutorService.submit(AbstractExecutorService.java:122)
	at com.simiacryptus.cognotik.util.ImmediateExecutorService.submit(ImmediateExecutorService.kt:77)
	at com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask.run(GameMechanicsDesignTask.kt:362)
	at com.simiacryptus.cognotik.apps.SingleTaskApp.executeTask(SingleTaskApp.kt:105)
	at com.simiacryptus.cognotik.apps.SingleTaskApp.startSession$lambda$0(SingleTaskApp.kt:83)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
	at java.base/java.util.concurrent.FutureTask.run$$$capture(FutureTask.java:317)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java)
	at --- Async.Stack.Trace --- (captured by IntelliJ IDEA debugger)
	at java.base/java.util.concurrent.FutureTask.<init>(FutureTask.java:151)
	at java.base/java.util.concurrent.AbstractExecutorService.newTaskFor(AbstractExecutorService.java:98)
	at java.base/java.util.concurrent.AbstractExecutorService.submit(AbstractExecutorService.java:122)
	at com.simiacryptus.cognotik.util.ImmediateExecutorService.submit(ImmediateExecutorService.kt:77)
	at com.simiacryptus.cognotik.apps.SingleTaskApp.startSession(SingleTaskApp.kt:83)
	at com.simiacryptus.cognotik.util.UnifiedHarness$runTask$singleTaskApp$1.newSession(UnifiedHarness.kt:278)
	at com.simiacryptus.cognotik.util.UnifiedHarness.runTask(UnifiedHarness.kt:298)
	at com.simiacryptus.cognotik.util.TaskHarness.run(TaskHarness.kt:65)
	at com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTaskTest.test(GameMechanicsDesignTaskTest.kt:38)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:787)
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:479)
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:131)
	at org.junit.jupiter.engine.extension.SameThreadTimeoutInvocation.proceed(SameThreadTimeoutInvocation.java:49)
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:161)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:152)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:91)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:112)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:94)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:106)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:64)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:45)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:37)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:93)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:87)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$4(TestMethodTestDescriptor.java:221)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:217)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:159)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:157)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:147)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:145)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:101)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:161)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:147)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:145)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:101)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:161)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:147)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:145)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:101)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:35)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:54)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.executeEngine(EngineExecutionOrchestrator.java:230)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.failOrExecuteEngine(EngineExecutionOrchestrator.java:204)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:172)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:101)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.lambda$execute$0(EngineExecutionOrchestrator.java:64)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.withInterceptedStreams(EngineExecutionOrchestrator.java:150)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:63)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:109)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:91)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.junit.platform.launcher.core.InterceptingLauncher.lambda$execute$1(InterceptingLauncher.java:39)
	at org.junit.platform.launcher.core.ClasspathAlignmentCheckingLauncherInterceptor.intercept(ClasspathAlignmentCheckingLauncherInterceptor.java:25)
	at org.junit.platform.launcher.core.InterceptingLauncher.execute(InterceptingLauncher.java:38)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor$CollectAllTestClassesExecutor.processAllTestClasses(JUnitPlatformTestClassProcessor.java:135)
	at org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor$CollectAllTestClassesExecutor.access$000(JUnitPlatformTestClassProcessor.java:110)
	at org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor.stop(JUnitPlatformTestClassProcessor.java:104)
	at org.gradle.api.internal.tasks.testing.SuiteTestClassProcessor.stop(SuiteTestClassProcessor.java:64)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at org.gradle.internal.dispatch.MethodInvocation.invokeOn(MethodInvocation.java:77)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:28)
	at org.gradle.internal.dispatch.ReflectionDispatch.dispatch(ReflectionDispatch.java:19)
	at org.gradle.internal.dispatch.ContextClassLoaderDispatch.dispatch(ContextClassLoaderDispatch.java:33)
	at org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler.invoke(ProxyDispatchAdapter.java:88)
	at jdk.proxy2/jdk.proxy2.$Proxy6.stop(Unknown Source)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker$3.run(TestWorker.java:194)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.executeAndMaintainThreadName(TestWorker.java:126)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:103)
	at org.gradle.api.internal.tasks.testing.worker.TestWorker.execute(TestWorker.java:63)
	at org.gradle.process.internal.worker.child.ActionExecutionWorker.execute(ActionExecutionWorker.java:56)
	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:122)
	at org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker.call(SystemApplicationClassLoaderWorker.java:72)
	at worker.org.gradle.process.internal.worker.GradleWorkerMain.run(GradleWorkerMain.java:69)
	at worker.org.gradle.process.internal.worker.GradleWorkerMain.main(GradleWorkerMain.java:74)

```
</details>
