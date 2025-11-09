<<<<<<< Updated upstream
// TyperVisitor.java - VERSIÓN CORREGIDA
=======
/**
 * @author Daniel Ramirez
 * @author Isella Rios
 * @author Giancarlo Arenas
 * @author Kaleb Rojas
 * @author Sebastian Alpizar
 */


// TyperVisitor.java
>>>>>>> Stashed changes
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

<<<<<<< Updated upstream
=======

>>>>>>> Stashed changes
public class TyperVisitor extends ExprBaseVisitor<Void> {

    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    private final List<String> typings = new ArrayList<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final String fileName;
    
    // Stack para manejar scopes anidados (solo para bindings locales)
    private final Deque<Map<String, String>> scopeStack = new ArrayDeque<>();

<<<<<<< Updated upstream
=======

>>>>>>> Stashed changes
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "int", "float", "double", "boolean", "string", "any", "void");

    public TyperVisitor(String fileName) {
        this.fileName = fileName;
        typings.add("# " + fileName);
        for (String b : BUILTIN_TYPES)
            symbolTable.put(b, b);
        
        // Inicializar con scope global
        scopeStack.push(new HashMap<>(symbolTable));
    }

<<<<<<< Updated upstream
    // -------------------------------
    // Gestión de Scopes (solo para bindings locales)
    // -------------------------------
    private void enterScope() {
        scopeStack.push(new HashMap<>(scopeStack.peek()));
    }

    private void exitScope() {
        if (scopeStack.size() > 1) {
            scopeStack.pop();
        }
    }

    private void addSymbolToCurrentScope(String name, String desc) {
        scopeStack.peek().put(name, desc);
        // NO agregar a symbolTable global - solo para bindings locales
    }

    private void addSymbolToGlobal(String name, String desc) {
        symbolTable.put(name, desc);
        scopeStack.peek().put(name, desc); // También en el scope actual
    }

    private boolean isDefined(String name) {
        return scopeStack.peek().containsKey(name);
    }

    // -------------------------------
    // Métodos principales
    // -------------------------------
=======
   
    // Utilitarios generales
 
>>>>>>> Stashed changes
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void printErrors() {
        errors.forEach(e -> System.err.println("[Typer ERROR] " + e));
    }

    public void generateTypingsFile(String outputDir) throws IOException {
        String outName = fileName.endsWith(".expresso")
                ? fileName.substring(0, fileName.length() - ".expresso".length())
                : fileName;
        Path outPath = Paths.get(outputDir, outName + ".typings");
        Files.createDirectories(outPath.getParent() == null ? Paths.get(".") : outPath.getParent());
        Files.writeString(outPath, String.join("\n", typings) + "\n");
        System.out.println("[Typer] .typings generado en: " + outPath.toAbsolutePath());
    }

    private void addTypingLine(String line) {
        typings.add(line);
    }

    private void reportRedefinition(String kind, String name) {
        errors.add("Redefinición de " + kind + ": " + name);
    }

    private void reportUndefined(String kind, String name) {
        errors.add(kind + " no definido: " + name);
    }

<<<<<<< Updated upstream
    // -------------------------------
    // Two-pass: Primero recolectar declaraciones top-level
    // -------------------------------
=======
 
    // Programa principal
 
>>>>>>> Stashed changes
    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        // FASE 1: Solo recolectar declaraciones top-level (sin visitar expresiones)
        for (ExprParser.StatementContext s : ctx.statement()) {
            if (s.letStatement() != null) {
                visitLetStatementForCollection(s.letStatement());
            } else if (s.funStatement() != null) {
                visitFunStatementForCollection(s.funStatement());
            } else if (s.dataStatement() != null) {
                visit(s.dataStatement()); // Data se procesa completamente en fase 1
            }
            // Ignorar print y expressions en fase de colección
        }
        
        // FASE 2: Verificación completa de tipos
        for (ExprParser.StatementContext s : ctx.statement()) {
            if (s.letStatement() != null || s.funStatement() != null || 
                s.printStatement() != null || s.expression() != null) {
                visit(s);
            }
            // Data ya fue procesado en fase 1
        }
        return null;
    }

