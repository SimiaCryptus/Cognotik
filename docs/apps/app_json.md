---
transforms:
  - (.*)/apps/(.*)/README.md -> $1/apps/$2/../apps.json
---

# apps.json Schema

The `apps.json` file defines the list of applications available in the DocOps desktop launcher. It is an array of app descriptor objects.

## Top-Level Structure

```json
[ <AppDescriptor>, ... ]
```

Each element in the array is an **AppDescriptor** object.

---

## AppDescriptor Object

| Field        | Type             | Required | Default        | Description                                                                                                       |
|--------------|------------------|----------|----------------|-------------------------------------------------------------------------------------------------------------------|
| `id`         | `string`         | ⬜ No     | `null`         | Unique identifier for the app entry. Convention: `"app-<appId>"`. Used to derive the registered app name and id. |
| `name`       | `string`         | ✅ Yes    | —              | Human-readable display name shown in the launcher UI.                                                             |
| `icon`       | `string`         | ✅ Yes    | —              | Emoji or icon character displayed alongside the app name.                                                         |
| `description`| `string`         | ✅ Yes    | —              | Short description of the app's purpose, shown in the app card.                                                    |
| `path`       | `string`         | ✅ Yes    | —              | Resource path used to locate the app's static files and derive its URL route (e.g. `"apps/my-app"`).             |
| `type`       | `string` (enum)  | ✅ Yes    | —              | Category of the app. See [App Types](#app-types) below.                                                           |
| `badge`      | `string \| null` | ⬜ No     | `null`         | Optional label shown as a badge on the app card (e.g. `"Experimental"`, `"Advanced"`).                           |
| `badgeClass` | `string \| null` | ⬜ No     | `null`         | CSS class applied to the badge element. Common values: `"accent"`, `"muted"`. Omit or use `null` for no badge.   |
| `cardClass`  | `string`         | ⬜ No     | `null`         | Additional CSS class applied to the app card element for custom styling (e.g. `"app-card-muted"`).                |
| `category`   | `string`         | ⬜ No     | `null`         | Logical grouping label used to organize apps in the launcher (e.g. `"productivity"`, `"education"`).              |
| `tags`       | `string[]`       | ⬜ No     | `[]`           | List of keyword tags used for filtering and search within the launcher (e.g. `["ai", "quiz", "learning"]`).       |

---

> **Note:** Fields such as `appId` and `resource_path` are **not** part of the JSON file. They are derived at runtime by `ResourceApps` from the `id` and `path` fields respectively when registering each app entry.

---

## App Types

The `type` field controls how the desktop shell categorizes and renders the app card.

| Value        | Description                                                                   |
|--------------|-------------------------------------------------------------------------------|
| `"docops"`   | A full DocOps AI-powered application with its own route and backend pipeline. |
| `"chat"`     | A simple conversational chat interface.                                       |
| `"pipeline"` | A configurable multi-step AI task pipeline builder.                           |

---

## Field Conventions

- **`id`** should follow the pattern `"app-{appId}"`. The `"app-"` prefix is stripped internally when deriving the `appId`. If omitted, the registered name and id will contain `"app-null"`.
- **`path`** is a resource-relative path (e.g. `"apps/my-app"`). The last path segment is used as the URL route (e.g. `/my-app`), and the full path is used to locate static resources and attempt to load a `README.md`.
- **`badge`** and **`badgeClass`** are both optional. Omit them or set to `null` when no badge is needed.
- **`tags`** defaults to an empty list if omitted.

---

## Full Example

```json
{
  "id": "app-my-new-app",
  "name": "My New App",
  "icon": "🚀",
  "description": "A brief description of what this app does.",
  "type": "docops",
  "path": "apps/my-new-app"
}
```

### With Optional Fields

```json
{
  "id": "app-my-resource-app",
  "name": "My Resource App",
  "icon": "📂",
  "description": "An app that uses dedicated static resources.",
  "badge": "Beta",
  "badgeClass": "accent",
  "type": "docops",
  "path": "apps/my-resource-app",
  "cardClass": "app-card-highlight",
  "category": "productivity",
  "tags": ["ai", "docs"]
}
```

---

## JSON Schema (Draft-07)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AppsRegistry",
  "type": "array",
  "items": {
    "title": "AppDescriptor",
    "type": "object",
    "required": ["name", "icon", "description", "type", "path"],
    "additionalProperties": false,
    "properties": {
      "id": {
        "type": ["string", "null"],
        "description": "Unique identifier for the app. Convention: 'app-{appId}'. Used to derive the registered app name and appId.",
        "pattern": "^app-.+"
      },
      "name": {
        "type": "string",
        "description": "Human-readable display name."
      },
      "icon": {
        "type": "string",
        "description": "Emoji or icon character for the app."
      },
      "description": {
        "type": "string",
        "description": "Short description shown in the app card."
      },
      "badge": {
        "type": ["string", "null"],
        "description": "Optional badge label. Null if no badge."
      },
      "badgeClass": {
        "type": ["string", "null"],
        "description": "CSS class for the badge. Null or omitted if no badge.",
        "examples": ["", "accent", "muted"]
      },
      "type": {
        "type": "string",
        "description": "App category used by the launcher shell.",
        "enum": ["docops", "chat", "pipeline"]
      },
      "path": {
        "type": "string",
        "description": "Resource-relative path to the app directory (e.g. 'apps/my-app'). The last segment becomes the URL route."
      },
      "cardClass": {
        "type": "string",
        "description": "Additional CSS class applied to the app card."
      },
      "category": {
        "type": "string",
        "description": "Logical grouping label for organizing apps in the launcher.",
        "pattern": "^[a-z0-9-]+$",
        "examples": ["productivity", "education", "developer-tools"]
      },
      "tags": {
        "type": "array",
        "description": "List of keyword tags for filtering and search.",
        "items": {
          "type": "string",
          "pattern": "^[a-z0-9-]+$"
        },
        "uniqueItems": true,
        "default": []
      }
    }
  }
}
```