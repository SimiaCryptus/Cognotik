#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# install_vad_deps.sh
#
# Installs Python dependencies for VAD-based silence segmentation.
#
# Usage:
#   ./scripts/install_vad_deps.sh [silero|webrtc|all]
#
# Default: all
# =============================================================================

BACKEND="${1:-all}"

echo "=== Installing VAD Dependencies ==="
echo "  Backend: ${BACKEND}"
echo ""

# Common dependencies
echo "--- Installing common dependencies ---"
pip install --upgrade pip
pip install numpy

case "$BACKEND" in
  silero|all)
    echo ""
    echo "--- Installing Silero VAD dependencies ---"
     pip install --ignore-installed torch torchaudio
    echo "  Silero VAD model will be downloaded on first run via torch.hub."
    ;;&  # fall through for 'all'
  webrtc|all)
    echo ""
    echo "--- Installing WebRTC VAD dependencies ---"
    pip install webrtcvad
    ;;
  silero)
    # already handled above, just need to not fall into *)
    ;;
  *)
    echo "ERROR: Unknown backend '${BACKEND}'. Use: silero, webrtc, or all"
    exit 1
    ;;
esac

echo ""
echo "=== Installation Complete ==="
echo ""
echo "Usage:"
echo "  # Run with Silero VAD (default, most accurate):"
echo "  VAD_BACKEND=silero ./scripts/segment_silence.sh"
echo ""
echo "  # Run with WebRTC VAD (lightweight, fast):"
echo "  VAD_BACKEND=webrtc ./scripts/segment_silence.sh"
echo ""
echo "Done."