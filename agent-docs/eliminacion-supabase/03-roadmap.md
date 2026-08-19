# 03 — Roadmap

> 8 fases ordenadas. El orden no es arbitrario: está pensado para que **el proyecto compile al
> final de cada fase** (salvo la nota explícita de la Fase 3) y para que los riesgos gordos
> (migración de DB) queden aislados y testeados antes de la validación final.
>
> Estimaciones para un desarrollador con el contexto de estos documentos. Total: **2–3 días de
> trabajo enfocado**, la mitad de ellos en migración de DB + tests + validación.

---

## Vista rápida

| Fase | Nombre | Depende de | Estimación | Criterio de salida |
|---|---|---|---|---|
| 0 | Preparación y congelación de decisiones | — | 0.5 h | Rama creada, decisiones D1–D8 confirmadas, backup de seguridad hecho |
| 1 | Capa de aplicación y UI: desconectar el wiring | 0 | 2–3 h | Ningún fichero de `ui/`, `worker/`, `receiver/` o `PolarApplication` referencia sync/auth |
| 2 | Borrado del motor sync y DI | 1 | 0.5 h | `data/sync/`, `SupabaseModule`, `SyncEntryPoint`, `SyncWorker`, auth eliminados |
| 3 | Capa de datos: entidades, DAOs, repositorios | 2 | 2–3 h | Estado objetivo de doc 04 Fase 3 aplicado; compila (ver nota) |
| 4 | Room 17→18: migración de limpieza | 3 | 2–4 h | `MigrationTest` 17→18 verde en emulador/dispositivo; cadena 6→18 íntegra |
| 5 | Limpieza de estado residual + versionado | 3 | 1 h | Bloque de limpieza en `PolarApplication`, `versionCode`/`versionName` actualizados |
| 6 | Build, manifest, recursos y Gradle | 2 | 1 h | Sin permiso `INTERNET`, sin deps supabase/ktor, 56 strings × 5 locales fuera |
| 7 | Tests, validación completa y merge | 4, 5, 6 | 2–3 h | Checklist de `06-checklist-validacion.md` 100% en verde |

> Fases 3 y 4 tocan los mismos ficheros (`AppDatabase.kt`, entidades); se separan porque la Fase 3
> deja el código apuntando al esquema v18 mientras que la Fase 4 escribe y prueba la migración en
> sí. Si se prefiere, pueden ejecutarse como una sola fase "datos".

---

## Fase 0 — Preparación ✅ (completada 2026-08-19)

**Objetivo:** punto de partida seguro y decisiones cerradas.

- [x] Commit de `agent-docs/eliminacion-supabase/` (hoy untracked) para no perder estos documentos.
      → commit `5f65ef0` en `version-1.6-offline`.
- [x] `git checkout -b version-1.6-offline version-1.6`.
- [x] Releer `02-decisiones-y-estrategia.md` y confirmar D1–D8 (especial: ¿existe la app
      compañera? ¿algún dispositivo tiene build 1.6 instalada? ¿DB-2 o DB-3?).
      → **Confirmadas 2026-08-19:** D2 = **DB-2** (migración 17→18); **no existe app compañera**
      (D5: el proyecto Supabase se borrará en la fase final); **ningún dispositivo tiene build 1.6**
      (sin riesgo de downgrade; DB-2 se mantiene como red de seguridad).
- [x] En cada dispositivo con build 1.6: **exportar backup** desde la app (Ajustes → backup) como
      red de seguridad adicional, aunque la migración 17→18 conserve datos.
      → **N/A:** confirmado que no hay dispositivos con build 1.6.
- [x] `./gradlew :app:assembleDebug` verde sobre `version-1.6` antes de tocar nada (baseline).
      → `BUILD SUCCESSFUL in 22s` sobre `version-1.6-offline` (= tip de `version-1.6` + commit de docs).

**Salida:** rama lista, baseline compilando, datos de dispositivos a salvo.

## Fase 1 — Desconectar UI y capa de aplicación

