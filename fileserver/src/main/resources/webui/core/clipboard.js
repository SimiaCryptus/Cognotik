/**
   * Clipboard writes that actually land (note #2).
   *
   * `navigator.clipboard.writeText` rejects when the document is not focused —
   * which is exactly the state right after a context menu closes and restores
   * focus — and is absent altogether on non-secure origins. So: try the modern
   * API, then fall back to the legacy `execCommand('copy')` dance.
   */
  export async function copyText(text) {
      const value = String(text ?? '');
      if (!value) return false;
      try {
          if (navigator.clipboard?.writeText) {
              window.focus?.();
              await navigator.clipboard.writeText(value);
              return true;
          }
      } catch (e) {
          /* NotAllowedError / document-not-focused: fall through. */
      }
      return legacyCopy(value);
  }
  
  function legacyCopy(value) {
      const area = document.createElement('textarea');
      area.value = value;
      area.setAttribute('readonly', '');
      area.setAttribute('aria-hidden', 'true');
      area.style.position = 'fixed';
      area.style.top = '-1000px';
      area.style.opacity = '0';
      document.body.appendChild(area);
      const previous = document.activeElement;
      let ok = false;
      try {
          area.focus();
          area.select();
          area.setSelectionRange(0, value.length);
          ok = document.execCommand('copy');
      } catch (e) {
          ok = false;
      }
      area.remove();
      if (previous && previous.isConnected) previous.focus?.();
      return ok;
  }