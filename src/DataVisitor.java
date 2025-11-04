import java.util.*;

// En lugar de sobrecargar mi CodeGenVisitor, creo un visitor solo para data

public class DataVisitor extends ExprBaseVisitor<String> {
    private final DataTypeGenerator generator = new DataTypeGenerator();
    private final StringBuilder generatedCode = new StringBuilder();

    @Override
    public String visitDataStatement(ExprParser.DataStatementContext ctx) {
        String typeName = ctx.ID().getText();
        List<DataTypeGenerator.ConstructorDef> constructors = new ArrayList<>();

        for (ExprParser.ConstructorContext cctx : ctx.constructorList().constructor()) {
            String name = cctx.ID().getText();
            DataTypeGenerator.ConstructorDef def = new DataTypeGenerator.ConstructorDef(name);

            if (cctx.arguments() != null) {
                for (ExprParser.ArgumentContext arg : cctx.arguments().argument()) {
                    String argName = arg.ID() != null ? arg.ID().getText() : "arg";
                    String argType = arg.flatType().getText();
                    def.addArg(argName, argType);
                }
            }
            constructors.add(def);
        }

        generator.visitDataStatement(typeName, constructors);
        generatedCode.append(generator.getTopLevelCode()).append("\n");
        generator.getTopLevelCode();
        return null;
    }

    public String getGeneratedCode() {
        return generatedCode.toString();
    }

    public void clear() {
        generatedCode.setLength(0);
    }
}
