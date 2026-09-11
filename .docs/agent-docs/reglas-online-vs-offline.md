# Reglas obligatorias: `polar-online` vs `polar-offline`

> Léase antes de tocar cualquier fichero dentro de `polar-online/` o de implementar una
> funcionalidad que hoy solo exista ahí. Este documento existe porque `AGENTS.md`/`CLAUDE.md`/
> `README.md` describen una única estructura de proyecto (`app/src/main/java/app/polar/...`) que
> **ya no es literal**: el repositorio contiene dos proyectos Gradle independientes,
> `polar-online/` y `polar-offline/`, y esa documentación de alto nivel todavía no se ha
> actualizado para reflejarlo. Mientras eso no ocurra, este fichero es la fuente de verdad sobre
> cómo tratar la relación entre ambos.

## 1. Qué son estas dos carpetas

En la raíz del repositorio conviven dos proyectos Android independientes, cada uno con su propio
`settings.gradle.kts`/`build.gradle.kts`, mismo `applicationId`/`namespace` (`app.polar`) y el
mismo paquete de código (`app.polar.*`), pero **no son el mismo módulo ni comparten código en
build-time** (no hay `:shared`, no hay `includeBuild`, no hay `composite build`):

- **`polar-online/`** — versión con cuenta y sincronización en la nube (Supabase: Auth, Postgres,
  Realtime, Storage). Documentada en detalle en [`supabase-sync/`](supabase-sync/00-README.md) y
  auditada en [`implementacion-supabase.md`](implementacion-supabase.md).
- **`polar-offline/`** — versión **100% local**, sin cuenta, sin red, sin ningún SDK de Supabase.
  Es la app que se distribuye como "polar" a secas: la que describe el `README.md` de la raíz
  ("**offline first**: complete functionality without an internet connection").

Ambas parten del mismo diseño de producto y de UI. La diferencia entre ellas debe ser,
**exclusivamente**, todo lo que depende de tener una cuenta y una conexión de red. Cualquier otra
divergencia entre las dos carpetas es, por definición, un defecto: una funcionalidad que un agente
implementó en un lado y olvidó portar al otro.

## 2. La regla central (léase dos veces)

> **Todo lo que no necesite obligatoriamente una cuenta ni una conexión a internet tiene que
> existir, idéntico, en `polar-offline/`.**

Dicho de otra forma:

- Si estás implementando o modificando algo en `polar-online/` y esa funcionalidad **no depende
  de datos remotos ni de sesión de usuario** para funcionar (una pantalla nueva, una regla de
  negocio, un cambio de UI, un fix de un bug en `TaskRepository`, un nuevo campo de una entidad,
  una mejora del parser inteligente, un widget, un tema, una traducción, etc.), **tienes que
  replicarla en `polar-offline/`** en la misma sesión de trabajo, no como una tarea futura.
- Solo queda exento de portarse lo que **por definición** requiere una cuenta o internet:
  autenticación, sincronización, Realtime, Storage de imágenes en la nube, cualquier pantalla o
  ajuste que solo tenga sentido si hay una sesión Supabase activa.
- Ante la duda de si algo "depende de internet", la pregunta correcta es: *¿esto seguiría
  funcionando igual con el dispositivo en modo avión y sin haber iniciado sesión nunca?* Si la
  respuesta es sí, va en `polar-offline/`.

**`polar-offline/` no tiene cuentas que vincular.** No hay ningún usuario, ni sesión, ni email, ni
contraseña, ni token, en ningún punto de ese proyecto. No existe ningún flujo — ni activo ni
oculto tras un flag — para iniciar sesión, registrarse, recuperar contraseña o vincular una cuenta
ya creada en `polar-online`/la app externa (doc [07](supabase-sync/07-guia-app-externa.md)). No se
debe añadir nada que lo insinúe (una pantalla de login deshabilitada, un botón "próximamente",
un campo de ajustes "cuenta", etc.): si no funciona, no debe estar.

## 3. Qué implica "100% sin internet" para `polar-offline/`

- **No declara el permiso `INTERNET`** en
  [`AndroidManifest.xml`](../../polar-offline/app/src/main/AndroidManifest.xml). Si una
  dependencia nueva (una librería, un SDK) lo añade transitivamente, es una señal de alarma: revisa
  por qué se ha colado y retírala si no es imprescindible para una funcionalidad puramente local.
