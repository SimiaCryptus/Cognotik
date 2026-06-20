package com.simiacryptus.cognotik.crawl.fetch

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.crawl.fetch.FetchMethod.Companion.HttpClient
import org.slf4j.LoggerFactory.getLogger
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
     val log = getLogger(FetchMethod::class.java)

     val HttpClient = register(FetchMethod("HttpClient") { task -> BasicHttpClientStrategy(task) })

     /**
      * Selenium-based fetch method.  Only active when [FetchConfig.isSeleniumEnabled] is `true`
      * **and** the Selenium runtime classes are present on the classpath.
      *
      * The strategy itself guards against missing classes via [SeleniumFetchStrategy.isEnabled],
      * so callers can safely reference this constant even when Selenium is not on the classpath –
      * the strategy will simply report itself as disabled and the crawl pipeline will fall back
      * to [HttpClient].
      */
     val Selenium = register(FetchMethod("Selenium") { task ->
       try {
         SeleniumFetchStrategy(task)
       } catch (e: NoClassDefFoundError) {
         log.warn(
           "Selenium classes not found on classpath – falling back to BasicHttpClientStrategy. " +
               "Add 'org.seleniumhq.selenium:selenium-java' to your runtime dependencies to enable Selenium fetching."
         )
         BasicHttpClientStrategy(task)
       }
     })


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