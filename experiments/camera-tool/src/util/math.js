// Small statistics helpers.

export function clamp(v, lo, hi) {
  return v < lo ? lo : v > hi ? hi : v;
}

// Online (Welford) accumulation helpers operate on raw sums in the
// accumulator, but these are handy for ad-hoc stats.

export function mean(arr) {
  if (!arr.length) return 0;
  let s = 0;
  for (let i = 0; i < arr.length; i++) s += arr[i];
  return s / arr.length;
}

export function median(arr) {
  if (!arr.length) return 0;
  const copy = Array.prototype.slice.call(arr).sort((a, b) => a - b);
  const mid = copy.length >> 1;
  return copy.length % 2 ? copy[mid] : (copy[mid - 1] + copy[mid]) / 2;
}

export function variance(arr, m) {
  if (arr.length < 2) return 0;
  const mu = m === undefined ? mean(arr) : m;
  let s = 0;
  for (let i = 0; i < arr.length; i++) {
    const d = arr[i] - mu;
    s += d * d;
  }
  return s / (arr.length - 1);
}

// Variance from accumulated sum / sumSq / count (population-ish, n-1).
export function varianceFromSums(sum, sumSq, n) {
  if (n < 2) return 0;
  const m = sum / n;
  const v = (sumSq - n * m * m) / (n - 1);
  return v < 0 ? 0 : v;
}
