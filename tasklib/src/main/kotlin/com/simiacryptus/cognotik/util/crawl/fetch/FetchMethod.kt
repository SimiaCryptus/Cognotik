package com.simiacryptus.cognotik.util.crawl.fetch

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.LoggerFactory
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import java.io.File
import java.util.concurrent.ExecutorService

interface FetchStrategy : EnabledStrategy {
  fun fetch(
    url: String,
    webSearchDir: File,
    index: Int,
    pool: ExecutorService,
    orchestrationConfig: OrchestrationConfig
  ): String
}

object FetchConfig {
  var isSeleniumEnabled: Boolean = false
}

interface FetchMethodFactory {
  fun createStrategy(task: CrawlerAgentTask): FetchStrategy
}

@JsonDeserialize(using = FetchMethodDeserializer::class)
@JsonSerialize(using = FetchMethodSerializer::class)
class FetchMethod(
   name: String,
   private val strategyFactory: (CrawlerAgentTask) -> FetchStrategy
) : DynamicEnum<FetchMethod>(name), FetchMethodFactory {

   override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = strategyFactory(task)

   companion object {
     val log = LoggerFactory.getLogger(FetchMethod::class.java)

     val HttpClient = register(FetchMethod("HttpClient") { task -> BasicHttpClientStrategy(task) })

     fun register(fetchMethod: FetchMethod): FetchMethod {
       DynamicEnum.register(FetchMethod::class.java, fetchMethod)
       return fetchMethod
     }

     fun values(): List<FetchMethod> = DynamicEnum.values(FetchMethod::class.java)

     fun valueOf(name: String): FetchMethod = DynamicEnum.valueOf(FetchMethod::class.java, name)
   }
}

class FetchMethodSerializer : DynamicEnumSerializer<FetchMethod>(FetchMethod::class.java)

class FetchMethodDeserializer : DynamicEnumDeserializer<FetchMethod>(FetchMethod::class.java)