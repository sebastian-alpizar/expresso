import java.util.*;

public class ScopeManager {
    private static class Scope {
        Set<String> names = new HashSet<>();
        Map<String, String> types = new HashMap<>();
    }

    Map<String, String> safeNameMap = new HashMap<>();
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int lambdaCounter = 0;

    public void enterScope() {
        scopes.push(new Scope());
    }

    public void exitScope() {
        if (!scopes.isEmpty()) scopes.pop();
    }

    public void declareVar(String name, String type) {
        if (!scopes.isEmpty()) {
            Scope current = scopes.peek();
            current.names.add(name);
            if (type != null) current.types.put(name, type);
        }
    }

    public void declareVar(String name) {
        declareVar(name, null);
    }

    public boolean isVarDeclared(String name) {
        for (Scope s : scopes) {
            if (s.names.contains(name)) return true;
        }
        return false;
    }

    public String getVarType(String name) {
        for (Scope s : scopes) {
            if (s.types.containsKey(name)) return s.types.get(name);
        }
        return null;
    }

    public String getSafeName(String original) {
        // si ya existe un mapeo, retornarlo (no crear otro)
        if (safeNameMap.containsKey(original)) {
            return safeNameMap.get(original);
        }

        if (isVarDeclared(original)) {
            lambdaCounter++;
            String safe = original + "_" + lambdaCounter;
            safeNameMap.put(original, safe);
            return safe;
        }
        return original;
    }

    public String resolveSafeName(String original) {
        return safeNameMap.getOrDefault(original, original);
    }

    public void clear() {
        scopes.clear();
    }
}
