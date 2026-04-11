import sys
import os
import json
import math
import numpy as np
import cv2
from pathlib import Path
from collections import OrderedDict

# ---------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------
video_path         = sys.argv[1]
json_out           = sys.argv[2]
txt_out            = sys.argv[3]
srt_out            = sys.argv[4]
thumb_dir          = sys.argv[5]
annotated_out      = sys.argv[6]
detection_method   = sys.argv[7]
content_threshold  = float(sys.argv[8])
diff_threshold     = float(sys.argv[9])
min_scene_sec      = float(sys.argv[10])
max_scene_sec      = float(sys.argv[11])
analysis_fps       = float(sys.argv[12])
thumbnail_width    = int(sys.argv[13])
generate_annotated = sys.argv[14].lower() == "true"
annotation_style   = sys.argv[15]
video_duration_str = sys.argv[16]

# ---------------------------------------------------------------------------
# Open video
# ---------------------------------------------------------------------------
cap = cv2.VideoCapture(video_path)
if not cap.isOpened():
    print(f"ERROR: Cannot open video: {video_path}", file=sys.stderr)
    sys.exit(1)

native_fps = cap.get(cv2.CAP_PROP_FPS)
total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

if native_fps <= 0:
    native_fps = 30.0

total_duration = total_frames / native_fps
if total_duration <= 0:
    try:
        total_duration = float(video_duration_str)
    except:
        total_duration = 0.0

print(f"  [VIDEO] {width}x{height} @ {native_fps:.2f} fps, {total_frames} frames, {total_duration:.1f}s")

# Frame sampling interval
frame_interval = max(1, int(round(native_fps / analysis_fps)))
print(f"  [SAMPLE] Analyzing every {frame_interval} frame(s) (~{native_fps/frame_interval:.1f} fps)")

# ---------------------------------------------------------------------------
# Frame analysis utilities
# ---------------------------------------------------------------------------
def compute_histogram(frame_gray):
    """Compute normalized grayscale histogram."""
    hist = cv2.calcHist([frame_gray], [0], None, [256], [0, 256])
    cv2.normalize(hist, hist)
    return hist.flatten()

def compute_color_histogram(frame_bgr):
    """Compute normalized color histogram (HSV)."""
    hsv = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2HSV)
    hist_h = cv2.calcHist([hsv], [0], None, [180], [0, 180])
    hist_s = cv2.calcHist([hsv], [1], None, [256], [0, 256])
    hist_v = cv2.calcHist([hsv], [2], None, [256], [0, 256])
    cv2.normalize(hist_h, hist_h)
    cv2.normalize(hist_s, hist_s)
    cv2.normalize(hist_v, hist_v)
    return hist_h.flatten(), hist_s.flatten(), hist_v.flatten()

def histogram_diff(hist1, hist2):
    """Compute histogram correlation difference (0 = identical, 1 = completely different)."""
    corr = cv2.compareHist(
        hist1.reshape(-1, 1).astype(np.float32),
        hist2.reshape(-1, 1).astype(np.float32),
        cv2.HISTCMP_CORREL
    )
    return 1.0 - max(0.0, corr)

def frame_diff_score(prev_gray, curr_gray):
    """Compute mean absolute difference between two grayscale frames."""
    diff = cv2.absdiff(prev_gray, curr_gray)
    return float(np.mean(diff))

def compute_motion_score(prev_gray, curr_gray):
    """Estimate motion using optical flow magnitude."""
    flow = cv2.calcOpticalFlowFarneback(
        prev_gray, curr_gray,
        None, 0.5, 3, 15, 3, 5, 1.2, 0
    )
    mag, _ = cv2.cartToPolar(flow[..., 0], flow[..., 1])
    return float(np.mean(mag)), float(np.max(mag))

def compute_brightness_stats(frame_gray):
    """Compute brightness statistics."""
    mean_val = float(np.mean(frame_gray))
    std_val = float(np.std(frame_gray))
    return mean_val, std_val

def compute_color_stats(frame_bgr):
    """Compute per-channel color statistics."""
    hsv = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2HSV)
    return {
        "hue_mean": float(np.mean(hsv[:, :, 0])),
        "saturation_mean": float(np.mean(hsv[:, :, 1])),
        "value_mean": float(np.mean(hsv[:, :, 2])),
    }

def compute_edge_density(frame_gray):
    """Compute edge density using Canny edge detector."""
    edges = cv2.Canny(frame_gray, 50, 150)
    return float(np.count_nonzero(edges)) / edges.size