<<<<<<< Updated upstream
    /**
     * Fase 1: Solo recolecta declaraciones let sin verificar cuerpos
     */
    private void visitLetStatementForCollection(ExprParser.LetStatementContext ctx) {
=======
 
    // Declaración let
   
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
>>>>>>> Stashed changes
        String varName = ctx.ID().getText();
        if (isDefined(varName)) {
            reportRedefinition("identificador", varName);
            return;
        }

        String typeStr = "~";
        if (ctx.type() != null) {
            typeStr = renderType(ctx.type());
            checkTypeDefinedRecursively(ctx.type());
        }

        addSymbolToGlobal(varName, typeStr);
        addTypingLine(varName + ": " + typeStr);
    }

<<<<<<< Updated upstream
    /**
     * Fase 1: Solo recolecta declaraciones fun sin verificar cuerpos  
     */
    private void visitFunStatementForCollection(ExprParser.FunStatementContext ctx) {
=======
 
    // Declaración de función
  
    @Override
    public Void visitFunStatement(ExprParser.FunStatementContext ctx) {
>>>>>>> Stashed changes
        String funName = ctx.ID().getText();
        if (isDefined(funName)) {
            reportRedefinition("función", funName);
            return;
        }

        List<String> paramTypes = new ArrayList<>();
        if (ctx.paramList() != null) {
            for (ExprParser.ParamContext p : ctx.paramList().param()) {
                String paramType = renderType(p.type());
                paramTypes.add(paramType);
                checkTypeDefinedRecursively(p.type());
            }
        }

        String typeStr = "~";
        if (ctx.type() != null) {
            String ret = renderType(ctx.type());
            checkTypeDefinedRecursively(ctx.type());
            if (paramTypes.isEmpty())
                typeStr = ret;
            else if (paramTypes.size() == 1)
                typeStr = paramTypes.get(0) + " -> " + ret;
            else
                typeStr = "(" + String.join(", ", paramTypes) + " -> " + ret + ")";
        }

<<<<<<< Updated upstream
        addSymbolToGlobal(funName, typeStr);
=======
        // Inferir retorno si hay cast explícito
        if (ctx.type() == null && ctx.expression() != null) {
            ExprParser.TypeContext inferred = findReturnTypeInExpression(ctx.expression());
            if (inferred != null) {
                String ret = renderType(inferred);
                if (!paramTypes.isEmpty())
                    typeStr = paramTypes.get(0) + " -> " + ret;
                else
                    typeStr = ret;
            }
        }

        addSymbol(funName, typeStr);
>>>>>>> Stashed changes
        addTypingLine(funName + ": " + typeStr);
    }

    // -------------------------------
    // FASE 2: Verificación completa
    // -------------------------------

    // -------------------------------
    // Declaración let - FASE 2
    // -------------------------------
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
        // Solo verificar la expresión (la declaración ya se procesó en fase 1)
        if (ctx.expression() != null) {
            visit(ctx.expression());
        }
        return null;
    }

