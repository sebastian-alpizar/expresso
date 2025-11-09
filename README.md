# Expressor - Transpilador de .expresso a Java 🚀

[![Java](https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white)](https://www.java.com/es/)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.13.2-9BC53D?style=for-the-badge&logo=antlr&logoColor=white)](https://www.antlr.org/)

### Integrantes 
1) Nombre: Isella Ríos Sanabria ID: 118920882 correo: isella.rios.sanabria@est.una.ac.cr
2) Nombre:  Sebastián Alpízar Porras ID: 118240079 correo: sebastian.alpizar.porras@est.una.ac.cr  
3) Nombre: Gian Carlo Arenas Valverde ID: 119430111 correo: gian.arenas.valverde@est.una.ac.cr
4) Nombre: Daniel Ramírez Calvo ID: 118560912 correo: daniel.ramirez.calvo@est.una.ac.cr
5) Nombre: Kaleb Baruc Rojas Gómez ID:  402500850 correo: kaleb.rojas.gomez@est.una.ac.cr


HORARIO: 1 pm  
Grupo: 03-1pm

## 📋 Descripción del Proyecto

Expresso es un minilenguaje educativo inspirado en la programación funcional. Su transpilador convierte código .expresso en Java usando ANTLR4, permitiendo ejecutar programas con lambdas, operadores lógicos, y expresiones de orden superior.

### Características Principales
- Sintaxis funcional: Tipos algebraicos, funciones y pattern matching
- Transpilación a Java: Genera código Java 23+ moderno y legible
- CLI integrado: Herramienta de línea de comandos para transpilar, compilar y ejecutar
- Parser ANTLR4: Análisis sintáctico robusto y profesional

## ✅ Prerrequisitos

### 1. Instalar Java JDK 23+ Descargar desde https://www.oracle.com/java/technologies/downloads/

Verificar instalación: 
``` bash
java --version javac --version
```

Salida esperada: 
```bash
java 23.0.1 2024-10-15 javac 23.0.1
```

### 2. Configuración de Variables de Entorno (Windows)

1. **Abrir Configuración de Variables:**
   - Presionar Win + R, escribir sysdm.cpl y presionar Enter
   - Ir a la pestaña "Avanzado" → Clic en "Variables de entorno"
  
2. **Crear JAVA_HOME:**
   - En "Variables del sistema" clic en "Nueva...
   - Nombre de variable: JAVA_HOME
   - Valor de variable: C:\Program Files\Java\jdk-23 (ajustar según tu instalación)
   - Clic "Aceptar"
  
3. **Actualizar PATH:**
   - En "Variables del sistema" seleccionar Path y clic "Editar..."
   - Clic "Nueva" y agregar: %JAVA_HOME%\bin
   - Clic "Aceptar" en todas las ventanas
  
4. **Aplicar Cambios:**
   - Cerrar todas las ventanas con "Aceptar"
   - Importante: Cerrar y abrir nuevamente la terminal/cmd

## ⚙️ Comandos Disponibles

🔄 `transpile`

Genera código Java desde un archivo .expresso
- Crea el archivo .java equivalente a partir del código .expresso
- Realiza verificación de tipos estática
- Genera el código Java listo para compilar
- Salida: Archivo .java en el directorio especificado

🔨 `build`

Compila el código Java generado
- Ejecuta automáticamente el comando transpile primero
- Compila el archivo .java generado
- Crea el archivo .class (bytecode Java)
- Salida: Archivo .class listo para ejecutar

▶️ `run`

Ejecuta el programa compilado
- Ejecuta automáticamente los comandos transpile y build
- Ejecuta el programa Java resultante
- Salida: Ejecución del programa en consola


## 🛠️ Explicación del Archivo .bat

El archivo expressor.bat es el corazón del proyecto y maneja automáticamente todo el proceso:

### 🔧 Funcionalidades Automatizadas

#### 1. Gestión de ANTLR Automática

