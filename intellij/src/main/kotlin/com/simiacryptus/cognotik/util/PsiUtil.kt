package com.simiacryptus.cognotik.util

import com.intellij.psi.PsiElement

object PsiUtil {

    /**
     * Returns the deepest element that fully contains [selectionStart, selectionEnd] and is at least
     * [minSize] characters long. Subtrees that cannot contain the selection are pruned.
     */
    fun getSmallestContainingEntity(
        element: PsiElement?,
        selectionStart: Int,
        selectionEnd: Int,
        minSize: Int = 0
    ): PsiElement? {
        if (element == null) return null
        val range = element.textRange ?: return null
        if (range.startOffset > selectionStart || range.endOffset < selectionEnd) return null
        for (child in element.children) {
            getSmallestContainingEntity(child, selectionStart, selectionEnd, minSize)?.let { return it }
        }
        return if (range.length >= minSize) element else null
    }
}