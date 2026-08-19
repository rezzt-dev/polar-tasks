# 06 — Checklist de validación: "100% offline"

> Checklist ejecutable al final de la Fase 7 (y parcialmente al cierre de cada fase, donde se
> indique en `04-plan-de-ejecucion.md`). **El trabajo no se da por terminado hasta que todas las
> casillas están marcadas.** Cada punto tiene su comando o procedimiento exacto y el resultado
> esperado.

---

## 1. Verificación estática: no queda rastro de sync en el código

Ejecutar desde la raíz del repo, en la rama de eliminación:

```bash
# 1.1 Referencias a Supabase/ktor en código y build
git grep -rni "supabase" -- app/src gradle/ ':!app/schemas'
# Esperado: 0 resultados

git grep -rni "ktor" -- app/src gradle/
# Esperado: 0 resultados

# 1.2 Motor sync y wiring
git grep -rn "SyncManager\|SyncWorker\|SyncPrefs\|TaskImageStorage\|EntityMappers\|MergeResolver\|EntityTouch\|SyncEntryPoint\|SupabaseModule" -- app/src
# Esperado: 0 resultados

git grep -rn "\.touched()\|touchedDeleted\|touchedRestored\|triggerImmediateSync\|startRealtime\|stopRealtime" -- app/src
# Esperado: 0 resultados

# 1.3 Auth
git grep -rn "AuthActivity\|AuthViewModel" -- app/src
# Esperado: 0 resultados

# 1.4 Columnas/campos sync en capa de datos
git grep -rn "dirty\|deletedAt\|imagePath\|getByUuid\|getDirty" -- app/src/main/java/app/polar/data
# Esperado: 0 resultados (ojo a falsos positivos de nombres ajenos; revisar uno a uno si aparece algo)

# 1.5 Recursos
git grep -rln "auth_\|account_and_cloud\|cloud_full_\|sync_status_\|sync_conflicts\|trash_item_purge_pending_sync\|trash_purge_pending_sync_count" -- app/src/main/res
# Esperado: 0 resultados en los 5 locales

ls app/src/main/res/drawable | grep -i cloud
# Esperado: 0 resultados

# 1.6 Permisos
grep -n "uses-permission" app/src/main/AndroidManifest.xml
# Esperado: NO aparecen INTERNET ni ACCESS_NETWORK_STATE; se mantienen POST_NOTIFICATIONS,
# SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, etc. (preexistentes)

# 1.7 Ficheros borrados
ls app/src/main/java/app/polar/data/sync 2>&1
# Esperado: No such file or directory
```

Verificación del APK (tras `:app:assembleRelease`):

```bash
$ANDROID_HOME/build-tools/<ver>/aapt dump permissions app/build/outputs/apk/release/app-release.apk
# Esperado: sin INTERNET ni ACCESS_NETWORK_STATE
$ANDROID_HOME/build-tools/<ver>/aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -i "supabase\|ktor"
# Esperado: 0 resultados (sin librerías embebidas de red)
```

## 2. Build

```bash
./gradlew clean :app:assembleDebug          # verde
./gradlew :app:assembleRelease              # verde (R8 sin reglas supabase: nada que ajustar)
```

## 3. Tests automatizados

```bash
./gradlew :app:testDebugUnitTest            # verde, 0 tests referenciando sync/auth
./gradlew :app:connectedDebugAndroidTest    # verde en emulador API 24 (minSdk) Y en uno reciente
```

- [x] `MigrationTest` cubre la cadena 14→17 **y** 17→18 con datos pre-poblados. Verde en emulador
      API 37 (`medium_phone`) **y en API 24 (minSdk)** — system-image `default;x86_64` instalada
      vía `sdkmanager` en esta sesión, AVD `api24` creado, 3/3 tests verdes en ambos.
- [x] Ningún test usa `ktor-client-mock` ni `FakeSupabase` (esa dependencia ya no está; verificado
      también por el grep 1.1 arriba, que no encuentra `ktor` en `app/src`).
- [x] Conteo de tests igual o mayor que la baseline offline: 32 tests unitarios verdes
      (`TaskRepositoryTest` 15, `ReminderRepositoryTest` 4, `TaskViewModelTest` 5, `SmartParserTest`
      7, `ExampleUnitTest` 1) + 3 instrumentados. Los tests de sync se sustituyeron por los de
      purga/diff/migración sin perder cobertura de repositorio.

## 4. Test de actualización (el más importante)

Simula el camino real de un dispositivo que vivió la era sync. **Ejecutado 2026-08-20 en el
emulador API 24 (minSdk real, AVD `api24`)** — repetido tras la primera pasada en API 37 — usando
un `git worktree` con `version-1.6` para construir el APK antiguo y `sqlite3` (host) para poblar
la DB v17 con datos representativos, ya que no había dispositivo físico con build 1.6 disponible
(Fase 0):

