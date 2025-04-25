package com.simiacryptus.cognotik.diff

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DiffUtilTest {

  /*
    @Test
    fun testNoChanges() {
      val original = listOf("line1", "line2", "line3")
      val modified = listOf("line1", "line2", "line3")
      val diffResults = DiffUtil.generateDiff(original, modified)
      val formattedDiff = DiffUtil.formatDiff(diffResults)
      assertEquals("", formattedDiff, "There should be no diff for identical inputs.")
    }
  */

  @Test
  fun testMixedChanges() {
    val original = listOf("line1", "line2", "line4")
    val modified = listOf("line1", "line3", "line4")
    val diffResults = DiffUtil.generateDiff(original, modified)
    val formattedDiff = DiffUtil.formatDiff(diffResults)
    val expectedDiff = """
              line1
            - line2
            + line3
              line4
        """.trimIndent()
    Assertions.assertEquals(expectedDiff, formattedDiff, "The diff should correctly represent mixed changes.")
  }

  @Test
  fun testStartWithChange() {
    val original = listOf("line1", "line2")
    val modified = listOf("changed_line1", "line2")
    val diffResults = DiffUtil.generateDiff(original, modified)
    val formattedDiff = DiffUtil.formatDiff(diffResults)
    val expectedDiff = """
             - line1
             + changed_line1
               line2
         """.trimIndent()
    Assertions.assertEquals(
      expectedDiff,
      formattedDiff,
      "The diff should correctly represent changes at the start."
    )
  }

  @Test
  fun testEndWithChange() {
    val original = listOf("line1", "line2")
    val modified = listOf("line1", "changed_line2")
    val diffResults = DiffUtil.generateDiff(original, modified)
    val formattedDiff = DiffUtil.formatDiff(diffResults)
    val expectedDiff = """
               line1
             - line2
             + changed_line2
         """.trimIndent()
    Assertions.assertEquals(expectedDiff, formattedDiff, "The diff should correctly represent changes at the end.")
  }

  @Test
  fun testVerifyLLMPatch() {
    val originalCode = """
      <!DOCTYPE html>
      <html lang="en">
      <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Speed Word Search</title>
          <link rel="stylesheet" href="style.css">
      </head>
      <body>
          <div id="game-container">
              <h1>Speed Word Search</h1>
              <div id="score-board">
                  <p>Score: <span id="score">0</span></p>
                  <p>Time Left: <span id="time-left">60</span> seconds</p>
              </div>
              <div id="word-display">
                  <!-- Words will be dynamically added here -->
              </div>
              <input type="text" id="word-input" placeholder="Start typing...">
              <button id="start-game">Start Game</button>
          </div>
          <script src="game.js"></script>
      </body>
      </html>
    """.trimIndent()
    val llmPatch = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Speed Word Search</title>
            <link rel="stylesheet" href="style.css">
        </head>
        <body>
            <div id="game-container">
                <h1>Speed Word Search</h1>
        +       <div id="instructions">
        +           <p>Find as many words as possible in 60 seconds. Start typing to begin!</p>
        +       </div>
                <div id="score-board">
                    <p>Score: <span id="score">0</span></p>
                    <p>Time Left: <span id="time-left">60</span> seconds</p>
                </div>
                <div id="word-display">
                    <!-- Words will be dynamically added here -->
                </div>
                <input type="text" id="word-input" placeholder="Start typing...">
                <button id="start-game">Start Game</button>
            </div>
            <script src="game.js"></script>
        </body>
        </html>
    """.trimIndent()
    val reconstructed = ApxPatchUtil.patch(originalCode, llmPatch)

    val patchLines = DiffUtil.generateDiff(originalCode.lines(), reconstructed.lines())
//    println("\n\nPatched:\n\n")
//    patchLines.forEach { println(it) }

    println("\n\nEcho Patch:\n\n")
    DiffUtil.formatDiff(patchLines).lines().forEach { println(it) }
  }
}