# Game Narrative Design: The Crystal Shards of Eldoria

# Game Narrative Design

**Game Title:** The Crystal Shards of Eldoria
**Genre:** RPG

## Configuration
- Narrative Style: branching
- Player Agency: high
- Main Characters: 3
- Branching Points: 5
- Endings: 3
- Estimated Playtime: 5 hours
- Tone: heroic
- Player Role: protagonist

## Features
- Dialogue Trees: ✓
- Character Arcs: ✓
- Side Quests: ✓

**Started:** 2026-01-09 10:44:17

---

## Progress

### Phase 1: Base Narrative Generation
*Generating core narrative structure...*


**Error:** ```text
java.lang.RuntimeException: Failed to parse response:   {
    "title": "The Crystal Shards of Eldoria",
    "premise": "A high-agency RPG framework focusing on the ideological tug-of-war between a magical past and a technological future.",
    "setting": "Eldoria, featuring locations such as Aethelgard, the industrial city of Oros, and the magical Spire.",
    "acts": [
      {
        "act_number": 1,
        "title": "The Escape from Aethelgard",
        "description": "The protagonist, now a Shard-Binder, must escape the city of Aethelgard while being pursued by the High Artificer's forces.",
        "key_events": [
          "The protagonist becomes a vessel for the Shards",
          "Cornered by Clockwork Seekers at the docks",
          "Escape via Kaelen’s submersible"
        ],
        "character_developments": {
          "The Shard-Binder": "Transitions from a passive archivist to an active participant in the world's fate.",
          "Elara Vane": "Introduced as a magical specialist seeking to restore the past.",
          "Kaelen Jax": "Introduced as a pragmatic tech specialist focused on the common people."
        }
      },
      {
        "act_number": 2,
        "title": "The Foundry and the Rift",
        "description": "The party infiltrates the industrial heart of Oros and faces internal ideological conflicts.",
        "key_events": [
          "Infiltration of the Foundry inner sanctum",
          "Confrontation with High Artificer Vane's projection",
          "The ideological argument at the safehouse"
        ],
        "character_developments": {
          "Elara Vane": "Begins to realize the flaws and elitism of the old magical ways.",
          "Kaelen Jax": "Moves toward a belief that technology can democratize power."
        }
      },
      {
        "act_number": 3,
        "title": "The Heart of Eldoria",
        "description": "The final confrontation at the Crystal Heart where the ultimate fate of the world is decided.",
        "key_events": [
          "Final siege using ally-specific ultimate abilities",
          "Standing before the Crystal Heart",
          "The final decision to shatter, stabilize, or synthesize"
        ],
        "character_developments": {
          "The Shard-Binder": "Becomes the sole architect of the future, bearing the physical toll of the Shards."
        }
      }
    ],
    "characters": [
      {
        "name": "The Shard-Binder",
        "role": "Protagonist / Player Character",
        "arc": "From a passive observer of history to the sole architect of the future.",
        "motivations": [
          "Survival",
          "Understanding the 'Great Fade'",
          "Deciding the world's fate"
        ],
        "relationship_to_player": "Self",
        "dialogue_style": "Player-driven",
        "key_scenes": [
          "All scenes"
        ]
      },
      {
        "name": "Elara Vane",
        "role": "Ally / Magical Specialist",
        "arc": "Moves from a desperate desire to 'restore the past' to realizing that the old ways were flawed and elitist.",
        "motivations": [
          "Preserve the beauty of magic",
          "Stop her father from 'lobotomizing' the world’s soul"
        ],
        "relationship_to_player": "Ally and ideological representative of Magic",
        "dialogue_style": "Academic, lyrical, occasionally elitist, deeply empathetic",
        "branching_reactions": {
          "Restoring magic": "Strong approval",
          "Industrial solutions": "Disapproval",
          "Siding with High Artificer": "Hostility"
        }
      },
      {
        "name": "Kaelen Jax",
        "role": "Ally / Tech Specialist",
        "arc": "Moves from cynical self-interest to a belief that technology can democratize power.",
        "motivations": [
          "Provide a stable world for the 'un-gifted' masses",
          "Ensure no one is left behind by 'fading' magic"
        ],
        "relationship_to_player": "Ally and ideological representative of Technology",
        "dialogue_style": "Gruff, sarcastic, practical, uses industrial slang",
        "branching_reactions": {
          "Technological progress": "Approval",
          "Magical miracles": "Skepticism",
          "Restoring magical hierarchy": "Betrayal"
        }
      }
    ],
    "branching_points": [
      {
        "id": "branch_1",
        "location": "Aethelgard Docks",
        "description": "The party is cornered by Clockwork Seekers.",
        "choices": [
          {
            "choice_id": "b1_magic",
            "text": "[Magic] Overload the Spire’s Wards",
            "consequences": "High collateral damage to the Spire; Elara gains influence.",
            "emotional_tone": "Powerful/Destructive",
            "character_reactions": "Elara approves; Kaelen is concerned about the damage."
          },
          {
            "choice_id": "b1_tech",
            "text": "[Tech] Sabotage the Steam-Vents",
            "consequences": "Safer escape; Kaelen gains influence.",
            "emotional_tone": "Tactical/Pragmatic",
            "character_reactions": "Kaelen approves; Elara finds it crude."
          },
          {
            "choice_id": "b1_binder",
            "text": "[Binder] Channel the Shard",
            "consequences": "Increases 'Crystalline Corruption' stat; disables seekers.",
            "emotional_tone": "Desperate/Sacrificial",
            "character_reactions": "Both allies are worried about the protagonist's health."
          }
        ],
        "convergence_point": "Kaelen’s submersible"
      },
      {
        "id": "branch_2",
        "location": "The Foundry",
        "description": "How to enter the inner sanctum.",
        "choices": [
          {
            "choice_id": "b2_stealth",
            "text": "The Ghost Walk",
            "consequences": "Reveals lore about the 'Great Fade'.",
            "emotional_tone": "Mysterious",
            "character_reactions": "Favors Elara's methods."
          },
          {
            "choice_id": "b2_combat",
            "text": "The Front Door",
            "consequences": "High combat; gains favor with the Oros working class.",
            "emotional_tone": "Aggressive",
            "character_reactions": "Favors Kaelen's methods."
          }
        ],
        "convergence_point": "The Observation Deck"
      },
      {
        "id": "branch_3",
        "location": "Observation Deck",
        "description": "High Artificer Vane offers a seat at his side.",
        "choices": [
          {
            "choice_id": "b3_accept",
            "text": "Accept the Offer",
            "consequences": "Unlocks 'Infiltrator' dialogue options; allies become suspicious.",
            "emotional_tone": "Deceptive",
            "unlocks": "Infiltrator dialogue"
          },
          {
            "choice_id": "b3_reject",
            "text": "Defiant Rejection",
            "consequences": "Triggers immediate boss fight with an Aether-Sentinel.",
            "emotional_tone": "Heroic/Rebellious"
          }
        ],
        "convergence_point": "The Safehouse"
      },
      {
        "id": "branch_4",
        "location": "Safehouse",
        "description": "Final argument about the Great Engine.",
        "choices": [
          {
            "choice_id": "b4_elara",
            "text": "Side with Elara",
            "consequences": "Vow to destroy the Engine; unlocks Elara's Ultimate Ability.",
            "emotional_tone": "Idealistic"
          },
          {
            "choice_id": "b4_kaelen",
            "text": "Side with Kaelen",
            "consequences": "Vow to repurpose the Engine; unlocks Kaelen's Ultimate Ability.",
            "emotional_tone": "Utilitarian"
          },
          {
            "choice_id": "b4_middle",
            "text": "The Middle Path",
            "consequences": "Refuse to choose; sets up Synthesis ending.",
            "emotional_tone": "Determined"
          }
        ],
        "convergence_point": "The Final Siege"
      },
      {
        "id": "branch_5",
        "location": "Crystal Heart",
        "description": "The final decision for Eldoria.",
        "choices": [
          {
            "choice_id": "b5_shatter",
            "text": "Shatter the Shards",
            "consequences": "Releases energy back into the world.",
            "emotional_tone": "Transcendent"
          },
          {
            "choice_id": "b5_stabilize",
            "text": "Stabilize the Engine",
            "consequences": "Feeds the Heart into the machine.",
            "emotional_tone": "Grounded"
          },
          {
            "choice_id": "b5_synthesis",
            "text": "The Synthesis",
            "consequences": "Fuse protagonist's soul with the Heart.",
            "emotional_tone": "Harmonious"
          }
        ],
        "convergence_point": "End Credits"
      }
    ],
    "endings": [
      {
        "ending_id": "ending_a",
        "title": "The Eternal Dawn",
        "description": "The Great Fade is reversed and magic returns in a flood.",
        "conditions": "Side with Elara in Branch 4; choose 'Shatter the Shards' in Branch 5.",
        "character_fates": "Elara becomes High Mage; Kaelen leaves in disgust; Protagonist becomes a Spirit of the Shard.",
        "thematic_resolution": "Tradition and wonder are preserved, but industrial progress is erased.",
        "epilogue": "The Spires float once more, but the common man's tools lie silent."
      },
      {
        "ending_id": "ending_b",
        "title": "The Iron Horizon",
        "description": "Magic is extinguished, replaced by clean mechanical energy for all.",
        "conditions": "Side with Kaelen in Branch 4; choose 'Stabilize the Engine' in Branch 5.",
        "character_fates": "Kaelen becomes Chief Engineer; Elara goes into exile; Protagonist becomes a mortal hero with scars.",
        "thematic_resolution": "Security and equality are achieved through the death of wonder.",
        "epilogue": "Eldoria is powered by the Engine, stable but devoid of the supernatural."
      },
      {
        "ending_id": "ending_c",
        "title": "The Resonance",
        "description": "A new form of 'Techno-Magic' is born, bridging both worlds.",
        "conditions": "Maintain high 'Trust' with both Elara and Kaelen; choose 'The Synthesis' in Branch 5.",
        "character_fates": "Elara and Kaelen lead a new Council together; Protagonist remains the 'Living Bridge'.",
        "thematic_resolution": "Evolution through compromise.",
        "epilogue": "A difficult but hopeful middle ground where magic and machine coexist."
      }
    ],
    "themes": [
      "Tradition vs. Progress",
      "Magic vs. Technology",
      "Democratization of Power",
      "The Burden of Choice",
      "Compromise and Synthesis"
    ],
    "player_role": "The Shard-Binder: A former archivist turned living vessel for magical power.",
    "estimated_playtime": "5 hours"
  }
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:180)
	at com.simiacryptus.cognotik.agents.ParsedAgent.getParser$lambda$0(ParsedAgent.kt:138)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl._obj_delegate$lambda$0(ParsedAgent.kt:92)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.get_obj(ParsedAgent.kt:83)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.getObj(ParsedAgent.kt:95)
	at com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask.run(GameNarrativeDesignTask.kt:570)
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
	at com.simiacryptus.cognotik.util.UnifiedHarness$runTask$singleTaskApp$1.newSession(UnifiedHarness.kt:273)
	at com.simiacryptus.cognotik.util.UnifiedHarness.runTask(UnifiedHarness.kt:293)
	at com.simiacryptus.cognotik.util.TaskHarness.run(TaskHarness.kt:63)
	at com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTaskTest.test(GameNarrativeDesignTaskTest.kt:47)
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
Caused by: java.lang.RuntimeException: Failed to parse JSON: {
  "title": "The Crystal Shards of Eldoria",
  "premise": "A high-agency RPG framework focusing on the ideological tug-of-war between a magical past and a technological future.",
  "setting": "Eldoria, featuring locations such as Aethelgard, the industrial city of Oros, and the magical Spire.",
  "acts": [
    {
      "act_number": 1,
      "title": "The Escape from Aethelgard",
      "description": "The protagonist, now a Shard-Binder, must escape the city of Aethelgard while being pursued by the High Artificer's forces.",
      "key_events": [
        "The protagonist becomes a vessel for the Shards",
        "Cornered by Clockwork Seekers at the docks",
        "Escape via Kaelen’s submersible"
      ],
      "character_developments": {
        "The Shard-Binder": "Transitions from a passive archivist to an active participant in the world's fate.",
        "Elara Vane": "Introduced as a magical specialist seeking to restore the past.",
        "Kaelen Jax": "Introduced as a pragmatic tech specialist focused on the common people."
      }
    },
    {
      "act_number": 2,
      "title": "The Foundry and the Rift",
      "description": "The party infiltrates the industrial heart of Oros and faces internal ideological conflicts.",
      "key_events": [
        "Infiltration of the Foundry inner sanctum",
        "Confrontation with High Artificer Vane's projection",
        "The ideological argument at the safehouse"
      ],
      "character_developments": {
        "Elara Vane": "Begins to realize the flaws and elitism of the old magical ways.",
        "Kaelen Jax": "Moves toward a belief that technology can democratize power."
      }
    },
    {
      "act_number": 3,
      "title": "The Heart of Eldoria",
      "description": "The final confrontation at the Crystal Heart where the ultimate fate of the world is decided.",
      "key_events": [
        "Final siege using ally-specific ultimate abilities",
        "Standing before the Crystal Heart",
        "The final decision to shatter, stabilize, or synthesize"
      ],
      "character_developments": {
        "The Shard-Binder": "Becomes the sole architect of the future, bearing the physical toll of the Shards."
      }
    }
  ],
  "characters": [
    {
      "name": "The Shard-Binder",
      "role": "Protagonist / Player Character",
      "arc": "From a passive observer of history to the sole architect of the future.",
      "motivations": [
        "Survival",
        "Understanding the 'Great Fade'",
        "Deciding the world's fate"
      ],
      "relationship_to_player": "Self",
      "dialogue_style": "Player-driven",
      "key_scenes": [
        "All scenes"
      ]
    },
    {
      "name": "Elara Vane",
      "role": "Ally / Magical Specialist",
      "arc": "Moves from a desperate desire to 'restore the past' to realizing that the old ways were flawed and elitist.",
      "motivations": [
        "Preserve the beauty of magic",
        "Stop her father from 'lobotomizing' the world’s soul"
      ],
      "relationship_to_player": "Ally and ideological representative of Magic",
      "dialogue_style": "Academic, lyrical, occasionally elitist, deeply empathetic",
      "branching_reactions": {
        "Restoring magic": "Strong approval",
        "Industrial solutions": "Disapproval",
        "Siding with High Artificer": "Hostility"
      }
    },
    {
      "name": "Kaelen Jax",
      "role": "Ally / Tech Specialist",
      "arc": "Moves from cynical self-interest to a belief that technology can democratize power.",
      "motivations": [
        "Provide a stable world for the 'un-gifted' masses",
        "Ensure no one is left behind by 'fading' magic"
      ],
      "relationship_to_player": "Ally and ideological representative of Technology",
      "dialogue_style": "Gruff, sarcastic, practical, uses industrial slang",
      "branching_reactions": {
        "Technological progress": "Approval",
        "Magical miracles": "Skepticism",
        "Restoring magical hierarchy": "Betrayal"
      }
    }
  ],
  "branching_points": [
    {
      "id": "branch_1",
      "location": "Aethelgard Docks",
      "description": "The party is cornered by Clockwork Seekers.",
      "choices": [
        {
          "choice_id": "b1_magic",
          "text": "[Magic] Overload the Spire’s Wards",
          "consequences": "High collateral damage to the Spire; Elara gains influence.",
          "emotional_tone": "Powerful/Destructive",
          "character_reactions": "Elara approves; Kaelen is concerned about the damage."
        },
        {
          "choice_id": "b1_tech",
          "text": "[Tech] Sabotage the Steam-Vents",
          "consequences": "Safer escape; Kaelen gains influence.",
          "emotional_tone": "Tactical/Pragmatic",
          "character_reactions": "Kaelen approves; Elara finds it crude."
        },
        {
          "choice_id": "b1_binder",
          "text": "[Binder] Channel the Shard",
          "consequences": "Increases 'Crystalline Corruption' stat; disables seekers.",
          "emotional_tone": "Desperate/Sacrificial",
          "character_reactions": "Both allies are worried about the protagonist's health."
        }
      ],
      "convergence_point": "Kaelen’s submersible"
    },
    {
      "id": "branch_2",
      "location": "The Foundry",
      "description": "How to enter the inner sanctum.",
      "choices": [
        {
          "choice_id": "b2_stealth",
          "text": "The Ghost Walk",
          "consequences": "Reveals lore about the 'Great Fade'.",
          "emotional_tone": "Mysterious",
          "character_reactions": "Favors Elara's methods."
        },
        {
          "choice_id": "b2_combat",
          "text": "The Front Door",
          "consequences": "High combat; gains favor with the Oros working class.",
          "emotional_tone": "Aggressive",
          "character_reactions": "Favors Kaelen's methods."
        }
      ],
      "convergence_point": "The Observation Deck"
    },
    {
      "id": "branch_3",
      "location": "Observation Deck",
      "description": "High Artificer Vane offers a seat at his side.",
      "choices": [
        {
          "choice_id": "b3_accept",
          "text": "Accept the Offer",
          "consequences": "Unlocks 'Infiltrator' dialogue options; allies become suspicious.",
          "emotional_tone": "Deceptive",
          "unlocks": "Infiltrator dialogue"
        },
        {
          "choice_id": "b3_reject",
          "text": "Defiant Rejection",
          "consequences": "Triggers immediate boss fight with an Aether-Sentinel.",
          "emotional_tone": "Heroic/Rebellious"
        }
      ],
      "convergence_point": "The Safehouse"
    },
    {
      "id": "branch_4",
      "location": "Safehouse",
      "description": "Final argument about the Great Engine.",
      "choices": [
        {
          "choice_id": "b4_elara",
          "text": "Side with Elara",
          "consequences": "Vow to destroy the Engine; unlocks Elara's Ultimate Ability.",
          "emotional_tone": "Idealistic"
        },
        {
          "choice_id": "b4_kaelen",
          "text": "Side with Kaelen",
          "consequences": "Vow to repurpose the Engine; unlocks Kaelen's Ultimate Ability.",
          "emotional_tone": "Utilitarian"
        },
        {
          "choice_id": "b4_middle",
          "text": "The Middle Path",
          "consequences": "Refuse to choose; sets up Synthesis ending.",
          "emotional_tone": "Determined"
        }
      ],
      "convergence_point": "The Final Siege"
    },
    {
      "id": "branch_5",
      "location": "Crystal Heart",
      "description": "The final decision for Eldoria.",
      "choices": [
        {
          "choice_id": "b5_shatter",
          "text": "Shatter the Shards",
          "consequences": "Releases energy back into the world.",
          "emotional_tone": "Transcendent"
        },
        {
          "choice_id": "b5_stabilize",
          "text": "Stabilize the Engine",
          "consequences": "Feeds the Heart into the machine.",
          "emotional_tone": "Grounded"
        },
        {
          "choice_id": "b5_synthesis",
          "text": "The Synthesis",
          "consequences": "Fuse protagonist's soul with the Heart.",
          "emotional_tone": "Harmonious"
        }
      ],
      "convergence_point": "End Credits"
    }
  ],
  "endings": [
    {
      "ending_id": "ending_a",
      "title": "The Eternal Dawn",
      "description": "The Great Fade is reversed and magic returns in a flood.",
      "conditions": "Side with Elara in Branch 4; choose 'Shatter the Shards' in Branch 5.",
      "character_fates": "Elara becomes High Mage; Kaelen leaves in disgust; Protagonist becomes a Spirit of the Shard.",
      "thematic_resolution": "Tradition and wonder are preserved, but industrial progress is erased.",
      "epilogue": "The Spires float once more, but the common man's tools lie silent."
    },
    {
      "ending_id": "ending_b",
      "title": "The Iron Horizon",
      "description": "Magic is extinguished, replaced by clean mechanical energy for all.",
      "conditions": "Side with Kaelen in Branch 4; choose 'Stabilize the Engine' in Branch 5.",
      "character_fates": "Kaelen becomes Chief Engineer; Elara goes into exile; Protagonist becomes a mortal hero with scars.",
      "thematic_resolution": "Security and equality are achieved through the death of wonder.",
      "epilogue": "Eldoria is powered by the Engine, stable but devoid of the supernatural."
    },
    {
      "ending_id": "ending_c",
      "title": "The Resonance",
      "description": "A new form of 'Techno-Magic' is born, bridging both worlds.",
      "conditions": "Maintain high 'Trust' with both Elara and Kaelen; choose 'The Synthesis' in Branch 5.",
      "character_fates": "Elara and Kaelen lead a new Council together; Protagonist remains the 'Living Bridge'.",
      "thematic_resolution": "Evolution through compromise.",
      "epilogue": "A difficult but hopeful middle ground where magic and machine coexist."
    }
  ],
  "themes": [
    "Tradition vs. Progress",
    "Magic vs. Technology",
    "Democratization of Power",
    "The Burden of Choice",
    "Compromise and Synthesis"
  ],
  "player_role": "The Shard-Binder: A former archivist turned living vessel for magical power.",
  "estimated_playtime": "5 hours"
}
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:101)
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:166)
	... 110 more
Caused by: com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot construct instance of `java.util.ArrayList` (although at least one Creator exists): no String-argument constructor/factory method to deserialize from String value ('High collateral damage to the Spire; Elara gains influence.')
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 107, column: 27] (through reference chain: com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$GameNarrative["branching_points"]->java.util.ArrayList[0]->com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$BranchingPoint["choices"]->java.util.ArrayList[0]->com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$NarrativeChoice["consequences"])
	at com.fasterxml.jackson.databind.exc.MismatchedInputException.from(MismatchedInputException.java:63)
	at com.fasterxml.jackson.databind.DeserializationContext.reportInputMismatch(DeserializationContext.java:1781)
	at com.fasterxml.jackson.databind.DeserializationContext.handleMissingInstantiator(DeserializationContext.java:1406)
	at com.fasterxml.jackson.databind.deser.std.StdDeserializer._deserializeFromString(StdDeserializer.java:310)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.handleNonArray(StringCollectionDeserializer.java:284)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:193)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:183)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:27)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer._deserializeFromArray(CollectionDeserializer.java:360)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:245)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:29)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer._deserializeFromArray(CollectionDeserializer.java:360)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:245)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:29)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.DefaultDeserializationContext.readRootValue(DefaultDeserializationContext.java:342)
	at com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4971)
	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3887)
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:92)
	... 111 more

```

