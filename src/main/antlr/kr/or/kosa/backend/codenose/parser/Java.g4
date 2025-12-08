grammar Java;

@header {
package kr.or.kosa.backend.codenose.parser;
}

compilationUnit
    : packageDeclaration? importDeclaration* typeDeclaration* EOF
    ;

packageDeclaration
    : annotation* PACKAGE qualifiedName SEMI
    ;

importDeclaration
    : IMPORT STATIC? qualifiedName (DOT MUL)? SEMI
    ;

typeDeclaration
    : classDeclaration
    | interfaceDeclaration
    | enumDeclaration
    | annotationTypeDeclaration
    | SEMI
    ;

classDeclaration
    : modifier* CLASS Identifier typeParameters? (EXTENDS typeType)? (IMPLEMENTS typeList)? classBody
    ;

interfaceDeclaration
    : modifier* INTERFACE Identifier typeParameters? (EXTENDS typeList)? interfaceBody
    ;

enumDeclaration
    : modifier* ENUM Identifier (IMPLEMENTS typeList)? enumBody
    ;

annotationTypeDeclaration
    : modifier* AT INTERFACE Identifier annotationTypeBody
    ;

modifier
    : classOrInterfaceModifier
    | annotation
    ;

classOrInterfaceModifier
    : PUBLIC | PROTECTED | PRIVATE | STATIC | ABSTRACT | FINAL | STRICTFP
    ;

typeParameters
    : LT typeParameter (COMMA typeParameter)* GT
    ;

typeParameter
    : Identifier (EXTENDS typeBound)?
    ;

typeBound
    : typeType (AND typeType)*
    ;

typeList
    : typeType (COMMA typeType)*
    ;

classBody
    : LBRACE classBodyDeclaration* RBRACE
    ;

interfaceBody
    : LBRACE interfaceBodyDeclaration* RBRACE
    ;

enumBody
    : LBRACE enumConstant (COMMA enumConstant)* (COMMA)? (SEMI classBodyDeclaration*)? RBRACE
    ;

annotationTypeBody
    : LBRACE annotationTypeElementDeclaration* RBRACE
    ;

classBodyDeclaration
    : SEMI
    | STATIC? block
    | modifier* memberDeclaration
    ;

interfaceBodyDeclaration
    : modifier* interfaceMemberDeclaration
    | SEMI
    ;

enumConstant
    : annotation* Identifier arguments? classBody?
    ;

annotationTypeElementDeclaration
    : modifier* annotationTypeElementRest
    | SEMI
    ;

memberDeclaration
    : methodDeclaration
    | genericMethodDeclaration
    | fieldDeclaration
    | constructorDeclaration
    | genericConstructorDeclaration
    | interfaceDeclaration
    | annotationTypeDeclaration
    | classDeclaration
    | enumDeclaration
    ;

interfaceMemberDeclaration
    : constDeclaration
    | interfaceMethodDeclaration
    | genericInterfaceMethodDeclaration
    | interfaceDeclaration
    | annotationTypeDeclaration
    | classDeclaration
    | enumDeclaration
    ;

annotationTypeElementRest
    : typeType annotationMethodRest SEMI
    | classDeclaration
    | interfaceDeclaration
    | enumDeclaration
    | annotationTypeDeclaration
    ;

methodDeclaration
    : typeTypeOrVoid Identifier formalParameters (LBRACK RBRACK)* (THROWS qualifiedNameList)? (methodBody | SEMI)
    ;

methodBody
    : block
    ;

genericMethodDeclaration
    : typeParameters methodDeclaration
    ;

constructorDeclaration
    : Identifier formalParameters (THROWS qualifiedNameList)? constructorBody
    ;

constructorBody
    : block
    ;

genericConstructorDeclaration
    : typeParameters constructorDeclaration
    ;

fieldDeclaration
    : typeType variableDeclarators SEMI
    ;

constDeclaration
    : typeType constantDeclarator (COMMA constantDeclarator)* SEMI
    ;

constantDeclarator
    : Identifier (LBRACK RBRACK)* ASSIGN variableInitializer
    ;

