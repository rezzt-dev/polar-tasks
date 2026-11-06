# POLAR-TASKS: REPOSITORY GUIDE

## ESPAÑOL DE ESPAÑA: USO OBLIGATORIO DE Ñ

- todo texto en castellano debe usar español de españa (`es-ES`) y su ortografia, tambien en titulos y cuerpos de commits, changelogs, pull requests, merges y documentacion
- escribir siempre `ñ` en las palabras que la llevan y `Ñ` cuando corresponda la mayuscula; nunca sustituirlas por `n` o `N`
- la `ñ` es una letra distinta de la `n`; las instrucciones «sin acentos» o «sin tildes» solo afectan a las tildes de las vocales y nunca permiten eliminar la eñe
- ejemplos correctos: `añadir`, `diseño`, `año`, `tamaño`, `contraseña`, `español`, `españa` y `AÑADIDO`; sus formas con `n` son incorrectas cuando se refieren a esas palabras españolas
- conservar literalmente rutas, urls, nombres de archivo, identificadores tecnicos y texto de otros idiomas; esta regla no autoriza renombrarlos ni aplicar sustituciones indiscriminadas
- antes de entregar o publicar texto en castellano, revisar que ninguna palabra haya perdido su `ñ` o `Ñ`; esta regla prevalece sobre cualquier normalizacion a ascii o instruccion generica de eliminar acentos


android task manager maintained as separate online and offline projects. this guide covers the repository; nested agent instructions apply within their own folders.

## PROJECT OVERVIEW

| property | verified value |
| --- | --- |
| repository | `rezzt-dev/polar-tasks` |
| active source | `./` |
| development base for this change | `version-1.7` |
| remote default branch | `main` |
| version source | `polar-offline/app/build.gradle.kts`; `polar-offline/build.gradle.kts`; `polar-online/app/build.gradle.kts`; `polar-online/build.gradle.kts` |
| continuous integration | no tracked github actions workflows |

## TECHNOLOGY STACK

- android gradle projects: kotlin, xml resources and module configuration declared in the gradle files

## PROJECT STRUCTURE

- `LICENSE`
- `README.md`
- `THIRD_PARTY_NOTICES.md`
- `changelog.md`
- `polar-offline/`
- `polar-online/`

management documentation lives in `.docs/manager.md`, `.docs/-github-guides/` and `.docs/repo-audit-and-fix.md`. keep this file and `CLAUDE.md` consistent.

## BUILD, RUN AND TEST COMMANDS

