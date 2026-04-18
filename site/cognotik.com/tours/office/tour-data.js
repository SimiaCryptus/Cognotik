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
     description: "See how to browse, install, and activate premium plugins from the Cognotik Hub in under a minute — no command line required. This chapter covers the full flow from discovery to a ready-to-use workspace.",
        icon: "🧩",
        order: 0,
        transcript: "edit/plugin-install.md"
    },
    {
        id: "presentation-creator",
        file: "edit/presentation-creator.mp4",
        title: "Presentation Creator",
     description: "Feed the AI a topic outline, meeting notes, or an existing document and receive a fully structured slide deck — with speaker notes, consistent styling, and export-ready formats like PPTX and PDF.",
        icon: "📊",
        order: 4,
        transcript: "edit/presentation-creator.md"
    },
    {
        id: "ocr-import",
        file: "edit/ocr-import.mp4",
        title: "OCR Import",
     description: "Drag in a photo, scanned PDF, or screenshot and the AI extracts clean, structured text — preserving tables, headings, and formatting so you can edit, search, and reuse legacy documents instantly.",
        icon: "📄",
        order: 3,
        transcript: "edit/ocr-import.md"
    },
    {
        id: "tex-wizard",
        file: "edit/tex-wizard.mp4",
        title: "TeX Wizard",
     description: "Describe what you need in plain English and the AI generates publication-quality LaTeX — handling equations, bibliographies, figures, and cross-references so you can focus on content, not markup.",
        icon: "✨",
        order: 6,
        transcript: "edit/tex-wizard.md"
    }
];