// Versioned JSON import/export with schema validation.

const VERSION = 1;

/** Export lattice + params to a pretty-printed JSON string. */
export function exportJSON(lattice, params) {
    const doc = {
        version: VERSION,
        grid: {pitch: lattice.pitch, snap: lattice.snap},
        params: {
            k: params.k,
            I: params.I,
            gamma: params.gamma,
            m: params.m,
        },
        magnets: lattice.magnets.map((mg) => ({
            id: mg.id,
            cell: [mg.cell[0], mg.cell[1]],
            theta: mg.theta,
        })),
    };
    return JSON.stringify(doc, null, 2);
}

/**
 * Validate a parsed document. Throws Error with a helpful message on failure.
 * Returns the validated doc.
 */
export function validate(doc) {
    if (typeof doc !== 'object' || doc === null) throw new Error('Root must be an object');
    if (doc.version !== VERSION)
        throw new Error(`Unsupported version: ${doc.version} (expected ${VERSION})`);
    if (typeof doc.grid !== 'object') throw new Error('Missing "grid"');
    if (typeof doc.grid.pitch !== 'number' || doc.grid.pitch <= 0)
        throw new Error('grid.pitch must be a positive number');
    if (typeof doc.grid.snap !== 'boolean') throw new Error('grid.snap must be boolean');
    if (typeof doc.params !== 'object') throw new Error('Missing "params"');
    for (const key of ['k', 'I', 'gamma', 'm']) {
        if (typeof doc.params[key] !== 'number') throw new Error(`params.${key} must be a number`);
    }
    if (!Array.isArray(doc.magnets)) throw new Error('magnets must be an array');
    const seen = new Set();
    doc.magnets.forEach((mg, idx) => {
        if (typeof mg.id !== 'number') throw new Error(`magnet[${idx}].id must be a number`);
        if (seen.has(mg.id)) throw new Error(`Duplicate magnet id: ${mg.id}`);
        seen.add(mg.id);
        if (
            !Array.isArray(mg.cell) ||
            mg.cell.length !== 2 ||
            !Number.isInteger(mg.cell[0]) ||
            !Number.isInteger(mg.cell[1])
        ) {
            throw new Error(`magnet[${idx}].cell must be [int,int]`);
        }
        if (typeof mg.theta !== 'number') throw new Error(`magnet[${idx}].theta must be a number`);
    });
    return doc;
}

/**
 * Import a JSON string into a lattice + params. Returns { grid, params, magnets }.
 * Throws on invalid input.
 */
export function importJSON(text) {
    let doc;
    try {
        doc = JSON.parse(text);
    } catch (e) {
        throw new Error('Invalid JSON: ' + e.message);
    }
    validate(doc);
    return doc;
}
