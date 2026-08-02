// Efficient commutative IFS orbit computation (see algo.md).
//
// Given K affine transforms T_k(x) = A_k x + b_k on R^d, ASSUMED TO COMMUTE,
// compute the orbit
//
//   P_N = { T_1^{n_1} ... T_K^{n_K}(0) : sum n_i = N }
//
// efficiently, using:
//   (1) binary-power tables U_{k,r} = T_k^{2^r}  (doubling),
//   (2) DP over multisets, building partial orbits per (k, s) =
//       (prefix of transforms used, total budget consumed so far).
//
// Output: tf.Tensor [|P_N|, d], differentiable wrt {A_k, b_k}.
//
// The canonical application order is T_1 innermost, then T_2, ..., then T_K.
// For commuting T_k this is exact; otherwise it is a modeling choice.
//
// Complexity (point-only variant, d=2):
//   binary powers : O(K log N) matmuls
//   DP            : O(K * S) matvecs, where S = C(N+K-1, K-1)

/**
 * Build per-transform binary-power tables.
 * Returns:
 *   Aksr : array[K][R+1] of tf.Tensor [d,d]    (A_k^{2^r})
 *   bksr : array[K][R+1] of tf.Tensor [d]      (b for T_k^{2^r})
 *   R    : floor(log2(N))
 *
 * Recurrence:
 *   A_{k,0} = A_k,                  b_{k,0} = b_k
 *   A_{k,r+1} = A_{k,r} @ A_{k,r},  b_{k,r+1} = A_{k,r} @ b_{k,r} + b_{k,r}
 *
 * All ops are tf ops so gradients flow.
 */
export function buildBinaryPowers(model, N, d = 2) {
  const K = model.K;
  const R = N <= 1 ? 0 : Math.floor(Math.log2(N));
  const Aksr = [];
  const bksr = [];
  for (let k = 0; k < K; k++) {
    const A0 = model.transforms[k].A.reshape([d, d]);
    const b0 = model.transforms[k].b;
    const Arow = [A0];
    const brow = [b0];
    for (let r = 0; r < R; r++) {
      const Aprev = Arow[r];
      const bprev = brow[r];
      // A_{k,r+1} = Aprev @ Aprev
      const Anext = tf.matMul(Aprev, Aprev);
      // b_{k,r+1} = Aprev @ bprev + bprev
      const bnext = tf.add(tf.matMul(Aprev, bprev.reshape([d, 1])).reshape([d]), bprev);
      Arow.push(Anext);
      brow.push(bnext);
    }
    Aksr.push(Arow);
    bksr.push(brow);
  }
  return { Aksr, bksr, R };
}

/**
 * Compute affine map (A, b) for T_k^n by binary decomposition of n.
 *
 * Compose left-to-right by absorbing U_{k,r} on the RIGHT:
 *   start (A_acc, b_acc) = (I, 0)
 *   for each set bit r:  (A_acc, b_acc) := (A_acc @ U.A, A_acc @ U.b + b_acc)
 * Result acts as: x -> A_acc x + b_acc, which equals T_k^n(x).
 *
 * Special case n=0 returns identity.
 */
export function powerMap(Aksr_k, bksr_k, n, d = 2) {
  if (n === 0) {
    return { A: tf.eye(d), b: tf.zeros([d]) };
  }
  let A_acc = null;
  let b_acc = null;
  let r = 0;
  let m = n;
  while (m > 0) {
    if (m & 1) {
      const A_kr = Aksr_k[r];
      const b_kr = bksr_k[r];
      if (A_acc === null) {
        A_acc = A_kr;
        b_acc = b_kr;
      } else {
        const newA = tf.matMul(A_acc, A_kr);
        const newB = tf.add(tf.matMul(A_acc, b_kr.reshape([d, 1])).reshape([d]), b_acc);
        A_acc = newA;
        b_acc = newB;
      }
    }
    m >>= 1;
    r += 1;
  }
  return { A: A_acc, b: b_acc };
}

/**
 * Compute the full commutative orbit P_N as a tf.Tensor [S, d].
 *
 * Uses the point-only DP variant (algo.md §3.2). For prefix k and budget s,
 * we maintain a tensor `pts[k][s]` of shape [count, d] containing
 *   p_{(n_1,...,n_k)} = (T_1^{n_1} o ... o T_k^{n_k})(0)
 * for all (n_1,...,n_k) with sum = s, listed in colexicographic order.
 *
 * Recurrence: for each n_k in 0..s,
 *   pts[k][s] gets a block = T_k^{n_k}( pts[k-1][s - n_k] )
 *                          = pts[k-1][s-n_k] @ A_kn^T + b_kn
 *
 * Final answer: pts[K][N].
 */
export function computeCommutativeOrbit(model, N, d = 2) {
  const K = model.K;
  return tf.tidy(() => {
    // Edge case: N = 0  ->  single point at origin.
    if (N === 0) return tf.zeros([1, d]);

    const { Aksr, bksr, R } = buildBinaryPowers(model, N, d);

    // Cache power_map(k, n) so we don't rebuild it across budgets.
    // pmCache[k] : Map<n, {A:[d,d], b:[d]}>
    const pmCache = new Array(K).fill(null).map(() => new Map());
    const getPM = (k, n) => {
      const cache = pmCache[k];
      if (cache.has(n)) return cache.get(n);
      const pm = powerMap(Aksr[k], bksr[k], n, d);
      cache.set(n, pm);
      return pm;
    };

    // pts[k][s] : tf.Tensor [count, d]   (k = 0..K, s = 0..N)
    // Base: pts[0][0] = [[0,...,0]], pts[0][s>0] = empty.
    const pts = new Array(K + 1);
    for (let k = 0; k <= K; k++) pts[k] = new Array(N + 1).fill(null);
    pts[0][0] = tf.zeros([1, d]);
    for (let s = 1; s <= N; s++) pts[0][s] = tf.zeros([0, d]);

    for (let k = 1; k <= K; k++) {
      for (let s = 0; s <= N; s++) {
        const blocks = [];
        for (let nk = 0; nk <= s; nk++) {
          const parent = pts[k - 1][s - nk]; // [count_parent, d]
          const cnt = parent.shape[0];
          if (cnt === 0) continue;

          if (nk === 0) {
            // T_k^0 = identity; pass parent through.
            blocks.push(parent);
          } else {
            const { A, b } = getPM(k - 1, nk); // 0-indexed: transform k-1
            // child = parent @ A^T + b
            const Achild = tf.add(tf.matMul(parent, A, false, true), b);
            blocks.push(Achild);
          }
        }
        pts[k][s] = blocks.length
          ? blocks.length === 1
            ? blocks[0]
            : tf.concat(blocks, 0)
          : tf.zeros([0, d]);
      }
    }

    return pts[K][N];
  });
}

/**
 * Orbit size |P_N| = C(N+K-1, K-1).
 */
export function commutativeOrbitSize(K, N) {
  // multiset coefficient
  let num = 1,
    den = 1;
  const r = K - 1;
  for (let i = 1; i <= r; i++) {
    num *= N + i;
    den *= i;
  }
  return Math.round(num / den);
}
