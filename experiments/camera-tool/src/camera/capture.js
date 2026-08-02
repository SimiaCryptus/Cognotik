// Frame & still capture helpers built around a <video> element.
import { createCanvas, grabFrame } from '../util/imageData.js';

export class FrameCapture {
  constructor(video) {
    this.video = video;
    this.canvas = createCanvas(2, 2);
  }

  // Wait until the video has valid dimensions and a fresh frame.
  async ready() {
    const v = this.video;
    if (v.videoWidth && v.readyState >= 2) return;
    await new Promise((resolve) => {
      const check = () => {
        if (v.videoWidth && v.readyState >= 2) resolve();
        else requestAnimationFrame(check);
      };
      check();
    });
  }

  // Grab a single ImageData at native resolution.
  grab() {
    return grabFrame(this.video, this.canvas);
  }

  // Wait approximately one frame (for capturing a sequence with spacing).
  nextFrame() {
    return new Promise((resolve) => requestAnimationFrame(() => resolve()));
  }
}
