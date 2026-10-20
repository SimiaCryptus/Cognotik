// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

parser grammar ANTLRv4Parser;

options {
    tokenVocab = ANTLRv4Lexer;
}

grammarSpec
    : grammarDecl prequelConstruct* rules modeSpec* EOF
    ;

grammarDecl
    : grammarType identifier SEMI
    ;

grammarType
    : LEXER GRAMMAR
    | PARSER GRAMMAR
    | GRAMMAR
    ;

prequelConstruct
    : optionsSpec
    | importDeclaration
    | tokensSpec
    | channelsSpec
    | action_
    ;

optionsSpec
    : OPTIONS (option SEMI?)* RBRACE
    ;

option
    : identifier ASSIGN optionValue
    ;

optionValue
    : identifier (DOT identifier)*
    | STRING_LITERAL
    | ACTION
    | INT
    ;

importDeclaration
    : IMPORT delegateGrammar (COMMA delegateGrammar)* SEMI
    ;

delegateGrammar
    : identifier ASSIGN identifier
    | identifier
    ;

tokensSpec
    : TOKENS idList? RBRACE
    ;

channelsSpec
    : CHANNELS idList? RBRACE
    ;

idList
    : identifier (COMMA identifier)* COMMA?
    ;

action_
    : AT (actionScopeName COLONCOLON)? identifier ACTION
    ;

actionScopeName
    : identifier
    | LEXER
    | PARSER
    ;

modeSpec
    : MODE identifier SEMI lexerRuleSpec*
    ;

rules
    : ruleSpec*
    ;

ruleSpec
    : parserRuleSpec
    | lexerRuleSpec
    ;

parserRuleSpec
    : ruleModifiers? ruleName argActionBlock? ruleReturns? throwsSpec? localsSpec? rulePrequel* COLON ruleAltList SEMI exceptionGroup
    ;

exceptionGroup
    : exceptionHandler* finallyClause?
    ;

exceptionHandler
    : CATCH argActionBlock ACTION
    ;

finallyClause
    : FINALLY ACTION
    ;

rulePrequel
    : optionsSpec
    | ruleAction
    ;

ruleReturns
    : RETURNS argActionBlock
    ;

throwsSpec
    : THROWS qualifiedIdentifier (COMMA qualifiedIdentifier)*
    ;

localsSpec
    : LOCALS argActionBlock
    ;

ruleAction
    : AT identifier ACTION
    ;

ruleModifiers
    : ruleModifier+
    ;

ruleModifier
    : PUBLIC
    | PRIVATE
    | PROTECTED
    | FRAGMENT
    ;

ruleAltList
    : labeledAlt (OR labeledAlt)*
    ;

labeledAlt
    : alternative (POUND identifier)?
    ;

lexerRuleSpec
    : FRAGMENT? TOKEN_IDENT optionsSpec? COLON lexerAltList SEMI
    ;

lexerAltList
    : lexerAlt (OR lexerAlt)*
    ;

lexerAlt
    : lexerElements? lexerCommands?
    ;

lexerElements
    : lexerElement+
    ;

lexerElement
    : labeledLexerElement ebnfSuffix?
    | lexerAtom ebnfSuffix?
    | lexerBlock ebnfSuffix?
    | ACTION QUESTION? elementOptions?
    ;

labeledLexerElement
    : identifier (ASSIGN | PLUS_ASSIGN) (lexerAtom | lexerBlock)
    ;

lexerBlock
    : LPAREN (optionsSpec? COLON)? lexerAltList RPAREN
    ;

lexerCommands
    : RARROW lexerCommand (COMMA lexerCommand)*
    ;

lexerCommand
    : lexerCommandName LPAREN lexerCommandExpr RPAREN
    | lexerCommandName
    ;

lexerCommandName
    : identifier
    | MODE
    ;

lexerCommandExpr
    : identifier
    | INT
    ;

altList
    : alternative (OR alternative)*
    ;

alternative
    : elementOptions? element*
    ;

element
    : labeledElement ebnfSuffix?
    | atom ebnfSuffix?
    | ebnf
    | ACTION QUESTION? elementOptions?
    ;

labeledElement
    : identifier (ASSIGN | PLUS_ASSIGN) (atom | block)
    ;

ebnf
    : block blockSuffix?
    ;

blockSuffix
    : ebnfSuffix
    ;

ebnfSuffix
    : QUESTION QUESTION?
    | STAR QUESTION?
    | PLUS QUESTION?
    ;

lexerAtom
    : characterRange
    | terminalDef
    | notSet
    | LEXER_CHAR_SET elementOptions?
    | DOT elementOptions?
    ;

atom
    : characterRange
    | terminalDef
    | ruleref
    | notSet
    | DOT elementOptions?
    ;

notSet
    : NOT setElement
    | NOT blockSet
    ;

blockSet
    : LPAREN setElement (OR setElement)* RPAREN
    ;

setElement
    : TOKEN_IDENT elementOptions?
    | STRING_LITERAL elementOptions?
    | characterRange
    | LEXER_CHAR_SET
    ;

block
    : LPAREN (optionsSpec? ruleAction* COLON)? altList RPAREN
    ;

ruleref
    : ruleName argActionBlock? elementOptions?
    ;

characterRange
    : STRING_LITERAL RANGE STRING_LITERAL
    ;

terminalDef
    : TOKEN_IDENT argActionBlock? elementOptions?
    | STRING_LITERAL elementOptions?
    ;

elementOptions
    : LT elementOption (COMMA elementOption)* GT
    ;

elementOption
    : qualifiedIdentifier
    | identifier ASSIGN (qualifiedIdentifier | STRING_LITERAL | INT)
    ;

argActionBlock
    : LEXER_CHAR_SET
    ;

ruleName
    : RULE_IDENT
    | IMPORT
    | LEXER
    | PARSER
    | GRAMMAR
    | RETURNS
    | LOCALS
    | THROWS
    | CATCH
    | FINALLY
    | MODE
    ;

identifier
    : RULE_IDENT
    | TOKEN_IDENT
    | IMPORT
    | FRAGMENT
    | LEXER
    | PARSER
    | GRAMMAR
    | PROTECTED
    | PUBLIC
    | PRIVATE
    | RETURNS
    | LOCALS
    | THROWS
    | CATCH
    | FINALLY
    | MODE
    ;

qualifiedIdentifier
    : identifier (DOT identifier)*
    ;