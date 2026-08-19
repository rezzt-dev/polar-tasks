# 01 — Análisis exhaustivo de la implementación Supabase

> Inventario completo de todo lo que la sincronización con Supabase introdujo en el proyecto,
> fichero a fichero, verificado contra el commit `aa617df` (tip de `version-1.6`, también en
> `main`). Es la base factual del plan: **todo lo listado aquí tiene una acción asignada en
> `04-plan-de-ejecucion.md`**. Si durante la ejecución aparece algo relacionado con sync que no
> esté en este documento, parar y actualizar ambos.

---

## 1. Topología git

```
version-offline (9bf4a6e) ── base común (merge de PR #6, v1.5)
                            │
version-1.6:  5bf964e  strings lowercase
              bf4bf8b  swipe con OnTouchListener
              80a3bb2  merge PR #7
              bf92f6a  ocultar chip de ordenación
              ec94391  rediseño detalle de tarea + maps
              17b01ce  update (varios)
              a2b5a69  theme creator, stats, settings refresh, swipe helper
              29e0a22  fix namespace en layouts
              7fe3581  elimina custom themes, añade tema onyx   ← padre del commit sync
              aa617df  ★ SUPABASE: sync completo (75 ficheros) ← tip de version-1.6
main:         aa617df + 1 commit de merge (contiene el sync)
```

- `aa617df` es el **único** commit con código Supabase. Está contenido en `main`, `version-1.6`,
  `origin/main` y `origin/version-1.6`. Sin tags.
- `version-offline` no contiene nada de sync, pero tampoco las 9 features previas de 1.6.
- La versión con sync **nunca se publicó** (APK de `app/release/` es anterior; `versionCode`
  nunca se incrementó). El único "despliegue" posible son builds de desarrollo en dispositivos
  del propio autor — tratarlos como instalaciones reales a efectos de migración de DB (ver §4.2).

## 2. Inventario por capas

### 2.1 Build y configuración Gradle

**`gradle/libs.versions.toml`** — entradas añadidas:

