import { describe, it, expect } from 'vitest';
import { Selection } from '../src/input/Selection.js';

describe('Selection', () => {
  it('adds a first tile freely', () => {
    const s = new Selection();
    expect(s.add({ q: 0, r: 0 })).toBe(true);
    expect(s.length).toBe(1);
  });

  it('only adds contiguous neighbors', () => {
    const s = new Selection();
    s.add({ q: 0, r: 0 });
    expect(s.add({ q: 1, r: 0 })).toBe(true); // neighbor
    expect(s.add({ q: 5, r: 5 })).toBe(false); // not neighbor
  });

  it('rejects repeated tiles', () => {
    const s = new Selection();
    s.add({ q: 0, r: 0 });
    s.add({ q: 1, r: 0 });
    expect(s.add({ q: 0, r: 0 })).toBe(false);
  });

  it('enforces max length', () => {
    const s = new Selection({ minLength: 2, maxLength: 2 });
    s.add({ q: 0, r: 0 });
    s.add({ q: 1, r: 0 });
    expect(s.add({ q: 1, r: -1 })).toBe(false);
  });

  it('supports drag-back', () => {
    const s = new Selection();
    s.add({ q: 0, r: 0 });
    s.add({ q: 1, r: 0 });
    expect(s.tryBacktrack({ q: 0, r: 0 })).toBe(true);
    expect(s.length).toBe(1);
  });

  it('reports valid length', () => {
    const s = new Selection({ minLength: 2, maxLength: 4 });
    s.add({ q: 0, r: 0 });
    expect(s.hasValidLength()).toBe(false);
    s.add({ q: 1, r: 0 });
    expect(s.hasValidLength()).toBe(true);
  });
});
