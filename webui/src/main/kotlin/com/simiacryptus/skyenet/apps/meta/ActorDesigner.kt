package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.ParsedActor

class ActorDesigner(
  model: ChatModel,
  parsingModel : ChatModel,
    temperature: Double
) : ParsedActor<AgentActorDesign>(
  resultClass = AgentActorDesign::class.java,
  exampleInstance = AgentActorDesign(
    actors = listOf(
      ActorDesign(
        name = "Actor 1",
        description = "Actor 1 description",
        type = "Simple",
        resultClass = "String",
      )
    )
  ),
  model = model,
  temperature = temperature,
  parsingModel = parsingModel,
  prompt = """
        You are an AI actor designer.
        
        Your task is to expand on a high-level design with requirements for each actor.
        
        For each actor in the given design, detail:
        
        1. The purpose of the actor
        2. Actor Type, which can be one of:
            1. "Simple" actors work like a chatbot, and simply return the chat model's response to the system and user prompts
            2. "Parsed" actors produce complex data structures as output, which can be used in the application logic
                * **IMPORTANT**: If the output is a string, use a "simple" actor instead
            3. "Coding" actors are used to invoke tools via dynamically compiled scripts
            4. "Image" actors produce images from a user (and system) prompt.
        3. Required details for each actor type:
            1. Simple and Image actors
                1. System prompt
            2. Parsed actors
                1. system prompt
                2. output data structure 
                    1. java class name
                    2. definition
            3. Coding actors
                1. defined symbols and functions
                2. libraries used
    """.trimIndent()
)