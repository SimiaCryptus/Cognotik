/**
 * Search + filter logic producing ConceptSummary-like lists.
 */

export function toSummary(c) {
  return {
    id: c.id,
    term: c.term,
    aliases: c.aliases || [],
    domain: c.domain || 'general',
    difficulty: c.difficulty || 'foundational',
    tags: c.tags || [],
    summary: c.summary || '',
    published: c.published !== false,
  };
}

function matchesQuery(c, q) {
  if (!q) return true;
  const hay = [c.term, c.summary, ...(c.aliases || []), ...(c.tags || [])].join(' ').toLowerCase();
  return hay.includes(q);
}

export function filterConcepts(concepts, { search, filterDomain, filterDifficulty }) {
  const q = (search || '').trim().toLowerCase();
  return concepts
    .filter((c) => matchesQuery(c, q))
    .filter((c) => !filterDomain || c.domain === filterDomain)
    .filter((c) => !filterDifficulty || c.difficulty === filterDifficulty)
    .map(toSummary)
    .sort((a, b) => a.term.localeCompare(b.term));
}

export function collectDomains(concepts) {
  const set = new Set();
  for (const c of concepts) {
    if (c.domain) set.add(c.domain);
    for (const d of c.crossDomains || []) set.add(d);
  }
  return [...set].sort();
}
