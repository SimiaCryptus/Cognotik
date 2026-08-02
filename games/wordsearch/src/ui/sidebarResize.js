// Make the <aside> sidebar resizable via a draggable handle. The chosen
// width is persisted to localStorage so it survives reloads.

const STORAGE_KEY = 'mws-sidebar-width';
const MIN_WIDTH = 220;
const MAX_WIDTH = 900;

/**
 * Wire a draggable resize handle onto the sidebar.
 * @param {Document|HTMLElement} root
 */
export function wireSidebarResize(root = document) {
  const aside = root.querySelector('aside');
  if (!aside) return;

  // Restore any persisted width.
  try {
    const saved = parseFloat(localStorage.getItem(STORAGE_KEY));
    if (Number.isFinite(saved) && saved >= MIN_WIDTH) {
      aside.style.width = `${saved}px`;
    }
  } catch {
    /* ignore storage errors */
  }

  // Create the handle if it doesn't already exist.
  let handle = aside.querySelector('.sidebar-resize-handle');
  if (!handle) {
    handle = document.createElement('div');
    handle.className = 'sidebar-resize-handle';
    handle.title = 'Drag to resize';
    aside.appendChild(handle);
  }

  let startX = 0;
  let startWidth = 0;

  const onMove = (e) => {
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    let next = startWidth + (clientX - startX);
    next = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, next));
    aside.style.width = `${next}px`;
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.removeEventListener('touchmove', onMove);
    document.removeEventListener('touchend', onUp);
    document.body.classList.remove('resizing-sidebar');
    try {
      localStorage.setItem(STORAGE_KEY, String(parseFloat(aside.style.width) || MIN_WIDTH));
    } catch {
      /* ignore */
    }
    // Notify listeners (e.g. grid re-fit) that layout changed.
    window.dispatchEvent(new Event('resize'));
  };

  const onDown = (e) => {
    e.preventDefault();
    startX = e.touches ? e.touches[0].clientX : e.clientX;
    startWidth = aside.getBoundingClientRect().width;
    document.body.classList.add('resizing-sidebar');
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onUp);
  };

  handle.addEventListener('mousedown', onDown);
  handle.addEventListener('touchstart', onDown, { passive: false });
}
