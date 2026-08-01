import {h, clear} from '../../core/dom.js';
  import {fs} from '../../core/fsclient.js';
  import {ui} from '../../core/ui.js';
  import {bus} from '../../core/bus.js';
  import {delimiterFor} from '../../core/mime.js';
  import {basename} from '../../core/paths.js';

  /** Beyond this the browser, not the file, becomes the bottleneck. */
  const MAX_ROWS = 5000;

  /** RFC 4180-ish parser: quoted fields, "" escapes, CR/CRLF normalised. */
  export function parseDelimited(text, delimiter) {
      const source = String(text ?? '').replace(/\r\n?/g, '\n');
      const rows = [];
      let row = [];
      let field = '';
      let quoted = false;
      const endField = () => {
          row.push(field);
          field = '';
      };
      const endRow = () => {
          endField();
          rows.push(row);
          row = [];
      };
      for (let i = 0; i < source.length; i++) {
          const ch = source[i];
          if (quoted) {
              if (ch !== '"') field += ch;
              else if (source[i + 1] === '"') {
                  field += '"';
                  i++;
              } else quoted = false;
              continue;
          }
          if (ch === '"' && field === '') quoted = true;
          else if (ch === delimiter) endField();
          else if (ch === '\n') endRow();
          else field += ch;
      }
      if (field !== '' || row.length) endRow();
      return rows;
  }

  /**
   * Rendered view of a delimited-text document (CSV/TSV), reached through a
   * virtual tab whose stat carries `previewKind: 'table'`. Editing the raw text
   * stays one click away, exactly as for Markdown.
   */
  export class TablePreview {
      static id = 'table-preview';

      static canOpen(stat) {
          return stat?.previewKind === 'table';
      }

      constructor(ctx) {
          this.ctx = ctx;
          this.path = ctx.tab.stat.sourcePath || ctx.tab.path;
          this.delimiter = ctx.tab.stat.delimiter || delimiterFor(this.path);
          this.header = true;
          this.rows = [];
          this.wrap = h('div', {class: 'fs-table-wrap', tabindex: '0'});
          this.status = h('span', {class: 'fs-preview__status', role: 'status'});
          this.headerToggle = h('button', {
              type: 'button', 'aria-pressed': 'true', text: '⌗ Header row',
              title: 'Treat the first row as column headings',
              onclick: () => {
                  this.header = !this.header;
                  this.headerToggle.setAttribute('aria-pressed', String(this.header));
                  this.paint();
              },
          });
          this.el = h('div', {class: 'fs-preview'}, [
              h('div', {class: 'fs-preview__toolbar', role: 'toolbar', 'aria-label': 'Table preview'}, [
                  h('span', {text: basename(this.path)}),
                  this.headerToggle,
                  h('span', {style: {flex: '1'}}),
                  this.status,
                  h('button', {type: 'button', text: '⟳ Reload', onclick: () => this.reload()}),
                  h('button', {
                      type: 'button', text: '✎ Edit',
                      onclick: () => ui.openPath(this.path, {pinned: true}),
                  }),
              ]),
              this.wrap,
          ]);
          this.offEvent = bus.on('fs:event', (event) => {
              if (event?.path === this.path) this.reload();
          });
      }

      async load() {
          await this.reload();
      }

      async reload() {
          this.status.textContent = 'Loading…';
          try {
              const {text} = await fs.readText(this.path);
              this.rows = parseDelimited(text ?? '', this.delimiter);
              this.paint();
          } catch (error) {
              clear(this.wrap);
              this.status.textContent = '';
              this.wrap.appendChild(h('p', {
                  class: 'fs-explorer__status',
                  text: `Could not read ${basename(this.path)}: ${error?.message || error}`,
              }));
          }
      }

      paint() {
          clear(this.wrap);
          const rows = this.rows.slice(0, MAX_ROWS);
          if (!rows.length) {
              this.status.textContent = '';
              this.wrap.appendChild(h('p', {class: 'fs-explorer__status', text: 'Empty document'}));
              return;
          }
          const columns = rows.reduce((max, row) => Math.max(max, row.length), 0);
          const table = h('table', {class: 'fs-table'});
          let body = rows;
          if (this.header) {
              table.appendChild(h('thead', {}, [this.rowEl(rows[0], columns, 'th')]));
              body = rows.slice(1);
          }
          const tbody = h('tbody');
          for (const row of body) tbody.appendChild(this.rowEl(row, columns, 'td'));
          table.appendChild(tbody);
          this.wrap.appendChild(table);
          this.status.textContent = `${this.rows.length} row(s) · ${columns} column(s)`
              + (this.rows.length > MAX_ROWS ? ` · showing the first ${MAX_ROWS}` : '');
      }

      rowEl(row, columns, cell) {
          const tr = h('tr');
          for (let i = 0; i < columns; i++) {
              const value = row[i] ?? '';
              const numeric = cell === 'td' && value !== '' && !Number.isNaN(Number(value));
              tr.appendChild(h(cell, {
                  class: numeric ? 'num' : null,
                  scope: cell === 'th' ? 'col' : null,
                  title: value.length > 48 ? value : null,
                  text: value,
              }));
          }
          return tr;
      }

      focus() {
          this.wrap.focus();
      }

      dispose() {
          this.offEvent?.();
      }
  }