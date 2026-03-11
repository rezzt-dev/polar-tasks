# Documentación Completa de Polar

Este documento es una versión consolidada de toda la documentación oficial de **Polar**, generada a partir de los diferentes archivos de la carpeta `docs`.

---

# Inicio
<!-- Archivo: index.md -->

# Documentación de Polar

Bienvenido a la documentación oficial de **Polar**, tu gestor de tareas minimalista y potente.

<div align="center">
  <img src="https://img.shields.io/badge/polar-minimalist%20task%20manager-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Polar Badge"/>
</div>

Polar está diseñado para simplificar la gestión de tareas personales. Combina una interfaz limpia y libre de distracciones con herramientas de organización potentes, como subtareas anidadas, etiquetado personalizado y vistas de calendario. Todo ello construido asegurando el rendimiento, la estabilidad y la disponibilidad sin conexión.

## Estructura de la Documentación

Hemos dividido esta documentación en dos grandes secciones para adaptarnos a tus necesidades:

### 📖 Guía del Usuario
Diseñada para cualquier persona que desee aprovechar al máximo las funciones de Polar en su día a día. Aprenderás a dominar la aplicación sin necesidad de conocimientos técnicos previos.

*   Primeros Pasos y Configuración
*   Creación de Tareas y Subtareas (NLP y Dependencias)
*   Organización con Listas y Etiquetas
*   Vistas de Calendario y Estadísticas
*   Personalización y Exportación

### 💻 Documentación Técnica
Orientada a desarrolladores, ingenieros de software y colaboradores que deseen entender la arquitectura interna, compilar el proyecto o contribuir al código abierto.

*   Arquitectura (Clean Architecture + MVVM)
*   Esquema de Base de Datos (Room)
*   Interfaz de Usuario y Navegación
*   Servicios en Segundo Plano (WorkManager)
*   Inyección de Dependencias (Hilt)
*   Guía de Contribución

---

## Casos de Uso Comunes

*   **Planificación Diaria:** Haz un seguimiento de tus hábitos y listas de tareas pendientes diarias sin saturación visual.
*   **Seguimiento de Proyectos:** Desglosa proyectos complejos utilizando subtareas dependientes y etiquetas.
*   **Gestión de Plazos:** Visualiza tus horarios con la vista de calendario integrada.
*   **Organización Personal:** Mantén tu vida laboral y personal separadas mediante el uso de listas personalizadas.

Descubre todo el potencial de tu productividad con Polar. ¡Comienza ahora!

---

# 📖 Guía del Usuario

<!-- Archivo: user-guide/getting-started.md -->

# Primeros Pasos con Polar

¡Bienvenido a Polar! Esta guía te ayudará a dar tus primeros pasos con la aplicación, desde la descarga e instalación inicial hasta la configuración de tu primera lista de tareas.

## 📥 Instalación

Actualmente, Polar está disponible para dispositivos Android (requiere Android 8.0 Oreo - API level 26 o superior).

### Opción 1: Compilación Local (Avanzado)
Si eres desarrollador y prefieres compilar la aplicación tú mismo:
1. Asegúrate de tener Android Studio instalado.
2. Clona el repositorio desde GitHub.
3. Abre el proyecto, sincroniza Gradle y ejecuta la aplicación en tu dispositivo o emulador.
*(Para más detalles, consulta nuestra Documentación Técnica).*

### Opción 2: Descarga de APK (Beta/Demos)
Consulta la sección de **Releases** en el repositorio de GitHub para descargar versiones `.apk` compiladas listas para instalar en tu dispositivo.

## ⚡ Configuración Inicial

Al abrir Polar por primera vez, te encontrarás con una interfaz completamente limpia y minimalista.

1. **Pantalla Principal:** Inmediatamente verás tu bandeja de entrada ("Inbox" o tu primera lista predeterminada). Polar prioriza una experiencia de usuario rápida, por lo que no necesitas registrar cuentas molestas en la nube para comenzar a usar las funciones.
2. **Funcionamiento Offline-First:** Toda tu información se guarda de forma segura y encriptada en tu propio dispositivo de manera local mediante la base de datos interna. No se requiere conexión a internet para crear, organizar o completar tareas.

## 🧭 Navegando por la Interfaz

