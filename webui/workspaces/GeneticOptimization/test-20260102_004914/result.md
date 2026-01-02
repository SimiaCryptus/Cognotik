Final Optimized Text

```
Engineer a production-ready function to calculate the factorial ($n!$) of a provided numerical input. The implementation must define explicit behavior for edge cases: specifically, how the system handles negative integers (e.g., throwing a `ValueError` or returning `NaN`) and non-integer types (e.g., rejection via type-checking or mathematical truncation). 

Please provide the solution in **[Specify Language]**, selecting either an iterative or recursive architecture. Your response should include:

1.  **Technical Implementation:** Clean, modular code using the specified algorithmic approach.
2.  **Robust Error Handling:** Logic that prevents execution errors when encountering invalid inputs.
3.  **Overflow Management:** Strategies for handling large results that may exceed standard integer limits (e.g., utilizing `BigInt` or equivalent data types).
4.  **Documentation & Validation:** Comprehensive inline comments and a suite of unit test examples covering zero, positive integers, and the aforementioned edge cases.
5.  **Complexity Analysis:** A brief explanation of the chosen method's time and space complexity.
```

**Strengths:**
- Exceptional Specificity: The prompt leaves no room for ambiguity regarding edge cases, explicitly mentioning negative integers and non-integer types.
- Professional Tone: Uses industry-standard terminology such as 'production-ready,' 'modular code,' 'mathematical truncation,' and 'complexity analysis.'
- Comprehensive Scope: It goes beyond the basic algorithm to include overflow management (e.g., BigInt), unit testing, and documentation, which are critical for production environments.
- Structured Output: By numbering the required components (1-5), it ensures the resulting output will be organized and easy to parse.
- Flexibility: The use of placeholders like [Specify Language] makes it a highly reusable template for various technical contexts.

**Remaining Areas for Improvement:**
- Slight Verbosity: While the detail is necessary for 'production-ready' code, the parenthetical examples (e.g., 'throwing a ValueError or returning NaN') could be slightly more concise, though they do aid in clarity.
- Constraint Conflict: Asking for 'either an iterative or recursive architecture' is good, but in a production context, recursion without tail-call optimization can be a risk; the prompt could have explicitly asked for the most efficient approach for the chosen language.


Detailed results: <a href='fileIndex/G-20260102-L5UB/optimization_results_20260102005257.md' target='_blank'>fileIndex/G-20260102-L5UB/optimization_results_20260102005257.md</a> <a href='fileIndex/G-20260102-L5UB/optimization_results_20260102005257.html' target='_blank'>html</a> <a href='fileIndex/G-20260102-L5UB/optimization_results_20260102005257.pdf' target='_blank'>pdf</a>
