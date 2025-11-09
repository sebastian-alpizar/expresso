grammar Expr;

// ========================
//  Reglas principales
// ========================

program: (statement ';'?)* EOF; 

// ------------------------
//  Sentencias principales
// ------------------------

statement
    : letStatement
    | funStatement
    | printStatement
    | dataStatement
    | expression
    ;

// ------------------------
//  Declaración de variables
// ------------------------

letStatement
    : 'let' ID (':' type)? '=' expression
    ;

// ------------------------
//  Declaración de funciones
// ------------------------

funStatement
    : FUN ID '(' paramList? ')' (':' type)? '=' expression
    ;

paramList
    : param (',' param)*
    ;

param
    : ID ':' type
    ;

// ------------------------
//  Tipos de datos - CORREGIDO
// ------------------------

type
    : 'int'
    | 'float'
    | 'double'                     // AGREGADO
    | 'boolean'                    // AGREGADO
    | 'string'
    | 'any'
    | 'void'                       // AGREGADO
    | '(' typeList '->' type ')'
    | type '->' type
    ;

typeList
    : type (',' type)*
    ;

// ------------------------
//  Sentencia print
// ------------------------

printStatement
    : 'print' '(' expression ')'
    ;

// ------------------------
//  Expresiones generales
// ------------------------

expression
    : ternaryExpression
    | logicalOrExpression
    | additiveExpression
    | lambdaExpression
    | matchExpression
    | constructorExpr
    | castExpression
    | printStatement
    ;

// ------------------------
//  Expresiones lógicas
// ------------------------

logicalOrExpression
    : logicalAndExpression ('||' logicalAndExpression)*
    ;

logicalAndExpression
    : equalityExpression ('&&' equalityExpression)*
    ;

equalityExpression
    : relationalExpression (('==' | '!=') relationalExpression)*
    ;

relationalExpression
    : additiveExpression (('<' | '<=' | '>' | '>=') additiveExpression)*
    ;

// ------------------------
//  Constructores de datos
// ------------------------

constructorExpr
    : '^' ID ('(' expressionList? ')')?
    ;

expressionList
    : expression (',' expression)*
    ;

// ------------------------
//  Match / pattern matching
// ------------------------

matchExpression
    : 'match' expression 'with' '|'? matchRule ('|' matchRule)*
    ;

matchRule
    : pattern ('when' expression)? '->' expression
    ;

pattern
    : dataPattern
    | nativePattern
    ;

dataPattern
    : ID ('(' pattern (',' pattern)* ')')?
    ;

nativePattern
    : 'none'
    | STRING
    | INTEGER
    | FLOAT
    ;

// ------------------------
//  Expresión ternaria
// ------------------------

ternaryExpression
    : logicalOrExpression '?' expression ':' expression
    ;

// ------------------------
//  Expresiones lambda
// ------------------------

lambdaExpression
    : lambdaParams '->' expression
    | '(' lambdaParams '->' expression ')'
    ;

lambdaParams
    : ID (',' ID)*
    | '(' ID (',' ID)* ')'
    | typedLambdaParams
    ;

typedLambdaParams
    : typedParam (',' typedParam)*
    | '(' typedParam (',' typedParam)* ')'
    ;

typedParam
    : ID ':' type
    ;

// ------------------------
//  Casteo de tipos
// ------------------------

castExpression
    : unaryExpression (':' type)?
    ;

// ------------------------
//  Operadores aritméticos
// ------------------------

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : powerExpression (('*' | '/') powerExpression)*
    ;

powerExpression
    : castExpression ('**' castExpression)*
    ;

// ------------------------
//  Expresiones unarias
// ------------------------

unaryExpression
    : ('+' | '-') unaryExpression
    | primaryExpression
    ;

// ------------------------
//  Expresiones primarias
// ------------------------

primaryExpression
    : functionCall
    | FLOAT
    | INTEGER
    | STRING
    | ID
    | booleanLiteral
    | noneLiteral
    | printStatement
    | '(' expression ')'
    ;

// ------------------------
//  Literal none
// ------------------------

noneLiteral
    : 'none'
    ;

// ------------------------
//  Booleanos
// ------------------------

booleanLiteral
    : 'true'
    | 'false'
    ;

// ------------------------
//  Llamadas a funciones
// ------------------------

functionCall
    : primaryTarget callArgs+
    ;

callArgs
    : '(' (expression (',' expression)*)? ')'
    ;

primaryTarget
    : ID
    | '(' expression ')'
    ;

// ------------------------
//  Definición de tipos de datos
// ------------------------

dataStatement
    : 'data' ID '=' '{' constructorList '}'
    ;

constructorList
    : constructor (',' constructor)*
    ;

constructor
    : ID arguments?
    ;

arguments
    : '(' argument (',' argument)* ')'
    ;

argument
    : (ID ':')? flatType
    ;

// ------------------------
//  Tipos simples ("flat")
// ------------------------

flatType
    : 'int'
    | 'float'
    | 'double'                     // AGREGADO
    | 'boolean'                    // AGREGADO
    | 'string'
    | 'any'
    | 'void'                       // AGREGADO
    | ID
    | '(' flatTypeList '->' flatType ')'
    ;

flatTypeList
    : flatType (',' flatType)*
    ;

// ------------------------
//  Palabras clave
// ------------------------
FUN: 'fun';

ID: [a-zA-Z_][a-zA-Z0-9_]*; 

INTEGER: [0-9]+;

FLOAT: [0-9]+'.'[0-9]+;

STRING: '"' (ESC | ~["\\])* '"';

fragment ESC: '\\' (["\\/bfnrt]);

WS: [ \t\r\n]+ -> skip;

COMMENT: '//' ~[\r\n]* -> skip;

MULTILINE_COMMENT: '/*' .*? '*/' -> skip;