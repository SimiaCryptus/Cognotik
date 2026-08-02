// Lightweight 2D FFT (power-of-two) and 3D surface rendering of the
// log-magnitude spectrum for the irrational lattice field.

// In-place iterative radix-2 Cooley-Tukey FFT on real/imag arrays.
function fft1d(re, im, inverse) {
  const n = re.length;
  // Bit reversal permutation.
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      const tr = re[i];
      re[i] = re[j];
      re[j] = tr;
      const ti = im[i];
      im[i] = im[j];
      im[j] = ti;
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = ((inverse ? 2 : -2) * Math.PI) / len;
    const wpr = Math.cos(ang);
    const wpi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let wr = 1,
        wi = 0;
      for (let k = 0; k < len / 2; k++) {
        const a = i + k;
        const b = i + k + len / 2;
        const tr = wr * re[b] - wi * im[b];
        const ti = wr * im[b] + wi * re[b];
        re[b] = re[a] - tr;
        im[b] = im[a] - ti;
        re[a] += tr;
        im[a] += ti;
        const nwr = wr * wpr - wi * wpi;
        wi = wr * wpi + wi * wpr;
        wr = nwr;
      }
    }
  }
}

function largestPow2LE(n) {
  let p = 1;
  while (p * 2 <= n) p *= 2;
  return p;
}

// Compute the centered log-magnitude 2D FFT of a square field (Float32Array,
// length width*height). Downsamples to a power-of-two grid (max `maxN`).
// Returns { mag: Float32Array(N*N), N, min, max }.
export function computeFFT2D(data, width, maxN = 64, height = width) {
  const minDim = Math.min(width, height);
  let N = largestPow2LE(Math.min(minDim, maxN));
  const re = new Float64Array(N * N);
  const im = new Float64Array(N * N);

  // Sample/average the source field down to N x N.
  const scaleX = width / N;
  const scaleY = height / N;
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      // Nearest-block average for a smoother spectrum.
      const sx = Math.min(width - 1, Math.floor(x * scaleX));
      const sy = Math.min(height - 1, Math.floor(y * scaleY));
      re[y * N + x] = data[sy * width + sx] || 0;
    }
  }

  // Rows.
  const rowRe = new Float64Array(N);
  const rowIm = new Float64Array(N);
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      rowRe[x] = re[y * N + x];
      rowIm[x] = im[y * N + x];
    }
    fft1d(rowRe, rowIm, false);
    for (let x = 0; x < N; x++) {
      re[y * N + x] = rowRe[x];
      im[y * N + x] = rowIm[x];
    }
  }
  // Columns.
  const colRe = new Float64Array(N);
  const colIm = new Float64Array(N);
  for (let x = 0; x < N; x++) {
    for (let y = 0; y < N; y++) {
      colRe[y] = re[y * N + x];
      colIm[y] = im[y * N + x];
    }
    fft1d(colRe, colIm, false);
    for (let y = 0; y < N; y++) {
      re[y * N + x] = colRe[y];
      im[y * N + x] = colIm[y];
    }
  }

  // Log magnitude, fftshift to center the DC component.
  const mag = new Float32Array(N * N);
  let min = Infinity,
    max = -Infinity;
  const half = N / 2;
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const sx = (x + half) % N;
      const sy = (y + half) % N;
      const idx = y * N + x;
      const m = Math.log1p(Math.hypot(re[idx], im[idx]));
      mag[sy * N + sx] = m;
      if (m < min) min = m;
      if (m > max) max = m;
    }
  }
  return { mag, N, min, max };
}

