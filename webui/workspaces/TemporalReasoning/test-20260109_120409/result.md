ERROR: Temporal reasoning analysis failed - ```text
java.lang.RuntimeException: Failed to parse response:   {
    "timeline_events": [
      {
        "timestamp": "2023-01-15",
        "event_type": "Milestone",
        "description": "Completion of Monolithic Architecture Audit and Modernization Roadmap",
        "significance": "High",
        "related_metrics": {
          "technical_debt_score": "85/100",
          "readiness_index": "0.4"
        }
      },
      {
        "timestamp": "2023-02-10",
        "event_type": "Deployment",
        "description": "Extraction of Authentication and Authorization into a standalone Microservice",
        "significance": "Medium",
        "related_metrics": {
          "decoupling_ratio": "15%",
          "latency_impact": "+5ms"
        }
      },
      {
        "timestamp": "2023-03-20",
        "event_type": "Change",
        "description": "Implementation of Centralized API Gateway (Kong) for traffic management",
        "significance": "High",
        "related_metrics": {
          "routing_efficiency": "+30%",
          "security_vulnerability_reduction": "20%"
        }
      },
      {
        "timestamp": "2023-05-12",
        "event_type": "Incident",
        "description": "Network Latency Spike: Inter-service communication overhead exceeds thresholds",
        "significance": "High",
        "related_metrics": {
          "downtime": "45 mins",
          "p99_latency": "1200ms"
        }
      },
      {
        "timestamp": "2023-06-05",
        "event_type": "Deployment",
        "description": "Service Mesh (Istio) rollout to manage mTLS and observability",
        "significance": "High",
        "related_metrics": {
          "observability_coverage": "90%",
          "mtls_adoption": "100%"
        }
      },
      {
        "timestamp": "2023-08-15",
        "event_type": "Change",
        "description": "Migration from synchronous REST to Event-Driven Architecture (Kafka) for core order processing",
        "significance": "Critical",
        "related_metrics": {
          "throughput_increase": "3x",
          "system_resiliency": "+40%"
        }
      },
      {
        "timestamp": "2023-10-22",
        "event_type": "Milestone",
        "description": "Integration of AI-driven Predictive Scaling and Inference Service",
        "significance": "Medium",
        "related_metrics": {
          "resource_utilization_efficiency": "+25%",
          "prediction_accuracy": "88%"
        }
      },
      {
        "timestamp": "2023-12-10",
        "event_type": "Milestone",
        "description": "Decommissioning of the primary Legacy Monolith database",
        "significance": "Critical",
        "related_metrics": {
          "maintenance_cost_reduction": "60%",
          "data_redundancy_score": "0.95"
        }
      }
    ],
    "patterns": [
      {
        "confidence": "High",
        "description": "Major architectural shifts (Gateway in Q1, Mesh in Q2, Event-driven in Q3) occur roughly every 90-100 days.",
        "frequency": "Quarterly",
        "pattern_type": "Quarterly Refactoring Cycle"
      },
      {
        "confidence": "Medium",
        "description": "Significant infrastructure deployments (Service Mesh) consistently follow major performance incidents (May Latency Spike).",
        "frequency": "Reactive",
        "pattern_type": "Incident-Driven Optimization"
      }
    ],
    "rate_of_change_analysis": "The system experienced an acceleration in change velocity during Q2 and Q3 as the foundational microservices were established. Acceleration period: 2023-05 to 2023-08. Stability period: 2023-11 to 2024-01. Trend quantification: Deployment frequency increased from 1 major change/month in Q1 to 3 major changes/month in Q3.",
    "transition_points": [
      {
        "timestamp": "2023-03-20",
        "transition_type": "Structural",
        "description": "Transition from direct service access to Gateway-mediated access.",
        "trigger": "Need for unified security and rate limiting."
      },
      {
        "timestamp": "2023-08-15",
        "transition_type": "Paradigm Shift",
        "description": "Transition from Request-Response to Event-Driven communication.",
        "trigger": "Scaling bottlenecks in synchronous processing."
      }
    ],
    "future_predictions": [
      "Adoption of WebAssembly (Wasm) for edge-side compute to reduce latency. Probability: 75%, Horizon: 6 months, Risk: Immature tooling and developer learning curve.",
      "Shift toward 'FinOps' automated cost-optimization modules within the Service Mesh. Probability: 85%, Horizon: 4 months, Opportunity: Significant reduction in cloud spend as microservice footprint grows."
    ]
  }
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:180)
	at com.simiacryptus.cognotik.agents.ParsedAgent.getParser$lambda$0(ParsedAgent.kt:138)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl._obj_delegate$lambda$0(ParsedAgent.kt:92)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.get_obj(ParsedAgent.kt:83)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.getObj(ParsedAgent.kt:95)
	at com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask.run(TemporalReasoningTask.kt:277)
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
	at com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTaskTest.test(TemporalReasoningTaskTest.kt:40)
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
  "timeline_events": [
    {
      "timestamp": "2023-01-15",
      "event_type": "Milestone",
      "description": "Completion of Monolithic Architecture Audit and Modernization Roadmap",
      "significance": "High",
      "related_metrics": {
        "technical_debt_score": "85/100",
        "readiness_index": "0.4"
      }
    },
    {
      "timestamp": "2023-02-10",
      "event_type": "Deployment",
      "description": "Extraction of Authentication and Authorization into a standalone Microservice",
      "significance": "Medium",
      "related_metrics": {
        "decoupling_ratio": "15%",
        "latency_impact": "+5ms"
      }
    },
    {
      "timestamp": "2023-03-20",
      "event_type": "Change",
      "description": "Implementation of Centralized API Gateway (Kong) for traffic management",
      "significance": "High",
      "related_metrics": {
        "routing_efficiency": "+30%",
        "security_vulnerability_reduction": "20%"
      }
    },
    {
      "timestamp": "2023-05-12",
      "event_type": "Incident",
      "description": "Network Latency Spike: Inter-service communication overhead exceeds thresholds",
      "significance": "High",
      "related_metrics": {
        "downtime": "45 mins",
        "p99_latency": "1200ms"
      }
    },
    {
      "timestamp": "2023-06-05",
      "event_type": "Deployment",
      "description": "Service Mesh (Istio) rollout to manage mTLS and observability",
      "significance": "High",
      "related_metrics": {
        "observability_coverage": "90%",
        "mtls_adoption": "100%"
      }
    },
    {
      "timestamp": "2023-08-15",
      "event_type": "Change",
      "description": "Migration from synchronous REST to Event-Driven Architecture (Kafka) for core order processing",
      "significance": "Critical",
      "related_metrics": {
        "throughput_increase": "3x",
        "system_resiliency": "+40%"
      }
    },
    {
      "timestamp": "2023-10-22",
      "event_type": "Milestone",
      "description": "Integration of AI-driven Predictive Scaling and Inference Service",
      "significance": "Medium",
      "related_metrics": {
        "resource_utilization_efficiency": "+25%",
        "prediction_accuracy": "88%"
      }
    },
    {
      "timestamp": "2023-12-10",
      "event_type": "Milestone",
      "description": "Decommissioning of the primary Legacy Monolith database",
      "significance": "Critical",
      "related_metrics": {
        "maintenance_cost_reduction": "60%",
        "data_redundancy_score": "0.95"
      }
    }
  ],
  "patterns": [
    {
      "confidence": "High",
      "description": "Major architectural shifts (Gateway in Q1, Mesh in Q2, Event-driven in Q3) occur roughly every 90-100 days.",
      "frequency": "Quarterly",
      "pattern_type": "Quarterly Refactoring Cycle"
    },
    {
      "confidence": "Medium",
      "description": "Significant infrastructure deployments (Service Mesh) consistently follow major performance incidents (May Latency Spike).",
      "frequency": "Reactive",
      "pattern_type": "Incident-Driven Optimization"
    }
  ],
  "rate_of_change_analysis": "The system experienced an acceleration in change velocity during Q2 and Q3 as the foundational microservices were established. Acceleration period: 2023-05 to 2023-08. Stability period: 2023-11 to 2024-01. Trend quantification: Deployment frequency increased from 1 major change/month in Q1 to 3 major changes/month in Q3.",
  "transition_points": [
    {
      "timestamp": "2023-03-20",
      "transition_type": "Structural",
      "description": "Transition from direct service access to Gateway-mediated access.",
      "trigger": "Need for unified security and rate limiting."
    },
    {
      "timestamp": "2023-08-15",
      "transition_type": "Paradigm Shift",
      "description": "Transition from Request-Response to Event-Driven communication.",
      "trigger": "Scaling bottlenecks in synchronous processing."
    }
  ],
  "future_predictions": [
    "Adoption of WebAssembly (Wasm) for edge-side compute to reduce latency. Probability: 75%, Horizon: 6 months, Risk: Immature tooling and developer learning curve.",
    "Shift toward 'FinOps' automated cost-optimization modules within the Service Mesh. Probability: 85%, Horizon: 4 months, Opportunity: Significant reduction in cloud spend as microservice footprint grows."
  ]
}
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:101)
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:166)
	... 110 more
Caused by: com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot deserialize value of type `java.lang.String` from Object value (token `JsonToken.START_OBJECT`)
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 100, column: 5] (through reference chain: com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask$TimelineAnalysis["transition_points"]->java.util.ArrayList[0])
	at com.fasterxml.jackson.databind.exc.MismatchedInputException.from(MismatchedInputException.java:59)
	at com.fasterxml.jackson.databind.DeserializationContext.reportInputMismatch(DeserializationContext.java:1794)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1568)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1473)
	at com.fasterxml.jackson.databind.DeserializationContext.extractScalarFromObject(DeserializationContext.java:971)
	at com.fasterxml.jackson.databind.deser.std.StdDeserializer._parseString(StdDeserializer.java:1444)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:217)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:183)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:27)
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
    "future_predictions": [
      "Adoption of WebAssembly (Wasm) for edge-side compute to reduce latency. Probability: 75%, Horizon: 6 months, Risk: Immature tooling and developer learning curve.",
      "Shift toward 'FinOps' automated cost-optimization modules within the Service Mesh. Probability: 85%, Horizon: 4 months, Opportunity: Significant reduction in cloud spend as microservice footprint grows."
    ],
    "patterns": [
      {
        "confidence": "High",
        "description": "Major architectural shifts (Gateway in Q1, Mesh in Q2, Event-driven in Q3) occur roughly every 90-100 days.",
        "frequency": "Quarterly",
        "pattern_type": "Quarterly Refactoring Cycle"
      },
      {
        "confidence": "Medium",
        "description": "Significant infrastructure deployments (Service Mesh) consistently follow major performance incidents (May Latency Spike).",
        "frequency": "Reactive",
        "pattern_type": "Incident-Driven Optimization"
      }
    ],
    "rate_of_change_analysis": "The system experienced an acceleration in change velocity during Q2 and Q3 as the foundational microservices were established. Acceleration period: 2023-05 to 2023-08. Stability period: 2023-11 to 2024-01. Trend quantification: Deployment frequency increased from 1 major change/month in Q1 to 3 major changes/month in Q3.",
    "timeline_events": [
      {
        "description": "Completion of Monolithic Architecture Audit and Modernization Roadmap",
        "event_type": "Milestone",
        "related_metrics": {
          "technical_debt_score": "85/100",
          "readiness_index": "0.4"
        },
        "significance": "High",
        "timestamp": "2023-01-15"
      },
      {
        "description": "Extraction of Authentication and Authorization into a standalone Microservice",
        "event_type": "Deployment",
        "related_metrics": {
          "decoupling_ratio": "15%",
          "latency_impact": "+5ms"
        },
        "significance": "Medium",
        "timestamp": "2023-02-10"
      },
      {
        "description": "Implementation of Centralized API Gateway (Kong) for traffic management",
        "event_type": "Change",
        "related_metrics": {
          "routing_efficiency": "+30%",
          "security_vulnerability_reduction": "20%"
        },
        "significance": "High",
        "timestamp": "2023-03-20"
      },
      {
        "description": "Network Latency Spike: Inter-service communication overhead exceeds thresholds",
        "event_type": "Incident",
        "related_metrics": {
          "downtime": "45 mins",
          "p99_latency": "1200ms"
        },
        "significance": "High",
        "timestamp": "2023-05-12"
      },
      {
        "description": "Service Mesh (Istio) rollout to manage mTLS and observability",
        "event_type": "Deployment",
        "related_metrics": {
          "observability_coverage": "90%",
          "mtls_adoption": "100%"
        },
        "significance": "High",
        "timestamp": "2023-06-05"
      },
      {
        "description": "Migration from synchronous REST to Event-Driven Architecture (Kafka) for core order processing",
        "event_type": "Change",
        "related_metrics": {
          "throughput_increase": "3x",
          "system_resiliency": "+40%"
        },
        "significance": "Critical",
        "timestamp": "2023-08-15"
      },
      {
        "description": "Integration of AI-driven Predictive Scaling and Inference Service",
        "event_type": "Milestone",
        "related_metrics": {
          "resource_utilization_efficiency": "+25%",
          "prediction_accuracy": "88%"
        },
        "significance": "Medium",
        "timestamp": "2023-10-22"
      },
      {
        "description": "Decommissioning of the primary Legacy Monolith database",
        "event_type": "Milestone",
        "related_metrics": {
          "maintenance_cost_reduction": "60%",
          "data_redundancy_score": "0.95"
        },
        "significance": "Critical",
        "timestamp": "2023-12-10"
      }
    ],
    "transition_points": [
      {
        "timestamp": "2023-03-20",
        "transition_type": "Structural",
        "description": "Transition from direct service access to Gateway-mediated access.",
        "trigger": "Need for unified security and rate limiting."
      },
      {
        "timestamp": "2023-08-15",
        "transition_type": "Paradigm Shift",
        "description": "Transition from Request-Response to Event-Driven communication.",
        "trigger": "Scaling bottlenecks in synchronous processing."
      }
    ]
  }
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:180)
	at com.simiacryptus.cognotik.agents.ParsedAgent.getParser$lambda$0(ParsedAgent.kt:138)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl._obj_delegate$lambda$0(ParsedAgent.kt:92)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.get_obj(ParsedAgent.kt:83)
	at com.simiacryptus.cognotik.agents.ParsedAgent$ParsedResponseImpl.getObj(ParsedAgent.kt:95)
	at com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask.run(TemporalReasoningTask.kt:277)
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
	at com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTaskTest.test(TemporalReasoningTaskTest.kt:40)
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
  "future_predictions": [
    "Adoption of WebAssembly (Wasm) for edge-side compute to reduce latency. Probability: 75%, Horizon: 6 months, Risk: Immature tooling and developer learning curve.",
    "Shift toward 'FinOps' automated cost-optimization modules within the Service Mesh. Probability: 85%, Horizon: 4 months, Opportunity: Significant reduction in cloud spend as microservice footprint grows."
  ],
  "patterns": [
    {
      "confidence": "High",
      "description": "Major architectural shifts (Gateway in Q1, Mesh in Q2, Event-driven in Q3) occur roughly every 90-100 days.",
      "frequency": "Quarterly",
      "pattern_type": "Quarterly Refactoring Cycle"
    },
    {
      "confidence": "Medium",
      "description": "Significant infrastructure deployments (Service Mesh) consistently follow major performance incidents (May Latency Spike).",
      "frequency": "Reactive",
      "pattern_type": "Incident-Driven Optimization"
    }
  ],
  "rate_of_change_analysis": "The system experienced an acceleration in change velocity during Q2 and Q3 as the foundational microservices were established. Acceleration period: 2023-05 to 2023-08. Stability period: 2023-11 to 2024-01. Trend quantification: Deployment frequency increased from 1 major change/month in Q1 to 3 major changes/month in Q3.",
  "timeline_events": [
    {
      "description": "Completion of Monolithic Architecture Audit and Modernization Roadmap",
      "event_type": "Milestone",
      "related_metrics": {
        "technical_debt_score": "85/100",
        "readiness_index": "0.4"
      },
      "significance": "High",
      "timestamp": "2023-01-15"
    },
    {
      "description": "Extraction of Authentication and Authorization into a standalone Microservice",
      "event_type": "Deployment",
      "related_metrics": {
        "decoupling_ratio": "15%",
        "latency_impact": "+5ms"
      },
      "significance": "Medium",
      "timestamp": "2023-02-10"
    },
    {
      "description": "Implementation of Centralized API Gateway (Kong) for traffic management",
      "event_type": "Change",
      "related_metrics": {
        "routing_efficiency": "+30%",
        "security_vulnerability_reduction": "20%"
      },
      "significance": "High",
      "timestamp": "2023-03-20"
    },
    {
      "description": "Network Latency Spike: Inter-service communication overhead exceeds thresholds",
      "event_type": "Incident",
      "related_metrics": {
        "downtime": "45 mins",
        "p99_latency": "1200ms"
      },
      "significance": "High",
      "timestamp": "2023-05-12"
    },
    {
      "description": "Service Mesh (Istio) rollout to manage mTLS and observability",
      "event_type": "Deployment",
      "related_metrics": {
        "observability_coverage": "90%",
        "mtls_adoption": "100%"
      },
      "significance": "High",
      "timestamp": "2023-06-05"
    },
    {
      "description": "Migration from synchronous REST to Event-Driven Architecture (Kafka) for core order processing",
      "event_type": "Change",
      "related_metrics": {
        "throughput_increase": "3x",
        "system_resiliency": "+40%"
      },
      "significance": "Critical",
      "timestamp": "2023-08-15"
    },
    {
      "description": "Integration of AI-driven Predictive Scaling and Inference Service",
      "event_type": "Milestone",
      "related_metrics": {
        "resource_utilization_efficiency": "+25%",
        "prediction_accuracy": "88%"
      },
      "significance": "Medium",
      "timestamp": "2023-10-22"
    },
    {
      "description": "Decommissioning of the primary Legacy Monolith database",
      "event_type": "Milestone",
      "related_metrics": {
        "maintenance_cost_reduction": "60%",
        "data_redundancy_score": "0.95"
      },
      "significance": "Critical",
      "timestamp": "2023-12-10"
    }
  ],
  "transition_points": [
    {
      "timestamp": "2023-03-20",
      "transition_type": "Structural",
      "description": "Transition from direct service access to Gateway-mediated access.",
      "trigger": "Need for unified security and rate limiting."
    },
    {
      "timestamp": "2023-08-15",
      "transition_type": "Paradigm Shift",
      "description": "Transition from Request-Response to Event-Driven communication.",
      "trigger": "Scaling bottlenecks in synchronous processing."
    }
  ]
}
	at com.simiacryptus.cognotik.util.JsonUtil.fromJson(JsonUtil.kt:101)
	at com.simiacryptus.cognotik.agents.ParsedAgent.parse(ParsedAgent.kt:166)
	... 110 more
Caused by: com.fasterxml.jackson.databind.exc.MismatchedInputException: Cannot deserialize value of type `java.lang.String` from Object value (token `JsonToken.START_OBJECT`)
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 104, column: 5] (through reference chain: com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask$TimelineAnalysis["transition_points"]->java.util.ArrayList[0])
	at com.fasterxml.jackson.databind.exc.MismatchedInputException.from(MismatchedInputException.java:59)
	at com.fasterxml.jackson.databind.DeserializationContext.reportInputMismatch(DeserializationContext.java:1794)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1568)
	at com.fasterxml.jackson.databind.DeserializationContext.handleUnexpectedToken(DeserializationContext.java:1473)
	at com.fasterxml.jackson.databind.DeserializationContext.extractScalarFromObject(DeserializationContext.java:971)
	at com.fasterxml.jackson.databind.deser.std.StdDeserializer._parseString(StdDeserializer.java:1444)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:217)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:183)
	at com.fasterxml.jackson.databind.deser.std.StringCollectionDeserializer.deserialize(StringCollectionDeserializer.java:27)
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