# 02 — Decisiones y estrategia

> Las 8 decisiones que gobiernan la eliminación. Cada una presenta opciones con pros/contras y una
> **recomendación**. Si se cambia alguna decisión durante la ejecución, actualizar este documento
> y revisar el impacto en `03-roadmap.md` y `04-plan-de-ejecucion.md`.

---

## D1 — Estrategia de rama

### Opciones

**A. Rama nueva desde el tip de `version-1.6` + eliminación quirúrgica** *(recomendada)*

```
git checkout -b version-1.6-offline version-1.6
```

- ✅ Conserva las 9 features de 1.6 (tema onyx, stats, rediseño detalle, maps, swipe helper…).
- ✅ Conserva los fixes de integridad que aportan valor offline (D3).
- ✅ Permite subir Room a v18 y resolver el downgrade de dispositivos con builds 1.6 (D2).
- ✅ Diff de eliminación acotado: todo el sync está en `aa617df`, conocido fichero a fichero.
- ❌ Hay que editar ~20 ficheros compartidos a mano (no es un solo comando).

**B. Ramificar desde `7fe3581` (padre del commit sync)**

- ✅ Cero código sync desde el primer momento; DB naturalmente en v14.
- ❌ Pierde F2/F3/F4 y la ordenación de subtareas (habría que reaplicarlos a mano de todas formas).
- ❌ Dispositivos con builds 1.6 (DB v17) sufren downgrade → **borrado destructivo silencioso**
  (`fallbackToDestructiveMigration()` está activo). Solo aceptable si se garantiza que ningún
  dispositivo conserva una build 1.6.

**C. `git revert aa617df` sobre `version-1.6`**

- ✅ Mecánico, un comando.
- ❌ Pierde exactamente lo mismo que B (el revert no distingue sync de fixes).
- ❌ Mismo problema de downgrade de DB que B.
- ❌ Reintroduce rutas de hard-delete muertas que la auditoría marcó como trampa.

**D. Quedarse en `version-offline` tal cual**

- ✅ Ya es 100% offline, cero trabajo.
- ❌ Pierde **toda** la 1.6 (no solo sync): el resultado sería una 1.5 disfrazada.

### Recomendación

**Estrategia A.** Nombre de rama sugerido: `version-1.6-offline`. Al validarse (doc 06), se mergea
a `main` y `version-offline` puede quedar como referencia histórica o apuntarse al resultado.
El trabajo se ejecuta sobre la copia de trabajo de la nueva rama — los documentos de
`agent-docs/eliminacion-supabase/` deben commitearse en ella primero para no perderse al cambiar
de rama (hoy están untracked en el working tree de `version-offline`).

## D2 — Esquema Room: qué hacer con las columnas de sync

### Opciones

**DB-1. Conservar v17 con columnas inertes**

Las columnas `uuid/updatedAt/deletedAt/dirty/imagePath` se quedan; el código simplemente deja de
escribirlas/leerlas.

- ✅ Cero migración, cero riesgo.
- ❌ Contradice el objetivo "eliminar por completo": las entidades arrastran 4–5 campos muertos y
  un índice único por tabla para siempre. Deuda visible y confusa para cualquier desarrollo futuro.

**DB-2. Migración de limpieza 17→18 (recrear tablas)** *(recomendada)*

Migración manual que recrea las 4 tablas sin las columnas de sync (conservando
`subtasks.orderIndex` y `subtasks.createdAt`, que sí aportan valor offline), en orden hijo→padre
con `PRAGMA defer_foreign_keys = true`. SQL completo en doc 04, Fase 4.

- ✅ Eliminación real y completa, coherente con el objetivo.
- ✅ Usuarios/dispositivos en cualquier versión 6–17 conservan sus datos.
- ✅ El `MigrationTest` instrumentado existente se extiende a 17→18 como red de seguridad.
- ❌ Es la fase con más riesgo técnico (FKs, orden de recreación) — mitigado con el test.

**DB-3. v18 sin migración (destructivo controlado)**