```toml
[versions]
kotlinxSerialization = "1.7.3"
supabase = "3.0.1"
ktor = "3.0.0"

[libraries]
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**`app/build.gradle.kts`** — cambios añadidos:

- Plugin `alias(libs.plugins.kotlin.serialization)` (solo lo usan los DTOs de sync).
- Lectura de `local.properties` (`java.util.Properties` + `FileInputStream`).
- `buildConfigField("String", "SUPABASE_URL", ...)` y `SUPABASE_ANON_KEY` (default `""`).
- `buildConfig = true` en `buildFeatures`. **Verificado:** el único consumidor de `BuildConfig`
  en todo `app/src/main` de `version-1.6` es `SupabaseModule.kt:28-29` → el flag se puede retirar.
- `room.schemaLocation` → `$projectDir/schemas` y `sourceSets { androidTest.assets.srcDirs(...) }`.
  **Infraestructura ajena a sync** (sirve para cualquier migración futura) — candidata a conservar.
- Dependencias:
  ```kotlin
  implementation(platform(libs.supabase.bom))              // BOM 3.0.1
  implementation("io.github.jan-tennert.supabase:postgrest-kt")
  implementation("io.github.jan-tennert.supabase:auth-kt")
  implementation("io.github.jan-tennert.supabase:realtime-kt")
  implementation("io.github.jan-tennert.supabase:storage-kt")
  implementation(libs.ktor.client.android)                 // ktor 3.0.0
  implementation(libs.kotlinx.serialization.json)
  testImplementation("io.ktor:ktor-client-mock:3.0.0")     // solo lo usa FakeSupabase
  androidTestImplementation("androidx.room:room-testing:2.6.1")  // MigrationTestHelper; conservar
  ```
- `gradle.properties`, `settings.gradle.kts`, `.gitignore` y el `build.gradle.kts` raíz: **sin
  cambios** por sync.
- `app/proguard-rules.pro`: **sin reglas** de supabase/ktor (la build release con sync nunca se
  minificó). Nada que revertir; anotar que la release offline debe verificarse con R8 como siempre.

### 2.2 AndroidManifest

Diff completo del commit sobre `app/src/main/AndroidManifest.xml`:

```xml
+  <uses-permission android:name="android.permission.INTERNET" />
+  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
...
+    <activity
+      android:name=".ui.activity.AuthActivity"
+      android:exported="false"
+      android:parentActivityName=".MainActivity" />
```

Sin providers, services ni meta-data nuevos. `POST_NOTIFICATIONS` y los receivers de alarmas son
preexistentes.

### 2.3 Capa de datos (Room)

#### AppDatabase — versión 14 → 17, `exportSchema = true`

`.fallbackToDestructiveMigration()` y `JournalMode.WRITE_AHEAD_LOGGING` siguen presentes.

**MIGRATION_14_15** — añade a las 4 tablas `uuid TEXT NOT NULL DEFAULT ''`,
`updatedAt INTEGER NOT NULL DEFAULT 0`, `deletedAt INTEGER DEFAULT NULL`,
`dirty INTEGER NOT NULL DEFAULT 1`; añade además `subtasks.orderIndex INTEGER NOT NULL DEFAULT 0`.
Después un **backfill en Kotlin** (`backfillUuidAndUpdatedAt()`): UUID fresco por fila;
`updatedAt` = `createdAt` (o `now()` para subtasks). `backfillSubtaskOrderIndex()` asigna
`orderIndex` secuencial **particionado por `taskId`**. Finalmente crea 4 índices únicos
`index_<tabla>_uuid`.

**MIGRATION_15_16** — `ALTER TABLE subtasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0` +
`UPDATE subtasks SET createdAt = updatedAt`.

**MIGRATION_16_17** — `ALTER TABLE tasks ADD COLUMN imagePath TEXT DEFAULT NULL`.

> **Nota crítica:** estas migraciones **no se pueden borrar** al eliminar sync — son el camino de
> actualización de cualquier DB v6–v16 hacia la versión final. La limpieza se hace con una
> migración 17→18 posterior (decisión DB-2, doc 02).

#### Entidades — campos añadidos

| Entidad | Campos nuevos | Índice nuevo |
|---|---|---|
| `TaskList` | `uuid`, `updatedAt`, `deletedAt`, `dirty` | `Index(["uuid"], unique = true)` |
| `Task` | los 4 + `imagePath: String?` | idem |
| `Subtask` | los 4 + `orderIndex: Int = 0` + `createdAt: Long` | idem |
| `Reminder` | los 4 | idem |

Cambio semántico introducido por sync: `TaskList` y `Subtask` pasaron a borrado suave por
`deletedAt != null` (antes: borrado físico). `Task` y `Reminder` ya tenían `isDeleted` (papelera)
y además recibieron `deletedAt`.

#### DAOs — cambios

**TaskDao**: `+getAllTasksForListSnapshot`, `+getDirtyTasks` (`WHERE dirty = 1`), `+getByUuid`,
`+updateImageUriCache` (no marca dirty — caché de Storage), `+getTrashCount`,
`+getConfirmedTrashedTasksSnapshot`; `−@Delete delete(task)`, `−softDelete(taskId)`,
`−restore(taskId)`; reescritas con guardas de sync:

```sql
DELETE FROM tasks WHERE id = :taskId AND dirty = 0
  AND NOT EXISTS (SELECT 1 FROM subtasks WHERE subtasks.taskId = tasks.id AND subtasks.dirty = 1)
DELETE FROM tasks WHERE isDeleted = 1 AND dirty = 0
  AND NOT EXISTS (SELECT 1 FROM subtasks WHERE subtasks.taskId = tasks.id AND subtasks.dirty = 1)
