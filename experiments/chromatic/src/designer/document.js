// Designer document model (designer.md §8).
//
// A plain-data structure describing nodes, links, and constraint groups in a
// single working colorspace. It is serializable to JSON and is designed to be
// convertible into the DSL compiler's artifacts (a constraint list + adjacency
// graph).

let _autoId = 0;
function nextId(prefix) {
  return `${prefix}${_autoId++}`;
}

export function createDocument(overrides = {}) {
  return {
    nodes: [],
    links: [],
    groups: [],
    viewer: {
      space: 'OKLch', // working colorspace
      planeAxes: ['hue', 'chroma'], // [xAxis, yAxis]
      depthAxis: 'lightness',
      depthValue: 0.6,
      gamutOverlay: true,
      ...overrides.viewer,
    },
    ...overrides,
  };
}

// --- nodes --------------------------------------------------------------

// A node stores its full 3D coordinate in OKLab ({ L, a, b }) regardless of
// the viewing space; anchors reference working-space dimensions.
export function addNode(doc, { name, oklab, anchors = [] } = {}) {
  const node = {
    id: nextId('n'),
    name: name ?? `node ${doc.nodes.length + 1}`,
    oklab: { L: oklab.L, a: oklab.a, b: oklab.b },
    anchors, // [{ dimension, target, weight, hard }]
  };
  doc.nodes.push(node);
  return node;
}

export function removeNode(doc, nodeId) {
  doc.nodes = doc.nodes.filter((n) => n.id !== nodeId);
  // drop links that referenced it, and clean up groups
  const droppedLinks = doc.links.filter((l) => l.a === nodeId || l.b === nodeId).map((l) => l.id);
  doc.links = doc.links.filter((l) => l.a !== nodeId && l.b !== nodeId);
  for (const g of doc.groups) {
    g.linkIds = g.linkIds.filter((id) => !droppedLinks.includes(id));
  }
  doc.groups = doc.groups.filter((g) => g.linkIds.length > 0);
}

export function setAnchor(doc, nodeId, anchor) {
  const node = doc.nodes.find((n) => n.id === nodeId);
  if (!node) throw new Error(`setAnchor: no node ${nodeId}`);
  const existing = node.anchors.find((an) => an.dimension === anchor.dimension);
  if (existing) {
    Object.assign(existing, anchor);
  } else {
    node.anchors.push({ weight: 1, hard: false, ...anchor });
  }
  return node;
}

export function clearAnchor(doc, nodeId, dimension) {
  const node = doc.nodes.find((n) => n.id === nodeId);
  if (!node) return;
  node.anchors = node.anchors.filter((an) => an.dimension !== dimension);
}

// --- links --------------------------------------------------------------

export function addLink(doc, aId, bId) {
  if (aId === bId) throw new Error('addLink: cannot link a node to itself');
  const exists = doc.links.some(
    (l) => (l.a === aId && l.b === bId) || (l.a === bId && l.b === aId)
  );
  if (exists)
    return doc.links.find((l) => (l.a === aId && l.b === bId) || (l.a === bId && l.b === aId));
  const link = { id: nextId('l'), a: aId, b: bId };
  doc.links.push(link);
  return link;
}

export function removeLink(doc, linkId) {
  doc.links = doc.links.filter((l) => l.id !== linkId);
  for (const g of doc.groups) {
    g.linkIds = g.linkIds.filter((id) => id !== linkId);
  }
  doc.groups = doc.groups.filter((g) => g.linkIds.length > 0);
}

// --- groups -------------------------------------------------------------

// kind: 'length' | 'angle'; mode: 'free' | 'fixed'.
export function addGroup(doc, { kind, linkIds, mode = 'free', target = null, weight = 1 }) {
  if (!['length', 'angle'].includes(kind)) {
    throw new Error(`addGroup: unknown kind ${kind}`);
  }
  const group = {
    id: nextId('g'),
    kind,
    mode,
    target,
    weight,
    linkIds: [...linkIds],
  };
  doc.groups.push(group);
  return group;
}

export function removeGroup(doc, groupId) {
  doc.groups = doc.groups.filter((g) => g.id !== groupId);
}

// --- serialization ------------------------------------------------------

export function serialize(doc) {
  return JSON.stringify(doc, null, 2);
}

export function deserialize(json) {
  const doc = typeof json === 'string' ? JSON.parse(json) : json;
  // ensure required containers exist
  doc.nodes ??= [];
  doc.links ??= [];
  doc.groups ??= [];
  doc.viewer ??= createDocument().viewer;
  return doc;
}
