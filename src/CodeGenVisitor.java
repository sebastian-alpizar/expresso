import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    private StringBuilder mainBody = new StringBuilder();
    private Set<String> declaredVariables = new LinkedHashSet<>();
    private Map<String, String> variableTypes = new HashMap<>();
    private Map<String, String> lambdaTypes = new HashMap<>();

    private static final Set<String> RESERVED_WORDS = Set.of("let", "print");

    @Override
    public String visitProgram(ExprParser.ProgramContext ctx) {
        for (var st : ctx.statement()) {
            visit(st);
        }
        return "";
    }

    @Override
    public String visitLetStatement(ExprParser.LetStatementContext ctx) {
        String id = validateIdentifier(ctx.ID().getText());
        String expr = visit(ctx.expr());
        
        declaredVariables.add(id);
        
        // Determinar el tipo basado en la expresión
        String type = determineType(ctx.expr(), expr);
        variableTypes.put(id, type);
        
        mainBody.append(type).append(" ").append(id).append(" = ").append(expr).append(";\n");
        return "";
    }

    @Override
    public String visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        String expr = visit(ctx.expr());
        mainBody.append("print(").append(expr).append(");\n");
        return "";
    }

    @Override
    public String visitExprStatement(ExprParser.ExprStatementContext ctx) {
        String expr = visit(ctx.expr());
        mainBody.append(expr).append(";\n");
        return "";
    }

    // --- Expresiones ---

    @Override
    public String visitTernaryOp(ExprParser.TernaryOpContext ctx) {
        if (ctx.QUEST() != null && ctx.COLON() != null) {
            String condition = visit(ctx.lambdaExpr());
            String trueExpr = visit(ctx.ternaryExpr(0));
            String falseExpr = visit(ctx.ternaryExpr(1));
            
            // Determinar el tipo del resultado del ternario
            String trueType = determineExpressionType(ctx.ternaryExpr(0));
            String falseType = determineExpressionType(ctx.ternaryExpr(1));
            
            // Si ambos lados son strings, el resultado es string
            if ("String".equals(trueType) && "String".equals(falseType)) {
                return "(" + condition + " ? " + trueExpr + " : " + falseExpr + ")";
            }
            // Si al menos uno es string, forzar ambos a string
            else if ("String".equals(trueType) || "String".equals(falseType)) {
                return "(" + condition + " ? String.valueOf(" + trueExpr + ") : String.valueOf(" + falseExpr + "))";
            }
            // Ambos son números
            else {
                return "(" + condition + " ? " + trueExpr + " : " + falseExpr + ")";
            }
        } else {
            return visit(ctx.lambdaExpr());
        }
    }

    @Override
    public String visitLambdaRightAssoc(ExprParser.LambdaRightAssocContext ctx) {
        String left = visit(ctx.lambdaExpr(0));
        String right = visit(ctx.lambdaExpr(1));
        
        if (right.contains("->")) {
            return left + " -> (" + right + ")";
        } else {
            return left + " -> " + right;
        }
    }

    @Override
    public String visitLambdaDef(ExprParser.LambdaDefContext ctx) {
        List<String> params = new ArrayList<>();
        if (ctx.paramList() != null) {
            for (var param : ctx.paramList().ID()) {
                params.add(param.getText());
            }
        }
        
        String body = visit(ctx.expr());
        
        switch (params.size()) {
            case 0:
                return "() -> " + body;
            case 1:
                return params.get(0) + " -> " + body;
            case 2:
                return "(" + params.get(0) + ", " + params.get(1) + ") -> " + body;
            default:
                return "(" + String.join(", ", params) + ") -> " + body;
        }
    }

    @Override
    public String visitOrOp(ExprParser.OrOpContext ctx) {
        String left = visit(ctx.orExpr());
        String right = visit(ctx.andExpr());
        return "(" + left + " != 0 || " + right + " != 0)";
    }

    @Override
    public String visitAndOp(ExprParser.AndOpContext ctx) {
        String left = visit(ctx.andExpr());
        String right = visit(ctx.eqExpr());
        return "(" + left + " != 0 && " + right + " != 0)";
    }

    @Override
    public String visitEqualOp(ExprParser.EqualOpContext ctx) {
        String left = visit(ctx.eqExpr());
        String right = visit(ctx.relExpr());
        String op = ctx.getChild(1).getText();
        return "(" + left + " " + op + " " + right + ")";
    }

    @Override
    public String visitCompareOp(ExprParser.CompareOpContext ctx) {
        String left = visit(ctx.relExpr());
        String right = visit(ctx.addExpr());
        String op = ctx.getChild(1).getText();
        return "(" + left + " " + op + " " + right + ")";
    }

    @Override
    public String visitAddSubOp(ExprParser.AddSubOpContext ctx) {
        String left = visit(ctx.addExpr());
        String right = visit(ctx.mulExpr());
        String op = ctx.getChild(1).getText();
        return "(" + left + " " + op + " " + right + ")";
    }

    @Override
    public String visitMulDivModOp(ExprParser.MulDivModOpContext ctx) {
        String left = visit(ctx.mulExpr());
        String right = visit(ctx.powExpr());
        String op = ctx.getChild(1).getText();
        return "(" + left + " " + op + " " + right + ")";
    }

    @Override
    public String visitPowOp(ExprParser.PowOpContext ctx) {
        String left = visit(ctx.unaryExpr());
        String right = visit(ctx.powExpr());
        return "pow(" + left + ", " + right + ")";
    }

    @Override
    public String visitUnaryOp(ExprParser.UnaryOpContext ctx) {
        String op = ctx.getChild(0).getText();
        String expr = visit(ctx.unaryExpr());
        if ("!".equals(op)) {
            return "(" + expr + " == 0 ? 1 : 0)";
        }
        return "(" + op + expr + ")";
    }

    @Override
    public String visitIntLiteral(ExprParser.IntLiteralContext ctx) {
        return ctx.INT().getText();
    }

    @Override
    public String visitVarExpr(ExprParser.VarExprContext ctx) {
        return ctx.ID().getText();
    }

    @Override
    public String visitParenExpr(ExprParser.ParenExprContext ctx) {
        return "(" + visit(ctx.expr()) + ")";
    }

    @Override
    public String visitCallExpr(ExprParser.CallExprContext ctx) {
        String id = ctx.ID().getText();
        
        List<String> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (var e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }
        
        // Si es una variable lambda conocida
        if (lambdaTypes.containsKey(id)) {
            String lambdaType = lambdaTypes.get(id);
            
            if (lambdaType.contains("UnaryOperator") && args.size() == 1) {
                return id + ".apply(" + args.get(0) + ")";
            } else if (lambdaType.contains("BinaryOperator") && args.size() == 2) {
                return id + ".apply(" + args.get(0) + ", " + args.get(1) + ")";
            } else if (lambdaType.contains("Supplier") && args.isEmpty()) {
                return id + ".get()";
            } else if (lambdaType.contains("Function")) {
                // Aplicación múltiple para funciones curried
                StringBuilder result = new StringBuilder(id);
                for (String arg : args) {
                    result.append(".apply(").append(arg).append(")");
                }
                return result.toString();
            }
        }
        
        // Llamada normal a función
        if (args.isEmpty()) {
            return id + "()";
        } else {
            return id + "(" + String.join(", ", args) + ")";
        }
    }

    @Override
    public String visitStringLiteral(ExprParser.StringLiteralContext ctx) {
        String text = ctx.STRING().getText();
        String content = text.substring(1, text.length() - 1);
        content = content.replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
        return "\"" + content + "\"";
    }

    @Override
    public String visitLambdaPrimary(ExprParser.LambdaPrimaryContext ctx) {
        return visit(ctx.lambdaDefinition());
    }

    // Métodos de conveniencia
    @Override public String visitToOrExpr(ExprParser.ToOrExprContext ctx) { return visit(ctx.orExpr()); }
    @Override public String visitToAndExpr(ExprParser.ToAndExprContext ctx) { return visit(ctx.andExpr()); }
    @Override public String visitToEqExpr(ExprParser.ToEqExprContext ctx) { return visit(ctx.eqExpr()); }
    @Override public String visitToRelExpr(ExprParser.ToRelExprContext ctx) { return visit(ctx.relExpr()); }
    @Override public String visitToAddExpr(ExprParser.ToAddExprContext ctx) { return visit(ctx.addExpr()); }
    @Override public String visitToMulExpr(ExprParser.ToMulExprContext ctx) { return visit(ctx.mulExpr()); }
    @Override public String visitToPowExpr(ExprParser.ToPowExprContext ctx) { return visit(ctx.powExpr()); }
    @Override public String visitToUnaryExpr(ExprParser.ToUnaryExprContext ctx) { return visit(ctx.unaryExpr()); }
    @Override public String visitToPrimaryExpr(ExprParser.ToPrimaryExprContext ctx) { return visit(ctx.primary()); }

    // Métodos auxiliares
    private String validateIdentifier(String id) {
        if (RESERVED_WORDS.contains(id)) {
            throw new RuntimeException("Identificador '" + id + "' es una palabra reservada");
        }
        return id;
    }

    private String determineType(ExprParser.ExprContext exprContext, String generatedExpr) {
        // Primero verificar si es string literal
        if (hasStringInTernary(exprContext)) {
            return "String";
        }
        else if (exprContext.getChild(0) instanceof ExprParser.StringLiteralContext) {
            return "String";
        }
        else if (generatedExpr.contains("->")) {
            // Es una lambda
            String lambdaType = determineLambdaType(generatedExpr);
            String varName = ((ExprParser.LetStatementContext) exprContext.getParent()).ID().getText();
            lambdaTypes.put(varName, lambdaType);
            return lambdaType;
        }
        else if (exprContext.getChild(0) instanceof ExprParser.CallExprContext) {
            ExprParser.CallExprContext callCtx = (ExprParser.CallExprContext) exprContext.getChild(0);
            String id = callCtx.ID().getText();
            if (lambdaTypes.containsKey(id)) {
                String lambdaType = lambdaTypes.get(id);
                if (lambdaType.contains("Function<Integer, UnaryOperator<Integer>>")) {
                    return "UnaryOperator<Integer>";
                } else if (lambdaType.contains("UnaryOperator")) {
                    return "int";
                }
            }
        }
        // Por defecto, asumir int
        return "int";
    }

    private String determineExpressionType(ExprParser.TernaryExprContext ctx) {
    if (ctx instanceof ExprParser.TernaryOpContext ternaryCtx) {
        String t1 = determineExpressionType(ternaryCtx.ternaryExpr(0));
        String t2 = determineExpressionType(ternaryCtx.ternaryExpr(1));
        if ("String".equals(t1) || "String".equals(t2)) return "String";
        return "Number";
    }
    return "Unknown";
}

    private boolean hasStringInTernary(ParserRuleContext exprContext) {
        if (exprContext instanceof ExprParser.TernaryOpContext) {
            ExprParser.TernaryOpContext ternary = (ExprParser.TernaryOpContext) exprContext;
            return hasStringInExpression(ternary.ternaryExpr(0)) || 
                hasStringInExpression(ternary.ternaryExpr(1));
        }
        return false;
    }

    private boolean hasStringInExpression(ParserRuleContext exprContext) {
        if (exprContext instanceof ExprParser.StringLiteralContext) {
            return true;
        } 
        else if (exprContext instanceof ExprParser.TernaryOpContext) {
            ExprParser.TernaryOpContext ternary = (ExprParser.TernaryOpContext) exprContext;
            return hasStringInExpression(ternary.ternaryExpr(0)) || 
                hasStringInExpression(ternary.ternaryExpr(1));
        }
        else if (exprContext instanceof ExprParser.VarExprContext) {
            ExprParser.VarExprContext varCtx = (ExprParser.VarExprContext) exprContext;
            String varName = varCtx.ID().getText();
            return "String".equals(variableTypes.get(varName));
        }
        return false;
    }


    private String determineLambdaType(String expr) {
        if (expr.contains("->")) {
            int arrowIndex = expr.indexOf("->");
            String paramsPart = expr.substring(0, arrowIndex).trim();
            
            // Contar parámetros
            int paramCount = 0;
            if (paramsPart.startsWith("(")) {
                if (paramsPart.contains(",")) {
                    paramCount = paramsPart.split(",").length;
                } else if (paramsPart.equals("()")) {
                    paramCount = 0;
                } else {
                    paramCount = 1;
                }
            } else {
                paramCount = 1;
            }
            
            // Verificar si es lambda anidada
            String bodyPart = expr.substring(arrowIndex + 2).trim();
            boolean isNestedLambda = bodyPart.contains("->");
            
            if (isNestedLambda) {
                if (paramCount == 1) {
                    return "Function<Integer, UnaryOperator<Integer>>";
                }
            } else {
                // Determinar tipo de retorno del cuerpo
                boolean returnsBoolean = bodyPart.contains(">") || bodyPart.contains("==") || bodyPart.contains("!=");
                boolean returnsString = bodyPart.contains("\"");
                
                if (paramCount == 0) {
                    if (returnsString) return "Supplier<String>";
                    return returnsBoolean ? "Supplier<Boolean>" : "Supplier<Integer>";
                } else if (paramCount == 1) {
                    if (returnsString) return "Function<Integer, String>";
                    return returnsBoolean ? "Function<Integer, Boolean>" : "UnaryOperator<Integer>";
                } else if (paramCount == 2) {
                    if (returnsString) return "BiFunction<Integer, Integer, String>";
                    return returnsBoolean ? "BiFunction<Integer, Integer, Boolean>" : "BinaryOperator<Integer>";
                }
            }
        }
        return "UnaryOperator<Integer>";
    }

    public String getMainBody() {
        return mainBody.toString();
    }
}