La aplicación se enorgullece de su patrón de diseño y colores limpios.
*   **Menú de Navegación (Bottom App Bar o Drawer):** Te permite moverte fluidamente entre tus Listas, el Calendario y el panel de Estadísticas.
*   **Botón de Acción Flotante (FAB):** Ubicado en la parte inferior, es el punto de partida principal para añadir nuevas tareas en cualquier momento.

¡Estás listo para empezar! Continúa leyendo para descubrir cómo el Lenguaje Natural te ahorrará tiempo al teclear.

---

<!-- Archivo: user-guide/tasks-and-subtasks.md -->

# Tareas y Subtareas en Polar

El núcleo de **Polar** es la gestión eficiente de tus tareas diarias. Esta guía te enseñará cómo aprovechar al máximo las características inteligentes para añadir tus actividades rápidamente.

## 📝 Creación de Tareas y Procesamiento de Lenguaje Natural (NLP)

Añadir una tarea es tan sencillo como tocar el botón flotante (+) y escribir. Pero Polar no es un simple bloc de notas; incluye un **analizador inteligente (Smart Parser)** integrado.

### ¿Qué es el Lenguaje Natural?
El Procesamiento de Lenguaje Natural en Polar te permite escribir fechas y horas de forma conversacional y el sistema las extraerá automáticamente sin que tengas que usar engorrosos selectores de calendario manuales.

**Ejemplos de creación rápida:**
*   *"Llamar a Juan mañana a las 5pm."* -> Crea la tarea "Llamar a Juan", con fecha de vencimiento configurada para el día siguiente a las 17:00.
*   *"Reunión de equipo el próximo viernes."* -> Configura la fecha para el viernes de la semana siguiente.
*   *"Comprar pan hoy en la tarde."* -> Establece la fecha de hoy a una hora predeterminada por la tarde.

El texto de la fecha detectada normalmente se resaltará y se eliminará inteligentemente del título de la tarea final para mantener tus listas limpias.

## 🔗 Cadenas de Dependencia y Subtareas

Para proyectos más grandes o actividades complejas, una sola tarea general no es suficiente. Polar te permite estructurar tu trabajo mediante:

### Subtareas Anidadas
Dentro de los detalles de cualquier tarea principal, puedes desglosar pequeños pasos creando una lista de verificación interna.
*   **Ejemplo:** Tarea: "Planear Viaje". Subtareas: "Comprar pasajes", "Reservar hotel", "Hacer maletas".
El progreso de la tarea principal se reflejará conforme vas completando estas subtareas organizadas.

### Dependencias de Tareas (Avanzado)
A veces, no puedes comenzar la Tarea B hasta que la Tarea A esté terminada. Polar te permite crear **Cadenas de Dependencia entre Tareas**.
1.  **Vincular:** Puedes indicar que una tarea requiere que otra (o múltiples extras) sea completada de antemano.
2.  **Organización Inteligente:** Esta función bloquea u oculta en tu flujo principal las tareas que aún no puedes realizar porque sus pre-requisitos no se han cumplido, ayudándote a concentrarte solo en lo que es accionable ahora mismo.

Al utilizar estas cadenas, organizar grandes hitos, flujos de trabajo de proyectos y rutinas secuenciales resulta increíblemente fluido.

---

<!-- Archivo: user-guide/organization-and-labels.md -->

# Organización con Listas y Etiquetas

Una vez que comprendes cómo añadir tareas y estructurar dependencias, el siguiente paso es mantener el caos bajo control utilizando los sistemas de clasificación integrados en Polar. Aquí exploraremos cómo separar distintos aspectos de tu vida.

## 📂 Listas Diferenciadas

A diferencia de organizar todo en una lista gigantesca, **Polar** fomenta la creación de **múltiples listas personalizadas** como cajones separados.

*   Puedes tener listas para: *Trabajo*, *Personal*, *Compras*, *Hobbies* o *Desarrollo de Software*.
*   Cada lista actúa como un espacio independiente. Cuando abres una, visualizas únicamente las tareas que corresponden a ese directorio, garantizando enfoque y libre de distracciones externas.
*   *Movimiento de Tareas:* Si clasificas mal una tarea accidentalmente, puedes moverla ágilmente desde el panel de opciones de la tarea hacia la lista correcta sin tener que volver a escribirla.