def classify_transition(prev_gray, curr_gray, diff_score, hist_diff_val):
    """Classify the type of scene transition."""
    prev_brightness = float(np.mean(prev_gray))
    curr_brightness = float(np.mean(curr_gray))
    brightness_change = abs(curr_brightness - prev_brightness)

    # Hard cut: large sudden change in both histogram and pixel difference
    if hist_diff_val > 0.5 and diff_score > 40:
        return "cut"

    # Fade to/from black
    if (prev_brightness < 30 or curr_brightness < 30) and brightness_change > 50:
        if curr_brightness < 30:
            return "fade_out"
        else:
            return "fade_in"

    # Dissolve: moderate histogram change with lower pixel difference
    if hist_diff_val > 0.2 and diff_score > 15:
        return "dissolve"

    # Gradual change
    if hist_diff_val > content_threshold:
        return "gradual"

    return "cut"

def extract_thumbnail(cap_obj, timestamp, out_path, thumb_w):
    """Extract a single thumbnail frame at the given timestamp."""
    cap_obj.set(cv2.CAP_PROP_POS_MSEC, timestamp * 1000.0)
    ret, frame = cap_obj.read()
    if not ret or frame is None:
        return False
    h, w = frame.shape[:2]
    if w > 0:
        scale = thumb_w / w
        new_h = int(h * scale)
        frame = cv2.resize(frame, (thumb_w, new_h), interpolation=cv2.INTER_AREA)
    cv2.imwrite(out_path, frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return True

# ---------------------------------------------------------------------------
# Scene detection: Content-based
# ---------------------------------------------------------------------------
def detect_scenes_content(cap_obj):
    """Detect scenes using histogram correlation and frame differencing."""
    scenes = []
    cap_obj.set(cv2.CAP_PROP_POS_FRAMES, 0)

    ret, prev_frame = cap_obj.read()
    if not ret:
        return scenes

    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)
    prev_hist = compute_histogram(prev_gray)

    frame_idx = 0
    scene_start_frame = 0
    scene_start_time = 0.0
    frame_scores = []
    analyzed = 0

    while True:
        frame_idx += 1
        ret, frame = cap_obj.read()
        if not ret:
            break

        if frame_idx % frame_interval != 0:
            continue

        analyzed += 1
        curr_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        curr_hist = compute_histogram(curr_gray)

        # Compute difference metrics
        hist_diff_val = histogram_diff(prev_hist, curr_hist)
        diff_score = frame_diff_score(prev_gray, curr_gray)

        # Combined score (weighted)
        combined = 0.6 * hist_diff_val + 0.4 * (diff_score / 255.0)

        current_time = frame_idx / native_fps
        scene_duration = current_time - scene_start_time

        frame_scores.append({
            "frame": frame_idx,
            "time": current_time,
            "hist_diff": hist_diff_val,
            "pixel_diff": diff_score,
            "combined": combined,
        })

        is_scene_break = False

        # Detect scene boundary
        if combined > content_threshold and scene_duration >= min_scene_sec:
            is_scene_break = True
        elif scene_duration >= max_scene_sec:
            is_scene_break = True

        if is_scene_break:
            transition = classify_transition(prev_gray, curr_gray, diff_score, hist_diff_val)
            scenes.append({
                "start_frame": scene_start_frame,
                "end_frame": frame_idx,
                "start_time": scene_start_time,
                "end_time": current_time,
                "transition": transition,
                "score": round(combined, 4),
            })
            scene_start_frame = frame_idx
            scene_start_time = current_time

        prev_gray = curr_gray
        prev_hist = curr_hist

        if analyzed % 500 == 0:
            pct = (frame_idx / total_frames * 100) if total_frames > 0 else 0
            print(f"    Analyzed {analyzed} frames ({pct:.0f}%)...")

    # Final scene
    final_time = total_frames / native_fps if total_frames > 0 else scene_start_time
    if final_time > scene_start_time + 0.1:
        scenes.append({
            "start_frame": scene_start_frame,
            "end_frame": total_frames,
            "start_time": scene_start_time,
            "end_time": final_time,
            "transition": "end",
            "score": 0.0,
        })

    return scenes

