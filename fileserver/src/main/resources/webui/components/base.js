export class Component {
    constructor(props = {}) {
        this.props = props;
        this.el = null;
        this._subs = [];
    }

    mount(parent) {
        this.el = this.render();
        parent.appendChild(this.el);
        this.mounted?.();
        return this.el;
    }

    render() {
        throw new Error('render() required');
    }

    /** store/bus/DOM unsubscribe functions, released on destroy(). */
    track(unsubscribe) {
        if (typeof unsubscribe === 'function') this._subs.push(unsubscribe);
        return unsubscribe;
    }

    update() { /* opt-in, surgical */
    }

    destroy() {
        this._subs.forEach((un) => {
            try {
                un();
            } catch (e) {
                console.warn(e);
            }
        });
        this._subs.length = 0;
        this.destroyed?.();
        this.el?.remove();
        this.el = null;
    }
}
