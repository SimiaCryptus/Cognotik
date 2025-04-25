package com.simiacryptus.cognotik.apps.meta

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.jopenai.models.ChatModel

class DetailDesigner(
  model: ChatModel,
  parsingModel: ChatModel,
  temperature: Double
) : ParsedActor<AgentFlowDesign>(
  resultClass = AgentFlowDesign::class.java,
  exampleInstance = AgentFlowDesign(
    name = "TextAnalyzer",
    description = "Analyze input text for sentiment and key topics",
    mainInput = DataInfo(
      type = "String",
      description = "raw text"
    ),
    logicFlow = LogicFlow(
      items = listOf(
        LogicFlowItem(
          name = "Preprocess text",
          description = "Preprocess text (remove noise, normalize)",
          actors = listOf(
            "TextPreprocessor"
          ),
          inputs = listOf(
            DataInfo(
              type = "String",
              description = "raw text"
            )
          ),
          output = DataInfo(
            type = "String",
            description = "preprocessed text"
          )
        ),
      )
    )
  ),
  model = model,
  temperature = temperature,
  parsingModel = parsingModel,
  prompt = """
        You are an expert detailed software designer specializing in AI agent systems.
        
        Your task is to expand on the high-level architecture and design a detailed "agent" system that uses GPT "actors" to model a creative process.
        The system should have a procedural overall structure, with creative steps implemented by GPT actors.
        
        Consider the following system interactions:
        1. File storage and retrieval: Design a shared session folder accessible by both the user and the application.
        2. Concurrent operations: Plan for individual actors and actions to run in parallel using Java threading.
        
        Design user interactions including:
        1. Rich message display: HTML and image rendering in the web interface.
        2. User input mechanisms: Text input fields and clickable links with callback handling.
        
        Incorporate these important design patterns:
        1. Iterative Thinking: Implement user feedback loops and step-by-step processing using sequences of specialized actors.
        2. Parse-and-Expand: Use an initial actor to generate a base data structure, then expand it using various (potentially recursive) actors.
        3. File Builder: Design the main web interface for monitoring and control, with primary outputs written to files and displayed as links.
        
        Your detailed design output should include:
        1. Actor Specifications: For each actor, provide:
            * Purpose and description
            * Input and output formats
            * Key responsibilities and operations
        2. Logic Flow: Detailed pseudocode for the overall system flow
        3. Data Structures: Specifications for data structures used to pass and handle information between actors
        4. User Interface: Mockup or description of key UI components and their functionality
        5. File Management: Strategy for file storage, retrieval, and organization
        6. Concurrency Plan: Outline of how parallel operations will be managed
        Example Actor Specification:
        Actor: TextAnalyzer
        Purpose: Analyze input text for sentiment and key topics
        Inputs: String (raw text)
        Outputs: 
            * Float (sentiment score from -1 to 1)
            * List<String> (key topics)
        Operations:
          1. Preprocess text (remove noise, normalize)
          2. Perform sentiment analysis
          3. Extract key topics using NLP techniques
        Ensure your design is comprehensive, clear, and ready for implementation.
    """.trimIndent()
)