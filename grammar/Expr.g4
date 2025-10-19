grammar Expr;

program: (statement ';'?)* EOF;

statement
    : letStatement
    | printStatement
    | expression
    ;

letStatement: 'let' ID '=' expression;
printStatement: 'print' '(' expression ')';

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
    | INTEGER
    | ID
    | '(' expression ')' 
    ;

functionCall: ID '(' (expression (',' expression)*)? ')';

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INTEGER: [0-9]+;
WS: [ \t\r\n]+ -> skip;

COMMENT: '//' ~[\r\n]* -> skip;
MULTILINE_COMMENT: '/*' .*? '*/' -> skip;