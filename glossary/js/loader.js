/**
 * Loads and merges all glossary JSON shards into a single Glossary model.
 * Each shard file (A_*.json, B_*.json, …) is expected to be either:
 *   - a full Glossary object ({ version, title, description, concepts }), or
 *   - a bare map of concepts ({ id: GlossaryConcept, … }), or
 *   - an array of GlossaryConcept objects.
 */

// List of shard files, mirroring dir.txt (the letter-prefixed JSON files).
export const SHARD_FILES = [
  'A_algebraic_structures.json',
  'B_geometric_constructions.json',
  'C_higher_dimensional_polytopes.json',
  'D_group_theory.json',
  'E_topology_bundles.json',
  'F_dimensions_scaling.json',
  'G_spectral_graph_theory.json',
  'H_fractals_self_similarity.json',
  'I_cellular_automata.json',
  'J_physics.json',
  'K_extremal.json',
  'L_reconnection.json',
  'M_algorithmic.json',
  'N_notation.json',
  'O_project_terms.json',
];

function normalizeShard(data) {
  if (!data) return {};
  // Full glossary object
  if (data.concepts && typeof data.concepts === 'object') {
    return data.concepts;
  }
  // Array of concepts
  if (Array.isArray(data)) {
    const map = {};
    for (const c of data) {
      if (c && c.id) map[c.id] = c;
    }
    return map;
  }
  // Bare map keyed by id
  if (typeof data === 'object') {
    // Heuristic: values look like concepts (have term/id)
    const map = {};
    for (const [key, val] of Object.entries(data)) {
      if (val && typeof val === 'object') {
        map[val.id || key] = val;
      }
    }
    return map;
  }
  return {};
}

async function fetchShard(base, file) {
  try {
    const res = await fetch(`${base}${file}`, { cache: 'no-cache' });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return normalizeShard(json);
  } catch (err) {
    console.warn(`Failed to load shard ${file}:`, err.message);
    return {};
  }
}

export async function loadGlossary(base = '') {
  const results = await Promise.all(SHARD_FILES.map((file) => fetchShard(base, file)));

  const concepts = {};
  for (const shard of results) {
    Object.assign(concepts, shard);
  }

  const glossary = {
    version: '1.0.0',
    title: 'Pentagon Lattice Geometry Glossary',
    description: 'Interactive concept explorer.',
    concepts,
  };

  return glossary;
}
