// configPanel.js — parameter controls

import { el } from '../util/dom.js';
import { CONFIG_SCHEMA, REBUILD_KEYS } from '../config.js';

export function createConfigPanel(container, { config, onChange }) {
  const grid = el('div', { class: 'config-grid' });
  const inputs = {}; // key -> input element

  function makeControl(field) {
    const id = `cfg-${field.key}`;
    let input;
    if (field.type === 'select') {
      input = el('select', { id });
      for (const o of field.options) {
        const opt = el('option', { value: o.value, text: o.label });
        if (config[field.key] === o.value) opt.selected = true;
        input.appendChild(opt);
      }
    } else if (field.type === 'bool') {
      input = el('input', { id, type: 'checkbox' });
      input.checked = !!config[field.key];
    } else if (field.type === 'text') {
      input = el('input', { id, type: 'text', value: config[field.key] || '' });
    } else {
      input = el('input', {
        id,
        type: 'number',
        value: config[field.key],
        min: field.min,
        max: field.max,
        step: field.step || (field.type === 'int' ? 1 : 'any'),
      });
    }

    const evt = field.type === 'text' ? 'input' : 'change';
    input.addEventListener(evt, () => {
      let val;
      if (field.type === 'bool') val = input.checked;
      else if (field.type === 'int') val = parseInt(input.value, 10);
      else if (field.type === 'float') val = parseFloat(input.value);
      else val = input.value;
      config[field.key] = val;

      // When a regex preset is chosen, sync the pattern field to it.
      if (field.key === 'regexPreset' && inputs.regexPattern) {
        config.regexPattern = val;
        inputs.regexPattern.value = val;
      }

      onChange(field.key, val, REBUILD_KEYS.includes(field.key));
    });

    inputs[field.key] = input;
    return input;
  }

  for (const field of CONFIG_SCHEMA) {
    const label = el('label', { for: `cfg-${field.key}`, text: field.label });
    const control = makeControl(field);
    // Group regex-specific fields so we can show/hide them.
    if (field.key === 'regexPreset' || field.key === 'regexPattern') {
      label.dataset.regexOnly = '1';
      control.dataset.regexOnly = '1';
    }
    grid.appendChild(label);
    grid.appendChild(control);
  }

  function updateRegexVisibility() {
    const show = config.tokenizerId === 'regex';
    for (const node of grid.querySelectorAll('[data-regex-only]')) {
      node.style.display = show ? '' : 'none';
    }
  }
  updateRegexVisibility();

  // Toggle regex field visibility when tokenizer changes.
  if (inputs.tokenizerId) {
    inputs.tokenizerId.addEventListener('change', updateRegexVisibility);
  }

  container.appendChild(el('h2', { text: 'Configuration' }));
  container.appendChild(grid);
}
