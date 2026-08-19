# 04 — Plan de ejecución detallado

> Paso a paso, fichero por fichero, siguiendo las fases de `03-roadmap.md`. Cada fichero del
> commit `aa617df` tiene exactamente una de estas clases de acción:
>
> - **CLASE 1 — Borrar entero** (`git rm`).
> - **CLASE 2 — Restaurar desde el padre del commit sync** (`git checkout 7fe3581 -- <path>`).
>   Solo se usa donde el análisis verificó que *todos* los hunks de `aa617df` en ese fichero son
>   de sync (restaurar devuelve el estado pre-sync exacto, conservando los cambios de los 9
>   commits de features).
> - **CLASE 3 — Edición manual** (conservar fixes, quitar sync). Se da el estado objetivo exacto.
>
> Comandos de verificación al final de cada fase. La numeración de fases coincide con el roadmap.

---

## Fase 0 — Preparación

```bash
# Desde version-offline (donde se generaron estos docs)
git add agent-docs/eliminacion-supabase/
git commit -m "docs(agent): plan de eliminación de supabase"

git checkout -b version-1.6-offline version-1.6
./gradlew :app:assembleDebug   # baseline verde antes de tocar nada
```

En dispositivos con build 1.6 instalada: exportar backup desde la app (red de seguridad).

---

## Fase 1 — Desconectar UI y capa de aplicación

### 1.1 Restaurar desde `7fe3581` (CLASE 2)

```bash
git checkout 7fe3581 -- \
  app/src/main/java/app/polar/PolarApplication.kt \
  app/src/main/java/app/polar/ui/fragment/SettingsFragment.kt \
  app/src/main/res/layout/fragment_settings.xml \
  app/src/main/java/app/polar/ui/viewmodel/TaskListViewModel.kt \
  app/src/main/java/app/polar/ui/activity/TaskDetailActivity.kt \
  app/src/main/java/app/polar/worker/RecurrenceWorker.kt
```

Con esto: `PolarApplication` pierde el observer de `ProcessLifecycleOwner`, el `combine` de
`sessionStatus`, el `applicationScope` y el scheduling de `SyncWorker`; Settings pierde toda la
sección de cuenta/sync (y el `@AndroidEntryPoint` que se añadió solo para ella);
`TaskDetailActivity` vuelve a `updateTask(task.copy(imageUri = ...))` y pierde el branch de
`imagePath`; `RecurrenceWorker` pierde `.touched()` y la firma `updatedAt`.

> **No restaurar** `fragment_eisenhower.xml`: su cambio en `aa617df` es un fix de namespace sin
> relación con sync.

### 1.2 `TaskViewModel.kt` (CLASE 3)

- Constructor: eliminar `taskImageStorage: TaskImageStorage` y `syncManager: SyncManager` (y sus
  imports: `TaskImageStorage`, `SyncManager`, `touched`, `touchedDeleted`, `touchedRestored`).
- `safeLaunch`: quitar la línea `SyncWorker.triggerImmediateSync(getApplication())` tras `block()`.
- Eliminar **enteros** los métodos `attachImage(...)` y `downloadAndCacheTaskImage(...)`.
- En todos los métodos que hacen `copy(...).touched()` / `.touchedDeleted()` /
  `.touchedRestored()`: quitar la llamada, dejar el `copy(...)` plano. Métodos afectados:
  `updateTask`, `setTaskCompletion`, `toggleTaskCompletion`, `toggleSubtaskCompletion`,
  `renameSubtask`, `updateSubtask`, `updateTasksOrder`, `updateTaskListsOrder`,
  `updateTaskGroupsOrder`, y el mapeo de subtareas de `addTask` (**conservar** `orderIndex = index`).
- `deleteSubtask`: de `repository.updateSubtask(subtask.touchedDeleted())` a
  `repository.deleteSubtask(subtask)` (borrado físico, Fase 3 lo reintroduce).
- `permanentDelete` / `emptyTrash`: volver al patrón simple:
  ```kotlin
  fun permanentDelete(task: Task) = safeLaunch { repository.permanentDeleteTask(task.id) }
  fun emptyTrash() = safeLaunch { repository.emptyTrash() }
  ```
  (Sin `sync()` previo, sin `R.string.trash_*_pending_sync`, sin `triggerImmediateSync`.)
