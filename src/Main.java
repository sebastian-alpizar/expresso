import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 4 || !"--out".equals(args[1])) {
            System.err.println("Usage: expressor (transpile|build|run) --out <outDir> <file.expresso>");
            System.exit(1);
        }
        
        // Después de validar los argumentos básicos
        String command = args[0];
        if (!"transpile".equals(command) && !"build".equals(command) && !"run".equals(command)) {
            System.err.println("Error: Comando no válido. Use: transpile, build o run");
            System.exit(1);
        }

        String outDir = args[2];
        String inPath = args[3];

        // Validar extensión del archivo
        if (!inPath.endsWith(".expresso")) {
            System.err.println("Error: El archivo debe tener extensión .expresso");
            System.exit(1);
        }

        // Leer archivo de entrada
        CharStream input = CharStreams.fromFileName(inPath);
        ExprLexer lexer = new ExprLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ExprParser parser = new ExprParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new DiagnosticErrorListener());

        ParseTree tree = parser.program();
        
         // ===============================
        // FASE DE TYPING
        // ===============================
        String baseName = Paths.get(inPath).getFileName().toString();
        baseName = baseName.substring(0, baseName.length() - ".expresso".length());

        TyperVisitor typer = new TyperVisitor(baseName + ".expresso");
        typer.visit(tree);

        if (typer.hasErrors()) {
            typer.printErrors();
            System.err.println("Build failed due to typing errors");
            System.exit(1);
        }

        // Generar archivo .typings
        Path typingsFile = Paths.get(outDir).resolve(baseName + ".expresso.typings");
        typer.generateTypingsFile(typingsFile.toString());
        System.out.println("Generated: " + typingsFile.toAbsolutePath());

        // ===============================
        // FASE DE GENERACIÓN DE CÓDIGO
        // ===============================
        CodeGenVisitor gen = new CodeGenVisitor();
        gen.visit(tree);

        // Usar la información del visitor para determinar imports
        String javaSource = CodeGen.header(baseName) + 
                            gen.getMainBody() + 
                            CodeGen.footer();

        // Crear directorio de salida si no existe
        Path outDirectory = Paths.get(outDir);
        if (!Files.exists(outDirectory)) {
            Files.createDirectories(outDirectory);
        }

        // Escribir archivo Java generado
        Path outFile = outDirectory.resolve(baseName + ".java");
        Files.writeString(outFile, javaSource);
        
        System.out.println("Generated: " + outFile.toAbsolutePath());
        
        // Para los comandos build y run, Main solo hace transpilación
        // La compilación y ejecución se manejan en el .bat
    }
}