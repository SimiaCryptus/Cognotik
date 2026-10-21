# session -> com.simiacryptus.cognotik.cli.SessionServerCli

# commit -> com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli

# illustrate -> com.simiacryptus.cognotik.cli.task.IllustrateDocumentTaskCli

#

# Options:

@@
-h|--help) usage; exit 0 ;;
esac
[[ $# -gt 0 ]] || { usage; die "missing required action (fileserver|fs, keys, docops, autofix, session, commit, illustrate)"; }
ACTION="$1"; shift
case "$ACTION" in
@@
commit)        MAIN_CLASS="com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli" ;;
illustrate)    MAIN_CLASS="com.simiacryptus.cognotik.cli.task.IllustrateDocumentTaskCli" ;;
*) die "unknown action: '$ACTION' (expected: fileserver|fs, keys, docops, autofix, session, commit, illustrate)" ;;
esac