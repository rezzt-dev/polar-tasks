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

## Fase 0 — Preparación

**Objetivo:** punto de partida seguro y decisiones cerradas.

- [ ] Commit de `agent-docs/eliminacion-supabase/` (hoy untracked) para no perder estos documentos.
- [ ] `git checkout -b version-1.6-offline version-1.6`.
- [ ] Releer `02-decisiones-y-estrategia.md` y confirmar D1–D8 (especial: ¿existe la app
      compañera? ¿algún dispositivo tiene build 1.6 instalada? ¿DB-2 o DB-3?).
- [ ] En cada dispositivo con build 1.6: **exportar backup** desde la app (Ajustes → backup) como
      red de seguridad adicional, aunque la migración 17→18 conserve datos.
- [ ] `./gradlew :app:assembleDebug` verde sobre `version-1.6` antes de tocar nada (baseline).

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

## Fase 3 — Capa de datos offline

**Objetivo:** entidades v18 (sin campos sync), DAOs y repositorios con semántica offline.
Detalle método a método en doc 04, Fase 3.

- Entidades ×4: quitar `uuid/updatedAt/deletedAt/dirty` (+`imagePath` en Task), quitar índices
  uuid; conservar `orderIndex`/`createdAt` en `Subtask`.
- DAOs ×4: reescribir purgas sin guardas `dirty`; restaurar borrados físicos usados por la UI
  (`TaskListDao.delete`, `SubtaskDao.delete`); eliminar `getByUuid`/`getDirty*`/`updateImageUriCache`;
  `SubtaskDao` quita filtros `deletedAt IS NULL` y la firma `updatedAt` de reset/complete;
  `TaskListDao` quita el filtro `deletedAt IS NULL`.
- Repositorios ×2: quitar `touched*`; `deleteTaskList` vuelve a borrado físico (cascada FK);
  purgas vuelven a `Unit`; `replaceSubtasksForTask` cierra con borrado físico;
  `softDelete/restore` por entidad con `copy(isDeleted = …)`; eliminar `cacheTaskImageUri`.
- **No compilará** hasta que la Fase 4 registre la v18 (Room valida entidades contra `version`).
  Ejecutar 3+4 seguidas.

**Salida:** capa de datos coherente con esquema v18 (sin migración todavía).

## Fase 4 — Migración Room 17→18

**Objetivo:** `MIGRATION_17_18` completa y probada. Es la fase crítica.

- Escribir `MIGRATION_17_18` con el SQL de doc 04, Fase 4 (orden hijo→padre,
  `PRAGMA defer_foreign_keys = true`, `foreign_key_check` al final).
- `AppDatabase`: `version = 18`, registrar la migración en `addMigrations(...)`.
- Extender `androidTest/.../MigrationTest.kt`: caso 17→18 que pre-puebla v17 (incluidas filas con
  `dirty=1`, papelera, subtareas con `orderIndex`) y verifica conservación total de datos y
  ausencia de columnas sync.
- Ejecutar `./gradlew :app:connectedDebugAndroidTest` en emulador **API 24** (minSdk, SQLite más
  viejo) y en uno reciente.
- Tras el éxito, el schema exportado `app/schemas/.../18.json` queda commiteado.

**Salida:** `compileDebugKotlin` + tests de migración verdes. App abre con datos intactos sobre
una instalación previa 1.6 (probar manualmente la actualización, doc 06 §4).

## Fase 5 — Limpieza de estado residual y versionado

**Objetivo:** dispositivos con 1.6 quedan limpios al actualizar.

- Bloque de limpieza en `PolarApplication.onCreate()` (doc 04, Fase 5): cancelación de los 3
  trabajos WorkManager, borrado de `sync_prefs`, sesión del SDK (verificar nombre del fichero en
  dispositivo antes), `cacheDir/task_images/`. Guardado con flag `sync_cleanup_done` en `app_prefs`.
- `versionCode = 2`, `versionName = "1.6.1-offline"` (D7).

**Salida:** actualización 1.6 → offline no deja trabajos huérfanos ni prefs (verificable con
Device File Explorer / `adb shell dumpsys jobscheduler`).

## Fase 6 — Build, manifest y recursos

**Objetivo:** eliminar dependencias, permisos y recursos.

- `app/build.gradle.kts`: quitar plugin serialization, `localProperties`, `buildConfigField` ×2,
  `buildConfig = true`, deps supabase/ktor/serialization/`ktor-client-mock`. **Conservar**
  `room.schemaLocation`, `sourceSets` androidTest y `room-testing` (D3.6).
- `gradle/libs.versions.toml`: quitar las 6 entradas (restaurar desde `7fe3581`).
- `AndroidManifest.xml`: restaurar desde `7fe3581` (fuera `INTERNET`, `ACCESS_NETWORK_STATE`,
  `AuthActivity`).
- Strings: eliminar las 56 claves en los **5** ficheros de locale (lista en doc 04, Fase 6).
- Sync Gradle + `./gradlew :app:assembleDebug` y `:app:assembleRelease` verdes.

**Salida:** `git grep -ri "supabase\|ktor" -- app/src gradle/` sin resultados (salvo
`agent-docs/`). APK sin permiso INTERNET (ver doc 06 §2).

## Fase 7 — Tests, validación y merge

**Objetivo:** suite verde y checklist completo.

- Adaptar `TaskRepositoryTest`, `ReminderRepositoryTest`, `TaskViewModelTest` al estado offline
  (doc 04, Fase 7): constructores sin sync, purgas simples, sin mocks de WorkManager/Supabase.
- `./gradlew :app:testDebugUnitTest` + `:app:connectedDebugAndroidTest` verdes.
- Ejecutar **todo** `06-checklist-validacion.md` (greps, builds, test de actualización, modo
  avión, pruebas funcionales).
- Commit final: `git rm -r agent-docs/supabase-sync agent-docs/analisis-implementacion-supabase-sync.md`
  (D6) + actualización de `AGENTS.md` (Room v18).
- Teardown del servidor (D5) **solo tras** el merge y la validación en dispositivos.
- Merge a `main` (o la rama que gobierne releases) y decisión sobre `version-offline`.

**Salida:** proyecto 100% offline, verificado, mergeado.
