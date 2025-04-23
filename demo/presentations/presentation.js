let synth = window.speechSynthesis;
let isAutoPlaying = false;
let utterances = [];
let currentUtteranceIndex = 0;
const autoplayButton = document.getElementById('autoplayButton');
const voiceSelect = document.getElementById('voiceSelect');
const statusMessage = document.getElementById('statusMessage');
const debugLog = [];

function handleVideoElements(callback) {
    const currentSlide = Reveal.getCurrentSlide();
    const video = currentSlide.querySelector('video');
    if (video) {
        if (isAutoPlaying) {
            video.play();
            video.onerror = () => {
                log('Video failed to load, proceeding to next step');
                if (callback) callback();
            };
            video.onended = () => {
                if (callback) callback();
            };
        } else {
            video.pause();
            video.currentTime = 0;
            video.onended = null;
            video.onerror = null;
            video.onerror = null;
        }
    } else {

        if (callback) callback();
    }
}

function log(message) {
    console.log(message);
    //debugLog.push(`${new Date().toISOString()}: ${message}`);
    //if (debugLog.length > 100) debugLog.shift();
    //statusMessage.textContent = message;
    //setTimeout(() => { statusMessage.textContent = ''; }, 3000);
}

function displayDebugLog() {
    alert(debugLog.join('\n'));
}

function createUtterances(text) {
    let sentences = text.match(/[^.!?]+[.!?]+/g) || [text];
    return sentences.map(sentence => {
        let utterance = new SpeechSynthesisUtterance(sentence.trim());
        utterance.lang = 'en-US';
        utterance.volume = 1;
        utterance.rate = 1;
        utterance.pitch = 1;
        const selectedVoice = voiceSelect.value;
        if (selectedVoice) {
            utterance.voice = synth.getVoices().find(voice => voice.name === selectedVoice);
        }
        return utterance;
    });
}


function speakNotes() {
    log('Entering speakNotes function');
    let notes = Reveal.getCurrentSlide().querySelector('aside.notes');
    if (notes) {
        let audioSrc = notes.getAttribute('data-audio-src');
        if (audioSrc) {
            let audio = new Audio(audioSrc);
            audio.onended = () => {
                if (isAutoPlaying) {
                    log('Audio ended, moving to next slide in 1 second');
                    setTimeout(() => handleVideoElements(() => Reveal.next()), 1000);
                } else {
                    log('Audio ended, autoplay is off');
                }
            };
            try {
                audio.play();
                log(`Playing audio from ${audioSrc}`);
            } catch (error) {
                log(`Error playing audio: ${error.message}`);
            }
            return; // Exit the function to prevent speech synthesis
        }
        let text = notes.textContent.replace(/\s+/g, ' ').trim();
        log(`Notes text: ${text.substring(0, 50)}...`);
        utterances = createUtterances(text);
        log(`Created ${utterances.length} utterances`);
        currentUtteranceIndex = 0;
        utterances.forEach(utterance => {
            utterance.onend = onUtteranceEnd;
        });
        try {
            synth.cancel();  // Cancel any ongoing speech
            synth.speak(utterances[currentUtteranceIndex]);
            log('Started speaking first utterance');
        } catch (error) {
            log(`Error in synth.speak: ${error.message}`);
        }
    } else {
        if (isAutoPlaying) {
            log('No notes found, moving to next slide in 1 second');
            setTimeout(() => handleVideoElements(() => Reveal.next()), 1000);
        } else {
            log('No notes found and not autoplaying');
        }
    }
    handleVideoElements();
}

// Check if speech synthesis is supported and initialize voices
function initSpeech() {
    if ('speechSynthesis' in window) {
        log('Speech synthesis is supported');
        listVoices();
        synth.onvoiceschanged = listVoices;
        voiceSelect.addEventListener('change', () => {
            if (isAutoPlaying) {
                stopSpeaking();
                speakNotes();
            }
        });
    } else {
        log('Speech synthesis is not supported in this browser');
    }
}

// Call initSpeech when the page loads
window.addEventListener('load', initSpeech);

// List available voices
function listVoices() {
    let voices = synth.getVoices();
    log(`Available voices: ${voices.length}`);
    voiceSelect.innerHTML = '';
    voices.forEach((voice, index) => {
        log(`Voice ${index}: ${voice.name} (${voice.lang})`);
        const option = document.createElement('option');
        option.value = voice.name;
        option.textContent = `${voice.name} (${voice.lang})`;
        voiceSelect.appendChild(option);
    });
    if (voices.length > 0) {
        voiceSelect.value = voices.find(voice => voice.lang.startsWith('en-'))?.name || voices[0].name;
    }
}

// Call listVoices when voices are loaded
if (synth.onvoiceschanged !== undefined) {
    synth.onvoiceschanged = listVoices;
}

function onUtteranceEnd() {
    log('Utterance ended');
    currentUtteranceIndex++;
    if (currentUtteranceIndex < utterances.length) {
        try {
            synth.speak(utterances[currentUtteranceIndex]);
            log(`Speaking utterance ${currentUtteranceIndex + 1} of ${utterances.length}`);
        } catch (error) {
            log(`Error in synth.speak: ${error.message}`);
        }
    } else {
        if (isAutoPlaying) {
            log('All utterances finished, moving to next slide in 1 second');
            setTimeout(() => handleVideoElements(() => Reveal.next()), 1000);
        } else {
            log('All utterances finished, autoplay is off');
        }
    }
}

function stopSpeaking() {
    log('Stopping speech');
    try {
        synth.cancel();
    } catch (error) {
        log(`Error in synth.cancel: ${error.message}`);
    }
    utterances = [];
    currentUtteranceIndex = 0;
}

function toggleAutoplay() {
    log(`Toggling autoplay. Current state: ${isAutoPlaying}`);
    isAutoPlaying = !isAutoPlaying;
    if (isAutoPlaying) {
        autoplayButton.textContent = 'Autoplay: On';
        speakNotes();
    } else {
        stopSpeaking();
        autoplayButton.textContent = 'Autoplay: Off';
    }
    handleVideoElements();
}

document.addEventListener('keydown', (event) => {
    if (event.key === 'a' || event.key === 'A') {
        log('Autoplay toggled by keyboard');
        toggleAutoplay();
    } else if (event.key === 'd' || event.key === 'D') {
        displayDebugLog();
    }
});
autoplayButton.addEventListener('click', () => {
    log('Autoplay toggled by button click');
    toggleAutoplay();
});

Reveal.on('slidechanged', event => {
    log('Slide changed');
    if (isAutoPlaying) {
        setTimeout(() => {
            stopSpeaking();
            speakNotes();
        }, 100);
    } else {
        handleVideoElements();
    }
});