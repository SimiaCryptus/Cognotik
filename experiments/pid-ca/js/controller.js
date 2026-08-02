/**
 * controller.js — the per-cell PID mechanism (§3.3, §7.3).
 *
 * Deliberately knows nothing about grids, rendering, or discretisation:
 * it is a pure numeric map
 *   (e_(t-1), I_(t-1), e_t, gains) -> (P_t, I_t, D_t, u_t).
 */

/** Apply the optional anti-windup clamp to an integral accumulator. */
export function clampIntegral(value, cfg) {
  if (!cfg.integralClamp) return value;
  if (value < cfg.integralMin) return cfg.integralMin;
  if (value > cfg.integralMax) return cfg.integralMax;
  return value;
}

/**
 * One controller update for a single cell.
 *
 * @param {number} prevError    e_(t-1)
 * @param {number} prevIntegral I_(t-1)
 * @param {number} error        e_t = T - N_t(c)
 * @param {object} cfg          configuration providing kp/ki/kd and clamp settings
 * @param {object} [out]        optional reusable result object (avoids allocation in hot loops)
 * @returns {{p:number,i:number,d:number,u:number,error:number}}
 */
export function pidStep(prevError, prevIntegral, error, cfg, out) {
  const result = out || { p: 0, i: 0, d: 0, u: 0, error: 0 };
  const p = cfg.kp * error;
  const i = clampIntegral(prevIntegral + cfg.ki * error, cfg);
  const d = cfg.kd * (error - prevError);
  result.p = p;
  result.i = i;
  result.d = d;
  result.u = p + i + d;
  result.error = error;
  return result;
}

/** Convenience: fresh controller state for a cell whose first error is `error`. */
export function initialControllerState(error = 0) {
  return { prevError: error, integral: 0 };
}
