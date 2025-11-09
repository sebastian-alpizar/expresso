
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
 */
public class TyperVisitor extends ExprBaseVisitor<Void> {

    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    private final List<String> typings = new ArrayList<>();
    private final Set<String> errors = new LinkedHashSet<>();
    private final String fileName;

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

        // Inferencia de lambdas
        if (ctx.expression() != null) {
            ExprParser.LambdaExpressionContext lambda = ctx.expression().lambdaExpression();
            if (lambda == null)
                lambda = findNestedLambda(ctx.expression());
            if (lambda != null) {
                String inferred = tryExtractLambdaDeclaredType(lambda, varName, ctx.expression());
                if (inferred != null && !inferred.equals("~"))
                    typeStr = inferred;
            } else {
                ExprParser.TypeContext found = findReturnTypeInExpression(ctx.expression());
                if (found != null) {
                    String foundTxt = renderType(found);
                    if (ctx.type() == null && !foundTxt.equals("~"))
                        typeStr = foundTxt;
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

        // Inferir retorno si hay cast explícito
        if (ctx.type() == null && ctx.expression() != null) {
            ExprParser.TypeContext inferred = findReturnTypeInExpression(ctx.expression());
            if (inferred != null) {
                String ret = renderType(inferred);
                if (paramTypes.size() == 1)
                    typeStr = paramTypes.get(0) + " -> " + ret;
                else if (!paramTypes.isEmpty())
                    typeStr = "(" + String.join(", ", paramTypes) + " -> " + ret + ")";
                else
                    typeStr = ret;
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
        List<String> ctorLines = new ArrayList<>();

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

            ctorLines.add(ctor + ": " + ctorArg + " ^ " + typeName);
        }

        // ORDEN CORRECTO: primero el tipo, luego los constructores
        addTypingLine(typeName + ": " + String.join("|", ctorNames));
        for (String line : ctorLines) {
            addTypingLine(line);
        }

        return null;
    }

    // -------------------------------
    // Otros visit
    // -------------------------------
    @Override
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

    @Override
    public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            // Ignorar el wildcard underscore
            // También ignorar si estamos dentro de un pattern (pero esto ya se maneja en
            // visitMatchRule)
            if (!name.equals("_") && !isDefined(name)) {
                reportUndefined("Variable", name);
            }
        }
        if (ctx.expression() != null)
            visit(ctx.expression());
        return null;
    }

    @Override
    public Void visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        // Visitar la expresión a evaluar
        visit(ctx.expression());

        // Visitar las reglas del match
        for (var r : ctx.matchRule())
            visit(r);
        return null;
    }

    @Override
    public Void visitMatchRule(ExprParser.MatchRuleContext ctx) {
        // NO visitar el pattern para validación de símbolos
        // Los patterns crean bindings locales, no usan símbolos existentes
        // SOLO validar constructores en los patterns
        if (ctx.pattern() != null) {
            visitPatternForConstructors(ctx.pattern());
        }

        // Visitar las expresiones (condición when y resultado)
        if (ctx.expression() != null) {
            for (var e : ctx.expression()) {
                visit(e);
            }
        }
        return null;
    }

    private void visitPatternForConstructors(ExprParser.PatternContext pattern) {
        if (pattern.dataPattern() != null) {
            ExprParser.DataPatternContext dp = pattern.dataPattern();
            String id = dp.ID().getText();

            // SOLO validar si empieza con mayúscula (es un constructor)
            // Los identificadores con minúscula son variables de binding
            if (Character.isUpperCase(id.charAt(0))) {
                if (!isDefined(id)) {
                    reportUndefined("Constructor (en pattern)", id);
                }
            }
            // Si empieza con minúscula o es _, es una variable de binding, ignorar

            // Procesar subpatterns recursivamente
            if (dp.pattern() != null) {
                for (ExprParser.PatternContext subPattern : dp.pattern()) {
                    visitPatternForConstructors(subPattern);
                }
            }
        }
        // Ignorar nativePattern: son literales, no necesitan validación
    }

    @Override
    public Void visitDataPattern(ExprParser.DataPatternContext ctx) {
        // Este método ya no se usa directamente, usamos visitPatternForConstructors
        // Pero lo dejamos por si acaso se llama desde otro lugar
        String ctor = ctx.ID().getText();

        if (!isDefined(ctor)) {
            reportUndefined("Constructor (en pattern)", ctor);
        }

        // NO visitar subpatterns para validación general
        return null;
    }

    // -------------------------------
    // Render y validaciones
    // -------------------------------
    private String renderType(ExprParser.TypeContext t) {
        if (t == null)
            return "~";

        String text = t.getText();

        // Para tipos simples, devolver directamente
        if (BUILTIN_TYPES.contains(text))
            return text;

        // Para tipos función, preservar estructura
        if (text.contains("->")) {
            // Normalizar espacios alrededor de ->
            return text.replaceAll("\\s+", "").replace("->", " -> ");
        }

        return text;
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
                if (!txt.isEmpty() && Character.isLetter(txt.charAt(0)) &&
                        !BUILTIN_TYPES.contains(txt) && !isDefined(txt))
                    reportUndefined("Tipo", txt);
            } else if (c instanceof ExprParser.TypeContext sub)
                checkTypeDefinedRecursively(sub);
        }
    }

    // -------------------------------
    // Lambda utils
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

    private String tryExtractLambdaDeclaredType(ExprParser.LambdaExpressionContext lambda,
            String contextName,
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
        ExprParser.TypeContext cast = findReturnTypeInExpression(lambda.expression());
        if (cast != null) {
            returnType = renderType(cast);
        } else if (fullExpr != null) {
            ExprParser.TypeContext castOuter = findReturnTypeInExpression((ExprParser.ExpressionContext) fullExpr);
            if (castOuter != null) {
                returnType = renderType(castOuter);
            }
        }

        // Heurísticas si no hay cast
        if ("~".equals(returnType)) {
            String body = lambda.expression().getText();
            if (body.matches(".*(==|!=|&&|\\|\\||<|>|<=|>=).*"))
                returnType = "boolean";
            else if (body.matches(".*\".*\".*"))
                returnType = "string";
            else if (body.matches(".*\\d+\\.\\d+.*"))
                returnType = "double";
        }

        // Formar resultado
        if (paramTypes.isEmpty())
            return "~";
        else if (paramTypes.size() == 1)
            return paramTypes.get(0) + " -> " + returnType;
        else
            return "(" + String.join(", ", paramTypes) + " -> " + returnType + ")";
    }

    private ExprParser.TypeContext findReturnTypeInExpression(ExprParser.ExpressionContext expr) {
        if (expr == null)
            return null;

        if (expr.castExpression() != null && expr.castExpression().type() != null) {
            return expr.castExpression().type();
        }

        for (int i = 0; i < expr.getChildCount(); i++) {
            var child = expr.getChild(i);
            if (child instanceof ExprParser.CastExpressionContext cast && cast.type() != null) {
                return cast.type();
            }
            if (child instanceof ParserRuleContext prc) {
                var found = findReturnTypeInExpressionRecursive(prc);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

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