- `moveToTrash`/`restoreFromTrash`: conservar firma por entidad (`repository.softDeleteTask(task)`
  / `repository.restoreTask(task)`); el repositorio cambia internamente en Fase 3.

### 1.3 `RemindersViewModel.kt` (CLASE 3)

- Constructor: eliminar `syncManager: SyncManager` e imports (`SyncManager`, `touched`, `R` si
  solo se usaba para los mensajes de purga).
- `safeLaunch`: quitar `triggerImmediateSync`.
- `update`: `reminder.copy(...)` plano (sin `.touched()`).
- `permanentDelete` / `emptyTrash`: patrón simple como arriba; **conservar** en
  `permanentDelete` la cancelación `alarmHelper.cancelReminderAlarm(...)`.
- `moveToTrash`/`restoreFromTrash`: conservar firma por entidad.

### 1.4 `NotificationActionReceiver.kt` (CLASE 3)

- Quitar import de `touched` y la llamada final `SyncWorker.triggerImmediateSync(context)`.
- `ACTION_COMPLETE`: `db.taskDao().update(task.copy(completed = true))` (sin `.touched()`).
- `ACTION_SNOOZE` (**conservar F2**): mantener la persistencia de la fecha, sin `.touched()`:
  ```kotlin
  db.taskDao().update(task.copy(dueDate = snoozeTime))
  alarmHelper.scheduleTaskAlarm(...)   // como ya hacía
  ```

### Verificación Fase 1

```bash
git grep -n "SyncWorker\|SyncManager\|supabase\|Supabase\|touched\|TaskImageStorage\|SyncPrefs" \
  -- app/src/main/java/app/polar/ui app/src/main/java/app/polar/worker app/src/main/java/app/polar/receiver \
     app/src/main/java/app/polar/PolarApplication.kt
# Esperado: sin resultados (ic_sync es un drawable, no aparece en .kt)
```

---

## Fase 2 — Borrado del motor sync, DI y auth (CLASE 1)

```bash
git rm -r app/src/main/java/app/polar/data/sync
git rm app/src/main/java/app/polar/di/SupabaseModule.kt \
       app/src/main/java/app/polar/di/SyncEntryPoint.kt \
       app/src/main/java/app/polar/worker/SyncWorker.kt \
       app/src/main/java/app/polar/ui/activity/AuthActivity.kt \
       app/src/main/java/app/polar/ui/viewmodel/AuthViewModel.kt \
       app/src/main/res/layout/activity_auth.xml \
       app/src/main/res/drawable/ic_cloud_upload.xml \
       app/src/main/res/drawable/ic_cloud_download.xml
git rm -r app/src/test/java/app/polar/data/sync
git rm app/src/test/java/app/polar/ui/viewmodel/AuthViewModelTest.kt \
       app/src/test/java/app/polar/util/FakeSupabase.kt
```

> `data/sync/` incluye: `SyncManager`, `EntityMappers`, `EntityTouch`, `MergeResolver`,
> `SyncPrefs`, `TaskImageStorage`, `dto/{TaskDto,TaskListDto,SubtaskDto,ReminderDto}`.
> `test/.../data/sync/` incluye: `SyncManagerTest`, `EntityMappersTest`, `MergeResolverTest`,
> `SyncPrefsTest`.

### Verificación Fase 2

```bash
./gradlew :app:compileDebugKotlin
# Debe compilar: la capa de datos sync (entidades/DAOs) sigue presente pero sin consumidores.
git grep -rn "import app.polar.data.sync" -- app/src/main   # Esperado: solo data/ (se va en Fase 3)
```

---

## Fase 3 — Capa de datos offline (todo CLASE 3)

### 3.1 Entidades

Para `TaskList`, `Task`, `Subtask`, `Reminder`:

- Eliminar propiedades `uuid`, `updatedAt`, `deletedAt`, `dirty`; en `Task` también `imagePath`.
- Eliminar `indices = [Index(value = ["uuid"], unique = true)]` de la anotación `@Entity`.
- **Conservar** en `Subtask`: `orderIndex: Int = 0` y `createdAt: Long = System.currentTimeMillis()`.
- Imports sobrantes: `java.util.UUID`, `androidx.room.Index` (si no queda otro índice).
- Esquema objetivo completo: doc 02 §D2 (tabla).