run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` separately from `polar-online/` and `polar-offline/`. keep local sdk and service configuration out of git.

## VARIANT COMMAND SCOPE

all legacy `app/`, `res/` and `./gradlew` examples below are relative to one variant root, never the repository root. repeat shared feature checks from both `polar-online/` and `polar-offline/`. keep online authentication, remote permissions and synchronization out of the offline variant. room schemas and migrations must correspond to the variant being changed.

do not describe the whole repository as network-free: check each variant's manifest and dependencies. a version marker in a public release does not override `versionName` or `versionCode` declared in the module. preserve `[online]` and `[offline]` scope in release documentation.

## CODE STYLE AND ARCHITECTURAL DECISIONS

preserve both polar-online/ and polar-offline/; online supabase sync must not leak into offline; preserve room migrations, reminders and [online]/[offline] changelog markers. follow the existing formatter and naming of the changed module. verify manifest versions when dependencies change.

## TESTING STRATEGY

documentation-only changes require `git diff --check`, existing-path checks and a review of the staged file list. run module checks for application changes, with disposable data. report unavailable tooling and failed checks; never claim a check passed without running it.

## SECURITY AND DEPLOYMENT

keep credentials, local configuration, signing material and user data out of commits. do not print secret values while auditing. inspect configuration names and code references instead.

existing legal and community files: `LICENSE`, `THIRD_PARTY_NOTICES.md`. when changing dependencies, permissions, storage or network services, review those files against the implementation. record missing policies as audit work; do not invent licence terms or ownership decisions.

deployment, signing, store publication, migrations against hosted databases and repository settings require their own explicit request. this documentation task prepares management instructions and pull requests.

## COMMON TASKS

1. read the relevant module and its nearest agent instructions
2. inspect the working tree and choose the smallest change that solves the request
3. update the affected documentation and changelog with evidence from the diff
4. run checks appropriate to the changed module and report their actual results
5. for git operations, follow the manager and the digest below

## EXISTING PROJECT CONVENTIONS

the following project-specific context is retained from the previous guide. paths are relative to the active module when stated. for git operations, the manager and final digest supersede previous workflow examples.

## AGENTS.MD — GUÍA PARA AGENTES DE CÓDIGO (POLAR)

> este archivo está destinado a agentes de ia que interactúen con el proyecto **polar**.
> el lector se asume con cero contexto previo sobre la aplicación.

---

## 1. VISIÓN GENERAL DEL PROYECTO

**polar** es una aplicación nativa de android para la gestión personal de tareas. está diseñada con una interfaz minimalista, libre de distracciones, pero con herramientas de organización potentes: subtareas anidadas, etiquetas con color, vistas de calendario, recordatorios con alarmas exactas, matriz de eisenhower, estadísticas y un sistema de papelera con borrado suave.

- **idioma principal del código y documentación técnica:** español (docs técnicos), con código kotlin y comentarios mixtos (español/inglés).
- **arquitectura:** mvvm + clean architecture pragmática.
- **persistencia:** offline-first mediante room database (sqlite).
- **variantes:** `polar-offline/` conserva la lógica local; `polar-online/` incorpora sincronización con supabase. verifica cada afirmación de privacidad contra su manifest.

---

## 2. STACK TECNOLÓGICO Y DEPENDENCIAS CLAVE

| capa | tecnología | versión (relevante) |
|------|-----------|---------------------|
| lenguaje | kotlin | 2.0.21 |
| build system | gradle (kotlin dsl) | agp 8.13.2 |
| sdk android | `compileSdk` 36, `minSdk` 24, `targetSdk` 36 |
| ui framework | xml layouts + view binding + material components 1.13.0 |
| arquitectura | mvvm, viewmodel, livedata, stateflow |
| inyección de dependencias | hilt (dagger) | 2.51.1 |
| base de datos | room | 2.6.1 |
| corrutinas | kotlinx coroutines android | 1.7.3 |
| programación de tareas | workmanager | 2.9.0 |
| navegación | fragmentmanager manual (single activity) |
| serialización | gson | 2.10.1 |
| testing unitario | junit 4, mockk, kotlinx-coroutines-test, androidx.arch.core:core-testing |
| testing instrumentado | androidx test (junit + espresso) |

> **nota importante:** el proyecto usa `kapt` (no ksp) para el procesamiento de anotaciones de room y hilt.

---

## 3. ESTRUCTURA DEL CÓDIGO FUENTE

el código reside por separado en `polar-online/app/` y `polar-offline/app/`; las rutas `app/` de los ejemplos se interpretan desde la variante correspondiente.

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

### RECURSOS (`app/src/main/res/`)

- `layout/` — ~35 archivos xml de layouts (activities, fragments, items, dialogs).
- `menu/` — menús de toolbar/actionbar.
- `drawable/` — ~61 recursos gráficos, vectores y formas.
- `values/` — strings, colores, temas (solo color), estilos del sistema de diseño, dimensiones y atributos personalizados.
- `values-night/` — tema y colores del modo oscuro.
- `anim/`, `color/`, `font/` — animaciones, listas de estados de color y la fuente inter empaquetada.
- `xml/` — configuración de widgets, fileprovider paths, reglas de backup.

> **no existe carpeta `navigation/`**: la navegación se maneja manualmente con `FragmentManager` desde `MainActivity` y `DrawerManager`.

---

## 4. CONVENCIONES DE DESARROLLO

### 4.1 PATRONES ARQUITECTÓNICOS

- **separación de capas estricta:** la ui (fragment/activity) **nunca** accede directamente a daos. siempre pasa por viewmodel -> repository -> dao.
- **reactivo:** los daos exponen `Flow<List<T>>` o `LiveData<List<T>>`. el viewmodel transforma estos flujos en `StateFlow` o `LiveData` para la ui.
- **viewmodels con hilt:** usan `@HiltViewModel` e inyección por constructor `@Inject`. son `AndroidViewModel` cuando necesitan `Application`.
- **activities con hilt:** toda activity que requiera inyección debe llevar `@AndroidEntryPoint`.

### 4.2 MANEJO DE ERRORES EN VIEWMODELS

casi todos los viewmodels implementan un patrón `safeLaunch` para encapsular corrutinas:

```kotlin
private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
    try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()
        _errorMessage.value = "error: ${e.message}"
    }
}
```

si creas nuevos viewmodels, **replica este patrón** para consistencia.

### 4.3 VIEW BINDING (OBLIGATORIO)

se usa **view binding** en toda la ui. está habilitado en `build.gradle.kts` (`viewBinding = true`).
- prohibido usar `findViewById`.
- los `ViewHolder` de adapters reciben el binding como parámetro.

### 4.4 BASEACTIVITY

todas las activities heredan de `BaseActivity`, que gestiona:
- aplicación de tema dinámico (multicolor, oscuro, pastel, etc.) antes de `super.onCreate()`.
- overlay del sistema de diseño (`ThemeOverlay.Polar.Foundation`, ver 4.7) sobre cualquier tema.
- overlay de fuentes personalizadas.
- overlay de estilos de checkbox.
- configuración de locale (idioma) vía `attachBaseContext()`.

> si creas una nueva activity, extiende `BaseActivity`.

### 4.5 TEMAS Y ATRIBUTOS

los colores **no deben hardcodearse** con valores hex absolutos en layouts. se usan atributos del tema:
```xml
android:background="?attr/colorSurface"
android:textColor="?attr/colorOnSurface"
```

el `ThemeManager` soporta cambio en tiempo de ejecución de temas, fuentes e idioma.

para añadir un tema: define su paleta en `colors.xml`, su estilo `Theme.Polar.*` en `themes.xml`
y regístralo en `ThemeManager.THEME_OPTIONS` (clave, nombre, estilo y si es oscuro). el selector
(`ThemePickerDialog`), el modo noche y la barra de estado se derivan de esa lista.

**tema personalizado** (`ThemeManager.THEME_CUSTOM`): el usuario combina un fondo, un color de
acento y un color por prioridad en `CustomThemeActivity` (se abre desde el grupo "tu tema" del
selector). no se generan colores en tiempo de ejecución: cada fondo es un tema completo
(`Theme.Polar.Custom.*`) y el acento y cada prioridad son overlays con tono claro y oscuro
(`ThemeOverlay.Polar.Custom.*`) que `BaseActivity` aplica sobre él con
`ThemeManager.getColorOverlayStyles()`. la combinación se guarda en preferencias como claves
(`CustomTheme`) y las opciones se registran en `CustomThemePalette`. `colors_custom_theme.xml` y
`themes_custom.xml` se generan con `.docs/agent-docs/tools/custom_theme_palette.py`, que valida el
contraste wcag: no los edites a mano. para añadir una opción, añádela al script, regístrala en
`CustomThemePalette` y añade su texto en los cinco idiomas; no renombres las claves existentes.
ajustes recrea la app al volver si cambia `ThemeManager.themeSignature()`, que incluye la
combinación del personalizado.

**colores a medida** (android 11+): cada hueco de la combinación (fondo, acento y cada prioridad)
admite también un `#RRGGBB`, elegido en `CustomColorSheet` (selector hsv, código hex, contraste y
recientes). se pintan con estilos "portadores" (`Theme.Polar.Custom.Hex.Light/Dark`,
`ThemeOverlay.Polar.Custom.*.Hex`, generados por el mismo script) que apuntan a los colores
`custom_hex_*`; `BaseActivity` sustituye su valor antes de crear el tema con
`CustomColorResources.apply()`, que carga una tabla de recursos en memoria con un
`ResourcesLoader`. los tonos derivados (superficies, textos, contornos, texto sobre el acento) los
calcula `CustomThemeScheme`, que replica las fórmulas del script: si cambias unas, cambia las otras.
en android 10 o anterior (o si la carga falla) `CustomColorResources.isSupported` es `false`, el
editor oculta los colores a medida y `CustomThemePalette.sanitize()` los sustituye por los de por
defecto. las vistas previas usan `ThemePreviewColors.forTheme()` / `forCustomTheme()`, que calculan
los colores a medida sin tocar los recursos. no renombres los colores `custom_hex_*` sin actualizar
`CustomThemePalette.colorOverrides()`.

