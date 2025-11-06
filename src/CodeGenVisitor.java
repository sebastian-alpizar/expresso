import java.util.*;
// import org.antlr.v4.runtime.ParserRuleContext;

public class CodeGenVisitor extends ExprBaseVisitor<String> {
    /* ================================================================
        1. CAMPOS PRINCIPALES Y VISITORS AUXILIARES
    ================================================================ */
    private final ScopeManager scopeManager = new ScopeManager();
    private final DataVisitor dataVisitor = new DataVisitor();
    private final MatchVisitor matchVisitor = new MatchVisitor(this);
    private final Map<String, String> topLevelLambdas = new HashMap<>();

    private StringBuilder mainBody = new StringBuilder();
    private StringBuilder topLevel = new StringBuilder();
    private Map<String, String> pendingLambdaParamTypes = null;

    /* ================================================================
        2. GESTIÓN DE LAMBDAS TOP-LEVEL Y RECURSIVAS
    ================================================================ */

    private boolean isTopLevelLambda(String name) { return topLevelLambdas.containsKey(name); }
    private boolean isTopLevelMethod(String name) { return "method".equals(topLevelLambdas.get(name)); }
    private boolean isTopLevelField(String name) { return "lambdaField".equals(topLevelLambdas.get(name)); }
    private void registerTopLevelLambda(String name, String kind) { topLevelLambdas.put(name, kind); }

    private void promoteLambdaToTopLevel(String name) {
        String mainCode = mainBody.toString();
        String pattern = "(?m)^\\s*(?:var|UnaryOperator<.*?>|Function<.*?>|BiFunction<.*?>|BinaryOperator<.*?>)\\s+"
                + name + "\\s*=\\s*(.*?);\\s*$";

        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(mainCode);

        if (matcher.find()) {
            String lambdaExpr = matcher.group(1).trim();
            String fieldCode = generateStaticLambdaField(name, lambdaExpr);
            topLevel.append(fieldCode).append("\n");
            registerTopLevelLambda(name, "lambdaField");
            mainBody.replace(matcher.start(), matcher.end(), ""); // Remover la declaración del main
        }
    }

    private String generateStaticLambdaField(String name, String lambdaExpr) {
        // Detectar tipo según la cantidad de parámetros
        String type;
        String paramsPart = lambdaExpr.substring(0, lambdaExpr.indexOf("->")).trim();
        if (paramsPart.startsWith("(") && paramsPart.endsWith(")"))
            paramsPart = paramsPart.substring(1, paramsPart.length() - 1);

        int paramCount = paramsPart.isEmpty() ? 0 : paramsPart.split(",").length;
        switch (paramCount) {
            case 1 -> type = "UnaryOperator<Integer>";
            case 2 -> type = "BiFunction<Integer, Integer, Integer>";
            default -> type = "Function<Integer, Integer>";
        }

        return String.format("public static %s %s = %s;\n", type, name, lambdaExpr);
    }

    private boolean isRecursiveLambda(String varName, ExprParser.LambdaExpressionContext lambdaCtx) {
        if (lambdaCtx == null) return false;
        String bodyText = lambdaCtx.expression().getText();
        return bodyText.contains(varName + "(") || bodyText.contains(varName + " ("); // Detectar llamada recursiva (con o sin espacio)
    }

    private Set<String> getReferencedVars(ExprParser.LambdaExpressionContext lambdaCtx) {
        String bodyText = lambdaCtx.expression().getText();
        Set<String> refs = new HashSet<>();

        for (String var : scopeManager.getDeclaredVars()) {
            if (bodyText.contains(var + "(") || bodyText.contains(var + " ")) {
                // No incluir la propia lambda (evitar falso positivo recursivo)
                if (!lambdaCtx.getParent().getText().startsWith(var))
                    refs.add(var);
            }
        }
        return refs;
    }

