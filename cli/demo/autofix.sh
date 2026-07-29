#!/bin/bash

DIRNAME=`dirname $0`
pushd $DIRNAME
pushd ../bin
BIN_DIR=`pwd`
popd

cat <<EOF >test.sh
echo "Starting test script..."
exit 1
echo "Hello, World!"
EOF
chmod +x test.sh

JAVA_OPTS="-Dcognotik.db.root=.cognotik" $BIN_DIR/autofix --email "acharneski@gmail.com" --smart-model "claude-sonnet-5" --fast-model "claude-haiku-4-5-20251001" -- ./test.sh 2>&1 | tee autofix.log

cat test.sh

popd
