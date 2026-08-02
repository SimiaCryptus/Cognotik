// js/mesh-energy.js
// Soft energy functionals under tfjs autodiff (idea.md §1, math_outline §1).
// `tf` is expected to be available globally (loaded via CDN / tf.min.js).
//
//   E(P) = λ_area·A + λ_vol·|V−V*| + λ_fit·Φ + λ_len·(−H) + λ_ang·Θ + λ_smooth·S
//
// Every √(·) is softened to √(·+ε²) per the §1.7 shared clause so that both
// the value and the reverse-mode gradient stay bounded near degeneracies.

const EPS_LEN2 = 1e-12; // length/area softening (ε²)
const EPS_SIN = 1e-4; // angular clamp

function split3(m) {
  return [m.slice([0, 0], [-1, 1]), m.slice([0, 1], [-1, 1]), m.slice([0, 2], [-1, 1])];
}
function crossRows(u, v) {
  const [ux, uy, uz] = split3(u);
  const [vx, vy, vz] = split3(v);
  return tf.concat(
    [uy.mul(vz).sub(uz.mul(vy)), uz.mul(vx).sub(ux.mul(vz)), ux.mul(vy).sub(uy.mul(vx))],
    1
  );
}

export class MeshEnergy {
  /**
   * @param {object} topo  { faces, edges, corners, Lmatrix, V }
   * @param {object} opts  weights, targets, Vstar, entropy(centers,h)
   */
  constructor(topo, opts = {}) {
    const F = topo.faces,
      E = topo.edges,
      C = topo.corners,
      V = topo.V;
    this.V = V;

    this.faceA = tf.tensor1d(
      F.map((f) => f[0]),
      'int32'
    );
    this.faceB = tf.tensor1d(
      F.map((f) => f[1]),
      'int32'
    );
    this.faceC = tf.tensor1d(
      F.map((f) => f[2]),
      'int32'
    );
    this.edgeI = tf.tensor1d(
      E.map((e) => e[0]),
      'int32'
    );
    this.edgeJ = tf.tensor1d(
      E.map((e) => e[1]),
      'int32'
    );
    this.cApex = tf.tensor1d(C.apex, 'int32');
    this.cL = tf.tensor1d(C.la, 'int32');
    this.cR = tf.tensor1d(C.ra, 'int32');
    this.Lmat = tf.tensor2d(topo.Lmatrix);

    // entropy KDE constants
    const ent = opts.entropy || {};
    this.centers = tf.tensor1d(ent.centers || [0, 1]);
    this.h = ent.h || 0.1;
    this.B = (ent.centers || [0, 1]).length;

    // fidelity
    this.targets = tf.tensor2d(opts.targets || new Array(V).fill([0, 0, 0]));
    this.fidW = tf.tensor1d(opts.fidWeights || new Array(V).fill(0));

    this.Vstar = opts.Vstar ?? 0;

    this.w = Object.assign(
      { area: 0.5, vol: 0, fit: 0, len: 0, ang: 0.05, smooth: 0.1 },
      opts.weights || {}
    );
  }

  setWeights(w) {
    Object.assign(this.w, w);
  }
  setVstar(v) {
    this.Vstar = v;
  }

  // Individual terms as tensors (kept inside the caller's autodiff graph).
  _terms(P) {
    const a = tf.gather(P, this.faceA);
    const b = tf.gather(P, this.faceB);
    const c = tf.gather(P, this.faceC);
    const u = b.sub(a),
      v = c.sub(a);
    const w = crossRows(u, v);
    const wlen = w.square().sum(1).add(EPS_LEN2).sqrt();
    const area = wlen.sum().mul(0.5);

    // signed volume  (1/6) Σ a·(b×c)
    const bc = crossRows(b, c);
    const volume = a.mul(bc).sum(1).sum().div(6);
    const volMatch = volume.sub(this.Vstar).square().add(1e-8).sqrt();

    // fidelity
    const diff = P.sub(this.targets);
    const fid = diff.square().sum(1).mul(this.fidW).sum();

    // edge-length entropy
    const pa = tf.gather(P, this.edgeI);
    const pb = tf.gather(P, this.edgeJ);
    const len = pb.sub(pa).square().sum(1).add(EPS_LEN2).sqrt(); // [E]
    const dd = len.reshape([-1, 1]).sub(this.centers.reshape([1, -1]));
    const kern = dd
      .square()
      .div(-2 * this.h * this.h)
      .exp();
    const rho = kern.sum(0).add(1e-9);
    const pk = rho.div(rho.sum());
    const entropy = pk.mul(pk.add(1e-12).log()).sum().neg(); // H
    const negH = entropy.neg();

    // angular functional Σ (θ − 60°)²
    const A = tf.gather(P, this.cApex);
    const uu = tf.gather(P, this.cL).sub(A);
    const vv = tf.gather(P, this.cR).sub(A);
    const nu = uu.square().sum(1).add(EPS_LEN2).sqrt();
    const nv = vv.square().sum(1).add(EPS_LEN2).sqrt();
    const cosT = uu
      .mul(vv)
      .sum(1)
      .div(nu.mul(nv))
      .clipByValue(-1 + EPS_SIN, 1 - EPS_SIN);
    const theta = cosT.acos();
    const ang = theta
      .sub(Math.PI / 3)
      .square()
      .sum();

    // Laplacian smoothness ||L P||²
    const LP = this.Lmat.matMul(P);
    const lap = LP.square().sum();

    return { area, volume, volMatch, fid, entropy, negH, ang, lap };
  }

  // Scalar loss for computeGradients.
  energy(P) {
    const t = this._terms(P);
    const W = this.w;
    return t.area
      .mul(W.area)
      .add(t.volMatch.mul(W.vol))
      .add(t.fid.mul(W.fit))
      .add(t.negH.mul(W.len))
      .add(t.ang.mul(W.ang))
      .add(t.lap.mul(W.smooth));
  }

  // Numeric breakdown for the live-metrics panel (§7).
  report(P) {
    return tf.tidy(() => {
      const t = this._terms(P);
      return {
        area: t.area.dataSync()[0],
        volume: t.volume.dataSync()[0],
        entropy: t.entropy.dataSync()[0],
        entropyMax: Math.log(this.centers.size),
        angular: t.ang.dataSync()[0],
        laplacian: t.lap.dataSync()[0],
        fidelity: t.fid.dataSync()[0],
      };
    });
  }

  dispose() {
    [
      this.faceA,
      this.faceB,
      this.faceC,
      this.edgeI,
      this.edgeJ,
      this.cApex,
      this.cL,
      this.cR,
      this.Lmat,
      this.centers,
      this.targets,
      this.fidW,
    ].forEach((x) => x && x.dispose());
  }
}