- **No debe compilarse contra el SDK de Supabase** (`supabase-kt`, `postgrest-kt`, `auth-kt`,
  `realtime-kt`, `storage-kt`) ni contra ningún otro cliente HTTP orientado a red (Ktor como
  cliente remoto, Retrofit, OkHttp apuntando a un host externo, etc.). Usar OkHttp/Ktor
  puramente para I/O local (si alguna vez hiciera falta) no rompe la regla; lo que la rompe es
  cualquier llamada que salga del dispositivo.
- **No debe leer ni escribir nunca** ninguna de las tablas/buckets de Supabase documentados en
  [`03-esquema-supabase.md`](supabase-sync/03-esquema-supabase.md), ni directa ni indirectamente.
- **Todo el almacenamiento es local**: Room (`AppDatabase`), `SharedPreferences`, backups a
  fichero JSON vía `BackupManager` (ver doc
  [01](supabase-sync/01-modelo-de-datos-local.md)). El backup/restore local sigue siendo la única
  vía de "portabilidad" de datos en esta versión — no hay nube que la sustituya.
- **Sin `WorkManager` orientado a red**: `polar-offline/` puede seguir usando `WorkManager` para
  trabajo puramente local (`RecurrenceWorker`), pero no debe existir ningún `SyncWorker` ni
  equivalente. Si `polar-online/` añade un nuevo Worker, evalúa primero si esa lógica de fondo
  tiene una parte local reutilizable (p. ej. "recalcular estadísticas cada N horas") y, si la
  tiene, pórtala igual que el resto del código — sin la parte de red.
- `LegacyCloudDataCleaner`
  ([`polar-offline/app/src/main/java/app/polar/util/LegacyCloudDataCleaner.kt`](../../polar-offline/app/src/main/java/app/polar/util/LegacyCloudDataCleaner.kt))
  existe precisamente para esto: limpia, una sola vez, cualquier rastro que dejó una instalación
  previa con sync en la nube (WorkManager persistido, preferencias de sync, tokens de sesión) al
  actualizar a esta versión 100% local. Es el patrón a seguir si en el futuro se detecta más
  rastro de nube que limpiar en dispositivos que vinieron de una versión con cuenta.

## 4. Divergencias ya conocidas y por qué existen

Estas son las únicas diferencias de código/recursos entre las dos carpetas que están justificadas
hoy. Cualquier otra diferencia que encuentres que no esté en esta lista y que no encaje en la regla
del punto 2 es, probablemente, un olvido de portado — repórtalo o corrígelo.

| Área | Solo en `polar-online/` | Motivo (justificado) |
|---|---|---|
| Permisos | `android.permission.INTERNET` en `AndroidManifest.xml` | Necesario para hablar con Supabase. |
| Paquete `data/sync/` | `EntityMappers.kt`, `EntityTouch.kt`, `MergeResolver.kt`, `SyncManager.kt`, `SyncPrefs.kt`, `TaskImageStorage.kt`, `dto/*.kt` | Todo el pipeline de sincronización (DTOs, resolución de conflictos, push/pull, Storage de imágenes) solo tiene sentido con backend remoto. |
| `di/` | `SupabaseModule.kt`, `SyncEntryPoint.kt` | Proveen el cliente de Supabase vía Hilt; no existe nada que inyectar en la versión offline. |
| `ui/activity/` | `AuthActivity.kt` (+ `activity_auth.xml`) | Pantalla de login/registro/recuperación de contraseña; no aplica sin cuentas. |
| `ui/viewmodel/` | `AuthViewModel.kt` | Estado de sesión (`sessionStatus`, sign-in/sign-out/reset password). |
| `worker/` | `SyncWorker.kt` | Sincronización periódica/inmediata contra Supabase. |
| `ui/fragment/SettingsFragment.kt` + `fragment_settings.xml` | Sección "cuenta y sincronización" (login/logout, "sincronizar ahora", subir/descargar copia completa, aviso de conflictos perdidos) | Toda esa UI depende de tener sesión Supabase; en offline `SettingsFragment` es un subconjunto sin esa sección. |
| `res/drawable/` | `ic_cloud_download.xml`, `ic_cloud_upload.xml` | Iconografía exclusiva de los botones de sync. |
| `res/values*/strings.xml` | Claves `auth_*`, `account_*`, `cloud_*`, `sync_*` | Textos de las pantallas/diálogos anteriores. |
| Único fichero exclusivo de `polar-offline/` | `util/LegacyCloudDataCleaner.kt` | Migración de limpieza al pasar de una build con cuenta a una 100% local (ver punto 3). No tiene sentido en `polar-online/`, que sí usa esas preferencias/Workers activamente. |

