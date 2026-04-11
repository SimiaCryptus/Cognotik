#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# install_video_deps.sh
#
# Installs Python dependencies for video-based scene segmentation and
# annotation using OpenCV and related libraries.
#
# Usage:
#   ./scripts/install_video_deps.sh [minimal|full|all]
#
# Profiles:
#   minimal  - OpenCV headless + numpy (scene detection only)
#   full     - OpenCV full + scikit-image + matplotlib (with annotation/viz)
#   all      - Everything including optional extras (default)
# =============================================================================

PROFILE="${1:-all}"

echo "=== Installing Video Segmentation Dependencies ==="
echo "  Profile: ${PROFILE}"
echo ""

# Check Python3
if ! command -v python3 &>/dev/null; then
  echo "ERROR: python3 is not installed."
  echo "  Linux:  sudo apt install python3 python3-pip"
  echo "  macOS:  brew install python3"
  exit 1
fi

# Check pip
if ! python3 -m pip --version &>/dev/null; then
  echo "ERROR: pip is not available for python3."
  echo "  Install with: python3 -m ensurepip --upgrade"
  exit 1
fi

# Check ffmpeg
if ! command -v ffmpeg &>/dev/null; then
  echo "WARNING: ffmpeg is not installed. It is required by segment_video.sh."
  echo "  Run ./scripts/install_deps.sh to install it."
  echo ""
fi

# Check ffprobe
if ! command -v ffprobe &>/dev/null; then
  echo "WARNING: ffprobe is not installed. It is required by segment_video.sh."
  echo "  It is typically bundled with ffmpeg."
  echo ""
fi

PIP_INSTALL="python3 -m pip install --upgrade"

case "$PROFILE" in
  minimal)
    echo "--- Installing minimal dependencies ---"
    $PIP_INSTALL numpy
    $PIP_INSTALL opencv-python-headless
    ;;
  full)
    echo "--- Installing full dependencies ---"
    $PIP_INSTALL numpy
    $PIP_INSTALL opencv-python
    $PIP_INSTALL scikit-image
    $PIP_INSTALL matplotlib
    $PIP_INSTALL Pillow
    ;;
  all)
    echo "--- Installing all dependencies ---"
    $PIP_INSTALL numpy
    $PIP_INSTALL opencv-python
    $PIP_INSTALL scikit-image
    $PIP_INSTALL matplotlib
    $PIP_INSTALL Pillow
    $PIP_INSTALL scenedetect[opencv]
    ;;
  *)
    echo "ERROR: Unknown profile '${PROFILE}'. Use: minimal, full, or all."
    exit 1
    ;;
esac

echo ""
echo "--- Verifying installation ---"
python3 -c "
import sys

modules = {
    'numpy': 'numpy',
    'cv2': 'opencv-python',
}

profile = '${PROFILE}'
if profile in ('full', 'all'):
    modules['skimage'] = 'scikit-image'
    modules['matplotlib'] = 'matplotlib'
    modules['PIL'] = 'Pillow'
if profile == 'all':
    modules['scenedetect'] = 'scenedetect'

ok = True
for mod, pkg in modules.items():
    try:
        __import__(mod)
        ver = ''
        m = __import__(mod)
        if hasattr(m, '__version__'):
            ver = m.__version__
        elif hasattr(m, 'VERSION'):
            ver = str(m.VERSION)
        print(f'  ✓ {pkg:<30} {ver}')
    except ImportError:
        print(f'  ✗ {pkg:<30} NOT FOUND')
        ok = False

if not ok:
    print('\\nSome packages failed to install.', file=sys.stderr)
    sys.exit(1)
else:
    print('\\n  All video dependencies installed successfully.')
"

echo ""
echo "Done."