    private String generateRecursiveLambdaAsMethod(
        String name, ExprParser.LambdaExpressionContext lambdaCtx, Set<String> deps) {

        // Obtener parámetros de la lambda
        List<String> params = new ArrayList<>();
        if (lambdaCtx.lambdaParams().ID() != null) {
            for (var id : lambdaCtx.lambdaParams().ID())
                params.add("int " + id.getText());
        } else if (lambdaCtx.lambdaParams().typedLambdaParams() != null) {
            for (var tp : lambdaCtx.lambdaParams().typedLambdaParams().typedParam())
                params.add(mapType(tp.type()) + " " + tp.ID().getText());
        }

        // Inferir tipo de retorno
        String returnType = inferType(lambdaCtx.expression());
        if (returnType.equals("var")) returnType = "int";

        // Cuerpo del método
        String body = visit(lambdaCtx.expression());

        // Generar el método estático
        return String.format("""
            public static %s %s(%s) {
                return %s;
            }
            """,
            returnType,
            name,
            String.join(", ", params),
            body
        );
    }

    /* ================================================================
        3. VISITAS DE SENTENCIAS PRINCIPALES
    ================================================================ */

    @Override
    public String visitProgram(ExprParser.ProgramContext ctx) {
        mainBody.setLength(0);
        topLevel.setLength(0);

        mainBody.append("public static void main(String[] args) {\n");
        scopeManager.enterScope(); // scope global
        for (ExprParser.StatementContext stmt : ctx.statement()) {
            if (stmt.dataStatement() != null) {
                dataVisitor.visit(stmt.dataStatement());
                topLevel.append(dataVisitor.getGeneratedCode()).append("\n");
                dataVisitor.clear(); // Limpieza obligatoria
                continue;
            }
            String result = visit(stmt);
            if (result != null && !result.trim().isEmpty() && !result.startsWith("public static"))
                mainBody.append("    ").append(result).append("\n");
        }
        scopeManager.exitScope();
        mainBody.append("}\n");
        return mainBody.toString();
    }
        
    @Override
    public String visitLetStatement(ExprParser.LetStatementContext ctx) {
        String varName = ctx.ID().getText();
        String type = "var";

        if (ctx.type() != null)
            type = mapType(ctx.type());
        else
            type = inferType(ctx.expression());
        type = normalizeType(type);

        pendingLambdaParamTypes = (ctx.expression().lambdaExpression() != null && ctx.type() != null)
                ? parseLambdaParamTypes(ctx.type())
                : null;

        // --- Si es una lambda, detectar recursividad ---
        if (ctx.expression().lambdaExpression() != null) {
            var lambda = ctx.expression().lambdaExpression();

            boolean isRecursive = isRecursiveLambda(varName, lambda);
            Set<String> deps = getReferencedVars(lambda);

            // Si esta lambda es usada como dependencia por otra recursiva, se debe mover también
            if (isRecursive) {
                // Antes de generar el método recursivo, elevar sus dependencias si son lambdas
                for (String dep : deps) {
                    // Si la dependencia fue declarada dentro del main (y no ya movida)
                    if (scopeManager.isVarDeclared(dep) && !isTopLevelLambda(dep))
                        promoteLambdaToTopLevel(dep);
                }

                String methodCode = generateRecursiveLambdaAsMethod(varName, lambda, deps);
                topLevel.append(methodCode).append("\n");
                registerTopLevelLambda(varName, "method");
                scopeManager.declareVar(varName, inferType(ctx.expression()));
                return ""; // no declarar dentro del main
            } else {
                // Si YA es una lambda top-level, NO generar en el main
                if (isTopLevelLambda(varName)) {
                    scopeManager.declareVar(varName, type);
                    return ""; // ya existe en top-level, no redeclarar
                }

                // Caso normal (lambda no recursiva)
                String value = visit(lambda);
                pendingLambdaParamTypes = null;
                if (!scopeManager.isVarDeclared(varName)) {
                    scopeManager.declareVar(varName, type);
                    return String.format("%s %s = %s;", type, varName, value);
                } else
                    return String.format("%s = %s;", varName, value);
            }
        }

        // --- Caso general (no lambda) ---
        String value = visit(ctx.expression());
        pendingLambdaParamTypes = null;

        if (!scopeManager.isVarDeclared(varName)) {
            scopeManager.declareVar(varName, type);
            return String.format("%s %s = %s;", type, varName, value);
        } else
            return String.format("%s = %s;", varName, value);
    }

