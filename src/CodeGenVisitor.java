import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    private StringBuilder mainBody = new StringBuilder();
    private Set<String> declaredVariables = new HashSet<>();
    private Map<String, String> variableTypes = new HashMap<>();
    private int lambdaCounter = 0;

    private String debug(String label, ParserRuleContext ctx, String result) {
        String text = ctx.getText().replaceAll("\\s+", " ");
        System.out.println("[DEBUG] " + label + " -> " + text);
        System.out.println("         Result: " + result);
        System.out.println("-------------------------------------------");
        return result;
    }

    public void printTree(ParserRuleContext ctx, int indent) {
        String spaces = "  ".repeat(indent);
        System.out.println(spaces + ctx.getClass().getSimpleName() + ": " + ctx.getText());
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof ParserRuleContext) {
                printTree((ParserRuleContext) ctx.getChild(i), indent + 1);
            } else {
                System.out.println(spaces + "  " + ctx.getChild(i).getClass().getSimpleName() + ": " + ctx.getChild(i).getText());
            }
        }
    }
    
    @Override
    public String visitProgram(ExprParser.ProgramContext ctx) {
        mainBody.append("public static void main(String[] args) {\n");
        
        for (ExprParser.StatementContext stmt : ctx.statement()) {
            String result = visit(stmt);
            if (result != null && !result.trim().isEmpty()) {
                mainBody.append("    ").append(result).append("\n");
            }
        }
        
        mainBody.append("}\n");
        return null;
    }
    
    @Override
    public String visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        String value = visit(ctx.expression());
        String type = inferType(ctx.expression());
        variableTypes.put(varName, type);

        String code;
        if (!declaredVariables.contains(varName)) {
            declaredVariables.add(varName);
            code = String.format("%s %s = %s;", type, varName, value);
        } else {
            code = String.format("%s = %s;", varName, value);
        }

        return debug("LetStatement(" + varName + ")", ctx, code);
    }
    
    private String inferType(ExprParser.ExpressionContext expr) {
        // Si es una expresión lambda, determinar el tipo de función
        if (expr.lambdaExpression() != null) {
            ExprParser.LambdaExpressionContext lambda = expr.lambdaExpression();
            int paramCount = getLambdaParamCount(lambda.lambdaParams());
            
            switch (paramCount) {
                case 1: return "UnaryOperator<Integer>";
                case 2: return "BinaryOperator<Integer>";
                default: return "Function<Integer, Integer>";
            }
        } else {
            return "int";
        }
    }
    
    private int getLambdaParamCount(ExprParser.LambdaParamsContext params) {
        if (params == null) return 0;
        return params.ID().size();
    }
    
    @Override
    public String visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        String value = visit(ctx.expression());
        return String.format("System.out.println(%s);", value);
    }
    
    @Override
    public String visitLambdaParams(ExprParser.LambdaParamsContext ctx) {
        // Solo recolectar los parámetros, no generar código aquí
        return null;
    }
    
    @Override
    public String visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        List<String> params = new ArrayList<>();
        if (ctx.lambdaParams().ID() != null) {
            for (var id : ctx.lambdaParams().ID()) {
                params.add(getSafeParameterName(id.getText()));
            }
        }

        // PROCESAR DIRECTAMENTE LA EXPRESIÓN DEL CUERPO
        String body = visit(ctx.expression());

        // Si el cuerpo es solo un identificador que representa una función lambda,
        // necesitamos agregar .apply() con los parámetros apropiados
        body = processLambdaBodyIfNeeded(body, params);

        String code;
        if (params.size() == 1) {
            code = params.get(0) + " -> " + body;
        } else if (params.size() == 2) {
            code = "(" + params.get(0) + ", " + params.get(1) + ") -> " + body;
        } else {
            code = "(" + String.join(", ", params) + ") -> " + body;
        }

        return debug("LambdaExpression(" + params + ")", ctx, code);
    }

    private String processLambdaBodyIfNeeded(String body, List<String> params) {
        // Si el cuerpo es solo una variable lambda declarada, agregar .apply()
        if (declaredVariables.contains(body) && !params.isEmpty()) {
            String type = variableTypes.get(body);
            if (type != null) {
                switch (type) {
                    case "UnaryOperator<Integer>":
                        if (params.size() == 1) {
                            return body + ".apply(" + params.get(0) + ")";
                        }
                        break;
                    case "BinaryOperator<Integer>":
                        if (params.size() == 2) {
                            return body + ".apply(" + params.get(0) + ", " + params.get(1) + ")";
                        }
                        break;
                }
            }
        }
        return body;
    }

    private String getSafeParameterName(String originalName) {
        // Si el parámetro ya está declarado como variable, generar un nombre único
        if (declaredVariables.contains(originalName)) {
            lambdaCounter++;
            return originalName + "_" + lambdaCounter;
        }
        return originalName;
    }
    
    @Override
    public String visitAdditiveExpression(ExprParser.AdditiveExpressionContext ctx) {
        List<ExprParser.MultiplicativeExpressionContext> expressions = ctx.multiplicativeExpression();
        
        if (expressions.size() == 1) {
            return visit(expressions.get(0));
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(visit(expressions.get(0)));
        
        for (int i = 1; i < expressions.size(); i++) {
            String operator = ctx.getChild(i * 2 - 1).getText();
            String right = visit(expressions.get(i));
            sb.append(" ").append(operator).append(" ").append(right);
        }
        
        return "(" + sb.toString() + ")";
    }
    
    @Override
    public String visitMultiplicativeExpression(ExprParser.MultiplicativeExpressionContext ctx) {
        List<ExprParser.PowerExpressionContext> expressions = ctx.powerExpression();
        
        if (expressions.size() == 1) {
            return visit(expressions.get(0));
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(visit(expressions.get(0)));
        
        for (int i = 1; i < expressions.size(); i++) {
            String operator = ctx.getChild(i * 2 - 1).getText();
            String right = visit(expressions.get(i));
            sb.append(" ").append(operator).append(" ").append(right);
        }
        
        return "(" + sb.toString() + ")";
    }
    
    @Override
    public String visitPowerExpression(ExprParser.PowerExpressionContext ctx) {
        List<ExprParser.UnaryExpressionContext> expressions = ctx.unaryExpression();
        
        if (expressions.size() == 1) {
            return visit(expressions.get(0));
        }
        
        // Usar Math.pow para exponenciación
        String result = visit(expressions.get(expressions.size() - 1));
        for (int i = expressions.size() - 2; i >= 0; i--) {
            String base = visit(expressions.get(i));
            result = String.format("(int)Math.pow(%s, %s)", base, result);
        }
        
        return result;
    }
    
    @Override
    public String visitUnaryExpression(ExprParser.UnaryExpressionContext ctx) {
        if (ctx.primaryExpression() != null) {
            return visit(ctx.primaryExpression());
        }
        
        String operator = ctx.getChild(0).getText();
        String expression = visit(ctx.unaryExpression());
        return "(" + operator + expression + ")";
    }
    
    @Override
    public String visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        if (ctx.INTEGER() != null) {
            return ctx.INTEGER().getText();
        }
        if (ctx.ID() != null) {
            return ctx.ID().getText();
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return "";
    }
    
    @Override
    public String visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        String functionName = ctx.ID().getText();
        List<String> args = new ArrayList<>();
        for (ExprParser.ExpressionContext expr : ctx.expression()) {
            args.add(visit(expr));
        }

        String argsString = String.join(", ", args);

        // **CAMBIAR ESTA LÓGICA** - Siempre tratar como función lambda si está declarada
        if (declaredVariables.contains(functionName)) {
            String type = variableTypes.get(functionName);
            
            // **AGREGAR ESTA VERIFICACIÓN ADICIONAL**
            if (type != null && (type.contains("Operator") || type.contains("Function"))) {
                // Es una función lambda, usar .apply()
                if (args.size() == 1) {
                    return String.format("%s.apply(%s)", functionName, argsString);
                } else if (args.size() == 2) {
                    return String.format("%s.apply(%s, %s)", functionName, args.get(0), args.get(1));
                } else {
                    return String.format("%s.apply(%s)", functionName, argsString);
                }
            }
        }
        
        // **AGREGAR DEBUG PARA IDENTIFICAR EL PROBLEMA**
        return functionName + "(" + argsString + ")";
    }

    @Override
    public String visitExpression(ExprParser.ExpressionContext ctx) {
        if (ctx.lambdaExpression() != null) {
            return visit(ctx.lambdaExpression());
        } else if (ctx.ternaryExpression() != null) {
            return visit(ctx.ternaryExpression());
        } else if (ctx.additiveExpression() != null) {
            return visit(ctx.additiveExpression());
        }
        return visitChildren(ctx);
    }

    @Override
    public String visitTernaryExpression(ExprParser.TernaryExpressionContext ctx) {        
        String condition = visit(ctx.additiveExpression());
        String trueExpr = visit(ctx.expression(0));
        String falseExpr = visit(ctx.expression(1));

        String code = "(" + condition + " != 0 ? " + trueExpr + " : " + falseExpr + ")";
        return debug("TernaryExpression", ctx, code);
    }
    
    public String getMainBody() {
        return mainBody.toString();
    }
}