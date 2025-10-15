public class CodeGen {
    
    public static String header(String className) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("import java.util.function.*;\n");
        sb.append("\n");
        sb.append("public class ").append(className).append(" {\n\n");
        
        return sb.toString();
    }
    
    public static String footer() {
        return "}\n";
    }
}