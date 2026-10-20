// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

lexer grammar Lean4Lexer;

// Comments & Whitespace
DOC_COMMENT   : '/--' (BLOCK_COMMENT | .)*? '-/' -> channel(HIDDEN);
MOD_DOC       : '/-!' (BLOCK_COMMENT | .)*? '-/' -> channel(HIDDEN);
BLOCK_COMMENT : '/-' (BLOCK_COMMENT | .)*? '-/'  -> channel(HIDDEN);
LINE_COMMENT  : '--' ~[\r\n]*                    -> channel(HIDDEN);
WS            : [ \t\r\n\u000C]+                 -> skip;

// Declaration Keywords
KW_DEF          : 'def';
KW_THEOREM      : 'theorem';
KW_LEMMA        : 'lemma';
KW_ABBREV       : 'abbrev';
KW_EXAMPLE      : 'example';
KW_OPAQUE       : 'opaque';
KW_AXIOM        : 'axiom';
KW_CONSTANT     : 'constant';
KW_INDUCTIVE    : 'inductive';
KW_COINDUCTIVE  : 'coinductive';
KW_STRUCTURE    : 'structure';
KW_CLASS        : 'class';
KW_INSTANCE     : 'instance';
KW_WHERE        : 'where';
KW_EXTENDS      : 'extends';
KW_DERIVING     : 'deriving';

// Module / Scope Keywords
KW_NAMESPACE    : 'namespace';
KW_SECTION      : 'section';
KW_END          : 'end';
KW_MUTUAL       : 'mutual';
KW_OPEN         : 'open';
KW_EXPORT       : 'export';
KW_IMPORT       : 'import';
KW_PRELUDE      : 'prelude';
KW_VARIABLE     : 'variable';
KW_VARIABLES    : 'variables';
KW_UNIVERSE     : 'universe';
KW_UNIVERSES    : 'universes';

// Modifiers
KW_PUBLIC           : 'public';
KW_PRIVATE          : 'private';
KW_PROTECTED        : 'protected';
KW_NONCOMPUTABLE    : 'noncomputable';
KW_PARTIAL          : 'partial';
KW_UNSAFE           : 'unsafe';
KW_SCOPED           : 'scoped';
KW_LOCAL            : 'local';

// Term / Control Keywords
KW_FUN      : 'fun';
KW_MATCH    : 'match';
KW_WITH     : 'with';
KW_IF       : 'if';
KW_THEN     : 'then';
KW_ELSE     : 'else';
KW_LET      : 'let';
KW_HAVE     : 'have';
KW_SHOW     : 'show';
KW_FROM     : 'from';
KW_BY       : 'by';
KW_DO       : 'do';
KW_RETURN   : 'return';
KW_FOR      : 'for';
KW_IN       : 'in';
KW_UNLESS   : 'unless';
KW_TRY      : 'try';
KW_CATCH    : 'catch';
KW_FINALLY  : 'finally';
KW_MUT      : 'mut';
KW_REC      : 'rec';
KW_NOMATCH  : 'nomatch';
KW_NOFUN    : 'nofun';
KW_USING    : 'using';

// Meta / Syntax / Macro Keywords
KW_MACRO        : 'macro';
KW_MACRO_RULES  : 'macro_rules';
KW_SYNTAX       : 'syntax';
KW_ELAB         : 'elab';
KW_ELAB_RULES   : 'elab_rules';
KW_INFIX        : 'infix';
KW_INFIXL       : 'infixl';
KW_INFIXR       : 'infixr';
KW_PREFIX       : 'prefix';
KW_POSTFIX      : 'postfix';
KW_NOTATION     : 'notation';
KW_ATTRIBUTE    : 'attribute';
KW_SET_OPTION   : 'set_option';
KW_INITIALIZE   : 'initialize';
KW_BUILTIN_INIT : 'builtin_initialize';

// Sorts & Literals
KW_TYPE  : 'Type';
KW_PROP  : 'Prop';
KW_SORT  : 'Sort';
KW_TRUE  : 'true';
KW_FALSE : 'false';
KW_SORRY : 'sorry';

