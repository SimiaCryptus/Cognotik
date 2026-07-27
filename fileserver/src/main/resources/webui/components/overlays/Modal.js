import {h, clear} from '../../core/dom.js';
import {trapFocus} from '../../core/a11y.js';
import {ui} from '../../core/ui.js';
import {persist} from '../../core/persist.js';

let seq = 0;

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
            dialog.close();
            dialog.remove();
            resolve(value);
        };

        for (const action of actions) {
            footer.appendChild(h('button', {
                type: 'button', text: action.label,
                onclick: () => {
                    const value = action.value ?? action.run?.();
                    Promise.resolve(value).then(close);
                },
            }));
        }
        dialog.append(heading, content, footer);
        dialog.addEventListener('cancel', (event) => {
            event.preventDefault();
            close(null);
        });
        document.body.appendChild(dialog);
        dialog.showModal();
        release = trapFocus(dialog, {initial: initialFocus?.(dialog)});
    });
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
    ui.form = async ({title, params = [], ctx, remember}) => {
        const remembered = (remember && persist.get('actionParams', {})[remember]) || {};
        const values = {};
        const fields = [];
        const errors = new Map();
        const form = h('form', {novalidate: ''});

        for (const param of params) {
            const id = `fs-param-${param.id}`;
            const initial = remembered[param.id] ?? (typeof param.default === 'function' ? param.default(ctx) : param.default);
            values[param.id] = initial ?? (param.type === 'boolean' ? false : '');
            let input;
            switch (param.type) {
                case 'boolean':
                    input = h('input', {type: 'checkbox', id, checked: !!initial});
                    break;
                case 'text':
                    input = h('textarea', {id, rows: '4', placeholder: param.placeholder || ''});
                    input.value = initial ?? '';
                    break;
                case 'enum':
                    input = h('select', {id}, (param.options || []).map((option) =>
                        h('option', {value: option, text: option, selected: option === initial})));
                    break;
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
            const read = () => (param.type === 'boolean' ? input.checked : input.value);
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
                if (param.required && (value === '' || value === null || value === undefined)) problem = 'This field is required';
                else if (param.validate) problem = param.validate(value, ctx) || null;
                if (problem) {
                    error.textContent = problem;
                    errors.set(param.id, problem);
                }
            }
            if (errors.size) {
                fields.find((f) => errors.has(f.param.id))?.input.focus();
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
