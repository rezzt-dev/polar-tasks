# CHANGELOG

todas las modificaciones relevantes del proyecto polar se documentan en este fichero.
all notable changes to the polar project are documented in this file.

el contenido esta escrito en minusculas y sin acentos; los titulos van en mayusculas y sin acentos.
content is written in lowercase without accents; headings are uppercase without accents.

cada version incluye el mismo contenido en castellano y en ingles.
each version includes the same content in spanish and in english.

el repositorio contiene dos versiones de la app: `polar-online/` (sincronizacion en la nube) y `polar-offline/` (100% local). las entradas que solo afectan a una de ellas empiezan por `[online]` u `[offline]`; el resto aplica a las dos.
the repository contains two versions of the app: `polar-online/` (cloud sync) and `polar-offline/` (100% local). entries that only affect one of them start with `[online]` or `[offline]`; the rest apply to both.

las fechas usan el formato aaaa-mm-dd. el formato de las secciones esta descrito en `agents.md` y `claude.md`.
dates use the yyyy-mm-dd format. the section format is described in `agents.md` and `claude.md`.

---

## SIN PUBLICAR / UNRELEASED

### CASTELLANO

#### CAMBIOS

- reorganizar el repositorio en dos proyectos gradle independientes, `polar-online/` y `polar-offline/`, para mantener las dos versiones de la app en la misma rama sin duplicar trabajo.
- trabajar con dos ramas ademas de `main`: `testing-branch` para desarrollo y `release-branch` para publicacion.
- unificar en un unico `.gitignore` en la raiz las reglas de los dos proyectos y anadir `.docs/` para que git no rastree documentacion local del proyecto.
- unificar en este `changelog.md` los changelogs de las dos versiones.
- sustituir la licencia mit por una licencia propia de uso no comercial (`LICENSE`) y actualizarla en `readme.md`.
- [offline] convertir polar en una app 100% offline: todos los datos se guardan solo en el dispositivo y no hace falta ninguna cuenta.
- [offline] subir la base de datos a la version 18 con `MIGRATION_17_18`, que elimina las columnas de sincronizacion (`uuid`, `updatedAt`, `deletedAt`, `dirty`, `imagePath`), aplica los borrados logicos pendientes, reactiva las subtareas de tareas en la papelera, indexa `tasks.listId` y `subtasks.taskId` y conserva el contador de ids.
- [offline] borrar listas, subtareas y elementos de la papelera de forma fisica e inmediata; al eliminar una lista se cancelan tambien las alarmas de sus tareas.
- [offline] restaurar las copias de seguridad en una unica transaccion y aceptar las copias exportadas por la v1.6, descartando listas y subtareas borradas y elementos huerfanos.
- [offline] limpiar al arrancar, una sola vez, los trabajos de sincronizacion pendientes, el estado de sincronizacion y la sesion guardada que dejo la v1.6 (`LegacyCloudDataCleaner`).
- [offline] actualizar la documentacion tecnica de `polar-offline/docs/` y las notas de la version 1.7 para reflejar el modelo 100% offline.
- [online] usar el motor `ktor-client-okhttp` en lugar de `ktor-client-android` para las conexiones con supabase.
- redisenar recordatorios con resumen del proximo aviso, filtros por estado, grupos por fecha y tarjetas con descripcion, hora, ubicacion y acciones accesibles; mostrar fondo, icono y texto al deslizar para completar, reactivar o enviar a la papelera con deshacer.
- organizar los ajustes en categorias con vistas dedicadas, navegacion de vuelta y valores legibles bajo cada preferencia; en la version online, las opciones avanzadas de nube van en una categoria aparte.
- dar feedback visual al deslizar elementos de la papelera reutilizando `TaskSwipeHelper`: fondo de color e icono al restablecer a la lista (derecha) o eliminar permanentemente (izquierda), igual que el swipe de la lista de tareas.
- anadir una barra lateral izquierda con extremos redondos (`bg_trash_stripe`) y el color de foreground del tema (`colorOnSurface`) a las tarjetas de la papelera en `item_trash.xml` para distinguirlas mejor.
- mover `agent-docs/` dentro de `.docs/` y dejar las skills al mismo nivel que `agent-docs`.
- crear `claude.md` con el contexto de trabajo para claude code y enlace a `agents.md`.
- anadir a `agents.md` y `claude.md` la seccion de redaccion de commits, replica de `.docs/commit-guidelines.md`.
- anadir a `agents.md` y `claude.md` la seccion con el formato de las entradas de `changelog.md`.
- documentar en `agents.md` (seccion 4.6) y `claude.md` la regla de que toda la interfaz se escribe en minusculas y en todos los idiomas.
- anadir `.docs/agent-docs/reglas-online-vs-offline.md` con las reglas obligatorias entre `polar-online/` y `polar-offline/` y renombrar `analisis-implementacion-supabase-sync.md` a `implementacion-supabase.md` dentro de `agent-docs/`.
- crear `.docs/pr-merge-release-guidelines.md` y replicar su contenido en `agents.md` y `claude.md` para que las pull requests, los commits de merge y las releases de github usen el mismo formato de categorias que `changelog.md`.

