import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TyperVisitor extends ExprBaseVisitor<Void> {
    private Map<String, String> symbolTable = new HashMap<>();
    private List<String> typings = new ArrayList<>();
    private Set<String> errors = new HashSet<>();
    
    public TyperVisitor(String fileName) {
        typings.add("# " + fileName);
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public void printErrors() {
        for (String error : errors) {
            System.err.println("[ERROR] " + error);
        }
    }
    
    public void generateTypingsFile(String outputPath) throws IOException {
        Files.writeString(Paths.get(outputPath), String.join("\n", typings));
    }
    
    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        for (ExprParser.StatementContext stmt : ctx.statement()) {
            visit(stmt);
        }
        return null;
    }
    
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        
        // Verificar redefinición a nivel global
        if (symbolTable.containsKey(varName)) {
            errors.add("Redefinición de identificador: " + varName);
            return null;
        }
        
        // Tipo ~ para todos (sin inferencia en esta fase)
        String type = "~";
        symbolTable.put(varName, type);
        typings.add(varName + ": " + type);
        
        return null; // NO visitar la expresión - verificación minimalista
    }
    
    @Override
    public Void visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        return null; // NO verificar la expresión dentro de print
    }
    
    @Override
    public Void visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        String funName = ctx.ID().getText();
        
        // SOLO verificar que la función esté definida a nivel global
        if (!symbolTable.containsKey(funName)) {
            errors.add("Función no definida: " + funName);
        }
        
        return null; // NO verificar argumentos
    }
    
    // ELIMINAR todas las demás verificaciones de expresiones
    // En esta fase minimalista solo nos importan las declaraciones globales
    
    @Override 
    public Void visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        return null; // NO verificar lambdas internas
    }
    
    @Override
    public Void visitTernaryExpression(ExprParser.TernaryExpressionContext ctx) {
        return null; // NO verificar expresiones ternarias
    }
    
    @Override
    public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        return null; // NO verificar identificadores en expresiones
    }
}