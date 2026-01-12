Final Optimized Text

```
Create a production-ready function to compute the factorial of a numerical input, prioritizing strict input validation and robust error handling. 

Please follow these technical requirements:
1. **Input Verification:** Ensure the function confirms the input is a non-negative integer before proceeding with the calculation.
2. **Negative Integer Handling:** Raise a language-appropriate exception (e.g., `ValueError`) if a negative value is provided, as factorials are mathematically undefined for negative integers.
3. **Non-Integer Constraints:** Explicitly reject non-integer types, such as floats or decimals, to prevent the function from defaulting to Gamma function approximations.
4. **Overflow Prevention:** Use arbitrary-precision integers (such as `BigInt` or equivalent) to accommodate large results and prevent integer overflow errors.
5. **Code Documentation:** Include clear comments explaining the implementation logic, specifically identifying the base case and the chosen computational strategy (iterative or recursive).
```

**Strengths:**
- Technical Precision: The prompt uses highly specific terminology (e.g., 'Gamma function approximations,' 'arbitrary-precision integers,' 'base case') which ensures the output will be technically sound.
- Comprehensive Edge Case Coverage: It explicitly addresses the two primary edge cases requested (negative numbers and non-integers) and adds a third critical one (integer overflow).
- Professional Tone: The language is formal and mirrors the requirements found in professional software engineering documentation or technical specifications.
- Actionable Constraints: By specifying the type of error to raise (e.g., ValueError) and the reasoning (mathematically undefined), it leaves no room for ambiguity for the LLM.

**Remaining Areas for Improvement:**
- Language Agnostic vs. Specific: While being language-agnostic is often a strength, the prompt mentions BigInt (JavaScript) and ValueError (Python) in the same breath. This is a minor inconsistency, though it serves well as illustrative examples.
- Computational Strategy: While it asks for the strategy to be documented, it does not specify a preference between iterative or recursive, which could lead to stack overflow issues in some languages if recursion is chosen for very large numbers.


Detailed results: <a href='fileIndex/G-20260109-R3jQ/optimization_results_20260109113928.md' target='_blank'>fileIndex/G-20260109-R3jQ/optimization_results_20260109113928.md</a> <a href='fileIndex/G-20260109-R3jQ/optimization_results_20260109113928.html' target='_blank'>html</a> <a href='fileIndex/G-20260109-R3jQ/optimization_results_20260109113928.pdf' target='_blank'>pdf</a>
