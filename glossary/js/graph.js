/**
 * Graph utilities over the glossary concept relations.
 * Computes inverse edges (enabledBy) and exposes prerequisite traversal.
 */

export class ConceptGraph {
  constructor(glossary) {
    this.glossary = glossary;
    this.concepts = glossary.concepts;
    this._buildInverse();
  }

  get(id) {
    return this.concepts[id] || null;
  }

  has(id) {
    return Boolean(this.concepts[id]);
  }

  all() {
    return Object.values(this.concepts);
  }

  _refIds(refs) {
    return (refs || []).map((r) => r.id).filter(Boolean);
  }

  /** Build/augment the enabledBy (inverse of requires) edges. */
  _buildInverse() {
    // collect inverse: child requires parent => parent.enabledBy includes child
    const inverse = new Map(); // parentId -> Set(childId)

    for (const concept of this.all()) {
      if (!concept.relations) concept.relations = {};
      const rels = concept.relations;
      rels.requires ||= [];
      rels.related ||= [];
      rels.enabledBy ||= [];
      rels.synonyms ||= [];

      for (const pid of this._refIds(rels.requires)) {
        if (!inverse.has(pid)) inverse.set(pid, new Set());
        inverse.get(pid).add(concept.id);
      }
    }

    for (const [parentId, childSet] of inverse.entries()) {
      const parent = this.concepts[parentId];
      if (!parent) continue;
      const existing = new Set(this._refIds(parent.relations.enabledBy));
      for (const childId of childSet) {
        if (!existing.has(childId)) {
          parent.relations.enabledBy.push({ id: childId });
          existing.add(childId);
        }
      }
    }
  }

  requires(id) {
    const c = this.get(id);
    return c ? this._refIds(c.relations.requires) : [];
  }

  enabledBy(id) {
    const c = this.get(id);
    return c ? this._refIds(c.relations.enabledBy) : [];
  }

  /** Root nodes: concepts with no prerequisites. */
  roots() {
    return this.all()
      .filter((c) => this.requires(c.id).length === 0)
      .map((c) => c.id);
  }

  /** Does this concept have all prerequisites satisfied by `knownSet`? */
  isUnlocked(id, knownSet) {
    return this.requires(id).every((pid) => knownSet.has(pid));
  }
}
