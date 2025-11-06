grammar Expr; // Nombre de la gramática principal: Expr

// ========================
//  Reglas principales
// ========================

program: (statement ';'?)* EOF; 
// Un programa es una secuencia de sentencias (statement)
// Cada una puede terminar opcionalmente con ';'
// El programa termina con EOF (fin del archivo)

// ------------------------
//  Sentencias principales
// ------------------------

statement
    : letStatement       // Declaración de variable
    | funStatement       // Declaración de función
    | printStatement     // Sentencia de impresión
    | dataStatement      // Definición de tipo de datos
    | expression         // Expresión evaluable
    ;

// ------------------------
//  Declaración de variables
// ------------------------

letStatement
    : 'let' ID (':' type)? '=' expression
    ;
// Ejemplo: let x: int = 5
// 'let' define una variable, con tipo opcional y valor inicial

// ------------------------
//  Declaración de funciones
// ------------------------

funStatement
    : 'fun' ID '(' paramList? ')' (':' type)? '=' expression
    ;
// Ejemplo: fun add(x:int, y:int): int = x + y
// Define una función con nombre, parámetros, tipo opcional y cuerpo

paramList
    : param (',' param)*
    ;
// Lista de parámetros separados por coma: (x:int, y:int)

param
    : ID ':' type
    ;
// Define un parámetro con nombre y tipo

// ------------------------
//  Tipos de datos
// ------------------------

type
    : 'int'                        // Tipo entero
    | 'float'                      // Tipo flotante
    | 'string'                     // Tipo cadena
    | '(' typeList '->' type ')'   // Tipo función (int, float -> string)
    ;
// Ejemplo: (int, float -> string)

typeList
    : type (',' type)*
    ;
// Lista de tipos separados por coma, usada en funciones

// ------------------------
//  Sentencia print
// ------------------------

printStatement
    : 'print' '(' expression ')'
    ;
// Ejemplo: print(x + 1)
// Imprime el resultado de una expresión

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
    ;
// Una expresión puede ser ternaria, lógica, aritmética, lambda, match, constructor o cast

// ------------------------
//  Expresiones lógicas
// ------------------------

logicalOrExpression
    : logicalAndExpression ('||' logicalAndExpression)*
    ;
// Permite usar OR lógico (||)

logicalAndExpression
    : equalityExpression ('&&' equalityExpression)*
    ;
// Permite usar AND lógico (&&)

equalityExpression
    : relationalExpression (('==' | '!=') relationalExpression)*
    ;
// Permite comparar igualdad (==) o desigualdad (!=)

relationalExpression
    : additiveExpression (('<' | '<=' | '>' | '>=') additiveExpression)*
    ;
// Permite comparaciones relacionales (<, <=, >, >=)

// ------------------------
//  Constructores de datos
// ------------------------

constructorExpr
    : '^' ID ('(' expressionList? ')')?
    ;
// Ejemplo: ^Some(5) o ^None
// Crea una instancia de un constructor definido con 'data'

expressionList
    : expression (',' expression)*
    ;
// Lista de expresiones separadas por coma (para llamadas o constructores)

// ------------------------
//  Match / pattern matching
// ------------------------

matchExpression
    : 'match' expression 'with' matchRule ('|' matchRule)* 
    ;
// Ejemplo:
// match x with
// | Some(y) -> y
// | None -> 0
// Evalúa patrones sobre un valor

matchRule
    : pattern ('when' expression)? '->' expression
    ;
// Una regla del match: patrón, condición opcional 'when', y resultado

pattern
    : dataPattern
    | nativePattern
    ;
// Un patrón puede ser de datos o nativo

dataPattern
    : ID ('(' pattern (',' pattern)* ')')?
    ;
// Patrón para coincidencia con constructores de datos (ej: Some(x), Pair(a,b))

nativePattern
    : 'none'
    | STRING
    | INTEGER
    | FLOAT
    ;
// Patrón para valores literales o el valor especial 'none'

// ------------------------
//  Expresión ternaria
// ------------------------

ternaryExpression
    : logicalOrExpression '?' expression ':' expression
    ;
// Ejemplo: a > 0 ? 1 : 0
// Evalúa una condición y retorna uno de dos valores

// ------------------------
//  Expresiones lambda (funciones anónimas)
// ------------------------

