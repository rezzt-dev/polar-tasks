# AGENTS.md — Guía para Agentes de Código (Polar)

> Este archivo está destinado a agentes de IA que interactúen con el proyecto **Polar**.
> El lector se asume con cero contexto previo sobre la aplicación.

---

## 1. Visión General del Proyecto

**Polar** es una aplicación nativa de Android para la gestión personal de tareas. Está diseñada con una interfaz minimalista, libre de distracciones, pero con herramientas de organización potentes: subtareas anidadas, etiquetas con color, vistas de calendario, recordatorios con alarmas exactas, matriz de Eisenhower, estadísticas y un sistema de papelera con borrado suave.

- **Idioma principal del código y documentación técnica:** Español (docs técnicos), con código Kotlin y comentarios mixtos (español/inglés).
- **Arquitectura:** MVVM + Clean Architecture pragmática.
- **Persistencia:** Offline-first mediante Room Database (SQLite).
- **Sin conectividad de red:** La app no consume APIs externas; toda la lógica es local.

---

## 2. Stack Tecnológico y Dependencias Clave

| Capa | Tecnología | Versión (relevante) |
|------|-----------|---------------------|
| Lenguaje | Kotlin | 2.0.21 |
| Build System | Gradle (Kotlin DSL) | AGP 8.13.2 |
| SDK Android | `compileSdk` 36, `minSdk` 24, `targetSdk` 36 |
| UI Framework | XML Layouts + View Binding + Material Components 1.13.0 |
| Arquitectura | MVVM, ViewModel, LiveData, StateFlow |
| Inyección de Dependencias | Hilt (Dagger) | 2.51.1 |
| Base de Datos | Room | 2.6.1 |
| Corrutinas | Kotlinx Coroutines Android | 1.7.3 |
| Programación de Tareas | WorkManager | 2.9.0 |
| Navegación | FragmentManager manual (Single Activity) |
| Serialización | Gson | 2.10.1 |
| Testing Unitario | JUnit 4, MockK, kotlinx-coroutines-test, androidx.arch.core:core-testing |
| Testing Instrumentado | AndroidX Test (JUnit + Espresso) |

> **Nota importante:** El proyecto usa `kapt` (no KSP) para el procesamiento de anotaciones de Room y Hilt.

---

## 3. Estructura del Código Fuente

Todo el código fuente principal reside en `app/src/main/java/app/polar/`.

```
app/src/main/java/app/polar/
├── MainActivity.kt                 # Única Activity contenedora (Single Activity)
├── PolarApplication.kt             # Application con @HiltAndroidApp
│
├── data/                           # Capa de Datos (Data Layer)
│   ├── AppDatabase.kt              # Room Database (v14) con migraciones manuales
│   ├── dao/                        # Data Access Objects (Room)
│   ├── entity/                     # Entidades (@Entity): Task, TaskList, Subtask, Reminder
│   ├── model/                      # Modelos de dominio/datos auxiliares (TaskGroup, TaskWithList)
│   ├── repository/                 # Repositorios que abstraen DAOs
│   └── backup/BackupManager.kt     # Gestión de respaldos locales
│
├── domain/                         # Capa de Dominio (Domain Layer)
│   ├── usecase/                    # Casos de uso (ej. GetFilteredTasksUseCase)
│   └── util/SmartParser.kt         # Parser NLP para tareas inteligentes
│
├── ui/                             # Capa de Presentación (UI Layer)
│   ├── activity/                   # Activities secundarias (TaskDetail, SearchResults, Tutorial)
│   ├── fragment/                   # Pantallas principales (Tasks, Calendar, Stats, Settings, etc.)
│   ├── adapter/                    # Adapters de RecyclerView con DiffUtil
│   ├── dialog/                     # Dialogs y BottomSheets (TaskDialog, ReminderDialog, etc.)
│   ├── viewmodel/                  # ViewModels con @HiltViewModel
│   ├── view/                       # Vistas personalizadas (BarChartView)
│   ├── widget/                     # Widgets reutilizables (ColorPickerView, MaxHeightRecyclerView)
│   └── manager/DrawerManager.kt    # Gestor del Navigation Drawer lateral
│
├── di/                             # Módulos de Inyección de Dependencias (Hilt)
│   └── AppModule.kt                # Provee AppDatabase y DAOs como Singletons
│
├── receiver/                       # BroadcastReceivers para alarmas y notificaciones
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   └── NotificationActionReceiver.kt
│
├── worker/                         # Workers de WorkManager (tareas en segundo plano)
│   └── RecurrenceWorker.kt         # Revisa tareas recurrentes periódicamente
│
├── widget/                         # App Widget del launcher (tareas en home screen)
│   ├── TaskWidgetProvider.kt
│   └── TaskWidgetService.kt
│
└── util/                           # Utilidades y helpers
    ├── AlarmManagerHelper.kt       # Programación/cancelación de alarmas exactas
    ├── NotificationHelper.kt       # Canales y notificaciones
    ├── ThemeManager.kt             # Temas dinámicos, fuentes y localización
    ├── DateUtils.kt                # Helpers de fecha/calendario
    ├── DragDropHelper.kt           # Soporte drag-and-drop en RecyclerViews
    └── TaskSwipeHelper.kt          # Swipe bidireccional reutilizable para RecyclerViews
```

