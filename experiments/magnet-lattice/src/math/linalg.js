// Small dense linear algebra for the magnet lattice.
// All matrices are represented as arrays of Float64Array rows (or arrays).

/** Create an n×n zero matrix (array of Float64Array rows). */
export function zeros(n, m = n) {
    const A = new Array(n);
    for (let i = 0; i < n; i++) A[i] = new Float64Array(m);
    return A;
}

/** Deep copy a matrix. */
export function cloneMat(A) {
    return A.map((row) => Float64Array.from(row));
}

/** Identity matrix. */
export function eye(n) {
    const A = zeros(n);
    for (let i = 0; i < n; i++) A[i][i] = 1;
    return A;
}

/**
 * Solve A x = b via LU decomposition with partial pivoting.
 * A: n×n matrix, b: length-n array. Returns x (Float64Array).
 * Does not mutate inputs.
 */
export function luSolve(Ain, bin) {
    const n = Ain.length;
    const A = cloneMat(Ain);
    const b = Float64Array.from(bin);
    const piv = new Int32Array(n);
    for (let i = 0; i < n; i++) piv[i] = i;

    for (let k = 0; k < n; k++) {
        // pivot
        let p = k,
            max = Math.abs(A[k][k]);
        for (let i = k + 1; i < n; i++) {
            const v = Math.abs(A[i][k]);
            if (v > max) {
                max = v;
                p = i;
            }
        }
        if (max === 0) throw new Error('luSolve: singular matrix');
        if (p !== k) {
            const tmp = A[k];
            A[k] = A[p];
            A[p] = tmp;
            const tb = b[k];
            b[k] = b[p];
            b[p] = tb;
        }
        const akk = A[k][k];
        for (let i = k + 1; i < n; i++) {
            const f = A[i][k] / akk;
            A[i][k] = f;
            for (let j = k + 1; j < n; j++) A[i][j] -= f * A[k][j];
            b[i] -= f * b[k];
        }
    }
    // back substitution
    const x = new Float64Array(n);
    for (let i = n - 1; i >= 0; i--) {
        let s = b[i];
        for (let j = i + 1; j < n; j++) s -= A[i][j] * x[j];
        x[i] = s / A[i][i];
    }
    return x;
}

/** Matrix-vector product A·v. */
export function matVec(A, v) {
    const n = A.length;
    const out = new Float64Array(n);
    for (let i = 0; i < n; i++) {
        let s = 0;
        const row = A[i];
        for (let j = 0; j < row.length; j++) s += row[j] * v[j];
        out[i] = s;
    }
    return out;
}

/**
 * Symmetric eigen-decomposition via the cyclic Jacobi rotation method.
 * Input: symmetric n×n matrix A (not mutated).
 * Returns { values: Float64Array (ascending), vectors: array of columns }
 * where vectors[k] is the eigenvector for values[k].
 */
export function jacobiEigen(Ain, maxSweeps = 100, tol = 1e-14) {
    const n = Ain.length;
    const A = cloneMat(Ain);
    const V = eye(n);

    const off = () => {
        let s = 0;
        for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) s += A[i][j] * A[i][j];
        return s;
    };

    for (let sweep = 0; sweep < maxSweeps; sweep++) {
        if (off() < tol) break;
        for (let p = 0; p < n; p++) {
            for (let q = p + 1; q < n; q++) {
                const apq = A[p][q];
                if (Math.abs(apq) < 1e-300) continue;
                const app = A[p][p],
                    aqq = A[q][q];
                const phi = 0.5 * Math.atan2(2 * apq, aqq - app);
                const c = Math.cos(phi),
                    s = Math.sin(phi);
                // rotate rows/cols p,q
                for (let i = 0; i < n; i++) {
                    const aip = A[i][p],
                        aiq = A[i][q];
                    A[i][p] = c * aip - s * aiq;
                    A[i][q] = s * aip + c * aiq;
                }
                for (let i = 0; i < n; i++) {
                    const api = A[p][i],
                        aqi = A[q][i];
                    A[p][i] = c * api - s * aqi;
                    A[q][i] = s * api + c * aqi;
                }
                for (let i = 0; i < n; i++) {
                    const vip = V[i][p],
                        viq = V[i][q];
                    V[i][p] = c * vip - s * viq;
                    V[i][q] = s * vip + c * viq;
                }
            }
        }
    }

    const idx = Array.from({length: n}, (_, i) => i);
    const vals = new Float64Array(n);
    for (let i = 0; i < n; i++) vals[i] = A[i][i];
    idx.sort((a, b) => vals[a] - vals[b]);

    const values = new Float64Array(n);
    const vectors = new Array(n);
    for (let k = 0; k < n; k++) {
        const src = idx[k];
        values[k] = vals[src];
        const col = new Float64Array(n);
        for (let i = 0; i < n; i++) col[i] = V[i][src];
        vectors[k] = col;
    }
    return {values, vectors};
}
