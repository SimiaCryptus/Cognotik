import {h, clear} from '../../core/dom.js';
  import {store} from '../../core/store.js';
  import {persist} from '../../core/persist.js';
  import {tabs} from '../tabs/TabModel.js';
  import {ui} from '../../core/ui.js';
  import {announce} from '../../core/a11y.js';
  import {copyText} from '../../core/clipboard.js';
  import {FsError} from '../../core/errors.js';
  import {basename} from '../../core/paths.js';

  /**
   * Code chat sessions, hosted as ordinary editor tabs (note #4).
   *
   * The previous incarnation lived in the bottom dock and owned a single
   * iframe, so starting a second "Modify files" session silently destroyed the
   * first one. A session is a long-lived document, not a console: it therefore
   * belongs in the main tabbed region, one tab per session, closed and
   * re-ordered like any other tab.
   *
   * All of the intelligence stays on the server: `POST {base}/modify` builds the
   * session over a file selection and answers with its URL, which we host in a
   * sandbox-free iframe (it is our own origin's app) so a pop-up blocker can
   * never lose it.
   */

  let seq = 0;
  /**
   * The theme the agent UI should adopt so that it matches this workspace (#3).
   * The agent app only knows 'light' | 'dark' | 'auto', so the extra
   * high-contrast workspace theme is reported as its nearest equivalent.
   */
  export function currentTheme() {
      const theme = document.documentElement.getAttribute('data-theme')
          || persist.get('theme', 'auto') || 'auto';
      return theme === 'hc' ? 'dark' : theme;
  }
  /**
   * Adds `?theme=` to a session URL without disturbing the `#session` fragment
   * the server handed us (the fragment, not the query, identifies the session).
   */
  export function withTheme(url) {
      try {
          const parsed = new URL(url, location.href);
          parsed.searchParams.set('theme', currentTheme());
          return parsed.toString();
      } catch (e) {
          return url;
      }
  }


  /** Asks the server for a patch-chat session over `paths`; answers its URL. */
  export async function requestChatSession({paths = [], name} = {}) {
      const base = store.get().base;
      if (!base) throw new FsError('ENETWORK', {syscall: 'chat', message: 'no FS API base'});
      const query = new URLSearchParams();
      /* Server paths are root-relative: '/src/a.kt' would read as absolute. */
      for (const path of paths) query.append('path', String(path).replace(/^\/+/, ''));
      if (name) query.set('name', String(name).split('\n')[0].slice(0, 60));
      const search = query.toString();
      const response = await fetch(`${base}/modify${search ? `?${search}` : ''}`, {
          method: 'POST', headers: {'X-Fs-Api': '1'},
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || !payload?.url) {
          throw new FsError(payload?.error?.code || 'EACTION', {
              syscall: 'chat',
              message: payload?.error?.message || 'the server did not start a chat session',
          });
      }
      return payload.url;
  }

  /**
   * Opens one tab per session. The path is synthetic and monotonically numbered,
   * so two sessions over the same selection are two tabs rather than one.
   */
  export function openChatTab({url, paths = [], name, prompt}) {
      const raw = name
          || (paths.length === 1 ? basename(paths[0]) : `${paths.length || 'workspace'} file(s)`);
      const label = String(raw).split('\n')[0].replace(/\//g, ' ').trim().slice(0, 40) || 'Chat';
      const path = `fs-chat:/${++seq}/${label}`;
      return tabs.open(path, {
          virtual: true, pinned: true,
          stat: {
              path, type: 'file', size: 0, readOnly: true, mimeType: 'text/html',
              /* Open the agent UI already in the workspace's theme (#3). */
              chatUrl: withTheme(url), chatPrompt: prompt, title: label,
          },
      });
  }

  /**
   * Editor for those tabs. Selected only when the tab's stat carries `chatUrl`,
   * so nothing else in the registry is affected.
   */
  export class ChatSessionEditor {
      static id = 'chat-session';

      static canOpen(stat) {
          return !!stat?.chatUrl;
      }

      constructor(ctx) {
          this.ctx = ctx;
          const stat = ctx.tab.stat;
          this.url = stat.chatUrl;
          this.frame = h('iframe', {
              class: 'fs-chatsession__frame', src: this.url, allow: 'clipboard-write',
              title: `Chat session ${stat.title || ''}`.trim(),
          });
          this.promptBar = h('div', {class: 'fs-chat__prompt', hidden: true});
          this.el = h('div', {class: 'fs-chatsession'}, [
              h('div', {class: 'fs-chat__toolbar', role: 'toolbar', 'aria-label': 'Chat session'}, [
                  h('span', {class: 'fs-chat__title', text: stat.title || 'Chat session'}),
                  h('span', {style: {flex: '1'}}),
                  h('button', {type: 'button', text: '⟳ Reload', onclick: () => this.reload()}),
                  h('a', {href: this.url, target: '_blank', rel: 'noopener', text: '↗ New browser tab'}),
              ]),
              this.promptBar,
              this.frame,
          ]);
          if (stat.chatPrompt) this.setPrompt(stat.chatPrompt);
      }

      /**
       * The chat app owns its own input box, so a prepared instruction cannot be
       * injected: offer it for one-click copying, and copy it opportunistically
       * while the invoking user gesture is still live.
       */
      setPrompt(prompt) {
          clear(this.promptBar);
          this.promptBar.hidden = false;
          this.promptBar.append(
              h('span', {class: 'sr-only', text: 'Prepared prompt'}),
              h('pre', {class: 'fs-chat__prompt-text', text: prompt}),
              h('button', {
                  type: 'button', text: 'Copy prompt',
                  onclick: async () => {
                      const ok = await copyText(prompt);
                      ui.toast({
                          severity: ok ? 'info' : 'warn',
                          message: ok ? 'Prompt copied — paste it into the chat' : 'Could not copy the prompt',
                      });
                  },
              }),
          );
          copyText(prompt).then((ok) => {
              if (ok) announce('Prompt copied to the clipboard');
          });
      }

      reload() {
          this.frame.src = this.url;
      }

      focus() {
          this.frame.focus();
      }

      dispose() {
          this.frame.src = 'about:blank';
      }
  }