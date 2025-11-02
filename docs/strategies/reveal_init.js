// More info about initialization & config:


Reveal.initialize({
    hash: true,

    plugins: [RevealMarkdown, RevealHighlight, RevealNotes, RevealZoom],
    navigationMode: 'linear',

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
        65: toggleAutoplay,

        68: displayDebugLog

    },
});