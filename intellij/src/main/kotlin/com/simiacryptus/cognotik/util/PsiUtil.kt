package com.simiacryptus.cognotik.util

import com.intellij.psi.PsiElement
import java.util.Arrays
import java.util.HashSet
import java.util.stream.Collectors

object PsiUtil {

    fun getSmallestContainingEntity(
        element: PsiElement?,
        selectionStart: Int,
        selectionEnd: Int,
        minSize: Int = 0
    ): PsiElement? {
        if (null == element) {
            return null
        }
        for (child in element.children) {
            val entity = getSmallestContainingEntity(child, selectionStart, selectionEnd, minSize)
            if (null != entity) {
                return entity
            }
        }
        val textRange = element.textRange
        if (textRange.startOffset <= selectionStart) {
            if (textRange.endOffset >= selectionEnd) {
                if (element.text.length >= minSize) {
                    return element
                }
            }
        }
        return null
    }


}