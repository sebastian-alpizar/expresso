import java.util.*;

// Esta clase se encarga exclusivamente de generar el código Java
// para las declaraciones "data" (tipos algebraicos).
public class DataTypeGenerator {

    // Guarda todo el código generado (interfaces y records) que irá al nivel superior.
    private final StringBuilder topLevelCode = new StringBuilder();


    // ==========================================================
    //  Genera el código de una declaración `data`
    // ==========================================================
    public String visitDataStatement(String name, List<ConstructorDef> constructors) {
        // Ejemplo: si name = "Shape" y los constructores son Circle y Rectangle

        // Genera el encabezado del tipo sellado:
        // sealed interface Shape permits Circle, Rectangle {}
        topLevelCode.append(String.format("sealed interface %s permits %s {}\n",
                capitalize(name), // Pone en mayúscula la primera letra del nombre
                joinConstructorNames(constructors))); // Une los nombres de los constructores

        // Luego genera cada "record" que implementa la interfaz sellada
        for (ConstructorDef cons : constructors) {
            generateRecord(cons, capitalize(name));
        }

        // Este método no genera nada dentro del main, solo código superior
        return "";
    }


    // ==========================================================
    //  Genera el código Java de cada record constructor
    // ==========================================================
    private void generateRecord(ConstructorDef cons, String parentType) {
        // Crea los parámetros del constructor, ejemplo:
        // Circle(float r) o Rectangle(float w, float h)
        String params = cons.args.isEmpty()
                ? "()" // sin parámetros
                : "(" + String.join(", ", cons.args.stream().map(a ->
                        mapType(a.type) + " " + a.name).toList()) + ")";

        // Genera el record que implementa la interfaz sellada:
        // record Circle(float r) implements Shape {}
        topLevelCode.append(String.format("record %s%s implements %s {}\n",
                capitalize(cons.name), params, parentType));
    }


    // ==========================================================
    //  Devuelve el código generado hasta ahora
    // ==========================================================
    public String getTopLevelCode() {
        String code = topLevelCode.toString();
        clear(); // Limpia para reutilizar el generador
        return code;
    }

    // Limpia el contenido acumulado del StringBuilder
    public void clear() {
        topLevelCode.setLength(0);
    }


    // ==========================================================
    //  Funciones auxiliares ("helpers")
    // ==========================================================

    // Convierte tipos del lenguaje propio a tipos Java
    private String mapType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "float" -> "float";
            case "string" -> "String";
            case "any" -> "Object";
            default -> capitalize(t); // Si es otro tipo de data, lo pone con mayúscula
        };
    }

    // Une los nombres de los constructores en una sola línea:
    // Ejemplo: [Circle, Rectangle] → "Circle, Rectangle"
    private String joinConstructorNames(List<ConstructorDef> list) {
        return String.join(", ", list.stream().map(c -> capitalize(c.name)).toList());
    }

    // Capitaliza la primera letra (por convención en Java, los tipos van con mayúscula)
    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }


    // ==========================================================
    //  Clase auxiliar interna: ConstructorDef
    // Representa un constructor de tipo `data`
    // ==========================================================
    public static class ConstructorDef {
        public final String name;          // nombre del constructor (p. ej. "Circle")
        public final List<Arg> args = new ArrayList<>();  // lista de parámetros del constructor

        public ConstructorDef(String name) {
            this.name = name;
        }

        // Añade un argumento al constructor (p. ej. addArg("r", "float"))
        public void addArg(String name, String type) {
            args.add(new Arg(name, type));
        }

        // Clase interna para representar un argumento individual
        public static class Arg {
            public final String name, type;

            public Arg(String name, String type) {
                this.name = name;
                this.type = type;
            }
        }
    }
}