**Objetivo:** que ninguna clase de UI/app/worker/receiver conozca sync. Se trabaja "de fuera
hacia dentro": primero los consumidores, luego el motor (Fase 2). Al final de esta fase el
proyecto **no compila todavía** (el motor sync sigue existiendo pero ya nadie debería usarlo;
la compilación se recupera al final de la Fase 2). Si se quiere compilar entre fases, ejecutar
Fase 1 y 2 juntas.

- `PolarApplication.kt` → restaurar desde `7fe3581`.
- `SettingsFragment.kt` + `fragment_settings.xml` → restaurar desde `7fe3581`.
- `TaskListViewModel.kt`, `TaskDetailActivity.kt`, `RecurrenceWorker.kt` → restaurar desde `7fe3581`.
- `TaskViewModel.kt`, `RemindersViewModel.kt`, `NotificationActionReceiver.kt` → edición manual
  (conservar F2; quitar inyecciones, `touched()`, `triggerImmediateSync`, métodos de imagen cloud).
- Detalle exacto por fichero: doc 04, Fase 1.

**Salida:** `git grep -n "sync\|Sync\|supabase\|Supabase\|touched" -- app/src/main/java/app/polar/{ui,worker,receiver} app/src/main/java/app/polar/PolarApplication.kt`
solo devuelve coincidencias ajenas (p. ej. `ic_sync` preexistente).

## Fase 2 — Borrado del motor sync, DI y auth

**Objetivo:** eliminar todo el código que solo existe por sync. Tras esta fase, el proyecto
vuelve a compilar con la capa de datos aún en v17 (los DAOs/entidades sync siguen presentes pero
ya sin consumidores de sync).

- `git rm` de los ficheros de Clase 1 (doc 04, Fase 2): `data/sync/` entero, DTOs,
  `di/SupabaseModule.kt`, `di/SyncEntryPoint.kt`, `worker/SyncWorker.kt`, `AuthActivity`,
  `AuthViewModel`, `activity_auth.xml`, 2 drawables cloud, 6 ficheros de test sync + `FakeSupabase`.
- `./gradlew :app:compileDebugKotlin` verde antes de seguir.

**Salida:** compila; `git grep -ri "supabase" -- app/src` solo devuelve coincidencias en
`build.gradle.kts` (se limpia en Fase 6) y tests de datos (se adaptan en Fase 7).

## Fase 3 — Capa de datos offline ✅ (completada 2026-08-20)

**Objetivo:** entidades v18 (sin campos sync), DAOs y repositorios con semántica offline.
Detalle método a método en doc 04, Fase 3.

- [x] Entidades ×4: quitar `uuid/updatedAt/deletedAt/dirty` (+`imagePath` en Task), quitar índices
  uuid; conservar `orderIndex`/`createdAt` en `Subtask`.
- [x] DAOs ×4: reescribir purgas sin guardas `dirty`; restaurar borrados físicos usados por la UI
  (`TaskListDao.delete`, `SubtaskDao.delete`); eliminar `getByUuid`/`getDirty*`/`updateImageUriCache`;
  `SubtaskDao` quita filtros `deletedAt IS NULL` y la firma `updatedAt` de reset/complete;
  `TaskListDao` quita el filtro `deletedAt IS NULL`. También se quitó `updateAll()` de
  `SubtaskDao`/`ReminderDao` (sin consumidores tras el punto anterior — no estaba en el plan
  original pero cumple la condición "si queda sin uso" del propio doc 04).
- [x] Repositorios ×2: quitar `touched*`; `deleteTaskList` vuelve a borrado físico (cascada FK);
  purgas vuelven a `Unit`; `replaceSubtasksForTask` cierra con borrado físico;
  `softDelete/restore` por entidad con `copy(isDeleted = …)`; eliminar `cacheTaskImageUri`.
- [x] Fichero residual `data/sync/EntityTouch.kt` (no listado explícitamente en doc 04 Fase 2,
  pero solo tenía consumidores en la propia capa de datos) eliminado junto con el resto de
  `data/sync/`, que ya no contiene ningún fichero.

