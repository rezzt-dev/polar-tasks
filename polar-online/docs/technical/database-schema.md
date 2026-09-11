# Esquema de la Base de Datos Relacional

Este documento aborda las interioridades de la persistencia principal offline y ultra-rápida implementada fundamentalmente con **Room Database**, la librería recomendada de Google enfocada en abstraer el código "Repetitivo" crudo y molesto de la capa de SQlite al ecosistema completo y tipado de coroutines Kotlin.

## Archivos Centrales de la Capa de Datos

Toda interacción local y almacenamiento atómico sucede dentro del paquete modular superior `data/`.

### Las Entidades (Data Classes y Relaciones Mapeadas a Tablas)
Localizadas en `data/entity/*`, los esquemas principales se apoyan fuertemente de etiquetas de anotaciones como `@Entity` indicando tablas nombradas, `@PrimaryKey(autoGenerate = true)` y `@Ignore`.

**1. Tareas (`TaskEntity`):**
La tabla primordial de la aplicación.
*   Campos base conteniendo metadatos como Título, Descripción Extensa opcional, UUID únicos.
*   **Gestión de Estados e Hilos Temporales:** Banderas lógicas booleanas para estados completados `isCompleted = true/false`, instantes Unix para marca temporal (`timestamp_created`, `due_date_nullable`).

**2. Listas de Usuarios / Directorios (`ListEntity`):**
Contenedores estáticos de colección para dividir directorios personales de tareas en distintos apartados. Las "Tareas" poseen comúnmente una variable `list_id_parent` Foreign-Key virtual (clave foránea de interconexión ligada a Listas).

**3. Etiquetas de Meta-Data (`TagEntity`):**
Entidades flexibles, conteniendo propiedades UI directas persistidas como cadenas hexadecimales de su paleta de color (`hex_color`) y nombres semánticos.
*   **Relación Many-to-Many:** Dado que una tarea puede albergar múltiples tags y un tag aplica para incontables tareas al cruzar contextos o carpetas, una tabla unificadora (cross-reference join table) adicional invisible permite el relacionamiento entre ellas sin romperse o alterar sus modelos base.

**4. Subtareas Atomizadas y Dependencias:**
Para soportar el sistema de pasos subyacentes ligados a una `TaskEntity` principal "Padre", cada subtarea se empaqueta serializada o anidada en la propia tabla del padre como campo estructurado de lista pre-formateada en un conversor relacional propio, ó se implementan tablas hijas en una relación anclada de 1-a-Muchos que eliminan sus partes con efecto dominó protector ante borrados (mediante transacciones de `CASCADE Delete` en base de datos previniendo tablas inútiles o basura remanente "Zombies").

## Accesos Directos Ocultos y Mágicos: Data Access Objects (DAOs)

Una de las metas obligatorias implementadas tras toda capa superior UI que necesita reaccionar y re-pintar a cada pequeño cambio sin que todo explote es mediante DAOs en `data/dao/*`.

*   Anotaciones simples para interacciones cridas: Un simple `@Insert(onConflict = OnConflictStrategy.REPLACE)` para reemplazar sin colapso a nuevas actualizaciones atómicas inofensivas en tiempo real desde la edición UI.
*   **Búsquedas Complejas en Texto Natural (FTS) & Relaciones Mixtas:** Muchas interacciones analíticas utilizan instrucciones directas `@Query("SELECT * FROM tasks WHERE nombre LIKE '%' || :query || '%' AND listId = :id")` compiladas en chequeos de forma preventiva y empaquetando todo el resultado en un flujo reactivo estricto Kotlin `Flow<List<TaskEntity>>`. Estos flujos persistentes son la pieza fundamental para las interacciones reactivas de estado presentadas en nuestra capa limpia de vista UI, observadas desde DAOs mediante un intermediario transaccional del Repository layer subyacente de Arquitectura.
