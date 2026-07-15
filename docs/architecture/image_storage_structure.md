# Illustration Spec: Platform Storage Directory Structure

## Topic
Illustrate the file-based storage layout used by the Cognotik Platform Layer,
including global sessions, user sessions, and user settings.

## Format
- Rendered as a hierarchical tree diagram (a "file explorer" style tree).
- Left-to-right or top-to-bottom expansion with folder and file icons.
- Monospace font for all path and file names.
- Aspect ratio: 3:4 (tall, to accommodate the nested tree).

## Layout & Content

Root node (fill: #50E3C2, black text):
```
data/
```

Three primary children (each fill: #4A90E2, white text):

1. `global/`  — Global sessions (accessible to all)
- `2023-12-15/`            (date bucket)
 - `AbC1/`                (session id)
   - `messages/`          (folder of message content)
   - `config.json`        (session configuration)

2. `user-sessions/`  — User-specific sessions
- `user@example.com/`      (per-user namespace)
 - `2023-12-15/`          (date bucket)
   - `XyZ2/`              (session id)

3. `users/`  — User settings
- `user@example.com.json`  (per-user settings file, keys masked)

## Visual Conventions
- Folders drawn as folder icons; leaf files drawn as document icons.
- Use tree connector lines (├──, └──, │) to show hierarchy.
- Color the three top-level buckets distinctly and legend them:
- global/ ......... shared/global scope
- user-sessions/ .. isolated user scope
- users/ .......... credentials & preferences

## Callouts / Annotations
- Annotate `config.json` with "session metadata".
- Annotate `messages/` with "persisted UI/message blocks".
- Annotate `users/*.json` with "API credentials & tool paths (masked)".
- Add a small note: "Status/metadata files written atomically (temp file → rename)."

## Reference
Corresponds to Section 7 ("Platform Layer: Storage Structure") of diagrams.md
and Section 6 ("The Platform Layer") of architecture_overview.md.