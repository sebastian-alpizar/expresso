import java.util.*;
// import org.antlr.v4.runtime.ParserRuleContext;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    private final ScopeManager scopeManager = new ScopeManager();
    private StringBuilder mainBody = new StringBuilder();
    private StringBuilder topLevel = new StringBuilder();
    private Map<String, String> pendingLambdaParamTypes = null;

    // private String debug(String label, ParserRuleContext ctx, String result) {
    //     String text = ctx.getText().replaceAll("\\s+", " ");
    //     System.out.println("[DEBUG] " + label + " -> " + text);
    //     System.out.println("         Result: " + result);
    //     System.out.println("-------------------------------------------");
    //     return result;
    // }

    // public void printTree(ParserRuleContext ctx, int indent) {
    //     String spaces = "  ".repeat(indent);
    //     System.out.println(spaces + ctx.getClass().getSimpleName() + ": " + ctx.getText());
    //     for (int i = 0; i < ctx.getChildCount(); i++) {
    //         if (ctx.getChild(i) instanceof ParserRuleContext) {
    //             printTree((ParserRuleContext) ctx.getChild(i), indent + 1);
    //         } else {
    //             System.out.println(spaces + "  " + ctx.getChild(i).getClass().getSimpleName() + ": " + ctx.getChild(i).getText());
    //         }
    //     }
    // }
    
    @Override
    public String visitProgram(ExprParser.ProgramContext ctx) {
        mainBody.append("public static void main(String[] args) {\n");
        scopeManager.enterScope(); // scope global
        for (ExprParser.StatementContext stmt : ctx.statement()) {
            String result = visit(stmt);
            if (result != null && !result.trim().isEmpty() && !result.startsWith("public static"))
                mainBody.append("    ").append(result).append("\n");
        }
        scopeManager.exitScope();
        mainBody.append("}\n");
        return mainBody.toString();
    }
    
    @Override // ---------------- LET ----------------
    public String visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        String type = "int";
        
        if (ctx.type() != null)
            type = mapType(ctx.type());
        else
            type = inferType(ctx.expression());
        type = normalizeType(type);

        // Si el valor es una lambda y el tipo es una función, preparar los tipos de parámetros (PENDIENTES)
        if (ctx.expression().lambdaExpression() != null && ctx.type() != null)
            pendingLambdaParamTypes = parseLambdaParamTypes(ctx.type());
        else
            pendingLambdaParamTypes = null;
        
        String value = visit(ctx.expression());
        pendingLambdaParamTypes = null; // una vez visitada la expresión, limpiamos por seguridad
        // variableTypes.put(varName, type);

        String code;
        if (!scopeManager.isVarDeclared(varName)) {
            scopeManager.declareVar(varName, type);
            code = String.format("%s %s = %s;", type, varName, value);
        } else 
            code = String.format("%s = %s;", varName, value);

        return code;
    }

    private Map<String,String> parseLambdaParamTypes(ExprParser.TypeContext typeCtx) {
        Map<String,String> map = new LinkedHashMap<>();
        if (typeCtx == null) return map;
        String typeText = typeCtx.getText(); // ejemplo: ((float,int)->float)
        if (!typeText.contains("->")) return map;

        String inside = typeText;
        if (inside.startsWith("(") && inside.endsWith(")"))
            inside = inside.substring(1, inside.length() - 1);

        String[] parts = inside.split("->");
        if (parts.length != 2) return map;

        String paramPart = parts[0].trim().replace("(", "").replace(")", "");
        if (paramPart.isEmpty()) return map;

        String[] paramTypes = paramPart.split(",");
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = paramTypes[i].trim();
        }
        // solo devolvemos la lista de tipos; nombres los obtendremos en visitLambdaExpression
        for (int i = 0; i < paramTypes.length; i++) {
            map.put(String.valueOf(i), normalizeType(paramTypes[i])); // clave: índice como string
        }
        return map;
    }
    
    private String inferType(ExprParser.ExpressionContext expr) {
        if (expr == null) return "int";
        if (expr.lambdaExpression() != null) {
            var lambda = expr.lambdaExpression();

            // Si los parámetros están tipados, podemos construir el tipo exacto
            if (lambda.lambdaParams().typedLambdaParams() != null) {
                List<ExprParser.TypedParamContext> typedParams = 
                    lambda.lambdaParams().typedLambdaParams().typedParam();

                List<String> paramTypes = new ArrayList<>();
                for (ExprParser.TypedParamContext tp : typedParams) {
                    paramTypes.add(mapTypeName(tp.type().getText())); // convierte float->Float, int->Integer...
                }

                // Inferir tipo de retorno según el cuerpo de la lambda
                String returnType = inferPrimitiveType(visit(lambda.expression()));

                // Generar tipo de función adecuado
                if (paramTypes.size() == 1)
                    return String.format("Function<%s, %s>", paramTypes.get(0), mapTypeName(returnType));
                else if (paramTypes.size() == 2)
                    return String.format("BiFunction<%s, %s, %s>", paramTypes.get(0), paramTypes.get(1), mapTypeName(returnType));
                else
                    return String.format("Supplier<%s>", mapTypeName(returnType));
            }

            // Si NO hay tipos explícitos, seguimos usando el comportamiento antiguo
            int paramCount = getLambdaParamCount(lambda.lambdaParams());
            return (paramCount == 1 ? "UnaryOperator<Integer>" : "BinaryOperator<Integer>");
        }

        String text = expr.getText();
        if (scopeManager.isVarDeclared(text)) {
            String scopedType = scopeManager.getVarType(text);
            if (scopedType != null)
                return scopedType;
        }
        return inferPrimitiveType(text);
    }
    
    private int getLambdaParamCount(ExprParser.LambdaParamsContext params) {
        if (params == null) return 0;
        return params.ID().size();
    }
    
    @Override // ---------------- PRINT ----------------
    public String visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        String value = visit(ctx.expression());
        return String.format("System.out.println(%s);", value);
    }
    
    @Override
    public String visitLambdaParams(ExprParser.LambdaParamsContext ctx) {
        return null; // Solo recolectar los parámetros, no generar código aquí
    }
    
    @Override
    public String visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        scopeManager.enterScope(); // Nuevo scope de lambda
        List<String> params = new ArrayList<>();

        // --- Soporta tanto ID como typedParam ---
        List<org.antlr.v4.runtime.tree.TerminalNode> ids = ctx.lambdaParams().ID();
        List<ExprParser.TypedParamContext> typedParams = null;
        if (ctx.lambdaParams().typedLambdaParams() != null)
            typedParams = ctx.lambdaParams().typedLambdaParams().typedParam();

        if ((ids != null && !ids.isEmpty()) || (typedParams != null && !typedParams.isEmpty())) {
            // Parámetros simples sin tipo explícito
            if (ids != null && !ids.isEmpty()) {
                for (int i = 0; i < ids.size(); i++) {
                    String original = ids.get(i).getText();
                    String declaredType = null;
                    if (pendingLambdaParamTypes != null && pendingLambdaParamTypes.containsKey(String.valueOf(i)))
                        declaredType = pendingLambdaParamTypes.get(String.valueOf(i));

                    String finalName = scopeManager.isVarDeclared(original)
                            ? scopeManager.getSafeName(original)
                            : original;

                    if (declaredType != null)
                        scopeManager.declareVar(finalName, declaredType);
                    else
                        scopeManager.declareVar(finalName);

                    params.add(finalName);
                }
            }

            // Parámetros tipados (x:float, n:int)
            if (typedParams != null && !typedParams.isEmpty()) {
                for (ExprParser.TypedParamContext tp : typedParams) {
                    String original = tp.ID().getText();
                    String declaredType = mapType(tp.type());
                    String finalName = scopeManager.isVarDeclared(original)
                            ? scopeManager.getSafeName(original)
                            : original;

                    scopeManager.declareVar(finalName, declaredType);
                    params.add(finalName);
                }
            }
        }

        pendingLambdaParamTypes = null; // limpiar después de usar
        String body = visit(ctx.expression());
        body = processLambdaBodyIfNeeded(body, params);
        scopeManager.exitScope();

        String code = (params.size() == 1)
                ? params.get(0) + " -> " + body
                : "(" + String.join(", ", params) + ") -> " + body;
        return code;
    }



    private String processLambdaBodyIfNeeded(String body, List<String> params) {
        if (scopeManager.isVarDeclared(body) && !params.isEmpty()) {
            String type = scopeManager.getVarType(body);
            if (type != null) {
                switch (type) {
                    case "UnaryOperator<Integer>":
                        if (params.size() == 1)
                            return body + ".apply(" + params.get(0) + ")";
                        break; 
                    case "BinaryOperator<Integer>":
                        if (params.size() == 2)
                            return body + ".apply(" + params.get(0) + ", " + params.get(1) + ")";
                        break;
                }
            }
        }
        return body;
    }
    
    @Override  // ---------------- EXPRESIONES ----------------
    public String visitAdditiveExpression(ExprParser.AdditiveExpressionContext ctx) {
        List<ExprParser.MultiplicativeExpressionContext> expressions = ctx.multiplicativeExpression();
        
        if (expressions.size() == 1)
            return visit(expressions.get(0));
        
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
        
        if (expressions.size() == 1)
            return visit(expressions.get(0));

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

        if (expressions.size() == 1)
            return visit(expressions.get(0));

        String base = visit(expressions.get(0));
        String exponent = visit(expressions.get(1));

        base = scopeManager.resolveSafeName(base);
        exponent = scopeManager.resolveSafeName(exponent);

        // Obtener tipos REALES considerando el scope
        String baseType = inferPrimitiveType(base);
        String exponentType = inferPrimitiveType(exponent);

        boolean needsFloatCast = "float".equals(baseType) || "float".equals(exponentType);
        String result;
        if (needsFloatCast)
            result = String.format("(float)Math.pow(%s, %s)", base, exponent);
        else
            result = String.format("(int)Math.pow(%s, %s)", base, exponent);
        return result;
    }

    @Override
    public String visitUnaryExpression(ExprParser.UnaryExpressionContext ctx) {
        if (ctx.primaryExpression() != null)
            return visit(ctx.primaryExpression());
        
        String operator = ctx.getChild(0).getText();
        String expression = visit(ctx.unaryExpression());
        return "(" + operator + expression + ")";
    }
    
    @Override
    public String visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.functionCall() != null) return visit(ctx.functionCall());
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();
        if (ctx.FLOAT() != null) return ctx.FLOAT().getText() + "f";
        if (ctx.STRING() != null) return ctx.STRING().getText();
        if (ctx.ID() != null) return ctx.ID().getText();
        if (ctx.expression() != null) return visit(ctx.expression());
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

        if (scopeManager.isVarDeclared(functionName)) {
            String type = scopeManager.getVarType(functionName);
            if (type != null && (type.contains("Operator") || type.contains("Function"))) {
                if (args.size() == 1)
                    return String.format("%s.apply(%s)", functionName, argsString);
                else if (args.size() == 2)
                    return String.format("%s.apply(%s, %s)", functionName, args.get(0), args.get(1));
                else
                    return String.format("%s.apply(%s)", functionName, argsString);
            }
        }
        return functionName + "(" + argsString + ")";
    }

    @Override
    public String visitExpression(ExprParser.ExpressionContext ctx) {
        if (ctx.lambdaExpression() != null)
            return visit(ctx.lambdaExpression());
        else if (ctx.ternaryExpression() != null)
            return visit(ctx.ternaryExpression());
        else if (ctx.additiveExpression() != null)
            return visit(ctx.additiveExpression());
        return visitChildren(ctx);
    }

    @Override
    public String visitTernaryExpression(ExprParser.TernaryExpressionContext ctx) {        
        String condition = visit(ctx.additiveExpression());
        String trueExpr = visit(ctx.expression(0));
        String falseExpr = visit(ctx.expression(1));
        return "(" + condition + " != 0 ? " + trueExpr + " : " + falseExpr + ")";
    }
    
    public String getMainBody() {
        return topLevel.toString() + mainBody.toString();
    }

    // xxxxxxxxxxxxxxx Nuevas funciones del Spring Final xxxxxxxxxxxxxxx
    
    @Override
    public String visitFunStatement(ExprParser.FunStatementContext ctx) {
        String funName = ctx.ID().getText();
        String returnType = ctx.type() != null ? normalizeType(ctx.type().getText()) : "int";

        // variableTypes.put(funName, returnType);
        // declaredVariables.add(funName);
        scopeManager.declareVar(funName, returnType);

        List<String> params = new ArrayList<>();
        if (ctx.paramList() != null)
            for (ExprParser.ParamContext p : ctx.paramList().param()) {
                String t = normalizeType(returnType);
                params.add(t + " " + p.ID().getText());
            }

        String body = visit(ctx.expression());
        String functionCode = String.format("public static %s %s(%s) { return %s; }",
                returnType, funName, String.join(", ", params), body);
        
        topLevel.append(functionCode).append("\n");
        return "";
    }

    private String mapType(ExprParser.TypeContext ctx) {
        String text = ctx.getText();
        text = normalizeType(text);

        // Tipos de función (como (float, int) -> float)
        if (text.contains("->")) {
            // Parsear forma ((A, B) -> C)
            String inside = text.substring(1, text.length() - 1);
            String[] parts = inside.split("->");
            String params = parts[0].trim();
            String ret = parts[1].trim();

            List<String> paramTypes = Arrays.asList(params.replace("(", "").replace(")", "").split(","));
            paramTypes.replaceAll(String::trim);

            String retType = mapTypeName(ret);

            if (paramTypes.size() == 1)
                return "Function<" + mapTypeName(paramTypes.get(0)) + ", " + retType + ">";
            else if (paramTypes.size() == 2)
                return "BiFunction<" + mapTypeName(paramTypes.get(0)) + ", " + mapTypeName(paramTypes.get(1)) + ", " + retType + ">";
            else
                return "Supplier<" + retType + ">";
        }
        return text;
    }

    // convierte a wrappers Java (int -> Integer, float -> Float)
    private String mapTypeName(String t) {
        t = t.trim();
        switch (t) {
            case "int": return "Integer";
            case "float": return "Float";
            case "string": return "String";
            default: return t;
        }
    }

    private String normalizeType(String type) {
        if (type == null) return "int"; // valor por defecto
        switch (type) {
            case "string": return "String";
            case "float": return "float";
            case "int": return "int";
            default: return type;
        }
    }

    private String inferPrimitiveType(String exprCode) {
        if (exprCode == null) return "int";
        exprCode = exprCode.trim();

        // PRIORIDAD 1: Variables en scope (más confiable)
        if (scopeManager.isVarDeclared(exprCode)) {
            String scopedType = scopeManager.getVarType(exprCode);
            if (scopedType != null) {
                return normalizeType(scopedType);
            }
        }

        // PRIORIDAD 2: Literales y patrones
        if (exprCode.matches("^[0-9]+\\.[0-9]+f?$")) {
            return "float";
        }
        if (exprCode.matches("^[0-9]+$")) {
            return "int";
        }
        if (exprCode.startsWith("\"") && exprCode.endsWith("\"")) {
            return "String";
        }
        if (exprCode.contains("Math.pow") || exprCode.contains("float")) {
            return "float";
        }
        if (exprCode.matches(".*[\\+\\-\\*/].*")) {
            return exprCode.contains(".") || exprCode.contains("f") ? "float" : "int";
        }

        return "int";
    }
}