// Coupling-matrix heatmap and eigenvalue spectrum rendering.

/** Blue-white-red diverging colormap for value in [-1,1]. */
function divergeColor(v) {
    const t = Math.max(-1, Math.min(1, v));
    if (t >= 0) {
        const g = Math.round(255 * (1 - t));
        return `rgb(255,${g},${g})`;
    }
    const g = Math.round(255 * (1 + t));
    return `rgb(${g},${g},255)`;
}

/** Render normalized coupling matrix C (values in ~[-1,1]) as a heatmap. */
export function renderHeatmap(canvas, C) {
    const ctx = canvas.getContext('2d');
    const n = C.length;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (n === 0) return;
    const cell = Math.floor(Math.min(canvas.width, canvas.height) / n);
    const size = cell * n;
    const ox = (canvas.width - size) / 2;
    const oy = (canvas.height - size) / 2;
    for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
            ctx.fillStyle = divergeColor(C[i][j]);
            ctx.fillRect(ox + j * cell, oy + i * cell, cell - 1, cell - 1);
        }
    }
    ctx.strokeStyle = '#444';
    ctx.strokeRect(ox, oy, size, size);
}

/** Render eigenvalue spectrum (ω values). Negative → unstable (red). */
export function renderSpectrum(canvas, omega) {
    const ctx = canvas.getContext('2d');
    const n = omega.length;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (n === 0) return;
    let max = 1e-9;
    for (const w of omega) max = Math.max(max, Math.abs(w));
    const midY = canvas.height / 2;
    const barW = canvas.width / n;
    ctx.strokeStyle = '#444';
    ctx.beginPath();
    ctx.moveTo(0, midY);
    ctx.lineTo(canvas.width, midY);
    ctx.stroke();
    for (let i = 0; i < n; i++) {
        const w = omega[i];
        const hgt = (w / max) * (canvas.height / 2 - 6);
        ctx.fillStyle = w < 0 ? '#e55' : '#5c6';
        ctx.fillRect(i * barW + 2, midY - Math.max(hgt, 0), barW - 4, Math.abs(hgt));
        if (hgt < 0) ctx.fillRect(i * barW + 2, midY, barW - 4, -hgt);
    }
}
