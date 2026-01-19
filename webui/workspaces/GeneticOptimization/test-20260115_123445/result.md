Final Optimized Text
---
<details><summary>Optimized Content</summary>

```
Engineer a production-grade Python function to compute the factorial of a given integer. Your implementation must adhere to high-performance standards and professional coding practices by following these specifications:

1.  **Strict Input Validation:** Guard against invalid data by raising a `ValueError` with a descriptive error message if the input is a negative number or a non-integer type.
2.  **Mathematical Precision:** Ensure the logic correctly handles the base case where 0! equals 1.
3.  **Iterative Logic:** To prevent recursion depth errors and ensure scalability for large integers, implement the calculation using a manual iterative loop rather than recursion.
4.  **Modern Type Hinting:** Use explicit Python type hints in the function signature (e.g., `def factorial(n: int) -> int:`) to improve code maintainability and IDE support.
5.  **Comprehensive Documentation:** Include a professional docstring (e.g., Google or Sphinx style) that clearly outlines parameters, return values, and raised exceptions.
6.  **Clean Code Standards:** Maintain high readability through PEP 8 compliant variable naming and concise inline comments explaining the algorithmic steps.
```
</details>
---
**Strengths:**
- Explicit Edge Case Handling: The prompt explicitly mandates handling for negative numbers and non-integers, directly addressing the optimization goal.
- Technical Precision: It specifies the exact error type to raise (`ValueError`) and the specific implementation method (iterative), which removes ambiguity for the LLM.
- Professional Standards: By referencing PEP 8, specific docstring styles (Google/Sphinx), and type hinting, the prompt ensures the output will be industry-standard.
- Architectural Guidance: The instruction to avoid recursion depth errors shows a high level of technical foresight, ensuring the resulting code is 'production-grade.'

**Remaining Areas for Improvement:**
- Minor Style Ambiguity: While it mentions Google or Sphinx styles, picking one specific style would make the prompt slightly more 'specific,' though the current flexibility is often acceptable.
- Performance Nuance: While it asks for 'high-performance,' it doesn't specify if it should handle extremely large numbers using specialized libraries (like `math.factorial` which is implemented in C), though the iterative requirement implies a pure Python approach.


Detailed results: <a href='fileIndex/G-20260115-WuE6/optimization_results_20260115123948.md' target='_blank'>fileIndex/G-20260115-WuE6/optimization_results_20260115123948.md</a> <a href='fileIndex/G-20260115-WuE6/optimization_results_20260115123948.html' target='_blank'>html</a> <a href='fileIndex/G-20260115-WuE6/optimization_results_20260115123948.pdf' target='_blank'>pdf</a>
