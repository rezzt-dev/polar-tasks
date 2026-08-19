# Fase 0 — Resultado de la verificación de infraestructura

> Registro vivo del punto 0 del roadmap en
> [`agent-docs/analisis-implementacion-supabase-sync.md`](../analisis-implementacion-supabase-sync.md#fase-0--verificación-de-infraestructura-bloqueante-hacer-primero-y-en-paralelo-al-resto).
> Se actualiza cada vez que hay nueva información — no se borra el historial de intentos anteriores.

## 0.1 — Auditoría del proyecto Supabase real

### Acceso disponible para este agente

Según [`08-configuracion-y-credenciales.md`](08-configuracion-y-credenciales.md), este proyecto
**solo** guarda en `local.properties` el `SUPABASE_URL` y la clave `publishable` (anon). No hay
`service_role`, ni contraseña de base de datos, ni token de la Management API — a propósito, para no
tener ese secreto en ningún sitio del repo. Eso significa que **desde el código no se puede ejecutar SQL
de introspección** (`pg_policies`, `pg_trigger`, `cron.job`, `pg_publication_tables` no son accesibles vía
REST/PostgREST con la anon key). Lo que sigue es exactamente lo que sí se pudo comprobar así, y lo que
queda pendiente de que el usuario lo confirme desde el SQL Editor del dashboard.

### Verificado por API (anon key, solo lectura, 2026-08-19)

| Comprobación | Método | Resultado |
|---|---|---|
| `task_lists` existe | `GET /rest/v1/task_lists?select=*&limit=1` | `200 []` — tabla existe y responde |
| `tasks` existe | ídem | `200 []` |
| `subtasks` existe | ídem | `200 []` |
| `reminders` existe | ídem | `200 []` |
| RLS bloqueando lecturas anónimas | Las 4 llamadas anteriores devuelven `200 []` en vez de un error, y sin JWT de usuario (`auth.uid()` es `null`) — es **consistente** con que exista una política `select_own` basada en `auth.uid() = user_id`, pero **no es prueba concluyente**: una tabla sin ninguna política RLS también devolvería `[]` a un cliente sin filas propias visibles solo si RLS está *forzado y sin policies* (deniega todo) — no se puede distinguir "RLS correcto" de "RLS deniega todo sin policies" ni de "tabla vacía sin RLS" solo con este resultado. | Pendiente de confirmar con el bloque 1 y 3 del script SQL (abajo) |
| Bucket `task-images` | `GET /storage/v1/bucket/task-images` | `400 {"error":"Bucket not found","code":"NoSuchBucket"}` → **el bucket no existe** (o al menos no es visible/accesible con la anon key) |
| Endpoint raíz de Postgrest (OpenAPI) | `GET /rest/v1/` | `401 {"message":"Secret API key required"}` — esperado, ese endpoint concreto exige `service_role`, no indica ningún problema |

**Hallazgo confirmado:** el bucket de Storage `task-images` que pide el punto 7 del checklist de
`08-configuracion-y-credenciales.md` **no está creado** en el proyecto real, o la anon key no tiene
visibilidad sobre él. Esto bloquea cualquier subida/descarga de imágenes de tareas en producción
(`TaskImageStorage`, hallazgo 4.9 del análisis) — es una causa adicional, no documentada hasta ahora, de
por qué las imágenes "no salen del dispositivo".

### Decisión sobre el acceso (2026-08-19)

Se preguntó al usuario cómo completar esta parte (dar acceso `service_role` temporal, ejecutar el script
él mismo, o dejarlo pendiente). Respuesta: **el usuario ejecutará el script él mismo** en el SQL Editor y
pegará el resultado. Hasta que eso ocurra, los puntos 1, 3-9 del script siguen sin confirmar — este
documento se actualizará con los resultados reales en cuanto se reciban, sin asumir nada mientras tanto.

### Pendiente — requiere el SQL Editor del dashboard (no soy capaz de comprobarlo con la anon key)

Se preparó [`09-fase0-auditoria-sql.sql`](09-fase0-auditoria-sql.sql): un script de **solo lectura**
(consultas a catálogos del sistema, ningún `INSERT`/`UPDATE`/`DDL`) para pegar en **SQL Editor** del
dashboard de Supabase y contrastar contra `03-esquema-supabase.md`. Cubre:

1. RLS activado por tabla (`relrowsecurity`).
2. Columnas exactas de las 4 tablas.
3. Las 16 políticas esperadas (4 tablas × `select_own`/`insert_own`/`update_own`/`delete_own`).
4. Triggers instalados en las 4 tablas.
5. Código fuente real de `touch_and_resolve_lww()` (y las funciones de purga/recurrencia) — clave para
   cerrar el hallazgo 5 (punto 0.2 de abajo).
6. Jobs de `pg_cron` (`purge_old_tombstones`, recurrencia).
7. Tablas dadas de alta en la publicación `supabase_realtime`.
8. Bucket `task-images` y sus policies de Storage.
9. Extensiones (`pgcrypto`, `pg_cron`, `pg_net`).

**Estado: sin ejecutar todavía.** Ningún resultado de estos 9 puntos está confirmado — no se debe asumir
que el DDL de `03-esquema-supabase.md` está desplegado tal cual hasta que se pegue aquí la salida real del
script.

---

## 0.2 — Inconsistencia del trigger `touch_and_resolve_lww` (hallazgo 5)

**Estado: RESUELTO (2026-08-19) — Opción B, decisión del usuario.** No se toca el trigger real (tampoco
había credenciales para ejecutar DDL contra él) ni el código de `SyncManager`. Se corrigió la
documentación de `03-esquema-supabase.md` (principio de diseño nº4 y la cabecera de la sección del
trigger) para reflejar el comportamiento real: `updated_at` lo decide el cliente y el LWW depende de que
el reloj del dispositivo esté razonablemente sincronizado. Sin impacto en el roadmap de fases
posteriores: la Fase 1 (hallazgo 3.1) puede seguir asumiendo "devuelto == enviado ⇒ gané" en
`resolvePushOutcome`, tal y como ya hace hoy.

Resumen del problema (ver análisis completo, sección 5): el SQL documentado en `03-esquema-supabase.md`
solo reescribe `NEW.server_updated_at := now()`; el `updated_at` que se compara para decidir quién gana el
conflicto (`NEW.updated_at < OLD.updated_at`) sigue siendo el que manda el cliente. Esto contradice la
prosa del principio de diseño nº4 del propio doc 03 ("el LWW es fiable incluso si el reloj de un
dispositivo está mal ajustado"), que solo es cierta si el servidor controla `updated_at`.

Las dos opciones válidas, tal y como las plantea el roadmap:

- **Opción A — el servidor manda:** el trigger reescribe también `NEW.updated_at` con la hora del
  servidor en cada escritura aceptada. Full clock-safety, pero cambia la semántica de "quién gana": ya no
  gana "quien editó antes según su propio reloj", gana "quien llegó antes al servidor" — dos ediciones
  offline que se sincronizan más tarde se ordenan por orden de llegada, no por orden real de edición. Sin
  embargo, dado que la comparación LWW (`NEW.updated_at < OLD.updated_at`) necesita un valor de
  `updated_at` *entrante* para decidir si acepta o descarta el UPDATE, si el trigger ya lo hubiera
  reescrito en la fila anterior, la comparación pasa a hacerse siempre entre "hora de servidor de la
  escritura anterior" y "hora de servidor de la escritura actual" — que son crecientes por construcción
  (siempre `now() > now() anterior`), así que el trigger nunca descartaría un `UPDATE` entrante: **el
  último que llega siempre gana**, sin comparación real. Es decir, Opción A tal y como está planteada en
  el roadmap **no preserva LWW real**, lo convierte en "el último commit al servidor gana" — que es una
  garantía distinta (más simple, pero pierde la propiedad "quien editó objetivamente más tarde gana" en
  presencia de reconexión tardía de un dispositivo). Esto no estaba explicitado en el roadmap y hay que
  decidirlo con el usuario antes de tocar SQL de producción.
- **Opción B — se documenta la dependencia del reloj:** no se toca el trigger; se corrige la prosa del
  principio de diseño nº4 en `03-esquema-supabase.md` y `04-estrategia-sincronizacion.md` para reflejar
  que el LWW depende de que el reloj del dispositivo esté razonablemente sincronizado (NTP automático de
  Android, que es el caso por defecto salvo que el usuario lo desactive a mano). No requiere cambios de
  SQL ni de `SyncManager`.

Se preguntó al usuario cuál de las dos prefería antes de escribir ningún SQL nuevo o tocar `SyncManager`,
tal y como exige el punto 10 de las instrucciones para el agente del propio análisis. Eligió la Opción B.

---

## Próximos pasos para cerrar la Fase 0

1. **Pendiente:** el usuario ejecuta `09-fase0-auditoria-sql.sql` en el SQL Editor de Supabase y pega los 9
   resultados aquí (o los adjunta) — sin esto, 0.1 no se puede dar por cerrado ni se puede confiar en que
   el resto del roadmap (en particular Fase 3.4, ligada a `pg_cron`, y Fase 6, ligada a Realtime) parta de
   una base real. Cuando lleguen los resultados, este documento se actualiza con la comparación real contra
   `03-esquema-supabase.md` y, si aparece cualquier divergencia (además del bucket `task-images`, ya
   confirmado que falta), se añade a la tabla resumen del análisis principal como nuevo hallazgo.
2. ~~El usuario decide Opción A vs. Opción B para 0.2~~ — hecho, Opción B, sin cambios de código
   pendientes por este punto.
3. **Acción recomendada, fuera de este roadmap pero derivada de esta auditoría:** crear el bucket
   `task-images` en Storage (con sus 3 policies, ver bloque 7 del SQL de `03-esquema-supabase.md`) antes de
   dar por buena cualquier prueba de imágenes adjuntas — hoy cualquier subida fallará en silencio
   (`TaskImageStorage.upload()` capturando la excepción y devolviendo `null`, hallazgo 4.9).
4. Con 0.1 confirmado y 0.2 ya cerrado, se puede continuar con la Fase 1 del roadmap (hallazgos 2, 3.1,
   3.2, 3.3), que no dependen del resultado pendiente de 0.1 salvo en el punto 3 (bucket) para el caso
   específico de imágenes.
