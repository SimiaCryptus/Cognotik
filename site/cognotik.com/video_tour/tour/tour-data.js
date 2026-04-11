/**
 * Tour chapter data — defines the video files, metadata, and transcripts
 * for the Cognotik interactive video tour.
 *
 * Video files are referenced relative to the parent directory (../),
 * where the .mp4 source files reside.
 */
const TOUR_CHAPTERS = [
    {
        id: "install-windows",
        file: "../edit/Install_Windows.mp4",
        title: "Installing on Windows",
        description: "Get started with Cognotik by installing it on Windows. This walkthrough covers the full setup process from download to first launch.",
        icon: "💻",
        order: 1,
        transcript: null
    },
    {
        id: "plugin-install",
        file: "../edit/Plugin_Install.mp4",
        title: "Installing Plugins",
        description: "Learn how to extend Cognotik's capabilities by installing plugins from the plugin marketplace.",
        icon: "🔌",
        order: 2,
        transcript: null
    },
    {
        id: "sys-wizard",
        file: "../edit/Sys_Wizard.mp4",
        title: "System Wizard",
        description: "Walk through the System Wizard to configure Cognotik's core settings and AI model connections.",
        icon: "🧙",
        order: 3,
        transcript: null
    },
    {
        id: "filesystem",
        file: "../edit/Filesystem.mp4",
        title: "Filesystem Access",
        description: "Explore the powerful filesystem backing every session — download zips, view markdown as HTML or PDF, and use built-in Git version control.",
        icon: "📁",
        order: 4,
        transcript: `All sessions have a file system backing them that can be accessed. If you go to the URL that is here's a previous run, for example, that we generated a comic book from. If we open in a new tab that same URL but we remove the web page part so that we just access the root directory of that session. We can access the file system itself for that session. Now this comes with a number of interesting features. First of all, we can download the entire directory as a zip file. We can look at any markdown files, we can look at any HTML files and directly view them, of course, and we can also view markdown files. As either markdown, which this will cause download, or you can view the markdown files as HTML files that does a dynamic rendering. You can also view them as text files. This is handy if you want to view the markdown source, in a way that won't trigger a download for your browser. And you can also view markdown files as PDFs and that also does a dynamic rendering of the markdown file into PDF format. Additionally, the file system has built-in Git support, so you can view the current status, you can commit the directory. If you're not aware, Git is a version control system for file systems so that it's basically like a time machine so that you can track all of the file changes and if needed rollback. Accessing the root file system for any given session via this interface gives you a powerful backdoor into the system functionality. And provides a good level of hackability and transparency for any Cognotic applications. Finally, I'd like to point out that the physical location of the file system is shown here. So if you want, you can mount this with a development environment or you can open it in the file system explorer or whatever you want to do. I hope that functionality is useful to you.`
    },
    {
        id: "comic-generator",
        file: "../edit/Comic_Generator.mp4",
        title: "Comic Book Generator",
        description: "Watch the Comic Serial app generate a full comic book — from script and character references to rendered HTML pages with dialogue.",
        icon: "🎨",
        order: 5,
        transcript: `Probably the most entertaining app is the comic serial app. It generates a series of comic books. Based on your prompts here, let's say, Come

We saved this idea. Make sure that we have models selected. These are the deep. Must be First on the list for whatever reason. We will select. Flash three, Gemini three flash preview. And for the image model, let's use Gemini one flash image preview. Save our model settings. And the reason it's called Comic serial is because you can generate a first comic book series, comic book, which includes many frames. We will, we will do that in a second, but then afterwards you can generate sequels and you can keep generating sequels as desired. The final step is then to render the comic book into an HTML format, which uses the same images. It just gives it a, Nice HTML framing. But first, we will monitor the generation of the comic book itself.

We have a Sketch of a script. And then it goes into character generation. The first step that it does when generating comics is to generate Character reference images. These are then used when it's rendering the comics themselves. In order to achieve. Artistic. Consistency

And now we're on to the actual page generation.

And it ends with even a Nice little fourth wall joke. Great. So, now that that generation has completed and my parakeets agree, It is time to render the comic book. This renders the HTML structure that will, How is the comic since the, Basic comic book framing is. Somewhat Basic.

Here we go. Let's open this in a new tab and preview it. We've got our character reference images and, No. A much more attractive presentation with the textual, Dialogue alongside also. And that is the Comic book generator. I hope you enjoy it.`
    },
    {
        id: "philosophical-calculator",
        file: "../edit/Philosophical_Calculator.mp4",
        title: "Philosophical Calculator",
        description: "A fun demonstration of AI-powered app generation — a calculator that provides philosophical commentary on your equations.",
        icon: "🧮",
        order: 6,
        transcript: null
    },
    {
        id: "webapp-factory",
        file: "../edit/WebApp_Factory.mp4",
        title: "WebApp Factory",
        description: "See how Cognotik can generate complete web applications from natural language descriptions using the WebApp Factory.",
        icon: "🏭",
        order: 7,
        transcript: null
    }
];