#### CORREGIDO

- corregir los errores de lint al completar las traducciones, aplicar tintes compatibles en recordatorios y aislar los atributos de navegacion de api 27 en temas especificos.
- pasar a minusculas todos los textos de interfaz de recordatorios y ajustes (navegacion de ajustes, detalle de tarea, dialogos y agenda de recordatorios) en `values`, `values-en-rGB`, `values-en-rUS`, `values-de` y `values-fr` para respetar la convencion de minusculas de toda la app.
- [offline] ignorar en `RecurrenceWorker` las tareas recurrentes que estan en la papelera: se reactivaban y programaban alarmas de tareas ya borradas.
- [offline] actualizar a `v1.7` la version que muestra el pie de los ajustes (`developed_by_full`), que seguia indicando `v1.5`, en todos los idiomas.
- [online] permitir eliminar permanentemente y vaciar la papelera sin cuenta vinculada: el guardia de sincronizacion (`dirty = 0`) dejaba los elementos atascados con el aviso `still waiting to sync with the cloud` cuando la app es puramente local; ahora, si no hay sesion, se purga directamente con `forcePermanentDelete` / `forceEmptyTrash`.

#### NUEVO

- ampliar el selector de iconos de los formularios de creacion y edicion de listas con opciones para compras, estudios, salud, deporte, viajes, finanzas, ocio, familia y hogar.
- crear el fichero `changelog.md` para registrar los cambios del proyecto de aqui en adelante.

#### ELIMINADO

- eliminar el fichero obsoleto `readme_features.md`.
- [offline] eliminar la sincronizacion en la nube con supabase: `SyncManager`, `SyncWorker`, `SyncPrefs`, dtos, mappers, resolucion de conflictos, realtime y almacenamiento de imagenes en la nube.
- [offline] eliminar el inicio de sesion (`AuthActivity`, `AuthViewModel`, `activity_auth.xml`) y las categorias de ajustes de cuenta y de opciones avanzadas de nube, con sus textos en todos los idiomas.
- [offline] eliminar las dependencias de supabase, ktor y kotlinx serialization, los campos `SUPABASE_URL` / `SUPABASE_ANON_KEY` de `BuildConfig` y los tests y documentos de diseno de la sincronizacion.

#### SEGURIDAD

- [offline] quitar los permisos `INTERNET` y `ACCESS_NETWORK_STATE` que declaraba la app: polar no se conecta a ningun servidor ni envia datos fuera del dispositivo.

### ENGLISH

#### CHANGED

