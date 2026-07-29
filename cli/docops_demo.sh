#!/bin/bash

cat <<EOF >test_op.md
---
specifies: jokes.md
---

Write 101 jokes about left handed sea creatures
EOF

./docops.sh run test_op.md --email "acharneski@gmail.com" --smart-model "claude-sonnet-5" --fast-model "claude-haiku-4-5-20251001"

cat jokes.md
