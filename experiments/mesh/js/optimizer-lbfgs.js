/**
 * Wrapper for TensorFlow.js optimizers to facilitate experimentation.
 * Assumes 'tf' is available globally (e.g. via CDN).
 */
export class OptimizerLbfgs {
  constructor(learningRate) {
    this.learningRate = learningRate;
    this.m = 10; // History size
    this.history = [];
    this.lastX = null;
    this.lastGrad = null;
  }

  /**
   * Computes gradients for the given loss function.
   * @param {Function} lossFunction - Function that returns a scalar tensor.
   */
  computeGradients(lossFunction) {
    return tf.variableGrads(lossFunction);
  }

  /**
   * Applies gradients to variables.
   * @param {Object} grads - Gradients returned by computeGradients.
   */
  applyGradients(grads) {
    tf.tidy(() => {
      const varNames = Object.keys(grads).sort();
      const allVars = tf.engine().state.registeredVariables;

      const trainableVars = [];
      const gradTensors = [];
      varNames.forEach((name) => {
        if (allVars[name]) {
          trainableVars.push(allVars[name]);
          gradTensors.push(grads[name]);
        }
      });

      if (trainableVars.length === 0) return;

      const x = tf.concat(trainableVars.map((v) => v.flatten()));
      const g = tf.concat(gradTensors.map((t) => t.flatten()));

      if (this.lastX) {
        const s = x.sub(this.lastX);
        const y = g.sub(this.lastGrad);
        const ys = y.dot(s);

        if (ys.dataSync()[0] > 1e-8) {
          const rho = tf.div(1.0, ys);
          this.history.push({
            s: tf.keep(s),
            y: tf.keep(y),
            rho: tf.keep(rho),
          });
          if (this.history.length > this.m) {
            const old = this.history.shift();
            tf.dispose([old.s, old.y, old.rho]);
          }
        }
      }

      let q = g;
      const alphas = [];
      for (let i = this.history.length - 1; i >= 0; i--) {
        const { s, y, rho } = this.history[i];
        const alpha = rho.mul(s.dot(q));
        alphas[i] = alpha;
        q = q.sub(y.mul(alpha));
      }

      let r = q;
      if (this.history.length > 0) {
        const { s, y } = this.history[this.history.length - 1];
        const gamma = s.dot(y).div(y.dot(y));
        r = r.mul(gamma);
      }

      for (let i = 0; i < this.history.length; i++) {
        const { s, y, rho } = this.history[i];
        const beta = rho.mul(y.dot(r));
        r = r.add(s.mul(alphas[i].sub(beta)));
      }

      const direction = r.neg();
      const xNew = x.add(direction.mul(this.learningRate));

      let offset = 0;
      trainableVars.forEach((v) => {
        const size = v.shape.reduce((a, b) => a * b, 1);
        const newVal = xNew.slice([offset], [size]).reshape(v.shape);
        v.assign(newVal);
        offset += size;
      });

      if (this.lastX) tf.dispose(this.lastX);
      if (this.lastGrad) tf.dispose(this.lastGrad);
      this.lastX = tf.keep(x);
      this.lastGrad = tf.keep(g);
    });
  }

  /**
   * Updates the learning rate.
   * @param {number} lr
   */
  setLearningRate(lr) {
    this.learningRate = lr;
    this.history.forEach((h) => tf.dispose([h.s, h.y, h.rho]));
    this.history = [];
    if (this.lastX) tf.dispose(this.lastX);
    if (this.lastGrad) tf.dispose(this.lastGrad);
    this.lastX = null;
    this.lastGrad = null;
  }
}