### 3.2 DAOs — estado objetivo

**TaskDao**

| Acción | Método / query |
|---|---|
| Eliminar | `getDirtyTasks()`, `getByUuid()`, `updateImageUriCache()`, `getTrashCount()`, `getConfirmedTrashedTasksSnapshot()`, `getAllTasksForListSnapshot()` |
| Reescribir | `@Query("DELETE FROM tasks WHERE id = :taskId") suspend fun permanentDelete(taskId: Long)` |
| Reescribir | `@Query("DELETE FROM tasks WHERE isDeleted = 1") suspend fun emptyTrash()` |
| No reintroducir | `@Delete delete(task)`, `softDelete(taskId)`, `restore(taskId)` (F3: el repo usa `update()`) |

**TaskListDao**

| Acción | Método / query |
|---|---|
| Eliminar | `getDirtyTaskLists()`, `getByUuid()`, `getAllTaskListsIncludingDeletedSnapshot()` |
| Reescribir | `getAllLists()` y `getAllTaskListsSnapshot()`: quitar `WHERE deletedAt IS NULL`, conservar `ORDER BY orderIndex ASC` |
| Reintroducir | `@Delete suspend fun delete(taskList: TaskList)` (lo usa `deleteTaskList`) |

**SubtaskDao**

| Acción | Método / query |
|---|---|
| Eliminar | `getDirtySubtasks()`, `getByUuid()`; `updateAll()` si queda sin uso tras 3.3 |
| Reescribir | Las 3 consultas por tarea: `WHERE taskId = :taskId ORDER BY orderIndex ASC` (sin `deletedAt IS NULL`) |
| Reescribir | `resetSubtasksForTask` → `@Query("UPDATE subtasks SET completed = 0 WHERE taskId = :taskId")` (firma de 1 parámetro, como en `7fe3581`); `completeSubtasksForTask` análogo con `completed = 1` |
| Reintroducir | `@Delete suspend fun delete(subtask: Subtask)` (lo usa `deleteSubtask` y `replaceSubtasksForTask`) |

**ReminderDao**

| Acción | Método / query |
|---|---|
| Eliminar | `getDirtyReminders()`, `getByUuid()`, `getTrashCount()`, `getConfirmedTrashedRemindersSnapshot()`, `updateAll()` si queda sin uso |
| Reescribir | `@Query("DELETE FROM reminders WHERE id = :id") suspend fun permanentDelete(id: Long)` |
| Reescribir | `@Query("DELETE FROM reminders WHERE isDeleted = 1") suspend fun emptyTrash()` |
| No reintroducir | `@Delete delete(reminder)`, `softDelete(id)`, `restore(id)` (F3) |

### 3.3 Repositorios — estado objetivo

**TaskRepository** (quitar imports de `app.polar.data.sync.*`):

```kotlin
suspend fun deleteTaskList(taskList: TaskList) {
  taskListDao.delete(taskList)          // borrado físico; el FK CASCADE limpia tareas y subtareas
}

suspend fun softDeleteTask(task: Task) {          // firma por entidad: se conserva
  taskDao.update(task.copy(isDeleted = true))
}
suspend fun restoreTask(task: Task) {
  taskDao.update(task.copy(isDeleted = false))
}

suspend fun permanentDeleteTask(taskId: Long) = taskDao.permanentDelete(taskId)   // Unit
suspend fun emptyTrash() = taskDao.emptyTrash()                                    // Unit

suspend fun deleteSubtask(subtask: Subtask) = subtaskDao.delete(subtask)  // reintroducido (usado)
// deleteTask(task): NO reintroducir (F3, código muerto)
// cacheTaskImageUri(...): eliminar
```

`replaceSubtasksForTask(taskId, newSubtasks)` (**conservar F4**, adaptado): diff contra lo
almacenado — inserta nuevos con `id = 0, orderIndex = index`; actualiza solo si cambió
title/completed/dueDate/orderIndex; **borra físicamente** (`subtaskDao.delete`) los que ya no
vienen (en vez de tombstone). Sin `.touched()` en ningún punto.

**ReminderRepository**:

