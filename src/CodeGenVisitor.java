import java.util.*;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    private StringBuilder mainBody = new StringBuilder();
    private Set<String> declaredVariables = new HashSet<>();
    
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
        
        if (!declaredVariables.contains(varName)) {
            declaredVariables.add(varName);
            return String.format("%s %s = %s;", type, varName, value);
        } else {
            return String.format("%s = %s;", varName, value);
        }
    }
    
    private String inferType(ExprParser.ExpressionContext expr) {
        // Si el valor contiene una expresión lambda, usar UnaryOperator<Integer>
        if (expr.lambdaExpression() != null) {
            return "UnaryOperator<Integer>";
        } else {
            return "int";
        }
    }
    
    @Override
    public String visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        String value = visit(ctx.expression());
        return String.format("System.out.println(%s);", value);
    }
    
    @Override
    public String visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        String param = ctx.ID().getText();
        String body = visit(ctx.expression());
        // Solo devolver la expresión lambda, sin el tipo
        return String.format("%s -> %s", param, body);
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
        
        // Usar Math.pow para exponenciación - con import estático ya no necesita el import
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
        
        if (ctx.expression() != null) {
            String argument = visit(ctx.expression());
            
            // Si es una variable lambda, usar .apply()
            if (declaredVariables.contains(functionName)) {
                return String.format("%s.apply(%s)", functionName, argument);
            } else {
                // Si es una función matemática
                return String.format("%s(%s)", functionName, argument);
            }
        }
        
        return String.format("%s()", functionName);
    }
    
    public String getMainBody() {
        return mainBody.toString();
    }
}