import java.util.*;


// Esta clase se encargará solo de manejar los data y los constructores.
public class DataTypeGenerator {

    private final StringBuilder topLevelCode = new StringBuilder();

    public String visitDataStatement(String name, List<ConstructorDef> constructors) {
        // Crea el sealed interface base
        topLevelCode.append(String.format("sealed interface %s permits %s {}\n",
                capitalize(name),
                joinConstructorNames(constructors)));

        // Crea cada record
        for (ConstructorDef cons : constructors) {
            generateRecord(cons, capitalize(name));
        }

        return ""; // no genera código dentro del main
    }

    private void generateRecord(ConstructorDef cons, String parentType) {
        String params = cons.args.isEmpty()
                ? "()"
                : "(" + String.join(", ", cons.args.stream().map(a ->
                        mapType(a.type) + " " + a.name).toList()) + ")";

        topLevelCode.append(String.format("record %s%s implements %s {}\n",
                capitalize(cons.name), params, parentType));
    }

    public String getTopLevelCode() {
        String code = topLevelCode.toString();
        clear();
        return code;
    }

    public void clear() {
        topLevelCode.setLength(0);
    }

    // Helpers
    private String mapType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "float" -> "float";
            case "string" -> "String";
            case "any" -> "Object";
            default -> capitalize(t);
        };
    }

    private String joinConstructorNames(List<ConstructorDef> list) {
        return String.join(", ", list.stream().map(c -> capitalize(c.name)).toList());
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // Clase auxiliar para cada constructor
    public static class ConstructorDef {
        public final String name;
        public final List<Arg> args = new ArrayList<>();

        public ConstructorDef(String name) {
            this.name = name;
        }

        public void addArg(String name, String type) {
            args.add(new Arg(name, type));
        }

        public static class Arg {
            public final String name, type;
            public Arg(String name, String type) {
                this.name = name;
                this.type = type;
            }
        }
    }
}
