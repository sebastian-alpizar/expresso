# Expresso - Minilenguaje Funcional
// Expresso: Un minilenguaje muy concentrado

// Paradigmas de Programación

// 1) Nombre: Alexander Javier Vega Méndez ID: 402620735 correo: alexander.vega.mendez@est.una.ac.cr HORARIO: 1 pm [Coordinador]

// 2) Nombre: Luis Diego Fallas Brizuela ID: 118720684 correo: luis.fallas.brizuela@est.una.ac.cr HORARIO: 1 pm

// 3) Nombre:  Sebastián Alpízar Porras ID: 118240079 correo: sebastian.alpizar.porras@est.una.ac.cr HORARIO: 1 pm

// 4) Nombre: César Hernández Orozco ID: 118520198 correo: cesar.hernandez.orozco@est.una.ac.cr HORARIO: 1 pm

// 5) Nombre: Daniel Solano Ávila ID: 119060141 correo: daniel.solano.avila@est.una.ac.cr HORARIO: 1 pm

// Grupo: 04-1pm
## Descripción

Expresso es un minilenguaje educativo inspirado en la programación funcional.
Su transpilador convierte código `.expresso` en Java usando ANTLR4, permitiendo ejecutar programas con lambdas, operadores lógicos, y expresiones de orden superior.

## Estructura del Proyecto

expresso/
│
├── expressor.bat                # Script CLI principal
├── lib/
│   └── antlr-4.13.2-complete.jar
├── grammar/
│   └── Expr.g4                  # Gramática ANTLR
├── generated/                   # Archivos generados por ANTLR
├── src/                         # Código fuente del transpilador
│   ├── Main.java
│   ├── CodeGen.java
│   └── CodeGenVisitor.java
├── bin/                         # Clases compiladas del transpilador
└── examples/
    ├── HelloWorld0.expresso
    ├── HelloWorld1.expresso

### Características Principales

- **Sintaxis funcional**: Tipos algebraicos, funciones y pattern matching
- **Transpilación a Java**: Genera código Java 23+ moderno y legible
- **CLI integrado**: Herramienta de línea de comandos para transpilar, compilar y ejecutar
- **Parser ANTLR4**: Análisis sintáctico robusto y profesional

### Ejemplo de Código

```expresso
// Números naturales
data nat = {
    Zero,
    S(nat)
}

fun sum(x:nat, y:nat) = match x with
    Zero -> y
    S(z) -> S(sum(z, y))
```

Se transpila a Java como:

```java
sealed interface Nat permits Zero, S {}
record Zero() implements Nat {}
record S(Nat pred) implements Nat {}

static Nat sum(Nat x, Nat y) {
    return switch (x) {
        case Zero z -> y;
        case S(var pred) -> new S(sum(pred, y));
    };
}
```

## Prerrequisitos

### Software Requerido

1. Instalar Java JDK 23+
   Descargar desde https://www.oracle.com/java/technologies/downloads/

Verificar instalación:
java --version
javac --version

Salida esperada:
java 23.0.1 2024-10-15
javac 23.0.1

---

### Configuración de Variables de Entorno (Windows)

#### Configurar Variables del Sistema

1. **Abrir Configuración de Variables:**
    - Presionar `Win + R`, escribir `sysdm.cpl` y presionar Enter
    - Ir a la pestaña "Avanzado" → Clic en "Variables de entorno"

2. **Crear JAVA_HOME:**
    - En "Variables del sistema" clic en "Nueva..."
    - Nombre de variable: `JAVA_HOME`
    - Valor de variable: `C:\Program Files\Java\jdk-23` (ajustar según tu instalación)
    - Clic "Aceptar"

3. **Actualizar PATH:**
    - En "Variables del sistema" seleccionar `Path` y clic "Editar..."
    - Clic "Nueva" y agregar: `%JAVA_HOME%\bin`
    - Clic "Aceptar" en todas las ventanas

4. **Aplicar Cambios:**
    - Cerrar todas las ventanas con "Aceptar"
    - **Importante:** Cerrar y abrir nuevamente la terminal/cmd

### Verificar Instalación

**Abrir cmd (Command Prompt) como administrador y ejecutar:**

```cmd
java --version
javac --version
mvn --version
```

**Salidas esperadas:**

```cmd
C:\> java --version
java 23.0.1 2024-10-15
Java(TM) SE Runtime Environment (build 23.0.1+11-39)
Java HotSpot(TM) 64-Bit Server VM (build 23.0.1+11-39, mixed mode, sharing)

```

