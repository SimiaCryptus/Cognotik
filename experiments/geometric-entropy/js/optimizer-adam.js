/**
 * Wrapper for TensorFlow.js optimizers to facilitate experimentation.
 * Assumes 'tf' is available globally (e.g. via CDN).
 */
export class OptimizerAdam {
  constructor(learningRate) {
    this.learningRate = learningRate;
    this.optimizer = tf.train.adam(learningRate);
  }

  /**
   * Computes gradients for the given loss function.
   * @param {Function} lossFunction - Function that returns a scalar tensor.
   */
  computeGradients(lossFunction) {
    return this.optimizer.computeGradients(lossFunction);
  }

  /**
   * Applies gradients to variables.
   * @param {Object} grads - Gradients returned by computeGradients.
   */
  applyGradients(grads) {
    this.optimizer.applyGradients(grads);
  }

  /**
   * Updates the learning rate.
   * Currently re-instantiates the optimizer to reset state.
   * @param {number} lr
   */
  setLearningRate(lr) {
    this.learningRate = lr;
    this.optimizer = tf.train.adam(lr);
  }
}