## 🏷 Etiquetas y Código de Colores

Las etiquetas (Tags) actúan como otra dimensión potente dentro de tus tareas, cruzando barreras entre Listas.

### Creación de Etiquetas Visuales
*   Puedes crear una etiqueta global (ej: `Urgente`, `Baja Prioridad`, `Frontend`, o `Llamadas`).
*   **Códigos de color personalizados:** Asigna un color brillante (verde, rojo, pastel, neón, etc.) a cada etiqueta. Así, al desplazarte por tu bandeja de entrada o vistas principales, identificarás instantáneamente la naturaleza de un pendiente tan solo viendo su color en el margen o en su píldora visual.

### Filtrado y Búsqueda mediante Etiquetas
Las etiquetas no solo adornan; son funcionales. Una de las características estrella de la "Búsqueda Universal" de Polar es su capacidad para cruzar y buscar por etiquetas.

*   Puedes tocar una etiqueta en una tarea para ver rápidamente todas las otras tareas del sistema que comparten esa misma meta-data, independientemente de la Lista en la que vivan.
*   Combinar la búsqueda de texto global con filtros de Etiquetas te ofrece granularidad para encontrar lo que necesitas, incluso si posees miles de tareas archivadas o activadas.

Con Listas segmentadas y un jerárquico control de color a través de Etiquetas, tu productividad nunca se convertirá en un desorden indisciplinado.

---

<!-- Archivo: user-guide/calendar-and-views.md -->

# Vistas de Calendario y Estadísticas en Polar

Mientras la vista principal de listas te ayuda con el enfoque "diario" y granular, **Polar** incluye potentes herramientas integradas para la planificación visual a mediano y largo plazo, así como un seguimiento analítico de tu rendimiento.

## 📅 Integración de Calendario

Transiciona de una vista de texto en lista hacia una visualización espacial en el módulo de Calendario.

### Vista Mensual y Semanal
El **Calendario** unifica tus fechas límite de forma elegante. En vez de solo ver "Viernes", el calendario dibuja tarjetas completas, puntos de estatus o barras sobre todos los días en los que tengas fechas de vencimiento activas.

*   **Identificación Visual Inmediata:** Gracias a las *etiquetas de colores* que discutimos previamente, las barras en el calendario reflejan el tipo de tareas planificadas. (Por ejemplo, sabrás de antemano qué días están repletos de etiquetas rojas de "Urgencia").
*   **Navegación Intuitiva:** Desliza fluidamente (Swipes) entre las semanas o meses venideros para preparar tus compromisos.

### Recordatorios Visuales y Separación
El calendario no solo aloja tareas. Si empleas *Recordatorios*, la interfaz separa en la parte inferior claramente lo que es una obligación de acción (Tareas) de lo que son puras notificaciones pasivas (Recordatorios).

## 📊 Panel de Estadísticas y Productividad

Polar quiere motivarte a seguir siendo constante. Para ello provee un **Panel Analítico** ("Dashboard" o "Estadísticas") de vanguardia.

### ¿Qué se Mide?
*   **Productividad Global:** Revisa gráficos de cuántas tareas creas vs. cuántas completas en intervalos de tiempo (diario, semanal o mensual).
*   **Hábitos y Rachas (Streaks):** Para tareas recurrentes o la mera constancia de utilizar la aplicación y completar pendientes a tiempo, Polar calcula rachas positivas (ej. "¡Has estado enfocado 7 días seguidos!"). Es perfecto para ayudar a asentar hábitos duraderos al gamificar y premiar mantener una racha encendida.
*   **Desglose Visual:** Obtendrás gráficos circulares y métricas claras sobre rendimiento general.

Con el análisis integrado, la aplicación no solo guarda lo que tienes que hacer, sino que te retroalimenta con la prueba de todo lo que ya lograste.

---

<!-- Archivo: user-guide/customization.md -->

# Personalización y Exportación de Tareas

**Polar** no asume un formato rígido para todos; se adapta visualmente a tu personalidad y facilita compartir tu información fuera del entorno cerrado de la aplicación a través de robustas opciones de compartición.

## 🎨 Temáticas y Modos Oscuro/Claro (Themes)

Pasamos incontables horas diseñando paletas de colores armónicas. Dentro del apartado de Configuración (Settings) de Polar, podrás cambiar globalmente los colores de la aplicación mediante la función del **Administrador de Temas (ThemeManager)**.

