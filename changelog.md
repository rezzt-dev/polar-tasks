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
- redisenar la papelera con una tarjeta distinta para tareas (`item_trash_task.xml`: baldosa cuadrada con el icono de su lista, nombre de la lista, fecha y prioridad) y para recordatorios (`item_trash_reminder.xml`: baldosa circular tonal con campana, hora, fecha y ubicacion), agrupadas en secciones con contador, para que se sepa de un vistazo que se va a restaurar o eliminar; sustituye a `item_trash.xml` y `bg_trash_stripe`.
- anadir a cada tarjeta de la papelera un menu (al tocarla o con el boton de opciones) para restaurar o eliminar para siempre, etiquetas en el deslizamiento y mensajes que nombran el tipo de elemento.
- pedir confirmacion antes de eliminar para siempre un elemento de la papelera, tambien al deslizar, indicando si es una tarea o un recordatorio y su titulo.
- mover `agent-docs/` dentro de `.docs/` y dejar las skills al mismo nivel que `agent-docs`.
- crear `claude.md` con el contexto de trabajo para claude code y enlace a `agents.md`.
- anadir a `agents.md` y `claude.md` la seccion de redaccion de commits, replica de `.docs/commit-guidelines.md`.
- anadir a `agents.md` y `claude.md` la seccion con el formato de las entradas de `changelog.md`.
- documentar en `agents.md` (seccion 4.6) y `claude.md` la regla de que toda la interfaz se escribe en minusculas y en todos los idiomas.
- anadir `.docs/agent-docs/reglas-online-vs-offline.md` con las reglas obligatorias entre `polar-online/` y `polar-offline/` y renombrar `analisis-implementacion-supabase-sync.md` a `implementacion-supabase.md` dentro de `agent-docs/`.
- crear `.docs/pr-merge-release-guidelines.md` y replicar su contenido en `agents.md` y `claude.md` para que las pull requests, los commits de merge y las releases de github usen el mismo formato de categorias que `changelog.md`.
- centralizar en `AlarmRescheduler` la reprogramacion y cancelacion en bloque de alarmas de tareas, subtareas y recordatorios, y usarlo al reiniciar el dispositivo, al conceder el permiso de alarmas exactas y al restaurar una copia.
- unificar en `TaskFilter` las reglas de los filtros de hoy, pendientes, vencidas y recurrentes, que estaban duplicadas en `GetFilteredTasksUseCase` y `TaskViewModel`.
- calcular las estadisticas con una unica consulta agrupada en memoria (`CompletionHistory`) en lugar de una consulta por dia (unas 430 por carga).
- revisar en `RecurrenceWorker` solo las tareas recurrentes completadas en lugar de cargar todas las tareas cada hora.
- mover la importacion csv a `CsvImporter`: lectura fuera del hilo principal, una unica transaccion, soporte de comas, comillas y saltos de linea dentro de las celdas y aviso cuando el fichero no contiene tareas.
- refrescar el widget automaticamente cuando cambian las tareas (observador de room en `PolarApplication`) y al cambiar de dia.
- generar los textos de notificaciones y widget con el idioma elegido en la app (`ThemeManager.localizedContext()`).
- sustituir apis obsoletas (`systemUiVisibility`, `launchWhenStarted`, `announceForAccessibility`, `adapterPosition`, `scaledDensity` y el constructor de `Locale`) y eliminar los avisos de compilacion.
- anadir tests unitarios de `TaskFilter`, `CompletionHistory`, `RecurrenceUtils` y `CsvImporter`, y ampliar `TaskViewModelTest` con el orden de las tareas nuevas y las alarmas de subtareas.
- documentar en `agents.md` y `claude.md` el uso de `AlarmRescheduler`, el refresco del widget y la revision horaria de `RecurrenceWorker`.
- redisenar con el nuevo sistema todas las pantallas: inicio y listas, detalle de tarea, dialogos de tarea, lista, recordatorio y subtarea, calendario, recordatorios, estadisticas, ajustes, papelera, etiquetas, matriz de eisenhower, busqueda, tutorial, widget y tarjeta de exportacion como imagen.
- renovar la paleta de los temas claro y oscuro con grises neutros y definir en todos los temas los roles de color de material 3 que faltaban (`colorOnSurfaceVariant`, contenedores y superficies inversas), que antes caian en los morados por defecto.
- usar en el tema neon un rojo para los errores y la prioridad alta en lugar del verde lima.
- unificar en `TaskImageExporter` la exportacion de tareas como imagen, que estaba duplicada en la lista y en el detalle.
- revisar todos los textos de la interfaz en los cinco idiomas: tildes, `ñ`, signos de apertura, acentos franceses, un unico tono (tuteo, `du` y `vous`), tutorial traducido 1:1 y plurales correctos.
- generar las fechas con el formato propio de cada idioma (`getBestDateTimePattern`) y en minusculas.
- sustituir los atributos `paddingHorizontal` / `layout_marginHorizontal` (api 26) por sus equivalentes compatibles con android 7, donde se ignoraban.
- poner en minusculas el prefijo `error:` de los mensajes de error de los viewmodels.
- documentar el sistema de diseno en `agents.md` (seccion 4.7) y `claude.md`.
- aclarar el tema oscuro (fondo `#1C1C1F`) y llevar el tema onix a un gris neutro con base `#121212` para que los dos se distingan.
- centralizar en `ThemeManager.THEME_OPTIONS` la lista de temas con su estilo y si son oscuros, y mover el tema oscuro a `Base.Theme.Polar.Dark` para poder previsualizarlo desde cualquier tema.
- calcular la hora de la alarma de una tarea en un unico sitio (`TaskAlert` y `AlarmManagerHelper.syncTaskAlarm`) en lugar de repetirlo en viewmodels, workers y receptores, y reconstruir tambien los avisos por llegada en `AlarmRescheduler` al reiniciar el dispositivo y tras actualizar la app.
- compartir en `AlertActions` las acciones de completar y posponer de las notificaciones y de la hoja de posponer.
- pasar a `ReminderDialog` un callback `onSave` que recibe el `Reminder` editado y hacer que el formulario se desplace cuando no cabe en pantalla.
- [offline] subir la base de datos a la version 19 con `MIGRATION_18_19`, que anade `tasks.reminderOffset`, `tasks.snoozedUntil` y `reminders.notifyOnArrival`.
- [online] subir la base de datos a la version 18 con `MIGRATION_17_18`, que anade `tasks.reminderOffset`, `tasks.snoozedUntil` y `reminders.notifyOnArrival` e indexa `tasks.listId` y `subtasks.taskId` como en la version offline.
- [online] mantener el aviso relativo, el aviso pospuesto y el aviso al llegar como datos del dispositivo: no viajan a supabase, un pull o un conflicto perdido los conserva (`EntityMappers`) y un aviso pospuesto se descarta si otro dispositivo cambia la fecha de la tarea.

