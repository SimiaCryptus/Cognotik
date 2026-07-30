import {h, clear} from '../../core/dom.js';
import {trapFocus} from '../../core/a11y.js';
import {ui} from '../../core/ui.js';
import {persist} from '../../core/persist.js';

let seq = 0;
/**
  * Open dialogs, innermost last. The global Escape keybinding calls
  * preventDefault(), which suppresses <dialog>'s native `cancel` event, so the
  * "Close Overlay" command has to dismiss the top dialog explicitly (#3).
  */
const openDialogs = [];

function open({title, body, actions, initialFocus}) {
    return new Promise((resolve) => {
        const id = `fs-modal-${++seq}`;
        const dialog = h('dialog', {class: 'fs-modal', 'aria-labelledby': `${id}-title`});
        const heading = h('h2', {id: `${id}-title`, text: title});
        const content = h('div', {id: `${id}-body`});
        if (body) content.appendChild(typeof body === 'string' ? h('p', {text: body}) : body);
        const footer = h('div', {class: 'fs-modal__actions'});

        let release = null;
        const close = (value) => {
            release?.();
             const index = openDialogs.indexOf(dialog);
             if (index >= 0) openDialogs.splice(index, 1);
            dialog.close();
            dialog.remove();
            resolve(value);
        };
         dialog.__fsCancel = () => close(null);

        for (const action of actions) {
            footer.appendChild(h('button', {
                type: 'button', text: action.label,
                onclick: () => {
                    /* `??` would treat a declared `value: null` (Cancel) as "unset" and
                       fall through to run(), resolving the dialog with `undefined` — which
                       callers cannot distinguish from a confirmation. Ask for the key. */
                    const value = Object.prototype.hasOwnProperty.call(action, 'value')
                        ? action.value
                        : action.run?.();
                    /* run() answering `undefined` means "validation failed": stay open. */
                    if (value === undefined) return;
                    Promise.resolve(value).then((resolved) => {
                        if (resolved === undefined) return;
                        close(resolved);
                    });
                },
            }));
        }
        dialog.append(heading, content, footer);
        dialog.addEventListener('cancel', (event) => {
            event.preventDefault();
            close(null);
        });
        document.body.appendChild(dialog);
         openDialogs.push(dialog);
        dialog.showModal();
        release = trapFocus(dialog, {initial: initialFocus?.(dialog)});
    });
}
/** Dismisses the innermost dialog (as Cancel would). Returns false if none. */
export function closeTopModal() {
     const dialog = openDialogs[openDialogs.length - 1];
     if (!dialog) return false;
     dialog.__fsCancel?.();
     return true;
}