### Los Diferentes Patrones Visuales:
Actualmente, Polar cuenta con un amplio surtido para personalizar cada pantalla:
1.  **Modos Clásicos:** "Modo Claro" (Light) pulcro e institucional, y un "Modo Oscuro" (Dark) perfecto para entornos de baja luz y ahorro de batería, cuidando el contraste de todo tipo y subtipo de fuentes.
2.  **Multicolor Light y Dark:** Temáticas dinámicas con colores vibrantes que le inyectan gran energía a la interfaz, diferenciándose de las clásicas combinaciones negro/blanco con grises.
3.  **Pastel Soft:** Una opción estética mucho más delicada, suave y calmada, genial para no saturar los sentidos.
4.  **Neon Dark:** Una opción futurista, vibrante e intensa para un look moderno de alto impacto visual.

Toda la aplicación cambiará de paleta dinámicamente y la mantendrá en tus futuros reinicios.

## 📤 Exportación y Compartición de Información

A menudo, tus listas no solo son para ti, y **Polar** entiende que debes conectarlas con el mundo exterior o tus compañeros de equipo/familia.

### 1. Exportación de Tareas a Texto Plano
Cualquier lista, colección de subtareas, o conjuntos de tareas filtradas, puede ser **exportada automáticamente al portapapeles** bajo un formato de texto plano estructurado, listo para ser pegado en un email, en chat de WhatsApp o en notas de una reunión.

### 2. Compartir Tarea como Imagen
Con un solo esfuerzo, puedes elegir una tarea completa (con sus subtareas, etiquetas y descripciones) y pedirle a Polar que **"Genere una Captura como Imagen compartible"**.
El sistema compila instantáneamente una hermosa imagen tipográfica pre-diseñada mostrando toda esa información. Inmediatamente invoca el menú genérico de tu móvil para enviar dicha imagen por redes sociales (Instagram, Telegram, Discord, etc.) a quién requieras asignársela o informarle sin que ellos necesiten tener la app instalada.

¡Haz tuya la aplicación con los temas visuales y comparte tu constancia productiva al instante!

---

# 💻 Documentación Técnica

<!-- Archivo: technical/architecture.md -->

# Arquitectura del Proyecto (MVVM + Clean Architecture)

Este documento detalla los principios de diseño y la estructura global del código fuente de **Polar**, enfocados en la mantenibilidad, escalabilidad y tolerancia a fallos. El proyecto está construido completamente en Kotlin moderno y utiliza lo último del ecosistema de Android Jetpack.

## Visión General de la Arquitectura

Para asegurar que las responsabilidades de cada componente del software estén debidamente separadas, Polar fue concebida implementando una mezcla pragmática de **Clean Architecture** y el patrón de presentación **Model-View-ViewModel (MVVM)**.

La directriz central se basa en la **separación de capas**. La Interfaz de Usuario (UI) nunca tiene conocimiento sobre el origen de los datos, y los componentes de bajo nivel de la base de datos no dominan ninguna lógica de negocio compleja; todo fluye de adentro hacia afuera gracias a la **Inyección de Dependencias (DI)**.

## Las Capas Principales (Project Structure)

Físicamente, la estructura principal en el código en `app/src/main/java/app/polar/` agrupa las responsabilidades de la siguiente manera:

### 1. Capa de Presentación (UI Layer) - `ui/`
Su único trabajo es gestionar las interacciones del usuario y renderizar estados vivos.
*   **Activities & Fragments:** (`ui/activity`, `ui/fragment`) Son "tontos" — su único rol es inicializar el `ViewBinding`, suscribirse mediante observadores reactivos (*StateFlow, LiveData*) expuestos por sus ViewModels subyacentes y actualizar el layout.
*   **Adapters:** (`ui/adapter`) Manejan listas dinámicas complejas (como tu Inbox, vistas de calendario y tags) utilizando el paradigma de `RecyclerView` y calculando diferencias de rendimiento de listas suavemente (vía `DiffUtil`).
*   **ViewModels:** (`ui/viewmodel`) Manejan la *lógica de presentación*. Conservan el estado intermedio al rotar la pantalla y ordenan el trabajo complejo en despachadores asincrónicos sin bloquear el hilo principal de renderizado (`Dispatchers.IO` o `Dispatchers.Default`).

