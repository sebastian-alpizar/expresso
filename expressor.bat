@echo off
setlocal enabledelayedexpansion

:: ===============================
:: Configuración de rutas y nombres
:: ===============================
set ROOT_DIR=%~dp0
set ANTLR_JAR=%ROOT_DIR%lib\antlr-4.13.2-complete.jar
set GRAMMAR=%ROOT_DIR%grammar\Expr.g4
:: Carpeta para archivos generados por ANTLR:
set "PARSER_OUT=%ROOT_DIR%generated"
set TRANSPILER_SRC=%ROOT_DIR%src
set TRANSPILER_BIN=%ROOT_DIR%bin
set EXPRESSOR_MAIN=Main

echo PARSER_OUT=%PARSER_OUT%

:: ===============================
:: Verificar que el jar de ANTLR existe
:: ===============================
if not exist "%ANTLR_JAR%" (
    echo [ERROR] No se encuentra %ANTLR_JAR%
    exit /b 1
)

:: ===============================
:: Procesamiento de comandos
:: ===============================
if "%1"=="transpile" (
    goto :transpile
) else if "%1"=="build" (
    goto :build
) else if "%1"=="run" (
    goto :run
) else (
    echo Uso: expressor ^(transpile^|build^|run^) --out ^<outDir^> ^<file.expresso^>
    echo.
    echo Comandos:
    echo   transpile - Genera codigo Java desde .expresso
    echo   build     - Compila el codigo Java generado
    echo   run       - Ejecuta el programa compilado
    exit /b 1
)

:transpile
    :: ===============================
    :: [1] Generar parser ANTLR solo si es necesario
    :: ===============================
    set REGEN_PARSER=0

    if not exist "%PARSER_OUT%" (
        mkdir "%PARSER_OUT%"
        set REGEN_PARSER=1
    ) else (
        if not exist "%PARSER_OUT%\ExprParser.java" (
            set REGEN_PARSER=1
        ) else (
            for %%G in ("%GRAMMAR%") do set "GRAMMAR_TIME=%%~tG"
            for %%P in ("%PARSER_OUT%\ExprParser.java") do set "PARSER_TIME=%%~tP"
            if "!GRAMMAR_TIME!" GTR "!PARSER_TIME!" (
                set REGEN_PARSER=1
            )
        )
    )

    if "!REGEN_PARSER!"=="1" (
        echo [1/3] Generando parser ANTLR...
        java -jar "%ANTLR_JAR%" -Dlanguage=Java -visitor -o "%PARSER_OUT%" "%GRAMMAR%"
        if errorlevel 1 (
            echo [ERROR] Error generando el parser.
            exit /b 1
        )
    ) else (
        echo [1/3] Parser actualizado, no se regenera.
    )

    :: ===============================
    :: [2] Compilar transpilador solo si es necesario
    :: ===============================
    if not exist "%TRANSPILER_BIN%" mkdir "%TRANSPILER_BIN%"

    set NEED_COMPILE=0
    if not exist "%TRANSPILER_BIN%\Main.class" (
        set NEED_COMPILE=1
    ) else (
        for %%S in ("%TRANSPILER_SRC%\*.java") do (
            for %%C in ("%TRANSPILER_BIN%\Main.class") do (
                if "%%~tS" GTR "%%~tC" set NEED_COMPILE=1
            )
        )
    )

    if "!NEED_COMPILE!"=="1" (
        echo [2/3] Compilando transpilador...
        :: Considerar las comillas, error (Daniel)
        javac -cp %ANTLR_JAR%;. -d %TRANSPILER_BIN% %PARSER_OUT%\*.java %TRANSPILER_SRC%\*.java
        if errorlevel 1 (
            echo [ERROR] Error compilando el transpilador.
            exit /b 1
        )
    ) else (
        echo [2/3] Transpilador ya compilado, sin cambios.
    )

    :: ===============================
    :: [3] Ejecutar transpilacion
    :: ===============================
    echo [3/3] Ejecutando transpilacion...

    :: Pasar el directorio de salida al programa Java
    java -cp "%ANTLR_JAR%;%TRANSPILER_BIN%;." %EXPRESSOR_MAIN% %*
    goto :eof

:build
    call :transpile %*
    if errorlevel 1 exit /b 1

    set "SOURCE_FILE=%~4"
    for %%I in ("%SOURCE_FILE%") do set "BASE_NAME=%%~nI"

    set "OUT_DIR=%~3"
    set "JAVA_FILE=%OUT_DIR%\!BASE_NAME!.java"
    
    echo Compilando !JAVA_FILE!...
    javac -cp "." -d "%OUT_DIR%" "!JAVA_FILE!"
    if errorlevel 1 (
        echo [ERROR] Error compilando !JAVA_FILE!
        exit /b 1
    )
    echo Compilacion exitosa: %OUT_DIR%\!BASE_NAME!.class
    goto :eof

:run
    call :build %*
    if errorlevel 1 exit /b 1

    set "SOURCE_FILE=%~4"
    for %%I in ("%SOURCE_FILE%") do set "BASE_NAME=%%~nI"
    set "OUT_DIR=%~3"

    echo Ejecutando !BASE_NAME!...
    java -cp "%OUT_DIR%;." !BASE_NAME!
    goto :eof

:eof
endlocal

@REM expressor build --out dir archivo.expresso
@REM     │
@REM     ├── [1] Generar parser ANTLR (si es necesario)
@REM     ├── [2] Compilar TODOS los .java en src/ (incluye TyperVisitor)  
@REM     ├── [3] Ejecutar Main con typing + codegen
@REM     │     │
@REM     │     ├── FASE TYPING: TyperVisitor visita AST
@REM     │     │   ├── Si hay errores → termina con código 1
@REM     │     │   └── Si no hay errores → genera .typings y continúa
@REM     │     │
@REM     │     └── FASE CODEGEN: CodeGenVisitor genera .java
@REM     │
@REM     └── [4] Compilar el .java generado