```

**TaskListDao**: `getAllLists()`/`getAllTaskListsSnapshot()` ahora filtran `deletedAt IS NULL`;
`+getAllTaskListsIncludingDeletedSnapshot`, `+getDirtyTaskLists`, `+getByUuid`; `−@Delete delete`.

**SubtaskDao**: las 3 consultas por tarea ahora `WHERE taskId = :taskId AND deletedAt IS NULL
ORDER BY orderIndex ASC`; `+getDirtySubtasks`, `+getByUuid`, `+updateAll`; `−@Delete delete`,
`−deleteAllForTask`; `resetSubtasksForTask`/`completeSubtasksForTask` reescritas con
`updatedAt = :updatedAt, dirty = 1` y firma `(taskId, updatedAt)`.

**ReminderDao**: `+updateAll`, `+getTrashCount`, `+getConfirmedTrashedRemindersSnapshot`,
`+getDirtyReminders`, `+getByUuid`; `−@Delete delete`, `−softDelete(id)`, `−restore(id)`;
`emptyTrash()`/`permanentDelete(id)` reescritas con `AND dirty = 0`, devolviendo `Int`.

#### Repositorios

**No inyectan SyncManager** (eso ocurre en ViewModels); solo importan `touched()/touchedDeleted()/
touchedRestored()` de `data.sync`. Cambios:

- `deleteTaskList(taskList)`: de `@Delete` físico a **soft-delete con cascada** de tombstones a
  tareas y subtareas.
- `softDeleteTask(taskId)` → `softDeleteTask(task: Task)` con `touchedDeleted()` + cascada a
  subtareas activas (fix F1).
- `restoreTask(task: Task)` con `touchedRestored()` (sin cascada).
- Eliminados los hard-deletes muertos `deleteTask(task)`/`deleteSubtask(subtask)` (fix F3).
- `deleteAllSubtasksForTask` → `replaceSubtasksForTask(taskId, newSubtasks)`: diff
  insert/update/tombstone (fix F4).
- `permanentDeleteTask(taskId): Boolean`, `emptyTrash(): Int` (cuántos quedaron sin purgar, F5).
- `+cacheTaskImageUri(taskId, imageUri)` (caché de imágenes de Storage).
- `ReminderRepository`: mismo patrón (soft/restore por entidad, purgas con conteo, `−delete()`).

#### Motor de sync — `data/sync/` (todo nuevo)

| Fichero | Líneas | Responsabilidad |
|---|---|---|
| `SyncManager.kt` | 618 | `@Singleton`. Orquesta `sync()` = push→pull; upsert **batch** por tabla en orden lists→tasks→subtasks→reminders; `purgeTombstonesMissingRemote()`; `pushAllOverwrite()`/`pullAllOverwrite()`/`discardLocalAndPullFromCloud()`; `startRealtime()/stopRealtime()` (canal `postgres_changes` por tabla, merge compartido con pull vía `applyXDto()`); reprograma/cancela alarmas vía `AlarmManagerHelper` al aplicar cambios remotos. Inyecta `SupabaseClient`, 4 DAOs, `SyncPrefs`, `AlarmManagerHelper`. |
| `EntityMappers.kt` | 159 | Conversiones puras entidad↔DTO; tags CSV↔`List<String>`; `is_deleted` derivado de `deletedAt`. |
| `EntityTouch.kt` | 42 | Extensiones `touched()`, `touchedDeleted()`, `touchedRestored()` — el patrón "marcar sucio en cada escritura local". **Todas sus llamadas en repos/workers/receivers/ViewModels deben desaparecer.** |
| `MergeResolver.kt` | 41 | LWW puro: `resolvePushOutcome`, `resolvePullAction`, `nextSyncCursor = pullStartedAt - 1`, `extractUuidFromOldRecord`. |
| `SyncPrefs.kt` | 60 | SharedPreferences fichero **`sync_prefs`**: `last_sync_at`, `last_sync_success_at`, `last_sync_error`, `lost_conflicts_count` + `changes(): Flow<Unit>`. |
| `TaskImageStorage.kt` | 57 | Subida/descarga al bucket `task-images` (`{user_id}/{task_uuid}.jpg`); caché en `cacheDir/task_images/`. |
| `dto/` ×4 | ~100 | `TaskListDto`, `TaskDto`, `SubtaskDto`, `ReminderDto` (kotlinx.serialization, snake_case). |

#### BackupManager

**No tocado por el commit.** Serializa con **Gson sobre las entidades completas**:

- Backup hecho con 1.6 → incluye `uuid/updatedAt/deletedAt/dirty/imagePath/orderIndex/createdAt`.
  **Restaurar ese backup en la app offline funciona**: Gson ignora campos desconocidos. ✅
- Ojo al detalle: en 1.6 `exportBackup` usa `getAllTaskListsSnapshot()` que excluye listas
  `deletedAt != null`; tras la eliminación ese filtro desaparece y exporta todo (comportamiento
  v14). Sin acción necesaria.

### 2.4 Capa de aplicación y UI

#### `PolarApplication.kt` (+90 líneas, todo sync)

- `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- `SyncWorker.schedulePeriodic(this)` (siempre, incluso sin sesión).
- `SyncEntryPoint` vía `EntryPointAccessors.fromApplication(...)`.
- Observer `DefaultLifecycleObserver` sobre `ProcessLifecycleOwner`: en `onStart`, si hay sesión,
  `SyncWorker.triggerImmediateSync(...)`.
- Collector `combine(sessionStatus, appInForeground)`: arma/desarma `scheduleFrequentSync` /
  `cancelFrequentSync` y `startRealtime()/stopRealtime()`.
- Estado objetivo offline: `onCreate()` con ThemeManager + NotificationHelper + RecurrenceWorker
  (exactamente como en `7fe3581`).