### 4.6 TEXTOS DE INTERFAZ (SIEMPRE EN MINÚSCULAS)

toda la interfaz de polar se escribe **en minúsculas**, en **todos los idiomas**
(incluido alemán, donde los sustantivos normalmente se capitalizan, y nombres
propios como `polar`, `material` o `eisenhower`).

- aplica a los recursos de `app/src/main/res/values*/strings.xml`, incluidos los
  bloques `<plurals>`, y a cualquier texto hardcodeado en layouts o código.
- **no** aplica a datos introducidos por el usuario (títulos de tareas, notas,
  etiquetas, ubicaciones, etc.) ni a los especificadores de formato (`%1$s`,
  `%d`, `\n`).
- al añadir una funcionalidad nueva o traducir strings, replica esta convención
  en `values/`, `values-en-rGB/`, `values-en-rUS/`, `values-de/` y `values-fr/`.
- minúsculas no significa sin acentos (conservando siempre `ñ` y `Ñ`): la ortografía de cada idioma es obligatoria
  (tildes, `ñ`, `¿` y `¡` en castellano; `ä`, `ö`, `ü`, `ß` en alemán; acentos y
  espacio antes de `?`, `!` y `:` en francés). el tono es el mismo en todas las
  pantallas: tuteo en castellano, `du` en alemán y `vous` en francés.
