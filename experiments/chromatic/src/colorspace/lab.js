// XYZ <-> CIE Lab, Lab <-> Lch, plus sRGB <-> XYZ helpers.
// Uses D65 reference white.
import { srgbToLinear, linearToSrgb } from './srgb.js';

const DEG2RAD = Math.PI / 180;
const RAD2DEG = 180 / Math.PI;

// D65 reference white (2° observer), scaled so Y = 1.
const Xn = 0.95047;
const Yn = 1.0;
const Zn = 1.08883;

const EPS = 216 / 24389; // (6/29)^3
const KAPPA = 24389 / 27; // (29/3)^3

function fLab(t) {
  return t > EPS ? Math.cbrt(t) : (KAPPA * t + 16) / 116;
}

function fLabInv(t) {
  const t3 = t * t * t;
  return t3 > EPS ? t3 : (116 * t - 16) / KAPPA;
}

export function linearRgbToXyz({ r, g, b }) {
  return {
    x: 0.4123907993 * r + 0.3575843394 * g + 0.1804807884 * b,
    y: 0.2126390059 * r + 0.7151686788 * g + 0.0721923154 * b,
    z: 0.0193308187 * r + 0.119194779 * g + 0.9505321522 * b,
  };
}

export function xyzToLinearRgb({ x, y, z }) {
  return {
    r: 3.2409699419 * x - 1.5373831776 * y - 0.4986107603 * z,
    g: -0.9692436363 * x + 1.8759675015 * y + 0.0415550574 * z,
    b: 0.0556300797 * x - 0.2039769589 * y + 1.0569715142 * z,
  };
}

export function xyzToLab({ x, y, z }) {
  const fx = fLab(x / Xn);
  const fy = fLab(y / Yn);
  const fz = fLab(z / Zn);
  return {
    L: 116 * fy - 16,
    a: 500 * (fx - fy),
    b: 200 * (fy - fz),
  };
}

export function labToXyz({ L, a, b }) {
  const fy = (L + 16) / 116;
  const fx = fy + a / 500;
  const fz = fy - b / 200;
  return {
    x: Xn * fLabInv(fx),
    y: Yn * fLabInv(fy),
    z: Zn * fLabInv(fz),
  };
}

export function rgbToLab(rgb) {
  return xyzToLab(linearRgbToXyz(srgbToLinear(rgb)));
}

export function labToRgb(lab) {
  return linearToSrgb(xyzToLinearRgb(labToXyz(lab)));
}

export function labToLch({ L, a, b }) {
  const C = Math.hypot(a, b);
  let H = Math.atan2(b, a) * RAD2DEG;
  if (H < 0) H += 360;
  return { L, C, H };
}

export function lchToLab({ L, C, H }) {
  const h = H * DEG2RAD;
  return { L, a: C * Math.cos(h), b: C * Math.sin(h) };
}
