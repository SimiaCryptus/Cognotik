package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent

object LanguageUtils {

    /**
     * Detects the language of the active editor's file, falling back to the selected virtual file.
     * Safe to call from any thread; requires no read action.
     */
    fun getComputerLanguage(e: AnActionEvent): ComputerLanguage? = ComputerLanguage.getComputerLanguage(e)
}