- las fechas que genera el sistema (`SimpleDateFormat`, `DateFormat`) se pasan a
  minúsculas con `lowercase(locale)` y se construyen con
  `DateFormat.getBestDateTimePattern()` en lugar de patrones fijos.
- las cantidades usan `<plurals>`; en francés se define también `many`.

### 4.7 SISTEMA DE DISEÑO

la interfaz sigue un sistema de diseño único, minimalista y neutro, compartido por
todos los temas:

- **fundamentos (`res/values/styles.xml`):** `ThemeOverlay.Polar.Foundation`
  define tipografía (`TextAppearance.Polar.*`), formas (`ShapeAppearance.Polar.*`:
  10/12/16/24dp), estilos por defecto de los componentes de material y las
  animaciones de ventana. `BaseActivity` lo aplica antes de los overlays de fuente
  y casilla. los temas de `themes.xml` solo definen color (todos los roles de
  material 3, incluidos `colorOnSurfaceVariant`, contenedores y superficies
  inversas para snackbars).
- **tipografía:** en layouts usa siempre `?attr/textAppearance*` (nunca
  `@style/TextAppearance.Material3.*` ni `textSize`/`fontFamily` sueltos) para
  respetar la fuente elegida. la fuente por defecto es inter, empaquetada en
  `res/font/` (funciona sin red). en vistas dibujadas con `Canvas` usa
  `ChartTypeface.resolve()`.
- **componentes:** usa los estilos `Widget.Polar.*` (botones, chips, tarjetas,
  campos de texto, menús, hojas inferiores) y las piezas de layout `Polar.*`
  (`Polar.DrawerItem`, `Polar.FormRow`, `Polar.EmptyState`, `Polar.DialogActions`,
  `Polar.InlineInput`...). los menús emergentes son `androidx.appcompat.widget.PopupMenu`
  con `applyPolarStyle()` (iconos y acción destructiva en `colorError`).
