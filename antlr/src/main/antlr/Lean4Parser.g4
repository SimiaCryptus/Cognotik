// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

parser grammar Lean4Parser;

options {
    tokenVocab = Lean4Lexer;
}

module
    : KW_PRELUDE? importDecl* command* EOF
    ;

importDecl
    : KW_IMPORT KW_PUBLIC? qualifiedIdent
    ;

command
    : declModifiers? declaration
    | namespaceDecl
    | sectionDecl
    | endDecl
    | openDecl
    | exportDecl
    | variableDecl
    | universeDecl
    | attributeCmd
    | setOptionCmd
    | hashCmd
    | mutualBlock
    | initDecl
    | syntaxCmd
    ;

// Modifiers & Attributes
declModifiers
    : (attributeList | visibilityModifier | declModifier)+
    ;

visibilityModifier
    : KW_PRIVATE
    | KW_PROTECTED
    ;

declModifier
    : KW_NONCOMPUTABLE
    | KW_PARTIAL
    | KW_UNSAFE
    | KW_SCOPED
    | KW_LOCAL
    ;

attributeList
    : AT_LBRACK attrInstance (COMMA attrInstance)* RBRACK
    ;

attrInstance
    : (KW_SCOPED | KW_LOCAL)? ident term*
    ;

// Declarations
declaration
    : functionDeclaration
    | theoremDeclaration
    | abbrevDeclaration
    | exampleDeclaration
    | constantDeclaration
    | inductiveDeclaration
    | structureDeclaration
    | classDeclaration
    | instanceDeclaration
    | macroDeclaration
    | elabDeclaration
    ;

functionDeclaration
    : KW_DEF declId universeParams? binder* typeSpec? declBody derivingClause?
    ;

theoremDeclaration
    : (KW_THEOREM | KW_LEMMA) declId universeParams? binder* typeSpec declBody
    ;

abbrevDeclaration
    : KW_ABBREV declId universeParams? binder* typeSpec? declBody derivingClause?
    ;

exampleDeclaration
    : KW_EXAMPLE universeParams? binder* typeSpec? declBody
    ;

constantDeclaration
    : (KW_AXIOM | KW_CONSTANT | KW_OPAQUE) declId universeParams? binder* typeSpec? (COLON_EQ term)?
    ;

inductiveDeclaration
    : (KW_INDUCTIVE | KW_COINDUCTIVE) declId universeParams? binder* typeSpec? KW_WHERE? ctorDecl* derivingClause?
    ;

ctorDecl
    : PIPE declModifiers? ident universeParams? binder* typeSpec?
    ;

structureDeclaration
    : KW_STRUCTURE declId universeParams? binder* extendsClause? typeSpec? (
        KW_WHERE structBody?
    )? derivingClause?
    ;

classDeclaration
    : KW_CLASS KW_INDUCTIVE? declId universeParams? binder* extendsClause? typeSpec? (
        KW_WHERE? (ctorDecl+ | structBody)
    )? derivingClause?
    ;

instanceDeclaration
    : KW_INSTANCE prioritySpec? declId? universeParams? binder* typeSpec declBody
    ;

prioritySpec
    : LBRACK ident? COLON? NAT_LITERAL RBRACK
    ;

extendsClause
    : KW_EXTENDS term (COMMA term)*
    ;

derivingClause
    : KW_DERIVING qualifiedIdent (COMMA qualifiedIdent)*
    ;

structBody
    : (ident DOUBLE_COLON)? (structField | binder)*
    ;

structField
    : declModifiers? ident binder* typeSpec? (COLON_EQ term)?
    ;

declId
    : qualifiedIdent
    ;

universeParams
    : DOT LBRACE ident+ RBRACE
    | LBRACE ident+ RBRACE
    ;

typeSpec
    : COLON term
    ;

declBody
    : COLON_EQ term whereStructInst?
    | matchAlts whereStructInst?
    | whereStructInst
    ;

whereStructInst
    : KW_WHERE structFieldAssign*
    ;

structFieldAssign
    : PIPE? declModifiers? ident binder* typeSpec? (COLON_EQ term | matchAlts)
    ;

// Scope / Section / Module commands
namespaceDecl
    : KW_NAMESPACE qualifiedIdent
    ;

