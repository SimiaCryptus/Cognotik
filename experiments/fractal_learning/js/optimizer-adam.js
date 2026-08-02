/**
 * Adam optimizer wrapper. Supports freeze-by-variable via `activeVars`.
 * The standard tf.train.adam accumulates moments per Variable's name.
 */
export class OptimizerAdam {
  constructor(learningRate = 0.02) {
    this.name = 'adam';
    this.learningRate = learningRate;
    this.optimizer = tf.train.adam(learningRate);
  }

  /**
   * Take one step. lossFn must return a scalar tf.Scalar.
   * activeVars is the list of tf.Variables to update (frozen vars excluded).
   * Returns the scalar loss value (number).
   */
  step(lossFn, activeVars) {
    const lossT = this.optimizer.minimize(lossFn, true, activeVars);
    const val = lossT.dataSync()[0];
    lossT.dispose();
    return val;
  }

  setLearningRate(lr) {
    if (lr === this.learningRate) return;
    this.learningRate = lr;
    // Re-instantiate (resets moments).
    this.optimizer.dispose?.();
    this.optimizer = tf.train.adam(lr);
  }

  dispose() {
    this.optimizer.dispose?.();
  }
}