    @Override
    public String visitFunStatement(ExprParser.FunStatementContext ctx) {
        String funName = ctx.ID().getText();
        String returnType = ctx.type() != null ? normalizeType(ctx.type().getText()) : "int";

        scopeManager.declareVar(funName, returnType); // Registrar el nombre de la función
        scopeManager.enterScope(); // Abrir un nuevo scope para los parámetros

        List<String> params = new ArrayList<>();
        if (ctx.paramList() != null)
            for (ExprParser.ParamContext p : ctx.paramList().param()) {
                String paramName = p.ID().getText();
                String paramType = normalizeType(p.type().getText());
                params.add(paramType + " " + paramName);
                scopeManager.declareVar(paramName, paramType); // <- Declaramos los parámetros aquí
            }

        String body = visit(ctx.expression());
        scopeManager.exitScope(); // <- Cerrar scope de parámetros
        String functionCode = String.format("public static %s %s(%s) { return %s; }",
                returnType, funName, String.join(", ", params), body);
        
        topLevel.append(functionCode).append("\n");
        return "";
    }

    @Override
    public String visitPrintStatement(ExprParser.PrintStatementContext ctx) {
        String value = visit(ctx.expression());
        return String.format("System.out.println(%s);", value);
    }

    /* ================================================================
        4. VISITAS DE EXPRESIONES Y OPERACIONES
    ================================================================ */

    @Override
    public String visitExpression(ExprParser.ExpressionContext ctx) {
        if (ctx.ternaryExpression() != null)
            return visit(ctx.ternaryExpression());
        if (ctx.logicalOrExpression() != null)
            return visit(ctx.logicalOrExpression());
        if (ctx.additiveExpression() != null)
            return visit(ctx.additiveExpression());
        if (ctx.lambdaExpression() != null)
            return visit(ctx.lambdaExpression());
        if (ctx.matchExpression() != null)
            return matchVisitor.visit(ctx.matchExpression());
        if (ctx.constructorExpr() != null)
            return visit(ctx.constructorExpr());
        if (ctx.castExpression() != null)       
            return visit(ctx.castExpression());
        return visitChildren(ctx);
    }

    @Override
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
        List<ExprParser.CastExpressionContext> expressions = ctx.castExpression();

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

        if (needsFloatCast)
            return String.format("(float)Math.pow(%s, %s)", base, exponent);
        else
            return String.format("(int)Math.pow(%s, %s)", base, exponent);
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
    public String visitLambdaExpression(ExprParser.LambdaExpressionContext ctx) {
        scopeManager.enterScope(); // Nuevo scope de lambda
        List<String> params = new ArrayList<>();

        // --- Soporta tanto ID como typedParam ---
        List<org.antlr.v4.runtime.tree.TerminalNode> ids = ctx.lambdaParams().ID();
        List<ExprParser.TypedParamContext> typedParams = null;
        if (ctx.lambdaParams().typedLambdaParams() != null)
            typedParams = ctx.lambdaParams().typedLambdaParams().typedParam();

        // Guardar el pendingLambdaParamTypes actual para restaurarlo después
        Map<String, String> previousPendingLambdaParamTypes = pendingLambdaParamTypes;

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
                        scopeManager.declareVar(finalName, "int");

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

        // Limpiar pendingLambdaParamTypes SOLO para esta lambda, no para las anidadas
        pendingLambdaParamTypes = null;

        String body = visit(ctx.expression());
        body = processLambdaBodyIfNeeded(body, params);
        scopeManager.exitScope();

        // Restaurar el pendingLambdaParamTypes anterior
        pendingLambdaParamTypes = previousPendingLambdaParamTypes;

        String code = (params.size() == 1)
                ? params.get(0) + " -> " + body
                : "(" + String.join(", ", params) + ") -> " + body;
        return code;
    }

    private String processLambdaBodyIfNeeded(String body, List<String> params) {
        if (scopeManager.isVarDeclared(body) && !params.isEmpty()) {
            String type = scopeManager.getVarType(body);
            if (type != null) {
                // Manejar Function, UnaryOperator, BiFunction, BinaryOperator
                if (type.startsWith("Function<") || type.startsWith("UnaryOperator<"))
                    if (params.size() == 1) {
                        String result = body + ".apply(" + params.get(0) + ")";
                        return result;
                    }
                else if (type.startsWith("BiFunction<") || type.startsWith("BinaryOperator<"))
                    if (params.size() == 2) {
                        String result = body + ".apply(" + params.get(0) + ", " + params.get(1) + ")";
                        return result;
                    }
            }
        }
        // CASO 2: El cuerpo ES una expresión lambda - NO aplicar .apply() aquí
        // Solo debemos aplicar parámetros si el cuerpo es una variable función, no si es una lambda literal
        if (body.contains("->") && !params.isEmpty()) {
            // En este caso, la lambda ya está correcta, no necesitamos aplicar .apply()
            // Por ejemplo: para "x -> z -> ...", el cuerpo "z -> ..." ya es correcto
            return body;
        }
        return body;
    }
    
