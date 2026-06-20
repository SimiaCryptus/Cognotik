package com.simiacryptus.cognotik.crawl.processing

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer

@JsonDeserialize(using = ProcessingStrategyTypeDeserializer::class)
@JsonSerialize(using = ProcessingStrategyTypeSerializer::class)
open class ProcessingStrategyType(
   name: String,
   val strategyFactory: () -> PageProcessingStrategy
) : DynamicEnum<ProcessingStrategyType>(name) {

   fun createStrategy(): PageProcessingStrategy = strategyFactory()

   companion object {

     val DEFAULT = ProcessingStrategyType("DefaultSummarizer") { DefaultSummarizerStrategy.instance }
     val FACT_CHECK = ProcessingStrategyType("FactChecking") { FactCheckingStrategy() }

     init {
       register(DEFAULT)
       register(FACT_CHECK)
     }

     fun register(value: ProcessingStrategyType): ProcessingStrategyType {
       register(ProcessingStrategyType::class.java, value)
       return value
     }

     fun values(): List<ProcessingStrategyType> =
       values(ProcessingStrategyType::class.java)

     fun valueOf(name: String): ProcessingStrategyType =
       valueOf(ProcessingStrategyType::class.java, name)
   }
}
class ProcessingStrategyTypeSerializer : DynamicEnumSerializer<ProcessingStrategyType>(ProcessingStrategyType::class.java)

class ProcessingStrategyTypeDeserializer : DynamicEnumDeserializer<ProcessingStrategyType>(ProcessingStrategyType::class.java)