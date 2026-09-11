# CLAUDE.md — Guía para Claude Code (Polar)

> Este archivo orienta a Claude Code (y a cualquier agente de IA) al trabajar
> sobre el proyecto **Polar**. El contexto completo del proyecto (stack,
> arquitectura, estructura de código, base de datos, testing, seguridad) está
> en [`AGENTS.md`](AGENTS.md): **léelo siempre antes de tocar código**.
>
> `CLAUDE.md` y `AGENTS.md` deben mantenerse sincronizados: si cambias uno,
> replica el cambio equivalente en el otro.

---

## Redacción de commits (título y descripción)

<!--
  Este bloque replica literalmente el contenido de `.docs/commit-guidelines.md`
  (que es local y no se versiona) y de la sección homónima de `AGENTS.md`.
  Existe para que todos los commits del repositorio salgan con el mismo
  formato: más limpios y profesionales. Un historial homogéneo es más cómodo
  de leer para usuarios y desarrolladores, y transmite la información de los
  cambios de la mejor forma posible (prefijo claro, resumen en imperativo y
  cuerpo en lista). No improvises el formato: aplica siempre estas reglas. Si
  editas estas reglas, replica el cambio en `AGENTS.md` y en
  `.docs/commit-guidelines.md`.
-->

> instrucciones para cualquier agente de ia (o persona) que redacte el titulo
> y la descripcion de un commit de git en este repositorio. se aplica siempre
> que se pida algo equivalente a:
>
> *"sacame el titulo y la descripcion para el commit con los cambios
> realizados desde el ultimo commit. fijate en los anteriores commits para
> que tengan el mismo formato."*
>
> y, de forma general, **cada vez que se vaya a crear un commit en este
> repositorio**, se haya pedido explicitamente o no.

### Formato fijo del titulo

```
<prefijo> | <resumen corto en imperativo>
```

el separador es siempre espacio, barra vertical, espacio: ` | `. el
`<prefijo>` es siempre una de estas cinco palabras, en minusculas, y nunca
se combinan ni se inventan otras nuevas:

| prefijo | cuando usarlo |
|---|---|
| **update** | trabajo normal hacia adelante: se añade o mejora una funcionalidad, contenido o documento existente. es el prefijo por defecto. |
| **fix** | se corrige un error o comportamiento incorrecto detectado durante el desarrollo, sin urgencia de publicacion inmediata. |
| **hotfix** | se corrige algo urgente que afecta a una version ya publicada o a un bloqueo critico. debe usarse solo cuando la correccion es realmente urgente. |
| **changes** | cambios estructurales o no funcionales: reorganizar carpetas, renombrar ficheros, tocar configuracion o tooling, dependencias. no anade funcionalidad ni corrige un bug. |
| **changelog** | el unico proposito del commit es editar `changelog.md` (por ejemplo, al cerrar una version). |

ejemplos de titulos correctos:

```
update | añadir vista previa de mapeo de columnas al asistente de importación csv
fix | detección de duplicados fallando en filas con importes negativos
hotfix | fallo al iniciar cuando falta el archivo de perfil de importación
changes | reorganizar la documentación en la subcarpeta project-context
changelog | cerrar la sección de changelog para la versión mvp fase 0
```

### Formato de la descripcion (cuerpo)

- el cuerpo es siempre **una lista de markdown no numerada**: cada item empieza
  por `-`. nunca se usan listas numeradas ni parrafos sueltos.
- cada item es un bloque logico en imperativo, escrito como **una sola linea
  continua**, sin saltos de linea internos: no son esteticos ni utiles, deja
  que el cliente de git lo ajuste. no se dejan lineas en blanco entre items.
- usa markdown dentro de cada item: nombres de ficheros, rutas, comandos e
  identificadores siempre entre backticks.
- explica el que y el por que, no el detalle linea a linea.
- cierra con una linea en blanco y la linea de atribucion que corresponda al
  entorno.

### Como presentar el resultado

al responder con el titulo y la descripcion hay que usar **bloques de codigo
separados**: uno para el titulo del commit y otro para el cuerpo del commit (mas
otro para el fragmento del changelog). nunca se juntan el titulo y el cuerpo en
el mismo bloque.

---

## Formato de las secciones de `changelog.md`

<!--
  Este bloque define como se escriben las entradas de `changelog.md`. El
  objetivo es un registro de cambios homogeneo y bilingue (castellano e
  ingles) que la mayoria de personas pueda leer, y que quede limpio y
  profesional. Replica la seccion homonima de `AGENTS.md`; si editas estas
  reglas, replica el cambio alli tambien.
-->

`changelog.md` (en la raiz del repositorio) registra los cambios del proyecto
de aqui en adelante. reglas de escritura:

- **todo el contenido en minusculas y sin acentos.** los titulos (encabezados
  markdown `#`, `##`, `###`, `####`) van **en mayusculas y sin acentos**.
- **bilingue:** cada version contiene el mismo contenido en castellano y en
  ingles, con las mismas entradas en el mismo orden (traduccion 1:1).
- cada item es **una sola linea** que empieza por `-`, redactada en
  imperativo. usa backticks para nombres de ficheros, rutas, comandos e
  identificadores.
- la version mas reciente va **arriba** del fichero, debajo de la cabecera.
- las fechas usan el formato `aaaa-mm-dd`.

estructura de cada version:

```
## SIN PUBLICAR / UNRELEASED        (mientras la version no se ha publicado)
## [x.y.z] - aaaa-mm-dd             (al cerrar la version)

### CASTELLANO

#### <categoria>
- <cambio en una linea>

### ENGLISH

#### <category>
- <same change, one line>
```

categorias permitidas (lista cerrada; se omite la que no tenga items):

