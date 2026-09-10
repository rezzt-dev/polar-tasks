# Análisis del estado actual de la sincronización con Supabase

> Auditoría de la implementación real (rama `version-1.6`) contra el diseño documentado en
> `agent-docs/supabase-sync/`. Objetivo: listar fallos, deficiencias y gaps concretos que explican por
> qué la función de nube "no funciona correctamente" hoy, con archivo y línea cuando aplica.

## Resumen ejecutivo

El código implementa correctamente el esqueleto del diseño (columnas `uuid`/`updatedAt`/`deletedAt`/`dirty`,
`SyncManager` con push→pull, DTOs `snake_case`, `MergeResolver` puro, Storage para imágenes, `SyncWorker`
con cadencia 3 min en primer plano / 15 min de red de seguridad). Sin embargo hay **un bloqueador de
producto** (no se puede crear cuenta desde la app), **un fallo crítico de comportamiento** (la app no
dispara ninguna sincronización al abrirse — solo sincroniza como efecto secundario de que el usuario edite
algo, o cuando expira el temporizador de fondo), **dos bugs de integridad de datos** reproducibles
(resurrección de tareas/recordatorios borrados, subtareas huérfanas al purgar), y una lista larga de
huecos respecto a lo que los propios documentos de diseño (`04`, `06`) exigían implementar y que se
quedó a medias (Realtime, aviso de duplicados en primer login, indicador de estado de sync, tests de
integración). Ninguno de estos puntos es visible con una build/compile check — todos requieren leer el
flujo de datos o probar escenarios concretos, que es lo que se documenta abajo.

## Alcance revisado

Código: `data/sync/*`, `data/sync/dto/*`, `di/SupabaseModule.kt`, `di/SyncEntryPoint.kt`,
`worker/SyncWorker.kt`, `worker/RecurrenceWorker.kt`, `receiver/NotificationActionReceiver.kt`,
`ui/activity/AuthActivity.kt`, `ui/viewmodel/AuthViewModel.kt`, `ui/fragment/SettingsFragment.kt`,
`ui/viewmodel/{TaskViewModel,TaskListViewModel,RemindersViewModel}.kt`,
`data/repository/{TaskRepository,ReminderRepository}.kt`, `data/dao/*Dao.kt`, `data/entity/*.kt`,
`data/AppDatabase.kt` (migración 14→15), `PolarApplication.kt`, `AndroidManifest.xml`,
`app/build.gradle.kts`, tests en `app/src/test/java/app/polar/data/sync/`.
Contraste contra: `agent-docs/supabase-sync/00` a `08`.

No se ha podido verificar el lado del proyecto Supabase real (dashboard, SQL desplegado, RLS, Realtime
replication, `pg_cron`) — esos puntos se listan aparte en la sección "No verificable desde el código".

---

## 1. Bloqueador de producto

### 1.1. No existe flujo de registro (sign-up) — solo sign-in

`AuthViewModel` ([AuthViewModel.kt](app/src/main/java/app/polar/ui/viewmodel/AuthViewModel.kt)) solo
expone `signIn()` (que llama a `supabaseClient.auth.signInWith(Email)`) y `signOut()`. No hay ningún
método que llame a `signUpWith(Email)`. `AuthActivity`/`activity_auth.xml` solo pintan un formulario de
email+contraseña con un único botón "iniciar sesión" — no hay pestaña ni enlace "crear cuenta". Tampoco
hay recuperación de contraseña (`resetPasswordForEmail`) ni reenvío de verificación de email.

**Consecuencia real:** un usuario que instala Polar por primera vez no tiene ninguna forma de crear una
cuenta Supabase desde la app. La única vía es que la "otra app" (doc 07) implemente el registro y el
usuario traiga esas credenciales a Polar, algo que no está documentado como decisión explícita en
ningún sitio — hoy simplemente parece un olvido, no una decisión de producto. Mientras esto no se
resuelva, **la función de sync es inutilizable para cualquier usuario nuevo que solo tenga Polar**.

Tampoco hay recuperación de contraseña: si un usuario que sí tiene cuenta la olvida, queda bloqueado sin
ninguna salida dentro de la app.

---

## 2. Fallo crítico: la app no sincroniza al abrirse

Este es el síntoma más visible de todo el sistema: **abrir Polar no dispara ninguna lectura de
Supabase**. Si otro dispositivo o la otra app crearon/editaron una tarea mientras Polar estaba cerrado, el
usuario puede abrir Polar, ver la lista de tareas y quedarse mirando **datos desactualizados** un rato
largo, sin ningún indicio de que hay algo más reciente en la nube.

### 2.1. Ningún punto del código llama a `SyncWorker.triggerImmediateSync()` al abrir/reanudar la app

Se ha revisado cada llamada existente a `SyncWorker.triggerImmediateSync(...)` en todo el proyecto:

```
PolarApplication.kt        → no la llama (solo schedulePeriodic/scheduleFrequentSync, ver 2.2)
TaskViewModel.kt:96         → dentro de safeLaunch(), después de cada escritura local del usuario
TaskListViewModel.kt:38     → dentro de safeLaunch(), después de cada escritura local del usuario
RemindersViewModel.kt:75    → dentro de safeLaunch(), después de cada escritura local del usuario
AuthViewModel.kt:67         → solo justo después de un signIn() correcto
```

Es decir: la sincronización inmediata **solo se dispara como efecto secundario de que el propio
dispositivo escriba algo** (crear/editar/borrar una tarea, lista, subtarea o recordatorio) o de iniciar
sesión. No existe ningún disparador ligado a "la app se ha abierto" ni a "la app ha vuelto a primer
plano". Ni `MainActivity`, ni `BaseActivity` ([BaseActivity.kt](app/src/main/java/app/polar/ui/activity/BaseActivity.kt),
revisado íntegro: solo gestiona tema/idioma/status bar, cero relación con sync), ni ningún `Fragment`
llaman a `SyncManager.sync()`/`SyncWorker.triggerImmediateSync()` desde `onCreate`/`onStart`/`onResume`.
Tampoco hay un `ProcessLifecycleOwner` observando transiciones a primer plano en ningún punto del proyecto
(`grep -r "ProcessLifecycleOwner"` no devuelve resultados).

En la práctica esto significa que un usuario que **solo abre la app para mirar sus tareas**, sin editar
nada, nunca provoca un pull — ve exactamente lo que ya había en Room desde la última vez que algo (una
edición propia, un login, o el propio `SyncWorker` en segundo plano) disparó una sincronización.

### 2.2. Lo que sí existe en `PolarApplication.onCreate()` no cubre el caso "abrir la app"

[PolarApplication.kt:30-42](app/src/main/java/app/polar/PolarApplication.kt):

```kotlin
app.polar.worker.SyncWorker.schedulePeriodic(this)
...
if (entryPoint.getSupabaseClient().auth.currentUserOrNull() != null) {
  app.polar.worker.SyncWorker.scheduleFrequentSync(this)
}
```

- `schedulePeriodic()` usa `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)`
  ([SyncWorker.kt:59-68](app/src/main/java/app/polar/worker/SyncWorker.kt)): `KEEP` significa que si ya
  existe un trabajo periódico programado (que existirá desde la primera vez que se instaló/abrió la app),
  **esta llamada no hace nada** en aperturas sucesivas — no relanza ni adelanta ninguna ejecución. El
  único efecto real es que, de fondo, cada ~15 minutos (sujeto además al *Doze*/ahorro de batería de
  Android, que puede retrasarlo más) se ejecuta un ciclo de sync — pero eso no está atado a "abrir la
  app", es un reloj independiente que puede llevar minutos sin dispararse justo cuando el usuario decide
  abrir Polar.
- `scheduleFrequentSync()` solo se invoca si `currentUserOrNull() != null`, y aun así **no sincroniza al
  momento**: encola un `OneTimeWorkRequest` con `setInitialDelay(3, TimeUnit.MINUTES)`
  ([SyncWorker.kt:85-95](app/src/main/java/app/polar/worker/SyncWorker.kt)) — es decir, en el mejor caso
  (proceso recién arrancado, sesión iniciada) la primera sincronización posible tras abrir la app tarda
  **3 minutos**, no es inmediata.
- Ninguna de las dos rutas llama a `triggerImmediateSync()`, que es el único método que encola un
  `OneTimeWorkRequest` sin retraso (`ExistingWorkPolicy.REPLACE`, sin `setInitialDelay`,
  [SyncWorker.kt:70-79](app/src/main/java/app/polar/worker/SyncWorker.kt)).

Además, `PolarApplication.onCreate()` solo se ejecuta cuando Android crea el **proceso** de la app desde
cero. Si el usuario cierra Polar con el botón atrás/recientes (el proceso sigue vivo en background) y
luego lo vuelve a abrir minutos u horas después, `onCreate()` **no se vuelve a ejecutar en absoluto** —
ni siquiera se repite el chequeo de `scheduleFrequentSync()`. Si la cadena de 3 minutos ya se había
agotado (`doWork()` solo se reprograma a sí misma mientras hay sesión activa **y** el propio `doWork()`
llega a ejecutarse; ver comentario en [SyncWorker.kt:34-38](app/src/main/java/app/polar/worker/SyncWorker.kt)),
reabrir la app no hace nada por reactivarla: hay que esperar a que el `PeriodicWorkRequest` de 15 minutos
vuelva a disparar `doWork()`, que es lo único que re-arma `scheduleFrequentSync()`.

### 2.3. Consecuencia combinada con el bug 3.1 (Realtime sin usar)

Como además no hay ninguna suscripción Realtime activa (ver sección 4.1), no existe **ningún** mecanismo
de "empuje" (push desde servidor) ni de "al abrir, comprueba" (pull en apertura) que garantice datos
frescos al entrar a la app. Todo el sistema depende exclusivamente de temporizadores de fondo de
WorkManager (15 min garantizados, 3 min mientras la cadena esté viva) y de que el propio usuario edite
algo. El peor caso realista — app cerrada varias horas, se reabre, el usuario solo consulta sin editar —
puede dejar la pantalla mostrando datos de hace horas sin ningún indicio visual de ello (ver también 4.9,
falta de indicador "última sincronización").

**Esto es, con diferencia, el hueco más fácil de notar por un usuario real**: es la razón concreta por la
que "la app no lee los datos de Supabase cada vez que se abre", tal y como se ha reportado.

---

## 3. Bugs de integridad de datos (reproducibles)

### 3.1. Vaciar la papelera puede resucitar la tarea/recordatorio en el siguiente pull