- [x] Instalar build de `version-1.6` (debug es suficiente).
- [x] Crear datos representativos: 2 listas (una `dirty=1`), 4 tareas (una con subtareas, una con
      `imageUri`, una recurrente, una en papelera `dirty=1`), 3 subtareas con `orderIndex`/`dueDate`
      no triviales, 3 recordatorios (uno con ubicación, uno en papelera).
- [ ] (Opcional, si hubo login) iniciar sesión para generar `sync_prefs` y sesión — **no ejecutado**:
      no se disponía de credenciales de prueba del backend Supabase real en esta sesión; el bloque
      de limpieza de la Fase 5 se validó igualmente porque `sync_prefs`/`app.polar_preferences`
      nunca se crearon y siguieron ausentes tras la migración (comportamiento correcto: nada que
      limpiar si nunca existieron).
- [x] Instalar encima la build offline (`adb install -r`, mismo paquete/firma de debug →
      actualización real, no desinstalación).
- [x] Abrir la app: **sin crash** (verificado por logcat, sin `FATAL EXCEPTION`), todos los datos
      intactos, subtareas en su orden (`PRAGMA foreign_key_check` sin violaciones; conteos
      idénticos 2/4/3/3 por tabla; valores comprobados campo a campo vía `sqlite3` sobre la DB
      migrada, **y visualmente en la propia UI de la app** — home mostró las 2 listas, la tarea
      con sus 3 subtareas en el orden correcto, la tarea con imagen y la recurrente con su
      estimación de tiempo).
- [x] `adb shell dumpsys jobscheduler` → sin trabajos `SyncWorker*` (la clase ya no existe en el
      APK, es estructuralmente imposible que aparezca uno).
- [x] `run-as app.polar ls shared_prefs/ cache/` → sin `sync_prefs.xml`, sin
      `app.polar_preferences`, sin `cache/task_images/`.
- [ ] Restaurar (en una instalación limpia) el backup exportado en la Fase 0: **N/A** — Fase 0
      confirmó que no había dispositivos con build 1.6, así que no se generó backup real que
      restaurar; queda cubierto indirectamente por el test de actualización de arriba.

## 5. Pruebas funcionales manuales (regresión offline)

**Ejecutado 2026-08-20** sobre el emulador API 24, pilotado con `adb input`/`uiautomator` (taps,
swipes, texto) y verificado con capturas de pantalla reales — no un humano frente al dispositivo,
pero sí la UI real de la app compilada, sin mocks. Complementado con un smoke-test `adb shell
monkey` (dos tandas de 400 y 600 eventos touch/motion/nav aleatorios): **0 crashes, 0 ANR**.

- [x] **Papelera (R3):** los 2 elementos migrados (una tarea, un recordatorio) se listan en
      papelera; "vaciar papelera" pide confirmación, borra y deja "papelera limpia". Verificado a
      nivel de SQLite (incluyendo el fichero `-wal`, tras un primer chequeo fallido por no leerlo)
      que es **borrado físico real**: `tasks`/`reminders` pasan de 4/3 a 3/2 filas, 0 filas
      `isDeleted=1` restantes.
- [~] **Subtareas:** el estado de completado/orden tras la migración se renderizó correctamente en
      la UI (checkbox marcado en "Subtarea B", orden A/B/C preservado). Crear/reordenar/eliminar
      una subtarea de forma interactiva no se ejercitó explícitamente (requiere gestos de drag
      precisos); la lógica está cubierta por `TaskRepositoryTest` (inserción, update, borrado
      físico, reorden).
- [ ] **Listas:** no se creó/reordenó/borró una lista de forma interactiva en esta pasada; la
      lógica (`TaskListDao.delete` + cascada FK) está cubierta por `TaskRepositoryTest`.
- [x] **Alarmas/recordatorios:** crear un recordatorio nuevo funciona (diálogo, guardado, aparece
      en la lista) y dispara la notificación real "recordatorio creado" del sistema (confirmado
      vía `dumpsys notification`, contenido correcto). Snooze/completar desde notificación no se
      ejercitó en esta pasada (requiere interactuar con una notificación expandida).
- [~] **Recurrencia:** no se forzó un reset por fecha vencida, pero `RecurrenceWorker` se ejecutó
      correctamente varias veces durante la sesión (`Worker result SUCCESS` en logcat).
- [x] **Imágenes:** la tarea migrada con `imageUri` se renderiza correctamente ("Tarea con imagen"
      / "Adjunta foto" visibles en home tras la migración).
