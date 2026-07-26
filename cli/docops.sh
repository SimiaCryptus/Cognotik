#!/bin/bash

# If args is `--build` run `./gradlew :Cognotik:cli:shadowJar` from .. first
if [[ "$1" == "--build" ]]; then
  ./gradlew :Cognotik:cli:shadowJar
  shift
fi

java -jar /home/andrew/code/Cognotik/cli/build/libs/cli-*-all.jar $@