interfaceMethodDeclaration
    : (typeTypeOrVoid | typeParameters annotation* typeTypeOrVoid) Identifier formalParameters (LBRACK RBRACK)* (THROWS qualifiedNameList)? (methodBody | SEMI)
    ;

genericInterfaceMethodDeclaration
    : typeParameters interfaceMethodDeclaration
    ;

annotationMethodRest
    : Identifier LPAREN RPAREN defaultValue?
    ;

variableDeclarators
    : variableDeclarator (COMMA variableDeclarator)*
    ;

variableDeclarator
    : variableDeclaratorId (ASSIGN variableInitializer)?
    ;

variableDeclaratorId
    : Identifier (LBRACK RBRACK)*
    ;

variableInitializer
    : arrayInitializer
    | expression
    ;

arrayInitializer
    : LBRACE (variableInitializer (COMMA variableInitializer)* (COMMA)? )? RBRACE
    ;

typeTypeOrVoid
    : typeType
    | VOID
    ;

qualifiedNameList
    : qualifiedName (COMMA qualifiedName)*
    ;

formalParameters
    : LPAREN formalParameterList? RPAREN
    ;

formalParameterList
    : formalParameter (COMMA formalParameter)* (COMMA lastFormalParameter)?
    | lastFormalParameter
    ;

formalParameter
    : variableModifier* typeType variableDeclaratorId
    ;

lastFormalParameter
    : variableModifier* typeType ELLIPSIS variableDeclaratorId
    ;

variableModifier
    : FINAL
    | annotation
    ;

qualifiedName
    : Identifier (DOT Identifier)*
    ;

typeType
    : annotation* (classOrInterfaceType | primitiveType) (LBRACK RBRACK)*
    ;

classOrInterfaceType
    : Identifier typeArguments? (DOT Identifier typeArguments?)*
    ;

primitiveType
    : BOOLEAN | CHAR | BYTE | SHORT | INT | LONG | FLOAT | DOUBLE
    ;

typeArguments
    : LT typeArgument (COMMA typeArgument)* GT
    ;

typeArgument
    : typeType
    | QUESTION ( (EXTENDS | SUPER) typeType )?
    ;

block
    : LBRACE blockStatement* RBRACE
    ;

blockStatement
    : localVariableDeclaration SEMI
    | statement
    | typeDeclaration
    ;

localVariableDeclaration
    : variableModifier* typeType variableDeclarators
    ;

statement
    : block
    | ASSERT expression (COLON expression)? SEMI
    | IF parExpression statement (ELSE statement)?
    | FOR LPAREN forControl RPAREN statement
    | WHILE parExpression statement
    | DO statement WHILE parExpression SEMI
    | TRY block (catchClause+ finallyBlock? | finallyBlock)
    | TRY resourceSpecification block catchClause* finallyBlock?
    | SWITCH parExpression LBRACE switchBlockStatementGroup* switchLabel* RBRACE
    | SYNCHRONIZED parExpression block
    | RETURN expression? SEMI
    | THROW expression SEMI
    | BREAK Identifier? SEMI
    | CONTINUE Identifier? SEMI
    | SEMI
    | statementExpression SEMI
    | Identifier COLON statement
    ;

catchClause
    : CATCH LPAREN variableModifier* catchType Identifier RPAREN block
    ;

catchType
    : qualifiedName (BITOR qualifiedName)*
    ;

finallyBlock
    : FINALLY block
    ;

resourceSpecification
    : LPAREN resources (SEMI)? RPAREN
    ;

resources
    : resource (SEMI resource)*
    ;

resource
    : variableModifier* classOrInterfaceType variableDeclaratorId ASSIGN expression
    ;

switchBlockStatementGroup
    : switchLabel+ blockStatement+
    ;

switchLabel
    : CASE (constantExpression | enumConstantName) COLON
    | DEFAULT COLON
    ;

forControl
    : enhancedForControl
    | forInit? SEMI expression? SEMI forUpdate?
    ;

forInit
    : localVariableDeclaration
    | expressionList
    ;

forUpdate
    : expressionList
    ;