### 2. Capa de Dominio (Domain Layer) - `domain/`
*(Nota: En aplicaciones MVP pequeñas esta capa suele ser delgada y absorbida por los ViewModels y el Repositorio. Aquí encapsulamos la Lógica de Negocio Pura y Reglas)*
*   **Use Cases (Casos de uso / Interactors):** Contienen operaciones de negocio complejas re-utilizables como *"Mover una tarea de forma validada entre listas verificando si ambas existen"*, o *"Agendar un Recordatorio, validando pre-requisitos de un Flow NLP y desencadenando Broadcast Receivers simultáneamente"*.

### 3. Capa de Datos (Data Layer) - `data/`
Centraliza la obtención de la "Verdad Absoluta" (Single Source of Truth) para interactuar con la persistencia.
*   **Entities:** (`data/entity`) Mapas o "Data Classes" directos de Kotlin a las tablas SQL de SQLite/Room.
*   **DAOs (Data Access Objects):** (`data/dao`) Las interfaces especializadas de Room llenas de anotaciones como `@Query`, `@Insert` repletas del poder reactivo (*flujos de Kotlin continuos observados por el resto de la app*).
*   **Repositories:** (`data/repository`) Implementaciones bajo el Patrón Repositorio. Ocultan al ViewModel de dónde y cómo exactamente el "Dato X" fue traído (es decir, Room Database, Preferences DataStore, o en el futuro un caché/API).

## Principios Reactivos Sostenidos
Para lidiar con el alto grado de cambios, estado y velocidad exigidos, la aplicación utiliza extensivamente abstracciones reactivas.
Los DAOs empaquetan las respuestas de consultas SQLite/Room como objetos **`Flow<T>`** (Kotlin Coroutines). El repositorio lo transmite hacia el ViewModel y éste termina transformándolo en un estado mapeado y consumido en el UI Thread mediante recolectores como la función `collect{}` orientada a estados de ciclos de vida de Android.
El resultado es un refresco dinámico y constante: cuando modificas el nombre de una Lista o añades un Tag, cada fragmento en pantalla visible que dependa de esa misma colección, se re-pintará instantáneamente sin necesidad de peticiones imperativas manuales desde el UI.

---

<!-- Archivo: technical/database-schema.md -->

# Esquema de la Base de Datos Relacional

Este documento aborda las interioridades de la persistencia principal offline y ultra-rápida implementada fundamentalmente con **Room Database**, la librería recomendada de Google enfocada en abstraer el código "Repetitivo" crudo y molesto de la capa de SQlite al ecosistema completo y tipado de coroutines Kotlin.

## Archivos Centrales de la Capa de Datos

Toda interacción local y almacenamiento atómico sucede dentro del paquete modular superior `data/`.

### Las Entidades (Data Classes y Relaciones Mapeadas a Tablas)
Localizadas en `data/entity/*`, los esquemas principales se apoyan fuertemente de etiquetas de anotaciones como `@Entity` indicando tablas nombradas, `@PrimaryKey(autoGenerate = true)` y `@Ignore`.

**1. Tareas (`TaskEntity`):**
La tabla primordial de la aplicación.
*   Campos base conteniendo metadatos como Título, Descripción Extensa opcional, UUID únicos.
*   **Gestión de Estados e Hilos Temporales:** Banderas lógicas booleanas para estados completados `isCompleted = true/false`, instantes Unix para marca temporal (`timestamp_created`, `due_date_nullable`).

**2. Listas de Usuarios / Directorios (`ListEntity`):**
Contenedores estáticos de colección para dividir directorios personales de tareas en distintos apartados. Las "Tareas" poseen comúnmente una variable `list_id_parent` Foreign-Key virtual (clave foránea de interconexión ligada a Listas).

**3. Etiquetas de Meta-Data (`TagEntity`):**
Entidades flexibles, conteniendo propiedades UI directas persistidas como cadenas hexadecimales de su paleta de color (`hex_color`) y nombres semánticos.
*   **Relación Many-to-Many:** Dado que una tarea puede albergar múltiples tags y un tag aplica para incontables tareas al cruzar contextos o carpetas, una tabla unificadora (cross-reference join table) adicional invisible permite el relacionamiento entre ellas sin romperse o alterar sus modelos base.

