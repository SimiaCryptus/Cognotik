// Render a grid to the DOM and to PNG.
/**
 * Compute the optimal cell size (in px) so the rendered grid fits within the
 * available space of its container's parent region.
 * @param {HTMLElement} container the #grid host element
 * @param {import('../grid/Grid.js').Grid} grid
 * @returns {number} cell size in px
 */
export function computeCellSize(container, grid) {
  const MIN = 16;
  const MAX = 44;

  const SPACING = 4; // matches border-spacing in CSS
  // Determine the region we can paint into. Prefer the <main> region (the
  // container's offset parent) so we account for surrounding layout.
  const region = container.parentElement || container;
  const regionRect = region.getBoundingClientRect();
  // Subtract the grid host's own padding (1.5rem each side ~= 24px) plus a
  // little breathing room so we never overflow.
  const cs = window.getComputedStyle(container);
  const padX = parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight) || 48;
  const padY = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom) || 48;
  // Available width is bounded by the region width; available height is
  // bounded by the viewport height below the region's top edge. Both account
  // for the grid host padding plus a small safety margin.
  const regionW = regionRect.width || window.innerWidth;
  const regionH = regionRect.height || window.innerHeight - regionRect.top;
  const availW = Math.max(0, regionW - padX - 8);
  const availH = Math.max(0, Math.min(regionH, window.innerHeight - regionRect.top) - padY - 24);

  const totalSpacingX = SPACING * (grid.width + 1);
  const totalSpacingY = SPACING * (grid.height + 1);
  const byW = (availW - totalSpacingX) / grid.width;
  const byH = (availH - totalSpacingY) / grid.height;
  let size = Math.floor(Math.min(byW, byH));
  if (!Number.isFinite(size)) size = MAX;
  return Math.max(MIN, Math.min(MAX, size));
}

/** Apply a computed cell size to a rendered table via CSS variables. */
function applyCellSize(table, size, opts = {}) {
  const { fontScale = 1, fontFamily } = opts;
  table.style.setProperty('--cell-size', `${size}px`);
  const baseFont = Math.max(10, Math.floor(size * 0.5 * fontScale));
  table.style.setProperty('--cell-font', `${baseFont}px`);
  if (fontFamily) table.style.setProperty('--cell-font-family', fontFamily);
}
/**
 * Update only the font-related CSS variables on an already-rendered grid,
 * without re-rendering / regenerating the puzzle. The cell pixel size is
 * preserved (read back from the existing --cell-size variable) so only the
 * glyph size and font family change.
 * @param {HTMLElement} container the #grid host element
 * @param {object} [opts]
 * @param {number} [opts.fontScale] multiplier for glyph size
 * @param {string} [opts.fontFamily] CSS font family
 */
export function updateGridFont(container, opts = {}) {
  const { fontScale = 1, fontFamily } = opts;
  const table = container.querySelector('table.ws-grid');
  if (!table) return;
  const sizeRaw = parseFloat(table.style.getPropertyValue('--cell-size'));
  const size = Number.isFinite(sizeRaw) && sizeRaw > 0 ? sizeRaw : 32;
  const baseFont = Math.max(10, Math.floor(size * 0.5 * fontScale));
  table.style.setProperty('--cell-font', `${baseFont}px`);
  if (fontFamily) table.style.setProperty('--cell-font-family', fontFamily);
}

/**
 * Render grid into a container as a table of cells.
 * @param {HTMLElement} container
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {object} [opts]
 * @param {boolean} [opts.debug] highlight locked cells
 */
export function renderGrid(container, grid, opts = {}) {
  const { debug = false, lattice = 'square', fontScale = 1, fontFamily } = opts;
  // Measure available space *before* clearing so the region layout is stable.
  const cellSize = computeCellSize(container, grid);
  container.innerHTML = '';
  const table = document.createElement('table');
  table.className = `ws-grid ws-lattice-${lattice}`;
  applyCellSize(table, cellSize, { fontScale, fontFamily });
  for (let y = 0; y < grid.height; y++) {
    const tr = document.createElement('tr');
    if (lattice === 'hex' || lattice === 'triangular') {
      tr.classList.toggle('odd-row', (y & 1) === 1);
    }
    for (let x = 0; x < grid.width; x++) {
      const td = document.createElement('td');
      td.textContent = (grid.get(x, y) || '').toUpperCase();
      if (debug && grid.isLocked(x, y)) td.classList.add('locked');
      tr.appendChild(td);
    }
    table.appendChild(tr);
  }
  container.appendChild(table);
}

/**
 * Export grid to a PNG data URL via canvas.
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {number} [cell] cell size in px
 * @param {object} [opts]
 * @param {number} [opts.fontScale] multiplier for glyph size
 * @param {string} [opts.fontFamily] CSS font family
 * @returns {string} data URL
 */
export function gridToPNG(grid, cell = 32, opts = {}) {
  const { fontScale = 1, fontFamily = 'monospace' } = opts;
  const canvas = document.createElement('canvas');
  canvas.width = grid.width * cell;
  canvas.height = grid.height * cell;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#111111';
  ctx.font = `${Math.floor(cell * 0.6 * fontScale)}px ${fontFamily}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let y = 0; y < grid.height; y++) {
    for (let x = 0; x < grid.width; x++) {
      const ch = (grid.get(x, y) || '').toUpperCase();
      ctx.fillText(ch, x * cell + cell / 2, y * cell + cell / 2);
    }
  }
  return canvas.toDataURL('image/png');
}

/** Export grid as plain text. */
export function gridToText(grid) {
  return grid.toStringGrid().toUpperCase().replace(/\./g, ' ');
}
/**
 * Render an interactive grid for play mode. Each cell is a <td> with
 * data-x / data-y attributes so callers can wire selection handlers.
 * @param {HTMLElement} container
 * @param {import('../grid/Grid.js').Grid} grid
 * @returns {HTMLTableElement}
 */
export function renderInteractiveGrid(container, grid, opts = {}) {
  const { lattice = 'square', fontScale = 1, fontFamily } = opts;
  // Measure available space *before* clearing so the region layout is stable.
  const cellSize = computeCellSize(container, grid);
  container.innerHTML = '';
  const table = document.createElement('table');
  table.className = `ws-grid ws-grid-play ws-lattice-${lattice}`;
  applyCellSize(table, cellSize, { fontScale, fontFamily });
  for (let y = 0; y < grid.height; y++) {
    const tr = document.createElement('tr');
    if (lattice === 'hex' || lattice === 'triangular') {
      tr.classList.toggle('odd-row', (y & 1) === 1);
    }
    for (let x = 0; x < grid.width; x++) {
      const td = document.createElement('td');
      td.textContent = (grid.get(x, y) || '').toUpperCase();
      td.dataset.x = String(x);
      td.dataset.y = String(y);
      tr.appendChild(td);
    }
    table.appendChild(tr);
  }
  container.appendChild(table);
  return table;
}
/** Get the <td> element for a coordinate within a rendered table. */
export function cellAt(table, x, y) {
  const row = table.rows[y];
  return row ? row.cells[x] : null;
}
