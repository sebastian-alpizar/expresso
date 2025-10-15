import java.util.*;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    private StringBuilder mainBody = new StringBuilder();
    private Set<String> declaredVariables = new HashSet<>();
    private Map<String, String> variableTypes = new HashMap<>();
    private int lambdaCounter = 0;
    
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
        
        // Determinar el tipo basado en el valor
        String type = inferType(ctx.expression());
        variableTypes.put(varName, type);
        
        if (!declaredVariables.contains(varName)) {
            declaredVariables.add(varName);
            return String.format("%s %s = %s;", type, varName, value);
        } else {
            return String.format("%s = %s;", varName, value);
        }
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
                String originalParam = id.getText();
                // Verificar si el parámetro ya está declarado en el ámbito
                String safeParam = getSafeParameterName(originalParam);
                params.add(safeParam);
            }
        }
        
        String body = visit(ctx.expression());
        
        // Construir la expresión lambda según el número de parámetros
        if (params.size() == 1) {
            return String.format("%s -> %s", params.get(0), body);
        } else if (params.size() == 2) {
            return String.format("(%s, %s) -> %s", params.get(0), params.get(1), body);
        } else {
            // Para más parámetros, usar Function
            StringBuilder paramList = new StringBuilder();
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) paramList.append(", ");
                paramList.append(params.get(i));
            }
            return String.format("(%s) -> %s", paramList.toString(), body);
        }
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
        if (ctx.INTEGER() != null) {
            return ctx.INTEGER().getText();
        }
        if (ctx.ID() != null) {
            return ctx.ID().getText();
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        return "";
    }
    
    @Override
    public String visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        String functionName = ctx.ID().getText();
        
        if (ctx.expression() != null && ctx.expression().size() > 0) {
            List<String> arguments = new ArrayList<>();
            for (ExprParser.ExpressionContext expr : ctx.expression()) {
                arguments.add(visit(expr));
            }
            
            String argsString = String.join(", ", arguments);
            
            // Si es una variable lambda, usar .apply() o el método apropiado
            if (declaredVariables.contains(functionName)) {
                String type = variableTypes.get(functionName);
                if (type != null) {
                    if (type.equals("UnaryOperator<Integer>")) {
                        return String.format("%s.apply(%s)", functionName, argsString);
                    } else if (type.equals("BinaryOperator<Integer>")) {
                        return String.format("%s.apply(%s)", functionName, argsString);
                    } else {
                        return String.format("%s.apply(%s)", functionName, argsString);
                    }
                }
            }
            
            // Si es una función matemática
            return String.format("%s(%s)", functionName, argsString);
        }
        
        return String.format("%s()", functionName);
    }
    
    public String getMainBody() {
        return mainBody.toString();
    }
}