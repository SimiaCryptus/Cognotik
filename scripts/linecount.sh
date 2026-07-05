#!/usr/bin/env bash

echo 'FIX ME!'
exit 1
# Provides a directory tree with per-file line counts and totals per directory (cascaded).
traverse() {
    local dir="$1"
    local prefix="$2"
    local total=0
    local buffer=""
    shopt -s dotglob nullglob
    local all_files=("$dir"/*)
    shopt -u dotglob nullglob
    # Filter out .git
    local files=()
    for f in "${all_files[@]}"; do
        if [[ "${f##*/}" != ".git" ]]; then
            files+=("$f")
        fi
    done
    local num_files=${#files[@]}
    local i=0
    for f in "${files[@]}"; do
        ((i++))
        local name="${f##*/}"

        local is_last=0
        [[ $i -eq $num_files ]] && is_last=1
        local connector="├── "
        local next_prefix="│   "
        if [[ $is_last -eq 1 ]]; then
            connector="└── "
            next_prefix="    "
        fi
        if [[ -d "$f" ]]; then
            local result
            result=$(traverse "$f" "$prefix$next_prefix")
            local sub_total="${result%%$'\n'*}"
            local sub_tree=""
            if [[ "$result" == *"$'\n'*" ]]; then
                sub_tree="${result#*$'\n'}"
            fi
            total=$((total + sub_total))
            buffer+="${prefix}${connector}${name}/ [${sub_total}]"$'\n'
            if [[ -n "$sub_tree" ]]; then
                buffer+="${sub_tree}"
            fi
        elif [[ -f "$f" ]]; then
            local lines
            lines=$(wc -l < "$f" 2>/dev/null | tr -d ' \t')
            lines=${lines:-0}
            total=$((total + lines))
            buffer+="${prefix}${connector}${name} [${lines}]"$'\n'
        fi
    done
    echo "$total"
    echo -n "$buffer"
}
root="."
if [[ -n "$1" ]]; then root="$1"; fi
root="${root%/}"
if [[ ! -d "$root" ]]; then
    echo "Error: $root is not a directory" >&2
    exit 1
fi
res=$(traverse "$root" "")
root_total="${res%%$'\n'*}"
root_tree=""
if [[ "$res" == *"$'\n'*" ]]; then
    root_tree="${res#*$'\n'}"
fi
echo "${root}/ [${root_total}]"
printf "%s" "${root_tree}"