import {caps} from './capabilities.js';
import {bus} from './bus.js';
import {buildContext} from './context.js';
import {raise, FsError} from './errors.js';

const commands = new Map();

export function registerCommand(command) {
    if (!command || !command.id) throw new Error('a command requires an id');
    if (!command.title) throw new Error(`command ${command.id} requires a title`);
    commands.set(command.id, {requires: [], keys: [], ...command});
    return commands.get(command.id);
}

export function getCommand(id) {
    return commands.get(id);
}

export function allCommands() {
    return Array.from(commands.values());
}

export function missingCapabilities(command) {
    return (command.requires || []).filter((name) => !caps.has(name));
}

export function isVisible(command) {
    return missingCapabilities(command).length === 0;
}

export function isEnabled(command, ctx) {
    if (!isVisible(command)) return false;
    if (!command.when) return true;
    try {
        return !!command.when(ctx);
    } catch (e) {
        console.warn(`when() of ${command.id} threw`, e);
        return false;
    }
}

export async function executeCommand(id, ctx) {
    const command = commands.get(id);
    if (!command) throw new Error(`unknown command: ${id}`);
    const context = ctx || buildContext({origin: 'palette'});
    const missing = missingCapabilities(command);
    if (missing.length) {
        raise(new FsError('ENOSYS', {
            syscall: id,
            message: `Requires: ${missing.join(', ')}`
        }), {operation: command.title});
        return undefined;
    }
    bus.emit('command:before', {id});
    try {
        const result = await command.run(context);
        bus.emit('command:after', {id, result});
        return result;
    } catch (error) {
        if (error?.code === 'ECANCELED') {
            bus.emit('command:cancelled', {id});
            return undefined;
        }
        raise(error instanceof FsError ? error
                : new FsError('EACTION', {syscall: id, message: error?.message, detail: String(error?.stack || '')}),
            {operation: command.title});
        return undefined;
    }
}