### Recursos (`app/src/main/res/`)

- `layout/` — ~35 archivos XML de layouts (Activities, Fragments, Items, Dialogs).
- `menu/` — Menús de Toolbar/ActionBar.
- `drawable/` — ~61 recursos gráficos, vectores y formas.
- `values/` — Strings (es, en-rGB, fr), colores, temas, dimensiones, atributos personalizados.
- `values-night/colors.xml` — Paleta para modo oscuro.
- `xml/` — Configuración de widgets, FileProvider paths, reglas de backup.

> **No existe carpeta `navigation/`**: la navegación se maneja manualmente con `FragmentManager` desde `MainActivity` y `DrawerManager`.

---

## 4. Convenciones de Desarrollo

### 4.1 Patrones Arquitectónicos

- **Separación de capas estricta:** La UI (Fragment/Activity) **nunca** accede directamente a DAOs. Siempre pasa por ViewModel -> Repository -> DAO.
- **Reactivo:** Los DAOs exponen `Flow<List<T>>` o `LiveData<List<T>>`. El ViewModel transforma estos flujos en `StateFlow` o `LiveData` para la UI.
- **ViewModels con Hilt:** Usan `@HiltViewModel` e inyección por constructor `@Inject`. Son `AndroidViewModel` cuando necesitan `Application`.
- **Activities con Hilt:** Toda Activity que requiera inyección debe llevar `@AndroidEntryPoint`.

### 4.2 Manejo de Errores en ViewModels

Casi todos los ViewModels implementan un patrón `safeLaunch` para encapsular corrutinas:

```kotlin
private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
    try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()
        _errorMessage.value = "Error: ${e.message}"
    }
}
```

Si creas nuevos ViewModels, **replica este patrón** para consistencia.

### 4.3 View Binding (Obligatorio)

Se usa **View Binding** en toda la UI. Está habilitado en `build.gradle.kts` (`viewBinding = true`).
- Prohibido usar `findViewById`.
- Los `ViewHolder` de adapters reciben el binding como parámetro.

### 4.4 BaseActivity

Todas las Activities heredan de `BaseActivity`, que gestiona:
- Aplicación de tema dinámico (multicolor, oscuro, pastel, etc.) antes de `super.onCreate()`.
- Overlay de fuentes personalizadas.
- Overlay de estilos de checkbox.
- Configuración de locale (idioma) vía `attachBaseContext()`.

> Si creas una nueva Activity, extiende `BaseActivity`.

### 4.5 Temas y Atributos

Los colores **no deben hardcodearse** con valores hex absolutos en layouts. Se usan atributos del tema:
```xml
android:background="?attr/colorSurface"
android:textColor="?attr/colorOnSurface"
```

El `ThemeManager` soporta cambio en tiempo de ejecución de temas, fuentes e idioma.

### 4.6 Textos de Interfaz (siempre en minúsculas)

Toda la interfaz de Polar se escribe **en minúsculas**, en **todos los idiomas**
(incluido alemán, donde los sustantivos normalmente se capitalizan, y nombres
propios como `polar`, `material` o `eisenhower`).

- Aplica a los recursos de `app/src/main/res/values*/strings.xml`, incluidos los
  bloques `<plurals>`, y a cualquier texto hardcodeado en layouts o código.
- **No** aplica a datos introducidos por el usuario (títulos de tareas, notas,
  etiquetas, ubicaciones, etc.) ni a los especificadores de formato (`%1$s`,
  `%d`, `\n`).
