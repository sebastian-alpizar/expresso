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

Expresso es un minilenguaje diseñado para aprender cómo los lenguajes de programación modernos logran expresar ideas complejas con una sintaxis concisa. El proyecto implementa un transpilador que convierte código Expresso (estilo funcional con tipos algebraicos) a Java ejecutable.

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

#### 1. Java Development Kit (JDK) 23+

**Descarga e Instalación en Windows:**
- Descargar desde [Oracle JDK 23](https://www.oracle.com/java/technologies/downloads/)
- **Importante:** Debe ser JDK 23 o superior, versiones anteriores NO funcionarán

**Instalación paso a paso:**
1. Descargar el instalador Windows x64 (`.msi`)
2. Ejecutar como administrador
3. Seguir el asistente de instalación
4. Anotar la ruta de instalación (generalmente `C:\Program Files\Java\jdk-23`)

#### 2. Apache Maven 3.8+

**Descarga e Instalación en Windows:**
- Descargar desde: [Apache Maven](https://maven.apache.org/download.cgi)
- Descargar el archivo `Binary zip archive`

**Instalación paso a paso:**
1. Descargar `apache-maven-x.x.x-bin.zip`
2. Extraer en `C:\Program Files\Apache\maven\`
3. Anotar la ruta completa (ej: `C:\Program Files\Apache\maven\apache-maven-3.9.4`)

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

3. **Crear MAVEN_HOME:**
    - En "Variables del sistema" clic en "Nueva..."
    - Nombre de variable: `MAVEN_HOME`
    - Valor de variable: `C:\Program Files\Apache\maven\apache-maven-3.9.4`
    - Clic "Aceptar"

4. **Actualizar PATH:**
    - En "Variables del sistema" seleccionar `Path` y clic "Editar..."
    - Clic "Nueva" y agregar: `%JAVA_HOME%\bin`
    - Clic "Nueva" y agregar: `%MAVEN_HOME%\bin`
    - Clic "Aceptar" en todas las ventanas

5. **Aplicar Cambios:**
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

C:\> mvn --version
Apache Maven 3.9.4
Maven home: C:\Program Files\Apache\maven\apache-maven-3.9.4
Java version: 23.0.1, vendor: Oracle Corporation
Java home: C:\Program Files\Java\jdk-23
```

### Solución de Problemas de Instalación

**Si aparece error "'java' no se reconoce como comando":**
- Verificar que JAVA_HOME apunte a la carpeta correcta del JDK
- Verificar que `%JAVA_HOME%\bin` esté en PATH
- Cerrar y abrir nueva terminal

**Si aparece error "'mvn' no se reconoce como comando":**
- Verificar que MAVEN_HOME apunte a la carpeta correcta de Maven
- Verificar que `%MAVEN_HOME%\bin` esté en PATH
- Verificar que Java funcione correctamente primero


## Uso del CLI
Se deben correr los siguientes comandos para crear los ejecutables y cargar los comandos que trabajan con JPACKAGE.

Desde /expresso se abre CMD y se corre:
```cmd
mvn clean package
# Esto limpia la carpeta target y compila el código fuente
```
Desde /expresso/dist/expressor se corre:
```cmd
jpackage ^
  --name expressor ^
  --input target ^
  --main-jar expresso-1.0-SNAPSHOT-jar-with-dependencies.jar ^
  --main-class cr.ac.una.Expressor ^
  --type app-image ^
  --win-console ^
  --dest dist
# Esto convierte la aplicación Java en un ejecutable nativo de Windows
```

Esto permite pasar de:
```cmd
java -cp target\classes cr.ac.una.Expressor ...
```
Al comando propio de Expresso:
```cmd
expressor transpile archivo.expresso
```


### Transpilar
Convierte código Expresso a Java:

```cmd
expressor transpile --out output archivo.expresso

# Genera: archivo.java, la opción "--verbose" al final permite ver los pasos
```

### Compilar
Transpila y compila a bytecode Java:

```cmd
expressor build --out output archivo.expresso

# Genera: archivo.java y archivo.class
```

### Ejecutar
Transpila, compila y ejecuta el programa:

```cmd
expressor run --out output archivo.expresso

# Ejecuta el programa directamente
```


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
