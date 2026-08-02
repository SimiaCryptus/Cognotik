/**
 * QQN (Quasi-Newton Quadratic) Optimizer for TensorFlow.js
 * Combines L-BFGS with a quadratic path line search.
 * Assumes 'tf' is available globally.
 */
export class OptimizerQQN {
  constructor(learningRate = 0.01) {
    this.learningRate = learningRate;
    this.m = 10; // History size
    this.history = [];
    this.lastX = null;
    this.lastGrad = null;
    this.epsilon = 1e-8;
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
   * @param {Function} lossFunction - Function to evaluate loss (needed for line search).
   */
  applyGradients(grads, lossFunction) {
    tf.tidy(() => {
      // 1. Prepare variables and gradients
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

      // 2. Update History (using previous step info)
      if (this.lastX) {
        const s = x.sub(this.lastX);
        const y = g.sub(this.lastGrad);
        const ys = y.dot(s);

        if (ys.dataSync()[0] > this.epsilon) {
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

      // 3. Compute L-BFGS Direction (d_lbfgs)
      let q = g;
      const alphas = new Array(this.history.length);

      // Backward pass
      for (let i = this.history.length - 1; i >= 0; i--) {
        const { s, y, rho } = this.history[i];
        const alpha = rho.mul(s.dot(q));
        alphas[i] = alpha;
        q = q.sub(y.mul(alpha));
      }

      // Scaling
      let r = q;
      if (this.history.length > 0) {
        const { s, y } = this.history[this.history.length - 1];
        const gamma = s.dot(y).div(y.dot(y));
        r = r.mul(gamma);
      }

      // Forward pass
      for (let i = 0; i < this.history.length; i++) {
        const { s, y, rho } = this.history[i];
        const beta = rho.mul(y.dot(r));
        r = r.add(s.mul(alphas[i].sub(beta)));
      }

      // d_lbfgs is -r (descent direction) scaled by LR
      const d_lbfgs = r.neg().mul(this.learningRate);

      // Steepest descent direction scaled by learning rate
      const d_sd = g.neg().mul(this.learningRate);

      // 4. Line Search for t in [0, 1]
      // Path: step(t) = t(1-t)*d_sd + t^2*d_lbfgs

      let bestT = 1.0; // Default to full L-BFGS if no loss function

      if (lossFunction) {
        // Helper to evaluate loss at t
        const evalAt = (t) => {
          return tf.tidy(() => {
            const t2 = t * t;
            const t1t = t * (1 - t);
            // step = t(1-t)*d_sd + t^2*d_lbfgs
            const step = d_sd.mul(t1t).add(d_lbfgs.mul(t2));
            const xNew = x.add(step);

            // Assign xNew to variables
            let offset = 0;
            trainableVars.forEach((v) => {
              const size = v.shape.reduce((a, b) => a * b, 1);
              const newVal = xNew.slice([offset], [size]).reshape(v.shape);
              v.assign(newVal);
              offset += size;
            });

            const loss = lossFunction();
            return loss.dataSync()[0];
          });
        };

        // Golden Section Search
        const gr = (Math.sqrt(5) - 1) / 2;
        let a = 0;
        let b = 1.0;
        let c = b - gr * (b - a);
        let d = a + gr * (b - a);

        let fc = evalAt(c);
        let fd = evalAt(d);

        // 10 iterations
        for (let i = 0; i < 10; i++) {
          if (fc < fd) {
            b = d;
            d = c;
            fd = fc;
            c = b - gr * (b - a);
            fc = evalAt(c);
          } else {
            a = c;
            c = d;
            fc = fd;
            d = a + gr * (b - a);
            fd = evalAt(d);
          }
        }
        bestT = (a + b) / 2;
      }

      // 5. Apply best step
      const t = bestT;
      const t2 = t * t;
      const t1t = t * (1 - t);
      const step = d_sd.mul(t1t).add(d_lbfgs.mul(t2));
      const xNew = x.add(step);

      let offset = 0;
      trainableVars.forEach((v) => {
        const size = v.shape.reduce((a, b) => a * b, 1);
        const newVal = xNew.slice([offset], [size]).reshape(v.shape);
        v.assign(newVal);
        offset += size;
      });

      // 6. Update state
      if (this.lastX) tf.dispose(this.lastX);
      if (this.lastGrad) tf.dispose(this.lastGrad);
      this.lastX = tf.keep(x);
      this.lastGrad = tf.keep(g);
    });
  }

  setLearningRate(lr) {
    this.learningRate = lr;
    // Reset history
    this.history.forEach((h) => tf.dispose([h.s, h.y, h.rho]));
    this.history = [];
    if (this.lastX) tf.dispose(this.lastX);
    if (this.lastGrad) tf.dispose(this.lastGrad);
    this.lastX = null;
    this.lastGrad = null;
  }
}
