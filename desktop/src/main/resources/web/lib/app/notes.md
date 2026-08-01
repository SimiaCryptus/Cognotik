We are moving these modules from `<app>/util/<module>.js` to  `/lib/app/<module>.js` where the fileserver basis is
indicated by the loading html file This might need to be addressed by setting a variable from the html page. if so,
document this migration requirement. Either way, create `migration.md` to discuss

Major new feature: we want to add a new menu.js module that will automaticlly add a common menubar to any of our apps.
Functionality:

* Basic nav based on context (main page `/`, new app session `new`, etc)
* Links to filesystem ide view (e.g.
  `https://hosted.cognotik.com/presentation-creator/ui/?session=U-20260801-TH2s9UEo#/`; for reference a typical app url
  is `https://hosted.cognotik.com/presentation-creator/fileIndex/U-20260801-TH2s9UEo/app.html`
* Git status, operations
* Sessions: Currently running, all defined sessions, with navigation
* usage