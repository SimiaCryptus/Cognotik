// Common interface + factory.
import { GD } from './gd.js';
import { Adam } from './adam.js';
import { LBFGS } from './lbfgs.js';
import { QQN } from './qqn.js';

export function createOptimizer(name, params) {
  switch (name) {
    case 'gd':
      return new GD(params);
    case 'adam':
      return new Adam(params);
    case 'lbfgs':
      return new LBFGS(params);
    case 'qqn':
      return new QQN(params);
    default:
      return new GD(params);
  }
}