- Usa la librería ANTLR 4.13.2 incluida en lib/antlr-4.13.2-complete.jar
- Genera el parser automáticamente cuando es necesario
- Verifica si hay cambios en la gramática para regenerar solo cuando es requerido

#### 2. Compilación Inteligente

- Compila el transpilador solo si hay cambios en el código fuente
- Maneja las dependencias de classpath automáticamente
- Detecta archivos modificados para evitar recompilaciones innecesarias

#### 3. Proceso de Transpilación en 3 Fases

## 📊 Proceso Detallado del Comando `transpile`

``` bash
[1/3] Generando parser ANTLR...
    ↓ (Solo si es necesario)
[2/3] Compilando transpilador...
    ↓ (Solo si hay cambios)
[3/3] Ejecutando transpilación...
    ↓
FASE TYPING: TyperVisitor visita AST
    ├── Si hay errores → termina con código 1
    └── Si no hay errores → genera .typings y continúa
    ↓
FASE CODEGEN: CodeGenVisitor genera .java
```
## 🔄 Lógica de Reconstrucción Inteligente

El script incluye detección inteligente de cambios:

- Parser ANTLR: Se regenera solo si la gramática (Expr.g4) es más reciente que los archivos del parser existentes
- Transpilador: Se recompila solo si los archivos .java en src/ son más recientes que las clases compiladas
- Evita trabajo redundante: No regenera/recompila si no hay cambios

## 📁 Estructura del Proyecto

``` bash
├── 📁 bin/                                # Clases compiladas del transpilador (automático)
├── 📁 grammar
│   └── 📄 Expr.g4                         # Gramática del lenguaje .expresso
├── 📁 generated/                          # Parser generado por ANTLR (automático)
├── 📁 lib                                 # Librería ANTLR incluida
│   └── 📄 antlr-4.13.2-complete.jar
├── 📁 src                                 # Código fuente del transpilador
│   ├── ☕ CodeGen.java
│   ├── ☕ CodeGenVisitor.java             # Clase principal orquestadora
│   ├── ☕ DataTypeGenerator.java          # Genera estructuras de datos (Data)
│   ├── ☕ DataVisitor.java                # Visita y procesa específicamente las declaraciones data
│   ├── ☕ Main.java
│   ├── ☕ MatchVisitor.java               # Implementa el manejo de match o patrones (pattern matching)
│   ├── ☕ ScopeManager.java               # Maneja los alcances de variables y funciones (scope)
│   └── ☕ TyperVisitor.java               # Verificación de tipos
├── 📁 test
│   ├── 📁 Earth
│   │   ├── 📄 HelloWorld0.expresso
│   │   ├── 📄 HelloWorld1.expresso
│   │   └── 📄 HelloWorld2.expresso
│   ├── 📁 Mars
│   │   └── 📄 HelloWorldMars0.expresso
│   └── 📁 Moon
│       └── 📄 HelloWorldMoon0.expresso
├── ⚙️ .gitignore
├── 📝 README.md
└── 📄 expressor.bat                       # Script principal (este archivo)
```

## 🚀 Uso Rápido

El transpilador se ejecuta desde la línea de comandos utilizando el *script* `expressor.bat`.

| Comando | Descripción |
| :--- | :--- |
| `expressor transpile --out directorio_salida archivo.expresso` | **Transpila** a código Java (`.java`). |
| `expressor build --out directorio_salida archivo.expresso` | Transpila y **Compila** a bytecode Java (`.class`). |
| `expressor run --out directorio_salida archivo.expresso` | Transpila, Compila y **Ejecuta** el programa. |

## 💡 Ejemplo de Uso Completo

```bash
# Transpilar un archivo ejemplo.expresso
expressor transpile --out ./salida ejemplo.expresso

# Esto generará: ./salida/ejemplo.java

# Compilar el Java generado
expressor build --out ./salida ejemplo.expresso

# Esto generará: ./salida/ejemplo.class

# Ejecutar el programa
expressor run --out ./salida ejemplo.expresso

# Esto ejecutará el programa completo
