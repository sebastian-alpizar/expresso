
// TyperVisitor.java
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * TyperVisitor - recolecta tipos top-level y hace comprobaciones mínimas:
 * - Detecta redefiniciones (variables, funciones, tipos, constructores)
 * - Detecta referencias a identificadores no definidos (incluyendo tipos)
 * - Genera <filename>.typings con las asociaciones
 *
 * Añadido: debug ampliado (DEBUG_LEVEL)
 */
public class TyperVisitor extends ExprBaseVisitor<Void> {

    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    private final List<String> typings = new ArrayList<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final String fileName;

    /**
     * DEBUG_LEVEL:
     * 0 = off
     * 1 = básico (lambda detectada + resultado)
     * 2 = verboso (imprime cuerpos, búsqueda de casts, árbol de expresión)
     */
    private static final int DEBUG_LEVEL = 2;

    private static final Set<String> BUILTIN_TYPES = Set.of(
            "int", "float", "double", "boolean", "string", "any", "void");

    public TyperVisitor(String fileName) {
        this.fileName = fileName;
        typings.add("# " + fileName);
        for (String b : BUILTIN_TYPES)
            symbolTable.put(b, b);
    }

    // -------------------------------
    // Utilitarios generales
    // -------------------------------
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

    private void addSymbol(String name, String desc) {
        symbolTable.put(name, desc);
    }

    private boolean isDefined(String name) {
        return symbolTable.containsKey(name);
    }

    private void reportRedefinition(String kind, String name) {
        errors.add("Redefinición de " + kind + ": " + name);
    }

    private void reportUndefined(String kind, String name) {
        errors.add(kind + " no definido: " + name);
    }

