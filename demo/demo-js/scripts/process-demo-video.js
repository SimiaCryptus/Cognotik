const fs = require('fs');
const path = require('path');
const {execSync} = require('child_process');

/**
 * Post-process Cypress videos to combine with narration audio
 * This script assumes you have ffmpeg installed
 */
function processVideo(videoPath, audioDir) {
    const videoName = path.basename(videoPath, '.mp4');
    const outputPath = path.join(path.dirname(videoPath), `${videoName}_with_audio.mp4`);

    // Check if we have corresponding audio files
    const audioFiles = fs.readdirSync(audioDir)
        .filter(file => file.endsWith('.mp3'))
        .sort();

    if (audioFiles.length === 0) {
        console.log('No audio files found, keeping original video');
        return videoPath;
    }

    // Create a temporary audio file by concatenating all narration files
    const tempAudioPath = path.join(audioDir, 'temp_combined.mp3');

    try {
        // Combine audio files with silence between them
        const audioInputs = audioFiles.map(file => path.join(audioDir, file)).join('|');
        execSync(`ffmpeg -i "concat:${audioInputs}" -c copy "${tempAudioPath}"`);

        // Combine video with audio
        execSync(`ffmpeg -i "${videoPath}" -i "${tempAudioPath}" -c:v copy -c:a aac -map 0:v:0 -map 1:a:0 "${outputPath}"`);

        // Clean up
        fs.unlinkSync(tempAudioPath);

        console.log(`Processed video saved to: ${outputPath}`);
        return outputPath;
    } catch (error) {
        console.error('Error processing video:', error.message);
        return videoPath;
    }
}

module.exports = {processVideo};