Subir a v18 sin `MIGRATION_17_18` y dejar que `fallbackToDestructiveMigration()` haga el wipe.

- ✅ Código mínimo.
- ❌ Pérdida total de datos al actualizar. Solo aceptable porque es proyecto personal y existe
  backup/restore: exigiría **exportar backup antes de actualizar** en cada dispositivo.
  Documentarla como atajo legítimo si no importan los datos on-device.

### Recomendación

**DB-2.** Las migraciones 14→15, 15→16 y 16→17 **se conservan siempre** (son el camino de
actualización desde versiones antiguas) — la limpieza es una migración *adicional* 17→18, no una
reescritura del historial. La columna `Task.imagePath` también se elimina (su único productor era
`TaskImageStorage`); `Task.imageUri` (local, preexistente) sigue siendo la vía de imágenes.

Esquema objetivo v18 (v14 + `subtasks.orderIndex` + `subtasks.createdAt`):

| Tabla | Columnas v18 | Se eliminan |
|---|---|---|
| `task_lists` | id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color | uuid, updatedAt, deletedAt, dirty |
| `tasks` | id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate | uuid, updatedAt, deletedAt, dirty, imagePath |
| `subtasks` | id, taskId, title, completed, dueDate, **orderIndex**, **createdAt** | uuid, updatedAt, deletedAt, dirty |
| `reminders` | id, title, description, dateTime, isCompleted, createdAt, isDeleted, latitude, longitude, radius, locationName | uuid, updatedAt, deletedAt, dirty |

También desaparecen los 4 índices únicos `index_<tabla>_uuid` (caen con las tablas viejas).

## D3 — Qué se conserva del commit de sync (adaptado)

Siguiendo la tabla F1–F6 del doc 01 §3:

1. **F2 — Snooze persiste `dueDate`.** En `NotificationActionReceiver.ACTION_SNOOZE` se mantiene
   `db.taskDao().update(task.copy(dueDate = snoozeTime))` (sin `.touched()`). Es un bugfix de UX
   offline: la tarea refleja la nueva fecha, no solo la alarma.
2. **F3 — Sin hard-deletes muertos.** No se reintroducen `TaskDao.delete(task)`,
   `ReminderDao.delete(reminder)` ni los métodos de repositorio/ViewModel asociados. Sí se
   reintroducen **solo** los borrados físicos que la UI usa: `TaskListDao.delete(taskList)`
   (borrado de lista con cascada FK) y `SubtaskDao.delete(subtask)` (quitar subtarea).
3. **F4 — `replaceSubtasksForTask` por diff.** Se conserva la lógica insert/update/por-diff, pero
   las subtareas que ya no vienen se **borran físicamente** (`subtaskDao.delete`) en vez de
   tombstone, y sin `.touched()` ni `orderIndex` se mantiene.
4. **Ordenación de subtareas** (`ORDER BY orderIndex ASC`, `orderIndex = index` al crear,
   backfill ya hecho en 14→15). Se conserva.
5. **Firmas por entidad** `softDeleteTask(task)`/`restoreTask(task)`/`softDelete(reminder)`/
   `restore(reminder)` se conservan (los callers de UI de 1.6 ya las usan), pero el repositorio
   hace `dao.update(task.copy(isDeleted = true))` directamente — `EntityTouch.kt` desaparece.
6. **Infraestructura de schemas Room**: `exportSchema = true`, `room.schemaLocation`,
   `sourceSets` de androidTest, `room-testing` y `app/schemas/` se conservan. Se añadirá `18.json`.
7. **F1, F5, F6 se descartan** (sin sentido offline, ver doc 01 §3).

## D4 — Limpieza de estado residual en dispositivos

Bloque de una sola ejecución en `PolarApplication.onCreate()` de la primera build offline
(guardado con un flag en `app_prefs`), que:

1. Cancela los 3 trabajos únicos por nombre: `SyncWorkerPeriodic`, `SyncWorkerOneTime`,
   `SyncWorkerFrequent`.