#### CORREGIDO

- corregir `SmartParser`: una fecha escrita en el titulo se ignoraba al editar una tarea que ya tenia fecha, y "a las 17" no se reconocia si antes habia otro numero ("comprar 2 panes a las 17").
- corregir el contraste del texto de los menus emergentes en todos los temas, alargar la barra lateral de prioridad a la altura del contenido y centrar verticalmente la casilla y el boton de opciones de las tareas en inicio y listas, sin linea separadora y con mas espacio entre la barra y la casilla.
- corregir las tareas recurrentes: al llegar el dia de la siguiente ocurrencia `RecurrenceWorker` reabre la tarea con nueva fecha de vencimiento y nueva fecha de creacion, desmarca sus subtareas y reprograma la alarma.
- corregir las tareas recurrentes completadas con retraso o tras varios dias sin abrir la app: se ajustan a la ocurrencia vigente (`RecurrenceUtils`) en lugar de reaparecer una a una con fechas ya pasadas.
- revisar las recurrentes cada hora y al abrir la app (antes cada 12 horas) para que reaparezcan al empezar el dia.
- exigir fecha de vencimiento al elegir una repeticion en `TaskDialog` (tambien si se detecta en el titulo); sin fecha la tarea nunca se repetia.
- aplicar al completar una tarea desde la accion de la notificacion la misma regla que desde la app: completar las subtareas y cancelar la alarma, recurrentes incluidas.
- no programar alarmas al editar una tarea completada y unificar `toggleTaskCompletion` con `setTaskCompletion` para eliminar logica duplicada.
- mostrar en el detalle de tarea la repeticion `lunes y miercoles` y `primer dia del mes`, que aparecia vacia.
- corregir los errores de lint al completar las traducciones, aplicar tintes compatibles en recordatorios y aislar los atributos de navegacion de api 27 en temas especificos.
- pasar a minusculas todos los textos de interfaz de recordatorios y ajustes (navegacion de ajustes, detalle de tarea, dialogos y agenda de recordatorios) en `values`, `values-en-rGB`, `values-en-rUS`, `values-de` y `values-fr` para respetar la convencion de minusculas de toda la app.
- [offline] ignorar en `RecurrenceWorker` las tareas recurrentes que estan en la papelera: se reactivaban y programaban alarmas de tareas ya borradas.
- [offline] actualizar a `v1.7` la version que muestra el pie de los ajustes (`developed_by_full`), que seguia indicando `v1.5`, en todos los idiomas.
- [online] permitir eliminar permanentemente y vaciar la papelera sin cuenta vinculada: el guardia de sincronizacion (`dirty = 0`) dejaba los elementos atascados con el aviso `still waiting to sync with the cloud` cuando la app es puramente local; ahora, si no hay sesion, se purga directamente con `forcePermanentDelete` / `forceEmptyTrash`.
- no reprogramar al reiniciar el dispositivo las alarmas de tareas que estan en la papelera, y reprogramar tambien las de las subtareas.
- no notificar subtareas cuya tarea esta completada o en la papelera, y abrir su tarea desde la notificacion de una subtarea sin las acciones de completar y posponer, que apuntaban a una tarea inexistente.
- cancelar el aviso de una subtarea al quitarle la fecha, completarla o eliminarla, y cancelar la alarma anterior al programar una con fecha ya pasada en vez de dejarla sonando.
- cancelar al restaurar una copia las alarmas que apuntaban a ids de otras tareas y programar las de los datos restaurados.
- evitar que falle por valores nulos la restauracion de copias antiguas a las que les faltan campos.
- anadir las listas y tareas nuevas al final (`orderIndex`) en vez de con indice 0, que en las cadenas de dependencia colocaba el paso nuevo el primero.
- actualizar al momento el detalle de tarea al completarla, editarla o adjuntar una imagen, y evitar que desmarcar desde el detalle una tarea recien completada la vuelva a completar.
- usar el selector de documentos al adjuntar una imagen en el detalle de tarea, que cerraba la app (`takePersistableUriPermission` con `GetContent`), y ocultar la imagen si ya no se puede leer.
- ignorar las acciones completar y posponer de una notificacion si la tarea ya se completo o se envio a la papelera.
- corregir el contador de la papelera del menu lateral, que siempre mostraba 0.
- conservar la lista abierta, el titulo y el boton de anadir al recrear la pantalla principal (girar el dispositivo, cambiar tema o idioma, restaurar una copia).
- abrir la tarea al tocarla en el widget, refrescarlo solo y traducir sus textos, que estaban fijos en ingles.
- traducir los textos de notificaciones, del canal y de la confirmacion de recordatorio, que estaban fijos en castellano, y mostrar el titulo del recordatorio como texto en lugar de interpretarlo como html.
- formatear en el idioma de la app las fechas del detalle, de las tarjetas, de la exportacion como imagen y de las estadisticas, que estaban fijas en castellano (`Locale("es")` y el literal `de`).
- buscar `%` y `_` de forma literal, que devolvian cualquier tarea.
- evitar que el lector de pantalla anuncie la ordenacion cada vez que se abre una lista.
- evitar que la app se cierre al salir de ajustes mientras se restaura una copia.
- [online] restaurar la copia en una unica transaccion (si fallaba a mitad se perdian los datos) y exportar tambien las listas borradas, sin las cuales la copia no se podia restaurar.
- [online] cancelar las alarmas de las tareas al eliminar su lista.
- traducir los textos que estaban fijos en castellano en el cajon lateral, la matriz de eisenhower, las etiquetas, el calendario, las subtareas, el dialogo de recordatorio, el menu de tarea y la copia al portapapeles.
- mostrar solo editar y eliminar en el menu de un recordatorio, que ofrecia acciones de tareas que no hacian nada.
- mostrar la fecha de las tareas en la hoja de una etiqueta, que quedaba oculta.
- evitar que los iconos de las listas con un color de poco contraste (p. ej. blanco) desaparezcan en los temas claros.
- respetar la fuente elegida en las graficas de estadisticas y en el calendario, que usaban la del sistema.
- respetar el formato de 12 / 24 h del sistema al elegir la hora de una subtarea.
- usar plurales en el analisis semanal de estadisticas (decia `1 tareas`).
- [online] mostrar `v1.7` en el pie de ajustes, que seguia indicando `v1.5`.
- centrar verticalmente la casilla, el icono de lista y sus textos en el detalle de tarea, eliminando el espacio tipografico que los desalineaba.
- posponer desde la notificacion ya no reescribe la fecha de vencimiento de la tarea, que se perdia al posponer.
- abrir la ubicacion de un recordatorio con cualquier app de mapas instalada y no solo con google maps.
- traducir el aviso de titulo obligatorio del formulario de recordatorio, que estaba fijo en castellano.

