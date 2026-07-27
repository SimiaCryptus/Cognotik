import {bus} from './bus.js';

export class FsError extends Error {
    constructor(code, {errno, syscall, path, message, status, detail} = {}) {
        super(message || (UX[code]?.message ?? code));
        this.name = 'FsError';
        this.code = code || 'EIO';
        this.errno = errno;
        this.syscall = syscall;
        this.path = path;
        this.status = status;
        this.detail = detail;
    }
}

/** docs/ui.md §16 — exactly one UX rule per FS API code. */
export const UX = {
    ENOENT: {severity: 'info', message: 'No longer exists'},
    EACCES: {severity: 'warn', message: 'Read-only (marker file) or not permitted'},
    EROFS: {severity: 'warn', message: 'The server is read-only'},
    EEXIST: {severity: 'prompt', message: 'Already exists'},
    EISDIR: {severity: 'error', message: 'Illegal operation on a directory'},
    ENOTDIR: {severity: 'error', message: 'Not a directory'},
    ENOTEMPTY: {severity: 'prompt', message: 'Folder is not empty'},
    EINVAL: {severity: 'error', message: 'Invalid argument'},
    EFBIG: {severity: 'warn', message: 'Exceeds the server size limit'},
    EBUSY: {severity: 'prompt', message: 'Changed on disk since it was opened'},
    EMFILE: {severity: 'info', message: 'Too many watchers — falling back to polling'},
    ERANGE: {severity: 'error', message: 'Requested range is not satisfiable'},
    ENOSYS: {severity: 'info', message: 'Not supported by this server'},
    EIO: {severity: 'error', message: 'I/O error'},
    ENETWORK: {severity: 'error', message: 'Cannot reach the server'},
    ECANCELED: {severity: 'info', message: 'Cancelled'},
    EACTION: {severity: 'error', message: 'The action failed'},
};

export function severityOf(error) {
    const ux = UX[error?.code];
    const severity = ux?.severity ?? 'error';
    return severity === 'prompt' ? 'warn' : severity;
}

export function describe(error, context = {}) {
    if (!(error instanceof FsError)) {
        return {severity: 'error', code: 'EIO', message: error?.message || String(error)};
    }
    const parts = [];
    const operation = context.operation || error.syscall;
    if (operation) parts.push(operation);
    const path = context.path || error.path;
    if (path) parts.push(path);
    const head = parts.length ? `${parts.join(' ')}: ` : '';
    return {
        severity: severityOf(error),
        code: error.code,
        message: head + (error.message || UX[error.code]?.message || error.code),
    };
}

/** Emits `error:raised`; Toasts renders it and the Problems log keeps it. */
export function raise(error, context = {}) {
    const described = describe(error, context);
    bus.emit('error:raised', {error, context, ...described});
    return described;
}
