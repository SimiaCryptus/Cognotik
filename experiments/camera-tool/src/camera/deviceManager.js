// Camera enumeration & getUserMedia stream management.

export class DeviceManager {
  constructor() {
    this.stream = null;
    this.devices = [];
  }

  static supported() {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  }

  // Enumerate video input devices. Labels are only populated after a
  // permission grant, so callers may want to start a stream first.
  async enumerate() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
      this.devices = [];
      return this.devices;
    }
    const all = await navigator.mediaDevices.enumerateDevices();
    this.devices = all.filter((d) => d.kind === 'videoinput');
    return this.devices;
  }

  // Start a stream for a given deviceId (or default). Requests max resolution.
  async start(deviceId) {
    this.stop();
    const video = {
      width: { ideal: 4096 },
      height: { ideal: 2160 },
    };
    if (deviceId) {
      video.deviceId = { exact: deviceId };
    } else {
      video.facingMode = { ideal: 'environment' };
    }
    this.stream = await navigator.mediaDevices.getUserMedia({ video, audio: false });
    return this.stream;
  }

  getActiveSettings() {
    if (!this.stream) return null;
    const track = this.stream.getVideoTracks()[0];
    if (!track) return null;
    const settings = track.getSettings ? track.getSettings() : {};
    return {
      label: track.label || '',
      deviceId: settings.deviceId || '',
      width: settings.width || 0,
      height: settings.height || 0,
    };
  }

  stop() {
    if (this.stream) {
      this.stream.getTracks().forEach((t) => t.stop());
      this.stream = null;
    }
  }
}