```kotlin
suspend fun softDelete(reminder: Reminder) = reminderDao.update(reminder.copy(isDeleted = true))
suspend fun restore(reminder: Reminder)    = reminderDao.update(reminder.copy(isDeleted = false))
suspend fun permanentDelete(id: Long)      = reminderDao.permanentDelete(id)   // Unit
suspend fun emptyTrash()                   = reminderDao.emptyTrash()          // Unit
// delete(reminder): NO reintroducir (F3)
```

### Verificación Fase 3

```bash
git grep -rn "data.sync\|touched\|dirty\|uuid\|imagePath\|deletedAt" -- app/src/main/java/app/polar/data
# Esperado: sin resultados (salvo falsos positivos de nombres ajenos)
# NOTA: el proyecto no compilará hasta completar la Fase 4 (Room valida entidades vs version).
```

---

## Fase 4 — Migración Room 17→18

### 4.1 `MIGRATION_17_18` (en `AppDatabase.kt`, junto a las demás)

Patrón elegido: **copia a tablas temporales + recreación hijo→padre**. No depende de
`PRAGMA foreign_keys = OFF` (no-op dentro de la transacción en la que Room ejecuta las
migraciones): en todo momento ninguna tabla referenciada se elimina mientras tenga hijos vivos,
porque los hijos ya fueron volcados a tablas temporales sin FK y eliminados antes.

```kotlin
private val MIGRATION_17_18 = object : Migration(17, 18) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // ── 1. Volcar hijos a temporales sin FK y eliminarlos (desbloquea a los padres) ──
    db.execSQL("""
      CREATE TABLE temp_subtasks AS
      SELECT id, taskId, title, completed, dueDate, orderIndex, createdAt FROM subtasks
    """.trimIndent())
    db.execSQL("DROP TABLE subtasks")

    db.execSQL("""
      CREATE TABLE temp_tasks AS
      SELECT id, listId, title, description, completed, tags, createdAt, dueDate,
             orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate FROM tasks
    """.trimIndent())
    db.execSQL("DROP TABLE tasks")

    // ── 2. Recrear task_lists (padre de tasks) sin columnas sync ──
    db.execSQL("""
      CREATE TABLE task_lists_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        icon TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        orderIndex INTEGER NOT NULL,
        homeOrderIndex INTEGER NOT NULL,
        isDependencyChain INTEGER NOT NULL,
        color TEXT NOT NULL
      )
    """.trimIndent())
    db.execSQL("""
      INSERT INTO task_lists_new (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color)
      SELECT id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color FROM task_lists
    """.trimIndent())
    db.execSQL("DROP TABLE task_lists")
    db.execSQL("ALTER TABLE task_lists_new RENAME TO task_lists")

    // ── 3. Recrear tasks con su FK a task_lists ──
    db.execSQL("""
      CREATE TABLE tasks (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        listId INTEGER NOT NULL,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        completed INTEGER NOT NULL,
        tags TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        dueDate INTEGER,
        orderIndex INTEGER NOT NULL,
        recurrence TEXT NOT NULL,
        isDeleted INTEGER NOT NULL,
        priority INTEGER NOT NULL,
        imageUri TEXT,
        timeEstimate INTEGER NOT NULL,
        FOREIGN KEY(listId) REFERENCES task_lists(id) ON UPDATE NO ACTION ON DELETE CASCADE
      )
    """.trimIndent())
    db.execSQL("INSERT INTO tasks SELECT * FROM temp_tasks")
    db.execSQL("DROP TABLE temp_tasks")

    // ── 4. Recrear subtasks con su FK a tasks ──
    db.execSQL("""
      CREATE TABLE subtasks (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        taskId INTEGER NOT NULL,
        title TEXT NOT NULL,
        completed INTEGER NOT NULL,
        dueDate INTEGER,
        orderIndex INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
      )
    """.trimIndent())
    db.execSQL("INSERT INTO subtasks SELECT * FROM temp_subtasks")
    db.execSQL("DROP TABLE temp_subtasks")

    // ── 5. Recrear reminders (independiente) ──
    db.execSQL("""
      CREATE TABLE reminders_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        dateTime INTEGER NOT NULL,
        isCompleted INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        isDeleted INTEGER NOT NULL,
        latitude REAL,
        longitude REAL,
        radius REAL,
        locationName TEXT
      )
    """.trimIndent())
    db.execSQL("""
      INSERT INTO reminders_new (id, title, description, dateTime, isCompleted, createdAt, isDeleted,
                                 latitude, longitude, radius, locationName)
      SELECT id, title, description, dateTime, isCompleted, createdAt, isDeleted,
             latitude, longitude, radius, locationName FROM reminders
    """.trimIndent())
    db.execSQL("DROP TABLE reminders")
    db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")

    // ── 6. Verificación de integridad referencial antes de commitear ──
    // OJO: execSQL("PRAGMA foreign_key_check") NO lanza ante violaciones (devuelve filas y
    // execSQL las ignora). Hay que consultar el resultado y fallar explícitamente:
    db.query("PRAGMA foreign_key_check").use { cursor ->
      if (cursor.count > 0) {
        throw IllegalStateException("MIGRATION_17_18: ${cursor.count} violaciones de FK tras recrear tablas")
      }
    }
  }
}
```

