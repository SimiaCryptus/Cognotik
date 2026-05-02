package com.simiacryptus.cognotik.agents
    
    import com.simiacryptus.cognotik.models.AudioSegment
    
    /**
     * Data class representing a combined audio and text payload.
     * Used as input/output for AudioProcessingAgent.
     *
     * @property text The text content associated with the audio.
     * @property audio The audio content (may be null if not present).
     */
    data class AudioAndText(
      val text: String = "",
      val audio: AudioSegment? = null,
    )