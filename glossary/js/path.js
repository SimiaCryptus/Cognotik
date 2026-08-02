/**
 * Path-exploration mode logic.
 *
 * Strategy (per idea.md):
 *  - Start with root nodes that have no prerequisites.
 *  - Marking a node "known" unlocks its children (enabledBy) as new frontier.
 *  - Marking a node "don't know" surfaces its parents (requires) as new roots.
 */

export class PathExplorer {
  constructor(graph, store) {
    this.graph = graph;
    this.store = store;
  }

  get known() {
    return this.store.state.known;
  }

  get unknown() {
    return this.store.state.unknown;
  }

  /**
   * Compute the current frontier of explorable concept ids.
   */
  frontier() {
    const graph = this.graph;
    const known = this.known;
    const unknown = this.unknown;
    const frontier = new Set();

    // Seed with roots.
    for (const id of graph.roots()) {
      if (!known.has(id)) frontier.add(id);
    }

    // Unlock children of known nodes whose prerequisites are all satisfied.
    for (const id of known) {
      for (const childId of graph.enabledBy(id)) {
        if (!known.has(childId) && graph.isUnlocked(childId, known)) {
          frontier.add(childId);
        }
      }
    }

    // Surface parents of "don't know" nodes as new roots.
    for (const id of unknown) {
      for (const parentId of graph.requires(id)) {
        if (!known.has(parentId)) frontier.add(parentId);
      }
      // keep the unknown node itself visible so user can revisit
      if (!known.has(id)) frontier.add(id);
    }

    return [...frontier];
  }

  markKnown(id) {
    this.store.markKnown(id);
  }

  markUnknown(id) {
    this.store.markUnknown(id);
  }

  progress() {
    const total = this.graph.all().length;
    return { known: this.known.size, total };
  }
}
