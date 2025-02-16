package com.simiacryptus.skyenet.core.util

class SimpleDiffApplier {
   companion object {
     val log = org.slf4j.LoggerFactory.getLogger(SimpleDiffApplier::class.java)
 
     private const val MAX_DIFF_SIZE_CHARS = 100000
 
     private val DIFF_PATTERN = """(?s)(?<![^\n])```diff\n(.*?)\n```""".toRegex()

   }
 
   private fun validateDiffSize(diff: String): Boolean {
     return diff.length <= MAX_DIFF_SIZE_CHARS
   }
 
   fun apply(
     code: String,
     response: String,
   ): String {
     val matches = DIFF_PATTERN.findAll(response).distinct()
     var currentCode = code
 
     matches.forEach { diffBlock ->
       val diffVal: String = diffBlock.groupValues[1]
       try {
         if (!validateDiffSize(diffVal)) {
           throw IllegalArgumentException("Diff size exceeds maximum limit")
         }
 
         val isParenthesisBalanced = FileValidationUtils.isParenthesisBalanced(currentCode)
         val isQuoteBalanced = FileValidationUtils.isQuoteBalanced(currentCode)
         val isSingleQuoteBalanced = FileValidationUtils.isSingleQuoteBalanced(currentCode)
         val isCurlyBalanced = FileValidationUtils.isCurlyBalanced(currentCode)
         val isSquareBalanced = FileValidationUtils.isSquareBalanced(currentCode)
         
         val newCode = IterativePatchUtil.applyPatch(currentCode, diffVal).replace("\r", "")
         
         val isParenthesisBalancedNew = FileValidationUtils.isParenthesisBalanced(newCode)
         val isQuoteBalancedNew = FileValidationUtils.isQuoteBalanced(newCode)
         val isSingleQuoteBalancedNew = FileValidationUtils.isSingleQuoteBalanced(newCode)
         val isCurlyBalancedNew = FileValidationUtils.isCurlyBalanced(newCode)
         val isSquareBalancedNew = FileValidationUtils.isSquareBalanced(newCode)
         
         val isError = (isCurlyBalanced && !isCurlyBalancedNew) ||
             (isSquareBalanced && !isSquareBalancedNew) ||
             (isParenthesisBalanced && !isParenthesisBalancedNew) ||
             (isQuoteBalanced && !isQuoteBalancedNew) ||
             (isSingleQuoteBalanced && !isSingleQuoteBalancedNew)
         
         if (isError) {
           val error = StringBuilder()
           if (!isCurlyBalancedNew) error.append("Curly braces are not balanced\n")
           if (!isSquareBalancedNew) error.append("Square braces are not balanced\n") 
           if (!isParenthesisBalancedNew) error.append("Parenthesis are not balanced\n")
           if (!isQuoteBalancedNew) error.append("Quotes are not balanced\n")
           if (!isSingleQuoteBalancedNew) error.append("Single quotes are not balanced\n")
           throw IllegalStateException(error.toString())
         }
         
         currentCode = newCode
       } catch (e: Throwable) {
         log.error("Error applying diff", e)
         throw e
       }
     }
     
     return currentCode
   }
 }