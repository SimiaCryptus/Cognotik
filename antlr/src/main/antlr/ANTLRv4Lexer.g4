// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

lexer grammar ANTLRv4Lexer;

DOC_COMMENT
    : '/**' .*? ('*/' | EOF) -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? ('*/' | EOF) -> channel(HIDDEN)
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

OPTIONS
    : 'options' WSNLCHARS* '{'
    ;

TOKENS
    : 'tokens' WSNLCHARS* '{'
    ;

CHANNELS
    : 'channels' WSNLCHARS* '{'
    ;

fragment WSNLCHARS
    : [ \t\f\r\n]
    | '//' ~[\r\n]*
    | '/*' .*? '*/'
    ;

IMPORT    : 'import';
FRAGMENT  : 'fragment';
LEXER     : 'lexer';
PARSER    : 'parser';
GRAMMAR   : 'grammar';
PROTECTED : 'protected';
PUBLIC    : 'public';
PRIVATE   : 'private';
RETURNS   : 'returns';
LOCALS    : 'locals';
THROWS    : 'throws';
CATCH     : 'catch';
FINALLY   : 'finally';
MODE      : 'mode';

COLON       : ':';
COLONCOLON  : '::';
COMMA       : ',';
SEMI        : ';';
LPAREN      : '(';
RPAREN      : ')';
RBRACE      : '}';
RARROW      : '->';
LT          : '<';
GT          : '>';
ASSIGN      : '=';
QUESTION    : '?';
STAR        : '*';
PLUS_ASSIGN : '+=';
PLUS        : '+';
OR          : '|';
DOLLAR      : '$';
RANGE       : '..';
DOT         : '.';
AT          : '@';
POUND       : '#';
NOT         : '~';

INT
    : [0-9]+
    ;

STRING_LITERAL
    : '\'' ( '\\' . | ~['\\\r\n] )* '\''
    ;

LEXER_CHAR_SET
    : '[' ( '\\' . | ~[\]\\] )* ']'
    ;

ACTION
    : '{' ActionContent* '}'
    ;

fragment ActionContent
    : ACTION
    | '/*' .*? '*/'
    | '//' ~[\r\n]*
    | '"' ( '\\' . | ~["\\\r\n] )* '"'
    | '\'' ( '\\' . | ~['\\\r\n] )* '\''
    | ~[{}"'/]+
    | '/'
    ;

TOKEN_IDENT
    : [A-Z] [a-zA-Z0-9_]*
    ;

RULE_IDENT
    : [a-z] [a-zA-Z0-9_]*
    ;

WS
    : [ \t\r\n\f]+ -> channel(HIDDEN)
    ;