# ---------------------------------------------------------------------------
# Scene detection: Simple threshold
# ---------------------------------------------------------------------------
def detect_scenes_threshold(cap_obj):
    """Detect scenes using simple frame difference threshold."""
    scenes = []
    cap_obj.set(cv2.CAP_PROP_POS_FRAMES, 0)

    ret, prev_frame = cap_obj.read()
    if not ret:
        return scenes

    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)

    frame_idx = 0
    scene_start_frame = 0
    scene_start_time = 0.0
    analyzed = 0

    while True:
        frame_idx += 1
        ret, frame = cap_obj.read()
        if not ret:
            break

        if frame_idx % frame_interval != 0:
            continue

        analyzed += 1
        curr_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        diff_score = frame_diff_score(prev_gray, curr_gray)

        current_time = frame_idx / native_fps
        scene_duration = current_time - scene_start_time

        is_scene_break = False
        if diff_score > diff_threshold and scene_duration >= min_scene_sec:
            is_scene_break = True
        elif scene_duration >= max_scene_sec:
            is_scene_break = True

        if is_scene_break:
            scenes.append({
                "start_frame": scene_start_frame,
                "end_frame": frame_idx,
                "start_time": scene_start_time,
                "end_time": current_time,
                "transition": "cut",
                "score": round(diff_score / 255.0, 4),
            })
            scene_start_frame = frame_idx
            scene_start_time = current_time

        prev_gray = curr_gray

        if analyzed % 500 == 0:
            pct = (frame_idx / total_frames * 100) if total_frames > 0 else 0
            print(f"    Analyzed {analyzed} frames ({pct:.0f}%)...")

    final_time = total_frames / native_fps if total_frames > 0 else scene_start_time
    if final_time > scene_start_time + 0.1:
        scenes.append({
            "start_frame": scene_start_frame,
            "end_frame": total_frames,
            "start_time": scene_start_time,
            "end_time": final_time,
            "transition": "end",
            "score": 0.0,
        })

    return scenes

