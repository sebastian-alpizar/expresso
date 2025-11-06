import java.util.*;

// Este visitor maneja las expresiones `match` (pattern matching).
// Transforma el match del lenguaje en un switch expression de Java.
public class MatchVisitor extends ExprBaseVisitor<String> {

    // Referencia al visitor principal (CodeGenVisitor o similar)
    // Se usa para poder visitar expresiones internas dentro de los patrones.
    private final ExprBaseVisitor<String> parent;

    // Constructor: recibe el visitor principal como dependencia.
    public MatchVisitor(ExprBaseVisitor<String> parent) {
        this.parent = parent;
    }

    // ==========================================================
    //  Método principal: procesa una expresión `match`
    // ==========================================================
    @Override
    public String visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        // Evalúa la expresión que se va a "matchear", ej: xs
        String matchedExpr = parent.visit(ctx.expression());

        // Acumulador del código generado
        StringBuilder code = new StringBuilder();

        // Inicia la estructura switch moderna de Java (con flechas "->")
        code.append("switch (").append(matchedExpr).append(") {\n");

        // Recorre cada regla del match (cada "case")
        for (ExprParser.MatchRuleContext rule : ctx.matchRule()) {

            // Cada regla tiene una o más expresiones, pero la última es la del lado derecho del "->"
            ExprParser.ExpressionContext resultExpr = rule.expression(rule.expression().size() - 1);

            // Genera la línea "case patrón -> resultado;"
            code.append("    case ")
                .append(visit(rule.pattern())) // visita el patrón (ej: Cons(h, t))
                .append(" -> ")
                .append(parent.visit(resultExpr)) // visita el resultado (ej: h + sum(t))
                .append(";\n");
        }

        // Cierra el bloque switch
        code.append("}");
        return code.toString();
    }

    // ==========================================================
    //  Procesa patrones que corresponden a tipos de datos (DataPattern)
    // ==========================================================
    @Override
    public String visitDataPattern(ExprParser.DataPatternContext ctx) {
        String name = ctx.ID().getText(); // Nombre del constructor, ej: Cons o Nil

        // Caso 1: patrón sin argumentos, ej: Nil
        if (ctx.pattern().isEmpty()) return name + "()";

        // Caso 2: patrón con argumentos, ej: Cons(f, _)
        List<String> params = new ArrayList<>();

        for (ExprParser.PatternContext p : ctx.pattern()) {
            String sub = parent.visit(p); // Visita cada subpatrón (puede ser variable o valor)

            // Si el subpatrón es una variable o "_", se maneja especialmente
            if (sub == null || sub.isEmpty()) {
                String text = p.getText();
                if (text.equals("_"))
                    // "_" se convierte en un patrón ignorado
                    params.add("var _ignored");
                else
                    // Cualquier otro identificador se convierte en una variable pattern
                    params.add("var " + text);
            } else
                // Si es un subpatrón compuesto, se usa tal cual
                params.add(sub);
        }

        // Devuelve algo como: Cons(var h, var t)
        return name + "(" + String.join(", ", params) + ")";
    }

    // ==========================================================
    //  Procesa patrones nativos (números, strings, none, etc.)
    // ==========================================================
    @Override
    public String visitNativePattern(ExprParser.NativePatternContext ctx) {
        // Caso "_" → comodín (ignora el valor)
        if (ctx.getText().equals("_")) return "_";

        // Literales enteros
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();

        // Literales flotantes (agrega 'f' al final para Java)
        if (ctx.FLOAT() != null) return ctx.FLOAT().getText() + "f";

        // Literales de texto (cadena)
        if (ctx.STRING() != null) return ctx.STRING().getText();

        // none → se traduce como null
        if (ctx.getText().equals("none")) return "null";

        // Cualquier otro literal se devuelve tal cual
        return ctx.getText();
    }
}