<<<<<<< Updated upstream
    // -------------------------------
    // Declaración de función - FASE 2  
    // -------------------------------
    @Override
    public Void visitFunStatement(ExprParser.FunStatementContext ctx) {
        enterScope(); // Nuevo scope para parámetros

        // Registrar parámetros en el scope local
        if (ctx.paramList() != null) {
            for (ExprParser.ParamContext p : ctx.paramList().param()) {
                String paramName = p.ID().getText();
                String paramType = renderType(p.type());
                addSymbolToCurrentScope(paramName, paramType);
            }
        }

        // Visitar el cuerpo de la función
        if (ctx.expression() != null) {
            visit(ctx.expression());
        }

        exitScope(); // Salir del scope de parámetros
        return null;
    }

    // -------------------------------
    // Lambda Expression - FASE 2 - CORREGIDO
    // -------------------------------
    @Override
    public Void visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        enterScope(); // Nuevo scope para parámetros de lambda

        // Registrar parámetros de lambda en el scope local
        if (ctx.lambdaParams() != null) {
            // Caso 1: Parámetros simples sin tipo (x, y)
            if (ctx.lambdaParams().ID() != null) {
                for (TerminalNode id : ctx.lambdaParams().ID()) {
                    addSymbolToCurrentScope(id.getText(), "~");
                }
            }
            // Caso 2: Parámetros tipados (x:type, y:type) - ¡ESTE ERA EL PROBLEMA!
            else if (ctx.lambdaParams().typedLambdaParams() != null) {
                for (ExprParser.TypedParamContext tp : ctx.lambdaParams().typedLambdaParams().typedParam()) {
                    String paramName = tp.ID().getText();
                    String paramType = renderType(tp.type());
                    addSymbolToCurrentScope(paramName, paramType);
                }
            }
            // Caso 3: Parámetros entre paréntesis (x, y) o (x:type, y:type)
            else if (ctx.lambdaParams().getChild(0) instanceof TerminalNode && 
                     ctx.lambdaParams().getChild(0).getText().equals("(")) {
                // Este caso ya está cubierto por los casos anteriores en la gramática
            }
        }

        // Visitar el cuerpo de la lambda
        if (ctx.expression() != null) {
            visit(ctx.expression());
        }

        exitScope(); // Salir del scope de lambda
        return null;
    }

    // -------------------------------
    // Match Expression - FASE 2
    // -------------------------------
    @Override
    public Void visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        // Visitar la expresión que se está matcheando
        visit(ctx.expression());

        // Procesar cada regla del match
        for (ExprParser.MatchRuleContext rule : ctx.matchRule()) {
            visitMatchRule(rule);
        }
        
        return null;
    }

    @Override
    public Void visitMatchRule(ExprParser.MatchRuleContext ctx) {
        enterScope(); // Nuevo scope para variables de pattern

        // Procesar el pattern - esto define nuevas variables LOCALES
        if (ctx.pattern() != null) {
            visitPatternForBindings(ctx.pattern());
        }

        // Visitar condición when (si existe) y resultado
        if (ctx.expression().size() > 1) {
            visit(ctx.expression(0)); // condición
            visit(ctx.expression(1)); // resultado
        } else if (ctx.expression().size() == 1) {
            visit(ctx.expression(0)); // resultado
        }

        exitScope(); // Salir del scope de pattern
        return null;
    }

    /**
     * Procesa un pattern y registra las variables bindeadas en el scope LOCAL actual
     */
    private void visitPatternForBindings(ExprParser.PatternContext pattern) {
        if (pattern.dataPattern() != null) {
            ExprParser.DataPatternContext dp = pattern.dataPattern();
            String id = dp.ID().getText();

            // Si empieza con minúscula o es '_', es una variable de binding LOCAL
            if (Character.isLowerCase(id.charAt(0)) || id.equals("_")) {
                addSymbolToCurrentScope(id, "~");
            } else {
                // Constructor - validar que existe GLOBALMENTE
                if (!isDefined(id)) {
                    reportUndefined("Constructor", id);
                }
            }

            // Procesar subpatterns recursivamente
            if (dp.pattern() != null) {
                for (ExprParser.PatternContext subPattern : dp.pattern()) {
                    visitPatternForBindings(subPattern);
                }
            }
        }
        // Los native patterns (literales) no crean bindings
    }

    // -------------------------------
    // Primary Expression - FASE 2
    // -------------------------------
    @Override
    public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            if (!name.equals("_") && !isDefined(name)) {
                reportUndefined("Variable", name);
            }
        }
        if (ctx.expression() != null)
            visit(ctx.expression());
        return null;
    }

    // -------------------------------
    // Data Statement - Se procesa completamente en FASE 1
    // -------------------------------
=======

    // Declaración de data

>>>>>>> Stashed changes
    @Override
    public Void visitDataStatement(ExprParser.DataStatementContext ctx) {
        String typeName = ctx.ID().getText();
        if (isDefined(typeName)) {
            reportRedefinition("tipo", typeName);
            return null;
        }

        addSymbolToGlobal(typeName, "type");
        List<String> ctorNames = new ArrayList<>();
        List<String> ctorLines = new ArrayList<>();

        for (ExprParser.ConstructorContext cc : ctx.constructorList().constructor()) {
            String ctor = cc.ID().getText();
            if (isDefined(ctor)) {
                reportRedefinition("constructor", ctor);
                continue;
            }
            ctorNames.add(ctor);
            addSymbolToGlobal(ctor, "constructor of " + typeName);

            String ctorArg = "void";
            if (cc.arguments() != null) {
                List<String> args = new ArrayList<>();
                for (ExprParser.ArgumentContext a : cc.arguments().argument()) {
                    String r = renderFlatType(a.flatType());
                    args.add(r);
                    checkFlatTypeDefined(a.flatType());
                }
                ctorArg = args.size() == 1 ? args.get(0) : "(" + String.join(", ", args) + ")";
            }

            ctorLines.add(ctor + ": " + ctorArg + " ^ " + typeName);
        }

        addTypingLine(typeName + ": " + String.join("|", ctorNames));
        for (String line : ctorLines) {
            addTypingLine(line);
        }

        return null;
    }

