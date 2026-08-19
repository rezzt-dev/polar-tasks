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

- [ ] `MigrationTest` cubre la cadena 14→17 **y** 17→18 con datos pre-poblados.
- [ ] Ningún test usa `ktor-client-mock` ni `FakeSupabase` (esa dependencia ya no está).
- [ ] Conteo de tests igual o mayor que la baseline offline (los tests de sync se sustituyen por
      los de purga/diff/migración, no se pierde cobertura de repositorio).

## 4. Test de actualización (el más importante)

Simula el camino real de un dispositivo que vivió la era sync:

- [ ] Instalar build de `version-1.6` (debug es suficiente).
- [ ] Crear datos representativos: 2 listas, tareas con subtareas (varias, reordenadas), una tarea
      en papelera, un recordatorio, una tarea con imagen (`imageUri`), una tarea recurrente.
- [ ] (Opcional, si hubo login) iniciar sesión para generar `sync_prefs` y sesión.
- [ ] Instalar encima la build offline (mismo signing key → actualización, no desinstalación).
- [ ] Abrir la app: **sin crash**, todos los datos intactos, subtareas en su orden.
- [ ] `adb shell dumpsys jobscheduler | grep -i sync` → sin trabajos `SyncWorker*`.
- [ ] Device File Explorer: `/data/data/app.polar/shared_prefs/sync_prefs.xml` ausente;
      `cache/task_images/` ausente.
- [ ] Restaurar (en una instalación limpia) el backup exportado en la Fase 0: funciona (R4).

## 5. Pruebas funcionales manuales (regresión offline)

- [ ] **Papelera (R3):** mover tareas y recordatorios a la papelera → "vaciar papelera" los borra
      de verdad (reabrir la papelera: vacía). Borrado definitivo individual también.
- [ ] **Subtareas:** crear, reordenar, completar, eliminar una subtarea; al borrar una tarea
      definitivamente sus subtareas desaparecen (cascada FK).
- [ ] **Listas:** crear, reordenar, borrar una lista → sus tareas desaparecen con ella.
- [ ] **Alarmas/recordatorios:** crear recordatorio con alarma exacta → salta; completar desde la
      notificación; **posponer desde la notificación → la tarea muestra la nueva fecha** (F2).
- [ ] **Recurrencia:** tarea diaria que vence → se resetea correctamente (forzar con fecha pasada).
- [ ] **Imágenes:** adjuntar imagen a una tarea (ruta `imageUri` local) → visible al reabrir.
- [ ] **Backup:** exportar e importar en instalación limpia.
- [ ] **Widget de home:** añade y refleja tareas.
- [ ] **Calendario/estadísticas/Eisenhower:** render correcto (sanity de features 1.6).
- [ ] **Ajustes:** la sección cuenta/sync ya no existe; temas/idioma/fuentes funcionan.
- [ ] **Modo avión:** activar modo avión → la app funciona al 100%, sin errores, sin spinners, sin
      estados de "esperando red". Es la prueba de fuego del objetivo.

## 6. Criterios de aceptación finales

Todos deben ser ciertos a la vez:

1. `git grep -ri supabase -- app/src gradle/` → 0 resultados (salvo `agent-docs/`).
2. El APK no declara `INTERNET`.
3. Room en v18, `MigrationTest` verde en API 24 y reciente, cadena 6→18 completa.
4. Test de actualización 1.6 → offline sin crash y con datos intactos.
5. Suite de tests completa verde.
6. Todas las pruebas manuales de §5 pasadas, incluido el modo avión.
7. `AGENTS.md` actualizado (v18, sin permisos de red).
8. `agent-docs/supabase-sync/` y la auditoría eliminados de la rama (siguen en historial/main).

## 7. Post-merge (fuera del repo)

- [ ] Borrar `SUPABASE_URL`/`SUPABASE_ANON_KEY` de `local.properties` en cada máquina de desarrollo.
- [ ] Tras confirmar que la app compañera no lo usa (D5): borrar el proyecto en el dashboard de
      Supabase. Si se usa, documentar la dependencia y no borrar.
- [ ] El merge destino es `version-offline` (confirmado 2026-08-19). Decidir el destino de
      `version-1.6` (retirarla si la offline la sustituye) y de `version-1.6-offline` tras el merge.
- [ ] Opcional: tag `v1.6.1-offline` sobre el merge a `version-offline` para marcar el punto "100% offline".