### Solución de Problemas de Instalación

**Si aparece error "'java' no se reconoce como comando":**
- Verificar que JAVA_HOME apunte a la carpeta correcta del JDK
- Verificar que `%JAVA_HOME%\bin` esté en PATH
- Cerrar y abrir nueva terminal


## 🖥️ Uso desde la línea de comandos (Windows CMD)

El archivo `expressor.bat` permite realizar todas las acciones del compilador.

---

### 1. Transpilar

expressor transpile --out output examples\HelloWorld0.expresso

---

### 2. Compilar

expressor build --out output examples\HelloWorld0.expresso

---

### 3. Ejecutar

expressor run --out output examples\HelloWorld0.expresso

---

## Ejemplo de Código Expresso

examples/HelloWorld1.expresso
---------------------------------
let a = 2
let b = 3
let c = 4

let expr1 = a + b ** c
print(expr1)

let expr2 = a ** b ** c
print(expr2)

let complexLambda = x -> y -> x + y * 2
let partial = complexLambda(5)
print(partial(3))

let score = 85
let grade = score >= 90 ? "A" : score >= 80 ? "B" : "C"
print(grade)

Salida Esperada:
83
2417851639229258349412352
11
B

---

examples/HelloWorld0.expresso
---------------------------------
let add = (x, y) -> x + y
print(add(5, 3))

let makeAdder = x -> (y -> x + y)
let add5 = makeAdder(5)
print(add5(10))

Salida:
8
15

---

## Componentes del Sistema

### 1. Parser (ANTLR4)
- Análisis léxico y sintáctico del código Expresso
- Generación del Abstract Syntax Tree (AST)
- Manejo de errores sintácticos

### 2. Minityper (Análisis Semántico)
- Verificación de tipos
- Validación de patrones de matching
- Detección de errores semánticos

### 3. Generador de Código
- Transpilación de AST a código Java
- Optimizaciones básicas
- Preservación de semántica funcional

### 4. CLI Tool
- Interfaz unificada para todas las operaciones
- Integración con herramientas Java estándar
- Manejo de archivos y directorios

## Desarrollo

### Configuración del Entorno de Desarrollo

1. **IDE Recomendado**: IntelliJ IDEA o Eclipse con soporte Maven
2. **Plugins útiles**:
    - ANTLR4 Plugin para desarrollo de gramáticas
    - Maven Integration

## Contribución

### Proceso de Desarrollo

1. Crear rama para nueva funcionalidad
2. Implementar cambios con tests
3. Verificar que todos los tests pasan
4. Hacer commit con mensajes descriptivos
5. Crear pull request para revisión

### Estándares de Código

- Seguir convenciones Java estándar
- Documentar funciones públicas con Javadoc
- Mantener cobertura de tests > 80%
- Usar nombres descriptivos para variables y métodos

## Apoyo de IA
Se adjuntan conversaciones con ChatGPT que sirvieron de apoyo para el desarrollo del proyecto:


## Cronograma del Proyecto

| Entregable | Valor | Fecha Límite | Descripción |
|------------|-------|--------------|-------------|
| **Inicial** | 5% | 31/08/2025 12:00 | CLI básico con mocks de transpilación |
| **Mediano** | 15% | 05/10/2025 12:00 | Parser ANTLR4 + Minityper + Tests |
| **Final** | 20% | 16/11/2025 12:00 | Sistema completo con generación de código |

## Evaluación

### Criterios de Calificación

- **Función (65%)**: Funcionalidad correcta, casos de prueba, capacidad de defensa
- **Paradigma (35%)**: Uso apropiado del paradigma, calidad del código, estilo DRY

### Requisitos de Entrega

- Código fuente completo en repositorio Git
- Documentación técnica actualizada
- Batería completa de casos de prueba
- Demo funcional en clase

### Información del Curso

- **Profesor**: Carlos Loría-Sáenz (loriacarlos@gmail.com)
- **Curso**: EIF400-II-2025 - Paradigmas de Programación
- **Institución**: Escuela de Informática, UNA

## Licencia

Proyecto académico para fines educativos únicamente.

---

**Nota**: Este es un proyecto ágil sujeto a refinamientos y evoluciones según el avance del curso. Consultar regularmente por actualizaciones y cambios en los requerimientos.
