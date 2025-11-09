/**
 * @author Daniel Ramirez
 * @author Isella Rios
 * @author Giancarlo Arenas
 * @author Kaleb Rojas
 * @author Sebastian Alpizar
 */


// TyperRunner.java
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class TyperRunner {

    /**
     * Ejecuta el Typer sobre un fichero .expresso
     * @param inputPath ruta del .expresso
     * @param outputDir carpeta donde colocar el .typings (puede ser same dir)
     * @throws IOException en fallo de I/O
     */
    public static void run(String inputPath, String outputDir) throws IOException {
        CharStream cs = CharStreams.fromFileName(inputPath);
        ExprLexer lexer = new ExprLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ExprParser parser = new ExprParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new DiagnosticErrorListener());
        ExprParser.ProgramContext program = parser.program();

        String fname = Paths.get(inputPath).getFileName().toString();
        TyperVisitor typer = new TyperVisitor(fname);
        typer.visit(program);

        if (typer.hasErrors()) {
            typer.printErrors();
            throw new RuntimeException("Typer detectó errores. Abortando la fase de build.");
        }

        typer.generateTypingsFile(outputDir);
    }

    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: TyperRunner <file.expresso> [outputDir]");
            System.exit(1);
        }
        String file = args[0];
        String out = args.length >= 2 ? args[1] : new File(file).getParent();
        run(file, out == null ? "." : out);
    }
}
