// TyperVisitor.java
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * TyperVisitor - recolecta tipos top-level y hace comprobaciones mínimas:
 * - detecta redefiniciones (variables, funciones, tipos, constructores)
 * - detecta referencias a identificadores no definidos (incluyendo tipos)
 * - genera <filename>.typings con las asociaciones
 *
 * Notas:
 * - Solo recolecta símbolos del scope top-level.
 * - Si no hay tipo explícito, se usa "~".
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
        for (String b : BUILTIN_TYPES) symbolTable.put(b, b);
    }

    // -------------------------------
    // Utilitarios generales
    // -------------------------------
    public boolean hasErrors() { return !errors.isEmpty(); }

    public void printErrors() { errors.forEach(e -> System.err.println("[Typer ERROR] " + e)); }

    public void generateTypingsFile(String outputDir) throws IOException {
        String outName = fileName.endsWith(".expresso")
                ? fileName.substring(0, fileName.length() - ".expresso".length())
                : fileName;
        Path outPath = Paths.get(outputDir, outName + ".typings");
        Files.createDirectories(outPath.getParent() == null ? Paths.get(".") : outPath.getParent());
        Files.writeString(outPath, String.join("\n", typings) + "\n");
        System.out.println("[Typer] .typings generado en: " + outPath.toAbsolutePath());
    }

    private void addTypingLine(String line) { typings.add(line); }
    private void addSymbol(String name, String desc) { symbolTable.put(name, desc); }
    private boolean isDefined(String name) { return symbolTable.containsKey(name); }
    private void reportRedefinition(String kind, String name) { errors.add("Redefinición de " + kind + ": " + name); }
    private void reportUndefined(String kind, String name) { errors.add(kind + " no definido: " + name); }

    // -------------------------------
    // Programa principal
    // -------------------------------
    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        for (ExprParser.StatementContext s : ctx.statement()) visit(s);
        return null;
    }

    // -------------------------------
    // Declaración let
    // -------------------------------
    @Override
    public Void visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        if (isDefined(varName)) { reportRedefinition("identificador", varName); return null; }

        String typeStr = "~";
        if (ctx.type() != null) {
            typeStr = renderType(ctx.type());
            checkTypeDefinedRecursively(ctx.type());
        }

        // 🔍 Inferencia de lambdas (directas o anidadas)
        if (ctx.expression() != null) {
            ExprParser.LambdaExpressionContext lambda = ctx.expression().lambdaExpression();
            if (lambda == null) lambda = findNestedLambda(ctx.expression());
            if (lambda != null) {
                String inferred = tryExtractLambdaDeclaredType(lambda);
                if (inferred != null && !inferred.equals("~")) typeStr = inferred;
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
        if (isDefined(funName)) { reportRedefinition("función", funName); return null; }

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
            if (paramTypes.isEmpty()) typeStr = ret;
            else if (paramTypes.size() == 1) typeStr = paramTypes.get(0) + " -> " + ret;
            else typeStr = "(" + String.join(", ", paramTypes) + " -> " + ret + ")";
        } else if (!paramTypes.isEmpty()) {
            typeStr = "(" + String.join(", ", paramTypes) + " -> ~)";
        }

        // 🔍 Inferir retorno si hay cast explícito
        if (ctx.type() == null && ctx.expression() != null) {
            ExprParser.TypeContext inferred = findReturnTypeInExpression(ctx.expression());
            if (inferred != null) {
                String ret = renderType(inferred);
                if (!paramTypes.isEmpty()) typeStr = paramTypes.get(0) + " -> " + ret;
                else typeStr = ret;
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
        if (isDefined(typeName)) { reportRedefinition("tipo", typeName); return null; }

        addSymbol(typeName, "type");
        List<String> ctorNames = new ArrayList<>();

        for (ExprParser.ConstructorContext cc : ctx.constructorList().constructor()) {
            String ctor = cc.ID().getText();
            if (isDefined(ctor)) { reportRedefinition("constructor", ctor); continue; }
            ctorNames.add(ctor);
            addSymbol(ctor, "constructor of " + typeName);

            String ctorArg = "void";
            if (cc.arguments() != null) {
                List<String> args = new ArrayList<>();
                for (ExprParser.ArgumentContext a : cc.arguments().argument()) {
                    String r = renderFlatType(a.flatType());
                    args.add(r); checkFlatTypeDefined(a.flatType());
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
    @Override public Void visitPrintStatement(ExprParser.PrintStatementContext ctx) { if (ctx.expression()!=null) visit(ctx.expression()); return null; }

    @Override public Void visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        ExprParser.PrimaryTargetContext target = ctx.primaryTarget();
        if (target.ID()!=null && !isDefined(target.ID().getText()))
            reportUndefined("Función", target.ID().getText());
        if (ctx.callArgs()!=null)
            for (var c:ctx.callArgs())
                if (c.expression()!=null)
                    for (var e:c.expression()) visit(e);
        return null;
    }

    @Override public Void visitConstructorExpr(ExprParser.ConstructorExprContext ctx) {
        if (!isDefined(ctx.ID().getText())) reportUndefined("Constructor", ctx.ID().getText());
        if (ctx.expressionList()!=null) visit(ctx.expressionList());
        return null;
    }

    @Override public Void visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.ID()!=null && !isDefined(ctx.ID().getText()))
            reportUndefined("Variable", ctx.ID().getText());
        if (ctx.expression()!=null) visit(ctx.expression());
        return null;
    }

    @Override public Void visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        visit(ctx.expression());
        for (var r:ctx.matchRule()) visit(r);
        return null;
    }

    @Override public Void visitMatchRule(ExprParser.MatchRuleContext ctx) {
        if (ctx.pattern()!=null) visit(ctx.pattern());
        if (ctx.expression()!=null) for (var e:ctx.expression()) visit(e);
        return null;
    }

    @Override public Void visitDataPattern(ExprParser.DataPatternContext ctx) {
        if (!isDefined(ctx.ID().getText()))
            reportUndefined("Constructor (en pattern)", ctx.ID().getText());
        if (ctx.pattern()!=null) for (var p:ctx.pattern()) visit(p);
        return null;
    }

    // -------------------------------
    // Render y validaciones
    // -------------------------------
    private String renderType(ExprParser.TypeContext t) {
        if (t==null) return "~";
        String raw=t.getText().trim().replaceAll("\\s+","");
        return raw.replaceAll("[()]", "");
    }

    private String renderFlatType(ExprParser.FlatTypeContext ft) {
        if (ft==null) return "~";
        return ft.getText().trim();
    }

    private void checkFlatTypeDefined(ExprParser.FlatTypeContext ft) {
        if (ft==null) return;
        if (ft.ID()!=null) {
            String n=ft.ID().getText();
            if (!BUILTIN_TYPES.contains(n)&&!isDefined(n)) reportUndefined("Tipo",n);
        }
    }

    private void checkTypeDefinedRecursively(ExprParser.TypeContext t) {
        if (t==null) return;
        for (int i=0;i<t.getChildCount();i++) {
            var c=t.getChild(i);
            if (c instanceof TerminalNode tn) {
                String txt=tn.getText();
                if (Character.isLetter(txt.charAt(0)) && !BUILTIN_TYPES.contains(txt) && !isDefined(txt))
                    reportUndefined("Tipo",txt);
            } else if (c instanceof ExprParser.TypeContext sub) {
                checkTypeDefinedRecursively(sub);
            }
        }
    }

    // -------------------------------
    // Lambda utils
    // -------------------------------
    private ExprParser.LambdaExpressionContext findNestedLambda(ExprParser.ExpressionContext expr) {
        if (expr==null) return null;
        if (expr.lambdaExpression()!=null) return expr.lambdaExpression();
        for (int i=0;i<expr.getChildCount();i++) {
            var c=expr.getChild(i);
            if (c instanceof ExprParser.ExpressionContext sub) {
                var found=findNestedLambda(sub);
                if (found!=null) return found;
            }
        }
        return null;
    }

    private String tryExtractLambdaDeclaredType(ExprParser.LambdaExpressionContext lambda) {
        if (lambda==null) return null;
        ExprParser.LambdaParamsContext lp=lambda.lambdaParams();
        if (lp==null) return null;

        List<String> paramTypes=new ArrayList<>();
        if (lp.typedLambdaParams()!=null)
            for (var t:lp.typedLambdaParams().typedParam()) {
                paramTypes.add(renderType(t.type()));
                checkTypeDefinedRecursively(t.type());
            }

        String returnType="~";
        ExprParser.TypeContext cast=findReturnTypeInExpression(lambda.expression());
        if (cast!=null) {
            returnType=renderType(cast);
        } else {
            // 🔍 Inferencia heurística básica
            String body=lambda.expression().getText();
            if (body.matches(".*(==|!=|&&|\\|\\||<|>|<=|>=).*")) returnType="boolean";
            else if (body.matches(".*\".*\".*")||body.contains("String.valueOf")) returnType="string";
            else if (body.matches(".*\\d+\\.\\d+.*")) returnType="double";
            else if (body.matches(".*\\d+.*")) returnType="int";
            else if (body.contains("^")||body.contains("new ")) returnType="any";
        }

        if (paramTypes.isEmpty()) return null;
        if (paramTypes.size()==1) return paramTypes.get(0)+" -> "+returnType;
        return "(" + String.join(", ",paramTypes)+" -> "+returnType+")";
    }

    private ExprParser.TypeContext findReturnTypeInExpression(ExprParser.ExpressionContext expr) {
        if (expr==null) return null;
        if (expr.castExpression()!=null && expr.castExpression().type()!=null)
            return expr.castExpression().type();
        for (int i=0;i<expr.getChildCount();i++) {
            var c=expr.getChild(i);
            if (c instanceof ExprParser.CastExpressionContext cast && cast.type()!=null)
                return cast.type();
            if (c instanceof ExprParser.ExpressionContext sub) {
                var d=findReturnTypeInExpression(sub);
                if (d!=null) return d;
            }
        }
        return null;
    }
}
