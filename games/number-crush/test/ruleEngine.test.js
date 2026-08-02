import { describe, it, expect } from 'vitest';
import { RuleEngine } from '../src/rules/RuleEngine.js';
import { sumEquals } from '../src/rules/rules/sumEquals.js';

const t = (value) => ({ value });

describe('sumEquals rule', () => {
  it('validates a correct sum', () => {
    const engine = new RuleEngine(sumEquals(10));
    expect(engine.evaluate([t(4), t(6)]).valid).toBe(true);
    expect(engine.evaluate([t(3), t(3), t(4)]).valid).toBe(true);
  });

  it('rejects an incorrect sum', () => {
    const engine = new RuleEngine(sumEquals(10));
    expect(engine.evaluate([t(4), t(5)]).valid).toBe(false);
  });

  it('rejects an empty selection', () => {
    const engine = new RuleEngine(sumEquals(10));
    expect(engine.evaluate([]).valid).toBe(false);
  });

  it('exposes a description', () => {
    const engine = new RuleEngine(sumEquals(12));
    expect(engine.description).toContain('12');
  });
});
