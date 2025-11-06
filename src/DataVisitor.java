import java.util.*;

// Este visitor se encarga EXCLUSIVAMENTE de visitar las sentencias `data`
// y generar el código Java correspondiente (sealed interfaces + records)
public class DataVisitor extends ExprBaseVisitor<String> {

    // Instancia del generador de tipos (clase auxiliar que crea el código Java)
    private final DataTypeGenerator generator = new DataTypeGenerator();

    // Aquí se acumula todo el código generado para las sentencias `data`
    private final StringBuilder generatedCode = new StringBuilder();


    // ==========================================================
    //  Método principal: visita un nodo `dataStatement`
    // ==========================================================
    @Override
    public String visitDataStatement(ExprParser.DataStatementContext ctx) {
        // Extrae el nombre del tipo de datos, por ejemplo "Shape"
        String typeName = ctx.ID().getText();

        // Lista que contendrá todos los constructores (Circle, Rectangle, etc.)
        List<DataTypeGenerator.ConstructorDef> constructors = new ArrayList<>();

        // Recorre cada constructor definido dentro del "data"
        for (ExprParser.ConstructorContext cctx : ctx.constructorList().constructor()) {
            String name = cctx.ID().getText(); // Nombre del constructor (p. ej. Circle)

            // Crea una definición de constructor vacía
            DataTypeGenerator.ConstructorDef def = new DataTypeGenerator.ConstructorDef(name);

            // Si el constructor tiene parámetros, los procesa
            if (cctx.arguments() != null) {
                // Recorre cada argumento declarado
                for (ExprParser.ArgumentContext arg : cctx.arguments().argument()) {
                    // Obtiene el nombre del argumento, o usa "arg" si no hay identificador
                    String argName = arg.ID() != null ? arg.ID().getText() : "arg";

                    // Obtiene el tipo del argumento, por ejemplo "float" o "string"
                    String argType = arg.flatType().getText();

                    // Agrega el argumento a la definición del constructor
                    def.addArg(argName, argType);
                }
            }

            // Agrega el constructor completo a la lista
            constructors.add(def);
        }

        // Envía el tipo y sus constructores al generador
        generator.visitDataStatement(typeName, constructors);

        // Agrega el código generado al StringBuilder general
        generatedCode.append(generator.getTopLevelCode()).append("\n");

        // (esta llamada adicional a getTopLevelCode() no tiene efecto; podría eliminarse)
        generator.getTopLevelCode();

        // No devuelve nada porque el código se acumula en `generatedCode`
        return null;
    }


    // ==========================================================
    //  Devuelve todo el código generado de los `data`
    // ==========================================================
    public String getGeneratedCode() {
        return generatedCode.toString();
    }


    // ==========================================================
    //  Limpia el código generado (para reiniciar el visitor)
    // ==========================================================
    public void clear() {
        generatedCode.setLength(0);
    }
}