// Hash commands
HASH_CHECK      : '#check';
HASH_EVAL       : '#eval';
HASH_PRINT      : '#print';
HASH_REDUCE     : '#reduce';
HASH_SYNTH      : '#synth';
HASH_EXIT       : '#exit';

// Punctuation & Delimiters
AT_LBRACK       : '@[';
LPAREN          : '(';
RPAREN          : ')';
LBRACK          : '[';
RBRACK          : ']';
LBRACE          : '{';
RBRACE          : '}';
LDBRACE         : '{{' | '⦃';
RDBRACE         : '}}' | '⦄';
LANGLE_BRACK    : '⟨';
RANGLE_BRACK    : '⟩';
COLON_EQ        : ':=';
DOUBLE_COLON    : '::';
COLON           : ':';
SEMICOLON       : ';';
COMMA           : ',';
DOT_DOT         : '..';
DOT             : '.';
PIPE            : '|';
ARROW           : '->' | '→';
LARROW          : '<-' | '←';
FAT_ARROW       : '=>' | '⇒';
MAPSTO          : '↦';
IFF             : '<->' | '↔';
LAMBDA          : 'λ';
FORALL          : '∀';
EXISTS          : '∃';
AT              : '@';
HASH            : '#';
DOLLAR          : '$';
CARET           : '^';
TILDE           : '~';
BANG            : '!';
QUESTION        : '?';
UNDERSCORE      : '_';
CDOT            : '·';

// Common Operators
BIND_OP         : '>>=';
SEQ_OP          : '>>';
AND_AND         : '&&';
OR_OR           : '||';
LOGICAL_AND     : '/\\' | '∧';
LOGICAL_OR      : '\\/' | '∨';
LOGICAL_NOT     : '¬';
LE              : '<=' | '≤';
GE              : '>=' | '≥';
NE              : '!=' | '≠';
BEQ             : '==';
EQ              : '=';
LT              : '<';
GT              : '>';
APPEND          : '++';
PLUS            : '+';
MINUS           : '-';
STAR            : '*';
SLASH           : '/';
PERCENT         : '%';
AMP             : '&';
COMPOSE         : '∘';
TIMES           : '×';
SUM             : '⊕';
EQUIV           : '≃' | '≅' | '≡';
SUBSET          : '⊆' | '⊂';
MEM             : '∈' | '∉';

// Literals
STRING_LITERAL  : 's'? '"' ( '\\' . | ~["\\\r\n] )* '"';
CHAR_LITERAL    : '\'' ( '\\' . | ~['\\\r\n] ) '\'';
FLOAT_LITERAL   : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)?;
HEX_LITERAL     : '0' [xX] [0-9a-fA-F_]+;
BIN_LITERAL     : '0' [bB] [01_]+;
OCT_LITERAL     : '0' [oO] [0-7_]+;
NAT_LITERAL     : [0-9] [0-9_]*;

// Identifiers
IDENTIFIER
    : IDENT_START IDENT_PART* ('?' | '!')?
    | '«' ~[»\r\n]+ '»'
    ;

// Catch-all for custom unicode math operators / symbols in Lean
UNICODE_SYM
    : [\u2000-\u2BFF\u2E00-\u2E7F\uD835\uDC00-\uDFFF]
    ;

fragment IDENT_START
    : [a-zA-Z_]
    | [\u00A1-\u00AC\u00AE-\u02FF]
    | [\u0370-\u03FF] // Greek (α, β, γ, ...)
    | [\u0400-\u1FFF]
    | [\u2100-\u214F] // Letterlike symbols (ℕ, ℤ, ℝ, ℚ, ℂ, ...)
    ;

fragment IDENT_PART
    : IDENT_START
    | [0-9']
    | [\u0300-\u036F]
    | [\u2070-\u209F] // Superscripts and subscripts (₀, ₁, ₂, ...)
    | [\u1D00-\u1DBF]
    ;