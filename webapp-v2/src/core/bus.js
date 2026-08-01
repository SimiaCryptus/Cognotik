/**
 * Cross-cutting notification bus (§17 dependency rule: core/ must not import
 * render/ or ui/, so anything that needs to travel "upwards" goes through here).
 */
class Bus extends EventTarget {
    emit(type, detail) {
        this.dispatchEvent(new CustomEvent(type, {detail}));
    }

    on(type, handler) {
        this.addEventListener(type, handler);
        return () => this.off(type, handler);
    }

    off(type, handler) {
        this.removeEventListener(type, handler);
    }
}

export const bus = new Bus();

export const Events = Object.freeze({
    VERBOSE_CHANGED: 'verbose-changed',
     THEME_CHANGED: 'theme-changed',
    CONFIG_LOADED: 'config-loaded',
    CONNECTION_CHANGED: 'connection-changed',
    CONNECTION_ERROR: 'connection-error',
    MESSAGES_RENDERED: 'messages-rendered',
     PENDING_CHANGED: 'pending-changed',
    MODAL_CONTENT_READY: 'modal-content-ready'
});