    // -------------------------------
    // Programa principal
    // -------------------------------
    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        for (ExprParser.StatementContext s : ctx.statement())
            visit(s);
        return null;
    }

    // -------------------------------
    // Declaración let
    // -------------------------------
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        if (isDefined(varName)) {
            reportRedefinition("identificador", varName);
            return null;
        }

        String typeStr = "~";
        if (ctx.type() != null) {
            typeStr = renderType(ctx.type());
            checkTypeDefinedRecursively(ctx.type());
        }

        // Debug: mostrar el texto de la expresión RHS
        if (DEBUG_LEVEL >= 1) {
            String exprText = ctx.expression() != null ? ctx.expression().getText() : "<null>";
            System.out.printf("[DEBUG LET] %s : declared type='%s' RHS text='%s'%n", varName, typeStr, exprText);
            if (DEBUG_LEVEL >= 2 && ctx.expression() != null) {
                System.out.println("[DEBUG LET] dump RHS parse tree:");
                dumpExprTree(ctx.expression(), 0);
            }
        }

        // 🔍 Inferencia de lambdas (directas o anidadas)
        if (ctx.expression() != null) {
            ExprParser.LambdaExpressionContext lambda = ctx.expression().lambdaExpression();
            if (lambda == null)
                lambda = findNestedLambda(ctx.expression());
            if (lambda != null) {
                if (DEBUG_LEVEL >= 1) {
                    System.out.printf("[DEBUG] Lambda encontrada para '%s' -> lambda text = '%s'%n",
                            varName, lambda.getText());
                    if (DEBUG_LEVEL >= 2) {
                        System.out.println("[DEBUG] dump lambda subtree:");
                        dumpExprTree(lambda, 0);
                    }
                }
                String inferred = tryExtractLambdaDeclaredType(lambda, varName, ctx.expression());
                if (DEBUG_LEVEL >= 1) {
                    System.out.printf("[DEBUG] Inferencia para '%s' => %s (previo='%s')%n", varName, inferred, typeStr);
                }
                if (inferred != null && !inferred.equals("~"))
                    typeStr = inferred;
            } else {
                // si no es lambda, aún intentamos encontrar un cast dentro de la expresión
                ExprParser.TypeContext found = findReturnTypeInExpression(ctx.expression());
                if (found != null) {
                    String foundTxt = renderType(found);
                    if (DEBUG_LEVEL >= 1)
                        System.out.printf("[DEBUG] Cast encontrado en RHS de '%s' => %s%n", varName, foundTxt);
                    // si let no tenía tipo explícito, usarlo como tipo de variable (no para
                    // funciones)
                    if (ctx.type() == null) {
                        // solo asignar si es tipo plano (int/boolean/string/...)
                        if (!foundTxt.equals("~"))
                            typeStr = foundTxt;
                    }
                } else if (DEBUG_LEVEL >= 2) {
                    System.out.printf("[DEBUG] No se encontró cast en RHS de '%s'%n", varName);
                }
            }
        }

        addSymbol(varName, typeStr);
        addTypingLine(varName + ": " + typeStr);
        return null;
    }

    // -------------------------------
    // Declaración de función
    // -------------------------------
    @Override
    public Void visitFunStatement(ExprParser.FunStatementContext ctx) {
        String funName = ctx.ID().getText();
        if (isDefined(funName)) {
            reportRedefinition("función", funName);
            return null;
        }

        List<String> paramTypes = new ArrayList<>();
        if (ctx.paramList() != null) {
            for (ExprParser.ParamContext p : ctx.paramList().param()) {
                String t = renderType(p.type());
                paramTypes.add(t);
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
        } else if (!paramTypes.isEmpty()) {
            typeStr = "(" + String.join(", ", paramTypes) + " -> ~)";
        }

        if (DEBUG_LEVEL >= 2) {
            System.out.printf("[DEBUG FUN] %s params=%s declaredType=%s%n", funName, paramTypes, typeStr);
            if (ctx.expression() != null)
                dumpExprTree(ctx.expression(), 0);
        }

        // 🔍 Inferir retorno si hay cast explícito
        if (ctx.type() == null && ctx.expression() != null) {
            ExprParser.TypeContext inferred = findReturnTypeInExpression(ctx.expression());
            if (inferred != null) {
                String ret = renderType(inferred);
                if (!paramTypes.isEmpty())
                    typeStr = paramTypes.get(0) + " -> " + ret;
                else
                    typeStr = ret;
                if (DEBUG_LEVEL >= 1)
                    System.out.printf("[DEBUG] Fun '%s' inferido retorno = %s%n", funName, typeStr);
            }
        }

        addSymbol(funName, typeStr);
        addTypingLine(funName + ": " + typeStr);
        return null;
    }

    // -------------------------------
    // Declaración de data
    // -------------------------------
    @Override
    public Void visitDataStatement(ExprParser.DataStatementContext ctx) {
        String typeName = ctx.ID().getText();
        if (isDefined(typeName)) {
            reportRedefinition("tipo", typeName);
            return null;
        }

        addSymbol(typeName, "type");
        List<String> ctorNames = new ArrayList<>();

        for (ExprParser.ConstructorContext cc : ctx.constructorList().constructor()) {
            String ctor = cc.ID().getText();
            if (isDefined(ctor)) {
                reportRedefinition("constructor", ctor);
                continue;
            }
            ctorNames.add(ctor);
            addSymbol(ctor, "constructor of " + typeName);

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

            addTypingLine(ctor + ": " + ctorArg + " ^ " + typeName);
        }

        addTypingLine(typeName + ": " + String.join("|", ctorNames));
        return null;
    }

    // -------------------------------
    // Otros visit
    // -------------------------------
    @Override
    public Void visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        if (ctx.expression() != null)
            visit(ctx.expression());
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

    // -------------------------------
    // Render y validaciones
    // -------------------------------
    private String renderType(ExprParser.TypeContext t) {
        if (t == null)
            return "~";
        String raw = t.getText().trim().replaceAll("\\s+", "");
        return raw.replaceAll("[()]", "");
    }

    private String renderFlatType(ExprParser.FlatTypeContext ft) {
        return ft == null ? "~" : ft.getText().trim();
    }

    private void checkFlatTypeDefined(ExprParser.FlatTypeContext ft) {
        if (ft == null)
            return;
        if (ft.ID() != null) {
            String n = ft.ID().getText();
            if (!BUILTIN_TYPES.contains(n) && !isDefined(n))
                reportUndefined("Tipo", n);
        }
    }

    private void checkTypeDefinedRecursively(ExprParser.TypeContext t) {
        if (t == null)
            return;
        for (int i = 0; i < t.getChildCount(); i++) {
            var c = t.getChild(i);
            if (c instanceof TerminalNode tn) {
                String txt = tn.getText();
                if (Character.isLetter(txt.charAt(0)) && !BUILTIN_TYPES.contains(txt) && !isDefined(txt))
                    reportUndefined("Tipo", txt);
            } else if (c instanceof ExprParser.TypeContext sub)
                checkTypeDefinedRecursively(sub);
        }
    }

    // -------------------------------
    // Lambda utils con DEBUG (mejorada)
    // -------------------------------
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

    /**
     * tryExtractLambdaDeclaredType: ahora con más heurísticas y debug.
     * - lambda: nodo lambda
     * - contextName: nombre de la variable/function donde se detectó (para debug)
     * - fullExpr: expresión completa del RHS (puede ayudar a buscar casts más
     * arriba)
     */
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
            if (DEBUG_LEVEL >= 1)
                System.out.printf("[DEBUG cast] En lambda '%s' encontrado cast directo: %s%n", contextName, returnType);
        } else {
            // 2) si no hay cast dentro de la lambda, buscar en la expresión completa del
            // RHS
            if (fullExpr != null) {
                ExprParser.TypeContext castOuter = findReturnTypeInExpression((ExprParser.ExpressionContext) fullExpr);
                if (castOuter != null) {
                    returnType = renderType(castOuter);
                    if (DEBUG_LEVEL >= 1)
                        System.out.printf("[DEBUG cast] En lambda '%s' encontrado cast en RHS externo: %s%n",
                                contextName, returnType);
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

        if (DEBUG_LEVEL >= 1) {
            System.out.printf("[DEBUG] Lambda detectada en '%s': params=%s body=%s => inferido = %s%n",
                    contextName, paramTypes, lambda.expression().getText(), result);
        }

        return result;
    }

    // Busca el primer CastExpressionContext dentro de cualquier subárbol de la
    // expresión
    private ExprParser.TypeContext findReturnTypeInExpression(ExprParser.ExpressionContext expr) {
        if (expr == null)
            return null;

        // Caso directo
        if (expr.castExpression() != null && expr.castExpression().type() != null) {
            if (DEBUG_LEVEL >= 2)
                System.out.println("[TRACE findReturn] direct cast at top: " + expr.castExpression().getText());
            return expr.castExpression().type();
        }

        // Recorrido general (profundo)
        for (int i = 0; i < expr.getChildCount(); i++) {
            var child = expr.getChild(i);

            // 🔹 Si el hijo es un CastExpressionContext con tipo -> ¡encontrado!
            if (child instanceof ExprParser.CastExpressionContext cast && cast.type() != null) {
                if (DEBUG_LEVEL >= 2)
                    System.out.println("[TRACE findReturn] cast found: " + cast.getText());
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
            if (DEBUG_LEVEL >= 2)
                System.out.println("[TRACE findReturn/rec] cast found: " + cast.getText());
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

    // -------------------------------
    // Helper: imprime subárbol del parse tree (útil para debug)
    // -------------------------------
    private void dumpExprTree(ParserRuleContext node, int indent) {
        if (node == null)
            return;
        String pad = "  ".repeat(Math.max(0, indent));
        System.out.printf("%s- %s : '%s'%n", pad, node.getClass().getSimpleName(), node.getText().replace("\n", "\\n"));
        for (int i = 0; i < node.getChildCount(); i++) {
            var child = node.getChild(i);
            if (child instanceof ParserRuleContext prc)
                dumpExprTree(prc, indent + 1);
            else if (child instanceof TerminalNode tn) {
                System.out.printf("%s  * Terminal: %s -> '%s'%n", pad, tn.getSymbol().getText(), tn.getText());
            } else {
                System.out.printf("%s  * Other child: %s -> '%s'%n", pad, child.getClass().getSimpleName(),
                        child.toString());
            }
        }
    }
}
