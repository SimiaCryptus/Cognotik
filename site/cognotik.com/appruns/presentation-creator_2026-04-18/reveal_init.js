// More info about initialization & config:


Reveal.initialize({
    plugins: [RevealMarkdown, RevealHighlight, RevealNotes, RevealZoom],
    hash: true,
    navigationMode: 'linear',
    slideNumber: true,
    progress: true,
    transition: 'slide',
    transitionSpeed: 'fast',
    autoPlayMedia: false,
    autoSlide: 0,
    center: false,
    controlsTutorial: true,
    width: "100%",
    height: "100%",
    margin: 0.04,
    minScale: 0.2,
    maxScale: 1.5,
    zoom: {
        maxScale: 2.0,
        pan: false
    },
    keyboard: {
        65: toggleAutoplay,

        68: displayDebugLog

    },
});