- Al añadir una funcionalidad nueva o traducir strings, replica esta convención
  en `values/`, `values-en-rGB/`, `values-en-rUS/`, `values-de/` y `values-fr/`.

---

## 5. Base de Datos (Room)

### Entidades principales

| Entidad | Tabla | Clave |
|---------|-------|-------|
| `Task` | `tasks` | `id` (auto) |
| `TaskList` | `task_lists` | `id` (auto) |
| `Subtask` | `subtasks` | `id` (auto), FK a `tasks.id` |
| `Reminder` | `reminders` | `id` (auto) |

### Características del esquema

- `Task` tiene una Foreign Key a `TaskList` con `onDelete = CASCADE`.
- Soft-delete: los campos `isDeleted` en `Task` y `Reminder` marcan elementos en papelera en lugar de borrarlos físicamente.
- Tags se almacenan como cadena separada por comas en `Task.tags`.
- Recurrencia: campo `recurrence` con valores `"NONE"`, `"DAILY"`, `"WEEKLY"`, `"MONTHLY"`.
- Prioridad: entero `0=None, 1=Low, 2=Medium, 3=High`.

### Migraciones

`AppDatabase` define migraciones manuales de la 6→7 hasta la 13→14. La base de datos usa **WAL** (`JournalMode.WRITE_AHEAD_LOGGING`) para permitir lecturas concurrentes sin bloquear la UI.

> Si alteras el esquema, **aumenta la versión** y proporciona una `Migration` explícita. No confíes únicamente en `fallbackToDestructiveMigration`.

---

## 6. Servicios en Segundo Plano

### AlarmManager + BroadcastReceivers (`receiver/`)

Para recordatorios con precisión exacta se usa `AlarmManager` con `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`.
- `AlarmReceiver` — dispara la notificación cuando suena la alarma.
- `BootReceiver` — reprograma alarmas persistidas tras reinicio del dispositivo.
- `NotificationActionReceiver` — maneja acciones sobre notificaciones (completar, posponer).

### WorkManager (`worker/`)

- `RecurrenceWorker` — se ejecuta cada 12 horas para revisar y resetear tareas recurrentes cuya fecha de vencimiento haya pasado.

---

## 7. Comandos de Build y Test

### Compilación

```bash
# Compilar el proyecto (debug)
./gradlew :app:assembleDebug

# Compilar release
./gradlew :app:assembleRelease
```

### Tests

```bash
# Tests unitarios (JVM)
./gradlew :app:testDebugUnitTest

# Tests instrumentados (requiere emulador/dispositivo)
./gradlew :app:connectedDebugAndroidTest
```

### Verificación general

```bash
# Limpiar y compilar
./gradlew clean build
```

### Requisitos del entorno

- Android Studio Flamingo o superior.
- JDK 17.
- Android SDK API 36 (compileSdk).
- Kotlin 2.0+.

---

## 8. Estrategia de Testing

### Tests Unitarios (`app/src/test/`)

La suite de tests unitarios es pequeña pero representativa:

- `TaskViewModelTest` — Verifica que el ViewModel delega correctamente al UseCase y al Repository, y que la programación/cancelación de alarmas ocurre en los estados esperados.
- `TaskRepositoryTest` — Verifica que el Repository expone flujos de DAO y traduce operaciones CRUD.
- `SmartParserTest` — Tests del parser NLP para extracción de fechas/tiempos de texto natural.
- `MainDispatcherRule` — Regla de JUnit para reemplazar el dispatcher principal en tests de corrutinas.

**Herramientas:** MockK para mocks, `InstantTaskExecutorRule` para LiveData, `runTest` para corrutinas.

### Tests Instrumentados (`app/src/androidTest/`)

- `ExampleInstrumentedTest` — Test básico de contexto de la app.

### Convenciones para nuevos tests

- Usa `runTest` para funciones suspendidas.
- Usa `MockK` (no Mockito) para consistencia con el proyecto existente.
- Usa `MainDispatcherRule` en tests de ViewModels que usen corrutinas.
- Aísla el ViewModel mockeando Repository y UseCases.

---

## 9. Consideraciones de Seguridad