```text
java.lang.RuntimeException: Failed to parse response:   {
    "title": "The Crystal Shards of Eldoria",
    "premise": "A high-agency RPG framework focusing on the ideological tug-of-war between a magical past and a technological future in the world of Eldoria.",
    "setting": "Eldoria, featuring the magical Spires of Aethelgard, the industrial city of Oros, the Foundry, and the Wastes.",
    "acts": [
      {
        "act_number": 1,
        "title": "The Awakening and Escape",
        "description": "The protagonist transforms into the Shard-Binder and must escape the city of Aethelgard while being pursued by the High Artificer's forces.",
        "key_events": [
          "The protagonist becomes a vessel for the Shards",
          "Meeting Elara Vane and Kaelen Jax",
          "The escape from Aethelgard docks"
        ],
        "character_developments": {
          "Shard-Binder": "Transitions from a passive archivist to a powerful, crystalline-veined vessel.",
          "Elara Vane": "Introduced as a magical idealist seeking to restore the past.",
          "Kaelen Jax": "Introduced as a pragmatic mercenary focused on the 'un-gifted' masses."
        }
      },
      {
        "act_number": 2,
        "title": "The Industrial Heart",
        "description": "The party infiltrates the industrial city of Oros to confront the High Artificer, leading to deep ideological rifts within the group.",
        "key_events": [
          "Infiltration of the Foundry",
          "Confrontation with High Artificer Vane",
          "The ideological argument at the safehouse"
        ],
        "character_developments": {
          "Elara Vane": "Begins to realize the elitist flaws of the old magical ways.",
          "Kaelen Jax": "Shifts from cynical self-interest to a vision of technological democracy."
        }
      },
      {
        "act_number": 3,
        "title": "The Heart of Eldoria",
        "description": "The final siege and the ultimate decision at the Crystal Heart that will define the future of the world.",
        "key_events": [
          "The final siege",
          "Reaching the Crystal Heart",
          "The final choice for Eldoria's fate"
        ],
        "character_developments": {
          "Shard-Binder": "Becomes the sole architect of the future.",
          "Elara and Kaelen": "Their relationship with the player culminates in either betrayal, exile, or cooperation."
        }
      }
    ],
    "characters": [
      {
        "name": "The Shard-Binder",
        "role": "Player Character / Protagonist",
        "arc": "From a passive observer of history to the sole architect of the future.",
        "motivations": [
          "Survival",
          "Understanding the 'Great Fade'",
          "Deciding the world's fate"
        ],
        "relationship_to_player": "Self",
        "dialogue_style": "Player-driven",
        "key_scenes": [
          "All scenes"
        ]
      },
      {
        "name": "Elara Vane",
        "role": "Ally / Magical Specialist",
        "arc": "Moves from a desire to restore the past to realizing the old ways were flawed and elitist.",
        "motivations": [
          "Preserve the beauty of magic",
          "Stop her father from 'lobotomizing' the world's soul"
        ],
        "relationship_to_player": "Ally / Potential Antagonist (if player sides with Artificer)",
        "dialogue_style": "Academic, lyrical, occasionally elitist, deeply empathetic",
        "branching_reactions": {
          "Restore Magic": "Approves",
          "Industrial Solutions": "Disapproves",
          "Side with Artificer": "Hostile"
        }
      },
      {
        "name": "Kaelen Jax",
        "role": "Ally / Tech Specialist",
        "arc": "Moves from cynical self-interest to a belief that technology can democratize power.",
        "motivations": [
          "Provide a stable world for the 'un-gifted' masses"
        ],
        "relationship_to_player": "Ally / Skeptic",
        "dialogue_style": "Gruff, sarcastic, practical, uses industrial slang",
        "branching_reactions": {
          "Technological Progress": "Approves",
          "Magical Miracles": "Skeptical",
          "Restore Magical Hierarchy": "Feels betrayed"
        }
      }
    ],
    "branching_points": [
      {
        "id": "branch_1_escape",
        "location": "Aethelgard Docks",
        "description": "The party is cornered by Clockwork Seekers.",
        "choices": [
          {
            "choice_id": "magic_overload",
            "text": "[Magic] Overload the Spire’s Wards",
            "emotional_tone": "Powerful/Destructive",
            "character_reactions": {
              "Elara": "Gains influence"
            },
            "consequences": {
              "Spire": "High collateral damage"
            }
          },
          {
            "choice_id": "tech_sabotage",
            "text": "[Tech] Sabotage the Steam-Vents",
            "emotional_tone": "Tactical",
            "character_reactions": {
              "Kaelen": "Gains influence"
            },
            "consequences": {
              "Escape": "Safer escape"
            }
          },
          {
            "choice_id": "binder_channel",
            "text": "[Binder] Channel the Shard",
            "emotional_tone": "Desperate/Raw",
            "consequences": {
              "Stat": "Increases Crystalline Corruption"
            }
          }
        ],
        "convergence_point": "Kaelen’s submersible"
      },
      {
        "id": "branch_2_infiltration",
        "location": "The Foundry",
        "description": "How to enter the inner sanctum.",
        "choices": [
          {
            "choice_id": "ghost_walk",
            "text": "The Ghost Walk (Magic)",
            "emotional_tone": "Stealthy",
            "unlocks": {
              "Lore": "Reveals lore about the Great Fade"
            }
          },
          {
            "choice_id": "front_door",
            "text": "The Front Door (Tech)",
            "emotional_tone": "Aggressive",
            "character_reactions": {
              "Oros Working Class": "Gains favor"
            }
          }
        ],
        "convergence_point": "The Observation Deck"
      },
      {
        "id": "branch_3_vane_offer",
        "location": "Observation Deck",
        "description": "High Artificer Vane offers a seat at his side.",
        "choices": [
          {
            "choice_id": "accept_offer",
            "text": "Accept the Offer",
            "unlocks": {
              "Dialogue": "Infiltrator options"
            },
            "character_reactions": {
              "Elara": "Suspicious",
              "Kaelen": "Suspicious"
            }
          },
          {
            "choice_id": "reject_offer",
            "text": "Defiant Rejection",
            "consequences": {
              "Combat": "Triggers Aether-Sentinel boss fight"
            }
          }
        ],
        "convergence_point": "The Safehouse"
      },
      {
        "id": "branch_4_ideological_rift",
        "location": "Safehouse",
        "description": "Final argument about the Great Engine.",
        "choices": [
          {
            "choice_id": "side_elara",
            "text": "Side with Elara",
            "consequences": {
              "Vow": "Destroy the Engine"
            }
          },
          {
            "choice_id": "side_kaelen",
            "text": "Side with Kaelen",
            "consequences": {
              "Vow": "Repurpose the Engine"
            }
          },
          {
            "choice_id": "middle_path",
            "text": "The Middle Path",
            "emotional_tone": "Defiant/Independent"
          }
        ],
        "convergence_point": "Determines Ultimate Ability for final siege"
      },
      {
        "id": "branch_5_heart_decision",
        "location": "Crystal Heart",
        "description": "Standing before the Crystal Heart.",
        "choices": [
          {
            "choice_id": "shatter_shards",
            "text": "Shatter the Shards"
          },
          {
            "choice_id": "stabilize_engine",
            "text": "Stabilize the Engine"
          },
          {
            "choice_id": "synthesis",
            "text": "The Synthesis"
          }
        ]
      }
    ],
    "endings": [
      {
        "ending_id": "ending_a",
        "title": "The Eternal Dawn",
        "description": "The Great Fade is reversed and magic returns in a flood.",
        "conditions": "Side with Elara in Branch 4; choose 'Shatter the Shards' in Branch 5.",
        "character_fates": {
          "Elara": "High Mage of a new Academy",
          "Kaelen": "Leaves in disgust",
          "Protagonist": "Becomes a Spirit of the Shard"
        },
        "thematic_resolution": "Tradition and wonder are preserved, but industrial progress is erased.",
        "epilogue": "The Spires float once more, but the common man is left behind."
      },
      {
        "ending_id": "ending_b",
        "title": "The Iron Horizon",
        "description": "Magic is extinguished, but the Great Engine provides energy for all.",
        "conditions": "Side with Kaelen in Branch 4; choose 'Stabilize the Engine' in Branch 5.",
        "character_fates": {
          "Kaelen": "Chief Engineer of Oros",
          "Elara": "Goes into exile",
          "Protagonist": "Becomes a mortal hero with fading scars"
        },
        "thematic_resolution": "Security and equality are achieved through the death of wonder.",
        "epilogue": "A world of machines and equality, devoid of the supernatural."
      },
      {
        "ending_id": "ending_c",
        "title": "The Resonance",
        "description": "A new form of 'Techno-Magic' is born.",
        "conditions": "High 'Trust' with both allies; choose 'The Synthesis' in Branch 5.",
        "character_fates": {
          "Elara and Kaelen": "Lead a new Council together",
          "Protagonist": "Remains the 'Living Bridge'"
        },
        "thematic_resolution": "Evolution through compromise. A difficult but hopeful middle ground.",
        "epilogue": "The Heart powers the world while retaining its magical essence."
      }
    ],
    "themes": [
      "Tradition vs. Progress",
      "Magic vs. Technology",
      "Elitism vs. Democracy",
      "Compromise and Evolution"
    ],
    "player_role": "The Shard-Binder (Former archivist and living vessel for magical shards)",
    "estimated_playtime": "5 hours"
  }
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:180)
	at com.simiacryptus.cognotik.agents.ParsedAgent.getParser$lambda$0(ParsedAgent.kt:138)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl._obj_delegate$lambda$0(ParsedAgent.kt:92)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.get_obj(ParsedAgent.kt:83)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.getObj(ParsedAgent.kt:95)
	at com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask.run(GameNarrativeDesignTask.kt:570)
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
	at com.simiacryptus.cognotik.util.UnifiedHarness$runTask$singleTaskApp$1.newSession(UnifiedHarness.kt:273)
	at com.simiacryptus.cognotik.util.UnifiedHarness.runTask(UnifiedHarness.kt:293)
	at com.simiacryptus.cognotik.util.TaskHarness.run(TaskHarness.kt:63)
	at com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTaskTest.test(GameNarrativeDesignTaskTest.kt:47)
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
Caused by: java.lang.RuntimeException: Failed to parse JSON: {
  "title": "The Crystal Shards of Eldoria",
  "premise": "A high-agency RPG framework focusing on the ideological tug-of-war between a magical past and a technological future in the world of Eldoria.",
  "setting": "Eldoria, featuring the magical Spires of Aethelgard, the industrial city of Oros, the Foundry, and the Wastes.",
  "acts": [
    {
      "act_number": 1,
      "title": "The Awakening and Escape",
      "description": "The protagonist transforms into the Shard-Binder and must escape the city of Aethelgard while being pursued by the High Artificer's forces.",
      "key_events": [
        "The protagonist becomes a vessel for the Shards",
        "Meeting Elara Vane and Kaelen Jax",
        "The escape from Aethelgard docks"
      ],
      "character_developments": {
        "Shard-Binder": "Transitions from a passive archivist to a powerful, crystalline-veined vessel.",
        "Elara Vane": "Introduced as a magical idealist seeking to restore the past.",
        "Kaelen Jax": "Introduced as a pragmatic mercenary focused on the 'un-gifted' masses."
      }
    },
    {
      "act_number": 2,
      "title": "The Industrial Heart",
      "description": "The party infiltrates the industrial city of Oros to confront the High Artificer, leading to deep ideological rifts within the group.",
      "key_events": [
        "Infiltration of the Foundry",
        "Confrontation with High Artificer Vane",
        "The ideological argument at the safehouse"
      ],
      "character_developments": {
        "Elara Vane": "Begins to realize the elitist flaws of the old magical ways.",
        "Kaelen Jax": "Shifts from cynical self-interest to a vision of technological democracy."
      }
    },
    {
      "act_number": 3,
      "title": "The Heart of Eldoria",
      "description": "The final siege and the ultimate decision at the Crystal Heart that will define the future of the world.",
      "key_events": [
        "The final siege",
        "Reaching the Crystal Heart",
        "The final choice for Eldoria's fate"
      ],
      "character_developments": {
        "Shard-Binder": "Becomes the sole architect of the future.",
        "Elara and Kaelen": "Their relationship with the player culminates in either betrayal, exile, or cooperation."
      }
    }
  ],
  "characters": [
    {
      "name": "The Shard-Binder",
      "role": "Player Character / Protagonist",
      "arc": "From a passive observer of history to the sole architect of the future.",
      "motivations": [
        "Survival",
        "Understanding the 'Great Fade'",
        "Deciding the world's fate"
      ],
      "relationship_to_player": "Self",
      "dialogue_style": "Player-driven",
      "key_scenes": [
        "All scenes"
      ]
    },
    {
      "name": "Elara Vane",
      "role": "Ally / Magical Specialist",
      "arc": "Moves from a desire to restore the past to realizing the old ways were flawed and elitist.",
      "motivations": [
        "Preserve the beauty of magic",
        "Stop her father from 'lobotomizing' the world's soul"
      ],
      "relationship_to_player": "Ally / Potential Antagonist (if player sides with Artificer)",
      "dialogue_style": "Academic, lyrical, occasionally elitist, deeply empathetic",
      "branching_reactions": {
        "Restore Magic": "Approves",
        "Industrial Solutions": "Disapproves",
        "Side with Artificer": "Hostile"
      }
    },
    {
      "name": "Kaelen Jax",
      "role": "Ally / Tech Specialist",
      "arc": "Moves from cynical self-interest to a belief that technology can democratize power.",
      "motivations": [
        "Provide a stable world for the 'un-gifted' masses"
      ],
      "relationship_to_player": "Ally / Skeptic",
      "dialogue_style": "Gruff, sarcastic, practical, uses industrial slang",
      "branching_reactions": {
        "Technological Progress": "Approves",
        "Magical Miracles": "Skeptical",
        "Restore Magical Hierarchy": "Feels betrayed"
      }
    }
  ],
  "branching_points": [
    {
      "id": "branch_1_escape",
      "location": "Aethelgard Docks",
      "description": "The party is cornered by Clockwork Seekers.",
      "choices": [
        {
          "choice_id": "magic_overload",
          "text": "[Magic] Overload the Spire’s Wards",
          "emotional_tone": "Powerful/Destructive",
          "character_reactions": {
            "Elara": "Gains influence"
          },
          "consequences": {
            "Spire": "High collateral damage"
          }
        },
        {
          "choice_id": "tech_sabotage",
          "text": "[Tech] Sabotage the Steam-Vents",
          "emotional_tone": "Tactical",
          "character_reactions": {
            "Kaelen": "Gains influence"
          },
          "consequences": {
            "Escape": "Safer escape"
          }
        },
        {
          "choice_id": "binder_channel",
          "text": "[Binder] Channel the Shard",
          "emotional_tone": "Desperate/Raw",
          "consequences": {
            "Stat": "Increases Crystalline Corruption"
          }
        }
      ],
      "convergence_point": "Kaelen’s submersible"
    },
    {
      "id": "branch_2_infiltration",
      "location": "The Foundry",
      "description": "How to enter the inner sanctum.",
      "choices": [
        {
          "choice_id": "ghost_walk",
          "text": "The Ghost Walk (Magic)",
          "emotional_tone": "Stealthy",
          "unlocks": {
            "Lore": "Reveals lore about the Great Fade"
          }
        },
        {
          "choice_id": "front_door",
          "text": "The Front Door (Tech)",
          "emotional_tone": "Aggressive",
          "character_reactions": {
            "Oros Working Class": "Gains favor"
          }
        }
      ],
      "convergence_point": "The Observation Deck"
    },
    {
      "id": "branch_3_vane_offer",
      "location": "Observation Deck",
      "description": "High Artificer Vane offers a seat at his side.",
      "choices": [
        {
          "choice_id": "accept_offer",
          "text": "Accept the Offer",
          "unlocks": {
            "Dialogue": "Infiltrator options"
          },
          "character_reactions": {
            "Elara": "Suspicious",
            "Kaelen": "Suspicious"
          }
        },
        {
          "choice_id": "reject_offer",
          "text": "Defiant Rejection",
          "consequences": {
            "Combat": "Triggers Aether-Sentinel boss fight"
          }
        }
      ],
      "convergence_point": "The Safehouse"
    },
    {
      "id": "branch_4_ideological_rift",
      "location": "Safehouse",
      "description": "Final argument about the Great Engine.",
      "choices": [
        {
          "choice_id": "side_elara",
          "text": "Side with Elara",
          "consequences": {
            "Vow": "Destroy the Engine"
          }
        },
        {
          "choice_id": "side_kaelen",
          "text": "Side with Kaelen",
          "consequences": {
            "Vow": "Repurpose the Engine"
          }
        },
        {
          "choice_id": "middle_path",
          "text": "The Middle Path",
          "emotional_tone": "Defiant/Independent"
        }
      ],
      "convergence_point": "Determines Ultimate Ability for final siege"
    },
    {
      "id": "branch_5_heart_decision",
      "location": "Crystal Heart",
      "description": "Standing before the Crystal Heart.",
      "choices": [
        {
          "choice_id": "shatter_shards",
          "text": "Shatter the Shards"
        },
        {
          "choice_id": "stabilize_engine",
          "text": "Stabilize the Engine"
        },
        {
          "choice_id": "synthesis",
          "text": "The Synthesis"
        }
      ]
    }
  ],
  "endings": [
    {
      "ending_id": "ending_a",
      "title": "The Eternal Dawn",
      "description": "The Great Fade is reversed and magic returns in a flood.",
      "conditions": "Side with Elara in Branch 4; choose 'Shatter the Shards' in Branch 5.",
      "character_fates": {
        "Elara": "High Mage of a new Academy",
        "Kaelen": "Leaves in disgust",
        "Protagonist": "Becomes a Spirit of the Shard"
      },
      "thematic_resolution": "Tradition and wonder are preserved, but industrial progress is erased.",
      "epilogue": "The Spires float once more, but the common man is left behind."
    },
    {
      "ending_id": "ending_b",
      "title": "The Iron Horizon",
      "description": "Magic is extinguished, but the Great Engine provides energy for all.",
      "conditions": "Side with Kaelen in Branch 4; choose 'Stabilize the Engine' in Branch 5.",
      "character_fates": {
        "Kaelen": "Chief Engineer of Oros",
        "Elara": "Goes into exile",
        "Protagonist": "Becomes a mortal hero with fading scars"
      },
      "thematic_resolution": "Security and equality are achieved through the death of wonder.",
      "epilogue": "A world of machines and equality, devoid of the supernatural."
    },
    {
      "ending_id": "ending_c",
      "title": "The Resonance",
      "description": "A new form of 'Techno-Magic' is born.",
      "conditions": "High 'Trust' with both allies; choose 'The Synthesis' in Branch 5.",
      "character_fates": {
        "Elara and Kaelen": "Lead a new Council together",
        "Protagonist": "Remains the 'Living Bridge'"
      },
      "thematic_resolution": "Evolution through compromise. A difficult but hopeful middle ground.",
      "epilogue": "The Heart powers the world while retaining its magical essence."
    }
  ],
  "themes": [
    "Tradition vs. Progress",
    "Magic vs. Technology",
    "Elitism vs. Democracy",
    "Compromise and Evolution"
  ],
  "player_role": "The Shard-Binder (Former archivist and living vessel for magical shards)",
  "estimated_playtime": "5 hours"
}
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:101)
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:166)
	... 110 more
Caused by: com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot deserialize value of type `java.util.ArrayList<java.lang.String>` from Object value (token `JsonToken.START_OBJECT`)
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 111, column: 27] (through reference chain: com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$GameNarrative["branching_points"]->java.util.ArrayList[0]->com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$BranchingPoint["choices"]->java.util.ArrayList[0]->com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask$NarrativeChoice["consequences"])
	at com.fasterxml.jackson.databind.exc.MismatchedInputException.from(MismatchedInputException.java:59)
	at com.fasterxml.jackson.databind.DeserializationContext.reportInputMismatch(DeserializationContext.java:1794)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1568)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1515)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.handleNonArray(StringCollectionDeserializer.java:286)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:193)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:183)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:27)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer._deserializeFromArray(CollectionDeserializer.java:360)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:245)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:29)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer._deserializeFromArray(CollectionDeserializer.java:360)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:245)
	at com.fasterxml.jackson.databind.deser.std.CollectionDeserializer.deserialize(CollectionDeserializer.java:29)
	at com.fasterxml.jackson.databind.deser.SettableBeanProperty.deserialize(SettableBeanProperty.java:543)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeWithErrorWrapping(BeanDeserializer.java:587)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer._deserializeUsingPropertyBased(BeanDeserializer.java:440)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.deserializeFromObjectUsingNonDefault(BeanDeserializerBase.java:1499)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserializeFromObject(BeanDeserializer.java:340)
	at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:177)
	at com.fasterxml.jackson.databind.deser.DefaultDeserializationContext.readRootValue(DefaultDeserializationContext.java:342)
	at com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4971)
	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3887)
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:92)
	... 111 more

```
