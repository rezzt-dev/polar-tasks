# CHANGELOG

todas las modificaciones relevantes del proyecto polar se documentan en este fichero.
all notable changes to the polar project are documented in this file.

el contenido esta escrito en minusculas y sin acentos; los titulos van en mayusculas y sin acentos.
content is written in lowercase without accents; headings are uppercase without accents.

cada version incluye el mismo contenido en castellano y en ingles.
each version includes the same content in spanish and in english.

las fechas usan el formato aaaa-mm-dd. el formato de las secciones esta descrito en `agents.md` y `claude.md`.
dates use the yyyy-mm-dd format. the section format is described in `agents.md` and `claude.md`.

---

## SIN PUBLICAR / UNRELEASED

### CASTELLANO

#### CAMBIOS

- redisenar recordatorios con resumen del proximo aviso, filtros por estado, grupos por fecha y tarjetas con descripcion, hora, ubicacion y acciones accesibles; mostrar fondo, icono y texto al deslizar para completar, reactivar o enviar a la papelera con deshacer.

- organizar los ajustes en categorias con vistas dedicadas, navegacion de vuelta, opciones avanzadas de nube separadas y valores legibles bajo cada preferencia.

- dar feedback visual al deslizar elementos de la papelera reutilizando `TaskSwipeHelper`: fondo de color e icono al restablecer a la lista (derecha) o eliminar permanentemente (izquierda), igual que el swipe de la lista de tareas.
- anadir una barra lateral izquierda con extremos redondos (`bg_trash_stripe`) y el color de foreground del tema (`colorOnSurface`) a las tarjetas de la papelera en `item_trash.xml` para distinguirlas mejor.
- anadir `.docs/` al fichero `.gitignore` para que git no rastree documentacion local del proyecto.
- mover `agent-docs/` dentro de `.docs/` y dejar las skills al mismo nivel que `agent-docs`.
- crear `claude.md` con el contexto de trabajo para claude code y enlace a `agents.md`.
- anadir a `agents.md` y `claude.md` la seccion de redaccion de commits, replica de `.docs/commit-guidelines.md`.
- anadir a `agents.md` y `claude.md` la seccion con el formato de las entradas de `changelog.md`.
- documentar en `agents.md` (seccion 4.6) y `claude.md` la regla de que toda la interfaz se escribe en minusculas y en todos los idiomas.

#### CORREGIDO

- corregir los errores de lint al completar las traducciones, aplicar tintes compatibles en recordatorios y aislar los atributos de navegacion de api 27 en temas especificos.

- permitir eliminar permanentemente y vaciar la papelera sin cuenta vinculada: el guardia de sincronizacion (`dirty = 0`) dejaba los elementos atascados con el aviso `still waiting to sync with the cloud` cuando la app es puramente local; ahora, si no hay sesion, se purga directamente con `forcePermanentDelete` / `forceEmptyTrash`.
- pasar a minusculas todos los textos de interfaz de recordatorios y ajustes (navegacion de ajustes, detalle de tarea, dialogos y agenda de recordatorios) en `values`, `values-en-rGB`, `values-en-rUS`, `values-de` y `values-fr` para respetar la convencion de minusculas de toda la app.

#### NUEVO

- ampliar el selector de iconos de los formularios de creacion y edicion de listas con opciones para compras, estudios, salud, deporte, viajes, finanzas, ocio, familia y hogar.
- crear el fichero `changelog.md` para registrar los cambios del proyecto de aqui en adelante.

### ENGLISH

#### CHANGED

- redesign reminders with an upcoming alert summary, status filters, date groups and cards with description, time, location and accessible actions; reveal a background, icon and label when swiping to complete, reactivate or move to trash with undo.

- organize settings into categories with dedicated views, back navigation, separate advanced cloud options and readable values below each preference.

- give visual feedback when swiping trash items by reusing `TaskSwipeHelper`: colored background and icon when restoring to the list (right) or deleting permanently (left), matching the task list swipe.
- add a left side bar with rounded ends (`bg_trash_stripe`) using the theme foreground color (`colorOnSurface`) to the trash cards in `item_trash.xml` so they are easier to tell apart.
- add `.docs/` to the `.gitignore` file so git stops tracking local project documentation.
- move `agent-docs/` into `.docs/` and keep the skills at the same level as `agent-docs`.
- create `claude.md` with the working context for claude code and a link to `agents.md`.
- add the commit writing section to `agents.md` and `claude.md`, mirroring `.docs/commit-guidelines.md`.
- add the section describing the format of `changelog.md` entries to `agents.md` and `claude.md`.
- document in `agents.md` (section 4.6) and `claude.md` the rule that the whole interface is written in lowercase in every language.

#### FIXED

- fix lint errors by completing translations, applying compatible reminder icon tints and isolating api 27 navigation attributes in version-specific themes.

- allow permanently deleting and emptying the trash without a linked account: the sync guard (`dirty = 0`) left items stuck with the `still waiting to sync with the cloud` warning when the app is purely local; now, if there is no session, the purge runs directly via `forcePermanentDelete` / `forceEmptyTrash`.
- lowercase every reminders and settings interface string (settings navigation, task detail, dialogs and reminders agenda) in `values`, `values-en-rGB`, `values-en-rUS`, `values-de` and `values-fr` to follow the app-wide lowercase convention.

#### ADDED

- expand the icon picker in the list creation and editing forms with options for shopping, education, health, fitness, travel, finance, leisure, family, and home.
- create the `changelog.md` file to record project changes from now on.
