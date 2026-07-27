/**
 * The only module a host-registered client contribution may import (§19.12).
 * A lint test asserts that webui/ext/** imports nothing else.
 */
export const API_VERSION = 1;

export {registerAction, registerGroup, registerMenuAnchor, DEFAULT_GROUP} from '../core/actions.js';
export {registerCommand, executeCommand, getCommand, allCommands} from '../core/commands.js';
export {registerDataProvider, registerContextSource, buildContext} from '../core/context.js';
export {registerPanel, registerStatusItem, registerTreeDecorator} from '../core/registry.js';
export {registerEditor} from '../components/editors/EditorRegistry.js';
export {fs} from '../core/fsclient.js';
export {caps} from '../core/capabilities.js';
export {ui} from '../core/ui.js';
export {bus} from '../core/bus.js';
export {t} from '../core/i18n.js';
export {announce} from '../core/a11y.js';
export {FsError} from '../core/errors.js';
export {Component} from '../components/base.js';
export {h} from '../core/dom.js';
export {basename, dirname, join, normalize, formatBytes} from '../core/paths.js';

export function on(type, fn) {
    // eslint-disable-next-line no-restricted-globals
    return import('../core/bus.js').then(({bus}) => bus.on(type, fn));
}