# ---------------------------------------------------------------------------
# Scene detection: Adaptive threshold
# ---------------------------------------------------------------------------
def detect_scenes_adaptive(cap_obj):
    """Detect scenes using adaptive threshold based on local statistics."""
    # First pass: collect all frame difference scores
    cap_obj.set(cv2.CAP_PROP_POS_FRAMES, 0)

    ret, prev_frame = cap_obj.read()
    if not ret:
        return []

    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)

    frame_idx = 0
    all_scores = []

    print("    [PASS 1] Computing frame differences...")
    while True:
        frame_idx += 1
        ret, frame = cap_obj.read()
        if not ret:
            break

        if frame_idx % frame_interval != 0:
            continue

        curr_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        diff_score = frame_diff_score(prev_gray, curr_gray)
        current_time = frame_idx / native_fps

        all_scores.append({
            "frame": frame_idx,
            "time": current_time,
            "diff": diff_score,
        })

        prev_gray = curr_gray

    if not all_scores:
        return []

    # Compute adaptive threshold using rolling statistics
    diffs = np.array([s["diff"] for s in all_scores])
    window_size = max(10, int(analysis_fps * 30))  # 30-second window

    print("    [PASS 2] Computing adaptive thresholds...")
    adaptive_thresholds = np.zeros_like(diffs)
    for i in range(len(diffs)):
        start = max(0, i - window_size // 2)
        end = min(len(diffs), i + window_size // 2)
        local_mean = np.mean(diffs[start:end])
        local_std = np.std(diffs[start:end])
        # Threshold = mean + 2*std, but at least diff_threshold
        adaptive_thresholds[i] = max(diff_threshold, local_mean + 2.0 * local_std)

    # Second pass: detect scenes using adaptive thresholds
    scenes = []
    scene_start_time = 0.0
    scene_start_frame = 0

    for i, score_info in enumerate(all_scores):
        current_time = score_info["time"]
        scene_duration = current_time - scene_start_time

        is_scene_break = False
        if diffs[i] > adaptive_thresholds[i] and scene_duration >= min_scene_sec:
            is_scene_break = True
        elif scene_duration >= max_scene_sec:
            is_scene_break = True

        if is_scene_break:
            scenes.append({
                "start_frame": scene_start_frame,
                "end_frame": score_info["frame"],
                "start_time": scene_start_time,
                "end_time": current_time,
                "transition": "cut",
                "score": round(diffs[i] / 255.0, 4),
            })
            scene_start_frame = score_info["frame"]
            scene_start_time = current_time

    final_time = total_frames / native_fps if total_frames > 0 else scene_start_time
    if final_time > scene_start_time + 0.1:
        scenes.append({
            "start_frame": scene_start_frame,
            "end_frame": total_frames,
            "start_time": scene_start_time,
            "end_time": final_time,
            "transition": "end",
            "score": 0.0,
        })

    return scenes

# ---------------------------------------------------------------------------
# Enrich scenes with per-scene metadata
# ---------------------------------------------------------------------------
def enrich_scenes(cap_obj, scenes):
    """Add motion, brightness, color, and edge statistics to each scene."""
    print(f"  [ENRICH] Computing per-scene statistics for {len(scenes)} scene(s)...")

    for i, scene in enumerate(scenes):
        mid_time = (scene["start_time"] + scene["end_time"]) / 2.0
        cap_obj.set(cv2.CAP_PROP_POS_MSEC, mid_time * 1000.0)
        ret, mid_frame = cap_obj.read()

        if not ret or mid_frame is None:
            scene["brightness_mean"] = 0.0
            scene["brightness_std"] = 0.0
            scene["color"] = {"hue_mean": 0, "saturation_mean": 0, "value_mean": 0}
            scene["edge_density"] = 0.0
            scene["motion_mean"] = 0.0
            scene["motion_max"] = 0.0
            continue

        mid_gray = cv2.cvtColor(mid_frame, cv2.COLOR_BGR2GRAY)

        # Brightness
        b_mean, b_std = compute_brightness_stats(mid_gray)
        scene["brightness_mean"] = round(b_mean, 1)
        scene["brightness_std"] = round(b_std, 1)

        # Color
        scene["color"] = {k: round(v, 1) for k, v in compute_color_stats(mid_frame).items()}

        # Edge density
        scene["edge_density"] = round(compute_edge_density(mid_gray), 4)

        # Motion: compare start and mid frames
        cap_obj.set(cv2.CAP_PROP_POS_MSEC, scene["start_time"] * 1000.0)
        ret2, start_frame = cap_obj.read()
        if ret2 and start_frame is not None:
            start_gray = cv2.cvtColor(start_frame, cv2.COLOR_BGR2GRAY)
            # Resize for faster optical flow
            small_h = 240
            scale = small_h / mid_gray.shape[0] if mid_gray.shape[0] > 0 else 1
            small_w = int(mid_gray.shape[1] * scale)
            if small_w > 0 and small_h > 0:
                sg = cv2.resize(start_gray, (small_w, small_h))
                mg = cv2.resize(mid_gray, (small_w, small_h))
                m_mean, m_max = compute_motion_score(sg, mg)
                scene["motion_mean"] = round(m_mean, 2)
                scene["motion_max"] = round(m_max, 2)
            else:
                scene["motion_mean"] = 0.0
                scene["motion_max"] = 0.0
        else:
            scene["motion_mean"] = 0.0
            scene["motion_max"] = 0.0

    return scenes

# ---------------------------------------------------------------------------
# Generate thumbnails
# ---------------------------------------------------------------------------
def generate_thumbnails(cap_obj, scenes, thumb_dir_path, thumb_w):
    """Extract a representative thumbnail for each scene."""
    print(f"  [THUMB] Generating {len(scenes)} thumbnail(s)...")
    thumb_paths = []
    for i, scene in enumerate(scenes):
        # Use a frame 1/3 into the scene (avoids transition artifacts)
        thumb_time = scene["start_time"] + (scene["end_time"] - scene["start_time"]) * 0.33
        thumb_path = os.path.join(thumb_dir_path, f"scene_{i+1:04d}.jpg")
        ok = extract_thumbnail(cap_obj, thumb_time, thumb_path, thumb_w)
        if ok:
            thumb_paths.append(thumb_path)
            scene["thumbnail"] = os.path.basename(thumb_path)
        else:
            scene["thumbnail"] = None
    return thumb_paths

# ---------------------------------------------------------------------------
# Generate annotated video
# ---------------------------------------------------------------------------
def generate_annotated_video(input_path, output_path, scenes, style):
    """Create a copy of the video with scene boundary overlays using FFmpeg drawtext."""
    print(f"  [ANNOTATE] Generating annotated video...")

    # Build FFmpeg drawtext filter chain
    filters = []
    for i, scene in enumerate(scenes):
        scene_num = i + 1
        start_t = scene["start_time"]
        end_t = min(scene["end_time"], start_t + 3.0)  # Show label for up to 3 seconds
        transition = scene.get("transition", "cut")

        if style == "full":
            dur_str = f"{scene['end_time'] - scene['start_time']:.1f}s"
            text = f"Scene {scene_num} | {transition} | {dur_str}"
            # Scene boundary flash (thin red bar at top)
            flash_end = min(start_t + 0.5, scene["end_time"])
            filters.append(
                f"drawbox=x=0:y=0:w=iw:h=4:color=red@0.8:t=fill"
                f":enable='between(t,{start_t:.3f},{flash_end:.3f})'"
            )
            # Text overlay
            filters.append(
                f"drawtext=text='{text}'"
                f":fontsize=24:fontcolor=white:borderw=2:bordercolor=black"
                f":x=20:y=30"
                f":enable='between(t,{start_t:.3f},{end_t:.3f})'"
            )
            # Scene number in corner throughout scene
            filters.append(
                f"drawtext=text='S{scene_num}'"
                f":fontsize=18:fontcolor=white@0.6:borderw=1:bordercolor=black@0.6"
                f":x=w-tw-10:y=10"
                f":enable='between(t,{start_t:.3f},{scene['end_time']:.3f})'"
            )
        else:  # minimal
            filters.append(
                f"drawtext=text='Scene {scene_num}'"
                f":fontsize=20:fontcolor=white:borderw=1:bordercolor=black"
                f":x=10:y=10"
                f":enable='between(t,{start_t:.3f},{end_t:.3f})'"
            )

    if not filters:
        print("    No scenes to annotate, skipping.")
        return False

    filter_chain = ",".join(filters)

    cmd = [
        "ffmpeg", "-hide_banner", "-nostdin",
        "-i", input_path,
        "-vf", filter_chain,
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-c:a", "copy",
        "-y", output_path,
        "-loglevel", "warning", "-stats"
    ]

    import subprocess
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"    WARNING: Annotated video generation failed: {result.stderr[:500]}")
        return False

    return True

# ---------------------------------------------------------------------------
# Format timestamp for SRT
# ---------------------------------------------------------------------------
def format_srt_ts(seconds):
    s = float(seconds)
    h = int(s // 3600)
    m = int((s % 3600) // 60)
    sec = int(s % 60)
    ms = int((s - int(s)) * 1000)
    return f"{h:02d}:{m:02d}:{sec:02d},{ms:03d}"

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
print(f"  [DETECT] Method: {detection_method}")

# Run scene detection
if detection_method == "content":
    raw_scenes = detect_scenes_content(cap)
elif detection_method == "threshold":
    raw_scenes = detect_scenes_threshold(cap)
elif detection_method == "adaptive":
    raw_scenes = detect_scenes_adaptive(cap)
else:
    print(f"ERROR: Unknown detection method: {detection_method}", file=sys.stderr)
    sys.exit(1)

print(f"  [DETECT] Found {len(raw_scenes)} scene(s)")

if not raw_scenes:
    # Create a single scene spanning the whole video
    raw_scenes = [{
        "start_frame": 0,
        "end_frame": total_frames,
        "start_time": 0.0,
        "end_time": total_duration,
        "transition": "none",
        "score": 0.0,
    }]
    print("  [INFO] No scene breaks detected; treating entire video as one scene.")

# Enrich scenes with metadata
scenes = enrich_scenes(cap, raw_scenes)

# Round all time values
for scene in scenes:
    scene["start_time"] = round(scene["start_time"], 3)
    scene["end_time"] = round(scene["end_time"], 3)
    scene["duration"] = round(scene["end_time"] - scene["start_time"], 3)

# Generate thumbnails
generate_thumbnails(cap, scenes, thumb_dir, thumbnail_width)

# Compute summary statistics
total_scene_dur = sum(s["duration"] for s in scenes)
avg_scene_dur = total_scene_dur / len(scenes) if scenes else 0
min_dur = min(s["duration"] for s in scenes) if scenes else 0
max_dur = max(s["duration"] for s in scenes) if scenes else 0

transition_counts = {}
for s in scenes:
    t = s.get("transition", "unknown")
    transition_counts[t] = transition_counts.get(t, 0) + 1

avg_brightness = np.mean([s.get("brightness_mean", 0) for s in scenes]) if scenes else 0
avg_motion = np.mean([s.get("motion_mean", 0) for s in scenes]) if scenes else 0
avg_edge = np.mean([s.get("edge_density", 0) for s in scenes]) if scenes else 0

summary = {
    "file": Path(video_path).stem,
    "duration": round(total_duration, 3),
    "resolution": f"{width}x{height}",
    "fps": round(native_fps, 2),
    "detection_method": detection_method,
    "settings": {
        "content_threshold": content_threshold if detection_method == "content" else None,
        "diff_threshold": diff_threshold if detection_method in ("threshold", "adaptive") else None,
        "min_scene_sec": min_scene_sec,
        "max_scene_sec": max_scene_sec,
        "analysis_fps": analysis_fps,
    },
    "statistics": {
        "total_scenes": len(scenes),
        "avg_scene_duration": round(avg_scene_dur, 3),
        "min_scene_duration": round(min_dur, 3),
        "max_scene_duration": round(max_dur, 3),
        "transitions": transition_counts,
        "avg_brightness": round(float(avg_brightness), 1),
        "avg_motion": round(float(avg_motion), 2),
        "avg_edge_density": round(float(avg_edge), 4),
    },
    "scenes": scenes,
}

# Write JSON output
with open(json_out, 'w') as f:
    json.dump(summary, f, indent=2)
print(f"  [WRITE] {json_out}")

# Write human-readable text summary
with open(txt_out, 'w') as f:
    f.write(f"Video Scene Segmentation Report: {Path(video_path).stem}\n")
    f.write(f"{'=' * 72}\n")
    f.write(f"Duration:         {total_duration:.1f}s\n")
    f.write(f"Resolution:       {width}x{height}\n")
    f.write(f"FPS:              {native_fps:.2f}\n")
    f.write(f"Detection Method: {detection_method}\n")
    f.write(f"\n")
    f.write(f"Summary:\n")
    f.write(f"  Total Scenes:     {len(scenes)}\n")
    f.write(f"  Avg Duration:     {avg_scene_dur:.1f}s\n")
    f.write(f"  Min Duration:     {min_dur:.1f}s\n")
    f.write(f"  Max Duration:     {max_dur:.1f}s\n")
    f.write(f"  Avg Brightness:   {avg_brightness:.1f}\n")
    f.write(f"  Avg Motion:       {avg_motion:.2f}\n")
    f.write(f"  Avg Edge Density: {avg_edge:.4f}\n")
    f.write(f"\n")
    f.write(f"Transitions:\n")
    for t_type, t_count in sorted(transition_counts.items()):
        f.write(f"  {t_type:<12}: {t_count}\n")
    f.write(f"\n")
    f.write(f"{'#':>4}  {'Start':>9}  {'End':>9}  {'Duration':>9}  {'Transition':<12}  {'Bright':>7}  {'Motion':>7}  {'Edges':>7}  {'Score':>6}\n")
    f.write(f"{'-' * 85}\n")
    for i, scene in enumerate(scenes, 1):
        start_str = f"{scene['start_time']:.3f}s"
        end_str = f"{scene['end_time']:.3f}s"
        dur_str = f"{scene['duration']:.3f}s"
        trans = scene.get("transition", "?")
        bright = scene.get("brightness_mean", 0)
        motion = scene.get("motion_mean", 0)
        edges = scene.get("edge_density", 0)
        score = scene.get("score", 0)
        f.write(f"{i:4d}  {start_str:>9}  {end_str:>9}  {dur_str:>9}  {trans:<12}  {bright:>6.1f}  {motion:>6.2f}  {edges:>6.4f}  {score:>5.3f}\n")
print(f"  [WRITE] {txt_out}")

# Write SRT-style markers
with open(srt_out, 'w') as f:
    for i, scene in enumerate(scenes, 1):
        trans = scene.get("transition", "?").upper()
        dur = scene["duration"]
        bright = scene.get("brightness_mean", 0)
        motion = scene.get("motion_mean", 0)
        f.write(f"{i}\n")
        f.write(f"{format_srt_ts(scene['start_time'])} --> {format_srt_ts(scene['end_time'])}\n")
        f.write(f"[SCENE {i}] {dur:.1f}s | {trans} | Bright:{bright:.0f} Motion:{motion:.1f}\n")
        f.write(f"\n")
print(f"  [WRITE] {srt_out}")

# Generate annotated video if requested
if generate_annotated:
    ok = generate_annotated_video(video_path, annotated_out, scenes, annotation_style)
    if ok:
        print(f"  [WRITE] {annotated_out}")
    else:
        print(f"  [SKIP] Annotated video not generated.")

# Print summary to stdout
print(f"  [SUMMARY] {len(scenes)} scene(s) | "
      f"Avg: {avg_scene_dur:.1f}s | "
      f"Range: {min_dur:.1f}s - {max_dur:.1f}s | "
      f"Transitions: {transition_counts}")

cap.release()