<<<<<<< Updated upstream
    // -------------------------------
    // Otros visit methods - FASE 2
    // -------------------------------
    @Override
=======

    // Otros visit

>>>>>>> Stashed changes
    public Void visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        if (ctx.expressionList() != null) {
            for (ExprParser.ExpressionContext expr : ctx.expressionList().expression()) {
                visit(expr);
            }
        }
        return null;
    }

    @Override
    public Void visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        ExprParser.PrimaryTargetContext target = ctx.primaryTarget();
        if (target.ID() != null && !isDefined(target.ID().getText()))
            reportUndefined("Función", target.ID().getText());
        if (ctx.callArgs() != null)
            for (var c : ctx.callArgs())
                if (c.expression() != null)
                    for (var e : c.expression())
                        visit(e);
        return null;
    }

    @Override
    public Void visitConstructorExpr(ExprParser.ConstructorExprContext ctx) {
        if (!isDefined(ctx.ID().getText()))
            reportUndefined("Constructor", ctx.ID().getText());
        if (ctx.expressionList() != null)
            visit(ctx.expressionList());
        return null;
    }

<<<<<<< Updated upstream
    // -------------------------------
    // Métodos de utilidad
    // -------------------------------
=======
    @Override
    public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.ID() != null && !isDefined(ctx.ID().getText()))
            reportUndefined("Variable", ctx.ID().getText());
        if (ctx.expression() != null)
            visit(ctx.expression());
        return null;
    }

    @Override
    public Void visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        visit(ctx.expression());
        for (var r : ctx.matchRule())
            visit(r);
        return null;
    }

    @Override
    public Void visitMatchRule(ExprParser.MatchRuleContext ctx) {
        if (ctx.pattern() != null)
            visit(ctx.pattern());
        if (ctx.expression() != null)
            for (var e : ctx.expression())
                visit(e);
        return null;
    }

    @Override
    public Void visitDataPattern(ExprParser.DataPatternContext ctx) {
        if (!isDefined(ctx.ID().getText()))
            reportUndefined("Constructor (en pattern)", ctx.ID().getText());
        if (ctx.pattern() != null)
            for (var p : ctx.pattern())
                visit(p);
        return null;
    }


    // Render y validaciones

>>>>>>> Stashed changes
    private String renderType(ExprParser.TypeContext t) {
        if (t == null) return "~";
        String text = t.getText();
        if (BUILTIN_TYPES.contains(text)) return text;
        if (text.contains("->")) {
            return text.replaceAll("\\s+", "").replace("->", " -> ");
        }
        return text;
    }

    private String renderFlatType(ExprParser.FlatTypeContext ft) {
        return ft == null ? "~" : ft.getText().trim();
    }

    private void checkFlatTypeDefined(ExprParser.FlatTypeContext ft) {
        if (ft == null) return;
        if (ft.ID() != null) {
            String n = ft.ID().getText();
            if (!BUILTIN_TYPES.contains(n) && !isDefined(n))
                reportUndefined("Tipo", n);
        }
    }

    private void checkTypeDefinedRecursively(ExprParser.TypeContext t) {
        if (t == null) return;
        for (int i = 0; i < t.getChildCount(); i++) {
            var c = t.getChild(i);
            if (c instanceof TerminalNode tn) {
                String txt = tn.getText();
                if (!txt.isEmpty() && Character.isLetter(txt.charAt(0)) &&
                        !BUILTIN_TYPES.contains(txt) && !isDefined(txt))
                    reportUndefined("Tipo", txt);
            } else if (c instanceof ExprParser.TypeContext sub)
                checkTypeDefinedRecursively(sub);
        }
    }
