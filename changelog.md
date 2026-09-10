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

- organizar los ajustes en categorias con vistas dedicadas, navegacion de vuelta, opciones avanzadas de nube separadas y valores legibles bajo cada preferencia.

- dar feedback visual al deslizar elementos de la papelera reutilizando `TaskSwipeHelper`: fondo de color e icono al restablecer a la lista (derecha) o eliminar permanentemente (izquierda), igual que el swipe de la lista de tareas.
- anadir una barra lateral izquierda con extremos redondos (`bg_trash_stripe`) y el color de foreground del tema (`colorOnSurface`) a las tarjetas de la papelera en `item_trash.xml` para distinguirlas mejor.
- anadir `.docs/` al fichero `.gitignore` para que git no rastree documentacion local del proyecto.
- mover `agent-docs/` dentro de `.docs/` y dejar las skills al mismo nivel que `agent-docs`.
- crear `claude.md` con el contexto de trabajo para claude code y enlace a `agents.md`.
- anadir a `agents.md` y `claude.md` la seccion de redaccion de commits, replica de `.docs/commit-guidelines.md`.
- anadir a `agents.md` y `claude.md` la seccion con el formato de las entradas de `changelog.md`.

#### CORREGIDO

- permitir eliminar permanentemente y vaciar la papelera sin cuenta vinculada: el guardia de sincronizacion (`dirty = 0`) dejaba los elementos atascados con el aviso `still waiting to sync with the cloud` cuando la app es puramente local; ahora, si no hay sesion, se purga directamente con `forcePermanentDelete` / `forceEmptyTrash`.

#### NUEVO

- ampliar el selector de iconos de los formularios de creacion y edicion de listas con opciones para compras, estudios, salud, deporte, viajes, finanzas, ocio, familia y hogar.
- crear el fichero `changelog.md` para registrar los cambios del proyecto de aqui en adelante.

### ENGLISH

#### CHANGED

- organize settings into categories with dedicated views, back navigation, separate advanced cloud options and readable values below each preference.

- give visual feedback when swiping trash items by reusing `TaskSwipeHelper`: colored background and icon when restoring to the list (right) or deleting permanently (left), matching the task list swipe.
- add a left side bar with rounded ends (`bg_trash_stripe`) using the theme foreground color (`colorOnSurface`) to the trash cards in `item_trash.xml` so they are easier to tell apart.
- add `.docs/` to the `.gitignore` file so git stops tracking local project documentation.
- move `agent-docs/` into `.docs/` and keep the skills at the same level as `agent-docs`.
- create `claude.md` with the working context for claude code and a link to `agents.md`.
- add the commit writing section to `agents.md` and `claude.md`, mirroring `.docs/commit-guidelines.md`.
- add the section describing the format of `changelog.md` entries to `agents.md` and `claude.md`.

#### FIXED

- allow permanently deleting and emptying the trash without a linked account: the sync guard (`dirty = 0`) left items stuck with the `still waiting to sync with the cloud` warning when the app is purely local; now, if there is no session, the purge runs directly via `forcePermanentDelete` / `forceEmptyTrash`.

#### ADDED

- expand the icon picker in the list creation and editing forms with options for shopping, education, health, fitness, travel, finance, leisure, family, and home.
- create the `changelog.md` file to record project changes from now on.
