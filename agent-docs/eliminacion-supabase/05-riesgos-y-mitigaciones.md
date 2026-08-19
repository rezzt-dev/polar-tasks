# 05 — Riesgos y mitigaciones

> Matriz de riesgos de la eliminación. Probabilidad e impacto: **A**lta/**M**edia/**B**aja. Cada
> riesgo enlaza con la fase del plan que lo mitiga. Revisar antes de empezar y de nuevo antes del
> merge.

---

## Riesgos de datos

### R1 — Pérdida de datos en dispositivos con build 1.6 por downgrade de Room
- **Probabilidad: M** (solo si se ignoran D2/DB-2) · **Impacto: A**
- Escenario: la app final se queda en v14–v16; un dispositivo con DB v17 la abre en *downgrade* y
  `fallbackToDestructiveMigration()` borra todo silenciosamente.
- **Mitigación:** Decisión DB-2 (v18 con migración de limpieza, doc 04 Fase 4). Nunca bajar el
  número de versión de Room. Backup exportado en Fase 0 como red extra.

### R2 — Migración 17→18 defectuosa (FKs, orden de recreación, DDL no fiel)
- **Probabilidad: M** · **Impacto: A**
- Escenario: recrear tablas en orden incorrecto dispara el `ON DELETE CASCADE` implícito de
  `DROP TABLE` y vacía tablas hijas; o el DDL recreado difiere del esperado por Room y la app
  crashea al abrir tras migrar.
- **Mitigación:** patrón de tablas temporales hijo→padre (doc 04 §4.1, diseñado para que ningún
  `DROP` toque una tabla referenciada con hijos vivos), `PRAGMA foreign_key_check` al final,
  DDL cotejado contra el `createSql` de `17.json`, y **`MigrationTest` instrumentado 17→18
  obligatorio en emulador API 24** (SQLite más antiguo soportado) + uno reciente. Room además
  valida el esquema al abrir (segunda red).
- **Aceptación:** si el test no puede hacerse pasar, caer a DB-3 (destructivo con backup previo)
  como atajo documentado, nunca como silencio.

### R3 — Papelera inutilizable por purgas con `dirty = 0`
- **Probabilidad: A si se ejecuta mal la Fase 3** · **Impacto: A**
- Escenario: se borra el motor sync pero las queries de purga conservan `AND dirty = 0`; como el
  default es `dirty = 1` y ya nadie lo limpia, "vaciar papelera" no borra nada, sin error visible.
- **Mitigación:** estado objetivo explícito por query (doc 04 §3.2), test de repositorio que purga
  de verdad (Fase 7), prueba manual de papelera en el checklist (doc 06 §5).

### R4 — Restaurar un backup de la era sync en la app offline
- **Probabilidad: M** (es el flujo de la red de seguridad de la Fase 0) · **Impacto: B**
- Escenario: el JSON de backup 1.6 lleva campos extra (`uuid`, `dirty`, …).
- **Mitigación:** verificado — `BackupManager` usa Gson sobre la entidad completa y Gson ignora
  campos desconocidos; la restauración funciona sin cambios. Prueba manual incluida en doc 06 §5.
- **Nota inversa (no aplica aquí, consta para historia):** un backup offline restaurado en una
  build 1.6 sí fallaría (campos NOT NULL sin default vía Gson/Unsafe). Irrelevante una vez la 1.6
  quede retirada.

## Riesgos de estado residual

### R5 — Sesión/preferencias/caché residuales en dispositivos
- **Probabilidad: A** (ocurre siempre al actualizar) · **Impacto: B** (residuo inerte, sin fuga)
- Escenario: `sync_prefs`, posible fichero de sesión de supabase-kt y `cacheDir/task_images/`
  sobreviven a la desinstalación del SDK.
- **Mitigación:** bloque de limpieza única (doc 04 Fase 5). El nombre del fichero de sesión se
  verifica en dispositivo antes de codificarlo; si no se identifica, se acepta explícitamente
  como inerte (nadie lo lee sin el SDK) — no inventar nombres.

### R6 — Trabajos huérfanos de WorkManager
- **Probabilidad: A** (al actualizar) · **Impacto: B**
- Escenario: los 3 trabajos únicos (`SyncWorkerPeriodic/OneTime/Frequent`) quedan encolados con
  una clase que ya no existe → WorkManager los marca fallidos; el periódico puede seguir
  reintentándose y generando ruido.
- **Mitigación:** cancelación por nombre en el mismo bloque de limpieza. Verificación con
  `adb shell dumpsys jobscheduler` en doc 06 §4.

## Riesgos externos

### R7 — Borrar el proyecto Supabase mientras la app compañera lo usa
- **Probabilidad: ?** (depende de si la app compañera existe y está activa) · **Impacto: A para
  esa app**
- **Mitigación:** verificación previa obligatoria (doc 02 §D5 paso 1). Si hay duda, **no borrar**:
  retirar solo las credenciales locales. El coste de dejar el proyecto vivo unos meses es cero;
  el de borrarlo por error, irrecuperable.

### R8 — Credenciales expuestas
- **Probabilidad: B** · **Impacto: M**
- Estado verificado: ningún secreto commiteado; `local.properties` gitignored; la anon key nunca
  salió del entorno local (la build con sync nunca se distribuyó).
- **Mitigación:** borrado de las dos líneas de `local.properties` en la Fase 6 y, si se borra el
  proyecto, las claves quedan invalidadas de facto. Si alguna vez se distribuyera una build con
  sync, rotar claves desde el dashboard antes de borrar.

## Riesgos de ingeniería

### R9 — Revert ciego que arrastre fixes válidos o reintroduzca deuda
- **Probabilidad: M** · **Impacto: M**
- Escenario: `git revert aa617df` o restaurar todo desde `7fe3581` pierde F2/F3/F4 y la ordenación
  de subtareas, y reintroduce los hard-deletes muertos señalados por la auditoría.
- **Mitigación:** la clasificación CLASE 1/2/3 del doc 04 se hizo fichero a fichero contra el diff
  real; los restaurados desde `7fe3581` están limitados a ficheros cuyos hunks son 100% sync.

### R10 — Compilación rota entre fases
- **Probabilidad: A (conocido y acotado)** · **Impacto: B**
- Escenario: Fase 1 deja consumidores sin motor; Fase 3 deja entidades sin versión válida.
- **Mitigación:** el roadmap agrupa 1+2 y 3+4 explícitamente; los puntos de compilación verde
  están marcados por fase. No commitear a mitad de fase sin dejar compilando (salvo WIP en rama
  propia).

### R11 — Strings/recursos huérfanos o usados tras el borrado
- **Probabilidad: M** · **Impacto: B**
- Escenario: alguna de las 56 claves se usa fuera de lo inventariado, o un drawable "de sync" se
  usa en otro layout.
- **Mitigación:** los drawables `ic_cloud_*` solo se usan en `fragment_settings.xml` (verificado);
  `ic_sync`/`ic_chevron_right` son preexistentes y se conservan (doc 02 §D8). El checkout de
  strings desde `7fe3581` es atómico por fichero; la verificación de la Fase 6 grepea usos
  residuales, y el compilador de recursos (`aapt2`) detectaría cualquier referencia rota.

### R12 — Regresiones funcionales ajenas a sync
- **Probabilidad: B** · **Impacto: M**
- Escenario: al tocar ViewModels/receivers se rompe algo no relacionado (alarmas, recurrencia,
  ordenación, widget).
- **Mitigación:** suite unitaria completa + pruebas manuales de regresión del checklist
  (doc 06 §5), que cubren alarmas, recurrencia, papelera, backup, widget y calendario.

## Riesgos aceptados explícitamente

| Riesgo | Por qué se acepta |
|---|---|
| Residuo de sesión supabase-kt si el fichero no se identifica (R5 parcial) | Inerte sin el SDK; cero fuga (el token muere al borrar el proyecto) |
| `app/schemas/14.json` y `17.json` permanecen en el repo | Son necesarios para el `MigrationTest` de la cadena 14→18, que sigue viva |
| Las migraciones 14→17 (que crean columnas sync) permanecen para siempre | Son el camino de actualización de DBs antiguas; eliminarlas rompería upgrades |
