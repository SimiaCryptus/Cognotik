// Rational-arithmetic helpers for the irrational-lattice lab.
//
// The view grid is controlled by exact rationals. Zoom granularity is a
// ratio p/q; grid size is an integer. The continued-fraction routine below
// finds the best rational approximation p/q to a real number x subject to
// a bound on the denominator (default 100).

/**
 * Best rational approximation p/q to x with q <= maxDen, via continued
 * fractions (Stern-Brocot / convergents). Returns { num, den, value }.
 */
export function rationalApprox(x, maxDen = 100) {
  if (!isFinite(x)) return { num: 1, den: 1, value: 1 };
  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x);

  // Continued fraction expansion, tracking convergents.
  let h0 = 0,
    h1 = 1; // numerator convergents
  let k0 = 1,
    k1 = 0; // denominator convergents
  let a = Math.floor(x);
  let frac = x - a;
  let bestNum = a,
    bestDen = 1;

  for (let i = 0; i < 64; i++) {
    const h2 = a * h1 + h0;
    const k2 = a * k1 + k0;
    if (k2 > maxDen) break;
    bestNum = h2;
    bestDen = k2;
    h0 = h1;
    h1 = h2;
    k0 = k1;
    k1 = k2;
    if (frac < 1e-12) break;
    const inv = 1 / frac;
    a = Math.floor(inv);
    frac = inv - a;
  }

  // Guard against degenerate denominator.
  if (bestDen < 1) bestDen = 1;
  return {
    num: sign * bestNum,
    den: bestDen,
    value: (sign * bestNum) / bestDen,
  };
}

/** Euclidean gcd for reducing fractions. */
export function gcd(a, b) {
  a = Math.abs(a);
  b = Math.abs(b);
  while (b) {
    [a, b] = [b, a % b];
  }
  return a || 1;
}

/** Reduce a fraction to lowest terms; keep sign on the numerator. */
export function reduce(num, den) {
  if (den === 0) den = 1;
  const s = den < 0 ? -1 : 1;
  num *= s;
  den *= s;
  const g = gcd(num, den);
  return { num: num / g, den: den / g };
}

/**
 * Wire up the rational view controls.
 *
 * @param {object} opts
 * @param {function} opts.onChange - called with { size, zoomNum, zoomDen, zoom }
 *                                   whenever any control changes.
 * @param {number} [opts.maxDen=100] - denominator bound for slider->fraction.
 * @returns {object} a small API for reading/setting state.
 */
export function wireRationalControls({ onChange, onZoomChange, maxDen = 100 } = {}) {
  const $ = (id) => document.getElementById(id);

  const sizeValue = $('size'); // hidden mirror read by main.js
  const sizeInput = $('sizeInput'); // visible slider control
  const sizeMinus = $('sizeMinus');
  const sizePlus = $('sizePlus');
  const sizeOut = $('sizeOut');

  const zoomSlider = $('zoomStep');
  const zoomNum = $('zoomNum');
  const zoomDen = $('zoomDen');
  const zoomNumMinus = $('zoomNumMinus');
  const zoomNumPlus = $('zoomNumPlus');
  const zoomDenMinus = $('zoomDenMinus');
  const zoomDenPlus = $('zoomDenPlus');
  const zoomOut = $('zoomStepOut');

  function clampInt(v, lo, hi) {
    v = Math.round(Number(v));
    if (!isFinite(v)) v = lo;
    if (v < lo) v = lo;
    if (hi != null && v > hi) v = hi;
    return v;
  }

  function readState() {
    const size = clampInt(sizeInput.value, 1, 4096);
    const num = clampInt(zoomNum.value, 1, 100000);
    const den = clampInt(zoomDen.value, 1, 100000);
    return { size, zoomNum: num, zoomDen: den, zoom: num / den };
  }

  function refreshDisplays() {
    const { size, zoomNum: num, zoomDen: den, zoom } = readState();
    // Mirror the visible slider value into the hidden input main.js reads.
    sizeValue.value = String(size);
    sizeOut.textContent = String(size);
    zoomOut.textContent = `${num} / ${den} = ${zoom.toFixed(5)}`;
  }

  function emit() {
    refreshDisplays();
    if (onChange) onChange(readState());
  }

  // --- Grid size integer stepper ---
  sizeMinus.addEventListener('click', () => {
    sizeInput.value = clampInt(sizeInput.value, 1, 4096) - 1;
    emit();
  });
  sizePlus.addEventListener('click', () => {
    sizeInput.value = clampInt(sizeInput.value, 1, 4096) + 1;
    emit();
  });
  sizeInput.addEventListener('input', () => {
    sizeInput.value = clampInt(sizeInput.value, 1, 4096);
    emit();
  });

  // --- Zoom slider -> continued fraction approximation ---
  zoomSlider.addEventListener('input', () => {
    const x = Number(zoomSlider.value);
    const { num, den } = rationalApprox(x, maxDen);
    zoomNum.value = Math.max(1, Math.abs(num));
    zoomDen.value = Math.max(1, den);
    applyZoom();
    emit();
  });

  // --- Zoom numerator / denominator integer steppers ---
  function bumpNum(delta) {
    zoomNum.value = clampInt(zoomNum.value, 1, 100000) + delta;
    syncSliderFromFraction();
    applyZoom();
    emit();
  }
  function bumpDen(delta) {
    zoomDen.value = clampInt(zoomDen.value, 1, 100000) + delta;
    syncSliderFromFraction();
    applyZoom();
    emit();
  }
  function syncSliderFromFraction() {
    const { zoom } = readState();
    const lo = Number(zoomSlider.min),
      hi = Number(zoomSlider.max);
    // The slider is only a coarse seed. Reflect the fraction's value when
    // it lies within the slider's practical range, but never clamp or
    // override the user's arbitrary numerator/denominator entry.
    if (zoom >= lo && zoom <= hi) {
      zoomSlider.value = String(zoom);
    } else if (zoom > hi) {
      zoomSlider.value = String(hi);
    } else if (zoom < lo) {
      zoomSlider.value = String(lo);
    }
  }
  function applyZoom() {
    if (onZoomChange) onZoomChange(readState());
  }

  zoomNumMinus.addEventListener('click', () => bumpNum(-1));
  zoomNumPlus.addEventListener('click', () => bumpNum(1));
  zoomDenMinus.addEventListener('click', () => bumpDen(-1));
  zoomDenPlus.addEventListener('click', () => bumpDen(1));

  zoomNum.addEventListener('input', () => {
    syncSliderFromFraction();
    applyZoom();
    emit();
  });
  zoomDen.addEventListener('input', () => {
    syncSliderFromFraction();
    applyZoom();
    emit();
  });
  zoomNum.addEventListener('change', () => {
    zoomNum.value = clampInt(zoomNum.value, 1, 100000);
    syncSliderFromFraction();
    applyZoom();
    emit();
  });
  zoomDen.addEventListener('change', () => {
    zoomDen.value = clampInt(zoomDen.value, 1, 100000);
    syncSliderFromFraction();
    applyZoom();
    emit();
  });

  refreshDisplays();

  return {
    getState: readState,
    setSize(v) {
      sizeInput.value = clampInt(v, 1, 4096);
      emit();
    },
    setZoomFraction(num, den) {
      const r = reduce(num, den);
      zoomNum.value = Math.max(1, Math.abs(r.num));
      zoomDen.value = Math.max(1, r.den);
      syncSliderFromFraction();
      emit();
    },
    refresh: refreshDisplays,
  };
}