    @Override
    public String visitPrimaryTarget(ExprParser.PrimaryTargetContext ctx) {
        if (ctx.ID() != null) return ctx.ID().getText();
        if (ctx.expression() != null) return visit(ctx.expression());
        return "";
    }

    @Override
    public String visitFunctionCall(ExprParser.FunctionCallContext ctx) {
        String result = visit(ctx.primaryTarget());
        if (result == null || result.isEmpty())
            result = ctx.primaryTarget().getText();

        boolean isDeclaredFunction = scopeManager.isVarDeclared(result)
                && scopeManager.getVarType(result).matches("Function<.*>|BiFunction<.*>|UnaryOperator<.*>|BinaryOperator<.*>");
        boolean isTopLevel = isTopLevelLambda(result);
        boolean isMethod = isTopLevel && isTopLevelMethod(result);
        boolean isField = isTopLevel && isTopLevelField(result);

        for (ExprParser.CallArgsContext call : ctx.callArgs()) {
            List<String> args = new ArrayList<>();
            for (ExprParser.ExpressionContext expr : call.expression()) {
                args.add(visit(expr));
            }
            String argsStr = String.join(", ", args);

            if ((isDeclaredFunction && !isMethod) || isField) {
                // Lambdas o campos estáticos => usan apply()
                if (args.size() == 1)
                    result = String.format("%s.apply(%s)", result, argsStr);
                else if (args.size() == 2)
                    result = String.format("%s.apply(%s, %s)", result, args.get(0), args.get(1));
                else
                    result = String.format("%s.apply(%s)", result, argsStr);
            } else  // Métodos estáticos => se invocan directo
                result = String.format("%s(%s)", result, argsStr);
        }

        return result;
    }

    @Override
    public String visitConstructorExpr(ExprParser.ConstructorExprContext ctx) {
        String name = capitalize(ctx.ID().getText());

        if (ctx.expressionList() == null)
            return "new " + name + "()";

        List<String> args = new ArrayList<>();
        for (ExprParser.ExpressionContext e : ctx.expressionList().expression()) {
            args.add(visit(e));
        }
        return "new " + name + "(" + String.join(", ", args) + ")";
    }

    @Override
    public String visitBooleanLiteral(ExprParser.BooleanLiteralContext ctx) {
        return ctx.getText(); // "true" o "false" directamente
    }
    
    @Override
    public String visitTernaryExpression(ExprParser.TernaryExpressionContext ctx) {        
        String condition = visit(ctx.logicalOrExpression());
        String trueExpr = visit(ctx.expression(0));
        String falseExpr = visit(ctx.expression(1));
        
        // Determinar el tipo de la condición
        String conditionType = inferPrimitiveType(condition);
        // Si la condición NO es booleana, agregar != 0
        if (!"boolean".equals(conditionType))
            return "((" + condition + ") != 0 ? " + trueExpr + " : " + falseExpr + ")";
        else
            return "(" + condition + " ? " + trueExpr + " : " + falseExpr + ")";
    }

    @Override
    public String visitPrimaryExpression(ExprParser.PrimaryExpressionContext ctx) {
        if (ctx.functionCall() != null) return visit(ctx.functionCall());
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();
        if (ctx.FLOAT() != null) return ctx.FLOAT().getText() + "f";
        if (ctx.STRING() != null) return ctx.STRING().getText();
        if (ctx.ID() != null) return ctx.ID().getText();
        if (ctx.booleanLiteral() != null) return visit(ctx.booleanLiteral());
        if (ctx.expression() != null) return visit(ctx.expression());
        return "";
    }

    @Override
    public String visitCastExpression(ExprParser.CastExpressionContext ctx) {
        String expr = visit(ctx.unaryExpression());

        if (ctx.type() == null)  // sin ':' type
            return expr;

        String targetType = ctx.type().getText();
        switch (targetType) {
            case "int":
                return "(int)(" + expr + ")";
            case "float":
                return "(float)(" + expr + ")";
            case "string":
                return "String.valueOf(" + expr + ")";
            default:
                return "((" + capitalize(targetType) + ")(" + expr + "))";
        }
    }

