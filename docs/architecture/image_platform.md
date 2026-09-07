# Illustration Spec: Platform Layer Services Overview

## Topic
Illustrate the runtime foundation provided by the Cognotik Platform Layer:
session management, user identity, persistence, authorization, resource
isolation, and optional cloud integration — all coordinated by
`ApplicationServicesImpl`.

## Format
- Rendered as a "hub-and-spoke" diagram with a central registry hub.
- Aspect ratio: 4:3.
- Platform palette base color: #50E3C2 (black text) for the hub and services.

## Layout & Content

### Central Hub
- A large central node labeled **`ApplicationServicesImpl`** (fill: #50E3C2)
annotated: "Central registry for all platform services."

### Spoke Services (radiating from the hub)
Each drawn as a rounded box with a short capability caption:

1. **Session** (fill: #4A90E2, white text)
- "Uniquely identifies an interaction."
- Sub-labels: `Global (G-...)`, `User (U-...)`, `Legacy (treated as global)`.

2. **User / Identity**
- "Authenticated user with credentials."

3. **Storage** — `StorageInterface` / `MetadataStorageInterface`
- "Persist sessions, messages, metadata."
- Sub-labels: `DataStorage (file-based)`, `HSQLMetadataStorage (in-memory)`.

4. **Auth** — `AuthenticationManager` / `AuthorizationManager`
- "Identity & permission checks."
- Sub-labels (permission chips): `Read Write Delete Share Admin`.

5. **ThreadPoolManager**
- "Session- & user-scoped execution contexts (resource isolation)."

6. **User Settings**
- "API credentials & local tool paths (secure key masking)."

7. **Cloud Integration** (optional; draw with dashed border)
- "AWS S3 sharing + KMS encryption."

## Session ID Sub-Panel (inset callout)
Small inset box showing the Session ID format:
- `G-YYYY-MM-DD-XXXX`  → Global session (accessible to all)  [chip: #7ED321]
- `U-YYYY-MM-DD-XXXX`  → User session (user-specific)        [chip: #F5A623]
- `YYYY-MM-DD-XXXX`    → Legacy (treated as global)          [chip: #9B9B9B]

## Directed Edges / Relationships
- `ApplicationServicesImpl` ↔ each service (registry provides/locates service).
- `Session` → `Storage` (session content persisted).
- `Auth` → `Session` (permission checks gate session access).
- `ThreadPoolManager` → wraps execution of Application/Cognitive work
(annotate: "isolation per session/user").
- `Storage` → `Cloud Integration` (optional export/share).

## Emphasis / Callouts
- Banner note: "Higher layers depend on the Platform Layer; the Platform
Layer does not depend on them."
- Highlight "Human-in-the-Loop Safety" via the Auth spoke:
"Side effects guarded by approval mechanisms."

## Reference
Corresponds to Section 6 ("The Platform Layer") of architecture_overview.md
and diagrams.md Sections 7 ("Storage Structure") and 8 ("Session ID Format").