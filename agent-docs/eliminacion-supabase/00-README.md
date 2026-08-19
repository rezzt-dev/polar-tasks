# Eliminación completa de Supabase — Índice y resumen ejecutivo

> Objetivo: dejar Polar **100% offline y local**, eliminando por completo la implementación de
> sincronización con Supabase (código, dependencias, permisos, esquema de sincronización en Room,
> estado residual en dispositivos y recursos de servidor), sin perder las funcionalidades de la
> versión 1.6 ni los datos de los usuarios.
>
> Fecha del análisis: 2026-08-19. Estado del repositorio verificado con `git` en esa fecha.

---

## 1. Contexto verificado del repositorio

| Hecho | Valor | Verificación |
|---|---|---|
| Rama de trabajo actual | `version-offline` (sin Supabase, basada en `9bf4a6e`, pre-1.6) | `git branch --show-current` |
| Dónde vive Supabase | Íntegramente en el commit **`aa617df`** (tip de `version-1.6`, también mergeado en `main`) | `git branch --contains aa617df` |
| Commits de `version-1.6` sin relación con sync | 9 commits previos (tema onyx, stats, rediseño detalle de tarea, etc.) | `git log version-offline..version-1.6` |
| Versión de Room en `version-offline` | **14** | `AppDatabase.kt:18` |
| Versión de Room en `version-1.6` | **17** (migraciones 14→15, 15→16, 16→17 añadidas por sync) | `AppDatabase.kt` en `version-1.6` |
| ¿Se distribuyó la versión con sync? | **No.** `app/release/app-release.apk` es de ene-2026, anterior al sync; `versionCode=1`/`versionName=1.0` en ambas ramas | blob idéntico en ambas ramas |
| Secretos commiteados | **Ninguno.** `SUPABASE_URL`/`SUPABASE_ANON_KEY` solo en `local.properties` (gitignored) | `git grep` en `version-1.6` |
| Documentación oficial (`docs/`, `README.md`, `AGENTS.md`) | **Sin menciones a Supabase** — no requiere limpieza de contenido, solo actualización de versión de DB | `git grep -i supabase` |
| `agent-docs/` en `version-offline` | Existe en disco pero **untracked**; en `version-1.6`/`main` está trackeada | `git status` |

**Conclusión clave:** la implementación de sync está perfectamente acotada en un único commit
(`aa617df`, 75 ficheros, +7698/−187), lo que hace la eliminación un ejercicio de cirugía acotada,
no una excavación arqueológica. El riesgo real no está en "encontrar" el código, sino en tres
trampas no obvias documentadas en `01-analisis-implementacion-supabase.md` §4:

1. Las queries de purga de papelera quedan **inoperantes** si se borra sync sin reescribirlas
   (todas las filas tienen `dirty = 1` por defecto).
2. Los dispositivos que ejecutaron builds 1.6 tienen una DB **v17**; una app offline con DB v14
   provoca *downgrade* → con `fallbackToDestructiveMigration()` activo eso significa **borrado
   silencioso de datos**, no un crash visible.
3. Quedan trabajos de WorkManager encolados (`SyncWorkerPeriodic/OneTime/Frequent`) cuya clase
   dejará de existir, preferencias `sync_prefs`, una posible sesión de auth persistida por el SDK
   y una caché de imágenes en disco.

## 2. Estrategia recomendada (resumen)

Desarrollada en detalle en `02-decisiones-y-estrategia.md`:

- **Rama:** crear `version-1.6-offline` desde el tip de `version-1.6` y eliminar quirúrgicamente
  (Estrategia A). Conserva las 9 features de 1.6 y los fixes de integridad que aportan valor
  offline. *No* hacer `git revert` ciego ni quedarse en `version-offline` (se perderían features).
- **Room:** migración de limpieza **17→18** que recrea las 4 tablas sin las columnas de sync
  (`uuid`, `updatedAt`, `deletedAt`, `dirty`, `imagePath`), conservando `subtasks.orderIndex` y
  `subtasks.createdAt` (Decisión DB-2). Las migraciones 14→17 **se conservan**: son el camino de
  actualización de usuarios con DB antigua.
- **Conservar de `aa617df` (adaptado):** persistencia del `dueDate` al posponer desde notificación,
  `replaceSubtasksForTask` por diff, eliminación de rutas de hard-delete muertas, ordenación de
  subtareas por `orderIndex`, exportación de schemas Room + `room-testing` (infraestructura útil
  ajena a sync).
- **Limpieza residual:** bloque de una sola ejecución que cancela los 3 trabajos de WorkManager,
  borra `sync_prefs`, la sesión del SDK y `cacheDir/task_images`.
- **Servidor:** borrado del proyecto en el dashboard de Supabase (verificando antes que la "app
  compañera" no dependa de él) y eliminación de las claves de `local.properties`.

## 3. Mapa de documentos

| Doc | Contenido | Cuándo leerlo |
|---|---|---|
| `00-README.md` | Este índice y resumen ejecutivo | Primero |
| `01-analisis-implementacion-supabase.md` | Inventario exhaustivo de todo lo que `aa617df` introdujo, por capas, con ficheros y queries exactas | Antes de tocar código |
| `02-decisiones-y-estrategia.md` | Las 8 decisiones clave con opciones, pros/contras y recomendación | Antes de empezar la Fase 0 |
| `03-roadmap.md` | 8 fases ordenadas con dependencias, estimaciones y criterios de salida | Para planificar el trabajo |
| `04-plan-de-ejecucion.md` | Paso a paso, fichero por fichero: qué se borra, qué se restaura, qué se edita (con SQL de la migración 17→18 completo) | Durante la ejecución |
| `05-riesgos-y-mitigaciones.md` | Matriz de riesgos con probabilidad, impacto y mitigación | Antes de empezar y al revisar |
| `06-checklist-validacion.md` | Checklist ejecutable de verificación "100% offline" (greps, builds, tests, pruebas manuales, test de actualización) | Al finalizar cada fase y antes del merge |

## 4. Cómo usar esta documentación

1. Leer `01` para entender qué existe y dónde.
2. Leer `02` y confirmar/adjustar las decisiones (sobre todo DB-2 vs DB-3 y el borrado del proyecto
   Supabase si existe la app compañera).
3. Ejecutar el roadmap (`03`) siguiendo el plan detallado (`04`) fase a fase.
4. Al terminar, ejecutar el checklist completo (`06`). **No dar por terminado el trabajo sin que
   todos los puntos del checklist estén en verde.**

## 5. Relación con la documentación existente

- `agent-docs/supabase-sync/` (00–09): documentos de **diseño** de la sincronización. Pierden su
  propósito tras la eliminación; su destino se decide en `02` §D6 (recomendación: eliminarlos de la
  rama offline en el commit final — permanecen en el historial git y en `main`).
- `agent-docs/analisis-implementacion-supabase-sync.md`: auditoría de la implementación real.
  Mismo destino que el anterior. Sus hallazgos de integridad de datos (3.1–3.3) son la razón de
  varios de los fixes que esta eliminación **sí conserva** adaptados (ver `02` §D3).
