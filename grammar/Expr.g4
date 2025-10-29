grammar Expr;

program: (statement ';'?)* EOF;

statement
    : letStatement
    | funStatement
    | printStatement
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
    | ternaryExpression
    | additiveExpression
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

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INTEGER: [0-9]+;
FLOAT: [0-9]+'.'[0-9]+;
STRING: '"' (ESC | ~["\\])* '"';
fragment ESC: '\\' (["\\/bfnrt]);

WS: [ \t\r\n]+ -> skip;
COMMENT: '//' ~[\r\n]* -> skip;
MULTILINE_COMMENT: '/*' .*? '*/' -> skip;