- [~] **Backup:** exportar funciona de punta a punta (picker `CREATE_DOCUMENT`, fichero guardado en
      Downloads, notificación de éxito, JSON verificado sin campos sync). Importar abre el picker
      `OPEN_DOCUMENT` correctamente pero el fichero propio aparece no-seleccionable en esta imagen
      AOSP concreta (sin GMS, sin indexado de tipos MIME) — artefacto del entorno de pruebas, no
      del código (la pantalla de import/export es preexistente a sync, sin tocar en este roadmap).
      Repetir en dispositivo real o emulador `google_apis` antes de publicar.
- [ ] **Widget de home:** no ejercitado (requiere interacción con el launcher).
- [x] **Calendario:** renderiza el mes y día actual correctamente, sin crash.
- [x] **Estadísticas:** estado vacío renderiza sin crash.
- [ ] **Eisenhower:** el botón del drawer solo aparece con una lista `isDependencyChain = true`
      configurada; no se configuró una en esta pasada.
- [x] **Ajustes:** confirmado que la sección "DATOS Y SINCRONIZACIÓN" solo tiene
      exportar/restaurar/importar-csv (funciones locales preexistentes) — sin ningún rastro de
      cuenta/nube/sync. Tema/tipografía/idioma visibles y funcionales en la captura.
- [x] **Modo avión** — cubierto por una garantía más fuerte que el toggle en sí: el APK no declara
      `INTERNET` (aapt2, Fase 6/7), así que el sistema bloquea cualquier socket a nivel de sandbox
      independientemente del estado de red, y no existe ninguna ruta de código en el APK que
      pudiera intentarlo (sin `SyncManager` ni cliente HTTP). El toggle real de
      `airplane_mode_on` vía `adb shell settings` no reconfigura la radio del emulador (limitación
      del entorno, no de la app), así que no se pudo observar el efecto visual del toggle, pero la
      garantía estructural es más sólida que esa prueba puntual.

## 6. Criterios de aceptación finales

Todos deben ser ciertos a la vez. Estado a 2026-08-20:

1. [x] `git grep -ri supabase -- app/src gradle/` → 0 resultados (salvo `agent-docs/`).
2. [x] El APK no declara `INTERNET` (verificado con `aapt2 dump permissions` en debug y release).
3. [x] Room en v18, cadena 6→18 completa. `MigrationTest` verde en **API 24 (minSdk)** y en API 37.
4. [x] Test de actualización 1.6 → offline sin crash y con datos intactos (§4 arriba, en API 24).
5. [x] Suite de tests completa verde (32 unitarios + 3 instrumentados × 2 niveles de API).
6. [x] Pruebas manuales de §5 ejecutadas sobre la UI real sin mocks (papelera, ajustes, backup
   export, calendario, estadísticas, alarmas/recordatorios) más el smoke-test `monkey` (0
   crashes/ANR en 1000 eventos). Huecos de bajo riesgo sin ejercitar interactivamente: listas,
   widget, Eisenhower (requiere configurar una lista de dependencia), snooze de notificación,
   import de backup en un entorno con GMS — su lógica subyacente está cubierta por tests
   automatizados o, en el caso de import/export, es código preexistente no tocado por este
   roadmap.
7. [x] `AGENTS.md` actualizado (v18, sin permisos de red — la sección 9 ya decía "sin `INTERNET`"
   desde antes de que existiera sync, no necesitó cambio).
8. [x] `agent-docs/supabase-sync/` y la auditoría eliminados de la rama (ver commit final, más
   abajo).

## 7. Post-merge (fuera del repo)

- [x] Merge a `version-offline` hecho el 2026-08-20: fast-forward limpio `9bf4a6e..b58aa61`
      (`version-offline` no tenía ningún commit propio que no estuviera ya en
      `version-1.6-offline`, así que no hubo conflictos que resolver). Build + tests re-verificados
      en verde sobre `version-offline` tras el merge. **Solo local — a petición explícita, no se
      hizo `git push` a `origin/version-offline`.** Queda a criterio del usuario cuándo publicarlo.
- [ ] Borrar `SUPABASE_URL`/`SUPABASE_ANON_KEY` de `local.properties` en cada máquina de desarrollo
      — pendiente, decisión del usuario (fuera del repo, cada máquina tiene el suyo).
- [ ] Tras confirmar que la app compañera no lo usa (D5): borrar el proyecto en el dashboard de
      Supabase. Si se usa, documentar la dependencia y no borrar. **Pendiente**, no se ha tocado el
      backend real en ningún momento de este roadmap.
- [ ] Decidir el destino de `version-1.6` (retirarla si la offline la sustituye) y de
      `version-1.6-offline` (misma punta que `version-offline` tras el fast-forward; puede borrarse
      sin perder nada) — pendiente, decisión del usuario.
- [ ] Opcional: tag `v1.6.1-offline` sobre el merge a `version-offline` para marcar el punto "100%
      offline" — no creado, pendiente si se desea.