    @Override
    public String visitLogicalOrExpression(ExprParser.LogicalOrExpressionContext ctx) {
        if (ctx.getChildCount() == 1)
            return visit(ctx.logicalAndExpression(0));
        
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(visit(ctx.logicalAndExpression(0)));
        
        for (int i = 1; i < ctx.logicalAndExpression().size(); i++) {
            sb.append(" || ");
            sb.append(visit(ctx.logicalAndExpression(i)));
        }
        
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitLogicalAndExpression(ExprParser.LogicalAndExpressionContext ctx) {
        if (ctx.getChildCount() == 1)
            return visit(ctx.equalityExpression(0));
        
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(visit(ctx.equalityExpression(0)));
        
        for (int i = 1; i < ctx.equalityExpression().size(); i++) {
            sb.append(" && ");
            sb.append(visit(ctx.equalityExpression(i)));
        }
        
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitEqualityExpression(ExprParser.EqualityExpressionContext ctx) {
        if (ctx.getChildCount() == 1)
            return visit(ctx.relationalExpression(0));
        
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(visit(ctx.relationalExpression(0)));
        
        for (int i = 1; i < ctx.relationalExpression().size(); i++) {
            // El operador está entre las expresiones i-1 e i
            int operatorIndex = (i - 1) * 2 + 1;
            String operator = ctx.getChild(operatorIndex).getText();
            sb.append(" ").append(operator).append(" ");
            sb.append(visit(ctx.relationalExpression(i)));
        }
        
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitRelationalExpression(ExprParser.RelationalExpressionContext ctx) {
        if (ctx.getChildCount() == 1)
            return visit(ctx.additiveExpression(0));
        
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(visit(ctx.additiveExpression(0)));
        
        for (int i = 1; i < ctx.additiveExpression().size(); i++) {
            // El operador está entre las expresiones i-1 e i
            int operatorIndex = (i - 1) * 2 + 1;
            String operator = ctx.getChild(operatorIndex).getText();
            sb.append(" ").append(operator).append(" ");
            sb.append(visit(ctx.additiveExpression(i)));
        }
        
        sb.append(")");
        return sb.toString();
    }

    /* ================================================================
        5. INFERENCIA Y MANEJO DE TIPOS
    ================================================================ */

    private String inferPrimitiveType(String exprCode) {
        if (exprCode == null) return "int";
        exprCode = exprCode.trim();

        // PRIORIDAD 1: Variables en scope (más confiable)
        if (scopeManager.isVarDeclared(exprCode)) {
            String scopedType = scopeManager.getVarType(exprCode);
            if (scopedType != null)
                return normalizeType(scopedType);
        }

        // PRIORIDAD 2: Detectar strings (entre comillas)
        if ((exprCode.startsWith("\"") && exprCode.endsWith("\"")) || 
            exprCode.contains(".valueOf(") || 
            exprCode.equals("String.valueOf")) {
            return "String";
        }

        // PRIORIDAD 3: Detectar expresiones ternarias (para análisis recursivo)
        if (exprCode.contains("?") && exprCode.contains(":"))
            return "var"; // Simplificación: para ternarias anidadas, devolver var y dejar que el nivel superior lo resuelva

        // PRIORIDAD 4: Literales y patrones existentes
        if (exprCode.equals("true") || exprCode.equals("false"))
            return "boolean";
        if (exprCode.matches("^[0-9]+\\.[0-9]+f?$") || exprCode.endsWith("f") && !exprCode.contains("\""))
            return "float";
        if (exprCode.matches("^[0-9]+$"))
            return "int";
        if (exprCode.contains("Math.pow") || exprCode.contains("(float)"))
            return "float";
        
        // Detectar operadores booleanos
        if (exprCode.contains("||") || exprCode.contains("&&") || 
            exprCode.contains("==") || exprCode.contains("!=") ||
            exprCode.contains("<") || exprCode.contains(">") ||
            exprCode.contains("!") && !exprCode.contains("!=")) {
            return "boolean";
        }

        return "var";
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
        if (type == null) return "int";
        switch (type) {
            case "string": return "String";
            case "float": return "float";
            case "int": return "int";
            case "boolean": return "boolean";
            default: return type;
        }
    }

    private String inferType(ExprParser.ExpressionContext expr) {
        if (expr == null) return "int";
        
        // Caso específico para expresiones ternarias
        if (expr.ternaryExpression() != null) {
            ExprParser.TernaryExpressionContext ternary = expr.ternaryExpression();
            
            // Visitar las expresiones para obtener el código transpilado
            String trueExprCode = visit(ternary.expression(0));
            String falseExprCode = visit(ternary.expression(1));
            
            String trueType = inferPrimitiveType(trueExprCode);
            String falseType = inferPrimitiveType(falseExprCode);
            
            // Si ambos branches son del mismo tipo, usar ese tipo
            if (trueType.equals(falseType) && !"var".equals(trueType))
                return trueType;
            
            // Reglas de unificación de tipos
            if ("String".equals(trueType) || "String".equals(falseType)) return "String";
            if (("float".equals(trueType) && "int".equals(falseType)) || 
                ("float".equals(falseType) && "int".equals(trueType)))
                return "float";
            if (("boolean".equals(trueType) && !"boolean".equals(falseType)) ||
                ("boolean".equals(falseType) && !"boolean".equals(trueType)))
                return "var";

            return "var";
        }
        
        if (expr.lambdaExpression() != null) {
            var lambda = expr.lambdaExpression();
            int paramCount = getLambdaParamCount(lambda.lambdaParams());

            // Si los parámetros están tipados, podemos construir el tipo exacto
            if (lambda.lambdaParams().typedLambdaParams() != null) {
                List<ExprParser.TypedParamContext> typedParams = 
                    lambda.lambdaParams().typedLambdaParams().typedParam();

                List<String> paramTypes = new ArrayList<>();
                for (ExprParser.TypedParamContext tp : typedParams) {
                    paramTypes.add(mapTypeName(tp.type().getText()));
                }

                // Inferir tipo de retorno según el cuerpo de la lambda
                String returnType = inferType(lambda.expression());

                // Generar tipo de función adecuado
                if (paramTypes.size() == 1) {
                    return String.format("Function<%s, %s>", paramTypes.get(0), mapTypeName(returnType));
                } else if (paramTypes.size() == 2) {
                    return String.format("BiFunction<%s, %s, %s>", paramTypes.get(0), paramTypes.get(1), mapTypeName(returnType));
                } else {
                    return String.format("Supplier<%s>", mapTypeName(returnType));
                }
            }

            // Para lambdas sin tipos explícitos, inferir basado en el cuerpo
            String bodyType = inferType(lambda.expression());
            String bodyText = lambda.expression().getText();

            if (bodyText.contains("match")) {
                // Detecta una lambda con un 'match' pattern -> necesita un tipo más genérico
                if (paramCount == 1)
                    return "Function<List, Object>";
                else if (paramCount == 2)
                    return "BiFunction<Object, Object, Object>";
                else
                    return "Supplier<Object>";
            }
            
            // Si el cuerpo es otra lambda, es una función de orden superior
            if (bodyType.contains("Function") || bodyType.contains("Operator")) {
                if (paramCount == 1) {
                    return String.format("Function<Integer, %s>", bodyType);
                } else if (paramCount == 2) {
                    return String.format("BiFunction<Integer, Integer, %s>", bodyType);
                }
            }

            // Caso genérico (sin match)
            return (paramCount == 1 ? "UnaryOperator<Integer>" : "BinaryOperator<Integer>");
        }

        String text = expr.getText();
        if (scopeManager.isVarDeclared(text)) {
            String scopedType = scopeManager.getVarType(text);
            if (scopedType != null) return scopedType;
        }
        return inferPrimitiveType(visit(expr));
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

    /* ================================================================
        6. UTILIDADES FINALES
    ================================================================ */

    public String getMainBody() {
        return topLevel.toString() + mainBody.toString();
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

        private int getLambdaParamCount(ExprParser.LambdaParamsContext params) {
        if (params == null) return 0;
        return params.ID().size();
    }
    
    @Override
    public String visitLambdaParams(ExprParser.LambdaParamsContext ctx) {
        return null; // Solo recolectar los parámetros, no generar código aquí
    }
}