#### NUEVO

- ampliar el selector de iconos de los formularios de creacion y edicion de listas con opciones para compras, estudios, salud, deporte, viajes, finanzas, ocio, familia y hogar.
- crear el fichero `changelog.md` para registrar los cambios del proyecto de aqui en adelante.
- anadir un sistema de diseno comun (`ThemeOverlay.Polar.Foundation`) con escala tipografica, formas, estilos de componentes y animaciones, que `BaseActivity` aplica sobre cualquier tema.
- incluir la fuente inter en la app (`res/font/`, sin depender de la red) y usarla como tipografia por defecto, manteniendo el resto de opciones.
- sustituir todos los iconos por el pack lucide (trazo fino y uniforme) conservando los nombres `ic_*`, de los que dependen los iconos de lista guardados en la base de datos.
- anadir transiciones suaves al cambiar de seccion (fade through), al abrir y cerrar pantallas, al cargar listas y al cambiar de categoria en ajustes, y respuesta haptica al completar tareas y al cruzar el umbral de un deslizamiento.
- marcar en el cajon lateral la seccion o lista abierta, mostrar la fecha de hoy y mover estadisticas y ajustes al pie del cajon para dejar la barra superior solo con la busqueda.
- mostrar en inicio un resumen de tareas pendientes y vencidas bajo el saludo, y en cada lista el progreso con texto y porcentaje.
- anadir una paleta de colores predefinida y un selector de color personalizado plegable al crear o editar listas.
- mostrar las etiquetas del formulario de tarea como chips con boton de quitar, admitir varias separadas por comas y anadir etiquetas y subtareas con la tecla de confirmar del teclado.
- abrir el detalle al tocar un resultado de busqueda y mostrar el numero de resultados.
- anadir iconos a los menus emergentes y destacar en rojo la accion de eliminar.
- anadir `THIRD_PARTY_NOTICES.md` con las licencias de inter (sil ofl 1.1) y lucide (isc).
- redisenar el selector de tema como una hoja inferior (`ThemePickerDialog`) con una vista previa de cada tema pintada con sus propios colores, agrupada en temas claros y oscuros, y mostrar en ajustes una muestra de color del tema activo.
- anadir tres temas claros (arena, salvia y cerezo) y tres oscuros (bosque, amatista y brasa).
- ampliar `SmartParser` a los cinco idiomas de la app (castellano, ingles, frances y aleman) con dias de la semana ("el viernes"), fechas relativas ("en 3 dias", "la proxima semana", "este fin de semana"), repeticiones "los lunes y miercoles" y "el primer dia de cada mes", prioridad (`!`, `!!`, `!!!`, `!alta`, "prioridad alta") y lista (`@nombre`); el vocabulario de cada idioma vive en `SmartParserLexicon`.
- analizar el titulo del formulario de tarea mientras se escribe para que las filas de fecha, repeticion, prioridad y la nueva fila de lista muestren lo detectado, con un ejemplo de uso al crear y un aviso si `@nombre` no coincide con ninguna lista; lo elegido a mano sigue teniendo preferencia.
- permitir mover una tarea a otra lista desde su formulario, colocandola al final de la lista de destino.
- anadir repeticion personalizada con la opcion "personalizar…" del formulario de tarea, que abre una hoja inferior (`RecurrenceDialog`) para repetir cada n dias, semanas, meses o anos, en dias concretos de la semana, el mismo dia, el primero o el ultimo del mes, y contando desde su fecha o desde el dia en que se completa ("3 dias despues de completarla"), con un resumen y las proximas fechas en vivo.
- guardar las reglas personalizadas en `tasks.recurrence` como un subconjunto de rrule (`RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE`) leido y escrito por `RecurrenceRule`, conservando los valores fijos de siempre para las reglas equivalentes, de modo que no hace falta migracion de base de datos y las copias anteriores siguen siendo validas.
- reconocer en `SmartParser` "cada 2 semanas", "cada tres dias", "cada 6 meses" o "cada 2 anos" (y "cada 2 semanas los lunes") en los cuatro idiomas.
- mostrar las reglas personalizadas en el formulario y en el detalle de la tarea con `RecurrenceFormatter` y hacer que una tarea con dias concretos empiece en el primero de ellos.
- [online] permitir los valores `RRULE:` en el `CHECK` de `tasks.recurrence` de supabase (migracion sql en `03-esquema-supabase.md`), necesario antes de publicar para que la subida de tareas no falle.
- anadir "posponer" con eleccion a las notificaciones de tareas y recordatorios: abre sobre la pantalla actual, sin entrar en la app (`SnoozeActivity`), una hoja inferior (`SnoozeSheet`) con 15 minutos, 1 hora, 3 horas, esta tarde, manana a la hora de aviso por defecto o una fecha y hora a medida, y muestra la hora resultante de cada opcion.
- anadir la fila "aviso" al formulario de tarea para avisar a la hora del vencimiento, de 5 minutos a 1 semana antes o no avisar; la notificacion anticipada indica cuando vence la tarea.
- posponer el aviso de una tarea sin cambiar su fecha de vencimiento (`tasks.snoozedUntil`) y mostrar en el detalle el aviso elegido o hasta cuando esta pospuesto.
- anadir a los recordatorios el aviso al llegar a su lugar, ademas de a su hora, con un radio de 100 m a 1 km, usando las alertas de proximidad del sistema (`GeofenceHelper`) sin google play services ni red.
- pedir la ubicacion solo al activar el aviso al llegar, explicar antes el acceso "todo el tiempo" e indicar en el formulario y en la tarjeta del recordatorio si falta el permiso o la ubicacion del dispositivo esta desactivada.
- anadir "completar" y "posponer" a las notificaciones de recordatorios y "posponer" al menu de cada recordatorio pendiente, con deshacer.
- anadir `TaskAlertTest` y `SnoozeOptionTest`, casos de aviso relativo en `TaskViewModelTest` y de campos locales en `EntityMappersTest`, y tests de migracion de las columnas nuevas.

