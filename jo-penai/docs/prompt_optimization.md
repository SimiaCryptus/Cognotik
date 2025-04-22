# PromptOptimization Library README

## Overview

The `PromptOptimization` library is a utility designed to enhance your interactions with the OpenAI API. This library
aims to optimize system prompts through genetic algorithms, facilitating more efficient API calls and yielding
higher-quality responses. By providing a suite of functionalities to run multiple generations of system prompts and
evaluate their performance, you can be sure to get the most value out of your OpenAI API tokens.

### Key Features

- Genetic Algorithm-based optimization for system prompts.
- Flexible `TestCase` and `Turn` design for evaluating prompts.
- Embedding and pattern matching for scoring responses.
- Utilizes the OpenAI API for chat and other machine learning tasks.

## Quick Start

Here's a basic example to give you a taste of how this library works:

```kotlin
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.opt.Expectation
import com.simiacryptus.jopenai.opt.PromptOptimization
import com.simiacryptus.jopenai.opt.PromptOptimization.TestCase
import com.simiacryptus.jopenai.opt.PromptOptimization.Turn

val api = OpenAIClient("your_api_key_here")
val optimizer = PromptOptimization(api)

val testCases = listOf(
    TestCase(
        turns = listOf(
            Turn(
                userMessage = "Who won the Nobel prize in Physics in 2021?",
                expectations = listOf(
                    Expectation.VectorMatch("Syukuro Manabe and Klaus Hasselmann"),
                    Expectation.ContainsMatch("Syukuro".toRegex()),
                )
            )
        )
    )
)

val initialPrompts = listOf("I am a knowledgeable assistant.", "Your query will be answered accurately.")

val optimizedPrompts = optimizer.runGeneticGenerations(initialPrompts, testCases)

println("Optimized prompts: $optimizedPrompts")
```

## Classes and Functions

### PromptOptimization Class

#### `runGeneticGenerations`

Runs genetic algorithm generations to find the optimized system prompts.

- `systemPrompts`: List of initial system prompts.
- `testCases`: List of `TestCase` to evaluate prompts.
- `selectionSize`: Number of top prompts to take to the next generation.
- `generations`: Number of generations to run.

Returns a list of optimized system prompts.

#### `regenerate`

Regenerates a population of prompts for the next generation.

- `progenitors`: List of parent prompts.
- `desiredCount`: Desired size of the next generation.

#### `evaluate`

Evaluates a single system prompt against a test case and returns a score.

#### `run`

Runs a full conversation with the given system prompt and test case, returning chat responses and scores.

### TestCase and Turn

Defines the structure of the conversation for evaluation.

- `TestCase`: Contains a list of turns and the number of retries for each turn.
- `Turn`: Contains a user message and expectations.

### Expectation Classes

- `VectorMatch`: Matches based on semantic similarity.
- `ContainsMatch`: Matches based on regex patterns.

## Contributing

Feel free to open issues or submit pull requests as you see fit.