- **Sin permisos de red:** La app no declara `INTERNET`. Es completamente offline.
- **Almacenamiento externo limitado:** Solo lectura de imágenes (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` hasta API 32). Usa `FileProvider` para compartir archivos de backup de forma segura.
- **Alarmas exactas:** Requiere `SCHEDULE_EXACT_ALARM` y `USE_EXACT_ALARM`. En Android 12+ (API 31), el sistema puede restringir el uso; la app debe manejar la posibilidad de que el permiso sea revocado por el usuario.
- **Backup:** Habilitado (`android:allowBackup="true"`) con reglas de extracción declaradas en `data_extraction_rules.xml` y `backup_rules.xml`.
- **No hay cifrado de base de datos:** Room usa SQLite estándar. No se implementa SQLCipher ni cifrado a nivel de archivo.

---

## 10. Flujo de Contribución

1. Fork del repositorio.
2. Rama feature: `git checkout -b feature/nombre-descriptivo` o `fix/area-del-bug`.
3. Commits siguiendo **obligatoriamente** las reglas de "Redacción de commits" (ver abajo; réplica de `.docs/commit-guidelines.md`).
4. Asegurar que el código compila y los tests unitarios pasan.
5. Pull Request con capturas de pantalla si hay cambios visuales.

### Redacción de commits (título y descripción)

<!--
  Este bloque replica literalmente el contenido de `.docs/commit-guidelines.md`
  (que es local y no se versiona). Existe para que todos los commits del
  repositorio salgan con el mismo formato: más limpios y profesionales. Un
  historial homogéneo es más cómodo de leer para usuarios y desarrolladores, y
  transmite la información de los cambios de la mejor forma posible (prefijo
  claro, resumen en imperativo y cuerpo en lista). No improvises el formato:
  aplica siempre estas reglas. Si editas estas reglas, replica el cambio en
  `CLAUDE.md` y en `.docs/commit-guidelines.md`.
-->

> instrucciones para cualquier agente de ia (o persona) que redacte el titulo
> y la descripcion de un commit de git en este repositorio. se aplica siempre
> que se pida algo equivalente a:
>
> *"sacame el titulo y la descripcion para el commit con los cambios
> realizados desde el ultimo commit. fijate en los anteriores commits para
> que tengan el mismo formato."*
>
> y, de forma general, **cada vez que se vaya a crear un commit en este
> repositorio**, se haya pedido explicitamente o no.

#### Formato fijo del titulo

```
<prefijo> | <resumen corto en imperativo>
```

el separador es siempre espacio, barra vertical, espacio: ` | `. el
`<prefijo>` es siempre una de estas cinco palabras, en minusculas, y nunca
se combinan ni se inventan otras nuevas:

| prefijo | cuando usarlo |
|---|---|
| **update** | trabajo normal hacia adelante: se añade o mejora una funcionalidad, contenido o documento existente. es el prefijo por defecto. |
| **fix** | se corrige un error o comportamiento incorrecto detectado durante el desarrollo, sin urgencia de publicacion inmediata. |
| **hotfix** | se corrige algo urgente que afecta a una version ya publicada o a un bloqueo critico. debe usarse solo cuando la correccion es realmente urgente. |
| **changes** | cambios estructurales o no funcionales: reorganizar carpetas, renombrar ficheros, tocar configuracion o tooling, dependencias. no anade funcionalidad ni corrige un bug. |
| **changelog** | el unico proposito del commit es editar `changelog.md` (por ejemplo, al cerrar una version). |

ejemplos de titulos correctos:

```
update | añadir vista previa de mapeo de columnas al asistente de importación csv
fix | detección de duplicados fallando en filas con importes negativos
hotfix | fallo al iniciar cuando falta el archivo de perfil de importación
changes | reorganizar la documentación en la subcarpeta project-context
changelog | cerrar la sección de changelog para la versión mvp fase 0
```

#### Formato de la descripcion (cuerpo)

- el cuerpo es siempre **una lista de markdown no numerada**: cada item empieza
  por `-`. nunca se usan listas numeradas ni parrafos sueltos.
- cada item es un bloque logico en imperativo, escrito como **una sola linea
  continua**, sin saltos de linea internos: no son esteticos ni utiles, deja
  que el cliente de git lo ajuste. no se dejan lineas en blanco entre items.
- usa markdown dentro de cada item: nombres de ficheros, rutas, comandos e
  identificadores siempre entre backticks.
- explica el que y el por que, no el detalle linea a linea.
- cierra con una linea en blanco y la linea de atribucion que corresponda al
  entorno.

#### Como presentar el resultado

al responder con el titulo y la descripcion hay que usar **bloques de codigo
separados**: uno para el titulo del commit y otro para el cuerpo del commit (mas
otro para el fragmento del changelog). nunca se juntan el titulo y el cuerpo en
el mismo bloque.

### Formato de las secciones de `changelog.md`

<!--
  Este bloque define como se escriben las entradas de `changelog.md`. El
  objetivo es un registro de cambios homogeneo y bilingue (castellano e
  ingles) que la mayoria de personas pueda leer, y que quede limpio y
  profesional. Si editas estas reglas, replica el cambio en `CLAUDE.md`.
-->

`changelog.md` (en la raiz del repositorio) registra los cambios del proyecto
de aqui en adelante. reglas de escritura:

- **todo el contenido en minusculas y sin acentos.** los titulos (encabezados
  markdown `#`, `##`, `###`, `####`) van **en mayusculas y sin acentos**.
- **bilingue:** cada version contiene el mismo contenido en castellano y en
  ingles, con las mismas entradas en el mismo orden (traduccion 1:1).
- cada item es **una sola linea** que empieza por `-`, redactada en
  imperativo. usa backticks para nombres de ficheros, rutas, comandos e
  identificadores.
- la version mas reciente va **arriba** del fichero; debajo de la cabecera.
- las fechas usan el formato `aaaa-mm-dd`.

estructura de cada version:

```
## SIN PUBLICAR / UNRELEASED        (mientras la version no se ha publicado)
## [x.y.z] - aaaa-mm-dd             (al cerrar la version)

### CASTELLANO

#### <categoria>
- <cambio en una linea>

### ENGLISH

#### <category>
- <same change, one line>
```

categorias permitidas (lista cerrada; se omite la que no tenga items):

| castellano | english | cuando |
|---|---|---|
| **NUEVO** | **ADDED** | funcionalidad, pantalla o contenido nuevo. |
| **CAMBIOS** | **CHANGED** | cambios de comportamiento, refactor, reorganizacion, tooling. |
| **CORREGIDO** | **FIXED** | correccion de un bug o comportamiento incorrecto. |
| **ELIMINADO** | **REMOVED** | funcionalidad, opcion o fichero que se quita. |
| **SEGURIDAD** | **SECURITY** | cambios relacionados con seguridad o permisos. |

flujo:

- mientras se trabaja, las entradas nuevas se anaden a la seccion
  `## SIN PUBLICAR / UNRELEASED`.
- al cerrar una version se renombra esa seccion a `## [x.y.z] - aaaa-mm-dd` y
  se crea una nueva seccion `## SIN PUBLICAR / UNRELEASED` vacia encima.
- el commit que solo toca `changelog.md` usa el prefijo `changelog` (ver
  arriba).

### Pull requests, merges y releases

<!--
  Este bloque replica literalmente el contenido de
  `.docs/pr-merge-release-guidelines.md` y de la sección homónima de
  `CLAUDE.md`. Existe para que las pull requests, los commits de merge y las
  releases de github lean como una extensión directa de `changelog.md` en
  vez de tener cada una su propio formato. Si editas estas reglas, replica el
  cambio en `CLAUDE.md` y en `.docs/pr-merge-release-guidelines.md`.
-->

pull requests, merges y releases **no inventan su propio formato**: reusan
literalmente el contenido y las reglas de estilo de `changelog.md` — minusculas
y sin acentos, las mismas cinco categorias en el mismo orden (nuevo, cambios,
corregido, eliminado, seguridad / added, changed, fixed, removed, security,
omitiendo la que no tenga items), una linea por item en imperativo con
backticks para nombres de ficheros, rutas, comandos e identificadores, y
fechas en formato `aaaa-mm-dd`. las marcas `[online]` / `[offline]` de
`changelog.md` se conservan tal cual. la fuente de verdad es siempre
`changelog.md`: el contenido de pr, merge y release se extrae de ahi, nunca
se redacta desde cero ni en paralelo.

#### pull requests

- **pr de trabajo normal** (`feature/...` o `fix/...` contra
  `testing-branch`): mismo titulo que un commit,
  `<prefijo> | <resumen corto en imperativo>` (ver "redaccion de commits"
  arriba).
- **pr de publicacion de version** (`testing-branch` → `release-branch` /
  `main`, cierra una version): `release version <x.y.z> - <resumen corto en
  imperativo>`.
- **descripcion** (igual para las dos): el bloque `### CASTELLANO` +
  `### ENGLISH` de la seccion correspondiente de `changelog.md`, pegado tal
  cual con sus encabezados `####` de categoria. si la pr solo aporta parte de
  `## SIN PUBLICAR / UNRELEASED`, se incluyen solo los items que introduce.
  no se anaden parrafos sueltos ni resumenes fuera de la lista.

#### commit de merge

github genera el commit de merge a partir del titulo y la descripcion de la
pr, asi que una pr correcta ya produce un merge correcto. usa siempre
**"create a merge commit"** (nunca squash ni rebase), para conservar el
historial de commits individuales junto al resumen de la pr.

#### releases de github

las releases son **en ingles unicamente** (no bilingues):

- **titulo:** `v<x.y.z> — <resumen corto en ingles, minusculas, sin acentos>`
  (ejemplo: `v1.7 — 100% offline, reminders redesign & settings categories`).
- **cuerpo:** el bloque `### ENGLISH` de la version cerrada en
  `changelog.md`, con sus categorias `#### ADDED` / `#### CHANGED` /
  `#### FIXED` / `#### REMOVED` / `#### SECURITY` tal cual, omitiendo la que
  no tenga items. no se reescribe en parrafos narrativos.
- **tag:** siempre `v<x.y.z>`, sin sufijos adicionales.

las releases anteriores a esta regla (`.docs/releases-docs/release-1.7/`)
usan un formato narrativo distinto y quedan como historicas, sin reescribir.

### Estilo de Código

- `kotlin.code.style=official` (configurado en `gradle.properties`).
- Métodos modulares y aislados.
- No bloquear el `MainThread` en operaciones de datos; usar `Dispatchers.IO` o `Dispatchers.Default`.
- Mantener la separación de capas: UI no conoce DAOs.

---

## 11. Notas para Agentes de IA

- **Idioma preferido para explicaciones técnicas:** Español (coincide con la documentación técnica del proyecto).
- **Al redactar cualquier commit:** aplica las reglas de la sección "Redacción de commits" (10) antes de proponer título o descripción. <!-- formato centralizado para que el historial quede limpio y profesional, más cómodo de leer para usuarios y desarrolladores. -->
- **Al registrar cambios en `changelog.md`:** sigue el formato de la sección "Formato de las secciones de `changelog.md`" (10): minúsculas sin acentos, títulos en mayúsculas sin acentos, contenido bilingüe (castellano e inglés).
- **Al modificar `AGENTS.md`:** replica el cambio equivalente en `CLAUDE.md` (y viceversa) para que ambos ficheros no diverjan.
- **Textos de interfaz en minúsculas:** todos los strings de UI (recursos
  `strings.xml` en cualquier `values-*`, `plurals` y textos hardcodeados) van en
  minúsculas y en todos los idiomas. No aplica a datos del usuario ni a
  especificadores de formato. Ver sección 4.6.
- **No asumas Compose:** La UI es 100% XML + View Binding.
- **No asumas Navigation Component:** La navegación es manual con `FragmentManager`.
- **No asumes KSP:** Usa `kapt` para procesadores de anotaciones.
- **Al modificar Room:** Siempre actualiza la versión de la base de datos y considera una migración manual.
- **Al crear nuevos ViewModels:** Usa `@HiltViewModel`, inyección por constructor, y el patrón `safeLaunch`.
- **Al crear nuevas Activities:** Extiende `BaseActivity` y anota con `@AndroidEntryPoint`.
- **Al modificar temas:** Usa atributos de Material Theme (`?attr/...`) en lugar de colores hardcodeados.
- **Colores semánticos dinámicos:** Se han añadido atributos de tema para estados y prioridades (`colorSuccess`, `colorOnSuccess`, `colorError`, `colorOnError`, `colorPriorityHigh`, `colorPriorityMedium`, `colorPriorityLow`, `colorDateOverdue`). Todos los adapters y el detalle de tarea los resuelven en `init{}`; no debe usarse `Color.parseColor` con hex literals en código de UI.
- **Swipe en listas:** Usa `TaskSwipeHelper` para gestos bidireccionales en RecyclerViews (tareas y listas). Soporta configuración de colores/iconos por dirección y drag opcional. Los íconos de swipe se tinen con `colorOnSuccess` / `colorOnError` para respetar la paleta activa.
- **View Binding obligatorio:** `HomeTaskAdapter` y `TaskListAdapter` usan View Binding; no usar `findViewById` en nuevos adapters.
