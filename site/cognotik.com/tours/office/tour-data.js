/**
 * Tour chapter data — defines the video files, metadata, and transcripts
 * for the Cognotik interactive video tour.
 *
 * Video files are referenced relative to the parent directory (),
 * where the .mp4 source files reside.
 */
const TOUR_CHAPTERS = [
    /*Plugin_Install.mp4*/
    {
        id: "plugin-install",
        file: "edit/Plugin_Install.mp4",
        title: "Plugin Installation",
        description: "Easily install and manage AI plugins from the Cognotik Hub — browse categories, read reviews, and add new capabilities to your workspace with a click.",
        icon: "🧩",
        order: 0,
        transcript: "edit/plugin-install.md"
    },
    {
        id: "presentation-creator",
        file: "edit/presentation-creator.mp4",
        title: "Presentation Creator",
        description: "Generate polished, professional presentations from your content using AI — turn ideas and documents into compelling slide decks.",
        icon: "📊",
        order: 4,
        transcript: "edit/presentation-creator.md"
    },
    {
        id: "ocr-import",
        file: "edit/ocr-import.mp4",
        title: "OCR Import",
        description: "Extract text from images and scanned documents with AI-powered OCR — import your existing materials into Cognotik with ease.",
        icon: "📄",
        order: 3,
        transcript: "edit/ocr-import.md"
    },
    {
        id: "tex-wizard",
        file: "edit/tex-wizard.mp4",
        title: "TeX Wizard",
        description: "Create beautifully typeset documents with AI-assisted LaTeX — from academic papers to professional reports, rendered with precision.",
        icon: "✨",
        order: 6,
        transcript: "edit/tex-wizard.md"
    }
];