sectionDecl
    : KW_SECTION qualifiedIdent?
    ;

endDecl
    : KW_END qualifiedIdent?
    ;

openDecl
    : KW_OPEN KW_SCOPED? qualifiedIdent+ (LPAREN ident+ RPAREN)? (KW_IN term)?
    ;

exportDecl
    : KW_EXPORT qualifiedIdent LPAREN ident+ RPAREN
    ;

variableDecl
    : (KW_VARIABLE | KW_VARIABLES) binder+
    ;

universeDecl
    : (KW_UNIVERSE | KW_UNIVERSES) ident+
    ;

attributeCmd
    : KW_ATTRIBUTE attributeList qualifiedIdent+
    ;

setOptionCmd
    : KW_SET_OPTION qualifiedIdent (ident | STRING_LITERAL | NAT_LITERAL | KW_TRUE | KW_FALSE)
    ;

hashCmd
    : (HASH_CHECK | HASH_EVAL | HASH_PRINT | HASH_REDUCE | HASH_SYNTH) term
    | HASH_EXIT
    ;

mutualBlock
    : KW_MUTUAL command+ KW_END
    ;

initDecl
    : (KW_INITIALIZE | KW_BUILTIN_INIT) ident? typeSpec? LARROW? term
    ;

macroDeclaration
    : (KW_MACRO | KW_MACRO_RULES) binder* COLON? ident? FAT_ARROW term
    ;

elabDeclaration
    : (KW_ELAB | KW_ELAB_RULES) binder* COLON? ident? FAT_ARROW term
    ;

syntaxCmd
    : (KW_SYNTAX | KW_INFIX | KW_INFIXL | KW_INFIXR | KW_PREFIX | KW_POSTFIX | KW_NOTATION) term (COLON ident)? (FAT_ARROW term)?
    ;

// Binders
binder
    : explicitBinder
    | implicitBinder
    | strictImplicitBinder
    | instBinder
    | simpleBinder
    ;

simpleBinder
    : ident
    | UNDERSCORE
    ;

explicitBinder
    : LPAREN binderIdent+ (COLON term)? (COLON_EQ term)? RPAREN
    ;

implicitBinder
    : LBRACE binderIdent+ (COLON term)? RBRACE
    ;

strictImplicitBinder
    : LDBRACE binderIdent+ (COLON term)? RDBRACE
    ;

instBinder
    : LBRACK (ident COLON)? term RBRACK
    ;

binderIdent
    : ident
    | UNDERSCORE
    ;

// Terms & Expressions
term
    : KW_BY tacticSeq                                                 # ByTerm
    | (KW_FUN | LAMBDA) funBinder+ (FAT_ARROW | MAPSTO | COMMA) term  # FunTerm
    | (FORALL | EXISTS) binder+ COMMA term                            # QuantifierTerm
    | KW_LET KW_REC? KW_MUT? letDecl (SEMICOLON? KW_IN? term)?        # LetTerm
    | KW_HAVE letDecl (SEMICOLON? KW_IN? term)?                       # HaveTerm
    | KW_SHOW term (KW_FROM term | KW_BY tacticSeq)                   # ShowTerm
    | KW_IF (ident COLON)? term KW_THEN term KW_ELSE term             # IfTerm
    | KW_MATCH (term (COMMA term)*)? (COLON term)? KW_WITH matchAlts? # MatchTerm
    | KW_NOMATCH term                                                 # NoMatchTerm
    | KW_NOFUN                                                        # NoFunTerm
    | KW_DO doSeq                                                     # DoTerm
    | KW_RETURN term?                                                 # ReturnTerm
    | prefixOp term                                                   # PrefixTerm
    | appTerm (binOp term)* (COLON term)?                             # BinaryOrTypedTerm
    ;

funBinder
    : binder
    | atomicTerm
    ;

letDecl
    : binderIdent binder* typeSpec? (COLON_EQ | LARROW) term
    | atomicTerm (COLON_EQ | LARROW) term
    ;

matchAlts
    : matchAlt+
    ;

matchAlt
    : PIPE term (COMMA term)* FAT_ARROW term
    ;

doSeq
    : doElem (SEMICOLON? doElem)*
    ;

