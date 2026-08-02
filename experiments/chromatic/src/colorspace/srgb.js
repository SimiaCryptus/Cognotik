// sRGB <-> linear RGB
// Values are in [0, 1].

function srgbToLinearChannel(c) {
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function linearToSrgbChannel(c) {
  return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
}

export function srgbToLinear({ r, g, b }) {
  return {
    r: srgbToLinearChannel(r),
    g: srgbToLinearChannel(g),
    b: srgbToLinearChannel(b),
  };
}

export function linearToSrgb({ r, g, b }) {
  return {
    r: linearToSrgbChannel(r),
    g: linearToSrgbChannel(g),
    b: linearToSrgbChannel(b),
  };
}