Notas de implementación:

- **Antes de escribir el DDL definitivo, cotejarlo con el `createSql` de
  `app/schemas/app.polar.data.AppDatabase/17.json`** (fuente de verdad del compilador) eliminando
  las columnas sync. Room valida al abrir la DB que el esquema real coincide con el esperado por
  las entidades (columnas, nullability, PKs, FKs) — cualquier desviación produce crash al abrir,
  que el `MigrationTest` detecta antes.
- Los 4 índices únicos `index_<tabla>_uuid` desaparecen con las tablas viejas; las entidades v18
  no declaran índices → no hay que recrear ninguno.
- `PRAGMA foreign_key_check` se consulta con `db.query(...)` y se lanza excepción si devuelve
  filas; combinado con la transacción de Room, una violación haría fallar la migración (y caería
  el `fallbackToDestructiveMigration`, visible en el test).
- Registrar en el builder: `.addMigrations(..., MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
  MIGRATION_17_18)` y `version = 18`. **Las migraciones 14→17 se conservan** (doc 02 §D2).
- Si apareciera cualquier problema con el patrón de temporales en un SQLite concreto, la
  alternativa documentada es `PRAGMA defer_foreign_keys = true` al inicio de `migrate()` +
  recreación directa hijo→padre sin temporales.

### 4.2 Extender `app/src/androidTest/java/app/polar/data/MigrationTest.kt`

Añadir caso 17→18 que:

1. Crea DB v17 con `17.json` (MigrationTestHelper).
2. Inserta datos que cubran: lista con tareas, tarea en papelera (`isDeleted=1`), subtareas con
   `orderIndex` distintos de 0, recordatorio con ubicación, filas con `dirty=1` y `uuid` rellenos.
3. Migra a 18 y verifica: conteos idénticos por tabla, valores campo a campo en una fila de cada
   tipo, `orderIndex`/`createdAt` de subtareas conservados, y que las columnas sync ya no existen
   (`PRAGMA table_info(tasks)` sin `uuid`).

### Verificación Fase 4

```bash
./gradlew :app:compileDebugKotlin                 # verde (Room valida entidades vs v18)
./gradlew :app:connectedDebugAndroidTest          # MigrationTest verde — OBLIGATORIO en emulador API 24 (minSdk) y en uno reciente
# Commit del schema exportado:
git add app/schemas/app.polar.data.AppDatabase/18.json
```

---

## Fase 5 — Limpieza de estado residual y versionado ✅

### 5.1 Nombre del fichero de sesión del SDK — resuelto por análisis de fuentes

En vez de depender de un dispositivo con build 1.6 instalada (ninguno disponible, doc 03 Fase 0),
se determinó el nombre leyendo los fuentes de las dependencias en la caché de Gradle:
`SettingsSessionManager` de `auth-kt` usa `createDefaultSettings()` →
`com.russhwolf.settings.Settings()` sin argumentos; en Android (`multiplatform-settings-no-arg`,
`NoArg.kt`) esa función es:

```kotlin
public actual fun Settings(): Settings {
    val preferencesName = "${appContext.packageName}_preferences"
    val delegate = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    return SharedPreferencesSettings(delegate)
}
```

