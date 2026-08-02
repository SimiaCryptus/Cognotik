// Profile schema & versioning helpers (Phase 1: in-memory only).
import { DEFAULT_THRESHOLDS } from '../analysis/defectDetector.js';

export const PROFILE_VERSION = 1;

function uuid() {
  if (crypto && crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// Build a profile object from analysis results.
export function buildProfile({ name, deviceSettings, defectMap, frameCount, thresholds }) {
  return {
    version: PROFILE_VERSION,
    id: uuid(),
    name: name || 'Untitled profile',
    createdAt: new Date().toISOString(),
    device: {
      label: deviceSettings ? deviceSettings.label : '',
      deviceId: deviceSettings ? deviceSettings.deviceId : '',
      resolution: {
        width: deviceSettings ? deviceSettings.width : defectMap ? defectMap.width : 0,
        height: deviceSettings ? deviceSettings.height : defectMap ? defectMap.height : 0,
      },
    },
    defects: defectMap ? defectMap.toJSON() : { encoding: 'sparse-coords', count: 0, pixels: [] },
    analysis: {
      frameCount: frameCount || 0,
      thresholds: Object.assign({}, DEFAULT_THRESHOLDS, thresholds || {}),
    },
  };
}