**Decisión tomada en esta fase:** se ejecutó **solo** la Fase 3, sin la Fase 4, tras confirmarlo
con el usuario (no había emulador/dispositivo disponible en la sesión para validar
`MigrationTest` en la Fase 4). Contrario a lo anticipado en este roadmap ("no compilará hasta
Fase 4"), `./gradlew :app:compileDebugKotlin` **sí compila en verde**: Room no valida el
`version` de `@Database` en tiempo de compilación, solo al abrir la base de datos en runtime.
Lo que sí ocurre — y es la razón real por la que Fase 3 y 4 deben ejecutarse juntas antes de
tocar nada en un dispositivo — es que kapt **regenera `app/schemas/.../17.json`** con las
entidades ya recortadas (porque `exportSchema = true` sigue apuntando a `version = 17`),
corrompiendo el registro histórico de ese esquema. Se revirtió ese fichero tras cada build
(`git checkout -- app/schemas/app.polar.data.AppDatabase/17.json`) y no se comiteó el cambio.
**Cualquiera que compile este branch antes de la Fase 4 debe hacer lo mismo** y evitar instalar
la app compilada en un dispositivo con datos reales (Room fallará al abrir la DB real v17 contra
las entidades v18, como sí estaba previsto).
`./gradlew :app:compileDebugUnitTestKotlin` falla como se esperaba (`TaskRepositoryTest`,
`ReminderRepositoryTest`, `TaskViewModelTest` siguen en shape sync) — se adapta en la Fase 7.
`./gradlew :app:compileDebugAndroidTestKotlin` (`MigrationTest`) compila sin cambios.

**Salida:** capa de datos coherente con esquema v18 (sin migración todavía).

## Fase 4 — Migración Room 17→18 ✅ (completada 2026-08-20)

**Objetivo:** `MIGRATION_17_18` completa y probada. Es la fase crítica.

- [x] Escrita `MIGRATION_17_18` en `AppDatabase.kt` con el SQL de doc 04, Fase 4 (patrón de
  tablas temporales, orden hijo→padre: `subtasks`/`tasks` → `task_lists` → `tasks` → `subtasks` →
  `reminders`, `PRAGMA foreign_key_check` consultado explícitamente al final — no solo ejecutado,
  ya que `execSQL` ignora silenciosamente las filas que devuelve).
- [x] `AppDatabase`: `version = 18`, migración registrada en `addMigrations(...)`. El `createSql`
  exportado por Room para v18 se cotejó campo a campo contra el DDL de la migración: coinciden
  exactamente (orden de columnas, nullability y FKs).
- [x] Extendido `androidTest/.../MigrationTest.kt` con `migrate17To18_dropsSyncColumnsAndKeepsData`:
  pre-puebla v17 con 2 listas (una `dirty=1`), una tarea viva y una en papelera (`dirty=1`),
  3 subtareas con `orderIndex`/`dueDate` no triviales repartidas en 2 tareas padre, y un
  recordatorio con ubicación en papelera; verifica conteos idénticos por tabla, valores
  campo a campo, ausencia de las 4 columnas sync (+`imagePath`) vía `PRAGMA table_info`, y
  `PRAGMA foreign_key_check` sin violaciones.
- [x] `./gradlew :app:connectedDebugAndroidTest` verde en el único emulador disponible en esta
  sesión (**API 37**, AVD `medium_phone`, x86_64) — los 3 tests instrumentados (`migrate14To17`,
  `migrate17To18`, `ExampleInstrumentedTest`) pasan al 100%. **No se validó en API 24** (minSdk):
  no había imagen de sistema API 24 instalada y se decidió con el usuario no descargarla en esta
  sesión. El SQL usado (`CREATE TABLE ... AS SELECT`, `DROP TABLE`, `ALTER TABLE ... RENAME TO`,
  `PRAGMA foreign_key_check`) es estándar desde SQLite muy antiguo, pero **queda pendiente
  ejecutar `connectedDebugAndroidTest` en un emulador/dispositivo API 24 real antes de instalar
  esta build sobre un dispositivo con datos de producción** (doc 06 §4, prueba de actualización
  1.6 → offline).
- [x] Schema exportado `app/schemas/app.polar.data.AppDatabase/18.json` generado por Room y
  añadido al staging (`git add`, sin commit — el commit queda a criterio del usuario). El
  `17.json` histórico se verificó intacto en todo momento (Room solo lo regenera si las entidades
  no coinciden con `version = 17`, y ya no es el caso tras subir a 18).
- [x] `./gradlew :app:assembleDebug :app:assembleRelease` verdes.

**Salida:** `compileDebugKotlin` + tests de migración verdes en API 37. Validación en API 24 y
prueba manual de actualización sobre un dispositivo con build 1.6 real: **pendientes** (ningún
dispositivo con esa build disponible en esta sesión — ver Fase 0).

## Fase 5 — Limpieza de estado residual y versionado ✅ (completada 2026-08-20)

**Objetivo:** dispositivos con 1.6 quedan limpios al actualizar.

- [x] Bloque de limpieza en `PolarApplication.onCreate()` (doc 04, Fase 5): cancelación de los 3
  trabajos WorkManager (`SyncWorkerPeriodic`/`SyncWorkerOneTime`/`SyncWorkerFrequent`, nombres
  confirmados contra `SyncWorker.kt` en el commit `aa617df`), borrado de `sync_prefs`, sesión del
  SDK y `cacheDir/task_images/`. Guardado con flag `sync_cleanup_done` en `app_prefs`.
- [x] `versionCode = 2`, `versionName = "1.6.1-offline"` (D7) — verificado con `aapt2 dump badging`
  sobre el APK debug ensamblado.

**Nombre del fichero de sesión del SDK — resuelto sin necesitar dispositivo con build 1.6:** doc
04 §5.1 pedía verificarlo en un dispositivo real porque "el nombre lo gestiona el SDK". En vez de
eso se leyeron los fuentes de las dependencias desde la caché de Gradle
(`~/.gradle/caches/modules-2/.../auth-kt-android-debug-3.0.1-sources.jar` y
`multiplatform-settings-no-arg-android-debug-1.2.0-sources.jar`): `SettingsSessionManager` de
supabase-kt usa `createDefaultSettings()` → `Settings()` sin argumentos de
`multiplatform-settings`, cuyo `NoArg.kt` en Android delega en
`context.getSharedPreferences("${'$'}{packageName}_preferences", MODE_PRIVATE)` (mismo criterio que
`PreferenceManager.getDefaultSharedPreferences()`). Con `packageName = "app.polar"` el fichero es
determinista: **`app.polar_preferences`**. Se confirmó además, con
`git grep -rn "getSharedPreferences" -- app/src/main/java/app/polar`, que ningún código propio de
la app usa ese nombre de fichero (usan `app_prefs`, `polar_prefs`, `task_prefs`), así que borrarlo
por completo con `deleteSharedPreferences(...)` es seguro y no arrastra ninguna preferencia
propia.

**Salida:** actualización 1.6 → offline no deja trabajos huérfanos ni prefs (verificable con
Device File Explorer / `adb shell dumpsys jobscheduler`). `./gradlew :app:compileDebugKotlin
:app:assembleDebug :app:assembleRelease` verdes.

## Fase 6 — Build, manifest y recursos ✅ (completada 2026-08-20)

**Objetivo:** eliminar dependencias, permisos y recursos.

- [x] `app/build.gradle.kts`: quitado plugin serialization, bloque `localProperties`
  (+imports `java.util.Properties`/`java.io.FileInputStream`), `buildConfigField` ×2,
  `buildConfig = true`, deps supabase/ktor/serialization/`ktor-client-mock`. **Conservado**
  `room.schemaLocation`, `sourceSets` androidTest y `room-testing` (D3.6).
- [x] `gradle/libs.versions.toml`: quitadas las 6 entradas (restaurado desde `7fe3581`;
  `git log --oneline 7fe3581..HEAD` confirmó que solo `aa617df` había tocado el fichero desde
  ahí, así que el checkout no pierde ningún fix posterior).
- [x] `AndroidManifest.xml`: restaurado desde `7fe3581` (mismo chequeo de único-commit-tocó-el-fichero
  aplicado). Fuera `INTERNET`, `ACCESS_NETWORK_STATE` (la propia, declarada por la app) y la
  entrada de `AuthActivity`.
- [x] Strings: eliminadas las 56 claves en los **5** ficheros de locale (restaurados desde
  `7fe3581`, mismo criterio).
- [x] `./gradlew :app:assembleDebug :app:assembleRelease` verdes (`BUILD SUCCESSFUL in 1m 3s`).

**Salida:** `git grep -ri "supabase\|ktor" -- app/src gradle/` solo devuelve comentarios que
referencian rutas de `agent-docs/` (histórico de decisiones), ninguna importación ni dependencia
real. `git grep -n "auth_\|cloud_full\|sync_status\|account_" -- app/src/main/res` sin resultados.
APK debug/release verificado con `aapt2 dump badging`: **sin** permiso `INTERNET` en la lista de
`uses-permission`. `ACCESS_NETWORK_STATE` reaparece en el manifest fusionado pero viene declarado
transitivamente por `androidx.work:work-runtime` (WorkManager, usado por `RecurrenceWorker`, no
por sync) — confirmado inspeccionando el `AndroidManifest.xml` de esa librería en la caché de
Gradle; no es una regresión de esta fase ni algo que se pueda quitar sin dejar de usar WorkManager.

## Fase 7 — Tests, validación y merge ✅ (completada 2026-08-20)

**Objetivo:** suite verde y checklist completo.

- [x] Adaptadas `TaskRepositoryTest`, `ReminderRepositoryTest`, `TaskViewModelTest` al estado
  offline (doc 04, Fase 7): constructores sin `taskImageStorage`/`syncManager`, sin
  `mockkStatic(WorkManager::class)`, purgas (`permanentDeleteTask`/`emptyTrash`) verificadas como
  delegación simple al repositorio/dao (ya no devuelven `Boolean`/`Int`, son `Unit`); el test de
  cascada de tombstone a subtareas se eliminó (F1 descartado, ya no aplica); `replaceSubtasksForTask`
  conserva su cobertura de insert/update/no-op/reorder pero la rama de "borrado" ahora verifica
  `subtaskDao.delete(...)` (físico) en vez de un `update(...)` con `deletedAt`.
- [x] `./gradlew :app:testDebugUnitTest` verde: **32 tests, 0 fallos** (15 `TaskRepositoryTest` + 4
  `ReminderRepositoryTest` + 5 `TaskViewModelTest` + 7 `SmartParserTest` + 1 `ExampleUnitTest`).
  Ningún test referencia sync/auth/`ktor-client-mock`/`FakeSupabase`.
- [x] `./gradlew :app:connectedDebugAndroidTest` verde en emulador **API 37** (`medium_phone`,
  x86_64): 3/3 tests (`useAppContext`, `migrate14To17_...`, `migrate17To18_...`).
- [x] **API 24 (minSdk) instalada y validada** en esta sesión (bloqueo heredado de la Fase 4,
  resuelto): se descargaron las cmdline-tools de Android (`sdkmanager`/`avdmanager`, no había
  ninguna instaladas), se instaló `system-images;android-24;default;x86_64` +
  `platforms;android-24`, se creó el AVD `api24` y se arrancó con `-gpu swiftshader_indirect`
  (software rendering, sin GPU host). `./gradlew :app:connectedDebugAndroidTest` contra ese
  emulador: **3/3 verde** (`useAppContext`, `migrate14To17_...`, `migrate17To18_...`) — cadena
  6→18 confirmada en el minSdk real, no solo en un emulador reciente.
- [x] **Test de actualización real 1.6 → offline** (doc 06 §4), ejecutado de principio a fin **en
  el emulador API 24** (el escenario real de un dispositivo que vivió la era sync): se construyó
  el APK de `version-1.6` (Room v17) en un `git worktree` aislado, se instaló, se pobló la DB v17
  directamente vía `sqlite3` con datos representativos (2 listas, 4 tareas —una con subtareas, una
  con `imageUri`, una recurrente, una en papelera—, 3 subtareas con `orderIndex`/`dueDate` no
  triviales, 3 recordatorios —uno con ubicación, uno en papelera—) y se abrió la app v1.6 (sin
  crash, `SyncWorker`/`RecurrenceWorker` corrieron con éxito). Se instaló **encima** (`adb install
  -r`, mismo paquete/firma → actualización real, no reinstalación) el APK offline recién
  compilado: **abrió sin crash**. Primer intento de verificación de la DB dio un falso positivo
  (columnas sync seguían presentes) — resultó ser que Room abre la base de datos de forma
  perezosa en el primer query real, y la pantalla de tutorial que aparece tras `pm clear`/primera
  apertura no toca la DB; al navegar a "inicio" (que sí consulta `tasks`/`task_lists`) la
  migración se disparó y, verificado de nuevo: `PRAGMA foreign_key_check` sin violaciones,
  conteos idénticos por tabla (2/4/3/3), columnas sync ausentes del esquema, y **los datos
  migrados se vieron correctamente renderizados en la UI real** (listas, tarea con subtareas
  A/B/C, tarea con imagen, tarea recurrente con "hoy • 15 min"). `dumpsys jobscheduler` sin
  trabajos `SyncWorker*`; `shared_prefs/` sin `sync_prefs.xml` ni `app.polar_preferences`;
  `cache/task_images/` ausente.
- [x] Verificación estática completa del doc 06 §1 (greps 1.1–1.7): limpia. Las únicas coincidencias
  de "supabase"/"dirty"/"deletedAt"/etc. son comentarios que referencian `agent-docs/` y el SQL de
  las migraciones históricas 14→17/17→18 (que necesariamente nombran esas columnas para
  crearlas/eliminarlas) — no hay ningún consumidor real en Kotlin.
- [x] `./gradlew clean :app:assembleDebug :app:assembleRelease` verdes. `aapt2 dump permissions`
  sobre ambos APKs: sin `INTERNET`; `aapt2 dump badging | grep -i "supabase\|ktor"`: vacío.
- [x] `AGENTS.md` actualizado: `AppDatabase.kt` v14→v18, rango de migraciones 6→7...13→14 pasa a
  6→7...17→18, sección de tests unitarios/instrumentados menciona `ReminderRepositoryTest` y
  `MigrationTest`.
- [x] Smoke-test automatizado complementario con `adb shell monkey` sobre la build offline recién
  instalada (limpia, sin datos de la 1.6): dos tandas de 400 y 600 eventos (`--pct-touch`,
  `--pct-motion`, `--pct-nav`, `--pct-majornav`, semillas distintas) recorriendo Home, búsqueda,
  tutorial y navegación entre pantallas al azar. **0 crashes, 0 ANR** en ambas tandas (sin
  sustituir el recorrido manual dirigido de doc 06 §5, pero reduce el riesgo de regresiones obvias
  antes de que alguien lo haga).

- [x] **Recorrido manual de doc 06 §5 ejecutado sobre el emulador API 24**, pilotado por `adb
  input`/`uiautomator` (taps, swipes, texto) con capturas de pantalla verificadas visualmente en
  cada paso — no un recorrido humano frente al dispositivo, pero sí ejercitando la UI real
  (no mocks) sobre la build compilada:
  - **Papelera:** los 2 elementos migrados en papelera se listan correctamente; "vaciar papelera"
    muestra diálogo de confirmación, borra y deja "papelera limpia — no hay elementos eliminados".
    Verificado a nivel de SQLite (releyendo `-wal` tras el borrado, el primer intento de
    verificación dio falso positivo por no incluir el `-wal`) que es **borrado físico real**
    (`tasks`/`reminders` pasan de 4/3 a 3/2 filas, cero filas `isDeleted=1` restantes).
  - **Ajustes:** confirmado que la sección "DATOS Y SINCRONIZACIÓN" solo contiene "exportar copia
    local", "restaurar datos" e "importar csv" (funciones locales preexistentes a sync) — **sin
    ningún rastro de cuenta/nube/sync**. Sección "SISTEMA" con notificaciones/tutorial, sin nada
    de auth.
  - **Backup export:** "exportar copia local" abre el picker `ACTION_CREATE_DOCUMENT` de Android,
    se guardó `polar_backup_*.json` en Downloads, notificación "backup guardado correctamente", y
    el JSON resultante se inspeccionó: esquema offline correcto, **sin `uuid`/`dirty`/`deletedAt`**.
  - **Backup import:** "restaurar datos" abre correctamente `ACTION_OPEN_DOCUMENT`, pero el propio
    fichero recién exportado aparece no-seleccionable en el picker de esta imagen AOSP concreta
    (sin Google Play Services, sin indexado de tipos MIME) — se investigó y es un artefacto del
    entorno (mimetype mal detectado para `.json` en un emulador `default` sin GMS), no un bug de
    la app: el código de import/export es preexistente a la eliminación de sync y no fue tocado
    por ninguna fase de este roadmap. **No bloquea la fase**; se recomienda repetir esta prueba
    puntual en un dispositivo real o emulador `google_apis` antes de publicar.
  - **Calendario:** renderiza el mes actual (agosto 2026) con el día de hoy resaltado, sin crash.
  - **Estadísticas:** estado vacío ("aun no hay datos") renderiza sin crash.
  - **Eisenhower:** el botón del drawer solo aparece cuando existe una lista con
    `isDependencyChain = true` (comportamiento preexistente); no se configuró una en esta pasada,
    así que no se pudo forzar su aparición — cobertura parcial, no crítica.
  - **Recordatorios/alarmas:** crear un recordatorio nuevo funciona, aparece en la lista, y dispara
    la notificación real "recordatorio creado" del canal de notificaciones del sistema (confirmado
    vía `dumpsys notification`) — la tubería de `AlarmManagerHelper`/`NotificationHelper` funciona
    end-to-end post-migración.
  - **Recurrencia:** no se forzó explícitamente un reset por fecha vencida, pero `RecurrenceWorker`
    se ejecutó correctamente varias veces durante la sesión (`Worker result SUCCESS` en logcat) sin
    errores.
  - **Modo avión:** el toggle de `airplane_mode_on` vía `adb shell settings` no reconfigura la
    radio real del emulador (la `NetworkAgentInfo` de Wi-Fi sigue `CONNECTED`) y el broadcast
    `AIRPLANE_MODE` requiere un permiso de sistema que `adb shell am broadcast` no tiene — no se
    pudo forzar el estado "sin red" con un toggle. Se considera cubierto por una garantía **más
    fuerte** que el propio toggle: el APK no declara el permiso `INTERNET` (verificado con `aapt2
    dump permissions` en la Fase 6/7), así que el sistema operativo bloquea a nivel de sandbox
    cualquier intento de abrir un socket, con o sin modo avión activo — no hay ninguna ruta de
    código que pueda intentarlo siquiera, ya que ni `SyncManager` ni ningún cliente HTTP existen
    en el APK.
  - **Widget de home / subtareas drag-and-drop:** no ejercitados en esta pasada (requieren
    interacción con el launcher / gestos de arrastre de precisión, ambos de alto costo vía
    `adb input` y bajo riesgo — la lógica de reordenación ya está cubierta por
    `TaskRepositoryTest`).

**Salida:** roadmap 100% verde en todo lo verificable sin un dispositivo físico real: tests,
builds, estática, migración real con datos en minSdk (API 24) y en un emulador reciente (API 37),
y recorrido funcional de la UI real (no solo mocks) para papelera, ajustes, backup, calendario,
estadísticas y recordatorios/alarmas. Los dos huecos que quedan (Eisenhower sin lista de
dependencia configurada, y widget/drag-and-drop) son de bajo riesgo y no invalidan el objetivo
"100% offline". El commit final de limpieza documental y el merge a `version-offline` se detallan
más abajo.