#### `worker/SyncWorker.kt` (nuevo, 103 líneas)

| Trabajo único | Tipo | Cadencia | Política |
|---|---|---|---|
| `"SyncWorkerPeriodic"` | PeriodicWorkRequest | 15 min | `ExistingPeriodicWorkPolicy.KEEP` |
| `"SyncWorkerOneTime"` | OneTimeWorkRequest | inmediato | `ExistingWorkPolicy.REPLACE` |
| `"SyncWorkerFrequent"` | OneTimeWorkRequest auto-rearmado | 3 min | `ExistingWorkPolicy.REPLACE` |

Todos con `Constraints` de `NetworkType.CONNECTED`; sin tags. Estos identificadores son necesarios
para la limpieza de trabajos huérfanos (doc 04, Fase 5). `"RecurrenceWorker"` es preexistente y
**no** se toca.

#### ViewModels

- **TaskViewModel**: inyecta `TaskImageStorage` + `SyncManager`; `safeLaunch` dispara
  `triggerImmediateSync` tras cada escritura; `touched()` en ~10 métodos; `deleteSubtask` pasa a
  tombstone; `deleteTask()` eliminado; **nuevos** `attachImage()` (sube a Storage) y
  `downloadAndCacheTaskImage()`; `permanentDelete`/`emptyTrash` hacen `sync()` previo y avisan si
  quedan filas sin purgar (strings `trash_item_purge_pending_sync` / `trash_purge_pending_sync_count`).
- **TaskListViewModel** (4 líneas): import `touched`, `triggerImmediateSync` en `safeLaunch`,
  `updateTaskList(taskList.touched())`.
- **RemindersViewModel**: inyecta `SyncManager`; mismo patrón de purga con sync previo;
  `moveToTrash`/`restoreFromTrash` por entidad.

#### Auth (todo nuevo)

- `ui/activity/AuthActivity.kt` (155 líneas, `@AndroidEntryPoint`, extiende `BaseActivity`):
  sign-in con email, "olvidaste tu contraseña" (`resetPasswordForEmail`), diálogo de merge de
  primer login. **Sin sign-up** (decisión deliberada documentada). Único punto de entrada:
  `SettingsFragment.kt:88` (`btnAccount`).
- `ui/viewmodel/AuthViewModel.kt` (150 líneas): `sessionStatus` reactivo, merge decision,
  `signOut`.
- `res/layout/activity_auth.xml` (168 líneas).

#### `SettingsFragment.kt` (+178) y `fragment_settings.xml` (+295)

- Sección "CUENTA Y SINCRONIZACIÓN EN LA NUBE" antes de "APARIENCIA": `layoutSyncConflictWarning`
  (banner de conflictos, con `tvSyncConflictWarning` + `btnDismissSyncConflictWarning`),
  `btnAccount` (+`tvAccountStatus`), `btnSyncNow` (+`tvSyncStatus`), `btnCloudFullOverwrite`,
  `btnCloudFullDownload`.
- Kotlin: `@AndroidEntryPoint` nuevo, `@Inject` de `SupabaseClient`/`SyncManager`/`SyncPrefs`,
  métodos `setupAccountSettings`, `observeAccountStatus`, `updateAccountStatus`,
  `onCloudFullOverwriteClicked`/`performCloudFullOverwrite`, `setupSyncStatus`, `onSyncNowClicked`,
  `observeSyncStatus`, `updateSyncStatus`, `onCloudFullDownloadClicked`/`performCloudFullDownload`.

#### Receivers y workers preexistentes

- **NotificationActionReceiver**: `ACTION_COMPLETE` ahora hace `.touched()`; `ACTION_SNOOZE`
  **nuevo**: persiste `dueDate = snoozeTime` (fix F2 — antes solo reprogramaba la alarma);
  `triggerImmediateSync(context)` al final.
- **RecurrenceWorker**: `.touched()` en el rollover de tareas recurrentes; nueva firma
  `resetSubtasksForTask(task.id, updatedAt)`.
- **TaskDetailActivity** (10 líneas): el picker de imagen llama `viewModel.attachImage()`; nuevo
  branch de bind que resuelve `imagePath` descargando de Storage.
- **MainActivity, DrawerManager, widget de home, resto de fragments**: **limpios** (verificado por
  grep). `fragment_eisenhower.xml` solo recibió un fix cosmético de namespace, sin relación.

### 2.5 Recursos

