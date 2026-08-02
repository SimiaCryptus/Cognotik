// Word enumeration for length-N strings over K symbols.

export function enumerateOrdered(K, N) {
  const out = [];
  const w = new Array(N).fill(0);
  while (true) {
    out.push(w.slice());
    let i = N - 1;
    while (i >= 0) {
      w[i]++;
      if (w[i] < K) break;
      w[i] = 0;
      i--;
    }
    if (i < 0) break;
  }
  return out;
}

export function enumerateCommutative(K, N) {
  const out = [];
  const counts = new Array(K).fill(0);
  function rec(k, remaining) {
    if (k === K - 1) {
      counts[k] = remaining;
      const w = [];
      for (let j = 0; j < K; j++) for (let t = 0; t < counts[j]; t++) w.push(j);
      out.push(w);
      return;
    }
    for (let n = 0; n <= remaining; n++) {
      counts[k] = n;
      rec(k + 1, remaining - n);
    }
  }
  rec(0, N);
  return out;
}

export function enumerate(K, N, mode) {
  if (mode === 'ordered') return enumerateOrdered(K, N);
  return enumerateCommutative(K, N);
}