`TaskRepository.emptyTrash()`/`permanentDeleteTask()` y `ReminderRepository.emptyTrash()`/
`permanentDelete()` llaman directo a `taskDao.emptyTrash()` / `taskDao.permanentDelete()` (y equivalentes
de `ReminderDao`), que son un `DELETE` físico de SQLite
([TaskDao.kt:59-63](app/src/main/java/app/polar/data/dao/TaskDao.kt)). Esa ruta **no comprueba `dirty`**
antes de borrar la fila local.

Secuencia que reproduce el bug:
1. Usuario borra una tarea (`moveToTrash`) → `touchedDeleted()` pone `isDeleted=true, dirty=true`.
2. Antes de que el siguiente `SyncWorker` corra (offline, o simplemente dentro de la ventana de hasta 3
   minutos — ver sección 2), el usuario entra a la papelera y pulsa "vaciar papelera" / "eliminar
   definitivamente".
3. La fila desaparece físicamente de Room **sin que el tombstone (`is_deleted=true`) llegara nunca a
   Supabase** — el registro remoto sigue existiendo tal cual estaba antes de borrarlo.
4. En el siguiente `pull()`, `SyncManager.pullTasks()`/`pullReminders()` trae esa fila remota (sigue
   teniendo `updated_at` reciente, sigue sin `is_deleted`), no encuentra fila local con ese `uuid`
   (`taskDao.getByUuid` devuelve null) y `resolvePullAction` devuelve `INSERT`
   ([SyncManager.kt:228-236](app/src/main/java/app/polar/data/sync/SyncManager.kt)) → **la tarea que el
   usuario acaba de borrar definitivamente reaparece sola**.

El mismo patrón aplica a recordatorios. La corrección correcta es que "vaciar papelera"/"eliminar
definitivamente" solo purguen localmente las filas que ya tengan `dirty = false` (es decir, cuyo
tombstone ya se confirmó en servidor), y para las que aún tengan `dirty = true` forzar un push previo o
bloquear la purga hasta confirmarlo.

### 3.2. Purgar una tarea deja subtareas huérfanas que también pueden resucitar

`taskDao.permanentDelete(taskId)` es un `DELETE FROM tasks WHERE id = :taskId`. El `ForeignKey` de
`Subtask` hacia `Task` usa `onDelete = ForeignKey.CASCADE`
([Subtask.kt:10-17](app/src/main/java/app/polar/data/entity/Subtask.kt)), así que SQLite borra en
cascada, **de forma física**, todas las subtareas de esa tarea — sin pasar nunca por
`subtask.touchedDeleted()`. Si alguna de esas subtareas tenía cambios sin subir (`dirty=true`) o
simplemente no le había dado tiempo al `SyncWorker` a confirmarlas, su tombstone tampoco llega a
Supabase y sufren exactamente el mismo problema del punto 3.1: reaparecen fantasma en el siguiente pull,
ahora colgando de una tarea que ya no existe localmente (`taskDao.getByUuid(dto.taskId)` puede devolver
null si la tarea padre tampoco se sincronizó a tiempo, en cuyo caso la subtarea se descarta en silencio
— otro estado inconsistente, ver 4.5).

Adicionalmente, `TaskRepository.softDeleteTask()` (mover una tarea a la papelera) **no** hace cascada de
soft-delete sobre sus subtareas — a diferencia de `deleteTaskList()`, que sí cascada correctamente a
tareas y subtareas ([TaskRepository.kt:45-55](app/src/main/java/app/polar/data/repository/TaskRepository.kt)
vs. [TaskRepository.kt:181-183](app/src/main/java/app/polar/data/repository/TaskRepository.kt)). Esto
deja subtareas "activas" colgando de una tarea en papelera, que es justo el estado que hace más probable
tropezar con el bug de arriba en cuanto esa tarea se purgue.

### 3.3. Rutas de borrado físico siguen vivas fuera del pipeline de sync

`TaskRepository.deleteTask(task)` → `taskDao.delete(task)` (borrado físico de Room `@Delete`) y
`TaskRepository.deleteSubtask(subtask)` → `subtaskDao.delete(subtask)` siguen existiendo y expuestas
públicamente (`TaskViewModel.deleteTask()`,
[TaskViewModel.kt:413-415](app/src/main/java/app/polar/ui/viewmodel/TaskViewModel.kt)). Hoy no hay
ningún botón de UI que llame a `viewModel.deleteTask()` (todo el flujo de UI usa `moveToTrash` /
`softDeleteTask`), así que a día de hoy es código muerto — pero es una trampa: su nombre es casi
idéntico a `moveToTrash`/`softDeleteTask`, no tiene ningún comentario de aviso, y si alguien lo conecta a
un botón en el futuro (o un IDE lo autocompleta) el borrado **nunca se propaga a Supabase ni a la otra
app**. Lo mismo aplica a `ReminderRepository.delete(reminder)` (sin usar, mismo riesgo). Deberían
eliminarse o, como mínimo, marcarse `@Deprecated` con aviso explícito de que rompen el contrato de sync.

---

## 4. Huecos respecto al propio diseño documentado

### 4.1. Realtime instalado pero nunca usado

