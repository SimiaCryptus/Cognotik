// Canvas / ImageData helpers.

// Create a canvas (OffscreenCanvas if available, else DOM canvas).
export function createCanvas(width, height) {
  if (typeof OffscreenCanvas !== 'undefined') {
    return new OffscreenCanvas(width, height);
  }
  const c = document.createElement('canvas');
  c.width = width;
  c.height = height;
  return c;
}

// Grab an ImageData snapshot from a video element at its native resolution.
export function grabFrame(video, canvas) {
  const w = video.videoWidth;
  const h = video.videoHeight;
  if (!w || !h) return null;
  if (canvas.width !== w) canvas.width = w;
  if (canvas.height !== h) canvas.height = h;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(video, 0, 0, w, h);
  return ctx.getImageData(0, 0, w, h);
}

// Convert luminance helper (Rec. 601).
export function luma(r, g, b) {
  return 0.299 * r + 0.587 * g + 0.114 * b;
}

// Render a defect overlay onto a canvas sized to match a display element.
// defects: array of {x,y,type}. scaleX/scaleY map sensor coords to display.
export function drawDefectOverlay(overlayCanvas, defects, srcW, srcH, dispW, dispH) {
  if (overlayCanvas.width !== dispW) overlayCanvas.width = dispW;
  if (overlayCanvas.height !== dispH) overlayCanvas.height = dispH;
  const ctx = overlayCanvas.getContext('2d');
  ctx.clearRect(0, 0, dispW, dispH);
  if (!defects || !defects.length || !srcW || !srcH) return;

  const sx = dispW / srcW;
  const sy = dispH / srcH;
  const colors = {
    dead: 'rgba(255,80,80,0.9)',
    stuck: 'rgba(255,210,60,0.9)',
    hot: 'rgba(255,120,255,0.9)',
    noisy: 'rgba(120,200,255,0.9)',
  };
  // Draw at least 1px markers, scaled up a touch for visibility.
  const size = Math.max(1, Math.min(sx, sy));
  for (let i = 0; i < defects.length; i++) {
    const d = defects[i];
    ctx.fillStyle = colors[d.type] || 'rgba(255,255,255,0.9)';
    ctx.fillRect(d.x * sx, d.y * sy, size, size);
  }
}