doElem
    : KW_LET KW_MUT? letDecl
    | KW_HAVE letDecl
    | KW_IF (ident COLON)? term KW_THEN doSeq (KW_ELSE doSeq)?
    | KW_FOR ident KW_IN term KW_DO doSeq
    | KW_UNLESS term KW_DO doSeq
    | KW_TRY doSeq (KW_CATCH ident FAT_ARROW doSeq)? (KW_FINALLY doSeq)?
    | KW_RETURN term?
    | ident typeSpec? LARROW term
    | term
    ;

appTerm
    : atomicTerm argTerm*
    ;

argTerm
    : LPAREN ident COLON_EQ term RPAREN
    | DOT (ident | NAT_LITERAL)
    | atomicTerm
    ;

atomicTerm
    : literal
    | sortTerm
    | holeTerm
    | AT? qualifiedIdent universeParams?
    | LPAREN (term (COMMA term)*)? RPAREN
    | LBRACK (term (COMMA term)*)? RBRACK
    | structInst
    | anonCtor
    ;

structInst
    : LBRACE (term KW_WITH)? (structInstField (COMMA? structInstField)* COMMA?)? (DOT_DOT term?)? RBRACE
    ;

structInstField
    : qualifiedIdent (COLON_EQ term)?
    ;

anonCtor
    : LANGLE_BRACK (term (COMMA term)*)? RANGLE_BRACK
    ;

sortTerm
    : (KW_TYPE | KW_SORT | KW_PROP) (ident | NAT_LITERAL)?
    ;

holeTerm
    : UNDERSCORE
    | KW_SORRY
    | QUESTION ident?
    | CDOT
    ;

literal
    : NAT_LITERAL
    | FLOAT_LITERAL
    | HEX_LITERAL
    | BIN_LITERAL
    | OCT_LITERAL
    | STRING_LITERAL
    | CHAR_LITERAL
    | KW_TRUE
    | KW_FALSE
    ;

prefixOp
    : MINUS
    | BANG
    | LOGICAL_NOT
    | TILDE
    ;

binOp
    : ARROW
    | LARROW
    | IFF
    | FAT_ARROW
    | MAPSTO
    | BIND_OP
    | SEQ_OP
    | AND_AND
    | OR_OR
    | LOGICAL_AND
    | LOGICAL_OR
    | LE
    | GE
    | NE
    | BEQ
    | EQ
    | LT
    | GT
    | APPEND
    | PLUS
    | MINUS
    | STAR
    | SLASH
    | PERCENT
    | AMP
    | PIPE
    | CARET
    | DOLLAR
    | DOUBLE_COLON
    | COMPOSE
    | TIMES
    | SUM
    | EQUIV
    | SUBSET
    | MEM
    | UNICODE_SYM
    ;

// Tactics
tacticSeq
    : tactic (SEMICOLON? tactic)*
    ;

tactic
    : CDOT tacticSeq
    | LBRACE tacticSeq RBRACE
    | LPAREN tacticSeq RPAREN
    | KW_HAVE letDecl
    | KW_LET letDecl
    | KW_SHOW term
    | KW_BY tacticSeq
    | ident tacticArg* (LARROW | FAT_ARROW)?
    ;

tacticArg
    : LBRACK (term (COMMA term)*)? RBRACK
    | atomicTerm
    | COLON term
    | KW_WITH ident*
    | KW_USING term
    | LARROW atomicTerm
    ;

// Identifiers
qualifiedIdent
    : ident (DOT ident)*
    ;

ident
    : IDENTIFIER
    // Soft keywords permitted as identifier names
    | KW_FROM
    | KW_OPEN
    | KW_EXPORT
    | KW_PRELUDE
    | KW_SCOPED
    | KW_LOCAL
    | KW_PARTIAL
    | KW_UNSAFE
    | KW_MUT
    | KW_REC
    | KW_SYNTAX
    | KW_MACRO
    | KW_ELAB
    | KW_INFIX
    | KW_INFIXL
    | KW_INFIXR
    | KW_PREFIX
    | KW_POSTFIX
    | KW_NOTATION
    | KW_DERIVING
    | KW_EXTENDS
     | KW_PUBLIC
     | KW_USING
    ;