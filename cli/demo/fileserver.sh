#!/bin/bash

DIRNAME=`dirname $0`
pushd $DIRNAME
pushd ../bin
BIN_DIR=`pwd`
popd

cd ../..
$BIN_DIR/fileserver --email "acharneski@gmail.com" --smart-model "claude-sonnet-5" --fast-model "claude-haiku-4-5-20251001"

popd
