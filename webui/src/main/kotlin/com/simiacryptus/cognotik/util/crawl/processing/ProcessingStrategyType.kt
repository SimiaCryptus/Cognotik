package com.simiacryptus.cognotik.util.crawl.processing

enum class ProcessingStrategyType {

  DefaultSummarizer {
    override fun createStrategy(): PageProcessingStrategy = DefaultSummarizerStrategy()
  },
  FactChecking {
    override fun createStrategy(): PageProcessingStrategy = FactCheckingStrategy()
  },
  JobMatching {
    override fun createStrategy(): PageProcessingStrategy = JobMatchingStrategy()
  },
  SchemaExtraction {
    override fun createStrategy(): PageProcessingStrategy = SchemaExtractionStrategy()
  },
  DataTableAccumulation {;
    override fun createStrategy(): PageProcessingStrategy = DataTableAccumulationStrategy()
  };

  abstract fun createStrategy(): PageProcessingStrategy
}