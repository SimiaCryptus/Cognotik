#!/usr/bin/env bash
#
# Launcher for the Cognotik AutoFix CLI.
#
# Usage:
#   ./autofix.sh [--build] [--] [args passed to AutoFixCli...]
#
# Options:
#   --build      Force a rebuild (:cli:shadowJar) before running.
#   --no-build   Never build, even if the jar is missing (fail instead).
#   -h, --help   Show this help.
#
# Environment:
#   JAVA_HOME    If set, $JAVA_HOME/bin/java is used.
#   JAVA_OPTS    Extra JVM options (word-split, e.g. "-Xmx2g -Dfoo=bar").

set -Eeuo pipefail

# --- Locate ourselves (resolving symlinks) so the script is CWD-independent ----
self="${BASH_SOURCE[0]}"
while [[ -L "$self" ]]; do
  target="$(readlink "$self")"
  [[ "$target" == /* ]] && self="$target" || self="$(dirname -- "$self")/$target"
done
SCRIPT_DIR="$(cd -- "$(dirname -- "$self")" && pwd -P)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
LIBS_DIR="$SCRIPT_DIR/build/libs"
MAIN_CLASS="com.simiacryptus.cognotik.cli.AutoFixCli"

die() { printf 'autofix: %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '3,20p' "$self" | sed 's/^# \{0,1\}//'
}

# --- Parse our own options; stop at the first unknown arg -----------------------
build_mode=auto
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)    build_mode=force; shift ;;
    --no-build) build_mode=never; shift ;;
    -h|--help)  usage; exit 0 ;;
    --)         shift; break ;;
    *)          break ;;
  esac
done

# --- Java ----------------------------------------------------------------------
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  command -v java >/dev/null 2>&1 || die "no 'java' on PATH and JAVA_HOME is not usable"
  JAVA_BIN="$(command -v java)"
fi

# --- Build ---------------------------------------------------------------------
build() {
  local gradlew="$PROJECT_ROOT/gradlew"
  [[ -x "$gradlew" ]] || die "gradle wrapper not found or not executable: $gradlew"
  printf 'autofix: building :cli:shadowJar ...\n' >&2
  ( cd -- "$PROJECT_ROOT" && "$gradlew" :cli:shadowJar ) \
    || die "build failed"
}

# --- Resolve the newest shadow jar ---------------------------------------------
find_jar() {
  local -a jars=()
  shopt -s nullglob
  # Prefer the fat jar; ignore -sources/-javadoc artifacts.
  jars=( "$LIBS_DIR"/*-all.jar )
  shopt -u nullglob
  (( ${#jars[@]} )) || return 1
  # Newest by mtime.
  local newest="${jars[0]}" j
  for j in "${jars[@]}"; do
    [[ "$j" -nt "$newest" ]] && newest="$j"
  done
  printf '%s\n' "$newest"
}

[[ "$build_mode" == force ]] && build

if ! JAR="$(find_jar)"; then
  case "$build_mode" in
    never) die "no *-all.jar in $LIBS_DIR (and --no-build was given)" ;;
    *)     build
           JAR="$(find_jar)" || die "build produced no *-all.jar in $LIBS_DIR" ;;
  esac
fi

# --- Run -----------------------------------------------------------------------
# shellcheck disable=SC2206  # deliberate word-splitting of JAVA_OPTS
read -r -a java_opts <<< "${JAVA_OPTS:-}"

EMAIL="acharneski@gmail.com" \
COGNOTIK_SMART_MODEL="claude-sonnet-5" \
COGNOTIK_FAST_MODEL="claude-haiku-4-5-20251001" \
exec "$JAVA_BIN" "${java_opts[@]}" -cp "$JAR" "$MAIN_CLASS" "$@"