// Render the FFT log-magnitude as a 3D surface (isometric/oblique projection).
// opts: { rot (deg), tilt (deg), heightScale }.
export function renderFFT3D(canvas, fft, opts = {}) {
  const ctx = canvas.getContext('2d');
  const W = canvas.width,
    H = canvas.height;
  ctx.clearRect(0, 0, W, H);
  if (!fft) return;

  const { mag, N, min, max } = fft;
  const range = max - min || 1;
  const rot = ((opts.rot ?? 35) * Math.PI) / 180;
  const tilt = ((opts.tilt ?? 55) * Math.PI) / 180;
  const heightScale = opts.heightScale ?? 1;

  const cosR = Math.cos(rot),
    sinR = Math.sin(rot);
  const cosT = Math.cos(tilt),
    sinT = Math.sin(tilt);

  // Map grid coords -> screen. We compute then fit to canvas.
  const cellH = Math.min(W, H) * 0.32 * heightScale;
  const project = (gx, gy, gz) => {
    // Normalize grid to [-1,1].
    const nx = (gx / (N - 1)) * 2 - 1;
    const ny = (gy / (N - 1)) * 2 - 1;
    // Rotate around vertical axis.
    const rx = nx * cosR - ny * sinR;
    const ry = nx * sinR + ny * cosR;
    // Oblique tilt projection.
    const sx = rx;
    const sy = ry * cosT - gz * sinT;
    return [sx, sy];
  };

  // First pass: find bounds for auto-fit.
  let minSx = Infinity,
    maxSx = -Infinity,
    minSy = Infinity,
    maxSy = -Infinity;
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const z = ((mag[y * N + x] - min) / range) * (cellH / (Math.min(W, H) * 0.5));
      const [sx, sy] = project(x, y, z);
      if (sx < minSx) minSx = sx;
      if (sx > maxSx) maxSx = sx;
      if (sy < minSy) minSy = sy;
      if (sy > maxSy) maxSy = sy;
    }
  }
  const pad = 14;
  const spanX = maxSx - minSx || 1;
  const spanY = maxSy - minSy || 1;
  const s = Math.min((W - 2 * pad) / spanX, (H - 2 * pad) / spanY);
  const ox = pad - minSx * s;
  const oy = pad - minSy * s;

  const toScreen = (gx, gy, z) => {
    const [sx, sy] = project(gx, gy, z);
    return [sx * s + ox, sy * s + oy];
  };
  const zAt = (x, y) => ((mag[y * N + x] - min) / range) * (cellH / (Math.min(W, H) * 0.5));

  // Painter's algorithm: draw quads from back to front.
  // "Back" = larger projected depth (ry after rotation). We approximate by
  // iterating in an order based on rotation quadrant.
  const yOrder = sinR + cosR >= 0 ? 1 : -1;
  const ys = [];
  for (let y = 0; y < N - 1; y++) ys.push(y);
  // Order rows by their average screen-y so nearer ones paint last.
  ys.sort((a, b) => {
    const za = zAt(0, a),
      zb = zAt(0, b);
    const [, sya] = project(N / 2, a, za);
    const [, syb] = project(N / 2, b, zb);
    return sya - syb;
  });

  for (const y of ys) {
    for (let x = 0; x < N - 1; x++) {
      const z00 = zAt(x, y);
      const z10 = zAt(x + 1, y);
      const z11 = zAt(x + 1, y + 1);
      const z01 = zAt(x, y + 1);
      const p00 = toScreen(x, y, z00);
      const p10 = toScreen(x + 1, y, z10);
      const p11 = toScreen(x + 1, y + 1, z11);
      const p01 = toScreen(x, y + 1, z01);

      const t = (z00 + z10 + z11 + z01) / 4 / (cellH / (Math.min(W, H) * 0.5) || 1);
      const [r, g, b] = spectrumColor(Math.max(0, Math.min(1, t)));
      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.strokeStyle = 'rgba(0,0,0,0.25)';
      ctx.lineWidth = 0.5;
      ctx.beginPath();
      ctx.moveTo(p00[0], p00[1]);
      ctx.lineTo(p10[0], p10[1]);
      ctx.lineTo(p11[0], p11[1]);
      ctx.lineTo(p01[0], p01[1]);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    }
  }
}

// Small viridis-ish colormap for the 3D surface.
function spectrumColor(t) {
  // Guard against NaN / out-of-range inputs which would index past the
  // stops array and yield `undefined`.
  if (!isFinite(t)) t = 0;
  t = Math.max(0, Math.min(1, t));
  const stops = [
    [68, 1, 84],
    [59, 82, 139],
    [33, 145, 140],
    [94, 201, 98],
    [253, 231, 37],
  ];
  const n = stops.length - 1;
  const idx = t * n;
  const i0 = Math.min(n, Math.max(0, Math.floor(idx)));
  const i1 = Math.min(i0 + 1, n);
  const f = idx - i0;
  const a = stops[i0],
    b = stops[i1];
  return [
    Math.round(a[0] + (b[0] - a[0]) * f),
    Math.round(a[1] + (b[1] - a[1]) * f),
    Math.round(a[2] + (b[2] - a[2]) * f),
  ];
}