**4. Subtareas Atomizadas y Dependencias:**
Para soportar el sistema de pasos subyacentes ligados a una `TaskEntity` principal "Padre", cada subtarea se empaqueta serializada o anidada en la propia tabla del padre como campo estructurado de lista pre-formateada en un conversor relacional propio, ó se implementan tablas hijas en una relación anclada de 1-a-Muchos que eliminan sus partes con efecto dominó protector ante borrados (mediante transacciones de `CASCADE Delete` en base de datos previniendo tablas inútiles o basura remanente "Zombies").

## Accesos Directos Ocultos y Mágicos: Data Access Objects (DAOs)

Una de las metas obligatorias implementadas tras toda capa superior UI que necesita reaccionar y re-pintar a cada pequeño cambio sin que todo explote es mediante DAOs en `data/dao/*`.

*   Anotaciones simples para interacciones cridas: Un simple `@Insert(onConflict = OnConflictStrategy.REPLACE)` para reemplazar sin colapso a nuevas actualizaciones atómicas inofensivas en tiempo real desde la edición UI.
*   **Búsquedas Complejas en Texto Natural (FTS) & Relaciones Mixtas:** Muchas interacciones analíticas utilizan instrucciones directas `@Query("SELECT * FROM tasks WHERE nombre LIKE '%' || :query || '%' AND listId = :id")` compiladas en chequeos de forma preventiva y empaquetando todo el resultado en un flujo reactivo estricto Kotlin `Flow<List<TaskEntity>>`. Estos flujos persistentes son la pieza fundamental para las interacciones reactivas de estado presentadas en nuestra capa limpia de vista UI, observadas desde DAOs mediante un intermediario transaccional del Repository layer subyacente de Arquitectura.

---

<!-- Archivo: technical/ui-and-navigation.md -->

# Interfaz de Usuario y Sistema de Navegación

En este artículo, se expone el andamiaje principal de la presentación visual y las transiciones entre todas las pantallas de Polar, aprovechando herramientas modernas del SDK de Android para asegurar alta legibilidad y prevención de cierres forzados (crashes).

## Componente de Navegación Arquitectónica (Navigation Component)

A diferencia de las arquitecturas clásicas de Android usando decenas de "Activities" pesadas interconectadas mediante "Intents", Polar usa la arquitectura de **"Una Sola Activity" (Single Activity Architecture)** liderada por Jetpack Navigation.

### Intermitencia de `MainActivity`
Hay una única actividad real: `MainActivity.kt`.
Ésta se mantiene activa permanentemente y actúa como barco nodriza. Lo único que hace esta Activity es anidar dentro de su estructura de layout un "NavHostFragment". Actúa como contenedor donde docenas de `Fragment`s rotan fluidamente como páginas web emparejados al stack de Android (manejo de botón Atrás nativo perfecto garantizado).

### Mapas de Navegación y Grafos Sensibles a Parámetros
*   **Navigation Graphs (XML):** Las jerarquías o el "sitio web" de Fragmentos y sus "líneas de transiciones seguras permitidas" se declaran usando XML de navegación unificado en la carpeta `res/navigation`.
*   **Safe Args:** Para pasar identificadores, IDs de bases de datos o simples cadenas crudas entre Fragmentos A y Fragmentos B, Polar cuenta con Jetpack Safe Args Plugin, el cual autogenera código Kotlin con constructores orientados a tipos para eliminar la arcaica dependencia de recuperar Bundle keys estáticas mágicas o cadenas nulas desordenadas proactivamente al momento de compilación.

## Vinculación De Vistas en Tipos Seguros (View Binding)

El diseño de la aplicación usa esquemas densos bajo XML nativo (`res/layout/`). Para la conexión rápida y confiable hacia la lógica (ViewModels), hemos prohibido prácticas peligrosas de inicialización tardía en IDs usando el clásico y poco tipado `findViewById()`, propensas a temblores de `NullPointerExceptions` de vistas de otro Layout inexistente o recicladas.

Polar habilita y aboga fuertemente por el uso agresivo de Android **View Binding** a lo largo de toda la UI. Las clases agrupan mágicamente todos tus botones e infieren automáticamente sus referencias y sus tipos originales nativos sin errores al primer intento después del compilador.
*   **Adaptadores limpios:** Cada Celda `ViewHolder` en tu Lista RecyclerView, implementa variables aisladas de View Binding pasante en constructores para manipular eficientemente cada TextView de texto natural extraída.

