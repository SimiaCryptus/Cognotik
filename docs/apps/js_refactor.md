---
transforms:
  - (.*)/apps/([^/]+)/utils/README\.md -> $1/apps/$2/app\.js
  - (.*)/apps/([^/]+)/app\.js -> $1/apps/$2/app\.js
---

Rewrite the app.js file to utilize the new library in `utils/`
