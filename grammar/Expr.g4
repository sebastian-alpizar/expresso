grammar Expr;

program: (statement ';'?)* EOF;

statement
    : letStatement
    | funStatement
    | printStatement
    | dataStatement 
    | expression
    ;

letStatement
    : 'let' ID (':' type)? '=' expression
    ;

funStatement
    : 'fun' ID '(' paramList? ')' (':' type)? '=' expression
    ;

paramList
    : param (',' param)*
    ;

param
    : ID ':' type
    ;
    
type
    : 'int'
    | 'float'
    | 'string'
    | '(' typeList '->' type ')'
    ;

typeList
    : type (',' type)*
    ;

printStatement
    : 'print' '(' expression ')'
    ;

expression
    : lambdaExpression
    | matchExpression
    | constructorExpr
    | ternaryExpression
    | additiveExpression
    ;

constructorExpr
    : '^' ID ('(' expressionList? ')')?
    ;

expressionList
    : expression (',' expression)*
    ;

matchExpression
    : 'match' expression 'with' matchRule ('|' matchRule)* 
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

ternaryExpression
    : additiveExpression '?' expression ':' expression
    ;

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

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : powerExpression (('*' | '/') powerExpression)*
    ;

powerExpression
    : unaryExpression ('**' unaryExpression)*
    ;

unaryExpression
    : ('+' | '-') unaryExpression
    | primaryExpression
    ;

primaryExpression
    : functionCall
    | FLOAT
    | INTEGER
    | STRING
    | ID
    | '(' expression ')'
    ;

functionCall
    : ID '(' (expression (',' expression)*)? ')'
    ;

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

// flatType es como un "tipo base" (no anidado), por ejemplo int, string, (int->int)
flatType
    : 'int'
    | 'float'
    | 'string'
    | ID
    | '(' flatTypeList '->' flatType ')'
    ;

flatTypeList
    : flatType (',' flatType)*
    ;

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INTEGER: [0-9]+;
FLOAT: [0-9]+'.'[0-9]+;
STRING: '"' (ESC | ~["\\])* '"';
fragment ESC: '\\' (["\\/bfnrt]);

WS: [ \t\r\n]+ -> skip;
COMMENT: '//' ~[\r\n]* -> skip;
MULTILINE_COMMENT: '/*' .*? '*/' -> skip;