## Animaciones de Material Design, Recursos y Theming

Al inspeccionar los recursos del paquete `res/`, encontrarás la potente matriz gráfica subyacente de nuestra filosofía de múltiples temas con **Modos Claros y Modos Oscuros**.

1.  **Material Components y Tematización Atributiva (Attribute Theming):** A lo largo del proyecto, los colores no están 'hardcodeados' cruda ni arrogantemente usando colores absolutos `#FFFFFF`. Polar llama la jerarquía base invocando paletas bajo abstracciones, mediante el prefijo del atributo del tema de material actual como `?attr/colorPrimary` o `?attr/colorSurface`. Esto permite repintar cualquier contenedor instantáneamente durante el switch del ThemeManager dentro de "Personalización de Preferencias Settings" hacia esquemas Oscuro, Multicolor o Pastel.
2.  **Transiciones en Drawer y Menú Inferior:** Elementos dinámicos en los menús modales inferiores y fragmentos superpuestos de transiciones rápidas utilizan implementaciones canónicas del subsistema actual de componentes de Material 3, engranándose fluida y nativamente para responder al panel flotante de Acciones Principales y el buscador superior.

---

<!-- Archivo: technical/background-services.md -->

# Servicios en Segundo Plano (Background Services)

En cualquier gestor de tareas moderno moderno, una de las garantías más importantes para el usuario final es que sus notificaciones suenen exactamente cuando se supone que deben sonar y no queden suprimidas o dormidas (Doze Mode) por el sistema operativo implacable de Android cerrando servicios de fondo.

**Polar** aborda la gestión del tiempo de vida secundario de la app usando dos primitivas oficiales poderosas y recomendadas por Google.

## Gestor de Trabajo: WorkManager (Jetpack)

Para procesos de largo aliento que requieren "Garantía de Ejecución" en el futuro, pero no imperativamente una precisión al micro-segundo, y deben sobreponerse a reinicios de dispositivo, caídas de la propia aplicación o cierres forzosos de la caché de RAM, empleamos la librería de infraestructura **Android WorkManager**.

### ¿Cuándo usamos Workers?
`app/src/main/java/app/polar/worker/*`:
1.  **Limpiezas (Cleanups):** Tareas periódicas, por ejemplo un Worker planificado para ejecutarse a las 3:00 AM que barre o depura en base de datos toda la caché residual muerta o tareas viejas que el usuario configuró hace semanas para autodestrucción.
2.  **Soporte de Recuperación de Estado Local:** Un script para sincronizar respaldos automáticos en un archivo ZIP almacenado fuera de peligro de forma offline.
3.  **Procesamiento Masivo:** Si el usuario decide generar etiquetas de metadatos o marcar masivamente centenas de tareas como completadas concurrentes, estas transacciones pueden envolverse garantizadas con la delegación hacia una instancia pre-configurada `OneTimeWorkRequestBuilder`.

## Alarmas Exactas (Precisión) y Notificaciones

Cuando el Smart Parser detecta una petición del usuario explícita de *"Llamar jefe mañana a las 5:00 PM"*, no podemos depender de un gestor de trabajos en segundo plano general inespecífico; debemos asegurar alertas quirúrgicas locales.

### BroadcastReceivers Temporales (`app.polar.receiver`)
Utilizamos fuertemente el subsistema de reloj del kernel por medio del API `AlarmManager` junto a **Broadcast Receivers** registrados y localizados bajo el directorio en `/receiver`.

*   **Flujo Exacto Programado:**
    1.  Polar establece el valor temporal milisegundo codificado del instante futuro mediante el método exacto (Si las políticas del sistema de SO posterior al nivel API 31 o Android 12 así lo permiten solicitando `SCHEDULE_EXACT_ALARM`).
    2.  El propio `AlarmManager` despertará al dispositivo y un Intent implícito chocará de frente con la escucha de nuestro `ReminderReceiver.kt`.
    3.  Este Receptor tomará control estático (sin necesidad que exista un UI Activity vivo), consumirá un acceso rápido con Inyección Hilt hacia el caso de uso adecuado y detonará una Alerta Visor (`NotificationManagerCompat`), haciendo sonar el banner UI con sonido directo notificando al usuario en su zona nativa superior de alertas Android.

