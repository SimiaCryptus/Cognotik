/**
   * Site-wide configuration: which menus exist, where they live, and how the
   * `file` fields inside them are resolved.
   *
   * Some menu files store a bare file name ("omega.md") while others store a
   * path relative to the site root ("cognitive-mode/Waterfall.md"). The `dir`
   * value below is used to fill in the missing prefix.
   */
  export const SITE = {
    title: 'Cognotik Documentation',
    tagline: 'Apps, modules, task types, cognitive modes and model providers — all in one place.'
  };

  export const SECTIONS = [
    {
      id: 'apps',
      label: 'Apps',
      icon: '▦',
      blurb: 'Ready-to-run applications you can launch and use immediately.',
      dir: 'apps',
      menu: 'menu/apps.json'
    },
    {
      id: 'modules',
      label: 'Modules',
      icon: '◱',
      blurb: 'The building blocks of the platform, from core runtime to IDE plugins.',
      dir: 'modules',
      menu: 'menu/modules.json'
    },
    {
      id: 'task-types',
      label: 'Task Types',
      icon: '◎',
      blurb: 'Individual units of work an orchestration plan can execute.',
      dir: 'task-types',
      menu: 'menu/task-types.json'
    },
    {
      id: 'cognitive-mode',
      label: 'Cognitive Modes',
      icon: '◈',
      blurb: 'How the planner decides what to do next.',
      dir: 'cognitive-mode',
      menu: 'menu/cognitive-mode.json'
    },
    {
      id: 'models',
      label: 'Models',
      icon: '⬡',
      blurb: 'Supported model providers and the capabilities they expose.',
      dir: 'models',
      menu: 'menu/models.json'
    },
    {
      id: 'devtools',
      label: 'Dev Tools',
      icon: '⚒',
      blurb: 'Agentic tooling bundled with every project.',
      dir: 'devtools',
      // No JSON menu exists for this folder, so the pages are declared inline.
      pages: [
        { slug: 'builder', name: 'Builder', file: 'builder.md' },
        { slug: 'coder', name: 'Coder', file: 'coder.md' },
        { slug: 'greenfield', name: 'Greenfield', file: 'greenfield.md' },
        { slug: 'reviewer', name: 'Reviewer', file: 'reviewer.md' }
      ]
    }
  ];