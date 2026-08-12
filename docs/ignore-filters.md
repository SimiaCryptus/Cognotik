## IgnoreFilter usage

    Composable, generalized calls:

    ```kotlin
      // pattern-file strategies
      IgnoreFilter.GITIGNORE.matches(path)
      FileSelectionUtils.matchesAny(file, IgnoreFilter.HIDDEN, IgnoreFilter.READONLY)

      // walks with explicit strategy selection
      FileSelectionUtils.walkFiltered(root, filters = *IgnoreFilter.DEFAULT)      // or positional spread
      FileSelectionUtils.walkFiltered(root, 20, false, { it.extension == "kt" }, *IgnoreFilter.GIT)

      // tree + expansion
      FileSelectionUtils.availableFileTree(root.toPath(), false, *IgnoreFilter.TEXT_SELECTION)
      FileSelectionUtils.expandFiles(listOf(root), false, *IgnoreFilter.DEFAULT)

      // reusable predicate for other APIs
      val keep: (File) -> Boolean = IgnoreFilter.accepting(*IgnoreFilter.RECURSIVE_LISTING)
    ```

    Legacy entry points (`isIgnored`, `isGitignore`, `isLLMIgnored`, `isLLMTextFile`,
    `filteredWalk`, `filteredWalkAsciiTree`, `getAvailableFiles`, `expandFileList`,
    `listFilesRecursively`) remain, now as inline delegates onto the vararg forms.