- reorganize the repository into two independent gradle projects, `polar-online/` and `polar-offline/`, to keep both versions of the app in the same branch without duplicating work.
- work with two branches besides `main`: `testing-branch` for development and `release-branch` for publishing.
- merge the rules of both projects into a single root `.gitignore` and add `.docs/` so git stops tracking local project documentation.
- merge the changelogs of both versions into this `changelog.md`.
- replace the mit license with a custom non-commercial license (`LICENSE`) and update it in `readme.md`.
- [offline] turn polar into a 100% offline app: all data is stored only on the device and no account is needed.
- [offline] bump the database to version 18 with `MIGRATION_17_18`, which drops the sync columns (`uuid`, `updatedAt`, `deletedAt`, `dirty`, `imagePath`), applies pending soft deletes, revives subtasks of trashed tasks, indexes `tasks.listId` and `subtasks.taskId` and keeps the id counter.
- [offline] delete lists, subtasks and trash items physically and immediately; deleting a list also cancels the alarms of its tasks.
- [offline] restore backups in a single transaction and accept backups exported by v1.6, discarding deleted lists and subtasks and orphaned items.
- [offline] clean up once on startup the pending sync jobs, sync state and saved session left by v1.6 (`LegacyCloudDataCleaner`).
- [offline] update the technical docs in `polar-offline/docs/` and the 1.7 release notes to reflect the 100% offline model.
- [online] use the `ktor-client-okhttp` engine instead of `ktor-client-android` for supabase connections.
- redesign reminders with an upcoming alert summary, status filters, date groups and cards with description, time, location and accessible actions; reveal a background, icon and label when swiping to complete, reactivate or move to trash with undo.
- organize settings into categories with dedicated views, back navigation and readable values below each preference; in the online version, the advanced cloud options live in a separate category.
- give visual feedback when swiping trash items by reusing `TaskSwipeHelper`: colored background and icon when restoring to the list (right) or deleting permanently (left), matching the task list swipe.
- add a left side bar with rounded ends (`bg_trash_stripe`) using the theme foreground color (`colorOnSurface`) to the trash cards in `item_trash.xml` so they are easier to tell apart.
- move `agent-docs/` into `.docs/` and keep the skills at the same level as `agent-docs`.
- create `claude.md` with the working context for claude code and a link to `agents.md`.
- add the commit writing section to `agents.md` and `claude.md`, mirroring `.docs/commit-guidelines.md`.
- add the section describing the format of `changelog.md` entries to `agents.md` and `claude.md`.
- document in `agents.md` (section 4.6) and `claude.md` the rule that the whole interface is written in lowercase in every language.
- add `.docs/agent-docs/reglas-online-vs-offline.md` with the mandatory rules between `polar-online/` and `polar-offline/`, and rename `analisis-implementacion-supabase-sync.md` to `implementacion-supabase.md` inside `agent-docs/`.
- create `.docs/pr-merge-release-guidelines.md` and mirror its content in `agents.md` and `claude.md` so pull requests, merge commits and github releases use the same category format as `changelog.md`.

#### FIXED

- fix lint errors by completing translations, applying compatible reminder icon tints and isolating api 27 navigation attributes in version-specific themes.
- lowercase every reminders and settings interface string (settings navigation, task detail, dialogs and reminders agenda) in `values`, `values-en-rGB`, `values-en-rUS`, `values-de` and `values-fr` to follow the app-wide lowercase convention.
- [offline] skip trashed recurring tasks in `RecurrenceWorker`: they were reset and scheduled alarms for tasks the user had already deleted.
- [offline] update the version shown in the settings footer (`developed_by_full`) to `v1.7` in every language; it still read `v1.5`.
- [online] allow permanently deleting and emptying the trash without a linked account: the sync guard (`dirty = 0`) left items stuck with the `still waiting to sync with the cloud` warning when the app is purely local; now, if there is no session, the purge runs directly via `forcePermanentDelete` / `forceEmptyTrash`.

#### ADDED

- expand the icon picker in the list creation and editing forms with options for shopping, education, health, fitness, travel, finance, leisure, family, and home.
- create the `changelog.md` file to record project changes from now on.

#### REMOVED

- remove the obsolete `readme_features.md` file.
- [offline] remove supabase cloud sync: `SyncManager`, `SyncWorker`, `SyncPrefs`, dtos, mappers, conflict resolution, realtime and cloud image storage.
- [offline] remove sign-in (`AuthActivity`, `AuthViewModel`, `activity_auth.xml`) and the account and advanced cloud settings categories, with their strings in every language.
- [offline] remove the supabase, ktor and kotlinx serialization dependencies, the `SUPABASE_URL` / `SUPABASE_ANON_KEY` `BuildConfig` fields and the sync tests and design docs.

#### SECURITY

- [offline] drop the `INTERNET` and `ACCESS_NETWORK_STATE` permissions declared by the app: polar never connects to a server or sends data off the device.
