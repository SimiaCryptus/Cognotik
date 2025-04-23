// More info about initialization & config:
// * https://revealjs.com/initialization/
// * https://revealjs.com/config/
Reveal.initialize({
    hash: true,
// Plugins configuration
    plugins: [RevealMarkdown, RevealHighlight, RevealNotes, RevealZoom],
    navigationMode: 'linear',
// Additional configuration options
    slideNumber: true,
    progress: true,
    transition: 'slide',
    transitionSpeed: 'fast',
    autoPlayMedia: false,
    autoSlide: 0,
    center: false,
    controlsTutorial: true,
    zoom: {
        maxScale: 2.0,
        pan: false
    },
    keyboard: {
        65: toggleAutoplay, // 'a' key
        68: displayDebugLog // 'd' key
    },
});