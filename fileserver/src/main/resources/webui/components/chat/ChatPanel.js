import {Component} from '../base.js';
  import {h, clear} from '../../core/dom.js';
  import {store} from '../../core/store.js';
  import {ui} from '../../core/ui.js';
  import {announce} from '../../core/a11y.js';
  import {raise, FsError} from '../../core/errors.js';
  import {copyText} from '../../core/clipboard.js';
  import {basename} from '../../core/paths.js';
  import {executeCommand} from '../../core/commands.js';
  
  /**
   * Basic code chat (note #4).
   *
   * All of the intelligence lives on the server: `POST {base}/modify` builds a
   * patch-chat session over a file selection and answers with its URL. We host
   * that URL in an iframe, so the session is never lost to a pop-up blocker and
   * the workspace stays on screen next to it.
   *
   * A prompt handed to [openSession] is *not* injected into the session — the
   * chat app owns its own input — so it is offered here for one-click copying
   * (and copied opportunistically while the user gesture is still live).
   */
  export class ChatPanel extends Component {
      render() {
          this.url = null;
          this.title = h('span', {class: 'fs-chat__title', text: 'No chat session'});
          this.promptBar = h('div', {class: 'fs-chat__prompt', hidden: true});
          this.status = h('p', {class: 'fs-chat__status', role: 'status'});
          this.frame = h('iframe', {
              class: 'fs-chat__frame', title: 'Code chat', src: 'about:blank',
              allow: 'clipboard-write',
          });
          this.el = h('section', {class: 'fs-chat'}, [
              h('div', {class: 'fs-chat__toolbar', role: 'toolbar', 'aria-label': 'Code chat'}, [
                  this.title,
                  h('span', {style: {flex: '1'}}),
                  this.button('⧉', 'Open in a new tab', () => this.openExternal()),
                  this.button('✕', 'Hide chat', () => executeCommand('view.toggleChat')),
              ]),
              this.promptBar,
              this.status,
              this.frame,
          ]);
          return this.el;
      }
  
      button(icon, label, onclick) {
          return h('button', {type: 'button', title: label, onclick}, [
              h('span', {'aria-hidden': 'true', text: icon}),
              h('span', {class: 'sr-only', text: label}),
          ]);
      }
  
      focus() {
          this.frame.focus();
      }
  
      setStatus(text) {
          this.status.textContent = text || '';
      }
  
      /** `url` short-circuits the round trip (a server action already made one). */
      async openSession({paths = [], name, prompt, url} = {}) {
          let target = url;
          if (!target) {
              this.setStatus('Starting a chat session…');
              try {
                  target = await this.requestSession(paths, name || prompt);
              } catch (error) {
                  this.setStatus('');
                  raise(error, {operation: 'chat'});
                  return null;
              }
          }
          this.setStatus('');
          this.title.textContent = name
              || (paths.length === 1 ? basename(paths[0]) : `${paths.length || 'workspace'} file(s)`);
          this.url = target;
          this.frame.src = target;
          this.setPrompt(prompt);
          announce('Code chat session opened');
          return {url: target};
      }
  
      async requestSession(paths, name) {
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
  
      setPrompt(prompt) {
          clear(this.promptBar);
          this.promptBar.hidden = !prompt;
          if (!prompt) return;
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
  
      openExternal() {
          if (this.url) window.open(this.url, '_blank', 'noopener');
      }
  
      destroyed() {
          this.frame.src = 'about:blank';
      }
  }