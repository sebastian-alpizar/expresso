import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

// ==========================================================
//  TyperVisitor
// ----------------------------------------------------------
// Realiza una verificación semántica mínima:
//  - Detecta redefiniciones.
//  - Detecta identificadores no definidos.
//  - Genera un archivo con las asociaciones var/tipo.
// ==========================================================
public class TyperVisitor extends ExprBaseVisitor<Void> {

    // ----------------------------------------------------------
    //  symbolTable: guarda los identificadores y sus "tipos"
    // ----------------------------------------------------------
    private Map<String, String> symbolTable = new HashMap<>();

    // ----------------------------------------------------------
    //  typings: lista de líneas que luego se escribirán a archivo
    // Ejemplo: ["x: ~", "foo: ~", "MyType: type"]
    // ----------------------------------------------------------
    private List<String> typings = new ArrayList<>();

    // ----------------------------------------------------------
    //  errors: conjunto para evitar errores duplicados
    // ----------------------------------------------------------
    private Set<String> errors = new HashSet<>();

    // ==========================================================
    //  Constructor
    // ==========================================================
    public TyperVisitor(String fileName) {
        // Agrega un encabezado con el nombre del archivo analizado
        typings.add("# " + fileName);
    }

    // ==========================================================
    //  Métodos utilitarios
    // ==========================================================

    // Retorna true si hubo errores durante el análisis
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // Imprime todos los errores encontrados en consola
    public void printErrors() {
        for (String error : errors) {
            System.err.println("[ERROR] " + error);
        }
    }

    // Genera un archivo de tipados (ej: typings.txt)
    public void generateTypingsFile(String outputPath) throws IOException {
        Files.writeString(Paths.get(outputPath), String.join("\n", typings));
    }

    // ==========================================================
    //  Visitas principales del AST
    // ==========================================================

    // Visita la raíz del programa: ejecuta el análisis para cada statement
    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        for (ExprParser.StatementContext stmt : ctx.statement()) {
            visit(stmt);
        }
        return null;
    }

    // ----------------------------------------------------------
    //  Verificación de declaraciones 'let'
    // ----------------------------------------------------------
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();

        // Si ya existe, es una redefinición
        if (symbolTable.containsKey(varName)) {
            errors.add("Redefinición de identificador: " + varName);
            return null;
        }

        // Asignamos tipo genérico "~" (sin inferencia aún)
        String type = "~";
        symbolTable.put(varName, type);
        typings.add(varName + ": " + type);

        // No se visita la expresión derecha para mantenerlo simple
        return null;
    }

    // ----------------------------------------------------------
    //  Verificación de funciones 'fun'
    // ----------------------------------------------------------
    @Override
    public Void visitFunStatement(ExprParser.FunStatementContext ctx) {
        String funName = ctx.ID().getText();

        if (symbolTable.containsKey(funName)) {
            errors.add("Redefinición de función: " + funName);
            return null;
        }

        // Tipo genérico "~"
        String type = "~";
        symbolTable.put(funName, type);
        typings.add(funName + ": " + type);

        // No analiza parámetros ni cuerpo
        return null;
    }

    // ----------------------------------------------------------
    //  Verificación de tipos algebraicos 'data'
    // ----------------------------------------------------------
    @Override
    public Void visitDataStatement(ExprParser.DataStatementContext ctx) {
        String typeName = ctx.ID().getText();

        // Verifica redefinición del tipo
        if (symbolTable.containsKey(typeName)) {
            errors.add("Redefinición de tipo: " + typeName);
            return null;
        }

        // Marca el tipo como "type"
        symbolTable.put(typeName, "type");
        typings.add(typeName + ": type");

        // ✅ Registrar los constructores del tipo
        if (ctx.constructorList() != null) {
            for (ExprParser.ConstructorContext ctorCtx : ctx.constructorList().constructor()) {
                String ctorName = ctorCtx.ID().getText();

                if (symbolTable.containsKey(ctorName)) {
                    errors.add("Redefinición de constructor: " + ctorName);
                } else {
                    symbolTable.put(ctorName, "constructor of " + typeName);
                    typings.add(ctorName + ": constructor of " + typeName);
                }
            }
        }

        return null;
    }


    // ----------------------------------------------------------
    //  Sentencia 'print'
    // ----------------------------------------------------------
    @Override
    public Void visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        // No realiza verificación de la expresión dentro del print
        return null;
    }

    // ----------------------------------------------------------
    //  Llamadas a función
    // ----------------------------------------------------------
    @Override
    public Void visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        ExprParser.PrimaryTargetContext target = ctx.primaryTarget();

        if (target.ID() != null) {
            // Llamada del tipo "foo(...)"
            String funName = target.ID().getText();

            // Verificar si la función está definida
            if (!symbolTable.containsKey(funName)) {
                errors.add("Función no definida: " + funName);
            }
        }

        // Si la llamada está entre paréntesis, se omite la verificación
        return null;
    }

    // ----------------------------------------------------------
    //  Expresiones lambda (no verificadas)
    // ----------------------------------------------------------
    @Override 
    public Void visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        return null;
    }

    // ----------------------------------------------------------
    //  Expresiones ternarias (no verificadas)
    // ----------------------------------------------------------
    @Override
    public Void visitTernaryExpression(ExprParser.TernaryExpressionContext ctx) {
        return null;
    }

    // ----------------------------------------------------------
    //  Expresiones primarias (identificadores sueltos)
    // ----------------------------------------------------------
    @Override
    public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        // Solo se revisan IDs que estén directamente dentro de una expresión
        if (ctx.ID() != null && ctx.parent instanceof ExprParser.ExpressionContext) {
            String varName = ctx.ID().getText();

            // Verificar si la variable existe
            if (!symbolTable.containsKey(varName)) {
                errors.add("Variable no definida: " + varName);
            }
        }
        return null;
    }

    // ----------------------------------------------------------
    //  Expresiones de constructores (data)
    // ----------------------------------------------------------
    @Override
    public Void visitConstructorExpr(ExprParser.ConstructorExprContext ctx) {
        String constructorName = ctx.ID().getText();

        // Verifica que el constructor esté declarado
        if (!symbolTable.containsKey(constructorName)) {
            errors.add("Constructor no definido: " + constructorName);
        }
        return null;
    }
}
