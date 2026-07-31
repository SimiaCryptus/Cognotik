/**
   * The user- and SEO-friendly URL for a file: `/files/root/dir/page.html`.
   *
   * The v2 endpoint (`/file?path=…`) answers application/octet-stream for
   * programmatic readers, so a browser navigating to it downloads the file
   * instead of rendering it (and an <img> may refuse it under nosniff). The
   * classic servlet serves the same bytes under a clean path with the detected
   * content type, which is what previews, <img> tags and shareable links want.
   */
  import {store} from './store.js';
  import {fs} from './fsclient.js';
  import {segments} from './paths.js';

  export function classicBase() {
      const state = store.get();
      /* Derive it from the API base when it was never recorded: silently falling
         back to `/file?path=…` is what turned "open in new tab" into a download. */
      const base = state.classicBase || String(state.base || '').replace(/\/\.fsapi\/v\d+\/?$/, '');
      return String(base || '').replace(/\/$/, '');
  }

  export function publicUrl(path) {
      const base = classicBase();
      if (!base) return fs.fileUrl(path);
      /* Encode per segment so '/' stays a separator and spaces still resolve. */
      const rel = segments(path).map(encodeURIComponent).join('/');
      return rel ? `${base}/${rel}` : `${base}/`;
  }