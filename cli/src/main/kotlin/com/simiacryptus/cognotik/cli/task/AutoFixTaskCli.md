# session -> com.simiacryptus.cognotik.cli.SessionServerCli

# commit -> com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli

# autofix-task -> com.simiacryptus.cognotik.cli.task.AutoFixTaskCli

#

# Options:

@@
[[ $# -gt 0 ]] || { usage; die "missing required action (fileserver|fs, keys, docops, autofix, session, commit, autofix-task)"; }
ACTION="$1"; shift
case "$ACTION" in
@@
commit)        MAIN_CLASS="com.simiacryptus.cognotik.cli.task.CommitAnalyzerTaskCli" ;;
autofix-task)  MAIN_CLASS="com.simiacryptus.cognotik.cli.task.AutoFixTaskCli" ;;
*) die "unknown action: '$ACTION' (expected: fileserver|fs, keys, docops, autofix, session, commit, autofix-task)" ;;
esac