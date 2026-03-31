package com.simiacryptus.cognotik.util.crawl.processing

import com.simiacryptus.cognotik.util.DynamicEnum

open class ProcessingStrategyType(
   name: String,
   val strategyFactory: () -> PageProcessingStrategy
) : DynamicEnum<ProcessingStrategyType>(name) {

   fun createStrategy(): PageProcessingStrategy = strategyFactory()

   companion object {

     init {
       register(ProcessingStrategyType("DefaultSummarizer") { DefaultSummarizerStrategy.instance })
       register(ProcessingStrategyType("FactChecking") { FactCheckingStrategy() })
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