enhancedForControl
    : variableModifier* typeType variableDeclaratorId COLON expression
    ;

parExpression
    : LPAREN expression RPAREN
    ;

expressionList
    : expression (COMMA expression)*
    ;

statementExpression
    : expression
    ;

constantExpression
    : expression
    ;

expression
    : primary
    | expression bop=DOT (Identifier | methodCall | THIS | SUPER | NEW nonWildcardTypeArguments? innerCreator)
    | expression LBRACK expression RBRACK
    | methodCall
    | NEW creator
    | LPAREN typeType RPAREN expression
    | expression (INC | DEC)
    | (INC | DEC | ADD | SUB) expression
    | (TILDE | BANG) expression
    | expression (MUL | DIV | MOD) expression
    | expression (ADD | SUB) expression
    | expression (LT | GT | LE | GE) expression
    | expression INSTANCEOF typeType
    | expression (EQUAL | NOTEQUAL) expression
    | expression BITAND expression
    | expression CARET expression
    | expression BITOR expression
    | expression AND expression
    | expression OR expression
    | expression QUESTION expression COLON expression
    | expression (ASSIGN | ADD_ASSIGN | SUB_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | AND_ASSIGN | OR_ASSIGN | XOR_ASSIGN | RSHIFT_ASSIGN | URSHIFT_ASSIGN | LSHIFT_ASSIGN | MOD_ASSIGN) expression
    | lambdaExpression
    ;

lambdaExpression
    : lambdaParameters ARROW lambdaBody
    ;

lambdaParameters
    : Identifier
    | LPAREN formalParameterList? RPAREN
    | LPAREN Identifier (COMMA Identifier)* RPAREN
    ;

lambdaBody
    : expression
    | block
    ;

primary
    : LPAREN expression RPAREN
    | THIS
    | SUPER
    | literal
    | Identifier
    | typeType DOT CLASS
    | VOID DOT CLASS
    | nonWildcardTypeArguments (explicitGenericInvocationSuffix | THIS arguments)
    ;

creator
    : nonWildcardTypeArguments createdName classCreatorRest
    | createdName (arrayCreatorRest | classCreatorRest)
    ;

createdName
    : Identifier typeArgumentsOrDiamond? (DOT Identifier typeArgumentsOrDiamond?)*
    | primitiveType
    ;

innerCreator
    : Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
    ;

arrayCreatorRest
    : LBRACK (RBRACK LBRACK)* RBRACK arrayInitializer
    | LBRACK expression RBRACK (LBRACK expression RBRACK)* (LBRACK RBRACK)*
    ;

classCreatorRest
    : arguments classBody?
    ;

explicitGenericInvocationSuffix
    : SUPER superSuffix
    | Identifier arguments
    ;

nonWildcardTypeArguments
    : LT typeList GT
    ;

typeArgumentsOrDiamond
    : LT typeArguments? GT
    ;

nonWildcardTypeArgumentsOrDiamond
    : LT typeList? GT
    ;

superSuffix
    : arguments
    | DOT Identifier arguments
    ;

arguments
    : LPAREN expressionList? RPAREN
    ;

literal
    : DECIMAL_LITERAL
    | HEX_LITERAL
    | OCTAL_LITERAL
    | BINARY_LITERAL
    | FLOAT_LITERAL
    | BOOL_LITERAL
    | CHAR_LITERAL
    | STRING_LITERAL
    | NULL_LITERAL
    ;

methodCall
    : Identifier LPAREN expressionList? RPAREN
    | THIS LPAREN expressionList? RPAREN
    | SUPER LPAREN expressionList? RPAREN
    ;

annotation
    : AT qualifiedName (LPAREN (elementValuePairs | elementValue)? RPAREN)?
    ;

elementValuePairs
    : elementValuePair (COMMA elementValuePair)*
    ;

elementValuePair
    : Identifier ASSIGN elementValue
    ;

elementValue
    : expression
    | annotation
    | arrayInitializer
    ;

defaultValue
    : DEFAULT elementValue
    ;

enumConstantName
    : Identifier
    ;

// Lexer Rules

