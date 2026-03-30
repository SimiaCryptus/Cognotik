package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.toJson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NarrativeGenerationTaskPrompts {

  fun highLevelPrompt(
    subject: String,
    analysisResult: StringBuilder,
    genConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData,
    totalScenes: Int,
    wordsPerScene: Int
  ): String = buildString {
    appendLine("You are a master story architect. Based on the narrative analysis, create a high-level narrative structure.")
    appendLine()
    appendLine("Subject: $subject")
    appendLine()
    appendLine("Narrative Analysis:")
    appendLine(analysisResult.toString().truncateForDisplay(8000))
    appendLine()
    appendLine("Narrative Elements:")
    appendLine(genConfig.narrative_elements?.entries?.joinToString("\n") { (key, value) -> "- $key: $value" }
      ?: "")
    appendLine()
    appendLine("- ${genConfig.number_of_acts} acts")
    appendLine("- Approximately ${genConfig.scenes_per_act} scenes per act (total ~$totalScenes scenes)")
    appendLine("- Target: ${genConfig.target_word_count} total words (~$wordsPerScene words per scene)")
    appendLine()
    appendLine("For each scene, specify:")
    appendLine("- Scene number and title")
    appendLine("- Purpose (what this scene accomplishes)")
    appendLine("- Key events (what happens)")
    appendLine("- Emotional arc (how characters feel/change)")
    appendLine("- Setting (choose from available settings or describe a new one)")
    appendLine("- Characters present (from the character list)")
    appendLine("- Estimated word count (~$wordsPerScene words)")
    appendLine()
    appendLine("Ensure the outline:")
    appendLine("- Has clear cause-and-effect between scenes")
    appendLine("- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style")
    appendLine()
    appendLine("Create a high-level outline with:")
    appendLine("1. **Characters**: Define all major characters with:")
    appendLine("   - Name")
    appendLine("   - Detailed description (appearance, personality, background)")
    appendLine("   - Role in the story (protagonist, antagonist, supporting, etc.)")
    appendLine("   - Key traits and motivations")
    appendLine("2. **Settings**: Define all major locations/settings with:")
    appendLine("   - Name")
    appendLine("   - Detailed description (visual details, atmosphere)")
    appendLine("   - Atmosphere/mood")
    appendLine("   - Significance to the story")
    appendLine("3. **Act Structure**: Create ${genConfig.number_of_acts} acts with:")
    appendLine("   - Act number and title")
    appendLine("   - Purpose (what this act accomplishes in the story)")
    appendLine("   - Key developments (major plot points and character changes)")
    appendLine("   - Estimated number of scenes (approximately ${genConfig.scenes_per_act} per act)")
    appendLine("Target: ${genConfig.target_word_count} total words")
    appendLine("Style: ${genConfig.writing_style}")
    appendLine("Tone: ${genConfig.tone}")
    appendLine("POV: ${genConfig.point_of_view}")
    appendLine("Ensure the structure:")
    appendLine("- Has well-defined, memorable characters")
    appendLine("- Uses vivid, atmospheric settings")
    appendLine("- Follows classic story structure (setup, rising action, climax, falling action, resolution)")
    appendLine("- Builds tension and stakes progressively")
    appendLine("- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style")
  }

  fun imageHtml(
    actNumber: Int,
    sceneNumber: Int,
    sceneTitle: String,
    setting: String,
    result: ImageAndText,
    link: String
  ): String = buildString {
    appendLine("<div class='scene-image'>")
    appendLine("  <h4>Act $actNumber, Scene $sceneNumber: $sceneTitle</h4>")
    appendLine("  <p><strong>Setting:</strong> $setting</p>")
    appendLine("  <p><strong>Image Prompt:</strong> ${result.text}</p>")
    appendLine("  <a href='$link' target='_blank'>")
    appendLine("    <img src='$link' alt='Act $actNumber Scene $sceneNumber' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />")
    appendLine("  </a>")
    appendLine("</div>")
  }

  fun imageHtml(
    title: String,
    premise: String,
    result: ImageAndText,
    link: String
  ): String = buildString {
    appendLine("<div class='cover-image'>")
    appendLine("  <h3>$title</h3>")
    appendLine("  <p><em>$premise</em></p>")
    appendLine("  <p><strong>Image Prompt:</strong> ${result.text}</p>")
    appendLine("  <a href='$link' target='_blank'>")
    appendLine("    <img src='$link' alt='Cover' style='max-width: 600px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);' />")
    appendLine("  </a>")
    appendLine("</div>")
  }

  fun imageHtml(
    settingProfile: NarrativeGenerationTask.SettingProfile,
    result: ImageAndText,
    link: String
  ): String = buildString {
    appendLine("<div class='setting-reference'>")
    appendLine("  <h4>${settingProfile.setting_id}</h4>")
    appendLine("  <p><strong>Description:</strong> ${settingProfile.description}</p>")
    appendLine("  <p><strong>Atmosphere:</strong> ${settingProfile.atmosphere}</p>")
    appendLine("  <p><strong>Image Prompt:</strong> ${result.text}</p>")
    appendLine("  <a href='$link' target='_blank'>")
    appendLine("    <img src='$link' alt='${settingProfile.setting_id}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />")
    appendLine("  </a>")
    appendLine("</div>")
  }

  fun taskPrompt(
    generatedScenes: MutableList<NarrativeGenerationTask.GeneratedScene>,
    cumulativeWordCount: Int,
    avgWordsPerScene: Int,
    genConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData,
    totalTime: Long
  ): String = buildString {
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("## ✅ Generation Complete")
    appendLine()
    appendLine("**Statistics:**")
    appendLine("- Total Scenes: ${generatedScenes.size}")
    appendLine("- Total Word Count: $cumulativeWordCount")
    appendLine("- Average Words/Scene: $avgWordsPerScene")
    appendLine("- Target Word Count: ${genConfig.target_word_count}")
    appendLine("- Completion: ${(cumulativeWordCount.toFloat() / genConfig.target_word_count * 100).toInt()}%")
    appendLine("- Total Time: ${totalTime / 1000.0}s")
    appendLine()
    appendLine(
      "**Completed:** ${
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      }"
    )
  }

  fun sceneExpansionPrompt(
    highLevelOutline: NarrativeGenerationTask.HighLevelOutline,
    actSummary: NarrativeGenerationTask.ActSummary,
    detailedActs: MutableList<NarrativeGenerationTask.ActOutline>
  ): String = buildString {
    appendLine("You are a master story architect. Expand this act into detailed scenes.")
    appendLine()
    appendLine("**High-Level Narrative Context:**")
    appendLine(highLevelOutline.toJson().indent("  "))
    appendLine()
    appendLine("**Act:**")
    appendLine(actSummary.toJson().indent("  "))
    appendLine()
    appendLine("**Previous Acts Context:**")
    appendLine(
      detailedActs.joinToString("\n") { act -> "Act ${act.act_number}: ${act.title} - ${act.scenes?.size ?: 0} scenes" }
        .indent("  ")
    )
    appendLine()
    appendLine("Create approximately ${actSummary.estimated_scenes} scenes for this act. For each scene specify:")
    appendLine("- Fulfills the act's purpose and key developments")
    appendLine("- Appropriate setting_id from defined settings")
    appendLine("- Characters present from defined characters")
  }

  fun outlineContent(outline: NarrativeGenerationTask.NarrativeOutline): String = buildString {
    appendLine("## ${outline.title}")
    appendLine()
    appendLine("**Premise:** ${outline.premise}")
    appendLine()
    appendLine("**Estimated Word Count:** ${outline.estimated_word_count}")
    appendLine()
    appendLine("**Total Scenes:** ${outline.acts.sumOf { it.scenes?.size ?: 0 }}")
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("### Detailed Scene Breakdown")
    appendLine()
    outline.acts.forEach { act ->
      appendLine("### Act ${act.act_number}: ${act.title}")
      appendLine()
      appendLine("**Purpose:** ${act.purpose}")
      appendLine()
      act.scenes?.forEach { scene ->
        appendLine("#### Scene ${scene.scene_number}: ${scene.title}")
        appendLine()
        appendLine("- **Setting:** ${scene.setting_id}")
        appendLine("- **Characters:** ${scene.characters.joinToString(", ")}")
        appendLine("- **Purpose:** ${scene.purpose}")
        appendLine("- **Emotional Arc:** ${scene.emotional_arc}")
        appendLine("- **Est. Words:** ${scene.estimated_word_count}")
        appendLine()
        appendLine("**Key Events:**")
        appendLine(scene.key_events?.toJson()?.indent("  ") ?: "None")
        appendLine()
      }
      appendLine("---")
      appendLine()
    }
    appendLine("**Status:** ✅ Complete")
  }

  fun highLevelContent(highLevelOutline: NarrativeGenerationTask.HighLevelOutline): String = buildString {
    appendLine("## ${highLevelOutline.title}")
    appendLine()
    appendLine("**Premise:** ${highLevelOutline.premise}")
    appendLine()
    appendLine("**Estimated Word Count:** ${highLevelOutline.estimated_word_count}")
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("### Characters")
    appendLine()
    highLevelOutline.characters.forEach { char ->
      appendLine("#### ${char.name}")
      appendLine()
      appendLine("**Role:** ${char.role}")
      appendLine()
      appendLine("**Description:** ${char.description}")
      appendLine()
      appendLine("**Traits:** ${char.traits.joinToString(", ")}")
      appendLine()
    }
    appendLine("---")
    appendLine()
    appendLine("### Settings")
    appendLine()
    highLevelOutline.settings.forEach { setting ->
      appendLine("#### ${setting.setting_id}")
      appendLine()
      appendLine("**Description:** ${setting.description}")
      appendLine()
      appendLine("**Atmosphere:** ${setting.atmosphere}")
      appendLine()
      appendLine("**Significance:** ${setting.significance}")
      appendLine()
    }
    appendLine("---")
    appendLine()
    appendLine("### Act Structure")
    appendLine()
    highLevelOutline.acts.forEach { act ->
      appendLine("#### Act ${act.act_number}: ${act.title}")
      appendLine()
      appendLine("**Purpose:** ${act.purpose}")
      appendLine()
      appendLine("**Estimated Scenes:** ${act.estimated_scenes}")
      appendLine()
      appendLine("**Key Developments:**")
      act.key_developments.forEach { dev ->
        appendLine("- $dev")
      }
      appendLine()
    }
    appendLine("---")
    appendLine()
    appendLine("**Status:** ✅ Pass 1 Complete")
  }

  fun scenePromptText(
    genConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData,
    sceneOutline: NarrativeGenerationTask.SceneOutline,
    outline: NarrativeGenerationTask.NarrativeOutline,
    recentContext: String
  ): String = buildString {
    appendLine("You are a skilled ${genConfig.writing_style} writer. Write Scene ${sceneOutline.scene_number} of the narrative.")
    appendLine("This is Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}.")
    appendLine()
    appendLine("Overall Story: ${outline.title}")
    appendLine("Premise: ${outline.premise}")
    appendLine()
    appendLine("Scene Outline:")
    appendLine(sceneOutline.toJson().indent("  "))
    appendLine()
    appendLine(recentContext)
    appendLine()
    appendLine("Writing Guidelines:")
    appendLine("- Point of View: ${genConfig.point_of_view}")
    appendLine("- Tone: ${genConfig.tone}")
    appendLine("- Style: ${genConfig.writing_style}")
    if (genConfig.detailed_descriptions) {
      appendLine("- Include vivid, sensory descriptions of setting and action")
    } else {
      appendLine("- Keep descriptions concise")
    }
    if (genConfig.include_dialogue) {
      appendLine("- Include natural, character-appropriate dialogue")
    } else {
      appendLine("- Minimize dialogue, focus on narration")
    }
    if (genConfig.show_internal_thoughts) {
      appendLine("- Show character internal thoughts and feelings")
    } else {
      appendLine("- Show emotions through action and dialogue only")
    }
    appendLine()
    appendLine("Write the complete scene with:")
    appendLine("- A strong opening that connects to the previous scene (if any)")
    appendLine("- Clear progression through the key events")
    appendLine("- Character development and emotional depth")
    appendLine("- Sensory details and atmosphere")
    appendLine("- A compelling ending that sets up the next scene")
    appendLine("- Approximately ${sceneOutline.estimated_word_count} words")
    appendLine()
    appendLine("After writing, provide:")
    appendLine("- The scene content")
    appendLine("- Actual word count")
    appendLine("- Key moments (3-5 bullet points of what happened)")
    appendLine("- Character states (how each character ends the scene emotionally/physically)")
    appendLine()
    appendLine("Make the writing engaging, immersive, and true to the characters and story.")
  }

  fun revisionPrompt(
    sceneOutline: NarrativeGenerationTask.SceneOutline,
    generatedScene: NarrativeGenerationTask.GeneratedScene
  ): String = buildString {
    appendLine("You are an expert editor. Review and improve this scene while maintaining its core events and purpose.")
    appendLine()
    appendLine("Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}")
    appendLine()
    appendLine("Current Version:")
    appendLine(generatedScene.content)
    appendLine()
    appendLine("Improve:")
    appendLine("- Prose quality and flow")
    appendLine("- Character voice consistency")
    appendLine("- Sensory details and atmosphere")
    appendLine("- Pacing and tension")
    appendLine("- Dialogue naturalness")
    appendLine("- Emotional impact")
    appendLine()
    appendLine("Maintain:")
    appendLine("- All key events and plot points")
    appendLine("- Character states and development")
    appendLine("- Word count (currently ${generatedScene.word_count}, target ${sceneOutline.estimated_word_count})")
    appendLine("- Tone and style")
    appendLine()
    appendLine("Provide the revised scene content only.")
  }

  fun sceneContent(
    sceneOutline: NarrativeGenerationTask.SceneOutline,
    generatedScene: NarrativeGenerationTask.GeneratedScene
  ): String = buildString {
    appendLine("## ${sceneOutline.title}")
    appendLine()
    appendLine("**Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}**")
    appendLine()
    appendLine("**Setting:** ${sceneOutline.setting_id}")
    appendLine()
    appendLine("**Characters:** ${sceneOutline.characters.joinToString(", ")}")
    appendLine()
    appendLine("---")
    appendLine()
    appendLine(generatedScene.content)
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("**Word Count:** ${generatedScene.word_count}")
    appendLine()
    appendLine("**Key Moments:**")
    generatedScene.key_moments.forEach { moment ->
      appendLine("- $moment")
    }
    appendLine()
    appendLine("**Character States:**")
    generatedScene.character_states.forEach { (char, state) ->
      appendLine("- **$char:** $state")
    }
    appendLine()
    appendLine("**Status:** ✅ Complete")
  }

  fun finalNarrative(
    outline: NarrativeGenerationTask.NarrativeOutline,
    generatedScenes: MutableList<NarrativeGenerationTask.GeneratedScene>,
    cumulativeWordCount: Int
  ): String = buildString {
    appendLine("# ${outline.title}")
    appendLine()
    appendLine("*${outline.premise}*")
    appendLine()
    appendLine("---")
    appendLine()

    outline.acts.forEach { act ->
      appendLine("# Act ${act.act_number}: ${act.title}")
      appendLine()

      val actScenes = generatedScenes.filter { scene ->
        act.scenes?.any { it.scene_number == scene.scene_number } == true
      }

      actScenes.forEach { scene ->
        appendLine("## ${scene.title}")
        appendLine()
        appendLine(scene.content)
        appendLine()
        appendLine("---")
        appendLine()
      }
    }
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("**THE END**")
    appendLine()
    appendLine("*Total Word Count: $cumulativeWordCount*")
  }

  fun finalResult(
    outline: NarrativeGenerationTask.NarrativeOutline,
    cumulativeWordCount: Int,
    generatedScenes: MutableList<NarrativeGenerationTask.GeneratedScene>,
    totalTime: Long,
    outlineContent: String
  ): String = buildString {
    appendLine("# Narrative Generation Summary: ${outline.title}")
    appendLine()
    appendLine("A complete narrative of **$cumulativeWordCount words** across **${generatedScenes.size} scenes** was generated in **${totalTime / 1000.0}s**.")
    appendLine("> The full narrative and detailed transcript are available in the UI tabs for review.")
    appendLine()
    appendLine(outlineContent.substringBeforeLast("\n**Status:**").trim())
  }

  fun overviewContent(
    subject: String,
    genConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData
  ): String =
    buildString {
      appendLine("# Narrative Generation")
      appendLine()
      appendLine("**Subject:** $subject")
      appendLine()
      appendLine("## Configuration")
      appendLine("- Target Word Count: ${genConfig.target_word_count}")
      appendLine("- Structure: ${genConfig.number_of_acts} acts, ~${genConfig.scenes_per_act} scenes per act")
      appendLine("- Writing Style: ${genConfig.writing_style}")
      appendLine("- Point of View: ${genConfig.point_of_view}")
      appendLine("- Tone: ${genConfig.tone}")
      appendLine("- Detailed Descriptions: ${if (genConfig.detailed_descriptions) "✓" else "✗"}")
      appendLine("- Include Dialogue: ${if (genConfig.include_dialogue) "✓" else "✗"}")
      appendLine("- Internal Thoughts: ${if (genConfig.show_internal_thoughts) "✓" else "✗"}")
      appendLine()
      appendLine(
        "**Started:** ${
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }"
      )
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Progress")
      appendLine()
      appendLine("### Phase 1: Narrative Analysis")
      appendLine("*Running base narrative reasoning analysis...*")
    }

}