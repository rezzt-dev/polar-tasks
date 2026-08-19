# Guía de Contribución para Polar

¡Gracias por tu interés en contribuir a **Polar**! Como un proyecto de código abierto, dependemos de contribuciones activas regulares por parte de la comunidad para encontrar y arreglar todo bug incipiente y sugerir grandes e innovadoras características.

A continuación describimos las reglas para aportar amistosamente:

## 1. Empezando como desarrollador (Configuración)

Antes de aportar tu primer parche, querrás asegurarte de tener el siguiente entorno de desarrollo configurado bajo las recomendaciones que empleamos permanentemente en Polar:

### Requisitos Mínimos:
*   Android Studio Flamingo o su posterior superior ("Iguana, Jellyfish, etc.")
*   Soporte para versión de Compilación OpenJDK 17.
*   Kotlin Standard 1.9+.
*   Soporte base compilando para la API Meta `compileSdk 34` SDK+.

### Clonado de entorno:
1. Asegúrate de presionar el botón de **Fork** del repositorio de *rezzt-devc/polar* hacia tu cuenta de Github.
2. Clona dicho Fork a tu máquina vía consola `git clone https://github.com/tu-usuario/polar.git`.
3. Ejecuta el IDE Android Studio, y utiliza la opción "Ir a Archivo \> Nuevo \> Importar Proyecto..." *(selecciona la carpeta top build.gradle.kts)*

*(La sincronía principal requerirá algunos minutos re-descargando dependencias de Android Jetpack previas al compilado total).*

## 2. Flujo de Trabajo Clásico de Contribuciones

Empleamos el flujo de trabajo normal basado en "Github Pull-Requests", preferiblemente operando ramas modulares (Feature Branch Workflow).

1.  **Mantenimiento Rápido:** Crea e inyecta un cambio siempre en una nueva pre-rama partiendo sobre desde tu Master principal sincronizado (`git checkout -b fix/bug-del-calendario-UI`).
2.  **Guía de Código y Linter:** Asegúrate que tus métodos nuevos intentan ser modulares aislados, no bloquean el Hilo `MainThread`, y preferiblemente poseen tipado reactivo de Jetpack Compose / XML. ¡Sigue la directriz Clean Architecture! Si rompes el diseño separando la responsabilidades de capas ViewModel al Inyector DAO, cerraremos tu Request.
3.  **Compilación y Comprobaciones Locales:** Procura hacer tests o correr mínimamente la aplicación sin errores y probando que transiciones fluyen bien y se guardan sin cerrar abruptamente tu compilador simulador en el ADB.

## 3. Realizando tu Commits

Intentamos adherir nuestra semántica a lineamientos convencionales de commit:

*   Usa el formato estructurado simple `<tipo>(<alcance>): <descripción>`. (Ejemplo: `feat(ui): add rainbow border tag pattern` o `fix(database): fix memory leak issue into dao collection`).
*   Intenta explicar el parche sustancial en tu cuerpo extendido de la descripción en vez la solución mágica corta. ¡Nadie puede adivinar que hace verdaderamente tu magia sin contexto!

## 4. Elevando de Tu PR (Pull Request)

Sube tu rama (`push branch`) a tu servidor en Fork Github, pulsa el reluciente gran botón verde **Compare & Pull Request**, adjunta pantallazos comparando el *Antes y el Después* o tu GIF de demostración en UI si afecta visualmente, y sé feliz, lo más probable es que sea fusionada exitosamente.

¡Muchas gracias a ti por hacer una herramienta tan asombrosamente hermosa!