| castellano | english | cuando |
|---|---|---|
| **NUEVO** | **ADDED** | funcionalidad, pantalla o contenido nuevo. |
| **CAMBIOS** | **CHANGED** | cambios de comportamiento, refactor, reorganizacion, tooling. |
| **CORREGIDO** | **FIXED** | correccion de un bug o comportamiento incorrecto. |
| **ELIMINADO** | **REMOVED** | funcionalidad, opcion o fichero que se quita. |
| **SEGURIDAD** | **SECURITY** | cambios relacionados con seguridad o permisos. |

flujo:

- mientras se trabaja, las entradas nuevas se anaden a la seccion
  `## SIN PUBLICAR / UNRELEASED`.
- al cerrar una version se renombra esa seccion a `## [x.y.z] - aaaa-mm-dd` y
  se crea una nueva seccion `## SIN PUBLICAR / UNRELEASED` vacia encima.
- el commit que solo toca `changelog.md` usa el prefijo `changelog`.

---

## Pull requests, merges y releases

<!--
  Este bloque replica literalmente el contenido de
  `.docs/pr-merge-release-guidelines.md` y de la sección homónima de
  `AGENTS.md`. Existe para que las pull requests, los commits de merge y las
  releases de github lean como una extensión directa de `changelog.md` en
  vez de tener cada una su propio formato. Si editas estas reglas, replica el
  cambio en `AGENTS.md` y en `.docs/pr-merge-release-guidelines.md`.
-->

pull requests, merges y releases **no inventan su propio formato**: reusan
literalmente el contenido y las reglas de estilo de `changelog.md` — minusculas
y sin acentos, las mismas cinco categorias en el mismo orden (nuevo, cambios,
corregido, eliminado, seguridad / added, changed, fixed, removed, security,
omitiendo la que no tenga items), una linea por item en imperativo con
backticks para nombres de ficheros, rutas, comandos e identificadores, y
fechas en formato `aaaa-mm-dd`. las marcas `[online]` / `[offline]` de
`changelog.md` se conservan tal cual. la fuente de verdad es siempre
`changelog.md`: el contenido de pr, merge y release se extrae de ahi, nunca
se redacta desde cero ni en paralelo.

### pull requests

- **pr de trabajo normal** (`feature/...` o `fix/...` contra
  `testing-branch`): mismo titulo que un commit,
  `<prefijo> | <resumen corto en imperativo>` (ver "redaccion de commits"
  arriba).
- **pr de publicacion de version** (`testing-branch` → `release-branch` /
  `main`, cierra una version): `release version <x.y.z> - <resumen corto en
  imperativo>`.
- **descripcion** (igual para las dos): el bloque `### CASTELLANO` +
  `### ENGLISH` de la seccion correspondiente de `changelog.md`, pegado tal
  cual con sus encabezados `####` de categoria. si la pr solo aporta parte de
  `## SIN PUBLICAR / UNRELEASED`, se incluyen solo los items que introduce.
  no se anaden parrafos sueltos ni resumenes fuera de la lista.

### commit de merge

github genera el commit de merge a partir del titulo y la descripcion de la
pr, asi que una pr correcta ya produce un merge correcto. usa siempre
**"create a merge commit"** (nunca squash ni rebase), para conservar el
historial de commits individuales junto al resumen de la pr.

### releases de github

las releases son **en ingles unicamente** (no bilingues):

- **titulo:** `v<x.y.z> — <resumen corto en ingles, minusculas, sin acentos>`
  (ejemplo: `v1.7 — 100% offline, reminders redesign & settings categories`).
- **cuerpo:** el bloque `### ENGLISH` de la version cerrada en
  `changelog.md`, con sus categorias `#### ADDED` / `#### CHANGED` /
  `#### FIXED` / `#### REMOVED` / `#### SECURITY` tal cual, omitiendo la que
  no tenga items. no se reescribe en parrafos narrativos.
- **tag:** siempre `v<x.y.z>`, sin sufijos adicionales.

las releases anteriores a esta regla (`.docs/releases-docs/release-1.7/`)
usan un formato narrativo distinto y quedan como historicas, sin reescribir.

---

## Notas rápidas

- **Idioma para explicaciones técnicas:** Español.
- **Interfaz siempre en minúsculas:** todos los textos de UI de la aplicación
  van **en minúsculas y en todos los idiomas** (incluido alemán, y nombres
  propios como `polar`, `material` o `eisenhower`). Aplica a los recursos de
  `res/values*/strings.xml` (incluidos `plurals`) y a cualquier texto
  hardcodeado en layouts o código. **No** aplica a datos introducidos por el
  usuario (títulos de tareas, notas, etiquetas, etc.) ni a los especificadores
  de formato (`%1$s`, `%d`). Al añadir una funcionalidad o traducir strings,
  replica esta convención en todos los `values-*`.
- **UI:** 100% XML + View Binding (no Compose, no Navigation Component).
- **Procesador de anotaciones:** `kapt` (no KSP).
- **Room:** al alterar el esquema, sube la versión y añade `Migration` manual.
- **ViewModels:** `@HiltViewModel`, inyección por constructor y patrón
  `safeLaunch`.
- **Activities:** extienden `BaseActivity` y se anotan con `@AndroidEntryPoint`.
- **Colores:** usa atributos de tema (`?attr/...`), nunca hex hardcodeados.
- **Commits:** aplica la sección "Redacción de commits" antes de proponer título o descripción.
- **`changelog.md`:** registra los cambios según la sección "Formato de las secciones de `changelog.md`".
- **Pull requests, merges y releases:** aplica la sección "Pull requests, merges y releases" antes de redactar cualquiera de los tres.

El resto de convenciones y detalle está en [`AGENTS.md`](AGENTS.md).
