# ANALISIS DE FUNCIONALIDADES

> analisis de producto de **polar** (`polar-offline/` y `polar-online/`) sobre
> que funcionalidades faltan o estan a medias, y cuales merece la pena
> implementar sin convertir la app en un frankenstein. esta pensado para el
> propietario del proyecto y para cualquier agente de ia que vaya a implementar
> alguna de ellas. no es un plan cerrado: son opciones, con sus pros, costes y
> riesgos, vistas desde varios angulos.
>
> estado de referencia: rama `version-1.7`, base de datos room v18 (offline),
> fecha 2026-10-27. todo lo que se afirma sobre el codigo se ha comprobado en
> `polar-offline/` salvo que se diga lo contrario.

---

## RESUMEN: FUNCIONALIDADES CANDIDATAS

polar ya cubre bien la base (listas, tareas, subtareas, etiquetas, prioridad,
recurrencia, recordatorios con alarma exacta, calendario, estadisticas, matriz
de eisenhower, papelera, widget, 13 temas, 5 idiomas, copia json e importacion
csv). lo que le falta no son mas pantallas, sino **cerrar el ciclo del dia a
dia** (capturar rapido, decidir que hacer hoy, hacerlo, revisar) y **explotar
datos que ya guarda y no aprovecha** (`timeEstimate`, coordenadas de los
recordatorios, `isDependencyChain`). las funcionalidades candidatas son (la
etiqueta indica en que version aplican y entre parentesis la recomendacion
final de la seccion 7):

- **F01 · captura rapida desde fuera de la app** `[ambas]` (hacer): tile de
  ajustes rapidos, atajo en el launcher y destino de "compartir" para crear una
  tarea sin abrir polar. reduce la friccion de captura, que es donde mas tareas
  se pierden.
- **F02 · parser inteligente ampliado y multilingue** `[ambas]` (hacer): hoy
  `SmartParser` solo entiende castellano y no reconoce "el viernes" ni "en 3
  dias". anadir dias de la semana, fechas relativas, prioridad y lista, en los
  cinco idiomas de la app.
- **F03 · plan de hoy con carga de tiempo** `[ambas]` (hacer): usar
  `timeEstimate` (hoy solo se muestra) para sumar la carga del dia, fijar una
  capacidad diaria y avisar de sobrecarga. convierte "hoy" en un plan realista.
- **F04 · modo enfoque con temporizador** `[ambas]` (valorar): temporizador
  (pomodoro) ligado a una tarea, que registra tiempo real frente a estimado.
  util, pero es la que mas empuja hacia "app de productividad total".
- **F05 · repeticion personalizada** `[ambas]` (hacer): "cada 2 semanas", dias
  concretos, "N dias despues de completar". hoy solo hay 6 reglas fijas en
  `RecurrenceUtils` (`DAILY`, `WEEKLY`, `MONTHLY`, `MON_WED`, `FIRST_DAY_MONTH`).