`SupabaseModule` instala el plugin `Realtime` y `app/build.gradle.kts` añade la dependencia
`realtime-kt` ([SupabaseModule.kt:41](app/src/main/java/app/polar/di/SupabaseModule.kt)), tal y como pide
el documento 06 (punto 0 y punto 7: *"Suscripción Realtime activa mientras la app está en foreground...
aplicando cada evento entrante con la misma lógica de merge del pull"*). No hay ninguna llamada a
`channel(...)`/`postgresChangeFlow` en todo el proyecto — se grepeó `data/sync`, `worker`, `ui/` sin
resultado. El resultado práctico es que **la latencia real de sincronización entre dispositivos/apps es
de 3 minutos en primer plano (o hasta 15 en segundo plano)**, no "casi instantánea" como sugiere la
documentación de estrategia (doc 04, sección Realtime), y agrava directamente el fallo de la sección 2:
sin Realtime y sin sync-al-abrir, no queda ningún mecanismo que refresque los datos con prontitud. Es
dependencia y superficie de ataque sin beneficio: o se implementa la suscripción, o se retira el
`install(Realtime)` y la dependencia para no sugerir una capacidad que no existe.

### 4.2. Primer login no distingue "fusionar" de "descartar", como el propio doc 04 recomienda

`AuthViewModel.signIn()` dispara `SyncWorker.triggerImmediateSync()` inmediatamente después de un login
correcto, sin ningún diálogo ([AuthViewModel.kt:60-71](app/src/main/java/app/polar/ui/viewmodel/AuthViewModel.kt)).
El documento 04 (*"Primera sincronización"*) es explícito: como no hay deduplicación automática por
`uuid`, la recomendación de producto es preguntar al usuario si quiere "subir mis datos locales" o
"descartar datos locales y usar solo lo de la nube" antes de fusionar a ciegas. Esa pantalla/diálogo no
existe. Consecuencia: cualquier usuario que instale Polar en un segundo dispositivo con datos locales de
prueba, o que ya tenga datos en la nube desde la otra app, **termina con listas y tareas duplicadas la
primera vez que inicia sesión**, sin ningún aviso.

### 4.3. Conflictos perdidos se descartan en silencio

Cuando `resolvePushOutcome` devuelve `LOST`, `SyncManager` sobreescribe la fila local con la versión del
servidor sin generar ningún evento observable por la UI
([SyncManager.kt:132-181](app/src/main/java/app/polar/data/sync/SyncManager.kt)). Si el usuario editó el
título/descripción de una tarea y perdió el conflicto LWW frente a otro dispositivo, su edición
desaparece sin ningún aviso ("se ha descartado tu cambio porque alguien editó esto después" o similar).
Es coherente con el diseño LWW documentado, pero el diseño nunca contempló cómo comunicárselo al usuario
— hoy simplemente no se comunica.

### 4.4. Snooze de notificación no se sincroniza (viola literalmente el doc 04)

El documento 04 dice explícitamente: *"esto aplica también a mutaciones de sistema... el
`NotificationActionReceiver` al completar/posponer desde una notificación. Ambos deben marcar `dirty =
true` y actualizar `updatedAt`"*. En el código,
[`NotificationActionReceiver.kt`](app/src/main/java/app/polar/receiver/NotificationActionReceiver.kt):
- `ACTION_COMPLETE` sí hace `task.copy(completed = true).touched()` (línea 38) — correcto.
- `ACTION_SNOOZE` (línea 44-55) **solo reprograma la alarma local** (`alarmHelper.scheduleTaskAlarm`) y
  cancela la notificación; nunca toca `Task.dueDate`/`updatedAt`/`dirty` en Room.

Resultado: posponer una tarea desde la notificación en un dispositivo no se refleja en absoluto en el
otro dispositivo ni en la otra app — el resto de los clientes siguen viendo la fecha/hora original.

### 4.5. No hay manejo del caso "tombstone purgado en servidor" que el propio doc 04 pide documentar

El doc 04 (*"Pull"*, punto 4) pide detectar cuando una fila purgada en servidor (`DELETE` físico tras 30
días de tombstone, ver doc 03) deja de aparecer en los pulls sucesivos y tratarla como borrado remoto
real. `SyncManager.pull*()` solo procesa filas que el `select ... gt(updated_at, since)` efectivamente
devuelve; una fila purgada nunca vuelve a aparecer en ningún resultado, así que nunca se detecta ni se
hace hard-delete local — el dispositivo se queda con una fila "fantasma" en su papelera para siempre. El
propio doc lo califica de "caso raro, no prioritario", así que es un gap de baja severidad, pero sigue
sin resolverse tal y como el diseño pedía documentar.

### 4.6. Sin equivalente simétrico de "descargar todo y sobrescribir local"

`SettingsFragment` solo ofrece "subir copia completa a la nube" (`SyncManager.pushAllOverwrite()`,
destructivo hacia el servidor). No existe el botón inverso ("descargar todo de la nube y sobrescribir
este dispositivo"), útil para un dispositivo nuevo/reseteado que quiere partir de cero desde la nube en
vez de fusionar. Hoy esa operación solo ocurre implícitamente vía el `pull()` normal (que si `lastSyncAt
= 0` trae todo), pero mezclado con cualquier dato local ya existente — no hay forma explícita de decir
"ignora lo local, gana la nube".

### 4.7. Cursor de pull con comparación estricta puede perder una fila en un caso límite de carrera

`pull()` captura `pullStartedAt` **antes** de lanzar las 4 queries y lo guarda como `lastSyncAt` al
terminar ([SyncManager.kt:183-194](app/src/main/java/app/polar/data/sync/SyncManager.kt)), justamente
para no perder una fila escrita en el servidor a mitad del pull. Pero el filtro usado es `gt("updated_at",
since)` (estrictamente mayor). Si una fila remota se escribe con `updated_at` **exactamente igual** al
`pullStartedAt` capturado (colisión de milisegundo, plausible si dos filas se tocan en el mismo `now()` o
si el trigger de servidor calcula su timestamp en el mismo instante), esa fila:
- No se recoge en el pull actual (se capturó el cursor antes de que se escribiera).
- Tampoco se recogerá en el **siguiente** pull, porque el próximo filtro será `updated_at > pullStartedAt`
  y su `updated_at` es igual, no mayor.

Es un caso límite (una colisión exacta de milisegundo), pero es un fallo real de exactitud del algoritmo
tal y como está escrito; se resolvería usando `gte` combinado con deduplicación por `uuid`, o restando 1ms
al cursor guardado.

### 4.8. Push sin batching: un roundtrip HTTP por fila sucia

`pushTaskLists`/`pushTasks`/`pushSubtasks`/`pushReminders` iteran las filas `dirty` con `forEach` y hacen
un `upsert` (una petición HTTP) por fila
([SyncManager.kt:127-181](app/src/main/java/app/polar/data/sync/SyncManager.kt)), en vez de agrupar en un
único `upsert(list)` por tabla (que Postgrest soporta de forma nativa). Reordenar una lista de 50 tareas
(`updateTasksOrder`) marca las 50 como `dirty` de golpe
([TaskViewModel.kt:442-444](app/src/main/java/app/polar/ui/viewmodel/TaskViewModel.kt)) y genera 50
peticiones secuenciales en el siguiente ciclo de sync — lento y más proclive a fallos parciales (si la
petición 30 de 50 falla, las 29 anteriores ya se marcaron `dirty=false` pero las 20 restantes quedan
pendientes hasta el siguiente ciclo, dejando el pedido a medio subir un tiempo).

### 4.9. Fallos silenciosos sin ningún estado visible

En todo `data/sync/`, el patrón dominante es `catch (e: Exception) { e.printStackTrace() }` sin propagar
nada a la UI:
- `TaskImageStorage.upload()`/`downloadToCache()` devuelven `null` en cualquier error de red/IO — la
  imagen adjuntada silenciosamente nunca llega a `imagePath`, y no hay cola de reintento; el usuario cree
  que la imagen está sincronizada porque la ve localmente (`imageUri`), pero nunca sale de ese
  dispositivo.
- `SyncWorker.doWork()` traga cualquier excepción y devuelve `Result.retry()`
  ([SyncWorker.kt:40-43](app/src/main/java/app/polar/worker/SyncWorker.kt)) — si la causa es persistente
  (credenciales revocadas, RLS mal configurada, tabla inexistente), el worker reintenta indefinidamente
  con backoff de WorkManager sin que nada en Ajustes le diga al usuario "la sincronización lleva fallando
  desde hace X".
- `SettingsFragment` no muestra ni "última sincronización: hace N minutos" ni ningún indicador de error —
  la única señal de sync que ve el usuario es el email de la cuenta y el botón destructivo de sobrescribir
  la nube. Combinado con la sección 2 (no hay sync al abrir), el usuario no tiene ninguna forma de saber
  si lo que está viendo está al día o lleva horas desactualizado.

### 4.10. Las cadenas de autenticación/cuenta/nube no están traducidas — solo existen en español

Se ha comprobado cuántas claves `auth_*`/`account_*`/`cloud_*` existen en cada fichero de recursos:

```
values/strings.xml (es, default) → 21
values-de/strings.xml            → 0
values-en-rGB/strings.xml        → 0
values-en-rUS/strings.xml        → 0
values-fr/strings.xml            → 0
```

Las 21 cadenas de la pantalla de login (`AuthActivity`), del bloque "cuenta y sincronización" de
`SettingsFragment` y de los diálogos de sobrescritura de nube ([strings.xml:193-214](app/src/main/res/values/strings.xml))
solo existen en el fichero por defecto (español). La app sí soporta selección de idioma — el usuario puede
elegir inglés (UK/US), alemán o francés desde Ajustes (`SettingsFragment.setupLanguageSelection()`,
aplicado vía `BaseActivity.attachBaseContext()` con `config.setLocale(...)`) — pero como Android resuelve
un recurso ausente en el idioma elegido cayendo de vuelta al `values/` por defecto, cualquier usuario que
use la app en inglés, alemán o francés verá **la pantalla de login, la sección de cuenta en Ajustes y los
diálogos de sobrescritura de nube íntegramente en español**, mientras el resto de la interfaz está en su
idioma. Es una inconsistencia visible en cuanto se toca esta parte de la app y contradice el trabajo de
localización ya hecho en el resto del proyecto (existen `values-de`, `values-en-rGB`, `values-en-rUS` y
`values-fr` completos para todo lo demás).

### 4.11. Expiración/revocación de sesión: el sync se para en silencio sin que nada lo detecte

`SyncManager.sync()` empieza con `val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return
Result.success(Unit)` ([SyncManager.kt:36](app/src/main/java/app/polar/data/sync/SyncManager.kt)): si no
hay sesión, el método **devuelve éxito** sin hacer nada — un no-op indistinguible, desde fuera, de "no
había nada que sincronizar". Esto es correcto para el caso normal de "usuario nunca ha iniciado sesión",
pero también es exactamente lo que pasa si una sesión que **sí** existía deja de ser válida (token
revocado desde el dashboard de Supabase, expiración que falla al refrescar, borrado de la cuenta, etc.):
`SyncWorker` seguirá ejecutándose cada 15 minutos, llamando a `sync()`, recibiendo `Result.success(Unit)`
cada vez, y no habrá ni un solo error en ningún log ni estado que indique que la sincronización real se
detuvo hace tiempo.

Se ha comprobado además que no existe ninguna observación reactiva del estado de autenticación en todo el
proyecto (`grep -r "sessionStatus\|SessionStatus\|onAuthStateChange"` no devuelve resultados).
`AuthViewModel._signedIn` es un `MutableStateFlow<Boolean>` que solo se escribe a mano dentro de
`signIn()`/`signOut()` ([AuthViewModel.kt:35,65,77](app/src/main/java/app/polar/ui/viewmodel/AuthViewModel.kt)) —
nunca se suscribe a `supabaseClient.auth.sessionStatus` (el flujo que el SDK expone precisamente para
reaccionar a refrescos/expiraciones/revocaciones de sesión en tiempo real). El único punto que sí relee el
estado real es `SettingsFragment.updateAccountStatus()` en `onResume()` (vía `currentUserOrNull()`
directo), así que la sección de Ajustes se autocorrige al volver a visitarla, pero mientras tanto — y en
cualquier otra pantalla — la app puede seguir comportándose como si la sesión siguiera activa. Combinado
con 4.9 (sin estado de sync visible), un usuario cuya sesión caduque puede pasar días sin darse cuenta de
que sus dispositivos han dejado de sincronizarse entre sí.

---

## 5. Riesgo de diseño heredado del propio esquema de Supabase (doc 03)

El trigger `touch_and_resolve_lww()` documentado en
[03-esquema-supabase.md](agent-docs/supabase-sync/03-esquema-supabase.md) solo reescribe
`NEW.server_updated_at := now()`, **nunca** `NEW.updated_at`. Esto contradice el principio de diseño nº4
del mismo documento (*"`updated_at` se actualiza siempre por trigger de servidor, nunca confiar en que el
cliente lo mande bien... así el Last-Write-Wins es fiable incluso si el reloj de un dispositivo está mal
ajustado"*): tal y como está escrita la función SQL, `updated_at` sigue siendo el valor que manda el
cliente, así que un dispositivo con el reloj adelantado seguirá ganando cualquier conflicto futuro para
siempre, exactamente el escenario que el principio de diseño decía prevenir. `SyncManager` en Android es
fiel a lo que el trigger realmente hace (compara `updated_at` devuelto, no `server_updated_at`), así que
esto no es un bug del cliente — es una inconsistencia entre lo que el propio doc 03 promete en prosa y el
SQL que da como implementación. Si ese SQL es efectivamente el que está desplegado en el proyecto
Supabase real, hay que decidir: o el trigger empieza a pisar `updated_at` con `now()` (y entonces hay que
revisar `resolvePushOutcome`, que hoy asume que puede coincidir con lo que mandó el cliente cuando gana),
o se documenta que el LWW **sí** depende del reloj del dispositivo y se acepta ese riesgo.

---

## 6. Cobertura de tests

Solo existen dos ficheros de test para todo `data/sync/`:
- `EntityMappersTest.kt` (120 líneas) — traducción DTO↔entidad, CSV↔array de tags.
- `MergeResolverTest.kt` (41 líneas) — las funciones puras `resolvePushOutcome`/`resolvePullAction`.

**Sin ningún test** para:
- `SyncManager.push()`/`pull()`/`sync()`/`pushAllOverwrite()` — la orquestación real, incluidas las rutas
  de "padre no encontrado localmente → `return@forEach`" (huecos de dependencia listas→tareas→subtareas).
- `SyncWorker` — el encadenado de reintentos cada 3 minutos, el reagendado en `doWork()`, y la ausencia de
  disparo al abrir la app (sección 2) — un test de integración lo habría hecho evidente antes de llegar a
  producción.
- La migración `MIGRATION_14_15`/`MIGRATION_15_16`/`MIGRATION_16_17` (backfill de `uuid`/`updatedAt`,
  índice único) — nada verifica con una base de datos real pre-poblada que el backfill no colisione ni
  dispare el `fallbackToDestructiveMigration()` accidentalmente.
- `TaskImageStorage` — subida/descarga de Storage.
- `AuthViewModel` — sign-in, sign-out, ni el hecho de que no exista sign-up.

El doc 06 (punto 10) pedía explícitamente tests para "el diff de subtareas del punto 3" y la traducción
DTO↔entidad — lo segundo está cubierto, lo primero (`TaskRepository.replaceSubtasksForTask`) no tiene
ningún test dedicado.

---

## 7. No verificable desde el código (requiere el dashboard de Supabase)

- Si las 4 tablas (`task_lists`, `tasks`, `subtasks`, `reminders`) y sus políticas RLS (doc 03) están
  realmente creadas tal cual el DDL documentado, o si divergieron durante la implementación manual.
- Si el trigger `touch_and_resolve_lww` y las policies de Storage (`task-images`) están desplegados y
  coinciden con el DDL de este repo (ver riesgo de la sección 5).
- Si la réplica lógica para Realtime está habilitada (aunque no se use desde el cliente, ver 4.1).
- Si `pg_cron` está activo y programando `purge_old_tombstones()`/`roll_recurring_tasks()` — sin esto, la
  papelera remota nunca se purga (crecimiento indefinido) y la recurrencia solo se resuelve vía el
  `RecurrenceWorker` local de cada dispositivo (funciona, pero diverge de "igual sin importar qué app esté
  abierta" que promete el doc 03).
- Si el proveedor de email de Supabase Auth exige confirmación de email — irrelevante mientras no exista
  sign-up en la app (punto 1.1), pero bloqueante en cuanto se implemente.

---

## 8. Tabla resumen

`Esfuerzo` es una estimación relativa (S = horas, M = 0.5-1 día, L = 1-3 días, XL = varios días/decisión de
producto), no una medida de tiempo formal. `Fase` enlaza cada hallazgo con el roadmap detallado de la
sección 10.

| # | Hallazgo | Severidad | Tipo | Esfuerzo | Fase | Resultado |
|---|---|---|---|---|---|---|
| 1.1 | Sin registro de cuenta ni recuperación de contraseña | **Crítico** | Producto/UX | L | 2 | **Resuelto parcialmente, por decisión de producto (2026-08-19):** Polar es un proyecto personal — el usuario decidió explícitamente que crear cuentas nuevas debe seguir sin ser posible desde la app, así que **2.1 (sign-up) queda deliberadamente sin implementar y oculto** (la UI de `AuthActivity` sigue sin ningún botón/pestaña de registro; las cuentas se siguen creando solo desde la "otra app", doc 07). Sí se implementó 2.2: `AuthViewModel.resetPassword(email)` (`resetPasswordForEmail`) + enlace "¿olvidaste tu contraseña?" en `AuthActivity`, para que una cuenta ya existente no quede bloqueada si se olvida la contraseña. |
| 2 | La app no sincroniza al abrirse/reanudarse (solo por edición local o cada 3-15 min de fondo) | **Crítico** | Bug de comportamiento | S | 1 | **Resuelto (2026-08-19):** `PolarApplication` registra un `DefaultLifecycleObserver` sobre `ProcessLifecycleOwner.get().lifecycle` cuyo `onStart()` llama a `SyncWorker.triggerImmediateSync()` si hay sesión activa. Cubre tanto el arranque en frío como volver a la app desde recientes (a diferencia de `Application.onCreate()`, que solo corre una vez por proceso). `lifecycle-process` ya estaba en el classpath transitivamente (verificado con `./gradlew :app:dependencies`), no hizo falta añadir la dependencia. |
| 3.1 | Vaciar papelera puede resucitar tareas/recordatorios | **Crítico** | Bug de datos | M | 1 | **Resuelto (2026-08-19):** `TaskDao`/`ReminderDao`.`emptyTrash()`/`permanentDelete()` ahora son `DELETE ... WHERE ... AND dirty = 0` (devuelven el nº de filas purgadas). `TaskViewModel`/`RemindersViewModel` inyectan `SyncManager` y fuerzan un `sync()` antes de purgar para maximizar lo que ya esté confirmado en servidor; si algo sigue `dirty=1` tras el intento (p. ej. sin conexión), no se purga y se avisa al usuario vía el `errorMessage`/Snackbar que `TrashFragment` ya observaba (strings nuevos `trash_item_purge_pending_sync`/`trash_purge_pending_sync_count`, traducidos a los 5 locales). Tests: `TaskRepositoryTest`/`ReminderRepositoryTest` (nuevo) y `TaskViewModelTest`. |
| 3.2 | Purgar tarea deja subtareas huérfanas que resucitan | **Alto** | Bug de datos | M | 1 | **Resuelto (2026-08-19):** `TaskRepository.softDeleteTask()` ahora cascada `touchedDeleted()` a todas las subtareas activas de la tarea (igual que `deleteTaskList()`), y `TaskDao.permanentDelete()`/`emptyTrash()` añaden un `NOT EXISTS (SELECT 1 FROM subtasks WHERE subtasks.taskId = tasks.id AND subtasks.dirty = 1)` que bloquea el borrado físico — y por tanto el `ON DELETE CASCADE` de Room — mientras alguna subtarea siga sin confirmar en servidor. Test de regresión: `softDeleteTask cascades the tombstone to every active subtask` en `TaskRepositoryTest`. |
| 3.3 | Rutas de hard-delete vivas fuera del pipeline de sync | Medio | Deuda técnica/riesgo | S | 1 | **Resuelto (2026-08-19):** eliminados `TaskRepository.deleteTask()`/`deleteSubtask()`, `TaskDao.delete()`, `SubtaskDao.delete()`, `TaskViewModel.deleteTask()`, `ReminderRepository.delete()` y `ReminderDao.delete()` — se reconfirmó con grep que ninguno tenía ninguna llamada desde la UI antes de borrarlos. `grep -rn "\.delete(" app/src/main/java/app/polar/data` ya no devuelve hard-deletes de `Task`/`Subtask`/`Reminder`. |
| 4.1 | Realtime instalado pero sin usar | Medio | Gap vs. diseño | S (retirar) / XL (implementar) | 6 | **Resuelto, Opción B (2026-08-19), decisión explícita del usuario:** `SyncManager` (ahora `@Singleton`) gana `startRealtime()`/`stopRealtime()`, que suscriben un canal `postgres_changes` por cada una de las 4 tablas (filtradas por `user_id`) y aplican cada evento entrante con exactamente la misma lógica de merge que `pull()` — se extrajeron `applyTaskListDto`/`applyTaskDto`/`applySubtaskDto`/`applyReminderDto` de los antiguos `pullTaskLists`/... para que ambos caminos compartan una única implementación, tal y como pedía el doc 04. `PolarApplication` liga la suscripción a la combinación de `sessionStatus` (autenticado) y `ProcessLifecycleOwner` (primer plano) vía `combine(...)`: se suscribe en `onStart` si hay sesión, se cancela en `onStop` o al perder la sesión, igual que ya hacía la cadena de 3 minutos. Un DELETE físico (solo ocurre cuando `pg_cron` purga un tombstone de 30 días, doc 03) solo trae la fila mínima en `oldRecord`, así que se extrae el `uuid` con la función pura nueva `extractUuidFromOldRecord` (`MergeResolver.kt`, con tests) y se reutiliza el `permanentDelete()` ya gateado por `dirty = 0` (hallazgo 3.1) — sin ese gate no se purga nada, evitando resucitar una fila con cambios locales sin confirmar. Realtime es una optimización de latencia pura: si el evento se pierde (app en segundo plano, fallo de `subscribe()`, red caída), el próximo `pull()` vía `SyncWorker` sigue siendo quien reconcilia de verdad, así que no hay ningún camino nuevo que pueda perder datos. Verificado con `./gradlew :app:compileDebugKotlin` y `./gradlew :app:testDebugUnitTest` (59 tests, verde, incluye 3 tests nuevos en `MergeResolverTest` para `extractUuidFromOldRecord`). |
| 4.2 | Sin diálogo de fusión en primer login → duplicados | Alto | Gap vs. diseño | M | 3 | **Resuelto (2026-08-19):** tras un `signIn()` correcto, si `SyncPrefs.lastSyncAt == 0L` y `SyncManager.hasLocalData()` es cierto, `AuthViewModel` expone `pendingMergeDecision` y `AuthActivity` muestra un diálogo con "subir mis datos locales" (`confirmMergeUpload()`, el sync push+pull normal) o "descartar datos locales y usar solo la nube" (`confirmDiscardLocalUseCloud()` → nuevo `SyncManager.discardLocalAndPullFromCloud()`: vacía las 4 tablas locales, resetea el cursor a 0 y hace un pull completo), esta última con una segunda confirmación destructiva. |
| 4.3 | Conflictos perdidos se descartan sin avisar | Medio | UX | M | 4 | **Resuelto (2026-08-19):** `SyncPrefs` gana `lostConflictsCount: Int`. `SyncManager.push*()` ahora devuelven cuántas filas resolvieron `PushOutcome.LOST` en ese ciclo; `sync()` suma el total a `syncPrefs.lostConflictsCount` en cada intento exitoso. `SettingsFragment` muestra un aviso (`layoutSyncConflictWarning`, oculto si el contador es 0) con el texto `sync_conflicts_lost_message` ("N cambios se sobrescribieron por ediciones más recientes en otro dispositivo") y un botón "descartar" que resetea el contador a 0. `pushAllOverwrite()` no suma a este contador a propósito (es una operación de "gana mi dispositivo" deliberada, no un conflicto real que avisar). |
| 4.4 | Snooze de notificación no sincroniza | Medio | Bug vs. spec (doc 04) | S | 3 | **Resuelto (2026-08-19):** `NotificationActionReceiver.ACTION_SNOOZE` ahora también carga la tarea y persiste `task.copy(dueDate = snoozeTime).touched()` antes de reprogramar la alarma, igual que ya hacía `ACTION_COMPLETE`, y dispara `SyncWorker.triggerImmediateSync()` al final del receiver. |
| 4.5 | Tombstones purgados no se detectan (fantasmas en papelera) | Bajo | Gap documentado | M | 3 | **Resuelto (2026-08-19), implementado sin esperar a la verificación de 0.1 (decisión explícita del usuario):** `SyncManager.pull()` llama a `purgeTombstonesMissingRemote(userId)` tras cada pull, que compara los tombstones locales ya confirmados (`dirty = 0`) de tasks/reminders (las únicas dos tablas con papelera real) contra el servidor por `uuid` (`isIn`) y purga localmente cualquiera que ya no exista remotamente. Alcance acotado a Task/Reminder porque son las únicas entidades con un flujo de papelera/purga explícito; task_lists/subtasks no tienen ese concepto hoy. |
| 4.6 | Sin "descargar todo y sobrescribir local" | Bajo | Gap de producto | M | 4 | **Resuelto (2026-08-19):** nuevo `SyncManager.pullAllOverwrite()`, delegado en `discardLocalAndPullFromCloud()` (ya existente desde la Fase 3.1: vacía las 4 tablas locales, resetea el cursor a 0 y hace un pull completo) — mismo mecanismo, expuesto con el nombre simétrico a `pushAllOverwrite()`. `SettingsFragment` añade el botón `btnCloudFullDownload` junto al ya existente "subir copia completa", con su propio diálogo de confirmación destructiva análogo (`cloud_full_download_warning_*`). |
| 4.7 | Cursor `gt` estricto puede perder fila en colisión de ms | Bajo | Edge case | S | 3 | **Resuelto (2026-08-19):** `SyncManager.pull()` guarda `nextSyncCursor(pullStartedAt)` (`pullStartedAt - 1`, función pura nueva en `MergeResolver.kt`) en vez de `pullStartedAt` directamente, para que una fila escrita en el mismo milisegundo que arrancó el pull siga siendo recogida por el `gt("updated_at", since)` del siguiente ciclo. Tests nuevos en `MergeResolverTest`. |
| 4.8 | Push sin batching (1 request por fila) | Medio | Rendimiento | M | 5 | **Resuelto (2026-08-19):** `pushTaskLists`/`pushTasks`/`pushSubtasks`/`pushReminders` ahora construyen la lista completa de DTOs `dirty` y hacen un único `upsert(list) { select() }` por tabla, en vez de un `upsert` por fila dentro de un `forEach`. Las filas devueltas se emparejan con su fila local por `uuid` (`associateBy { it.id }` sobre la respuesta, buscado luego por `local.uuid`), nunca por posición/orden de la lista, ya que Postgrest no garantiza que el orden de la respuesta coincida con el del array enviado. El orden de dependencia entre tablas (listas → tareas → subtareas → recordatorios) se mantiene igual que antes, solo se agrupó el `forEach` interno de cada tabla. `TaskListDao`/`TaskDao` ya tenían `updateAll()`; se añadió `updateAll()` a `SubtaskDao` y `ReminderDao` para aplicar en bloque los resultados `WON`/`LOST` de cada ciclo. Verificado con `./gradlew :app:compileDebugKotlin` y `./gradlew :app:testDebugUnitTest` (verde, sin tests nuevos porque no se tocó ninguna función pura — `resolvePushOutcome`/`resolvePullAction` ya estaban cubiertas y `SyncManager` en conjunto sigue sin tests de integración, hallazgo 6/Fase 7). |
| 4.9 | Fallos silenciosos sin estado visible (imagen, sync) | Medio | UX/observabilidad | M | 4 | **Resuelto parcialmente (2026-08-19):** las dos sub-causas de `SyncManager`/`SyncWorker` quedan resueltas — `SyncPrefs` gana `lastSyncSuccessAt: Long` y `lastSyncError: String?`, actualizados en cada intento de `sync()` (éxito o excepción); `SettingsFragment` muestra "última sincronización correcta: hace N min" (`DateUtils.getRelativeTimeSpanString`, respeta el idioma elegido en la app) o el error, en una fila `btnSyncNow` que además sirve de botón "sincronizar ahora" no destructivo (llama a `SyncWorker.triggerImmediateSync()`), distinto del botón ya existente de sobrescritura. La UI reacciona en vivo a escrituras de `SyncPrefs` vía `SyncPrefs.changes()` (un `Flow` sobre `OnSharedPreferenceChangeListener`, mismo patrón reactivo que ya usa `observeAccountStatus()` para el hallazgo 4.11), así que también se refresca cuando el `SyncWorker` de fondo termina un ciclo, no solo cuando el usuario pulsa el botón. **Queda sin resolver la tercera sub-causa**, no cubierta por el alcance de la Fase 4: `TaskImageStorage.upload()`/`downloadToCache()` siguen devolviendo `null` en silencio sin cola de reintento ni indicador — sigue pendiente para una fase futura. |
| 4.10 | Cadenas de autenticación/cuenta/nube sin traducir (solo `es`) | Medio | i18n | S | 2 | **Resuelto (2026-08-19):** las 21 cadenas originales `auth_*`/`account_*`/`cloud_full_overwrite_*` más las 16 nuevas de esta fase (recuperar contraseña, diálogo de fusión, sesión caducada) están traducidas en los 5 locales (`values`, `values-de`, `values-en-rGB`, `values-en-rUS`, `values-fr`) — 37 claves en cada fichero, verificado por conteo. |
| 4.11 | Expiración de sesión silenciosa — sync se detiene sin avisar | Alto | Bug de comportamiento/UX | M | 2 | **Resuelto (2026-08-19):** `PolarApplication` observa `supabaseClient.auth.sessionStatus` con un `CoroutineScope` de aplicación (vive todo el proceso, no solo mientras `AuthActivity` está abierta) y arma/desarma la cadena de 3 minutos de `SyncWorker` reactivamente ante cualquier transición Authenticated ⇄ NotAuthenticated/RefreshFailure, sustituyendo el chequeo manual de un único booleano en `onCreate()`. `AuthViewModel.signedIn` pasa a derivarse de ese mismo `sessionStatus` (en vez de un `MutableStateFlow` escrito a mano), y muestra `auth_session_expired` en cuanto se observa un `RefreshFailure`. `SettingsFragment` también colecciona `sessionStatus` reactivamente en vez de solo releer en `onResume()`. |
| 5 | Trigger SQL no reescribe `updated_at` pese a lo prometido en el doc | Medio | Inconsistencia de diseño | M (SQL, fuera del repo) | 0 | **Resuelto (Opción B, 2026-08-19):** no se toca el trigger real ni `SyncManager` — se corrigió la prosa de `03-esquema-supabase.md` (principio nº4 y cabecera del trigger) para documentar explícitamente que el LWW depende del reloj del dispositivo. Detalle en [`09-fase0-resultado.md`](agent-docs/supabase-sync/09-fase0-resultado.md). |
| — | Bucket `task-images` no existe en el proyecto real (hallazgo nuevo de la Fase 0.1, no estaba en la auditoría original) | Alto | Config. infraestructura | S (dashboard) | 0 | **Confirmado, pendiente de crear.** Ver `09-fase0-resultado.md`. Bloquea la subida/descarga de imágenes de tareas en producción — agrava el hallazgo 4.9. |
| 6 | Sin tests de `SyncManager`/`SyncWorker`/migraciones | Alto | Calidad/riesgo | L | 7 | **Resuelto (2026-08-19):** ver detalle de la Fase 7 más abajo. `SyncManagerTest` (6 tests), `AuthViewModelTest` (10 tests) y un test de migración instrumentado, todos verdes (`./gradlew :app:testDebugUnitTest` → 76 tests, 0 fallos; `./gradlew :app:connectedDebugAndroidTest` → 2 tests, 0 fallos, contra el emulador). `TaskRepository.replaceSubtasksForTask` (7.4) ya estaba cubierto desde la Fase 1 — se añadió un test más para el caso mixto insert+update+delete en la misma llamada. |

## 9. Recomendaciones priorizadas (resumen ejecutivo del roadmap)

Vista rápida de los siete puntos de mayor impacto; el desglose completo, con archivos, pasos concretos y
criterios de aceptación, está en la sección 10.

1. **Disparar sincronización al abrir/reanudar la app** (hallazgo 2): es el fix de mayor impacto percibido
   por el usuario y el más barato de implementar — resuelve directamente el síntoma reportado ("la app no
   lee los datos de Supabase cada vez que se abre").
2. Corregir 3.1/3.2 antes que cualquier otra cosa de integridad de datos: son los únicos hallazgos que
   **pierden o resucitan datos del usuario** de forma silenciosa.
3. Implementar sign-up + "olvidé mi contraseña" + traducción de las pantallas de auth (1.1, 4.10) — sin
   esto la función es inutilizable o poco profesional para una parte significativa de usuarios.
4. Observar `sessionStatus` reactivamente en vez de un booleano manual (4.11), para que una sesión caducada
   se detecte y se comunique en vez de fallar en silencio.
5. Añadir el diálogo de primer login fusionar/descartar (4.2) antes de que más usuarios reales lo sufran
   con duplicados.
6. Decidir el futuro de Realtime (4.1) — implementarlo de verdad o retirar la dependencia muerta — y
   añadir un indicador de estado de sync en Ajustes (4.9).
7. Añadir tests de integración de `SyncManager`/`SyncWorker`/migraciones (hallazgo 6) y verificar contra el
   dashboard real de Supabase que el SQL de `03-esquema-supabase.md`, en particular el trigger (sección 5),
   es exactamente el desplegado.

---

## 10. Roadmap detallado de implementación

El roadmap está organizado en fases con dependencias explícitas: cada fase asume que las anteriores están
cerradas. Dentro de cada fase, los puntos son independientes entre sí salvo que se indique lo contrario, y
pueden repartirse/paralelizarse. Cada punto incluye **qué hacer**, **archivos afectados** y **criterio de
aceptación** (cómo saber que está realmente resuelto, no solo "compila").

### Fase 0 — Verificación de infraestructura (bloqueante, hacer primero y en paralelo al resto)

No requiere tocar código Android; es trabajo de configuración/verificación contra el proyecto Supabase
real, y condiciona si algunas fases posteriores tienen sentido tal y como están planteadas.

**Estado (2026-08-19):** ver [`09-fase0-resultado.md`](supabase-sync/09-fase0-resultado.md) para el
registro completo. Resumen: 0.2 resuelto (Opción B). 0.1 parcialmente verificado por API (tablas OK,
bucket `task-images` falta); el resto (RLS exacto, trigger desplegado, `pg_cron`, Realtime) está pendiente
de que el usuario ejecute [`09-fase0-auditoria-sql.sql`](supabase-sync/09-fase0-auditoria-sql.sql) en el
SQL Editor del dashboard, porque este agente solo dispone de la anon key (por diseño, ver doc 08) y no
puede consultar catálogos del sistema por REST.

0.1. **Auditar el proyecto Supabase real contra `agent-docs/supabase-sync/03-esquema-supabase.md`.**
   Checklist mínimo a comprobar en el dashboard: las 4 tablas con sus columnas exactas; las 4×4 políticas
   RLS (`select_own`/`insert_own`/`update_own`/`delete_own` en `task_lists`, `tasks`, `subtasks`,
   `reminders`); el trigger `touch_and_resolve_lww` instalado en las 4 tablas; el bucket `task-images` con
   sus 3 policies; si la réplica lógica de Realtime está habilitada; si `pg_cron` tiene programados
   `purge_old_tombstones` y `roll_recurring_tasks`. Documentar el resultado (qué existe, qué falta, qué
   diverge) — no se puede confirmar nada de esto desde el repo del cliente Android.
   *Criterio de aceptación:* existe un registro (en este documento o en uno nuevo del mismo directorio) de
   qué se comprobó y con qué resultado, con fecha.

0.2. **Resolver la inconsistencia del trigger (hallazgo 5).** Decisión de producto, no solo técnica: o el
   trigger empieza a sobreescribir `NEW.updated_at := extract(epoch from now())*1000` además de
   `server_updated_at` (y entonces `SyncManager.resolvePushOutcome` deja de poder asumir "si gano, el
   `updated_at` que me devuelven es el que mandé" — hay que releer y aplicar el devuelto siempre, gane o
   pierda), o se acepta y se documenta explícitamente en el doc 03/04 que el LWW depende del reloj del
   dispositivo. **Esta decisión bloquea cómo se implementa el punto 3.1** (si `pushTaskLists`/`pushTasks`/...
   dejan de poder confiar en "devuelto == enviado ⇒ gané").
   *Criterio de aceptación:* el SQL del trigger en el proyecto real y el documentado en `03-esquema-supabase.md`
   coinciden byte a byte, y `SyncManager` está actualizado si el comportamiento cambió.

### Fase 1 — Fallos críticos de comportamiento y de datos

**Estado (2026-08-19):** completa. Los tres puntos (1.1, 1.2, 1.3) están implementados y verificados con
`./gradlew :app:compileDebugKotlin` y `./gradlew :app:testDebugUnitTest` (verde, incluye tests nuevos para
las rutas tocadas). Detalle de cada hallazgo resuelto en la tabla de la sección 8.

Estos son los que un usuario real nota o sufre directamente. Deben ir en el primer release que toque esta
área, y en este orden relativo (1.1 es independiente y trivial; 1.2/1.3 conviene resolverlos juntos porque
tocan las mismas rutas de trash).

1.1. **Sincronizar al abrir/reanudar la app (hallazgo 2).**
   - Añadir la dependencia `androidx.lifecycle:lifecycle-process` si no queda ya cubierta transitivamente
     (comprobar con `./gradlew :app:dependencies | grep lifecycle-process` antes de asumir que hace falta).
   - En `PolarApplication`, registrar un `DefaultLifecycleObserver` sobre
     `ProcessLifecycleOwner.get().lifecycle` cuyo `onStart()` compruebe sesión activa
     (`supabaseClient.auth.currentUserOrNull() != null`) y, si la hay, llame a
     `SyncWorker.triggerImmediateSync(context)`.
   - Proteger contra ráfagas: si el usuario entra/sale de la app repetidamente en segundos, no se deben
     encolar sincronizaciones redundantes sin sentido — `ExistingWorkPolicy.REPLACE` que ya usa
     `triggerImmediateSync` ya resuelve esto razonablemente bien (la última reemplaza a la anterior si aún
     no ha corrido), pero conviene verificarlo con una prueba manual explícita (abrir/cerrar la app rápido
     varias veces y comprobar en `adb shell dumpsys jobscheduler` o logs que no se dispara sync en bucle).
   - Archivos: `PolarApplication.kt`, `app/build.gradle.kts` (si hace falta la dependencia nueva).
   *Criterio de aceptación:* con sesión iniciada, cerrar la app completamente (recientes) y volver a
   abrirla dispara un pull visible en logs/red en los primeros segundos, sin esperar a los temporizadores
   de 3/15 minutos. Con sesión cerrada, abrir la app no intenta llamar a Supabase en absoluto.

1.2. **Vaciar papelera / borrado definitivo seguro (hallazgos 3.1, 3.2).**
   - Cambiar `TaskDao.emptyTrash()`/`permanentDelete()` y `ReminderDao.emptyTrash()`/`permanentDelete()`
     para que solo borren físicamente filas con `dirty = 0` (`WHERE isDeleted = 1 AND dirty = 0` /
     `WHERE id = :id AND dirty = 0`).
   - En el `ViewModel` (`TaskViewModel`/`RemindersViewModel`), antes de purgar, forzar un intento de
     sincronización síncrona (inyectar `SyncManager` y llamar a `sync()`/una variante de solo-push,
     `await`ada) para maximizar las filas que ya tengan `dirty = false` en el momento de purgar. Si tras
     el intento de sync siguen quedando filas `dirty = 1` en la papelera (por ejemplo, sin conexión),
     **no purgarlas** y avisar al usuario (reutilizar el canal `errorMessage`/Snackbar que `TrashFragment`
     ya observa) de que algunos elementos no se pudieron eliminar definitivamente por falta de conexión.
   - Hacer que `TaskRepository.softDeleteTask()` cascade a `touchedDeleted()` sobre todas las subtareas de
     la tarea, igual que `deleteTaskList()` ya cascada a tareas y subtareas.
   - Archivos: `TaskDao.kt`, `ReminderDao.kt`, `TaskRepository.kt`, `ReminderRepository.kt`,
     `TaskViewModel.kt`, `RemindersViewModel.kt`, `TrashFragment.kt` (si hace falta cablear el nuevo aviso).
   *Criterio de aceptación:* reproducir la secuencia descrita en 3.1 (borrar → vaciar papelera antes de que
   sincronice, estando offline) ya **no** hace que la tarea/recordatorio reaparezca tras reconectar y
   sincronizar; en su lugar, sigue en la papelera hasta que se confirme el tombstone en servidor.

1.3. **Retirar las rutas de hard-delete muertas (hallazgo 3.3).**
   - Eliminar `TaskRepository.deleteTask()`/`TaskDao.delete()`, `TaskViewModel.deleteTask()`,
     `TaskRepository.deleteSubtask()`/`SubtaskDao.delete()`, `ReminderRepository.delete()`/`ReminderDao.delete()`
     si el análisis de uso (ya hecho en este documento, sección 3.3) sigue confirmando que están muertas.
   - Antes de borrar, repetir el grep de la sección 3.3 por si algo cambió desde esta auditoría.
   *Criterio de aceptación:* `grep -rn "\.delete(" app/src/main/java/app/polar/data` ya no devuelve
   ninguna llamada de borrado físico sobre `Task`/`Subtask`/`Reminder` fuera de las migraciones de Room.

### Fase 2 — Autenticación completa (bloqueante para que el producto sea usable)

**Estado (2026-08-19):** 2.2/2.3/2.4 completos y verificados con `./gradlew :app:compileDebugKotlin` y
`./gradlew :app:testDebugUnitTest` (verde). **2.1 (sign-up) queda deliberadamente sin implementar**, por
decisión explícita del usuario: Polar es un proyecto personal y no debe ser posible crear cuentas nuevas
desde la app — esa opción debe permanecer oculta. Esto se aparta de lo que este documento recomendaba
originalmente (1.1/2.1 como bloqueante crítico); se documenta aquí como corrección deliberada del contrato,
no como una tarea pendiente. Las cuentas se siguen creando solo desde la "otra app" (doc 07), tal y como ya
ocurría antes de esta fase.

2.1. **Sign-up — descartado, no implementar (decisión de producto, ver nota de estado arriba).** La app
   sigue exponiendo solo `signIn()`/`signOut()`; no se añade `signUp()` ni ningún control de "crear cuenta"
   en `AuthActivity`/`activity_auth.xml`. Si en el futuro esta decisión cambiara, la implementación original
   sigue siendo válida como referencia: `AuthViewModel.signUp(email, password)`
   (`supabaseClient.auth.signUpWith(Email)`), un segundo modo de formulario (toggle o pestañas "iniciar
   sesión" / "crear cuenta"), y manejar el caso en que Supabase exija confirmación de email mostrando un
   mensaje claro en vez de tratarlo como error.

2.2. **Recuperar contraseña.** Añadir `AuthViewModel.resetPassword(email)`
   (`supabaseClient.auth.resetPasswordForEmail(email)`) y un enlace "¿olvidaste tu contraseña?" en el
   formulario de login que abra un diálogo/pantalla mínima de solo email.

2.3. **Observación reactiva de sesión (hallazgo 4.11).** Sustituir el booleano manual `_signedIn` por una
   colección de `supabaseClient.auth.sessionStatus` (`stateIn`/`collect` en `viewModelScope`), de forma que
   cualquier expiración/revocación/refresco se refleje solo sin intervención manual en `signIn()`/`signOut()`.
   Revisar también `SettingsFragment` para que pueda reaccionar igual si conviene (hoy se autocorrige en
   `onResume()`, pero sería más robusto observar el flujo en vez de solo releer en el ciclo de vida).

2.4. **Traducir las 21 cadenas de auth/cuenta/nube (hallazgo 4.10) a los 4 locales que faltan** (`values-de`,
   `values-en-rGB`, `values-en-rUS`, `values-fr`), más las cadenas nuevas que salgan de 2.2/2.3 (recuperar
   contraseña, sesión caducada).

   *Criterio de aceptación conjunto de la fase (ajustado: sin sign-up, ver nota de estado):* un usuario con
   una cuenta ya creada (desde la "otra app") puede iniciar sesión, recuperar su contraseña si la olvida, y
   ver todo el flujo en su idioma elegido — todo sin salir de Polar. Una sesión revocada manualmente desde
   el dashboard de Supabase se refleja en la UI de Polar (pasa a "no has iniciado sesión") sin que el
   usuario tenga que reabrir la app.

### Fase 3 — Fidelidad al diseño de sincronización cruzada (doc 04)

**Estado (2026-08-19):** completa (3.1, 3.2, 3.3, 3.4) y verificada con `./gradlew :app:compileDebugKotlin`
y `./gradlew :app:testDebugUnitTest` (verde). 3.4 se implementó ya, sin esperar a la verificación de Fase
0.1 (decisión explícita del usuario de adelantarla); si Fase 0.1 confirma más adelante que `pg_cron` no
purga nada en producción, el código sigue siendo correcto (simplemente no encontrará nunca tombstones
huérfanos que purgar).

3.1. **Diálogo de primer login fusionar/descartar (hallazgo 4.2).** Tras un `signIn()` (sin `signUp()`, ver
   éxito, si `SyncPrefs.lastSyncAt == 0L` (nunca se ha sincronizado desde este dispositivo) y hay datos
   locales no vacíos, mostrar un diálogo con dos opciones antes de disparar el primer sync:
   - "Subir mis datos locales" → flujo actual (push, luego pull).
   - "Descartar datos locales y usar solo la nube" → nuevo método en `SyncManager` (p. ej.
     `discardLocalAndPullFromCloud()`) que vacíe las 4 tablas locales (`deleteAll()`, ya existente en los
     4 DAOs) y luego haga un `pull()` completo (con `lastSyncAt = 0`, trae todo lo que haya en la nube).
   *Criterio de aceptación:* iniciar sesión en un dispositivo con datos locales de prueba y una cuenta que
   ya tiene datos en la nube presenta el diálogo antes de sincronizar nada; elegir cada opción produce el
   resultado esperado sin duplicados.

3.2. **Snooze sincronizado (hallazgo 4.4).** En `NotificationActionReceiver.ACTION_SNOOZE`, además de
   reprogramar la alarma local, cargar la tarea, aplicar `task.copy(dueDate = snoozeTime).touched()` y
   persistirla, igual que ya hace `ACTION_COMPLETE`.
   *Criterio de aceptación:* posponer una tarea desde la notificación en el dispositivo A actualiza su
   fecha/hora visible en el dispositivo B tras el siguiente sync.

3.3. **Cursor de pull sin pérdida en colisión de milisegundo (hallazgo 4.7).** Cambiar
   `syncPrefs.lastSyncAt = pullStartedAt` por `pullStartedAt - 1` (o cambiar el filtro de `gt` a `gte` con
   deduplicación por `uuid`, que es más robusto pero más trabajo). Añadir un test unitario a
   `MergeResolverTest`/uno nuevo que fije explícitamente este caso límite.

3.4. **Tombstones purgados (hallazgo 4.5), prioridad baja/opcional.** Solo abordar si Fase 0.1 confirma que
   `pg_cron` está realmente purgando filas en producción; si no, no hay nada que reproducir todavía y se
   puede posponer indefinidamente sin riesgo.

### Fase 4 — Observabilidad y UX de sincronización

**Estado (2026-08-19):** completa (4.1, 4.2, 4.3) y verificada con `./gradlew :app:compileDebugKotlin`
y `./gradlew :app:testDebugUnitTest` (56 tests, verde, incluye 8 tests nuevos en `SyncPrefsTest`) y
`./gradlew :app:lintDebug` (sin errores nuevos en los ficheros tocados; los 45 errores preexistentes del
proyecto — `NewApi` en `values-night/themes.xml` — son ajenos a esta fase). El sub-punto de imágenes del
hallazgo 4.9 (`TaskImageStorage` silencioso) queda fuera de alcance de esta fase, ver nota en la tabla de
la sección 8.

4.1. **Indicador de estado de sync + "sincronizar ahora" no destructivo (hallazgo 4.9).** Añadir a
   `SyncPrefs` (o a un nuevo `SyncStatusPrefs`) `lastSyncSuccessAt: Long` y `lastSyncError: String?`,
   actualizados desde `SyncManager.sync()` en cada intento. En `SettingsFragment`, mostrar "última
   sincronización: hace N minutos" (o el error, si el último intento falló) junto a un botón "sincronizar
   ahora" que llame a `triggerImmediateSync()` — distinto y menos alarmante que el botón ya existente de
   "subir copia completa" (que sigue siendo la opción destructiva para casos extremos).

4.2. **Aviso de conflictos perdidos (hallazgo 4.3).** Acumular en `SyncManager.push*()` cuántas filas
   perdieron el LWW en cada ciclo (`PushOutcome.LOST`) y exponerlo (por ejemplo, un `SharedFlow`/evento que
   la UI activa pueda coleccionar, o como mínimo un contador en `SyncPrefs` que `SettingsFragment` pueda
   mostrar la próxima vez que se abra: "3 cambios se sobrescribieron por ediciones más recientes en otro
   dispositivo").

4.3. **Botón simétrico "descargar todo y sobrescribir local" (hallazgo 4.6).** Añadir junto al ya existente
   "subir copia completa a la nube" un botón inverso que llame a un nuevo `SyncManager.pullAllOverwrite()`
   (vaciar local + pull completo, reutilizable con el método de 3.1), con su propio diálogo de confirmación
   destructiva análogo al ya existente.

### Fase 5 — Rendimiento

**Estado (2026-08-19):** completa (5.1) y verificada con `./gradlew :app:compileDebugKotlin` y
`./gradlew :app:testDebugUnitTest` (verde).

5.1. **Batching de push (hallazgo 4.8).** Sustituir el `forEach` con un `upsert` por fila en
   `pushTaskLists`/`pushTasks`/`pushSubtasks`/`pushReminders` por un único `upsert(list) { select() }` por
   tabla, y resolver el `WON`/`LOST` de cada fila emparejando la respuesta por `id` (no por posición/orden,
   que no está garantizado). Mantener el orden de dependencia entre tablas (listas → tareas → subtareas)
   tal y como está hoy, solo se agrupa dentro de cada tabla.
   *Criterio de aceptación:* reordenar una lista de 50 tareas genera 1 petición HTTP de push por tabla
   afectada, no 50.
   **Resuelto:** las cuatro funciones construyen la lista de DTOs `dirty` y hacen un único `upsert(list)`
   por tabla; los ganadores/perdedores se resuelven emparejando por `uuid` vía un `Map<String, Dto>`
   (`associateBy { it.id }`), no por índice de lista. Las actualizaciones locales resultantes (`dirty =
   false` para los que ganaron, sobrescritura con la versión del servidor para los que perdieron) también
   se aplican en bloque con `updateAll()` en vez de fila a fila; se añadió `updateAll()` a `SubtaskDao` y
   `ReminderDao` (ya existía en `TaskListDao`/`TaskDao`) para poder hacerlo. Detalle completo en la fila
   4.8 de la tabla de la sección 8.

### Fase 6 — Decisión sobre Realtime (hallazgo 4.1)

**Estado (2026-08-19):** completa. El usuario eligió explícitamente la **Opción B** (implementar de
verdad) cuando se le preguntó antes de codificar, revirtiendo la recomendación por defecto de este
documento (Opción A). Implementado y verificado con `./gradlew :app:compileDebugKotlin` y
`./gradlew :app:testDebugUnitTest` (59 tests, verde). Detalle completo en la fila 4.1 de la tabla de
la sección 8.

Requiere una decisión de producto explícita antes de codificar nada, porque las dos opciones son válidas:

- **Opción A (retirar):** eliminar `install(Realtime)` de `SupabaseModule` y la dependencia `realtime-kt`
  de `app/build.gradle.kts`. Esfuerzo mínimo, elimina la inconsistencia entre lo que se instala y lo que se
  usa. Con la Fase 1.1 ya hecha (sync al abrir), el impacto en UX percibida es bajo salvo para el caso de
  dos dispositivos usados simultáneamente por la misma persona en la misma sesión.
- **Opción B (implementar de verdad):** suscribirse a `postgres_changes` en las 4 tablas filtradas por
  `user_id = eq.<uid>` mientras la app está en foreground (ligado al mismo `ProcessLifecycleOwner` de la
  Fase 1.1: suscribir en `onStart`, cancelar en `onStop`), aplicando cada evento entrante con la misma
  lógica de `resolvePullAction` que ya usa `pull()`. Esfuerzo alto: gestión de ciclo de vida del canal,
  reconexión tras pérdida de red, y sobre todo pruebas — es la pieza con más superficie para introducir
  bugs nuevos de todo este roadmap.

*Recomendación de este documento:* empezar por la Opción A ya en la Fase 1/6 (es casi gratis y resuelve la
inconsistencia), y reevaluar la Opción B como trabajo aparte solo si el uso multi-dispositivo simultáneo
demuestra ser un caso de uso real y frecuente — no implementarla especulativamente.

### Fase 7 — Calidad: tests (hallazgo 6)

**Estado (2026-08-19): completa.** Los cuatro puntos (7.1–7.4) están implementados y verificados:
`./gradlew :app:testDebugUnitTest` → 76 tests, 0 fallos (incluye los 16 nuevos de esta fase más el test
extra de 7.4); `./gradlew :app:connectedDebugAndroidTest` → 2 tests, 0 fallos, ejecutado contra un
emulador real (no solo compilado); `./gradlew :app:compileDebugKotlin` y `./gradlew :app:lintDebug` no
introducen ningún error/warning nuevo (los 45 errores `NewApi` de `values-night/themes.xml` son
preexistentes y ajenos a esta fase, ver nota de la Fase 4).

7.1. **Tests de integración de `SyncManager`** (`app/src/test/java/app/polar/data/sync/SyncManagerTest.kt`,
   6 tests): push exitoso (WON), push que pierde el conflicto (LOST, incluye el contador
   `lostConflictsCount`), pull con inserción, pull con actualización, pull con padre no encontrado
   (tarea cuya lista aún no llegó localmente), y `pushAllOverwrite()` completo (fuerza el touch de todas
   las filas locales, las sube, y tumba un huérfano remoto). En vez de mockear `SupabaseClient`/`Postgrest`
   directamente (son en gran parte funciones de extensión inline/reified que mockk no puede interceptar de
   forma fiable), los tests construyen un `SupabaseClient` **real** — el mismo `createSupabaseClient(...)`
   que usa producción — con `httpEngine` apuntando a un `MockEngine` de Ktor
   (`app/src/test/java/app/polar/util/FakeSupabase.kt`), de forma que la lógica real de Postgrest/Auth
   (query params, headers `Prefer`, cuerpos de `upsert`) se ejecuta de verdad contra respuestas HTTP
   enlatadas en vez de contra la red. `Auth` se instala con `minimalSettings()` (pensado río arriba
   exactamente para "aplicaciones sin necesidad de persistir sesión") para no tocar
   `SharedPreferences`/Android en un test JVM puro, y la sesión se inyecta con
   `auth.importSession(session, autoRefresh = false)` en vez de un login real. El `MockEngine` se configura
   con `dispatcher = Dispatchers.Unconfined` — si no, la petición HTTP (incluso contra un motor en memoria)
   se resuelve en un hilo de fondo real fuera del scheduler virtual de `runTest`, y como los métodos
   públicos de `SyncManager`/`AuthViewModel` no son todos `suspend` (hay `viewModelScope.launch{}` de por
   medio en el ViewModel), no hay forma de esperar esa resolución de forma determinista sin forzar el
   engine a resolver en el mismo hilo que la llama.

7.2. **Test de `MIGRATION_14_15`** (`app/src/androidTest/java/app/polar/data/MigrationTest.kt`, instrumentado,
   ejecutado contra un emulador real vía `connectedDebugAndroidTest`) usando `MigrationTestHelper` de Room.
   `exportSchema` pasó de `false` a `true` en `AppDatabase` y se añadió `room.schemaLocation` en
   `app/build.gradle.kts`, de forma que a partir de ahora cada versión futura del esquema se exporta
   automáticamente a `app/schemas/` — pero la versión 14 nunca se exportó en su momento (exportSchema
   llevaba siendo `false` desde siempre), así que `app/schemas/app.polar.data.AppDatabase/14.json` es
   **hand-authored**: reconstruido columna a columna a partir del historial de migraciones
   (`MIGRATION_6_7`..`MIGRATION_13_14`) y contrastado contra el `17.json` real que el compilador de Room sí
   generó en esta fase (mismo formato, mismas convenciones). El test pre-puebla una base v14 con filas con
   valores límite (dos `task_lists` con el mismo `createdAt` exacto, para probar que el `uuid` backfillado
   nunca colisiona pese a compartir milisegundo; una `task` con `createdAt = 0`; subtareas de dos tareas
   distintas insertadas fuera de orden de `id`, para probar que `backfillSubtaskOrderIndex()` particiona por
   `taskId` y no asigna una secuencia global), corre la cadena completa 14→17 (no solo 14→15:
   `runMigrationsAndValidate()` siempre valida contra el esquema *compilado actual*, así que parar en un
   punto intermedio haría fallar la validación por columnas que 15_16/16_17 aún no habrían añadido) y
   verifica con queries SQL crudas: unicidad y no-blancura de cada `uuid`, `updatedAt` correctamente
   backfillado desde `createdAt` (incluido el caso límite 0), `orderIndex` de subtareas secuencial por
   tarea padre, `createdAt` de subtareas heredado de `updatedAt` (15_16), `dirty = 1` en toda fila
   preexistente, y que el índice único `index_task_lists_uuid` realmente rechaza un `uuid` duplicado (no
   solo que las filas de prueba resultaron únicas por casualidad). No hace falta ninguna comprobación
   explícita de "no cae en `fallbackToDestructiveMigration()`": ese camino solo existe en el
   `Room.databaseBuilder(...)` real de `AppDatabase.getDatabase()`, nunca en el flujo de
   `MigrationTestHelper`, así que un test que pasa ya prueba que la migración explícita es suficiente y
   correcta.

7.3. **Tests de `AuthViewModel`** (`app/src/test/java/app/polar/ui/viewmodel/AuthViewModelTest.kt`, 10
   tests): sign-in (validación de campos vacíos, éxito con/sin fusión de datos locales según
   `hasLocalData()`/`lastSyncAt`, ver hallazgo 4.2), `confirmDiscardLocalUseCloud()`, sign-out, reset
   password (validación y envío), y el comportamiento reactivo de `sessionStatus` de la Fase 2.3/hallazgo
   4.11 (el `StateFlow signedIn` reacciona a un cambio de sesión externo, no solo a `signIn()`/`signOut()`;
   un `RefreshFailure` observado fuera de esos dos métodos hace aparecer `auth_session_expired`). **No hay
   test de sign-up**: la Fase 2 decidió explícitamente no implementarlo (ver nota de esa fase) — no existe
   `AuthViewModel.signUp()` que probar. Los tests de sign-in/sign-out/reset-password usan el mismo
   `SupabaseClient` real + `MockEngine` de 7.1; los de reactividad de `sessionStatus` usan en cambio un
   `SupabaseClient`/`Auth` mockeados con mockk (`mockkStatic` sobre la propiedad de extensión `auth`) que
   exponen un `MutableStateFlow<SessionStatus>` controlado directamente por el test — llevar a
   `RefreshFailure` a través del mecanismo real de reintento de Auth habría implicado esperas reales de
   varios segundos fuera del scheduler virtual del test (el `retryDelay` de Auth corre en su propio scope
   interno, no en `viewModelScope`), así que se optó por la vía determinista.

7.4. **Test de `TaskRepository.replaceSubtasksForTask`**: ya estaba cubierto desde la Fase 1
   (`TaskRepositoryTest.kt`, 6 tests: insertar nueva, actualizar solo si cambió un campo, no tocar una
   subtarea sin cambios, soft-delete de la que desaparece de la lista entrante, marcar dirty solo por
   reorder). Se añadió un test más en esta fase para el caso realista que ninguno de los anteriores cubría
   de forma aislada: una sola llamada que mezcla insert + update + fila sin tocar + delete a la vez (la
   forma en que la UI realmente guarda una edición de lista de subtareas).

*Criterio de aceptación de la fase:* `./gradlew testDebugUnitTest` cubre, con asserts explícitos, cada
escenario de conflicto (WON/LOST) y cada rama de `resolvePullAction` ya no solo a nivel de función pura
sino de principio a fin a través de `SyncManager`. **Cumplido**: ver 7.1 arriba.

---

## 11. Instrucciones para el agente que implemente este roadmap

Esta sección es una guía operativa para cualquier agente (Claude Code u otro) al que se le encargue
ejecutar el roadmap de la sección 10. El objetivo es que el resultado sea el **desarrollo más completo y
correcto posible**, no el parche mínimo que hace desaparecer el síntoma.

1. **Orden de lectura antes de tocar código:** este documento completo, luego
   `agent-docs/supabase-sync/00-README.md` a `08-configuracion-y-credenciales.md` (el contrato de diseño
   original — este documento es la lista de *desviaciones* respecto a él, no lo sustituye). Si un hallazgo
   de aquí y el contrato de esos documentos entran en conflicto, gana el contrato salvo que este documento
   explique por qué se debe corregir el contrato en su lugar (como en el hallazgo 5).

2. **Antes de corregir cualquier hallazgo, reconfirmarlo leyendo el código actual.** Esta auditoría es una
   foto fija de un momento dado; el código puede haber cambiado desde entonces (incluso por otro agente
   trabajando en paralelo). No apliques un fix a ciegas basándote solo en la descripción — relee el archivo
   citado y verifica que el problema sigue ahí tal cual se describe.

3. **Sigue el orden de fases del roadmap (sección 10), no saltes a fases posteriores sin cerrar las
   anteriores.** La Fase 1 (bugs de datos y comportamiento) es la que más daño real hace a usuarios reales
   y no depende de decisiones de producto pendientes; la Fase 6 (Realtime) sí depende de una decisión que
   puede no estar tomada todavía — no la abordes sin confirmarla explícitamente primero.

4. **Busca siempre la mejor solución disponible, no el parche mínimo.** Cuando un hallazgo admita varias
   soluciones válidas (el ejemplo más claro es 4.1/Fase 6, Realtime), no elijas la más rápida sin más:
   sopesa el trade-off explícitamente, documenta la decisión tomada y por qué (actualizando este mismo
   fichero si cambia algo respecto a lo aquí recomendado), y luego impleméntala de forma completa — sin
   dejar mitad implementado un mecanismo (por ejemplo, no dejes `install(Realtime)` si decides no usarlo:
   retíralo del todo, dependencia incluida).

5. **Respeta los patrones ya existentes en el proyecto en vez de introducir arquitectura nueva:**
   - Workers con acceso a Hilt vía `EntryPointAccessors` manual (`SyncEntryPoint`, `AlarmHelperEntryPoint`),
     no `@HiltWorker` — así ya está todo el proyecto, no mezclar estilos.
   - `touched()`/`touchedDeleted()`/`touchedRestored()` como funciones de extensión en `data/sync/EntityTouch.kt`
     para cualquier mutación local nueva que deba marcar `dirty`/`updatedAt` — nunca lo hagas a mano en un
     `copy()` suelto.
   - `safeLaunch { ... }` en los ViewModels como envoltorio estándar de corrutinas + manejo de error +
     disparo de `triggerImmediateSync()`.
   - `MaterialAlertDialogBuilder` + `Snackbar` para confirmaciones/errores en fragments, como ya hace
     `TrashFragment`/`SettingsFragment`.
   - `SharedPreferences` para estado local ligero (`SyncPrefs`, `sort_mode`, `app_prefs`), no introduzcas
     `DataStore` u otra dependencia nueva solo por preferencia personal si lo existente ya resuelve el caso.

6. **Nunca hardcodees texto de UI.** Cualquier cadena nueva va a `values/strings.xml` **y** se traduce a
   los 4 locales existentes (`values-de`, `values-en-rGB`, `values-en-rUS`, `values-fr`) en el mismo commit
   — no lo dejes para "después", es exactamente el hallazgo 4.10 que este roadmap corrige.

7. **Cambios de esquema de Room van siempre en una migración nueva y numerada** (`MIGRATION_17_18`, etc.),
   nunca modificando una migración ya publicada (`MIGRATION_14_15` etc. son historial, no se tocan salvo
   que se confirme que ninguna build real las ha ejecutado todavía). Sube `version` en `@Database` y añade
   la migración a `addMigrations(...)` en el mismo cambio.

8. **No alteres el contrato de interoperabilidad (doc 05) unilateralmente.** Cualquier cambio de payload/DTO
   (nombres de campo, tipos, nuevos campos) debe seguir siendo compatible con lo que la "otra app" (doc 07)
   espera. Si un fix de este roadmap necesitara cambiar el wire format, ese cambio debe reflejarse primero
   en `agent-docs/supabase-sync/05-contrato-interoperabilidad.md` y tratarse como una decisión de mayor
   calado, no como un detalle de implementación de Polar.

9. **Verifica compilación y tests después de cada fase, no solo al final de todo el roadmap:**
   `./gradlew compileDebugKotlin` (o el módulo relevante) y `./gradlew testDebugUnitTest`. Añade tests
   unitarios junto con cada fix de lógica pura (siguiendo el estilo ya existente de `MergeResolverTest`/
   `EntityMappersTest`: nombres de test descriptivos en backticks, un assert por test) en el mismo cambio
   que el fix, no como tarea aparte para "luego".

10. **Cualquier acción irreversible o que afecte al proyecto Supabase real requiere confirmación explícita
    del usuario antes de ejecutarla** — esto incluye cambiar el trigger o las políticas RLS en el dashboard
    (Fase 0.2), y por supuesto cualquier operación que borre filas de producción. Este roadmap describe qué
    *debería* cambiar; no asumas permiso para ejecutarlo contra el proyecto real sin que el usuario lo
    confirme, igual que para cualquier acción de alto impacto fuera de este repositorio.

11. **Para los puntos marcados como "no verificable desde el código" (sección 7) y la Fase 0:** no asumas
    ni inventes el estado del dashboard de Supabase. Si no tienes acceso para comprobarlo, dilo
    explícitamente en vez de dar por hecho que el DDL documentado está desplegado tal cual — varios
    hallazgos de este documento (en particular el 5) dependen de verificarlo primero.

12. **Al cerrar cada hallazgo, vuelve a la tabla resumen (sección 8) de este mismo fichero y anota el
    resultado** (resuelto / resuelto parcialmente y por qué / descartado y por qué), en vez de borrar el
    hallazgo. Este documento debe funcionar como registro vivo de la auditoría y su resolución, no como una
    lista de tareas que se descarta en cuanto se completa — así cualquier persona o agente futuro puede ver
    qué se decidió y por qué sin tener que repetir esta misma investigación desde cero.

### Definición de "hecho" (Definition of Done) para todo el roadmap

El roadmap se considera completo cuando, para cada fila de la tabla de la sección 8:
- El código correspondiente ya no reproduce el escenario descrito en su hallazgo (reconfirmado manualmente,
  no solo "el test pasa").
- Existe al menos un test automatizado que habría detectado el problema original, salvo que el hallazgo sea
  puramente de configuración de infraestructura (Fase 0), en cuyo caso el criterio es la verificación
  manual documentada.
- Las cadenas de UI nuevas están traducidas a los 5 locales.
- `./gradlew testDebugUnitTest` y una compilación completa pasan sin warnings nuevos introducidos por estos
  cambios.
- Este documento refleja el estado final real (sección 8 actualizada por hallazgo, según el punto 12 de
  arriba).
