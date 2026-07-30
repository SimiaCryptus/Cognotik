import {el} from '../util/dom.js';
  import {bus, Events} from '../core/bus.js';
  import {cycleTheme, getTheme, THEMES} from '../config/theme.js';

  /**
   * reverse-spec §1.2 step 3 — mount the shell DOM.
   *
   * Also owns the purely-decorative HUD chrome: the fixed backdrop layers and the
   * telemetry bar. Both are driven exclusively by core/bus.js events, so the shell
   * never reaches into the store or the transport.
   */

  const CHROME_STATES = Object.freeze({
      offline: 'link lost',
      online: 'link online',
      busy: 'processing'
  });

  function chip(label, value, {as = 'div', title, onClick} = {}) {
      const valueEl = el('span', {class: 'hud-chip-value', text: value});
      const node = el(as, {
          class: `hud-chip hud-${label.toLowerCase()}`,
          title,
          type: as === 'button' ? 'button' : null,
          onClick
      }, [el('span', {class: 'hud-chip-label', text: label}), valueEl]);
      return {node, set: (next) => (valueEl.textContent = next)};
  }

  export function mountShell(root, {sessionId = '', version = ''} = {}) {
      root.classList.add('app');

      /* --- backdrop (fixed, behind #root, pointer-transparent) -------------- */
      if (!document.querySelector('.hud-backdrop')) {
          document.body.prepend(
              el('div', {class: 'hud-backdrop', 'aria-hidden': 'true'}, [
                  el('div', {class: 'hud-grid'}),
                  el('div', {class: 'hud-aurora'}),
                  el('div', {class: 'hud-scanlines'})
              ])
          );
      }

      /* --- HUD bar ---------------------------------------------------------- */
      const brandText = el('span', {class: 'hud-brand-text', text: 'Cognotik'});
      const brand = el('div', {class: 'hud-brand'}, [
          el('span', {class: 'hud-brand-mark', text: '◈', 'aria-hidden': 'true'}),
          brandText,
          version ? el('span', {class: 'hud-brand-version', text: `v${version}`}) : null
      ]);

      const session = chip('Session', sessionId || '—', {
          as: 'button',
          title: 'Click to copy the session id',
          onClick: () => copy(sessionId, session)
      });
      const msgs = chip('Msgs', '0', {title: 'Rendered messages'});

      const led = el('span', {class: 'hud-led', 'aria-hidden': 'true'});
      const statusText = el('span', {class: 'hud-status-text', text: CHROME_STATES.offline});
      const status = el('div', {class: 'hud-status offline', role: 'status'}, [led, statusText]);

      const themeButton = el('button', {
          class: 'hud-button hud-theme',
          type: 'button',
          title: 'Cycle theme',
          text: themeLabel(),
          onClick: () => cycleTheme()
      });

      const bar = el('header', {class: 'hud-bar', role: 'banner'}, [
          brand,
          el('div', {class: 'hud-telemetry'}, [session.node, msgs.node]),
          status,
          el('div', {class: 'hud-actions'}, [themeButton])
      ]);

      /* --- content ---------------------------------------------------------- */
      const list = el('div', {class: 'message-list', id: 'message-list', role: 'log'});
      const scroller = el('div', {class: 'message-list-container', id: 'message-list-container'}, [list]);
      const composerHost = el('div', {class: 'chat-input-container', id: 'chat-input-container'});
      const modalRoot = el('div', {class: 'modal-root', id: 'modal-root'});

      root.appendChild(bar);
      root.appendChild(scroller);
      root.appendChild(composerHost);
      root.appendChild(modalRoot);

      /* --- telemetry wiring ------------------------------------------------- */
      let connected = false;
      let pending = 0;

      const paintStatus = () => {
          const state = !connected ? 'offline' : pending > 0 ? 'busy' : 'online';
          status.className = `hud-status ${state}`;
          statusText.textContent =
              state === 'busy' ? `${CHROME_STATES.busy} ×${pending}` : CHROME_STATES[state];
      };

      bus.on(Events.CONNECTION_CHANGED, (event) => {
          connected = !!event.detail;
          paintStatus();
      });
      bus.on(Events.PENDING_CHANGED, (event) => {
          pending = event.detail?.pending || 0;
          paintStatus();
      });
      bus.on(Events.MESSAGES_RENDERED, (event) => msgs.set(String(event.detail?.count ?? 0)));
      bus.on(Events.THEME_CHANGED, () => (themeButton.textContent = themeLabel()));
      bus.on(Events.CONFIG_LOADED, (event) => {
          const name = event.detail?.applicationName;
          if (name) brandText.textContent = name;
      });
      paintStatus();

      return {root, scroller, list, composerHost, modalRoot, hud: {bar, status, setStatus: paintStatus}};
  }

  function themeLabel() {
      return THEMES[getTheme()]?.label || getTheme();
  }

  function copy(value, target) {
      if (!value) return;
      const restore = () => target.set(value);
      Promise.resolve(navigator.clipboard?.writeText(value)).then(
          () => {
              target.set('copied ✓');
              setTimeout(restore, 1200);
          },
          restore
      );
  }