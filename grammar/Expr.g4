grammar Expr;

program
  : statement* EOF
  ;

statement
  : letStatement
  | printStatement
  | exprStatement
  ;

letStatement
  : LET ID EQ expr SEMI?
  ;

printStatement
  : PRINT '(' expr ')' SEMI?
  ;

exprStatement
  : expr SEMI?
  ;

expr
  : ternaryExpr
  ;

ternaryExpr
  : lambdaExpr ('?' ternaryExpr ':' ternaryExpr)?  # TernaryOp
  ;

lambdaExpr
  : lambdaExpr ARROW lambdaExpr                  # LambdaRightAssoc
  | orExpr                                       # ToOrExpr
  ;

orExpr
  : orExpr '||' andExpr                          # OrOp
  | andExpr                                      # ToAndExpr
  ;

andExpr
  : andExpr '&&' eqExpr                          # AndOp
  | eqExpr                                       # ToEqExpr
  ;

eqExpr
  : eqExpr ('==' | '!=') relExpr                 # EqualOp
  | relExpr                                      # ToRelExpr
  ;

relExpr
  : relExpr ('<' | '>' | '<=' | '>=') addExpr    # CompareOp
  | addExpr                                      # ToAddExpr
  ;

addExpr
  : addExpr ('+' | '-') mulExpr                  # AddSubOp
  | mulExpr                                      # ToMulExpr
  ;

mulExpr
  : mulExpr ('*' | '/' | '%') powExpr            # MulDivModOp
  | powExpr                                      # ToPowExpr
  ;

powExpr
  : unaryExpr '**' powExpr                       # PowOp   // right-associative
  | unaryExpr                                    # ToUnaryExpr
  ;

unaryExpr
  : ('-' | '!') unaryExpr                        # UnaryOp
  | primary                                      # ToPrimaryExpr
  ;

primary
  : INT                                          # IntLiteral
  | ID                                           # VarExpr
  | STRING                                       # StringLiteral
  | lambdaDefinition                             # LambdaPrimary
  | ID '(' argList? ')'                          # CallExpr
  | '(' expr ')'                                 # ParenExpr
  ;

lambdaDefinition
  : '(' paramList? ')' ARROW expr                # LambdaDef
  ;

paramList
  : ID (',' ID)*
  ;

argList
  : expr (',' expr)*
  ;

// --- LEXER RULES ---

// Palabras reservadas
LET   : 'let';
PRINT : 'print';

// Operadores
ARROW : '->';
EQ    : '=';
POW   : '**';
LPAR  : '(';
RPAR  : ')';
PLUS  : '+';
MINUS : '-';
STAR  : '*';
DIV   : '/';
MOD   : '%';
COMMA : ',';
SEMI  : ';';
QUEST : '?';
COLON : ':';
NOT   : '!';
AND   : '&&';
OR    : '||';
EQEQ  : '==';
NEQ   : '!=';
LT    : '<';
GT    : '>';
LE    : '<=';
GE    : '>=';

// Identificadores y números
INT   : [0-9]+;
ID    : [a-zA-Z_][a-zA-Z_0-9]*;
STRING : '"' ( ESC | ~["\\] )* '"';
fragment ESC : '\\' ( ["\\/bfnrt] | UNICODE );
fragment UNICODE : 'u' [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F];

// Espacios en blanco y comentarios
WS    : [ \t\r\n]+ -> skip;
COMMENT : '//' ~[\r\n]* -> skip;
ML_COMMENT : '/*' .*? '*/' -> skip;