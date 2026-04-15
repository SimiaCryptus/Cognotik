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
        description: "Easily install and manage AI plugins from the Cognotik Hub — browse categories, read reviews, and add new capabilities to your workspace with a click.",
        icon: "🧩",
        order: 0,
        transcript: "edit/plugin-install.md"
    },
    {
        id: "career-advisor",
        file: "edit/career-advisor.mp4",
        title: "Career Advisor",
        description: "Get personalized career guidance powered by AI — explore career paths, skill gaps, and actionable next steps tailored to your background.",
        icon: "🎯",
        order: 1,
        transcript: "edit/career-advisor.md"
    },
    {
        id: "job-hunter",
        file: "edit/job-hunter.mp4",
        title: "Job Hunter",
        description: "Automate your job search with AI — discover relevant opportunities, track applications, and get insights to land your next role faster.",
        icon: "🔍",
        order: 2,
        transcript: "edit/job-hunter.md"
    },
    {
        id: "resume-customizer",
        file: "edit/resume-customizer.mp4",
        title: "Resume Customizer",
        description: "Tailor your resume to any job posting with AI — highlight the right skills and experience to maximize your chances of getting noticed.",
        icon: "📝",
        order: 5,
        transcript: "edit/resume-customizer.md"
    }
];