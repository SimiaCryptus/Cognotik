#!/bin/bash

DIRNAME=`dirname $0`
pushd $DIRNAME
pushd ../bin
BIN_DIR=`pwd`
popd

cat <<EOF >test_op.md
---
specifies: jokes.md
---

Write 101 jokes about left handed sea creatures
EOF

JAVA_OPTS="-Dcognotik.db.root=.cognotik" $BIN_DIR/docops run test_op.md --email "acharneski@gmail.com" --smart-model "claude-sonnet-5" --fast-model "claude-haiku-4-5-20251001" 2>&1 | tee docops.log

cat jokes.md

popd
