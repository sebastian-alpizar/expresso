public class CodeGen {
    public static String header(String className) {
        String capitalizedClassName = capitalizeFirst(className);
        
        return "import java.util.function.*;\n" +
                "public class " + capitalizedClassName + " {\n" +
                "    public static int pow(int x, int e) { return (int)Math.pow(x, e); }\n" +
                "    public static void print(Object arg) { System.out.println(arg); }\n" +
                "    public static void main(String... args) {\n";
    }
    
    public static String footer() {
        return "    }\n" +
                "}\n";
    }
    
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}