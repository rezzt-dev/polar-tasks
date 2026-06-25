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
3. Commits con formato semántico: `<tipo>(<alcance>): <descripción>`.
   - Ejemplos: `feat(ui): add rainbow border tag pattern`, `fix(database): resolve memory leak in dao collection`.
4. Asegurar que el código compila y los tests unitarios pasan.
5. Pull Request con capturas de pantalla si hay cambios visuales.

### Estilo de Código

- `kotlin.code.style=official` (configurado en `gradle.properties`).
- Métodos modulares y aislados.
- No bloquear el `MainThread` en operaciones de datos; usar `Dispatchers.IO` o `Dispatchers.Default`.
- Mantener la separación de capas: UI no conoce DAOs.

---

## 11. Notas para Agentes de IA

- **Idioma preferido para explicaciones técnicas:** Español (coincide con la documentación técnica del proyecto).
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
