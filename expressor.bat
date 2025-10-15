@echo off
setlocal enabledelayedexpansion

:: ===============================
:: Configuración de rutas y nombres
:: ===============================
set ROOT_DIR=%~dp0
set ANTLR_JAR=%ROOT_DIR%lib\antlr-4.13.2-complete.jar
set GRAMMAR=%ROOT_DIR%grammar\Expr.g4
set PARSER_OUT=%ROOT_DIR%generated
set TRANSPILER_SRC=%ROOT_DIR%src
set TRANSPILER_BIN=%ROOT_DIR%bin
set EXPRESSOR_MAIN=Main

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
        echo ===============================
        echo [1/3] Generando parser ANTLR...
        echo ===============================
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
        echo ===============================
        echo [2/3] Compilando transpilador...
        echo ===============================
        javac -cp "%ANTLR_JAR%;." -d "%TRANSPILER_BIN%" "%PARSER_OUT%\*.java" "%TRANSPILER_SRC%\*.java"
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
    echo ===============================
    echo [3/3] Ejecutando transpilacion...
    echo ===============================

    java -cp "%ANTLR_JAR%;%TRANSPILER_BIN%;." %EXPRESSOR_MAIN% %*
    goto :eof

:build
    call :transpile %*
    if errorlevel 1 exit /b 1

    set "SOURCE_FILE=%~4"
    for %%I in ("%SOURCE_FILE%") do set "BASE_NAME=%%~nI"

    set "JAVA_FILE=%~3\!BASE_NAME!.java"
    echo Compilando !JAVA_FILE!...
    javac -d "%TRANSPILER_BIN%" "!JAVA_FILE!"
    if errorlevel 1 (
        echo [ERROR] Error compilando !JAVA_FILE!
        exit /b 1
    )
    echo Compilacion exitosa: %TRANSPILER_BIN%\!BASE_NAME!.class
    goto :eof

:run
    call :build %*
    if errorlevel 1 exit /b 1

    set "SOURCE_FILE=%~4"
    for %%I in ("%SOURCE_FILE%") do set "BASE_NAME=%%~nI"

    echo Ejecutando !BASE_NAME!...
    java -cp "%TRANSPILER_BIN%;." !BASE_NAME!
    goto :eof

:eof
endlocal
