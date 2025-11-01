import java.util.*;

public class MatchVisitor extends ExprBaseVisitor<String> {

    private final ExprBaseVisitor<String> parent;

    public MatchVisitor(ExprBaseVisitor<String> parent) {
        this.parent = parent; // para poder visitar expresiones internas
    }

    @Override
    public String visitMatchExpression(ExprParser.MatchExpressionContext ctx) {
        String matchedExpr = parent.visit(ctx.expression());
        StringBuilder code = new StringBuilder();

        // Generamos un switch moderno (Java 21+)
        code.append("switch (").append(matchedExpr).append(") {\n");

        for (ExprParser.MatchRuleContext rule : ctx.matchRule()) {

        // Toma siempre la última expresión (la del lado derecho del "->")
        ExprParser.ExpressionContext resultExpr = rule.expression(rule.expression().size() - 1);
            code.append("    case ").append(visit(rule.pattern())).append(" -> ")
                .append(parent.visit(resultExpr)).append(";\n");
        }

        code.append("}");
        return code.toString();
    }

    @Override
    public String visitDataPattern(ExprParser.DataPatternContext ctx) {
        String name = ctx.ID().getText();

        // Caso: patrón sin argumentos, ejemplo: Nil
        if (ctx.pattern().isEmpty()) return name + "()";

        // Caso: patrón con argumentos, ejemplo: Cons(f, _)
        List<String> params = new ArrayList<>();
        for (ExprParser.PatternContext p : ctx.pattern()) {
            String sub = parent.visit(p);

            // Si el subpatrón es nulo (porque era variable o "_"), tratamos según su forma
            if (sub == null || sub.isEmpty()) {
                String text = p.getText();
                if (text.equals("_"))
                    params.add("var _ignored"); // patrón anónimo
                else
                    params.add("var " + text); // variable pattern
            } else
                params.add(sub);
        }
        return name + "(" + String.join(", ", params) + ")";
    }

    @Override
    public String visitNativePattern(ExprParser.NativePatternContext ctx) {
        if (ctx.getText().equals("_")) return "_";
        if (ctx.INTEGER() != null) return ctx.INTEGER().getText();
        if (ctx.FLOAT() != null) return ctx.FLOAT().getText() + "f";
        if (ctx.STRING() != null) return ctx.STRING().getText();
        if (ctx.getText().equals("none")) return "null";
        return ctx.getText();
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
