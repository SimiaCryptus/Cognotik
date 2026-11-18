## Sub-Planning Task Initialization
## Error

```
java.lang.IllegalArgumentException: No cognitive mode specified for SubPlanningTask. Please set a cognitive mode in either the task type configuration or the orchestration configuration.
	at com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.run(SubPlanTask.kt:121)
	at com.simiacryptus.cognotik.apps.SingleTaskApp.executeTask(SingleTaskApp.kt:138)
	at com.simiacryptus.cognotik.apps.SingleTaskApp.startSession$lambda$0(SingleTaskApp.kt:98)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
	at com.google.common.util.concurrent.TrustedListenableFutureTask$TrustedFutureInterruptibleTask.runInterruptibly(TrustedListenableFutureTask.java:128)
	at com.google.common.util.concurrent.InterruptibleTask.run(InterruptibleTask.java:74)
	at com.google.common.util.concurrent.TrustedListenableFutureTask.run(TrustedListenableFutureTask.java:80)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	at com.simiacryptus.cognotik.platform.ThreadPoolManager$RecordingThreadFactory.newThread$lambda$0(ThreadPoolManager.kt:486)
	at java.base/java.lang.Thread.run(Thread.java:1583)

```