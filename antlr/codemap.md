# Codemap

This module contains a collection of ANTLR4 grammars (and their supporting Java
runtime base classes) for parsing a variety of popular programming and markup
languages. Each language is implemented as one or more `.g4` grammar files
(lexer/parser, combined or split) under `src/main/antlr`, with custom Java
support classes under `src/main/java` where the grammar requires semantic
predicates, custom token stream manipulation (e.g. Python's INDENT/DEDENT
handling), or lexer/parser base-class helper methods.

## Overview

The repository is organized by language/grammar, one directory-flat set of
grammar files per target language. Every grammar follows common ANTLR4
formatting conventions (the `$antlr-format` directives at the top of each
file) and, where necessary, pairs with a `*Base.java` class that the
generated lexer or parser extends via the `superClass` option.

### Grammars

- **ECMAScript.g4** — Combined lexer/grammar for ES5 JavaScript. Implements
  ASI (automatic semicolon insertion) via the `eos` rule and semantic
  predicates (`here`, `lineTerminatorAhead`) for line-terminator-sensitive
  productions (e.g. `return`, `throw`, `continue`, `break`, postfix `++`/`--`).
  Also disambiguates regex literals vs. division using `isRegexPossible()`
  based on the previous token.

- **HTMLLexer.g4 / HTMLParser.g4** — Split lexer/parser for HTML. The lexer
  uses multiple modes (`TAG`, `ATTVALUE`, `SCRIPT`, `STYLE`) to tokenize tag
  structure, attribute values, and embedded `<script>`/`<style>` bodies.

- **JSON5.g4** — Combined grammar for the JSON5 data format (a JSON superset
  supporting comments, unquoted keys, trailing commas, etc.).

- **Java20Lexer.g4 / Java20Parser.g4** — Split lexer/parser implementing the
  Java 20 language grammar (JLS-based), including records, sealed classes,
  pattern matching in `switch`, modules, and text blocks.

- **KotlinLexer.g4 / KotlinParser.g4** — Split lexer/parser for Kotlin.
  The lexer uses modes for nested parens/brackets (`Inside`), string
  templates (`LineString`, `MultiLineString`), and string interpolation
  expressions (`StringExpression`). Depends on the shared `UnicodeClasses.g4`
  fragment grammar for identifier character classes.

- **PythonLexer.g4 / PythonParser.g4** — Split lexer/parser for Python 3.13,
  based on the official PEG grammar. The lexer declares `superClass =
  PythonLexerBase` to support INDENT/DEDENT token synthesis, f-string
  tokenization (multiple modes per quote style/raw-ness), and soft keywords
  (`type`, `match`, `case`, `_`).

- **RustLexer.g4 / RustParser.g4** — Split lexer/parser for Rust. The lexer
  extends `RustLexerBase` for float-literal disambiguation (avoiding
  ambiguity between float literals and method calls, e.g. `1.method()`),
  and the parser extends `RustParserBase` for shift-operator (`<<`/`>>`)
  disambiguation in generic type contexts.

- **TypeScriptLexer.g4 / TypeScriptParser.g4** — Split lexer/parser for
  TypeScript (superset of ECMAScript/JS grammar). The lexer extends
  `TypeScriptLexerBase` for strict-mode tracking, template string brace
  depth tracking, and regex-literal disambiguation. The parser extends
  `TypeScriptParserBase` for lookahead/lookbehind helper predicates (`p`,
  `n`, `notLineTerminator`, `lineTerminatorAhead`, etc.).

- **UnicodeClasses.g4** — Shared lexer grammar providing Unicode category
  fragment rules (`UNICODE_CLASS_LL`, `LM`, `LO`, `LT`, `LU`, `ND`, `NL`)
  used by `KotlinLexer.g4` via `import`.

- **css3Lexer.g4 / css3Parser.g4** — Split lexer/parser for CSS3, including
  media queries, `@supports`, `@keyframes`, `@font-face`, `@viewport`,
  `@counter-style`, `@font-feature-values`, CSS variables (`var()`), and
  `calc()` expressions. Uses per-character-case-insensitive fragment rules
  (`A`, `B`, `C`, ...) to match keywords irrespective of case.

### Java Support Classes

- **PythonLexerBase.java** — Abstract base class extended by the generated
  `PythonLexer`. Implements a pending-token queue to synthesize `ENCODING`,
  `INDENT`, and `DEDENT` tokens, handle NEWLINE suppression inside brackets,
  detect inconsistent tabs/spaces indentation, and manage f-string lexer
  mode transitions (including nested `{}` expressions and format specifiers).

- **RustLexerBase.java** — Abstract base class extended by `RustLexer`.
  Tracks the last two default-channel tokens to implement
  `FloatLiteralPossible()`/`FloatDotPossible()` predicates that resolve
  ambiguity between float literals and tuple-index/method-call syntax.

- **RustParserBase.java** — Abstract base class extended by `RustParser`.
  Provides `NextGT()`/`NextLT()` lookahead helpers used to manually compose
  `>>`/`<<` from consecutive `>`/`<` tokens (to support nested generics).

- **TypeScriptLexerBase.java** — Abstract base class extended by
  `TypeScriptLexer`. Tracks a stack of strict-mode scopes (for `'use
  strict'` directives), nested template-string/backtick depth, and brace
  depth (to distinguish a template-string-closing `}` from a normal code
  block `}`). Also implements `IsRegexPossible()` similarly to the
  ECMAScript grammar.

- **TypeScriptParserBase.java** — Abstract base class extended by
  `TypeScriptParser`. Provides token-text lookahead/lookbehind helpers (`p`,
  `prev`, `n`, `next`), ASI-related predicates (`notLineTerminator`,
  `lineTerminatorAhead`), and brace/token-type checks used throughout the
  TypeScript grammar's semantic predicates.

## Conventions

- Every grammar file begins with an `// $antlr-format ...` directive
  controlling code style when reformatted by the ANTLR4 formatter tool.
- Grammars requiring semantic predicates or custom token manipulation
  declare `superClass = <Name>Base` in an `options { }` block, with the
  corresponding Java class placed in `src/main/java`.
- Combined grammars (single file with both lexer and parser rules) are used
  for simpler languages (ECMAScript, JSON5); more complex languages use
  split lexer/parser grammar pairs following the `tokenVocab` linkage
  pattern (`options { tokenVocab = <Name>Lexer; }`).
- Lexer modes are used extensively (HTML, Kotlin, Python, TypeScript) to
  handle context-sensitive tokenization such as string interpolation,
  embedded script/style content, and attribute values.