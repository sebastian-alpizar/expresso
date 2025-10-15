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
    : INTEGER
    | ID
    | '(' expression ')'
    | functionCall
    ;

functionCall: ID '(' (expression (',' expression)*)? ')';

ID: [a-zA-Z_][a-zA-Z0-9_]*;
INTEGER: [0-9]+;
WS: [ \t\r\n]+ -> skip;

// Mejora las reglas de comentarios para soportar caracteres especiales
COMMENT: '//' ~[\r\n]* -> skip;
MULTILINE_COMMENT: '/*' .*? '*/' -> skip;  // Cambio importante aquí