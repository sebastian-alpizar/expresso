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
    | additiveExpression
    ;

lambdaExpression
    : ID '->' expression
    | '(' ID '->' expression ')'
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
    : INTEGER
    | ID
    | '(' expression ')'
    | functionCall
    ;

functionCall: ID '(' expression? ')';

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INTEGER: [0-9]+;
WS: [ \t\r\n]+ -> skip;
COMMENT: '//' ~[\r\n]* -> skip;