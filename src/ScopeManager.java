import java.util.*;

// Clase que maneja los ámbitos (scopes) de variables y sus tipos.
// Permite declarar variables, verificar si existen, y evitar colisiones de nombres.
public class ScopeManager {

    // ==========================================================
    //  Clase interna que representa un solo ámbito
    // ==========================================================
    private static class Scope {
        // Conjunto de nombres de variables declaradas en este ámbito
        Set<String> names = new HashSet<>();

        // Mapa que asocia cada variable con su tipo
        Map<String, String> types = new HashMap<>();
    }

    // ==========================================================
    //  Atributos principales del ScopeManager
    // ==========================================================

    // Mapa usado para evitar colisiones de nombres (ej: lambdas anidadas)
    // Guarda nombres "seguros" generados cuando hay conflictos.
    Map<String, String> safeNameMap = new HashMap<>();

    // Pila (stack) de ámbitos activos
    private final Deque<Scope> scopes = new ArrayDeque<>();

    // Contador usado para generar nombres únicos (ej: x_1, x_2, ...)
    private int lambdaCounter = 0;

    // ==========================================================
    //  Manejo de entrada y salida de scopes
    // ==========================================================

    // Crea un nuevo ámbito y lo agrega a la pila
    public void enterScope() {
        scopes.push(new Scope());
    }

    // Sale del ámbito actual (lo elimina de la pila)
    public void exitScope() {
        if (!scopes.isEmpty()) scopes.pop();
    }

    // ==========================================================
    //  Declaración de variables
    // ==========================================================

    // Declara una variable con su tipo en el ámbito actual
    public void declareVar(String name, String type) {
        if (!scopes.isEmpty()) {
            Scope current = scopes.peek();
            current.names.add(name); // registra el nombre
            if (type != null) current.types.put(name, type); // y el tipo si lo hay
        }
    }

    // Sobrecarga para declarar una variable sin especificar tipo
    public void declareVar(String name) {
        declareVar(name, null);
    }

    // ==========================================================
    //  Verificación de variables existentes
    // ==========================================================

    // Comprueba si una variable ya fue declarada en algún ámbito activo
    public boolean isVarDeclared(String name) {
        for (Scope s : scopes) {
            if (s.names.contains(name)) return true;
        }
        return false;
    }

    // Obtiene el tipo asociado a una variable, buscando de arriba hacia abajo en la pila
    public String getVarType(String name) {
        for (Scope s : scopes) {
            if (s.types.containsKey(name)) return s.types.get(name);
        }
        return null;
    }

    // ==========================================================
    //  Generación de nombres "seguros" (para lambdas o duplicados)
    // ==========================================================

    // Devuelve un nombre único si la variable ya existe en el scope
    public String getSafeName(String original) {
        // Si ya existe un nombre seguro asignado, lo devuelve
        if (safeNameMap.containsKey(original)) {
            return safeNameMap.get(original);
        }

        // Si ya existe una variable con ese nombre, genera una nueva versión
        if (isVarDeclared(original)) {
            lambdaCounter++;
            String safe = original + "_" + lambdaCounter; // ej: "x_1", "x_2"
            safeNameMap.put(original, safe);
            return safe;
        }

        // Si no hay conflicto, devuelve el nombre original
        return original;
    }

    // Devuelve el nombre seguro si existe, o el original en caso contrario
    public String resolveSafeName(String original) {
        return safeNameMap.getOrDefault(original, original);
    }

    // ==========================================================
    //  Limpieza y utilidades
    // ==========================================================

    // Limpia todos los ámbitos (por ejemplo, al reiniciar la ejecución)
    public void clear() {
        scopes.clear();
    }

    // Devuelve un conjunto con todas las variables declaradas en todos los scopes
    public Set<String> getDeclaredVars() {
        Set<String> allVars = new HashSet<>();
        for (Scope s : scopes) {
            allVars.addAll(s.names);
        }
        return allVars;
    }
}