- **F06 · recordatorios mas completos** `[ambas]` (hacer parte): posponer con
  eleccion (hoy fijo a 1 hora), aviso relativo a la fecha de la tarea ("1 dia
  antes") y, como extra opcional, aviso por llegada a un lugar (geofence) usando
  las coordenadas que `Reminder` ya guarda.
- **F07 · plantillas de tarea y de lista** `[ambas]` (hacer): guardar una tarea
  o lista con sus subtareas como plantilla ("viaje", "mudanza", "revision
  semanal") y crearla de un toque.
- **F08 · busqueda avanzada y vistas guardadas** `[ambas]` (hacer): filtros
  combinables (etiqueta, prioridad, lista, rango de fechas) y guardarlos como
  vista en el menu lateral. hoy la busqueda es solo texto (`LIKE`).
- **F09 · copias automaticas programadas** `[ambas, prioridad en offline]`
  (hacer): copia periodica a una carpeta elegida (SAF) con rotacion de las
  ultimas n. en offline la copia manual es la unica red de seguridad del usuario.
- **F10 · interoperabilidad con calendarios (`.ics`)** `[ambas]` (valorar):
  exportar tareas y recordatorios a `.ics` y, opcionalmente, mostrar eventos del
  calendario del sistema en la vista de calendario. sin cuentas ni red.
- **F11 · revision semanal e insights accionables** `[ambas]` (hacer): resumen
  semanal con lo completado, lo que se arrastra de semana en semana y una
  propuesta de replanificacion. aprovecha `CompletionHistory` y estadisticas.
- **F12 · notas enriquecidas y adjuntos multiples** `[ambas]` (descartar por
  ahora): markdown ligero en la descripcion y varias imagenes/archivos por
  tarea. hoy solo hay una imagen (`imageUri`). coste alto, retorno bajo.
- **F13 · widgets adicionales y configurables** `[ambas]` (hacer): widget por
  lista, widget de entrada rapida y widget de calendario/semana. hoy solo hay el
  de "tareas de hoy".
- **F14 · listas compartidas** `[online]` (valorar): compartir una lista con
  otra cuenta, con permisos de lectura o edicion. es la unica razon fuerte para
  que exista la version online frente a la offline.
- **F15 · sincronizacion fiable y visible** `[online]` (hacer antes que F14):
  registro y recuperacion de contrasena, sincronizar al abrir, indicador de
  estado y resolucion de conflictos visible. es deuda de la v1.6 (ver
  `implementacion-supabase.md`), no una funcionalidad nueva.
- **F16 · sincronizacion sin servidor** `[offline]` (investigar): exportacion
  incremental a una carpeta compartida (compatible con syncthing o la nube que
  el usuario ya use) sin que polar toque la red. mantiene la promesa "100%
  offline" y cubre el caso multidispositivo.

---

## 1. DIAGNOSTICO DEL ESTADO ACTUAL

### 1.1 que hay hoy (comprobado en el codigo)

| area | estado actual |
|---|---|
| modelo | `Task` (titulo, descripcion, etiquetas en cadena `,`, fecha, prioridad 0-3, recurrencia, `timeEstimate`, una imagen), `Subtask` (un solo nivel, con fecha propia), `TaskList` (icono, color, modo cadena de dependencias), `Reminder` (independiente de las tareas, con ubicacion). |
| captura | dialogo de tarea con `SmartParser` (etiquetas `#x`, fechas hoy/manana/pasado manana, hora, recurrencia) e importacion csv. |
| organizacion | listas, pantalla de inicio agrupada, etiquetas, matriz de eisenhower, calendario mensual, busqueda de texto, orden manual por arrastre, 2 modos de orden. |
| avisos | alarmas exactas, acciones completar y posponer 1 hora en la notificacion, aviso diario, reprogramacion tras reinicio (`AlarmRescheduler`). |
| revision | estadisticas (racha, mejor dia, tendencia 30 dias, por prioridad y por lista), papelera con deshacer. |
| personalizacion | 13 temas, tipografia, tamano de texto, estilo de casillas, 5 idiomas, widget de hoy. |
| datos | copia json manual, importacion csv, exportar tarea como texto o imagen. |

### 1.2 huecos y datos infrautilizados

estos hallazgos son la base de casi todas las propuestas; no son opiniones,
son cosas verificables en el codigo:

1. **`timeEstimate` se guarda y se pinta, pero no se usa para nada mas.** no
   hay total del dia, ni capacidad, ni comparacion con la realidad. es el dato
   mas desaprovechado de la app.
2. **los recordatorios guardan coordenadas y nombre de lugar**
   (`latitude`, `longitude`, `radius`, `locationName`), pero no hay geofence ni
   permiso de ubicacion: la notificacion solo abre un enlace `geo:`. el campo
   `radius` existe y no se usa para disparar nada.
3. **`SmartParser` solo funciona en castellano** (las expresiones regulares son
   `hoy`, `manana`, `cada lunes`...) aunque la app se traduce a cinco idiomas.
   ademas `cada lunes` fija la recurrencia semanal pero no la fecha, y no
   existen `el viernes`, `en 3 dias` ni `proxima semana`.
4. **las subtareas son planas.** `Subtask` solo tiene `taskId`, sin padre. la
   documentacion habla de "subtareas anidadas" (`AGENTS.md`, `README.md`), pero
   el modelo real es un solo nivel. es un desajuste de documentacion, no un bug.
5. **posponer es siempre +1 hora** (`NotificationActionReceiver`) y reescribe
   `dueDate`, lo que pierde la fecha original de la tarea.
6. **la recurrencia es un conjunto cerrado.** `MON_WED` y `FIRST_DAY_MONTH`
   existen en `RecurrenceUtils`, pero `AGENTS.md` solo documenta cuatro valores;
   no hay forma de definir "cada 2 semanas" o "los martes y jueves".
7. **solo hay dos modos de orden** (`SortMode.DEFAULT`, `UNMARK_FIRST`): no hay
   orden por fecha, prioridad ni etiqueta.
8. **la unica red de seguridad de datos es manual.** copia json bajo demanda;
   `allowBackup` esta activo, pero depende del backup del sistema y no hay
   copia programada.
9. **sin puntos de entrada externos.** no hay tile, atajo del launcher ni
   destino de "compartir"; para anotar una tarea hay que abrir la app y pulsar +.
10. **un solo widget** (tareas de hoy, sin configuracion).
11. **en la version online**, el informe `implementacion-supabase.md` ya listaba
    carencias estructurales (registro, sync al abrir, indicador de estado,
    realtime incompleto). antes de anadir funcionalidades de nube hay que
    cerrar eso.

---

## 2. PRISMA 1: EL USUARIO EN UN DIA NORMAL

pregunta guia: *donde se le escapa una tarea a alguien que usa polar de la
manana a la noche?* el ciclo es **capturar → decidir → hacer → revisar**.

| momento | lo que polar ofrece | lo que falta |
|---|---|---|
| capturar | dialogo con parser | acceder sin abrir la app (F01); que el parser entienda mas frases y su idioma (F02) |
| decidir | inicio, filtros, eisenhower | saber si el dia es realista (F03); plantillas para no empezar de cero (F07) |
| hacer | detalle de tarea, subtareas | enfocar una sola cosa (F04) |
| recordar | alarmas, snooze fijo | posponer a medida, avisos relativos, aviso por lugar (F06) |
| revisar | estadisticas | revision semanal accionable, no solo graficos (F11) |

**conclusion del prisma:** el mayor retorno esta en el principio y el final
del ciclo (capturar y revisar). son las dos puntas donde la mayoria de apps de
tareas pierden al usuario: si anotar cuesta, no se anota; si revisar no ayuda,
se abandona.

---

## 3. PRISMA 2: EL USUARIO AVANZADO (POWER USER)

pregunta guia: *que le haria dejar otra app (todoist, ticktick, things) por
polar sin que polar deje de ser simple?*

- **repeticion personalizada (F05)** es la carencia mas citada en cualquier
  comparativa; sin ella, el usuario avanzado no puede migrar sus rutinas.
- **vistas guardadas (F08)** son la forma elegante de dar potencia sin anadir
  pantallas: un filtro combinado se guarda y aparece como una entrada mas del
  menu lateral. escala sin cargar la interfaz.
- **plantillas (F07)** sirven tanto al power user como al ocasional.
- **ordenar por fecha/prioridad/etiqueta** es una mejora pequena de
  `SortMode` con mucho impacto: bajo coste, riesgo casi nulo.
- **lo que no hay que copiar** de esas apps: proyectos con secciones,
  tableros kanban, objetivos, karma/gamificacion, ia generativa. son
  precisamente las cosas que convierten una app en un frankenstein.

---

## 4. PRISMA 3: COHERENCIA DE PRODUCTO (ANTI-FRANKENSTEIN)

pregunta guia: *esta funcionalidad se parece a polar o la desfigura?*

el producto se define hoy por tres rasgos: **minimalista, sin distracciones,
privado y offline**. cada propuesta se puntua contra ellos:

| criterio | cumple si... |
|---|---|
| minimalismo | no anade una pantalla de primer nivel; vive en una pantalla existente, en un dialogo o en un ajuste |
| datos propios | usa datos que ya existen o amplia el modelo con una columna, no con una tabla nueva |
| privacidad | funciona en modo avion y no pide permisos que no se justifiquen |
| mantenimiento | no obliga a mantener dos veces el codigo (regla de `reglas-online-vs-offline.md`) |

reglas practicas para decidir:

1. **maximo una pantalla de primer nivel nueva por version**, y solo si
   reemplaza o absorbe otra. el menu lateral ya tiene inicio, calendario,
   recordatorios, estadisticas, etiquetas, eisenhower, papelera y ajustes.
2. **prefiere extender antes que anadir:** F03 extiende "hoy"; F08 extiende el
   menu lateral; F11 extiende estadisticas. F04 y F12 anaden superficie nueva.
3. **un permiso nuevo exige un beneficio visible** (geofence pide ubicacion en
   segundo plano: solo si F06 se usa de verdad).
4. **cada ajuste nuevo cuesta:** si una funcionalidad necesita mas de dos
   ajustes, probablemente esta mal disenada.

resultado de aplicar el filtro: F01, F02, F03, F05, F07, F08, F09, F11 y F13
**encajan**; F06 (geofence), F10 y F04 **encajan con condiciones**; F12
**desfigura** y se descarta por ahora.

---

## 5. PRISMA 4: LAS DOS VERSIONES (ONLINE Y OFFLINE)

pregunta guia: *que aporta cada version y que no debe divergir?* la regla es
la de `reglas-online-vs-offline.md`: **todo lo que no necesite cuenta ni red
se implementa en las dos**. aplicado a las propuestas:

| grupo | funcionalidades | version | nota |
|---|---|---|---|
| comun (sin red) | F01, F02, F03, F04, F05, F06 (sin geofence o con), F07, F08, F09, F10, F11, F13 | ambas, misma sesion de trabajo | se implementan en `polar-offline/` y se portan a `polar-online/` (o al reves) segun la regla central |
| exclusivo online | F14 listas compartidas, F15 sincronizacion fiable | solo `polar-online/` | dependen de cuenta y backend |
| exclusivo offline | F16 sincronizacion sin servidor | solo `polar-offline/` | no puede usar red; reutiliza el formato json de `BackupManager` |

puntos delicados:

- **F09 (copias automaticas)** es mas valiosa en offline, porque no hay nube
  que haga de respaldo. en online la nube cubre parcialmente el mismo riesgo,
  pero la copia local sigue siendo util ante una cuenta bloqueada.
- **F07 (plantillas) y F08 (vistas guardadas)** anaden datos nuevos. en
  online, si deben sincronizarse, requieren columnas y dto nuevos
  (`data/sync/dto/`) y cambios en el contrato de interoperabilidad con la app
  externa; si se deciden **locales por dispositivo**, no tocan el contrato y
  son baratas. recomendacion: empezar locales.
- **F14 (compartir)** obliga a rehacer el modelo de permisos (rls en postgres)
  y la resolucion de conflictos entre usuarios; hoy el diseno es de un solo
  propietario por fila. es la funcionalidad mas cara de todo el documento.
- **F16** es la alternativa offline a F14 para el caso "dos dispositivos mios",
  no para colaborar con otras personas.
- **ambito de las claves de ajustes y migraciones room:** cualquier columna
  nueva sube la version de la base de datos (v18 en offline) con `Migration`
  manual, y en online hay que coordinar con la migracion que anade las columnas
  de sync. numerar las migraciones de cada proyecto por separado.

---

## 6. PRISMA 5: TECNICO (COSTE, RIESGO Y DEUDA)

pregunta guia: *que cuesta de verdad cada propuesta en este codigo?*

| id | cambio principal | toca room | toca manifiesto/permisos | tests nuevos | coste | riesgo |
|---|---|---|---|---|---|---|
| F01 | `TileService`, shortcut estatico, `intent-filter` `SEND` | no | no | bajo | S | bajo |
| F02 | reescribir `SmartParser` con tablas por idioma | no | no | **alto** (`SmartParserTest`) | M | medio: regresiones de fecha |
| F03 | agregados en `TaskFilter`/vista "hoy" + 1 ajuste de capacidad | no | no | medio | S-M | bajo |
| F04 | servicio en primer plano, notificacion de progreso | si (tiempo real) | `FOREGROUND_SERVICE` | medio | M-L | medio |
| F05 | ampliar `RecurrenceUtils` y selector en `TaskDialog` | si (regla estructurada) | no | **alto** (`RecurrenceUtilsTest`) | M | **alto**: hay logica delicada de ocurrencias |
| F06 | acciones de snooze, aviso relativo; geofence opcional | opcional | geofence: ubicacion | medio | M (L con geofence) | medio-alto con geofence |
| F07 | tabla `templates` o json en preferencias | si | no | bajo | M | bajo |
| F08 | consulta dinamica en `TaskDao`, tabla de vistas | si | no | medio | M | bajo |
| F09 | `WorkManager` periodico + SAF + rotacion | no | no | bajo | S-M | bajo |
| F10 | generar `.ics`; lectura del calendario del sistema | no | `READ_CALENDAR` (solo la lectura) | bajo | S (L la lectura) | bajo |
| F11 | pantalla/seccion de revision sobre `CompletionHistory` | no | no | medio | M | bajo |
| F12 | markdown y adjuntos multiples | si | permisos de archivos | alto | L | medio |
| F13 | nuevos `AppWidgetProvider` + actividad de configuracion | no | no | bajo | M | bajo |
| F14 | rls, invitaciones, realtime | **backend** | no | alto | XL | alto |
| F15 | sign-up, sync al abrir, estado | si (poco) | no | alto | L | medio |
| F16 | export incremental + fusion | si | SAF | alto | L | alto (conflictos) |

observaciones de deuda que condicionan el orden:

- **cobertura de tests:** hay pruebas de `TaskFilter`, `CompletionHistory`,
  `RecurrenceUtils`, `CsvImporter`, `SmartParser` y `TaskViewModel`. F02 y F05
  tocan exactamente los modulos mas delicados; **escribir los tests antes de
  cambiar el codigo** es obligatorio, no opcional.
- **`TaskViewModel` es grande** (mas de 440 lineas) y cada propuesta de las
  anteriores le anadiria metodos. conviene extraer casos de uso (ya existe
  `GetFilteredTasksUseCase`) antes de F03 y F08.
- **documentacion desfasada:** `AGENTS.md` describe una app unica,
  sin red y con cuatro valores de recurrencia. conviene corregirla junto con
  la primera funcionalidad que se implemente (y replicar en `CLAUDE.md`).

---

## 7. SINTESIS: OPCIONES DE HOJA DE RUTA

en lugar de una unica recomendacion, tres rutas segun lo que se quiera
priorizar. todas empiezan por la misma base comun.

### 7.1 base comun (cualquier ruta)

1. **tests antes de tocar la logica delicada:** ampliar `SmartParserTest` y
   `RecurrenceUtilsTest` con los casos de F02 y F05 antes de modificarlos.
2. **F09 copias automaticas:** bajo coste, elimina el mayor riesgo para el
   usuario (perder datos) y no cambia la interfaz.
3. **orden por fecha/prioridad** en `SortMode`: mejora pequena, visible, sin
   riesgo.
4. **corregir `AGENTS.md` / `CLAUDE.md`:** subtareas planas, recurrencias
   reales, estructura online/offline.

### 7.2 ruta A · "el dia a dia" (recomendada)

objetivo: que polar sea la app que abres menos tiempo, porque captura y
planifica mejor.

| orden | funcionalidades | por que |
|---|---|---|
| 1 | F01 captura rapida, F02 parser ampliado | atacan el origen de las tareas perdidas |
| 2 | F03 plan de hoy con carga, F06 (snooze elegible y aviso relativo) | convierten datos que ya existen en decisiones |
| 3 | F05 repeticion personalizada, F07 plantillas | lo que pide quien tiene rutinas |
| 4 | F11 revision semanal, F13 widgets | cierre del ciclo y presencia en el launcher |

es la ruta con mejor relacion valor/frankenstein: casi todo extiende pantallas
existentes y no pide permisos nuevos.

### 7.3 ruta B · "potencia sin ruido" (usuario avanzado)

objetivo: ganar al usuario que hoy usa una app mas compleja.

| orden | funcionalidades |
|---|---|
| 1 | F05 repeticion personalizada, F08 busqueda avanzada y vistas guardadas |
| 2 | F07 plantillas, orden ampliado |
| 3 | F10 `.ics`, F13 widgets configurables |
| 4 | F04 modo enfoque (solo si hay demanda real) |

riesgo: es la ruta que mas se parece a "mas funciones". hay que vigilar que
cada pieza viva dentro de pantallas existentes (ver seccion 4).

### 7.4 ruta C · "la nube que justifique existir" (online)

objetivo: dar a la version online algo que la offline no pueda tener.

| orden | funcionalidades |
|---|---|
| 1 | F15 sincronizacion fiable y visible (deuda de v1.6; sin esto, nada mas) |
| 2 | F08 y F07 sincronizados entre dispositivos |
| 3 | F14 listas compartidas |

riesgo: es la ruta mas cara y la que menos beneficia a la version offline,
que es la que se distribuye como "polar". solo tiene sentido si la version
online va a tener usuarios propios.

### 7.5 que no implementar (y por que)

| idea | motivo para descartarla |
|---|---|
| gamificacion (puntos, niveles, insignias) | choca con "sin distracciones"; la racha actual ya cubre la motivacion basica |
| ia generativa / sugerencias en la nube | rompe "100% offline" y la privacidad; el parser local cubre el 90% |
| tableros kanban y proyectos con secciones | duplica listas y eisenhower; desfigura la navegacion |
| subtareas con muchos niveles | complica modelo, swipe, alarmas y sync para un uso minoritario; las plantillas (F07) cubren el caso real |
| equipos, asignacion y comentarios | es otro producto; F14 ya es el limite razonable |
| notas enriquecidas / editor completo (F12) | coste alto, usuarios que lo necesitan ya usan apps de notas |

---

## 8. RIESGOS TRANSVERSALES Y CHECKLIST DE IMPLEMENTACION

riesgos que aplican a cualquier funcionalidad de este documento:

1. **dos proyectos, una sola verdad:** olvidar portar a la otra version crea
   divergencias (ver `reglas-online-vs-offline.md`, seccion 6).
2. **textos de interfaz:** todos en minusculas y en los cinco idiomas
   (`values/`, `values-en-rGB/`, `values-en-rUS/`, `values-de/`, `values-fr/`).
3. **alarmas:** cualquier cambio que afecte a fechas debe pasar por
   `AlarmManagerHelper` / `AlarmRescheduler`, nunca desde la interfaz.
4. **sistema de diseno:** componentes `Widget.Polar.*`, iconos lucide,
   colores por atributo de tema y nada de `paddingHorizontal`.
5. **migraciones room:** version nueva y `Migration` explicita; probar la
   migracion con una copia real de la base de datos.
6. **`changelog.md`:** cada funcionalidad se registra bilingue, con las marcas
   `[online]` / `[offline]` cuando solo afecte a una version.

checklist minimo por funcionalidad:

- [ ] definir en que pantalla vive (sin pantalla de primer nivel nueva)
- [ ] decidir si toca room y escribir la migracion
- [ ] escribir los tests de la logica pura antes de integrarla
- [ ] implementar en `polar-offline/` y portar a `polar-online/` en la misma sesion
- [ ] anadir strings en los cinco idiomas, en minusculas
- [ ] actualizar `AGENTS.md`, `CLAUDE.md` y `changelog.md`

---

## 9. PREGUNTAS ABIERTAS PARA EL PROPIETARIO

estas decisiones cambian el orden de la hoja de ruta y no se pueden resolver
desde el codigo:

1. **la version online tiene usuarios propios o es sobre todo un entorno
   de pruebas para la app externa?** de eso depende que F14/F15 merezcan la
   pena.
2. **cuanto se quiere pesar en el uso con el movil bloqueado** (widgets,
   tile, notificaciones) frente al uso dentro de la app?
3. **se acepta un permiso de ubicacion en segundo plano** para el aviso por
   lugar (F06), o se prefiere mantener la lista de permisos actual?
4. **F07 y F08 deben sincronizarse en online** o basta con que sean locales
   por dispositivo?
5. **se quiere mantener el parser solo con reglas locales** (recomendado) o
   se contempla algun dia un modelo en el dispositivo?
