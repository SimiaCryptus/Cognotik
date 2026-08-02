// Lattice: source of truth for the magnet set and grid <-> world mapping.

export class Lattice {
    constructor(pitch = 48, snap = true) {
        this.pitch = pitch;
        this.snap = snap;
        /** @type {{id:number, cell:[number,number], theta:number}[]} */
        this.magnets = [];
        this._nextId = 0;
    }

    /** World coordinates (px) of a magnet's grid cell. */
    cellToWorld(cell) {
        return [cell[0] * this.pitch, cell[1] * this.pitch];
    }

    /** Convert world (px) to nearest integer grid cell. */
    worldToCell(x, y) {
        return [Math.round(x / this.pitch), Math.round(y / this.pitch)];
    }

    /** Find a magnet occupying the given cell, or undefined. */
    atCell(cell) {
        return this.magnets.find((mg) => mg.cell[0] === cell[0] && mg.cell[1] === cell[1]);
    }

    indexOfCell(cell) {
        return this.magnets.findIndex((mg) => mg.cell[0] === cell[0] && mg.cell[1] === cell[1]);
    }

    /** Toggle a magnet at a cell: add if empty, remove if occupied. Returns 'added'|'removed'. */
    toggle(cell, theta = 0) {
        const idx = this.indexOfCell(cell);
        if (idx >= 0) {
            this.magnets.splice(idx, 1);
            return 'removed';
        }
        this.add(cell, theta);
        return 'added';
    }

    add(cell, theta = 0) {
        const mg = {id: this._nextId++, cell: [cell[0], cell[1]], theta};
        this.magnets.push(mg);
        return mg;
    }

    clear() {
        this.magnets = [];
        this._nextId = 0;
    }

    get count() {
        return this.magnets.length;
    }

    /** Current orientation angles as a Float64Array. */
    angles() {
        return Float64Array.from(this.magnets.map((m) => m.theta));
    }

    setAngles(theta) {
        for (let i = 0; i < this.magnets.length; i++) this.magnets[i].theta = theta[i];
    }

    /** World positions as array of [x,y]. */
    positions() {
        return this.magnets.map((m) => this.cellToWorld(m.cell));
    }
}
