/**
 * Tour chapter data — defines the video files, metadata, and transcripts
 * for the Cognotik interactive video tour.
 *
 * Video files are referenced relative to the parent directory (),
 * where the .mp4 source files reside.
 */
const TOUR_CHAPTERS = [
    {
        id: "install-windows",
        file: "edit/Install_Windows.mp4",
        title: "Installing on Windows",
        description: "Get started with Cognotik by installing it on Windows. This walkthrough covers the full setup process from download to first launch.",
        icon: "💻",
        order: 1,
        transcript: "edit/Install_Windows.md"
    },
    {
        id: "plugin-install",
        file: "edit/Plugin_Install.mp4",
        title: "Installing Plugins",
        description: "Learn how to extend Cognotik's capabilities by installing plugins from the plugin marketplace.",
        icon: "🔌",
        order: 2,
        transcript: "edit/Plugin_Install.md"
    },
    {
        id: "sys-wizard",
        file: "edit/Sys_Wizard.mp4",
        title: "System Wizard",
        description: "Walk through the System Wizard to configure Cognotik's core settings and AI model connections.",
        icon: "🧙",
        order: 3,
        transcript: "edit/Sys_Wizard.md"
    },
    {
        id: "filesystem",
        file: "edit/Filesystem.mp4",
        title: "Filesystem Access",
        description: "Explore the powerful filesystem backing every session — download zips, view markdown as HTML or PDF, and use built-in Git version control.",
        icon: "📁",
        order: 4,
        transcript: "edit/Filesystem.md"
    },
    {
        id: "comic-generator",
        file: "edit/Comic_Generator.mp4",
        title: "Comic Book Generator",
        description: "Watch the Comic Serial app generate a full comic book — from script and character references to rendered HTML pages with dialogue.",
        icon: "🎨",
        order: 5,
        transcript: "edit/Comic_Generator.md"
    },
    {
        id: "philosophical-calculator",
        file: "edit/Philosophical_Calculator.mp4",
        title: "Philosophical Calculator",
        description: "A fun demonstration of AI-powered app generation — a calculator that provides philosophical commentary on your equations.",
        icon: "🧮",
        order: 6,
        transcript: "edit/Philosophical_Calculator.md"
    },
    {
        id: "webapp-factory",
        file: "edit/WebApp_Factory.mp4",
        title: "WebApp Factory",
        description: "See how Cognotik can generate complete web applications from natural language descriptions using the WebApp Factory.",
        icon: "🏭",
        order: 7,
        transcript: "edit/WebApp_Factory.md"
    }
];