- **Drawables nuevos:** `ic_cloud_upload.xml`, `ic_cloud_download.xml` — usados solo en
  `fragment_settings.xml`. `ic_sync`, `ic_stat_error`, `ic_arrow_back` son **preexistentes** (no
  tocar). `ic_chevron_right` vino del commit `a2b5a69` (settings refresh), **no** de sync (no tocar).
- **Strings:** 56 claves nuevas, idénticas en los 5 locales (`values`, `values-de`,
  `values-en-rGB`, `values-en-rUS`, `values-fr`). Lista completa en doc 04, Fase 6. Incluye las 2
  de purga (`trash_*_pending_sync*`), toda la familia `auth_*`, `account_*`,
  `cloud_full_overwrite_*`, `cloud_full_download_*`, `sync_status_*`, `sync_conflicts_*`.

### 2.6 Tests

**Nuevos (sync puro, descartables):** `data/sync/EntityMappersTest.kt`,
`data/sync/MergeResolverTest.kt`, `data/sync/SyncManagerTest.kt`, `data/sync/SyncPrefsTest.kt`,
`ui/viewmodel/AuthViewModelTest.kt`, `util/FakeSupabase.kt` (router sobre `ktor-client-mock`),
`androidTest/.../data/MigrationTest.kt` (14→17 con MigrationTestHelper).

**Modificados (requieren adaptación, no borrado):** `TaskRepositoryTest` (tests de cascada y de
purga con conteos), `ReminderRepositoryTest` (nuevo, espejo de purga), `TaskViewModelTest`
(constructor con `taskImageStorage`/`syncManager`, mock estático de `WorkManager`, orden
sync→purge). Detalle de la adaptación en doc 04, Fase 7.

**Schemas exportados:** `app/schemas/app.polar.data.AppDatabase/{14.json, 17.json}` (14.json es
hand-authored como punto de partida del test). `version-offline` no tiene `app/schemas/`.

### 2.7 Documentación

- `agent-docs/supabase-sync/` (11 ficheros, 00–09) + `agent-docs/analisis-implementacion-supabase-sync.md`:
  trackeados en `version-1.6`/`main`, untracked en `version-offline`. Destino: doc 02 §D6.
- `docs/`, `README.md`, `README_features.md`, `AGENTS.md`: **sin menciones a Supabase**. Solo hace
  falta actualizar la versión de Room documentada en `AGENTS.md` ("Room Database (v14)") cuando
  suba a v18, y confirmar que "Sin permisos de red / la app no declara INTERNET" vuelve a ser
  cierto (ya lo era para v14).

### 2.8 Estado residual en dispositivos (invisible en el repo)

Todo lo que una build 1.6 instalada deja en el dispositivo y que **no desaparece** al instalar la
versión offline encima:

| Residuo | Dónde | Efecto si no se limpia |
|---|---|---|
| 3 trabajos únicos encolados | WorkManager (su propia DB) | La clase `SyncWorker` ya no existe → WorkManager marca el trabajo como fallido; ruido en logs y posibles reintentos del periódico |
| `sync_prefs` | SharedPreferences | 4 claves inertes |
| Sesión de Supabase Auth | SharedPreferences del SDK (fichero gestionado por supabase-kt; **nombre exacto por verificar en dispositivo**, ver doc 04 Fase 5) | Token inerte; sin el SDK nadie lo lee |
| Caché de imágenes de Storage | `cacheDir/task_images/` | Ficheros huérfanos |
| DB Room v17 con columnas sync | `databases/polar_database` | Ver §4.2 — el punto más delicado de todo el plan |

### 2.9 Lado servidor (fuera del repo)

Según la auditoría (`agent-docs/analisis-implementacion-supabase-sync.md` §7 y
`supabase-sync/09-fase0-resultado.md`):

- Proyecto Supabase con 4 tablas (`task_lists`, `tasks`, `subtasks`, `reminders`), RLS, trigger
  `touch_and_resolve_lww()`, posibles jobs `pg_cron` (`purge_old_tombstones`, `roll_recurring_tasks`).
- Bucket `task-images`: **confirmado que nunca se creó** — nada que borrar en Storage.
- Usuarios de Auth creados manualmente (no hay sign-up en la app).
- La **app compañera** (doc 07 de supabase-sync) podría seguir usando el proyecto. **Verificar
  antes de borrar nada** (riesgo R6, doc 05).
- Claves: `SUPABASE_URL`/`SUPABASE_ANON_KEY` solo en `local.properties` local (gitignored). Ningún
  secreto commiteado.

## 3. Fixes de integridad empaquetados dentro del commit de sync

