// ca.js
// Cellular automaton on the multi-sheeted pentagon lattice.
//
// Each tile has an integer state in [0, numStates). The transition rule
// is "outer-totalistic": the next state of a cell depends only on its
// current state and the multiset (here: count for binary, or sum for
// multi-state) of its 5 neighbors' states.
//
// We support several rule families:
//   - "life":   binary B/S rule, parameterized by birth & survival sets
//               over {0,1,2,3,4,5} (the count of live neighbors).
//   - "parity": next state = sum of neighbor states (mod numStates).
//               A linear rule; produces self-similar fractal patterns.
//   - "cyclic": Greenberg-Hastings / cyclic CA. A cell in state s
//               advances to (s+1) mod numStates iff at least `threshold`
//               neighbors are in state (s+1) mod numStates.
//   - "majority": next state = most common state among self+neighbors
//                 (ties broken toward current state).
//
// Boundary tiles (those with fewer than 5 in-lattice neighbors) treat
// missing neighbors as state 0.

export const RULE_FAMILIES = ['life', 'parity', 'cyclic', 'majority'];
// Extended families:
//   - "threshold":  binary; cell becomes 1 iff live-neighbor count >= k.
//   - "diffusion":  next state = round(mean(self + neighbors)).
//   - "sheetflow":  anyonic-style; uses (sheet index of neighbors) mod n.
//   - "xor":        binary; next state = XOR of all neighbor states.
export const ALL_RULE_FAMILIES = [...RULE_FAMILIES, 'threshold', 'diffusion', 'sheetflow', 'xor'];

// Parse a "B/S" string like "B3/S23" into two Sets. For pentagons the
// counts live in {0,1,2,3,4,5}.
export function parseLifeRule(str) {
  const birth = new Set();
  const survive = new Set();
  if (!str) return { birth, survive };
  const m = str.toUpperCase().match(/B([0-9]*)\/?S([0-9]*)/);
  if (!m) return { birth, survive };
  for (const ch of m[1]) {
    const n = parseInt(ch, 10);
    if (n >= 0 && n <= 5) birth.add(n);
  }
  for (const ch of m[2]) {
    const n = parseInt(ch, 10);
    if (n >= 0 && n <= 5) survive.add(n);
  }
  return { birth, survive };
}

export function lifeRuleToString(birth, survive) {
  const b = [...birth].sort((a, b) => a - b).join('');
  const s = [...survive].sort((a, b) => a - b).join('');
  return `B${b}/S${s}`;
}

export class CA {
  constructor(lattice, opts = {}) {
    this.lattice = lattice;
    this.numStates = opts.numStates ?? 2;
    this.family = opts.family ?? 'life';
    // life rule defaults: B3/S23-ish adapted for 5-neighbor.
    // A reasonable starting point on pentagons: B2/S23.
    this.lifeRule = opts.lifeRule ?? parseLifeRule('B2/S23');
    this.cyclicThreshold = opts.cyclicThreshold ?? 1;
    this.threshold = opts.threshold ?? 2;
    const n = lattice.tiles.length;
    this.state = new Uint8Array(n);
    this.next = new Uint8Array(n);
    this.generation = 0;
  }

  setNumStates(n) {
    this.numStates = Math.max(2, Math.min(16, n | 0));
    // clamp existing state values
    for (let i = 0; i < this.state.length; i++) {
      if (this.state[i] >= this.numStates) this.state[i] = this.numStates - 1;
    }
  }

  setFamily(f) {
    if (ALL_RULE_FAMILIES.includes(f)) this.family = f;
  }

  setLifeRule(str) {
    this.lifeRule = parseLifeRule(str);
  }

  setCyclicThreshold(t) {
    this.cyclicThreshold = Math.max(1, Math.min(5, t | 0));
  }
  setThreshold(t) {
    this.threshold = Math.max(0, Math.min(5, t | 0));
  }

  clear() {
    this.state.fill(0);
    this.generation = 0;
  }

  randomize(density = 0.3, seed = null) {
    let rand = Math.random;
    if (seed !== null) {
      // simple LCG for reproducibility
      let s = seed | 0 || 1;
      rand = () => {
        s = (s * 1664525 + 1013904223) | 0;
        return ((s >>> 0) % 1000000) / 1000000;
      };
    }
    for (let i = 0; i < this.state.length; i++) {
      if (rand() < density) {
        this.state[i] = 1 + Math.floor(rand() * (this.numStates - 1));
      } else {
        this.state[i] = 0;
      }
    }
    this.generation = 0;
  }