<<<<<<< Updated upstream
}
=======


    // Lambda utils con DEBUG 
    private ExprParser.LambdaExpressionContext findNestedLambda(ExprParser.ExpressionContext expr) {
        if (expr == null)
            return null;
        if (expr.lambdaExpression() != null)
            return expr.lambdaExpression();
        for (int i = 0; i < expr.getChildCount(); i++) {
            var c = expr.getChild(i);
            if (c instanceof ExprParser.ExpressionContext sub) {
                var found = findNestedLambda(sub);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

   
    private String tryExtractLambdaDeclaredType(ExprParser.LambdaExpressionContext lambda, String contextName,
            ParserRuleContext fullExpr) {
        if (lambda == null)
            return null;
        ExprParser.LambdaParamsContext lp = lambda.lambdaParams();
        if (lp == null)
            return null;

        List<String> paramTypes = new ArrayList<>();
        if (lp.typedLambdaParams() != null)
            for (var t : lp.typedLambdaParams().typedParam()) {
                paramTypes.add(renderType(t.type()));
                checkTypeDefinedRecursively(t.type());
            }

        String returnType = "~";
        // 1) buscar un cast explícito dentro de la lambda body (preferible)
        ExprParser.TypeContext cast = findReturnTypeInExpression(lambda.expression());
        if (cast != null) {
            returnType = renderType(cast);
        } else {
            // 2) si no hay cast dentro de la lambda, buscar en la expresión completa del RHS
          
            if (fullExpr != null) {
                ExprParser.TypeContext castOuter = findReturnTypeInExpression((ExprParser.ExpressionContext) fullExpr);
                if (castOuter != null) {
                    returnType = renderType(castOuter);
                }
            }
        }

        // 3) heurística sobre el cuerpo si aún no determinamos
        if ("~".equals(returnType)) {
            String body = lambda.expression().getText();
            if (body.matches(".*(==|!=|&&|\\|\\||<|>|<=|>=).*"))
                returnType = "boolean";
            else if (body.matches(".*\".*\".*") || body.contains("String.valueOf"))
                returnType = "string";
            else if (body.matches(".*\\d+\\.\\d+.*"))
                returnType = "double";
            else if (body.matches(".*\\d+.*") && body.matches(".*\\+.*|.*-.*|.*\\*.*|.*/.*"))
                returnType = "int";
            else if (body.contains("^") || body.contains("new "))
                returnType = "any";
            // no sobreescribir si heurística débil no se ajusta
        }

        // formar resultado
        String result;
        if (paramTypes.isEmpty())
            result = "~";
        else if (paramTypes.size() == 1)
            result = paramTypes.get(0) + " -> " + returnType;
        else
            result = "(" + String.join(", ", paramTypes) + " -> " + returnType + ")";

        return result;
    }

    // Busca el primer CastExpressionContext dentro de cualquier subárbol de la
    // expresión
    private ExprParser.TypeContext findReturnTypeInExpression(ExprParser.ExpressionContext expr) {
        if (expr == null)
            return null;

        // Caso directo
        if (expr.castExpression() != null && expr.castExpression().type() != null) {
            return expr.castExpression().type();
        }

        // Recorrido general (profundo)
        for (int i = 0; i < expr.getChildCount(); i++) {
            var child = expr.getChild(i);

            // 🔹 Si el hijo es un CastExpressionContext con tipo -> ¡encontrado!
            if (child instanceof ExprParser.CastExpressionContext cast && cast.type() != null) {
                return cast.type();
            }

            // 🔹 Si es cualquier otro ParserRuleContext, seguir buscando dentro
            if (child instanceof ParserRuleContext prc) {
                var found = findReturnTypeInExpressionRecursive(prc);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    // Recorrido recursivo auxiliar para cualquier nodo ParserRuleContext
    private ExprParser.TypeContext findReturnTypeInExpressionRecursive(ParserRuleContext node) {
        if (node == null)
            return null;

        if (node instanceof ExprParser.CastExpressionContext cast && cast.type() != null) {
            return cast.type();
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            var child = node.getChild(i);
            if (child instanceof ParserRuleContext prc) {
                var found = findReturnTypeInExpressionRecursive(prc);
                if (found != null)
                    return found;
            }
        }
        return null;
    }
}
>>>>>>> Stashed changes