#### ELIMINADO

- eliminar el fichero obsoleto `readme_features.md`.
- [offline] eliminar la sincronizacion en la nube con supabase: `SyncManager`, `SyncWorker`, `SyncPrefs`, dtos, mappers, resolucion de conflictos, realtime y almacenamiento de imagenes en la nube.
- [offline] eliminar el inicio de sesion (`AuthActivity`, `AuthViewModel`, `activity_auth.xml`) y las categorias de ajustes de cuenta y de opciones avanzadas de nube, con sus textos en todos los idiomas.
- [offline] eliminar las dependencias de supabase, ktor y kotlinx serialization, los campos `SUPABASE_URL` / `SUPABASE_ANON_KEY` de `BuildConfig` y los tests y documentos de diseno de la sincronizacion.
- eliminar layouts, drawables, adapters y textos sin uso (`item_task_minimal.xml`, `item_task_group.xml`, `TagAdapter`, fondos y cadenas obsoletas).

#### SEGURIDAD

- [offline] quitar los permisos `INTERNET` y `ACCESS_NETWORK_STATE` que declaraba la app: polar no se conecta a ningun servidor ni envia datos fuera del dispositivo.
- declarar los permisos de ubicacion (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` y `ACCESS_BACKGROUND_LOCATION`), que solo se piden al activar el aviso al llegar de un recordatorio; la ubicacion nunca sale del dispositivo.

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
- redesign the trash with a different card for tasks (`item_trash_task.xml`: square tile with the list icon, list name, date and priority) and for reminders (`item_trash_reminder.xml`: round tonal tile with a bell, time, date and location), grouped in sections with a counter, so it is clear at a glance what is going to be restored or deleted; replaces `item_trash.xml` and `bg_trash_stripe`.
- add a menu to each trash card (on tap or from the options button) to restore or delete forever, labels on the swipe and messages that name the item type.
- ask for confirmation before deleting a trash item forever, also when swiping, stating whether it is a task or a reminder and its title.
- move `agent-docs/` into `.docs/` and keep the skills at the same level as `agent-docs`.
- create `claude.md` with the working context for claude code and a link to `agents.md`.
- add the commit writing section to `agents.md` and `claude.md`, mirroring `.docs/commit-guidelines.md`.
- add the section describing the format of `changelog.md` entries to `agents.md` and `claude.md`.
- document in `agents.md` (section 4.6) and `claude.md` the rule that the whole interface is written in lowercase in every language.
- add `.docs/agent-docs/reglas-online-vs-offline.md` with the mandatory rules between `polar-online/` and `polar-offline/`, and rename `analisis-implementacion-supabase-sync.md` to `implementacion-supabase.md` inside `agent-docs/`.
- create `.docs/pr-merge-release-guidelines.md` and mirror its content in `agents.md` and `claude.md` so pull requests, merge commits and github releases use the same category format as `changelog.md`.
- centralize in `AlarmRescheduler` the bulk rescheduling and cancelling of task, subtask and reminder alarms, and use it after a device reboot, when the exact alarm permission is granted and when restoring a backup.
- merge into `TaskFilter` the rules of the today, pending, overdue and recurring filters, which were duplicated in `GetFilteredTasksUseCase` and `TaskViewModel`.
- compute statistics with a single query grouped in memory (`CompletionHistory`) instead of one query per day (about 430 per load).
- check only completed recurring tasks in `RecurrenceWorker` instead of loading every task each hour.
- move csv import to `CsvImporter`: reading off the main thread, a single transaction, support for commas, quotes and line breaks inside cells, and a message when the file contains no tasks.
- refresh the widget automatically when tasks change (room observer in `PolarApplication`) and when the day changes.
- build notification and widget texts with the language chosen in the app (`ThemeManager.localizedContext()`).
- replace deprecated apis (`systemUiVisibility`, `launchWhenStarted`, `announceForAccessibility`, `adapterPosition`, `scaledDensity` and the `Locale` constructor) and remove the compiler warnings.
- add unit tests for `TaskFilter`, `CompletionHistory`, `RecurrenceUtils` and `CsvImporter`, and extend `TaskViewModelTest` with the order of new tasks and subtask alarms.
- document in `agents.md` and `claude.md` how to use `AlarmRescheduler`, the widget refresh and the hourly `RecurrenceWorker` check.
- redesign every screen with the new system: home and lists, task details, task, list, reminder and subtask dialogs, calendar, reminders, statistics, settings, bin, tags, eisenhower matrix, search, tutorial, widget and the share-as-image card.
- refresh the light and dark theme palette with neutral greys and define in every theme the missing material 3 colour roles (`colorOnSurfaceVariant`, containers and inverse surfaces), which used to fall back to the default purples.
- use red instead of lime green for errors and high priority in the neon theme.
- merge the share-as-image export, duplicated in the list and in the details screen, into `TaskImageExporter`.
- review every interface string in the five languages: accents, `ñ`, opening punctuation, french accents, a single tone (informal spanish, `du` and `vous`), a 1:1 translated tutorial and correct plurals.
- build dates with each language's own pattern (`getBestDateTimePattern`) and in lowercase.
- replace the `paddingHorizontal` / `layout_marginHorizontal` attributes (api 26) with their android 7 compatible equivalents, where they were ignored.
- lowercase the `error:` prefix of the viewmodel error messages.
- document the design system in `agents.md` (section 4.7) and `claude.md`.
- lighten the dark theme (`#1C1C1F` background) and move the onyx theme to a neutral grey based on `#121212` so the two are easy to tell apart.
- centralise the theme list with each style and whether it is dark in `ThemeManager.THEME_OPTIONS`, and move the dark theme to `Base.Theme.Polar.Dark` so it can be previewed from any theme.
- compute a task's alarm time in a single place (`TaskAlert` and `AlarmManagerHelper.syncTaskAlarm`) instead of repeating it in viewmodels, workers and receivers, and also rebuild arrival alerts in `AlarmRescheduler` after a device restart and after an app update.
- share the complete and snooze actions of notifications and of the snooze sheet in `AlertActions`.
- give `ReminderDialog` an `onSave` callback that receives the edited `Reminder` and make the form scroll when it does not fit on screen.
- [offline] bump the database to version 19 with `MIGRATION_18_19`, which adds `tasks.reminderOffset`, `tasks.snoozedUntil` and `reminders.notifyOnArrival`.
- [online] bump the database to version 18 with `MIGRATION_17_18`, which adds `tasks.reminderOffset`, `tasks.snoozedUntil` and `reminders.notifyOnArrival` and indexes `tasks.listId` and `subtasks.taskId` as in the offline version.
- [online] keep the relative alert, the snoozed alert and the arrival alert as device data: they do not travel to supabase, a pull or a lost conflict keeps them (`EntityMappers`) and a snoozed alert is dropped if another device changes the task date.

#### FIXED

- fix `SmartParser`: a date written in the title was ignored when editing a task that already had a date, and "a las 17" was missed when another number came first ("comprar 2 panes a las 17").
- fix popup menu text contrast across all themes, extend the priority stripe to the content height and vertically center task checkboxes and overflow buttons on the home screen and in lists, without a divider line and with more space between the stripe and the checkbox.
- fix recurring tasks: when the day of the next occurrence arrives `RecurrenceWorker` reopens the task with a new due date and a new creation date, unchecks its subtasks and reschedules the alarm.
- fix recurring tasks completed late or after several days without opening the app: they are moved to the current occurrence (`RecurrenceUtils`) instead of reappearing one by one with past dates.
- check recurring tasks every hour and on app start (previously every 12 hours) so they reappear when the day begins.
- require a due date when choosing a repetition in `TaskDialog` (also when detected in the title); without one the task never repeated.
- apply the same rule when completing a task from the notification action as in the app: complete the subtasks and cancel the alarm, recurring tasks included.
- do not schedule alarms when editing a completed task and merge `toggleTaskCompletion` into `setTaskCompletion` to remove duplicated logic.
- show the `monday and wednesday` and `first day of the month` repetitions in the task detail, which were rendered empty.
- fix lint errors by completing translations, applying compatible reminder icon tints and isolating api 27 navigation attributes in version-specific themes.
- lowercase every reminders and settings interface string (settings navigation, task detail, dialogs and reminders agenda) in `values`, `values-en-rGB`, `values-en-rUS`, `values-de` and `values-fr` to follow the app-wide lowercase convention.
- [offline] skip trashed recurring tasks in `RecurrenceWorker`: they were reset and scheduled alarms for tasks the user had already deleted.
- [offline] update the version shown in the settings footer (`developed_by_full`) to `v1.7` in every language; it still read `v1.5`.
- [online] allow permanently deleting and emptying the trash without a linked account: the sync guard (`dirty = 0`) left items stuck with the `still waiting to sync with the cloud` warning when the app is purely local; now, if there is no session, the purge runs directly via `forcePermanentDelete` / `forceEmptyTrash`.
- do not reschedule after a device reboot the alarms of tasks in the trash, and reschedule subtask alarms too.
- do not notify subtasks whose task is completed or in the trash, and open their task from a subtask notification without the complete and snooze actions, which pointed to a nonexistent task.
- cancel a subtask alert when its date is cleared or it is completed or deleted, and cancel the previous alarm when scheduling one with a past date instead of leaving it ringing.
- cancel on backup restore the alarms that pointed to the ids of other tasks and schedule those of the restored data.
- prevent restoring old backups that lack some fields from failing because of null values.
- add new lists and tasks at the end (`orderIndex`) instead of with index 0, which put the new step first in dependency chains.
- update the task detail right away when the task is completed, edited or gets an image, and stop unchecking a just completed task from the detail from completing it again.
- use the document picker when attaching an image in the task detail, which crashed the app (`takePersistableUriPermission` with `GetContent`), and hide the image if it can no longer be read.
- ignore the complete and snooze notification actions if the task was already completed or moved to the trash.
- fix the trash counter in the side menu, which always showed 0.
- keep the open list, the title and the add button when the main screen is recreated (rotating the device, changing theme or language, restoring a backup).
- open the task when tapped in the widget, refresh it automatically and translate its texts, which were hardcoded in english.
- translate the notification, channel and reminder confirmation texts, which were hardcoded in spanish, and show the reminder title as plain text instead of parsing it as html.
- format the dates in the detail, cards, image export and statistics in the app language; they were hardcoded in spanish (`Locale("es")` and the literal `de`).
- search `%` and `_` literally; they returned every task.
- stop the screen reader from announcing the sort order every time a list is opened.
- prevent the app from crashing when leaving settings while a backup is being restored.
- [online] restore the backup in a single transaction (a failure halfway lost the data) and also export deleted lists, without which the backup could not be restored.
- [online] cancel the task alarms when their list is deleted.
- translate the strings that were hardcoded in spanish in the drawer, the eisenhower matrix, tags, calendar, subtasks, the reminder dialog, the task menu and the clipboard copy.
- show only edit and delete in a reminder's menu, which offered task actions that did nothing.
- show the task date in a tag's sheet, which stayed hidden.
- keep list icons with a low-contrast colour (e.g. white) from disappearing in light themes.
- respect the chosen font in the statistics charts and the calendar, which used the system font.
- respect the system 12 / 24 h format when picking a subtask time.
- use plurals in the weekly statistics insight (it said `1 tasks`).
- [online] show `v1.7` in the settings footer, which still said `v1.5`.
- vertically centre the checkbox, list icon and their labels in the task details, removing the font padding that misaligned them.
- snoozing from the notification no longer rewrites the task due date, which was lost when snoozing.
- open a reminder's location with any installed maps app, not only google maps.
- translate the required title warning of the reminder form, which was hardcoded in spanish.

#### ADDED

- expand the icon picker in the list creation and editing forms with options for shopping, education, health, fitness, travel, finance, leisure, family, and home.
- create the `changelog.md` file to record project changes from now on.
- add a shared design system (`ThemeOverlay.Polar.Foundation`) with a type scale, shapes, component styles and motion, applied by `BaseActivity` on top of any theme.
- bundle the inter font in the app (`res/font/`, no network needed) and use it as the default font, keeping the other options.
- replace every icon with the lucide pack (thin, consistent stroke) while keeping the `ic_*` names that list icons stored in the database rely on.
- add smooth transitions when switching sections (fade through), opening and closing screens, loading lists and changing settings categories, plus haptic feedback when completing tasks and crossing a swipe threshold.
- highlight the open section or list in the drawer, show today's date and move statistics and settings to the drawer footer so the top bar only holds search.
- show a summary of pending and overdue tasks under the home greeting, and each list's progress as text and percentage.
- add a preset colour palette and a collapsible custom colour picker when creating or editing lists.
- show tags in the task form as chips with a remove button, accept several separated by commas and add tags and subtasks with the keyboard's done key.
- open the details when tapping a search result and show the number of results.
- add icons to popup menus and highlight the delete action in red.
- add `THIRD_PARTY_NOTICES.md` with the inter (sil ofl 1.1) and lucide (isc) licences.
- redesign the theme picker as a bottom sheet (`ThemePickerDialog`) with a preview of each theme painted in its own colours, grouped into light and dark themes, and show a colour swatch of the active theme in settings.
- add three light themes (sand, sage and blossom) and three dark themes (forest, amethyst and ember).
- extend `SmartParser` to the app's five languages (spanish, english, french and german) with weekdays ("on friday"), relative dates ("in 3 days", "next week", "this weekend"), "every monday and wednesday" and "first day of every month" repeats, priority (`!`, `!!`, `!!!`, `!high`, "high priority") and list (`@name`); each language's vocabulary lives in `SmartParserLexicon`.
- parse the task form title while typing so the date, repeat, priority and new list rows show what was detected, with a usage example when creating and a warning when `@name` matches no list; manual choices still take precedence.
- allow moving a task to another list from its form, placing it at the end of the target list.
- add custom repeat through the "custom…" option of the task form, which opens a bottom sheet (`RecurrenceDialog`) to repeat every n days, weeks, months or years, on specific weekdays, on the same, first or last day of the month, and counting from its date or from the day it is completed ("3 days after completion"), with a live summary and upcoming dates.
- store custom rules in `tasks.recurrence` as an rrule subset (`RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE`) read and written by `RecurrenceRule`, keeping the usual fixed values for equivalent rules, so no database migration is needed and older backups stay valid.
- recognise "every 2 weeks", "every three days", "every 6 months" or "every 2 years" (and "every 2 weeks on mondays") in `SmartParser` in the four languages.
- show custom rules in the task form and details with `RecurrenceFormatter` and make a task with specific days start on the first of them.
- [online] allow `RRULE:` values in the supabase `tasks.recurrence` `CHECK` (sql migration in `03-esquema-supabase.md`), required before release so the task upload does not fail.
- add "snooze" with a choice to task and reminder notifications: it opens over the current screen, without entering the app (`SnoozeActivity`), a bottom sheet (`SnoozeSheet`) with 15 minutes, 1 hour, 3 hours, this evening, tomorrow at the default reminder time or a custom date and time, and shows the resulting time of each option.
- add the "reminder" row to the task form to remind at the due time, from 5 minutes to 1 week before or not at all; an early notification shows when the task is due.
- snooze a task's alert without changing its due date (`tasks.snoozedUntil`) and show in the details the chosen alert or until when it is snoozed.
- add an arrival alert to reminders, on top of their time, with a 100 m to 1 km radius, using the system proximity alerts (`GeofenceHelper`) without google play services or network.
- ask for location only when the arrival alert is turned on, explain "all the time" access first and show in the form and in the reminder card when the permission is missing or device location is off.
- add "complete" and "snooze" to reminder notifications and "snooze" to the menu of each pending reminder, with undo.
- add `TaskAlertTest` and `SnoozeOptionTest`, relative alert cases in `TaskViewModelTest` and device-only field cases in `EntityMappersTest`, and migration tests for the new columns.

#### REMOVED

- remove the obsolete `readme_features.md` file.
- [offline] remove supabase cloud sync: `SyncManager`, `SyncWorker`, `SyncPrefs`, dtos, mappers, conflict resolution, realtime and cloud image storage.
- [offline] remove sign-in (`AuthActivity`, `AuthViewModel`, `activity_auth.xml`) and the account and advanced cloud settings categories, with their strings in every language.
- [offline] remove the supabase, ktor and kotlinx serialization dependencies, the `SUPABASE_URL` / `SUPABASE_ANON_KEY` `BuildConfig` fields and the sync tests and design docs.
- remove unused layouts, drawables, adapters and strings (`item_task_minimal.xml`, `item_task_group.xml`, `TagAdapter`, obsolete backgrounds and strings).

#### SECURITY

- [offline] drop the `INTERNET` and `ACCESS_NETWORK_STATE` permissions declared by the app: polar never connects to a server or sends data off the device.
- declare the location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` and `ACCESS_BACKGROUND_LOCATION`), which are only requested when a reminder's arrival alert is turned on; location never leaves the device.
