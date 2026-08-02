// Priority-pairing matching loss + regularizers. All differentiable via tf.js.

/**
 * Greedy 1-to-1 matching loss between predicted points P [Np,2] and target
 * Q [M,2]. Pairs are formed by ascending distance: the closest available
 * (P_i, Q_j) pair is matched first, both endpoints are then removed from the
 * pool, and we continue until one side is exhausted. The returned loss is
 *
 *   alpha * mean_{matched i} ||P_i - Q_{pi(i)}||^2
 * + beta  * mean_{matched j} ||P_{pi^{-1}(j)} - Q_j||^2
 *
 * Note: with |P| == |M|, both terms equal each other; we keep alpha/beta as
 * convenience scalers so the UI still has meaning.
 *
 * Pairing indices are computed in plain JS (non-differentiable), but the
 * matched squared distances are taken straight off the differentiable
 * distance tensor, so gradients flow into P (and through it into the IFS
 * parameters).
 */
export function chamferLoss(P, Q, alpha = 1, beta = 1) {
  return tf.tidy(() => {
    const Np = P.shape[0];
    const M = Q.shape[0];
    if (Np === 0 || M === 0) return tf.scalar(0);

    // Pairwise squared distances [Np, M]
    const Pn = tf.sum(tf.square(P), 1, true); // [Np,1]
    const Qn = tf.sum(tf.square(Q), 1, true); // [M,1]
    const cross = tf.matMul(P, Q, false, true); // [Np,M]
    const D = tf.add(tf.sub(Pn, tf.mul(cross, 2)), tf.transpose(Qn));

    // Pull distances to JS to compute the greedy assignment.
    const Dflat = D.dataSync(); // length Np*M

    // Build list of (i, j, d) and sort by d.
    // For very large products this is O(Np*M log(Np*M)); fine for lab sizes.
    const total = Np * M;
    const idxArr = new Int32Array(total);
    for (let k = 0; k < total; k++) idxArr[k] = k;
    // Sort indices into Dflat by ascending value.
    const sorted = Array.from(idxArr).sort((a, b) => Dflat[a] - Dflat[b]);

    const usedP = new Uint8Array(Np);
    const usedQ = new Uint8Array(M);
    const pairs = []; // [[i,j], ...]
    const maxPairs = Math.min(Np, M);
    for (let s = 0; s < sorted.length && pairs.length < maxPairs; s++) {
      const f = sorted[s];
      const i = (f / M) | 0;
      const j = f - i * M;
      if (usedP[i] || usedQ[j]) continue;
      usedP[i] = 1;
      usedQ[j] = 1;
      pairs.push([i, j]);
    }

    if (pairs.length === 0) return tf.scalar(0);

    // Build flat indices (i*M + j) and gather the matched squared distances
    // differentiably from a flattened D. We use tf.gather rather than
    // tf.gatherND because gatherND lacks a registered gradient in tf.js.
    const flatIdx = new Int32Array(pairs.length);
    for (let p = 0; p < pairs.length; p++) {
      flatIdx[p] = pairs[p][0] * M + pairs[p][1];
    }
    const idxT = tf.tensor1d(flatIdx, 'int32');
    const Dflat2 = tf.reshape(D, [Np * M]);
    const matched = tf.gather(Dflat2, idxT); // [pairs.length]

    // Both forward and backward terms collapse to the same set of pairs
    // under 1-to-1 matching. We expose alpha/beta as a simple weight sum.
    const meanMatched = tf.mean(matched);
    return tf.mul(meanMatched, alpha + beta);
  });
}

/**
 * Regularizers on the IFS transforms.
 * @param {IFSModel} model
 */
export function regularizers(model, { lamA = 0, lamb = 0, lamC = 0, eps = 0.02 } = {}) {
  return tf.tidy(() => {
    let r = tf.scalar(0);
    const sqrt2 = Math.SQRT2;
    for (const t of model.transforms) {
      const A = t.A,
        b = t.b;
      if (lamA !== 0) r = tf.add(r, tf.mul(tf.sum(tf.square(A)), lamA));
      if (lamb !== 0) r = tf.add(r, tf.mul(tf.sum(tf.square(b)), lamb));
      if (lamC !== 0) {
        const frob = tf.sqrt(tf.add(tf.sum(tf.square(A)), 1e-12));
        const target = tf.scalar(sqrt2 * (1 - eps));
        const ex = tf.relu(tf.sub(frob, target));
        r = tf.add(r, tf.mul(tf.square(ex), lamC));
      }
    }
    return r;
  });
}

/**
 * Full training loss builder (returns a closure that allocates target Q once).
 * @param {IFSModel} model
 * @param {number[][]} targetArr  list of [x,y]
 * @param {number[][]|null} words  list of words (ordered or commutative);
 *   if null, the commutative fast path is used with depth `hparams.N`.
 */
export function makeLossFn(model, targetArr, words, hparams) {
  const Q = tf.tensor2d(targetArr, [targetArr.length, 2]);
  const lossFn = () => {
    return tf.tidy(() => {
      const P =
        words === null ? model.computeCommutativeOrbit(hparams.N) : model.computeOrbit(words);
      const chamfer = chamferLoss(P, Q, hparams.alpha, hparams.beta);
      const reg = regularizers(model, hparams);
      return tf.add(chamfer, reg);
    });
  };
  lossFn.dispose = () => Q.dispose();
  return lossFn;
}