---

<!-- Archivo: technical/dependency-injection.md -->

# Inyección de Dependencias (Dependency Injection)

Una aplicación fuertemente estructurada bajo *Clean Architecture* con múltiples componentes que dependen el uno del otro (Fragmento -> ViewModel -> Use Case -> Repositorio -> Controlador Local/Entity Room Dao) requiere un pegamento para unificar todas las implementaciones o se ahogaría rápidamente en constructores monstruosamente rígidos sin mantenimiento, propensos al desacoplamiento insoportable si necesitas rotar componentes para implementar Testeos Aislados.

**Polar** resuelve este problema eficientemente desde su primer esqueleto introduciendo **Hilt (Dagger-Hilt)**, como su plataforma central de Proveedores de Inversión de Control.

## ¿Por qué Hilt?

Hilt está construido específicamente por Google por sobre las implementaciones del estándar y ultrarrápido Dagger 2, dándole vida a una API mucho más concisa y estándar de facto en desarrollos de Android modernos; reduciendo toda la necesidad previa de construir docenas de módulos extraños, provisioners y componentes customizados (boilerplate). Hilt entrega toda inyección y ciclos de la Aplicación global estandarizados mágicamente solo mediante la adición de Anotaciones al Código.

## Estructuración e Implementación

Para manejar eficazmente el estado de Hilt en el proyecto, revisaremos los cimientos implementados.

### 1. El Ámbito Superior: App Entry Point
El ciclo de auto-generación del árbol de dependencias despierta dentro del paquete raíz interceptando el objeto Application a nivel superior, `app.polar.PolarApplication.kt` junto al uso imperativo de la etiqueta mágica `@HiltAndroidApp`.

Esa etiqueta desencadena y da a luz al contenedor nivel raíz para procesar toda dependencia única Global (Singletons) inyectada en todo el hilo de vida predeterminado que sobreviva a toda instancia simple, y acopla un contenedor a nivel `Activity/Fragment`.

### 2. Contratos de Inyección en Vistas - `@AndroidEntryPoint`
Toda aquella intercepción al sistema para reclamar dependencias subyacentes en una Activity (como `MainActivity`) o los docenas de múltiples Layouts de UI `Fragment` debe poseer a pulso la etiqueta `@AndroidEntryPoint` en el comienzo de la clase y de este modo reclamar ser atendidos independientemente a cada requerimiento inyectable interno necesario, a petición libre.

### 3. Conectores Mágicos de Estado: ViewModels
Para el provisionamiento en clases `ViewModel` inyectados en la capa UI, Hilt ofrece la etiqueta `@HiltViewModel` por sobre su nombre. Aquellas variables o Repositorios que necesiten inicializaciones dentro de cada VM, automáticamente se solicitan delegándolas marcando a toda inyección que cruza el portal en el Constructor primario Kotlin mediante `@Inject constructor(…)`, acoplándose armónicamente al ciclo sin necesidad de fábricas de ViewModels monstruosas y pesadas hechas de forma manual o verbosas.

### 4. La Carpeta de Módulos Central (Módulos de Provisión `di/`)
Es posible que durante nuestro desarrollo debamos proveer clases que simplemente no tienen un constructor inyectable expuesto, por ejemplo:
- Inicialización en cascada de la **Room Database** (Es una interfaz).
- **Controlador Datastore** estático global (Librerías externas al desarrollo estático).
- API **Retrofit Clients**.

Para satisfacer la inyección y darle alcance general al arquetipo a proveer este tipo de librerías exógenas cerradas para proveer a través de `@Provides` delegamos toda esta arquitectura a simples y descriptivos archivos en la ruta del framework, en la importante carpeta llamada `di/` (Dependy Injection):

Allí encontraremos usualmente módulos como `DatabaseModule.kt` marcados fuertemente dictando sus retornos de constructores y variables estáticas que toda arquitectura proveerá cíclicamente a todo archivo `class` en Polar que necesite un DAO o conexión en segundo plano rápida y pre-cargada y en caché.

---

# 🤝 Guía de Contribución

<!-- Archivo: contributing.md -->

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