ABSTRACT : 'abstract';
ASSERT : 'assert';
BOOLEAN : 'boolean';
BREAK : 'break';
BYTE : 'byte';
CASE : 'case';
CATCH : 'catch';
CHAR : 'char';
CLASS : 'class';
CONST : 'const';
CONTINUE : 'continue';
DEFAULT : 'default';
DO : 'do';
DOUBLE : 'double';
ELSE : 'else';
ENUM : 'enum';
EXTENDS : 'extends';
FINAL : 'final';
FINALLY : 'finally';
FLOAT : 'float';
FOR : 'for';
IF : 'if';
GOTO : 'goto';
IMPLEMENTS : 'implements';
IMPORT : 'import';
INSTANCEOF : 'instanceof';
INT : 'int';
INTERFACE : 'interface';
LONG : 'long';
NATIVE : 'native';
NEW : 'new';
PACKAGE : 'package';
PRIVATE : 'private';
PROTECTED : 'protected';
PUBLIC : 'public';
RETURN : 'return';
SHORT : 'short';
STATIC : 'static';
STRICTFP : 'strictfp';
SUPER : 'super';
SWITCH : 'switch';
SYNCHRONIZED : 'synchronized';
THIS : 'this';
THROW : 'throw';
THROWS : 'throws';
TRANSIENT : 'transient';
TRY : 'try';
VOID : 'void';
VOLATILE : 'volatile';
WHILE : 'while';

DECIMAL_LITERAL : ('0' | [1-9] [0-9]*) [lL]?;
HEX_LITERAL : '0' [xX] [0-9a-fA-F]+ [lL]?;
OCTAL_LITERAL : '0' [0-7]+ [lL]?;
BINARY_LITERAL : '0' [bB] [01]+ [lL]?;

FLOAT_LITERAL : [0-9]+ '.' [0-9]* ([eE] [+-]? [0-9]+)? [fFdD]?
              | '.' [0-9]+ ([eE] [+-]? [0-9]+)? [fFdD]?
              | [0-9]+ [eE] [+-]? [0-9]+ [fFdD]?
              | [0-9]+ [fFdD]
              ;

BOOL_LITERAL : 'true' | 'false';

CHAR_LITERAL : '\'' (~['\\\r\n] | EscapeSequence) '\'';

STRING_LITERAL : '"' (~["\\\r\n] | EscapeSequence)* '"';

NULL_LITERAL : 'null';

LPAREN : '(';
RPAREN : ')';
LBRACE : '{';
RBRACE : '}';
LBRACK : '[';
RBRACK : ']';
SEMI : ';';
COMMA : ',';
DOT : '.';
ELLIPSIS : '...';
AT : '@';
COLONCOLON : '::';

ASSIGN : '=';
GT : '>';
LT : '<';
BANG : '!';
TILDE : '~';
QUESTION : '?';
COLON : ':';
EQUAL : '==';
LE : '<=';
GE : '>=';
NOTEQUAL : '!=';
AND : '&&';
OR : '||';
INC : '++';
DEC : '--';
ADD : '+';
SUB : '-';
MUL : '*';
DIV : '/';
BITAND : '&';
BITOR : '|';
CARET : '^';
MOD : '%';

ADD_ASSIGN : '+=';
SUB_ASSIGN : '-=';
MUL_ASSIGN : '*=';
DIV_ASSIGN : '/=';
AND_ASSIGN : '&=';
OR_ASSIGN : '|=';
XOR_ASSIGN : '^=';
MOD_ASSIGN : '%=';
LSHIFT_ASSIGN : '<<=';
RSHIFT_ASSIGN : '>>=';
URSHIFT_ASSIGN : '>>>=';

ARROW : '->';

Identifier : [a-zA-Z_$] [a-zA-Z0-9_$]*;

WS : [ \t\r\n\u000C]+ -> skip;
COMMENT : '/*' .*? '*/' -> skip;
LINE_COMMENT : '//' ~[\r\n]* -> skip;

fragment EscapeSequence : '\\' [btnfr"'\\] | '\\' [0-3]? [0-7]? [0-7];
