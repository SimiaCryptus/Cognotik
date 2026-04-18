/**
 * Tour chapter data — defines the video files, metadata, and transcripts
 * for the Cognotik interactive video tour.
 *
 * Video files are referenced relative to the parent directory (),
 * where the .mp4 source files reside.
 */
const TOUR_CHAPTERS = [
    {
        id: "plugin-install",
        file: "edit/install.mp4",
        title: "Plugin Installation",
     description: "See how to browse, install, and activate premium plugins from the Cognotik Hub in under a minute — no command line required. This chapter covers the full flow from discovery to a ready-to-use workspace.",
        icon: "🧩",
        order: 0,
        transcript: "edit/plugin-install.md"
    },
    {
        id: "career-advisor",
        file: "edit/career-advisor.mp4",
        title: "Career Advisor",
     description: "Upload your resume and let the AI map out realistic career trajectories, identify skill gaps worth closing, and suggest concrete next steps — from certifications to lateral moves you might not have considered.",
        icon: "🎯",
        order: 1,
        transcript: "edit/career-advisor.md"
    },
    {
        id: "job-hunter",
        file: "edit/job-hunter.mp4",
        title: "Job Hunter",
     description: "Define your target role, location, and preferences, then watch the AI scan multiple job boards, rank matches by fit, and compile a shortlist — complete with salary estimates and application deadlines.",
        icon: "🔍",
        order: 2,
        transcript: "edit/job-hunter.md"
    },
    {
        id: "resume-customizer",
        file: "edit/resume-customizer.mp4",
        title: "Resume Customizer",
     description: "Paste a job posting URL and your master resume, and the AI rewrites bullet points, reorders sections, and injects relevant keywords — producing a targeted, ATS-friendly resume in seconds.",
        icon: "📝",
        order: 5,
        transcript: "edit/resume-customizer.md"
    }
];