Todo lo demás — entidades Room, DAOs, repositorios (salvo los puntos donde `TaskRepository`/
`ReminderRepository` invocan a `SyncManager`), casos de uso, adapters, dialogs, fragments de
tareas/listas/calendario/estadísticas/papelera/Eisenhower, widgets, receivers de alarmas, temas,
`SmartParser`, `BackupManager`, y el resto de `strings.xml` — debe mantenerse **al mismo nivel de
funcionalidad y corrección** en ambos proyectos.

## 5. Flujo de trabajo recomendado al implementar algo en `polar-online/`

1. Antes de escribir código, decide explícitamente: *¿esta pieza es sync/cuenta/red, o es lógica
   de producto que da igual dónde corra?* Si tienes dudas, vuelve a la pregunta del punto 2.
2. Implementa y verifica primero en `polar-online/` si la funcionalidad toca algo relacionado con
   sync (para no tener que deshacer trabajo si el diseño de sync cambia sobre la marcha).
3. Si la pieza no es exclusiva de sync/cuenta, aplica el mismo cambio en `polar-offline/`:
   - Copia/adapta el fichero equivalente en la misma ruta relativa dentro de
     `polar-offline/app/src/main/java/app/polar/...` (o `res/...`).
   - **Elimina cualquier referencia a `SyncManager`, `SyncWorker`, `AuthViewModel`,
     `supabaseClient`, o a los campos exclusivos de sync (`dirty`, `updatedAt` remoto, `uuid` de
     Supabase si no se reutiliza igual localmente, etc.)** que pudiera arrastrar el copy-paste.
     `polar-offline/` puede tener sus propias columnas equivalentes en Room si las necesita para
     la misma lógica de negocio (p. ej. soft-delete local), pero nunca un campo cuyo único
     propósito sea coordinarse con el backend.
   - Traduce cualquier string nuevo en los 5 locales (`values`, `values-de`, `values-en-rGB`,
     `values-en-rUS`, `values-fr`) igual que exige la sección 4.6 de `AGENTS.md`/`CLAUDE.md`, en
     **ambos** proyectos.
4. Ejecuta build/tests en los dos proyectos por separado (cada uno tiene su propio Gradle
   wrapper/`settings.gradle.kts`):
   ```bash
   cd polar-online  && ./gradlew :app:assembleDebug :app:testDebugUnitTest
   cd polar-offline && ./gradlew :app:assembleDebug :app:testDebugUnitTest
   ```
5. Si el cambio afecta a `AppDatabase` (nueva entidad/columna/migración), sube la versión y añade
   la `Migration` manual **en ambos proyectos**, aunque los números de versión de esquema puedan
   haber divergido entre uno y otro por las columnas exclusivas de sync — la migración nueva debe
   existir en los dos.
6. Si el cambio es puramente de `polar-offline/` (algo que no aplica a `polar-online/` porque ya
   lo resuelve la nube, por ejemplo), documenta el motivo igual que se hace en la tabla del
   punto 4, para que no parezca un olvido a quien lea esto después.

## 6. Qué hacer si encuentras una divergencia no justificada

Si al trabajar en cualquiera de los dos proyectos detectas que existe una funcionalidad en uno y
no en el otro, y no está en la tabla del punto 4 ni depende de cuenta/red:

1. No lo ignores ni asumas que es intencional.
2. Compórtalo como un bug: implementa la funcionalidad que falta en el proyecto que la tiene
   incompleta, siguiendo el resto de convenciones de `AGENTS.md`/`CLAUDE.md` (patrones, Room,
   Hilt, `safeLaunch`, minúsculas en UI, etc.).
3. Menciónalo explícitamente en la respuesta al usuario y, si procede, en el commit (prefijo
   `fix` si es una funcionalidad rota/incompleta, `update` si simplemente faltaba por portar).

## 7. Referencia rápida

| Pregunta | `polar-online/` | `polar-offline/` |
|---|---|---|
| ¿Requiere `INTERNET`? | Sí | **No, nunca** |
| ¿Tiene cuentas/sesión? | Sí (Supabase Auth) | **No, ninguna, ni oculta** |
| ¿Sincroniza datos entre dispositivos? | Sí | No (backup/restore local a fichero) |
| ¿Funciona en modo avión, recién instalada? | No (bloquea en login) | **Sí, siempre, al 100%** |
| ¿Debe tener toda la lógica de producto no ligada a red? | Sí | **Sí, igual — es la misma app en lo demás** |
| Documento de referencia para su parte exclusiva | [`supabase-sync/`](supabase-sync/00-README.md), [`implementacion-supabase.md`](implementacion-supabase.md) | Este documento + `AGENTS.md`/`CLAUDE.md` |