2. Borra `sync_prefs` (`deleteSharedPreferences("sync_prefs")`).
3. Borra el fichero de preferencias donde supabase-kt persistió la sesión — **el nombre exacto se
   verifica en un dispositivo que haya corrido 1.6** (Device File Explorer →
   `/data/data/app.polar/shared_prefs/`) antes de escribir este paso; si no se identifica, se
   acepta como residuo inerte (nadie lo leerá sin el SDK).
4. Borra `cacheDir/task_images/`.

Decisión: el bloque **permanece en el código** de la versión offline (es idempotente y barato) o
se retira en una versión posterior. Recomendación: dejarlo permanentemente — son 15 líneas y
cubre cualquier dispositivo que aparezca con una 1.6 antigua. Código exacto en doc 04, Fase 5.

## D5 — Servidor Supabase y credenciales

Orden estricto:

1. **Verificar la app compañera.** El doc `supabase-sync/07-guia-app-externa.md` describe otra app
   consumiendo el mismo proyecto. Si existe y sigue activa, **no borrar el proyecto** — limitarse
   a retirar las credenciales del cliente Android (pasos 2–3) y anotar la dependencia.
2. Borrar las líneas `SUPABASE_URL` y `SUPABASE_ANON_KEY` de `local.properties` (local, no
   commiteado). Con los `buildConfigField` eliminados quedan inertes, pero la higiene manda.
3. Si se confirma que nada más usa el proyecto: **Dashboard → Settings → General → Delete
   project**. Eso elimina tablas, RLS, triggers, jobs `pg_cron`, usuarios de Auth e invalida la
   anon key de una vez. El bucket `task-images` nunca existió (confirmado en la auditoría).
4. Rotación de claves: innecesaria si se borra el proyecto; obligatoria solo si las claves
   hubieran salido del control local (no es el caso: nunca se commitearon).

## D6 — Documentación `agent-docs/supabase-sync/` y la auditoría

- ✅ Recomendado: en el **último commit** de la rama de eliminación, `git rm -r
  agent-docs/supabase-sync agent-docs/analisis-implementacion-supabase-sync.md`. Siguen en el
  historial git y en `main`/`version-1.6` si algún día se retoma la idea.
- ✅ Conservar `agent-docs/eliminacion-supabase/` en la rama offline como registro de la decisión
  (ADR práctico: por qué se quitó, cómo se hizo, qué riesgos se aceptaron).
- Actualizar `AGENTS.md`: versión de Room (v14 → **v18**), y verificar que las líneas "Sin
  conectividad de red" / "La app no declara `INTERNET`" vuelven a ser ciertas. `README.md` y
  `docs/` no mencionan sync — sin cambios de contenido.

## D7 — Versionado de la app

`versionCode=1`/`versionName="1.0"` en ambas ramas (la 1.6 nunca bumpó). Al publicar la offline:

- `versionCode` → 2, `versionName` → `"1.6.1-offline"` (o el esquema que se prefiera).
- Razón: distinguir builds en dispositivos y en WorkManager/Room un cambio de versión real;
  además cualquier build 1.6 previa queda claramente identificable como anterior.

## D8 — Qué NO se toca (alcance negativo explícito)

- `RecurrenceWorker` (trabajo `"RecurrenceWorker"`): solo pierde el `.touched()` y la firma
  `updatedAt`; su scheduling es preexistente y se conserva.
- `MainActivity`, `DrawerManager`, widget de home, `BackupManager`, `fragment_eisenhower.xml`:
  sin relación con sync (el cambio del eisenhower era un fix cosmético de namespace — conservarlo).
- Drawables preexistentes (`ic_sync`, `ic_stat_error`, `ic_arrow_back`, `ic_chevron_right`).
- `docs/`, `README.md`, `README_features.md` (sin menciones a sync).
- `app/proguard-rules.pro` (nunca tuvo reglas de supabase).
- `.idea/` (churn irrelevante del commit).