  // Seed only the origin tile (and optionally a small neighborhood) live.
  seedPoint(tileIdx = 0, value = 1) {
    this.clear();
    if (tileIdx >= 0 && tileIdx < this.state.length) {
      this.state[tileIdx] = value % this.numStates;
    }
  }
  // Seed a named shape centered on tileIdx (default origin).
  seedShape(shape, tileIdx = 0) {
    this.clear();
    const tiles = this.lattice.tiles;
    if (tileIdx < 0 || tileIdx >= tiles.length) return;
    const v = 1;
    const nb = tiles[tileIdx].neighbors;
    const nEdges = nb.length;
    switch (shape) {
      case 'single': {
        this.state[tileIdx] = v;
        break;
      }
      case 'pair': {
        this.state[tileIdx] = v;
        for (let k = 0; k < nEdges; k++) {
          if (nb[k] !== null) {
            this.state[nb[k]] = v;
            break;
          }
        }
        break;
      }
      case 'triple': {
        this.state[tileIdx] = v;
        let set = 0;
        for (let k = 0; k < nEdges && set < 2; k++) {
          if (nb[k] !== null) {
            this.state[nb[k]] = v;
            set++;
          }
        }
        break;
      }
      case 'petal': {
        for (let k = 0; k < nEdges; k++) {
          if (nb[k] !== null) this.state[nb[k]] = v;
        }
        break;
      }
      case 'all5': {
        this.state[tileIdx] = v;
        for (let k = 0; k < nEdges; k++) {
          if (nb[k] !== null) this.state[nb[k]] = v;
        }
        break;
      }
      case 'ring': {
        // depth-2 shell: neighbors-of-neighbors that aren't origin
        // or origin's direct neighbors
        const direct = new Set();
        direct.add(tileIdx);
        for (let k = 0; k < nEdges; k++) {
          if (nb[k] !== null) direct.add(nb[k]);
        }
        for (let k = 0; k < nEdges; k++) {
          const ni = nb[k];
          if (ni === null) continue;
          const nb2 = tiles[ni].neighbors;
          for (let j = 0; j < nb2.length; j++) {
            const nj = nb2[j];
            if (nj !== null && !direct.has(nj)) {
              this.state[nj] = v;
            }
          }
        }
        break;
      }
      case 'line': {
        // Walk in one direction (always edge 0) for n steps.
        this.state[tileIdx] = v;
        let cur = tileIdx;
        for (let step = 0; step < Math.max(nEdges - 1, 4); step++) {
          const next = tiles[cur].neighbors[0];
          if (next === null) break;
          this.state[next] = v;
          cur = next;
        }
        break;
      }
      default: {
        this.state[tileIdx] = v;
      }
    }
  }

  setCell(idx, value) {
    if (idx < 0 || idx >= this.state.length) return;
    this.state[idx] = ((value % this.numStates) + this.numStates) % this.numStates;
  }

  toggleCell(idx) {
    if (idx < 0 || idx >= this.state.length) return;
    // For binary-feeling editing: cycle through states 0..numStates-1.
    this.state[idx] = (this.state[idx] + 1) % this.numStates;
  }

  step() {
    const tiles = this.lattice.tiles;
    const s = this.state;
    const out = this.next;
    const ns = this.numStates;
    // Number of edges/neighbours varies by polygon type.
    const nEdges = (i) => tiles[i].neighbors.length;
    switch (this.family) {
      case 'life': {
        const { birth, survive } = this.lifeRule;
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let live = 0;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null && s[ni] !== 0) live++;
          }
          if (s[i] !== 0) {
            out[i] = survive.has(live) ? 1 : 0;
          } else {
            out[i] = birth.has(live) ? 1 : 0;
          }
        }
        break;
      }
      case 'parity': {
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let sum = 0;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null) sum += s[ni];
          }
          out[i] = sum % ns;
        }
        break;
      }
      case 'cyclic': {
        const thr = this.cyclicThreshold;
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          const target = (s[i] + 1) % ns;
          let count = 0;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null && s[ni] === target) count++;
          }
          out[i] = count >= thr ? target : s[i];
        }
        break;
      }
      case 'majority': {
        const counts = new Int32Array(ns);
        for (let i = 0; i < tiles.length; i++) {
          counts.fill(0);
          counts[s[i]]++;
          const nbrs = tiles[i].neighbors;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null) counts[s[ni]]++;
          }
          // pick max; ties favor current state
          let best = s[i];
          let bestC = counts[s[i]];
          for (let v = 0; v < ns; v++) {
            if (counts[v] > bestC) {
              bestC = counts[v];
              best = v;
            }
          }
          out[i] = best;
        }
        break;
      }
      case 'threshold': {
        const thr = this.threshold;
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let live = 0;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null && s[ni] !== 0) live++;
          }
          out[i] = live >= thr ? 1 : 0;
        }
        break;
      }
      case 'diffusion': {
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let sum = s[i];
          let cnt = 1;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null) {
              sum += s[ni];
              cnt++;
            }
          }
          out[i] = Math.round(sum / cnt) % ns;
        }
        break;
      }
      case 'sheetflow': {
        // Anyonic-style rule: cell adopts (self + sum of neighbor
        // sheet indices) mod numStates. Couples CA dynamics to the
        // multi-sheet topology of the lattice.
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let sum = s[i];
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null && s[ni] !== 0) {
              sum += tiles[ni].sheet;
            }
          }
          out[i] = ((sum % ns) + ns) % ns;
        }
        break;
      }
      case 'xor': {
        for (let i = 0; i < tiles.length; i++) {
          const nbrs = tiles[i].neighbors;
          let acc = 0;
          for (let k = 0; k < nbrs.length; k++) {
            const ni = nbrs[k];
            if (ni !== null) acc ^= s[ni];
          }
          out[i] = acc % ns;
        }
        break;
      }
      default: {
        for (let i = 0; i < tiles.length; i++) out[i] = s[i];
      }
    }
    // swap
    const tmp = this.state;
    this.state = out;
    this.next = tmp;
    this.generation++;
  }

  // Diagnostics
  population() {
    let p = 0;
    for (let i = 0; i < this.state.length; i++) if (this.state[i] !== 0) p++;
    return p;
  }

  stateCounts() {
    const counts = new Array(this.numStates).fill(0);
    for (let i = 0; i < this.state.length; i++) counts[this.state[i]]++;
    return counts;
  }

  // Sheet-resolved live populations (handy for studying holonomy/transport).
  populationBySheet() {
    const by = new Map();
    const tiles = this.lattice.tiles;
    for (let i = 0; i < tiles.length; i++) {
      if (this.state[i] === 0) continue;
      const sh = tiles[i].sheet;
      by.set(sh, (by.get(sh) || 0) + 1);
    }
    return by;
  }
}
