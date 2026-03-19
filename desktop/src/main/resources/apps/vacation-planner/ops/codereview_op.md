---
task_type: CodeReview
transforms: ../vacation_plan\.html -> ../review_report\.md
related:
   - ../itinerary.md
   - ../analysis_output.md
validation_regex: "(?=.*## HTML Validation)(?=.*## CSS Quality)(?=.*## JavaScript Quality)(?=.*## Accessibility)(?=.*## Performance)"
---

# Code Quality Review

You are reviewing generated code for quality, accessibility, performance, and security.

## Your Role

Evaluate the generated HTML/CSS/JavaScript for:
- HTML validity and semantic correctness
- CSS best practices and responsive design
- JavaScript quality and error handling
- WCAG 2.1 AA accessibility compliance
- Performance optimization opportunities
- Security vulnerabilities

## Review Checklist

### HTML Validation

**Check**:
- [ ] Valid HTML5 syntax
- [ ] Proper semantic tags (header, main, section, article, etc.)
- [ ] All images have alt text
- [ ] Form inputs have associated labels
- [ ] Proper heading hierarchy (h1, h2, h3, etc.)
- [ ] No duplicate IDs
- [ ] Proper meta tags (viewport, charset, etc.)

**Issues Found**:
- [Issue 1]: [Severity: Critical/High/Medium/Low] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

### CSS Quality

**Check**:
- [ ] No inline styles (use classes)
- [ ] Consistent naming conventions (BEM, etc.)
- [ ] Responsive design (mobile-first)
- [ ] Color contrast meets WCAG AA (4.5:1 for text)
- [ ] No hardcoded colors (use CSS variables)
- [ ] Proper media queries for breakpoints
- [ ] No unused CSS rules
- [ ] Proper vendor prefixes where needed

**Issues Found**:
- [Issue 1]: [Severity] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

### JavaScript Quality

**Check**:
- [ ] No console errors or warnings
- [ ] Proper error handling (try/catch blocks)
- [ ] No global variables (use modules/closures)
- [ ] Proper event listener cleanup
- [ ] No memory leaks
- [ ] Async operations handled correctly
- [ ] Input validation and sanitization
- [ ] No hardcoded values (use configuration)

**Issues Found**:
- [Issue 1]: [Severity] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

### Accessibility (WCAG 2.1 AA)

**Check**:
- [ ] Color contrast: 4.5:1 for normal text, 3:1 for large text
- [ ] Keyboard navigation: All interactive elements accessible via Tab
- [ ] Focus indicators: Visible focus ring on all focusable elements
- [ ] ARIA labels: Proper labels for screen readers
- [ ] Form labels: Associated with inputs via <label> tags
- [ ] Error messages: Associated with form fields
- [ ] Motion: Respects prefers-reduced-motion
- [ ] Text sizing: Supports up to 200% zoom

**Issues Found**:
- [Issue 1]: [Severity] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

### Performance

**Check**:
- [ ] Page load time < 3 seconds
- [ ] No render-blocking resources
- [ ] Images optimized (compressed, appropriate format)
- [ ] CSS/JS minified
- [ ] No unnecessary DOM manipulation
- [ ] Efficient event handlers (event delegation)
- [ ] Proper caching headers
- [ ] No memory leaks

**Issues Found**:
- [Issue 1]: [Severity] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

### Security

**Check**:
- [ ] No XSS vulnerabilities (proper escaping)
- [ ] No hardcoded secrets or API keys
- [ ] Proper input validation
- [ ] HTTPS recommended for external resources
- [ ] Content Security Policy headers
- [ ] No eval() or similar dangerous functions

**Issues Found**:
- [Issue 1]: [Severity] [Description] [Recommendation]
- [Issue 2]: [Severity] [Description] [Recommendation]

---

## Summary

**Overall Quality Score**: [1-5]
**Critical Issues**: [Number]
**High Priority Issues**: [Number]
**Medium Priority Issues**: [Number]
**Low Priority Issues**: [Number]

**Recommendation**: [APPROVED / APPROVED_WITH_MINOR_ISSUES / NEEDS_REVISION / REJECTED]

**Next Steps**: [What needs to be done before deployment]

---

## Success Criteria

This op file produces high-quality output when:

1. **Thoroughness**: All review categories are covered
2. **Specificity**: Issues are specific and actionable
3. **Accuracy**: Issues are correctly identified
4. **Prioritization**: Issues are properly prioritized
5. **Constructiveness**: Recommendations are helpful
6. **Completeness**: All required sections are present