Con `packageName = "app.polar"`, el fichero es determinista: **`app.polar_preferences`**. Se
confirmó con `git grep -rn "getSharedPreferences" -- app/src/main/java/app/polar` que ningún
código propio usa ese nombre (usan `app_prefs`, `polar_prefs`, `task_prefs`), así que
`deleteSharedPreferences("app.polar_preferences")` no arrastra ninguna preferencia propia de la
app.

### 5.2 Bloque de limpieza en `PolarApplication.kt`

```kotlin
override fun onCreate() {
  super.onCreate()
  // ... (ThemeManager, NotificationHelper, RecurrenceWorker: como en 7fe3581)
  runSyncLeftoverCleanup()
}

/**
 * Limpieza única de restos de la sincronización Supabase en dispositivos que ejecutaron
 * una build 1.6: trabajos de WorkManager cuya clase ya no existe, preferencias de sync,
 * sesión persistida del SDK (fichero "<packageName>_preferences", el nombre que usa por
 * defecto `Settings()` de multiplatform-settings en Android) y caché de imágenes de Storage.
 * Ver agent-docs/eliminacion-supabase/ (Fase 5).
 */
private fun runSyncLeftoverCleanup() {
  val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
  if (prefs.getBoolean("sync_cleanup_done", false)) return

  androidx.work.WorkManager.getInstance(this).apply {
    cancelUniqueWork("SyncWorkerPeriodic")
    cancelUniqueWork("SyncWorkerOneTime")
    cancelUniqueWork("SyncWorkerFrequent")
  }
  deleteSharedPreferences("sync_prefs")
  deleteSharedPreferences("${packageName}_preferences")
  java.io.File(cacheDir, "task_images").deleteRecursively()

  prefs.edit().putBoolean("sync_cleanup_done", true).apply()
}
```

> No tocar el trabajo `"RecurrenceWorker"` (preexistente). El bloque es idempotente y puede
> quedarse en el código permanentemente (doc 02 §D4).

### 5.3 Versionado (`app/build.gradle.kts`)

```kotlin
versionCode = 2
versionName = "1.6.1-offline"
```

---

## Fase 6 — Build, manifest y recursos ✅

### 6.1 `gradle/libs.versions.toml` (CLASE 2)

```bash
git checkout 7fe3581 -- gradle/libs.versions.toml
```

### 6.2 `app/build.gradle.kts` (CLASE 3)

Eliminar:

- `import java.util.Properties`, `import java.io.FileInputStream` y el bloque `val localProperties = ...`.
- Plugin `alias(libs.plugins.kotlin.serialization)`.
- Los dos `buildConfigField(...)`.
- `buildConfig = true` (verificado: su único consumidor era `SupabaseModule`).
- Dependencias: `platform(libs.supabase.bom)`, `postgrest-kt`, `auth-kt`, `realtime-kt`,
  `storage-kt`, `libs.ktor.client.android`, `libs.kotlinx.serialization.json`,
  `io.ktor:ktor-client-mock` (test).

**Conservar** (doc 02 §D3.6): `room.schemaLocation`, el `sourceSets` de androidTest y
`androidx.room:room-testing`.

### 6.3 `AndroidManifest.xml` (CLASE 2)

```bash
git checkout 7fe3581 -- app/src/main/AndroidManifest.xml
```

(Fuera `INTERNET`, `ACCESS_NETWORK_STATE` y la entrada de `AuthActivity`.)

### 6.4 Strings (CLASE 2 en los 5 locales)

```bash
git checkout 7fe3581 -- \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-en-rGB/strings.xml \
  app/src/main/res/values-en-rUS/strings.xml \
  app/src/main/res/values-fr/strings.xml
```

Equivale a eliminar estas 56 claves en cada fichero (verificación tras el checkout):