- **iconos:** pack [lucide](https://lucide.dev) convertido a vector drawable
  (viewport 24, trazo 1.75, `android:tint="?attr/colorControlNormal"`). los
  nombres `ic_*` se conservan porque los iconos de lista se guardan por nombre en
  la base de datos; no los renombres. para añadir uno nuevo usa
  `.docs/agent-docs/tools/lucide2vd.py` (mapeo `nombre -> icono lucide`).
  créditos y licencias en `THIRD_PARTY_NOTICES.md`.
- **espaciado:** margen lateral de pantalla `@dimen/spacing_screen` (20dp) y escala
  de 4dp. no uses `paddingHorizontal`/`layout_marginHorizontal` (api 26, ignorados
  en android 7): usa `Start`/`End` y `Top`/`Bottom`.
- **movimiento:** cambio de sección con fade through (`polar_fade_through_*`,
  desde `MainActivity.showScreen()`), activities con `Animation.Polar.Activity`,
  listas con `@anim/polar_list_layout` y respuesta háptica al completar o al cruzar
  el umbral de un deslizamiento.
- **widget:** los remoteviews no resuelven atributos de tema; usa los colores
  `@color/widget_*` (con variante en `values-night`).

---

## 5. BASE DE DATOS (ROOM)

### ENTIDADES PRINCIPALES

| entidad | tabla | clave |
|---------|-------|-------|
| `Task` | `tasks` | `id` (auto) |
| `TaskList` | `task_lists` | `id` (auto) |
| `Subtask` | `subtasks` | `id` (auto), fk a `tasks.id` |
| `Reminder` | `reminders` | `id` (auto) |

### CARACTERÍSTICAS DEL ESQUEMA

- `Task` tiene una foreign key a `TaskList` con `onDelete = CASCADE`.
- soft-delete: los campos `isDeleted` en `Task` y `Reminder` marcan elementos en papelera en lugar de borrarlos físicamente.
- tags se almacenan como cadena separada por comas en `Task.tags`.
- recurrencia: campo `recurrence` con `"NONE"`, uno de los valores fijos (`"DAILY"`, `"WEEKLY"`, `"MONTHLY"`, `"MON_WED"`, `"FIRST_DAY_MONTH"`) o una regla personalizada `RRULE:FREQ=...` (cada n días/semanas/meses/años, días concretos de la semana o del mes, o "n después de completarla" con `X-FROM=COMPLETION`). `RecurrenceRule` (`util/`) la lee y la escribe, y guarda con el valor fijo cualquier regla equivalente; `RecurrenceFormatter` genera su texto de interfaz. no compares `recurrence` con literales: usa `RecurrenceUtils.isRecurring()`. al añadir una regla nueva no hace falta migración, pero sí actualizar el `CHECK` de `tasks.recurrence` en supabase (`.docs/agent-docs/supabase-sync/03-esquema-supabase.md`).
- prioridad: entero `0=None, 1=Low, 2=Medium, 3=High`.
- avisos de tarea: `Task.reminderOffset` son los minutos antes de `dueDate` a los que suena (`0` a la hora, `TaskAlert.OFF` sin aviso) y `Task.snoozedUntil` el aviso pospuesto desde la notificación, que sustituye al normal **sin tocar `dueDate`**. la hora real la calcula `TaskAlert.triggerAt()`; programa siempre con `AlarmManagerHelper.syncTaskAlarm(task)` (y `syncReminderAlarm`) en lugar de llamar a `scheduleTaskAlarm` con `dueDate`.
- aviso al llegar: `Reminder.notifyOnArrival` activa, además del aviso a su hora, una alerta de proximidad sobre `latitude`/`longitude` con `radius` en metros (`GeofenceHelper`). en `polar-online/` estos tres campos son locales del dispositivo: no van en los dto y `EntityMappers` los conserva en cada pull.

### MIGRACIONES

`AppDatabase` define migraciones manuales desde la 6→7 (versión actual: 19 en `polar-offline/`, 18 en `polar-online/`; cada proyecto numera las suyas). la base de datos usa **wal** (`JournalMode.WRITE_AHEAD_LOGGING`) para permitir lecturas concurrentes sin bloquear la ui.

> si alteras el esquema, **aumenta la versión** y proporciona una `Migration` explícita. no confíes únicamente en `fallbackToDestructiveMigration`.

---

## 6. SERVICIOS EN SEGUNDO PLANO

### ALARMMANAGER + BROADCASTRECEIVERS (`receiver/`)

para recordatorios con precisión exacta se usa `AlarmManager` con `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`.
- `AlarmReceiver` — dispara la notificación cuando suena la alarma.
- `AlarmReceiver` — dispara la notificación cuando suena la alarma, tras comprobar en la base de datos que el elemento sigue pendiente y fuera de la papelera.
- `BootReceiver` — reconstruye todas las alarmas y avisos por llegada con `AlarmRescheduler` tras reiniciar el dispositivo, al actualizar la app y al conceder el permiso de alarmas exactas.
- `NotificationActionReceiver` — botón "completar" de las notificaciones de tareas y recordatorios; delega en `AlertActions`, que no hace nada si el elemento ya se completó o se borró.
- `SnoozeActivity` + `SnoozeSheet` — botón "posponer": activity transparente que muestra la hoja de opciones (15 min, 1 h, 3 h, esta tarde, mañana, a medida) sin abrir la app. la misma hoja se usa desde el menú de cada recordatorio. las opciones rápidas se calculan en `SnoozeOption`.
- `GeofenceHelper` (`util/`) — registra el aviso al llegar con `LocationManager.addProximityAlert` (sin google play services); el aviso llega a `AlarmReceiver` como `TYPE_REMINDER_ARRIVAL`.
- `AlarmRescheduler` (`util/`) — única fuente para reprogramar o cancelar en bloque las alarmas desde la base de datos. úsalo siempre que los ids dejen de ser válidos (por ejemplo, al restaurar una copia) en lugar de recorrer tablas a mano.
- los textos de notificaciones y widget se generan con `ThemeManager.localizedContext()`: el contexto de aplicación no lleva el idioma elegido en ajustes.

### WORKMANAGER (`worker/`)

- `RecurrenceWorker` — se ejecuta cada hora y al abrir la app para reabrir las tareas recurrentes completadas cuya siguiente ocurrencia ya ha empezado (reglas de fechas en `RecurrenceUtils` y `RecurrenceRule`).

### WIDGET (`widget/`)

- `TaskWidgetProvider` / `TaskWidgetService` — tareas pendientes de hoy; al tocar una se abre su detalle. `PolarApplication` registra un observador de room sobre `tasks` que refresca el widget ante cualquier escritura, así que no hace falta avisarle desde cada punto que modifica tareas.

---

## 7. COMANDOS DE BUILD Y TEST

### COMPILACIÓN

```bash
# Compilar el proyecto (debug)
./gradlew :app:assembleDebug

# Compilar release
./gradlew :app:assembleRelease
```

### TESTS

```bash
# Tests unitarios (JVM)
./gradlew :app:testDebugUnitTest

# Tests instrumentados (requiere emulador/dispositivo)
./gradlew :app:connectedDebugAndroidTest
```

### VERIFICACIÓN GENERAL

```bash
# Limpiar y compilar
./gradlew clean build
```

### REQUISITOS DEL ENTORNO

- android studio flamingo o superior.
- jdk 17.
- android sdk api 36 (compilesdk).
- kotlin 2.0+.

---

## 8. ESTRATEGIA DE TESTING

### TESTS UNITARIOS (`app/src/test/`)

la suite de tests unitarios es pequeña pero representativa:

- `TaskViewModelTest` — verifica que el viewmodel delega correctamente al usecase y al repository, y que la programación/cancelación de alarmas ocurre en los estados esperados.
- `TaskRepositoryTest` — verifica que el repository expone flujos de dao y traduce operaciones crud.
- `SmartParserTest` — tests del parser nlp para extracción de fechas/tiempos de texto natural.
- `MainDispatcherRule` — regla de junit para reemplazar el dispatcher principal en tests de corrutinas.

**herramientas:** mockk para mocks, `InstantTaskExecutorRule` para livedata, `runTest` para corrutinas.

### TESTS INSTRUMENTADOS (`app/src/androidTest/`)

- `ExampleInstrumentedTest` — test básico de contexto de la app.

### CONVENCIONES PARA NUEVOS TESTS

- usa `runTest` para funciones suspendidas.
- usa `MockK` (no mockito) para consistencia con el proyecto existente.
- usa `MainDispatcherRule` en tests de viewmodels que usen corrutinas.
- aísla el viewmodel mockeando repository y usecases.

---

## 9. CONSIDERACIONES DE SEGURIDAD

- **sin permisos de red:** la app no declara `INTERNET`. es completamente offline.
- **almacenamiento externo limitado:** solo lectura de imágenes (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` hasta api 32). usa `FileProvider` para compartir archivos de backup de forma segura.
- **ubicación (opcional):** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` y `ACCESS_BACKGROUND_LOCATION` solo se piden al activar el aviso al llegar de un recordatorio (`ReminderDialog` explica antes el acceso "todo el tiempo"). la ubicación no sale del dispositivo. no pidas estos permisos desde ningún otro punto.
- **alarmas exactas:** requiere `SCHEDULE_EXACT_ALARM` y `USE_EXACT_ALARM`. en android 12+ (api 31), el sistema puede restringir el uso; la app debe manejar la posibilidad de que el permiso sea revocado por el usuario.
- **backup:** habilitado (`android:allowBackup="true"`) con reglas de extracción declaradas en `data_extraction_rules.xml` y `backup_rules.xml`.
- **no hay cifrado de base de datos:** room usa sqlite estándar. no se implementa sqlcipher ni cifrado a nivel de archivo.

---

## 11. NOTAS PARA AGENTES DE IA

- **idioma preferido para explicaciones técnicas:** español (coincide con la documentación técnica del proyecto).
- **al redactar cualquier commit:** aplica las reglas de la sección "redacción de commits" (10) antes de proponer título o descripción.
- **al registrar cambios en `changelog.md`:** sigue el formato de la sección "formato de las secciones de `changelog.md`" (10): minúsculas sin acentos (conservando siempre `ñ` y `Ñ`), títulos en mayúsculas sin acentos (conservando siempre `ñ` y `Ñ`), contenido bilingüe (castellano e inglés).
- **al modificar `AGENTS.md`:** replica el cambio equivalente en `CLAUDE.md` (y viceversa) para que ambos ficheros no diverjan.
- **textos de interfaz en minúsculas:** todos los strings de ui (recursos
  `strings.xml` en cualquier `values-*`, `plurals` y textos hardcodeados) van en
  minúsculas y en todos los idiomas. no aplica a datos del usuario ni a
  especificadores de formato. ver sección 4.6.
- **no asumas compose:** la ui es 100% xml + view binding.
- **no asumas navigation component:** la navegación es manual con `FragmentManager`.
- **no asumes ksp:** usa `kapt` para procesadores de anotaciones.
- **al modificar room:** siempre actualiza la versión de la base de datos y considera una migración manual.
- **al crear nuevos viewmodels:** usa `@HiltViewModel`, inyección por constructor, y el patrón `safeLaunch`.
- **al crear nuevas activities:** extiende `BaseActivity` y anota con `@AndroidEntryPoint`.
- **al modificar temas:** usa atributos de material theme (`?attr/...`) en lugar de colores hardcodeados. los temas predefinidos se registran en `ThemeManager.THEME_OPTIONS`; el personalizado combina estilos generados por `custom_theme_palette.py` desde `CustomThemePalette`; sus colores a medida (`#RRGGBB`, android 11+) solo sustituyen en tiempo de ejecución los colores `custom_hex_*` de los estilos portadores con `CustomColorResources` (sección 4.5).
- **colores semánticos dinámicos:** se han añadido atributos de tema para estados y prioridades (`colorSuccess`, `colorOnSuccess`, `colorError`, `colorOnError`, `colorPriorityHigh`, `colorPriorityMedium`, `colorPriorityLow`, `colorDateOverdue`). todos los adapters y el detalle de tarea los resuelven en `init{}`; no debe usarse `Color.parseColor` con hex literals en código de ui.
- **swipe en listas:** usa `TaskSwipeHelper` para gestos bidireccionales en recyclerviews (tareas y listas). soporta configuración de colores/iconos por dirección y drag opcional. los íconos de swipe se tinen con `colorOnSuccess` / `colorOnError` para respetar la paleta activa.
- **view binding obligatorio:** `HomeTaskAdapter` y `TaskListAdapter` usan view binding; no usar `findViewById` en nuevos adapters.

## ADDITIONAL ENTRY-POINT CONVENTIONS

## CLAUDE.md — Guía para Claude Code (Polar)

> Este archivo orienta a Claude Code (y a cualquier agente de IA) al trabajar
> sobre el proyecto **Polar**. El contexto completo del proyecto (stack,
> arquitectura, estructura de código, base de datos, testing, seguridad) está
> en [`AGENTS.md`](AGENTS.md): **léelo siempre antes de tocar código**.
>
> `CLAUDE.md` y `AGENTS.md` deben mantenerse sincronizados: si cambias uno,
> replica el cambio equivalente en el otro.

---

## Notas rápidas

- **Idioma para explicaciones técnicas:** Español.
- **Interfaz siempre en minúsculas:** todos los textos de UI de la aplicación
  van **en minúsculas y en todos los idiomas** (incluido alemán, y nombres
  propios como `polar`, `material` o `eisenhower`). Aplica a los recursos de
  `res/values*/strings.xml` (incluidos `plurals`) y a cualquier texto
  hardcodeado en layouts o código. **No** aplica a datos introducidos por el
  usuario (títulos de tareas, notas, etiquetas, etc.) ni a los especificadores
  de formato (`%1$s`, `%d`). Al añadir una funcionalidad o traducir strings,
  replica esta convención en todos los `values-*`.
- **Ortografía:** minúsculas no significa sin acentos: tildes, `ñ`, `¿`/`¡`,
  umlauts y acentos franceses son obligatorios. Tono: tuteo, `du` y `vous`.
  Las fechas del sistema se generan con `DateFormat.getBestDateTimePattern()` y
  se pasan a `lowercase(locale)`.
- **Sistema de diseño:** tipografía con `?attr/textAppearance*`, componentes
  `Widget.Polar.*` y piezas `Polar.*`, iconos Lucide (`ic_*`, sin renombrar),
  fuente Inter empaquetada y nada de `paddingHorizontal`/`marginHorizontal`
  (API 26). Detalle en la sección 4.7 de `AGENTS.md`.
- **UI:** 100% XML + View Binding (no Compose, no Navigation Component).
- **Procesador de anotaciones:** `kapt` (no KSP).
- **Room:** al alterar el esquema, sube la versión y añade `Migration` manual.
- **ViewModels:** `@HiltViewModel`, inyección por constructor y patrón
  `safeLaunch`.
- **Activities:** extienden `BaseActivity` y se anotan con `@AndroidEntryPoint`.
- **Colores:** usa atributos de tema (`?attr/...`), nunca hex hardcodeados.
- **Temas:** los predefinidos se registran en `ThemeManager.THEME_OPTIONS`. El personalizado
  (`THEME_CUSTOM`, editor `CustomThemeActivity`) combina un tema de fondo y overlays de acento y
  prioridades en tono claro y oscuro; sus opciones viven en `CustomThemePalette` y su XML
  (`colors_custom_theme.xml`, `themes_custom.xml`) se genera con
  `.docs/agent-docs/tools/custom_theme_palette.py`, nunca a mano. Cada hueco admite además un color
  a medida `#RRGGBB` (`CustomColorSheet`, android 11+): se pinta con los estilos portadores `*.Hex`
  cuyos colores `custom_hex_*` sustituye `CustomColorResources` en `BaseActivity`, con los tonos de
  `CustomThemeScheme` (replica las fórmulas del script). Detalle en la sección 4.5 de `AGENTS.md`.
- **Alarmas:** se programan desde ViewModels/receivers con `AlarmManagerHelper`, nunca desde la UI;
  para reconstruirlas o cancelarlas en bloque usa `AlarmRescheduler`. La hora del aviso de una
  tarea la decide `TaskAlert` (antelación `reminderOffset`, aviso pospuesto `snoozedUntil`): usa
  `syncTaskAlarm(task)` y nunca reescribas `dueDate` para posponer. El aviso al llegar de un
  recordatorio va por `GeofenceHelper` y es el único uso de los permisos de ubicación. Los textos
  generados fuera de una activity (notificaciones, widget) usan `ThemeManager.localizedContext()`.
- **Recurrencia:** `Task.recurrence` guarda un valor fijo (`DAILY`, `MON_WED`...) o una regla
  `RRULE:...`; léela y escríbela con `RecurrenceRule`, muéstrala con `RecurrenceFormatter` y no la
  compares con literales (`RecurrenceUtils.isRecurring()`). Detalle en la sección 5 de `AGENTS.md`.
- **Commits:** aplica la sección "Redacción de commits" antes de proponer título o descripción.
- **`changelog.md`:** registra los cambios según la sección "Formato de las secciones de `changelog.md`".
- **Pull requests, merges y releases:** aplica la sección "Pull requests, merges y releases" antes de redactar cualquiera de los tres.

El resto de convenciones y detalle está en [`AGENTS.md`](AGENTS.md).

## COMMIT, PR, MERGE & RELEASE GUIDELINES

read `.docs/manager.md` first, then the selected guide in `.docs/-github-guides/`. these files are versioned; this digest also works without opening them.

| request | guide |
| --- | --- |
| commit title, body and changelog | `commit-guide.md`, `changelog-guide.md` |
| pull request or merge | `pr-merge-guide.md`, `branching-guide.md` |
| version and release | `release-guide.md`, `changelog-guide.md` |
| permissions and sensitive data | `agent-rules.md` |

- inspect `git status`, both diffs and recent history before describing changes
- use `<prefix> | <imperative summary>` with `update`, `fix`, `hotfix`, `changes` or `changelog`; keep titles within 72 characters
- write commit bodies as 2 to 6 markdown bullets, one logical change per line, with literal paths in backticks
- never add authorship footers, automated attribution, signatures or generation notices; preserve the configured git identity
- update `changelog.md` under `## SIN PUBLICAR / UNRELEASED` in matching `### CASTELLANO` and `### ENGLISH` sections
- use the existing changelog category vocabulary and preserve language parity; describe only changes in the actual diff
- summarize pull requests in 1 to 3 bullets per category and language, at most 110 characters per bullet
- documentation and repository-management work targets `version-1.7`; the remote default is `main`; do not silently promote development work to the default branch
- the authorization for a requested action remains valid throughout that task; creating a pull request includes pushing its branch, but does not authorize merging or publishing
- use merge commits when a merge is requested; never force-push, rewrite published history or remove branches without an explicit instruction
- releases use the approved version and an immutable tag on the verified release commit, with english notes from the closed changelog; do not create a release for documentation work
- deliver requested titles, bodies and changelog fragments in separate code blocks; report executed actions with their commit hash or pull request link
