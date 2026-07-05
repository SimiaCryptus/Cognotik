#!/bin/bash

# Create a report of lines of code in the project for each extension type.
# The project root is two levels up from this script (docs/reports/ -> project root).
# Outputs are written as siblings to this script (in docs/reports/).
#
# Metrics collected per file:
#   - total lines
#   - blank lines
#   - comment lines (best-effort, based on extension)
#   - code lines (total - blank - comment)
#   - byte size
#
# Aggregated per extension in the report:
#   - file count
#   - total lines, code lines, comment lines, blank lines
#   - total bytes
#   - average lines per file
#   - largest file (path + line count)

SUPPORTED_EXTENSIONS="js css html md kt ts json xml yml yaml sh py go rs java cpp c h"
# Directories to exclude from the search.
EXCLUDED_DIRS="node_modules build target dist out .git .gradle .idea .next .nuxt .cache vendor venv .venv __pycache__ bin obj coverage"
# Logging helpers (write to stderr so stdout stays clean for any redirection).
log()  { printf '[loc] %s\n' "$*" >&2; }
logf() { printf '[loc] %s\n' "$(printf "$@")" >&2; }
# Human-readable duration since a given epoch seconds value.
_elapsed() {
     local start="$1"
     local now
     now=$(date +%s)
     echo $(( now - start ))
}


# Collect per-file metrics and emit a JSON index.
create_loc_index() {
    local root="$1"
    local out="$2"
     local start_ts
     start_ts=$(date +%s)
     log "Scanning project root: $root"
     log "Supported extensions: $SUPPORTED_EXTENSIONS"
     log "Excluded directories: $EXCLUDED_DIRS"

    # Build prune arguments for excluded directories.
    local prune_args=()
    local first_prune=1
    for dir in $EXCLUDED_DIRS; do
        if [ $first_prune -eq 1 ]; then
            prune_args+=( -name "$dir" )
            first_prune=0
        else
            prune_args+=( -o -name "$dir" )
        fi
    done

    # Build find arguments safely as an array.
    local find_args=()
    local first=1
    for ext in $SUPPORTED_EXTENSIONS; do
        if [ $first -eq 1 ]; then
            find_args+=( -name "*.$ext" )
            first=0
        else
            find_args+=( -o -name "*.$ext" )
        fi
    done
     # Pre-count matching files for progress reporting.
     local total_files
     total_files=$(find "$root" \( -type d \( "${prune_args[@]}" \) -prune \) \
         -o \( -type f \( "${find_args[@]}" \) -print \) | wc -l | tr -d ' ')
     log "Discovered $total_files candidate files. Computing metrics..."


    # Find all matching files, then compute metrics per file via awk,
    # emitting a JSON array of file objects.
    find "$root" \( -type d \( "${prune_args[@]}" \) -prune \) \
        -o \( -type f \( "${find_args[@]}" \) -print0 \) \
         | awk -v RS='\0' -v root="$root" -v total_files="$total_files" '
        BEGIN {
            printf "[\n";
            first = 1;
             processed = 0;
             # Progress increment: every ~5% or every 50 files, whichever is smaller.
             step = (total_files >= 100) ? int(total_files / 20) : 50;
             if (step < 1) step = 1;
        }
        {
            path = $0;
            # Determine extension.
            n = split(path, parts, ".");
            ext = (n > 1) ? parts[n] : "(none)";

            # Comment patterns by extension (best-effort).
            # line_comment: regex matched after leading whitespace.
            lc = "";
            if (ext == "js" || ext == "ts" || ext == "kt" || ext == "go" || \
                ext == "rs" || ext == "java" || ext == "cpp" || ext == "c" || \
                ext == "h" || ext == "css") {
                lc = "^//";
            } else if (ext == "sh" || ext == "py" || ext == "yml" || ext == "yaml") {
                lc = "^#";
            }
            # Block comment markers (very simplified, single-line detect only).
            bc_open  = "";
            bc_close = "";
            if (ext == "js" || ext == "ts" || ext == "kt" || ext == "go" || \
                ext == "rs" || ext == "java" || ext == "cpp" || ext == "c" || \
                ext == "h" || ext == "css") {
                bc_open  = "/\\*";
                bc_close = "\\*/";
            } else if (ext == "html" || ext == "xml" || ext == "md") {
                bc_open  = "<!--";
                bc_close = "-->";
            }

            t_lines = 0; blank = 0; comment = 0;
            in_block = 0;
            size = 0;
            # Temporarily switch RS to newline for reading file contents,
            # since the outer RS is NUL for find -print0 parsing.
            saved_rs = RS;
            RS = "\n";
            while ((getline line < path) > 0) {
                t_lines++;
                size += length(line) + 1;
                stripped = line;
                sub(/^[ \t]+/, "", stripped);
                if (stripped == "") {
                    blank++;
                    continue;
                }
                if (in_block) {
                    comment++;
                    if (bc_close != "" && stripped ~ bc_close) {
                        in_block = 0;
                    }
                    continue;
                }
                if (bc_open != "" && stripped ~ bc_open) {
                    comment++;
                    if (!(stripped ~ bc_close)) {
                        in_block = 1;
                    }
                    continue;
                }
                if (lc != "" && stripped ~ lc) {
                    comment++;
                    continue;
                }
            }
            close(path);
            RS = saved_rs;

            code = t_lines - blank - comment;
            if (code < 0) code = 0;

            # JSON-escape path.
            esc = path;
            gsub(/\\/, "\\\\", esc);
            gsub(/"/, "\\\"", esc);

            if (!first) printf ",\n";
            first = 0;
            printf "  {\"path\": \"%s\", \"ext\": \"%s\", \"lines\": %d, \"blank\": %d, \"comment\": %d, \"code\": %d, \"bytes\": %d}",
                esc, ext, t_lines, blank, comment, code, size;
             processed++;
             if (processed % step == 0 || processed == total_files+0) {
                 pct = (total_files+0 > 0) ? (processed * 100.0 / total_files) : 100.0;
                 printf "[loc]   progress: %d/%d files (%.1f%%) - last: %s\n",
                     processed, total_files, pct, path | "cat 1>&2";
             }
        }
        END {
            printf "\n]\n";
             printf "[loc] Per-file metrics complete: %d files processed.\n", processed | "cat 1>&2";
        }
        ' > "$out"
     local elapsed
     elapsed=$(_elapsed "$start_ts")
     local bytes_written
     bytes_written=$(wc -c < "$out" 2>/dev/null | tr -d ' ')
     logf "Wrote JSON index (%s bytes) in %ss: %s" "$bytes_written" "$elapsed" "$out"
}