lambdaExpression
    : lambdaParams '->' expression
    | '(' lambdaParams '->' expression ')'
    ;
// Ejemplo: x -> x + 1
// Define una función anónima

lambdaParams
    : ID (',' ID)*                  // Varios parámetros sin tipos
    | '(' ID (',' ID)* ')'          // Igual pero con paréntesis
    | typedLambdaParams             // Parámetros con tipos explícitos
    ;
// Ejemplo: (x, y) -> x + y

typedLambdaParams
    : typedParam (',' typedParam)*              // Parámetros tipados sin paréntesis
    | '(' typedParam (',' typedParam)* ')'      // O con paréntesis
    ;
// Ejemplo: (x:int, y:int) -> x + y

typedParam
    : ID ':' type
    ;
// Parámetro con tipo (ejemplo: x:int)

// ------------------------
//  Casteo de tipos
// ------------------------

castExpression
    : unaryExpression (':' type)?
    ;
// Ejemplo: x : float
// Convierte una expresión a otro tipo

// ------------------------
//  Operadores aritméticos
// ------------------------

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;
// Suma y resta (+, -)

multiplicativeExpression
    : powerExpression (('*' | '/') powerExpression)*
    ;
// Multiplicación y división (*, /)

powerExpression
    : castExpression ('**' castExpression)*
    ;
// Potencia (**)

// ------------------------
//  Expresiones unarias
// ------------------------

unaryExpression
    : ('+' | '-') unaryExpression
    | primaryExpression
    ;
// Permite signos unarios (+x, -x)

// ------------------------
//  Expresiones primarias
// ------------------------

primaryExpression
    : functionCall        // Llamadas a funciones
    | FLOAT               // Literal flotante
    | INTEGER             // Literal entero
    | STRING              // Literal string
    | ID                  // Identificador
    | booleanLiteral      // true / false
    | '(' expression ')'  // Expresión entre paréntesis
    ;

// ------------------------
//  Booleanos
// ------------------------

booleanLiteral
    : 'true'
    | 'false'
    ;
// Literales booleanos

// ------------------------
//  Llamadas a funciones
// ------------------------

functionCall
    : primaryTarget callArgs+
    ;
// Una o más llamadas encadenadas (ej: f(1)(2))

callArgs
    : '(' (expression (',' expression)*)? ')'
    ;
// Argumentos de llamada de función

primaryTarget
    : ID
    | '(' expression ')'
    ;
// Identificador o subexpresión (ej: (f)(x))

// ------------------------
//  Definición de tipos de datos
// ------------------------

dataStatement
    : 'data' ID '=' '{' constructorList '}'
    ;
// Ejemplo:
// data Option = { Some(value:int), None }
// Define un nuevo tipo con constructores

constructorList
    : constructor (',' constructor)*
    ;
// Lista de constructores separados por coma

constructor
    : ID arguments?
    ;
// Un constructor con nombre y argumentos opcionales

arguments
    : '(' argument (',' argument)* ')'
    ;
// Lista de argumentos (con o sin nombre)

argument
    : (ID ':')? flatType
    ;
// Argumento con tipo opcionalmente nombrado (value:int)

// ------------------------
//  Tipos simples ("flat")
// ------------------------

flatType
    : 'int'
    | 'float'
    | 'string'
    | ID
    | '(' flatTypeList '->' flatType ')'
    ;
// Tipos básicos o funciones (int, float -> string)

flatTypeList
    : flatType (',' flatType)*
    ;
// Lista de tipos planos

// ------------------------
//  Tokens léxicos
// ------------------------

ID: [a-zA-Z_][a-zA-Z0-9_]*; 
// Identificadores: letras, números y guiones bajos, sin empezar con número

INTEGER: [0-9]+;
// Enteros

FLOAT: [0-9]+'.'[0-9]+;
// Números flotantes

STRING: '"' (ESC | ~["\\])* '"';
// Cadenas entre comillas

fragment ESC: '\\' (["\\/bfnrt]);
// Secuencias de escape válidas dentro de cadenas

WS: [ \t\r\n]+ -> skip;
// Espacios, tabulaciones y saltos de línea (ignorados)

COMMENT: '//' ~[\r\n]* -> skip;
// Comentarios de una línea (// ...)

MULTILINE_COMMENT: '/*' .*? '*/' -> skip;
// Comentarios multilínea (/* ... */)