`aa617df` no es solo sync: incluye correcciones que nacieron de la auditoría. Esta tabla decide su
destino (detalle y justificación en doc 02 §D3):

| # | Fix | ¿Depende de sync? | Destino en offline |
|---|---|---|---|
| F1 | Cascada de soft-delete a subtareas (`softDeleteTask`, `deleteTaskList`) | Sí (tombstones) | **Descartar** — sin columnas tombstone no aplica; el FK `CASCADE` físico ya cubre la purga |
| F2 | Snooze persiste `dueDate` en Room | No (bug de UX offline) | **Conservar** (sin `.touched()`) |
| F3 | Eliminación de hard-deletes muertos | No (higiene) | **Conservar** — no reintroducir `TaskDao.delete(task)`/`ReminderDao.delete()`; reintroducir solo lo que la UI usa (borrado físico de subtarea y de lista) |
| F4 | `replaceSubtasksForTask` por diff | No (mejora) | **Conservar**, pero el diff cierra con borrado físico en vez de tombstone |
| F5 | Purga devuelve conteo (`Int`/`Boolean`) | Parcialmente (el mensaje era "pendiente de sync") | **Revertir a `Unit`** — offline la purga siempre es completa; los mensajes `trash_*_pending_sync` desaparecen |
| F6 | Guarda `NOT EXISTS ... dirty = 1` en purgas | Sí | **Descartar** — offline el `ON DELETE CASCADE` de subtareas es el comportamiento deseado |
| — | `subtasks.orderIndex` + `createdAt` y `ORDER BY orderIndex` | No | **Conservar** (mejora de ordenación ajena a sync) |
| — | `exportSchema` + `app/schemas` + `room-testing` | No | **Conservar** (necesario para testear la migración 17→18) |

## 4. Trampas no obvias (leer antes de ejecutar nada)

### 4.1. La purga de papelera se rompe si solo "borras sync"

Las queries de purga exigen `dirty = 0`. El default de la columna es `dirty = 1`, y sin sync
**nadie volverá a ponerlo a 0**. Resultado: "vaciar papelera" no borraría nada, silenciosamente.
Cualquier plan que no reescriba `emptyTrash()`/`permanentDelete()` (quitando `AND dirty = 0` y la
guarda de subtareas) deja la papelera inutilizable. Es la trampa funcional más importante.

### 4.2. Downgrade de Room en dispositivos con builds 1.6

- Si la app final se queda en v14 o v15–v16: un dispositivo con DB v17 la abriría en *downgrade*.
  Con `fallbackToDestructiveMigration()` presente, Room **destruye y recrea la DB** (pérdida
  silenciosa de datos), no crashea.
- Si la app final sube a **v18 con migración de limpieza** (recomendado): los dispositivos v17
  migran conservando datos, y los dispositivos v6–v16 migran por la cadena 6→…→17→18.
- Nunca confiar en el fallback destructivo como "estrategia": es invisible para el usuario.

### 4.3. SQLite no tiene `DROP COLUMN` usable en minSdk 24

`DROP COLUMN` exige SQLite 3.35+ (Android 13+). Con `minSdk 24` la única vía portable es recrear
las tablas (create-new/copy/drop/rename). Las FK `CASCADE` (`subtasks→tasks`, `tasks→task_lists`)
exigen hacerlo **en orden hijo→padre** y con `PRAGMA defer_foreign_keys = true` al inicio de la
migración (Room ejecuta las migraciones dentro de una transacción, donde `PRAGMA foreign_keys =
OFF` es un no-op). SQL completo y orden exacto en doc 04, Fase 4.

### 4.4. Trabajos huérfanos de WorkManager

Al desinstalar no pasa nada, pero al **actualizar** (mismo paquete encima), WorkManager conserva
los 3 trabajos únicos encolados con el nombre de clase `app.polar.worker.SyncWorker`, que ya no
existirá. Hay que cancelarlos por nombre una vez en el primer arranque offline (doc 04, Fase 5).

### 4.5. El revert mecánico no sirve para todo

`git revert aa617df` o restaurar todo desde `7fe3581` es tentador, pero:
- perdería F2/F3/F4 y la ordenación de subtareas (hay que rehacerlos);
- devolvería la DB a v14 (trampa 4.2);
- reintroduciría el hard-delete muerto que la auditoría marcó como riesgo.

El plan (doc 04) clasifica cada fichero en **borrar / restaurar desde `7fe3581` / editar a mano**,
con el estado objetivo exacto de cada método afectado.