# Generate an aggregated JSON + human-readable report from the JSON index.
generate_loc_report() {
    local in_json="$1"
    local out_json="$2"
    local out_txt="$3"
     local start_ts
     start_ts=$(date +%s)
     log "Aggregating metrics from index: $in_json"
     if [ ! -s "$in_json" ]; then
         log "WARNING: index file is empty or missing: $in_json"
     fi

    # The index file emits one JSON object per line (between the [ and ]).
    # Process line-by-line to avoid quadratic buffer manipulation.
    awk '
    {
        line = $0;
        # Skip array open/close lines and empty lines.
        if (line ~ /^[ \t]*\[[ \t]*$/) next;
        if (line ~ /^[ \t]*\][ \t]*$/) next;
        if (line ~ /^[ \t]*$/) next;
        # Trim trailing comma if present.
        sub(/,[ \t]*$/, "", line);
        # Must look like a JSON object.
        if (line !~ /^[ \t]*\{.*\}[ \t]*$/) next;

        path  = json_str(line, "path");
        ext   = json_str(line, "ext");
        lines = json_num(line, "lines");
        blank = json_num(line, "blank");
        cmt   = json_num(line, "comment");
        code  = json_num(line, "code");
        bytes = json_num(line, "bytes");

        files[ext]++;
        tot_lines[ext]  += lines;
        tot_blank[ext]  += blank;
        tot_cmt[ext]    += cmt;
        tot_code[ext]   += code;
        tot_bytes[ext]  += bytes;

        if (lines > max_lines[ext]) {
            max_lines[ext] = lines;
            max_path[ext]  = path;
        }

        g_files++;
        g_lines += lines; g_blank += blank; g_cmt += cmt; g_code += code; g_bytes += bytes;
    }
    END {
        # JSON aggregate report.
        printf "{\n" > OUT_JSON;
        printf "  \"totals\": {\"files\": %d, \"lines\": %d, \"code\": %d, \"comment\": %d, \"blank\": %d, \"bytes\": %d},\n",
            g_files, g_lines, g_code, g_cmt, g_blank, g_bytes > OUT_JSON;
        printf "  \"by_extension\": [\n" > OUT_JSON;

        # Sort extensions by total lines desc.
        k = 0;
        for (e in files) { k++; exts[k] = e; }
        # Simple insertion sort.
        for (i = 2; i <= k; i++) {
            key = exts[i]; j = i - 1;
            while (j >= 1 && tot_lines[exts[j]] < tot_lines[key]) {
                exts[j+1] = exts[j]; j--;
            }
            exts[j+1] = key;
        }

        first = 1;
        for (i = 1; i <= k; i++) {
            e = exts[i];
            avg = (files[e] > 0) ? tot_lines[e] / files[e] : 0;
            if (!first) printf ",\n" > OUT_JSON;
            first = 0;
            # Escape largest path.
            lp = max_path[e];
            gsub(/\\/, "\\\\", lp);
            gsub(/"/, "\\\"", lp);
            printf "    {\"ext\": \"%s\", \"files\": %d, \"lines\": %d, \"code\": %d, \"comment\": %d, \"blank\": %d, \"bytes\": %d, \"avg_lines\": %.2f, \"largest_file\": \"%s\", \"largest_lines\": %d}",
                e, files[e], tot_lines[e], tot_code[e], tot_cmt[e], tot_blank[e], tot_bytes[e], avg, lp, max_lines[e] > OUT_JSON;
        }
        printf "\n  ]\n}\n" > OUT_JSON;

        # Human-readable text report.
        printf "%-8s %8s %10s %10s %10s %10s %12s %10s  %s\n",
            "EXT", "FILES", "LINES", "CODE", "COMMENT", "BLANK", "BYTES", "AVG", "LARGEST" > OUT_TXT;
        printf "%-8s %8s %10s %10s %10s %10s %12s %10s  %s\n",
            "---", "-----", "-----", "----", "-------", "-----", "-----", "---", "-------" > OUT_TXT;
        for (i = 1; i <= k; i++) {
            e = exts[i];
            avg = (files[e] > 0) ? tot_lines[e] / files[e] : 0;
            printf "%-8s %8d %10d %10d %10d %10d %12d %10.1f  %s (%d)\n",
                e, files[e], tot_lines[e], tot_code[e], tot_cmt[e], tot_blank[e], tot_bytes[e], avg, max_path[e], max_lines[e] > OUT_TXT;
        }
        printf "\nTOTALS: files=%d lines=%d code=%d comment=%d blank=%d bytes=%d\n",
            g_files, g_lines, g_code, g_cmt, g_blank, g_bytes > OUT_TXT;
         # Log summary to stderr.
         printf "[loc] Aggregation summary: %d files, %d lines (%d code, %d comment, %d blank), %d bytes across %d extensions.\n",
             g_files, g_lines, g_code, g_cmt, g_blank, g_bytes, k | "cat 1>&2";
    }

    function json_str(s, key,    re, rest, val, i, ch) {
        re = "\"" key "\"[ \t]*:[ \t]*\"";
        if (match(s, re)) {
            rest = substr(s, RSTART + RLENGTH);
            # Find closing quote (no escaped quote handling for simplicity beyond \").
            val = "";
            i = 1;
            while (i <= length(rest)) {
                ch = substr(rest, i, 1);
                if (ch == "\\") { val = val substr(rest, i, 2); i += 2; continue; }
                if (ch == "\"") break;
                val = val ch; i++;
            }
            # Unescape minimally.
            gsub(/\\"/, "\"", val);
            gsub(/\\\\/, "\\", val);
            return val;
        }
        return "";
    }
    function json_num(s, key,    re, v) {
        re = "\"" key "\"[ \t]*:[ \t]*-?[0-9]+(\\.[0-9]+)?";
        if (match(s, re)) {
            v = substr(s, RSTART, RLENGTH);
            sub(/^.*:[ \t]*/, "", v);
            return v + 0;
        }
        return 0;
    }
    ' OUT_JSON="$out_json" OUT_TXT="$out_txt" "$in_json"
     local elapsed
     elapsed=$(_elapsed "$start_ts")
     log "Generated reports in ${elapsed}s:"
     log "  - JSON: $out_json"
     log "  - TXT : $out_txt"
}

SCRIPT_DIR="$(cd "$(dirname "$(realpath "$0")")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOC_INDEX_FILE="$SCRIPT_DIR/loc_index.json"
LOC_REPORT_JSON="$SCRIPT_DIR/loc_report.json"
LOC_REPORT_TXT="$SCRIPT_DIR/loc_report.txt"
OVERALL_START=$(date +%s)
log "=========================================="
log "Lines-of-code report starting"
log "Script dir: $SCRIPT_DIR"
log "Root  dir: $ROOT_DIR"
log "=========================================="


create_loc_index "$ROOT_DIR" "$LOC_INDEX_FILE"
generate_loc_report "$LOC_INDEX_FILE" "$LOC_REPORT_JSON" "$LOC_REPORT_TXT"
OVERALL_ELAPSED=$(( $(date +%s) - OVERALL_START ))
log "=========================================="
log "Done in ${OVERALL_ELAPSED}s."
log "=========================================="


echo "Lines of code JSON index generated at: $LOC_INDEX_FILE"
echo "Lines of code JSON report generated at: $LOC_REPORT_JSON"
echo "Lines of code text report generated at: $LOC_REPORT_TXT"