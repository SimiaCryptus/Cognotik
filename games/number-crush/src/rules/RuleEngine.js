// RuleEngine wraps the active rule and evaluates selections.
//
// A rule is an object: { id, description, validate(tiles) -> boolean }.

export class RuleEngine {
  constructor(rule) {
    this.rule = rule;
  }

  setRule(rule) {
    this.rule = rule;
  }

  get description() {
    return this.rule ? this.rule.description : '';
  }

  // Evaluate a selection of Tile objects.
  // Returns { valid: boolean }.
  evaluate(tiles) {
    if (!this.rule) return { valid: false };
    if (!tiles || tiles.length === 0) return { valid: false };
    const valid = !!this.rule.validate(tiles);
    console.log(
      `[RuleEngine] evaluate rule '${this.rule.id}' on values`,
      tiles.map((t) => t.value),
      '->',
      valid
    );
    return { valid };
  }
}