export function initModal() {
    ui.confirm = async ({title, body, confirmLabel = 'OK', cancelLabel = 'Cancel', danger = false} = {}) => {
        const result = await open({
            title, body,
            /* Initial focus on the least destructive action. */
            initialFocus: (dialog) => dialog.querySelectorAll('.fs-modal__actions button')[danger ? 1 : 0],
            actions: [{label: confirmLabel, value: true}, {label: cancelLabel, value: false}],
        });
        return result === true;
    };

    ui.prompt = async ({title, label, value = '', validate, confirmLabel = 'OK'} = {}) => {
        const input = h('input', {type: 'text', value, id: 'fs-prompt-input'});
        const error = h('p', {class: 'error', role: 'alert'});
        const field = h('div', {class: 'fs-field'}, [
            h('label', {htmlFor: 'fs-prompt-input', text: label || title}), input, error,
        ]);
        const submit = () => {
            const problem = validate?.(input.value);
            if (problem) {
                error.textContent = problem;
                input.setAttribute('aria-invalid', 'true');
                input.focus();
                return undefined;
            }
            return input.value;
        };
        input.addEventListener('keydown', (event) => {
            if (event.key !== 'Enter') return;
            event.preventDefault();
            const result = submit();
            if (result !== undefined) {
                input.closest('dialog').querySelector('.fs-modal__actions button').click();
            }
        });
        return open({
            title, body: field, initialFocus: () => input,
            actions: [{label: confirmLabel, run: submit}, {label: 'Cancel', value: null}],
        });
    };

    /** ActionDialog: a real <form> generated from a declared parameter schema. */
   ui.form = async ({title, params = [], ctx, remember, resolveOptions}) => {
        const remembered = (remember && persist.get('actionParams', {})[remember]) || {};
        const values = {};
        const fields = [];
        const errors = new Map();
        const form = h('form', {novalidate: ''});

        for (const param of params) {
            const id = `fs-param-${param.id}`;
            const initial = remembered[param.id] ?? (typeof param.default === 'function' ? param.default(ctx) : param.default);
           values[param.id] = initial ?? (param.type === 'boolean' ? false : param.type === 'checklist' ? [] : '');
            let input;
            switch (param.type) {
                case 'boolean':
                    input = h('input', {type: 'checkbox', id, checked: !!initial});
                    break;
                case 'text':
                    input = h('textarea', {id, rows: '4', placeholder: param.placeholder || ''});
                    input.value = initial ?? '';
                    break;
                /* A single-value select whose options may be static or fetched live
                   (param.dynamic), e.g. DocOps listing the targets available for the
                   documents that are currently selected. */
                case 'enum': {
                    const select = h('select', {id});
                    const paint = (options, {loading = false, error = null} = {}) => {
                        clear(select);
                        select.disabled = !!loading;
                        if (loading) {
                            select.appendChild(h('option', {value: '', text: 'Loading…'}));
                            return;
                        }
                        if (error) {
                            select.appendChild(h('option', {value: '', text: error}));
                        } else if (param.dynamic && !param.required) {
                            /* Live lists need an explicit "leave unset" entry; static
                               enums keep their declared default as the only initial value. */
                            select.appendChild(h('option', {value: '', text: param.placeholder || '(none)'}));
                        }
                        for (const option of options) {
                            const value = typeof option === 'string' ? option : option.value;
                            const optionLabel = typeof option === 'string' ? option : (option.label || option.value);
                            select.appendChild(h('option', {
                                value, text: optionLabel, selected: value === initial,
                                title: (typeof option === 'object' && option.description) || null,
                            }));
                        }
                        values[param.id] = select.value;
                    };
                    if (param.dynamic && resolveOptions) {
                        paint([], {loading: true});
                        Promise.resolve(resolveOptions(param))
                            .then((options) => paint(options || []))
                            .catch((error) => paint([], {
                                error: `Could not load options: ${error?.message || error}`,
                            }));
                    } else {
                        paint(param.options || []);
                    }
                    input = select;
                    break;
                }
               /* A "checkbox list": options may be static or fetched live (param.dynamic)
                  from the server, e.g. DocOps enumerating targets for selected files. */
               case 'checklist': {
                   const list = h('div', {class: 'fs-checklist', id, role: 'group', 'aria-label': param.label || param.id});
                   const checkedValues = new Set(Array.isArray(initial) ? initial : []);
                   const paint = (options) => {
                       clear(list);
                       if (!options.length) {
                           list.appendChild(h('p', {class: 'help', text: 'No options available'}));
                           return;
                       }
                       for (const option of options) {
                           const value = typeof option === 'string' ? option : option.value;
                           const optionLabel = typeof option === 'string' ? option : (option.label || option.value);
                           const optionId = `${id}-${value}`;
                           list.appendChild(h('label', {class: 'fs-checklist__item', htmlFor: optionId}, [
                               h('input', {type: 'checkbox', id: optionId, value, checked: checkedValues.has(value)}),
                               h('span', {text: optionLabel}),
                           ]));
                       }
                   };
                   if (param.dynamic && resolveOptions) {
                       list.appendChild(h('p', {class: 'help', text: 'Loading…'}));
                       Promise.resolve(resolveOptions(param)).then((options) => paint(options || []))
                           .catch((error) => {
                               clear(list);
                               list.appendChild(h('p', {class: 'error', text: `Could not load options: ${error?.message || error}`}));
                           });
                   } else {
                       paint(param.options || []);
                   }
                   input = list;
                   break;
               }
                case 'number':
                case 'integer':
                    input = h('input', {type: 'number', id, value: initial ?? ''});
                    break;
                case 'secret':
                    input = h('input', {type: 'password', id});
                    break;
                default:
                    input = h('input', {type: 'text', id, value: initial ?? '', placeholder: param.placeholder || ''});
            }
            const error = h('p', {class: 'error', id: `${id}-error`});
            const help = param.help ? h('p', {class: 'help', id: `${id}-help`, text: param.help}) : null;
            if (help) input.setAttribute('aria-describedby', `${id}-help`);
           const read = () => {
               if (param.type === 'boolean') return input.checked;
               if (param.type === 'checklist') {
                   return Array.from(input.querySelectorAll('input[type="checkbox"]:checked')).map((cb) => cb.value);
               }
               return input.value;
           };
           /* For 'checklist' this listens on the container: checkbox 'input'/'change'
              events bubble, so one listener covers every option without extra wiring. */
            input.addEventListener('input', () => {
                values[param.id] = read();
                error.textContent = '';
            });
            input.addEventListener('change', () => {
                values[param.id] = read();
            });
            fields.push({param, input, error, read});
            form.appendChild(h('div', {class: 'fs-field'}, [
                h('label', {htmlFor: id, text: param.label || param.id}), input, help, error,
            ]));
        }

        const submit = () => {
            errors.clear();
            for (const {param, error, read} of fields) {
                const value = read();
                values[param.id] = value;
                let problem = null;
               const empty = Array.isArray(value) ? value.length === 0 : (value === '' || value === null || value === undefined);
               if (param.required && empty) problem = 'This field is required';
                else if (param.validate) problem = param.validate(value, ctx) || null;
                if (problem) {
                    error.textContent = problem;
                    errors.set(param.id, problem);
                }
            }
            if (errors.size) {
               fields.find((f) => errors.has(f.param.id))?.input.focus?.();
                return undefined;
            }
            if (remember) {
                const all = persist.get('actionParams', {});
                const persistable = {...values};
                for (const {param} of fields) if (param.type === 'secret' || param.remember === false) delete persistable[param.id];
                persist.set('actionParams', {...all, [remember]: persistable});
            }
            return {...values};
        };
        form.addEventListener('submit', (event) => event.preventDefault());

        return open({
            title, body: form, initialFocus: () => fields[0]?.input,
            actions: [{label: 'Run', run: submit}, {label: 'Cancel', value: null}],
        });
    };
}

export {open as openModal};

export function resetModalBody(el) {
    clear(el);
}