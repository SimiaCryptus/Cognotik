/** Loading + normalization of the JSON menu descriptors. */

  export async function loadSections(sections) {
    const loaded = await Promise.all(sections.map(loadSection));
    return loaded;
  }

  async function loadSection(section) {
    let raw = Array.isArray(section.pages) ? section.pages.slice() : [];
    let error = null;

    if (section.menu) {
      try {
        const response = await fetch(section.menu, { cache: 'no-cache' });
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        const data = await response.json();
        const entries = data.apps || data.pages || data.items || [];
        if (!Array.isArray(entries)) throw new Error('Menu file has no array of entries');
        raw = entries;
      } catch (cause) {
        error = `Could not load ${section.menu}: ${cause.message}`;
        console.warn('[menu]', error);
      }
    }

    const pages = raw
      .filter((entry) => entry && entry.file)
      .map((entry) => normalizePage(entry, section));

    return { ...section, pages, error };
  }

  function normalizePage(entry, section) {
    const file = normalizePath(
      entry.file.includes('/') ? entry.file : `${section.dir}/${entry.file}`
    );
    const slug = entry.slug || slugFromFile(entry.file);
    return {
      slug,
      name: entry.name || prettify(slug),
      shortDescription: entry.shortDescription || '',
      file,
      section: section.id,
      sectionLabel: section.label,
      route: `#/${section.id}/${slug}`
    };
  }

  export function normalizePath(path) {
    return String(path).replace(/^\.\//, '').replace(/^\/+/, '');
  }

  function slugFromFile(file) {
    return normalizePath(file)
      .split('/')
      .pop()
      .replace(/\.md$/i, '')
      .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase();
  }

  function prettify(slug) {
    return slug.replace(/[-_]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  }

  /** Flat, ordered list of every page across every section. */
  export function flatten(sections) {
    return sections.flatMap((section) => section.pages);
  }

  export function indexPages(sections) {
    const byRoute = new Map();
    const byFile = new Map();
    for (const page of flatten(sections)) {
      byRoute.set(`${page.section}/${page.slug}`, page);
      byFile.set(page.file.toLowerCase(), page);
    }
    return { byRoute, byFile };
  }