```
trash_item_purge_pending_sync, trash_purge_pending_sync_count,
account_and_cloud_sync, account_row_title, account_signed_in_as, account_not_signed_in,
auth_title, auth_subtitle, auth_email_hint, auth_password_hint, auth_sign_in, auth_sign_out,
auth_error_empty_fields, auth_signed_in_ok, auth_session_expired, auth_forgot_password,
auth_reset_password_title, auth_reset_password_message, auth_reset_password_send,
auth_reset_password_sent_ok, auth_reset_password_error_empty_email,
auth_merge_dialog_title, auth_merge_dialog_message, auth_merge_dialog_upload,
auth_merge_dialog_discard, auth_merge_discard_warning_title, auth_merge_discard_warning_message,
auth_merge_discard_confirm, auth_merge_discard_success, auth_merge_discard_error,
cloud_full_overwrite_title, cloud_full_overwrite_desc, cloud_full_overwrite_warning_title,
cloud_full_overwrite_warning_message, cloud_full_overwrite_confirm, cloud_full_overwrite_in_progress,
cloud_full_overwrite_success, cloud_full_overwrite_error, cloud_full_overwrite_requires_sign_in,
sync_status_row_title, sync_status_never, sync_status_last_success, sync_status_error,
sync_status_syncing, sync_requires_sign_in, sync_conflicts_lost_message, sync_conflicts_dismiss,
cloud_full_download_title, cloud_full_download_desc, cloud_full_download_warning_title,
cloud_full_download_warning_message, cloud_full_download_confirm, cloud_full_download_in_progress,
cloud_full_download_success, cloud_full_download_error, cloud_full_download_requires_sign_in
```

### Verificación Fase 6

```bash
./gradlew :app:assembleDebug :app:assembleRelease
git grep -rni "supabase\|ktor" -- app/src gradle/          # Esperado: sin resultados
git grep -n "auth_\|cloud_full\|sync_status\|account_" -- app/src/main/res   # sin resultados
```

---

## Fase 7 — Adaptación de tests ⚠️ (adaptación + validación automatizada completas; ver 03-roadmap.md para lo pendiente manual)

### 7.1 `TaskViewModelTest.kt` (CLASE 3)

- Constructor del ViewModel: quitar mocks de `taskImageStorage` y `syncManager`.
- Eliminar `mockkStatic(WorkManager::class)` / stubs de `WorkManager.getInstance()` (solo se
  usaban por `SyncWorker.triggerImmediateSync`).
- Tests de purga: reescribir como "delega en el repositorio" (sin `coVerifyOrder` sync→purge, sin
  aserciones sobre strings `trash_*_pending_sync`).
- El resto (delegación en use cases, alarmas) ya era preexistente: mantener.

### 7.2 `TaskRepositoryTest.kt` (CLASE 3)

- Test de cascada de tombstone a subtareas (`softDeleteTask`): **eliminar** (F1 descartado).
- Tests de purga con conteo/Boolean: reescribir como "la purga borra físicamente las filas
  `isDeleted=1` y no toca las demás".
- Tests de `replaceSubtasksForTask` (F4): **conservar**, cambiando la aserción del elemento
  eliminado de "soft-deleteado" a "borrado físicamente".
- Tests de ordenación por `orderIndex` si los hay: conservar.

### 7.3 `ReminderRepositoryTest.kt` (CLASE 3)

- Conservar el fichero; reescribir los tests de conteo como "purgar borra físicamente los
  `isDeleted=1`" y "permanentDelete solo borra el indicado".

### 7.4 `MigrationTest.kt`

- Se conserva y extendió en la Fase 4.2. Los casos 14→17 siguen siendo válidos (la cadena se
  mantiene).

### Verificación Fase 7

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

---

## Commit final de limpieza documental (tras checklist verde, doc 06) ✅

```bash
git rm -r agent-docs/supabase-sync agent-docs/analisis-implementacion-supabase-sync.md
# Actualizar AGENTS.md: Room v14 → v18; confirmar sección "Sin permisos de red".
# Estos docs (agent-docs/eliminacion-supabase/) se conservan como registro de la decisión.
```

Ejecutado 2026-08-20 — `agent-docs/supabase-sync/` (11 ficheros) y
`agent-docs/analisis-implementacion-supabase-sync.md` eliminados de la rama; `AGENTS.md` ya estaba
actualizado desde la Fase 6/7 (v18, migraciones 6→18, tests nuevos documentados).

Y fuera del repo (solo tras validar en dispositivos **físicos** reales — lo hecho en esta sesión
fue en emuladores API 24 y API 37, no en un dispositivo con datos de producción): teardown del
servidor según doc 02 §D5 y borrado de `SUPABASE_URL`/`SUPABASE_ANON_KEY` de `local.properties`.
