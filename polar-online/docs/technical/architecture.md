# Arquitectura del Proyecto (MVVM + Clean Architecture)

Este documento detalla los principios de diseño y la estructura global del código fuente de **Polar**, enfocados en la mantenibilidad, escalabilidad y tolerancia a fallos. El proyecto está construido completamente en Kotlin moderno y utiliza lo último del ecosistema de Android Jetpack.

## Visión General de la Arquitectura

Para asegurar que las responsabilidades de cada componente del software estén debidamente separadas, Polar fue concebida implementando una mezcla pragmática de **Clean Architecture** y el patrón de presentación **Model-View-ViewModel (MVVM)**.

La directriz central se basa en la **separación de capas**. La Interfaz de Usuario (UI) nunca tiene conocimiento sobre el origen de los datos, y los componentes de bajo nivel de la base de datos no dominan ninguna lógica de negocio compleja; todo fluye de adentro hacia afuera gracias a la **Inyección de Dependencias (DI)**.

## Las Capas Principales (Project Structure)

Físicamente, la estructura principal en el código en `app/src/main/java/app/polar/` agrupa las responsabilidades de la siguiente manera:

### 1. Capa de Presentación (UI Layer) - `ui/`
Su único trabajo es gestionar las interacciones del usuario y renderizar estados vivos.
*   **Activities & Fragments:** (`ui/activity`, `ui/fragment`) Son "tontos" — su único rol es inicializar el `ViewBinding`, suscribirse mediante observadores reactivos (*StateFlow, LiveData*) expuestos por sus ViewModels subyacentes y actualizar el layout.
*   **Adapters:** (`ui/adapter`) Manejan listas dinámicas complejas (como tu Inbox, vistas de calendario y tags) utilizando el paradigma de `RecyclerView` y calculando diferencias de rendimiento de listas suavemente (vía `DiffUtil`).
*   **ViewModels:** (`ui/viewmodel`) Manejan la *lógica de presentación*. Conservan el estado intermedio al rotar la pantalla y ordenan el trabajo complejo en despachadores asincrónicos sin bloquear el hilo principal de renderizado (`Dispatchers.IO` o `Dispatchers.Default`).

### 2. Capa de Dominio (Domain Layer) - `domain/`
*(Nota: En aplicaciones MVP pequeñas esta capa suele ser delgada y absorbida por los ViewModels y el Repositorio. Aquí encapsulamos la Lógica de Negocio Pura y Reglas)*
*   **Use Cases (Casos de uso / Interactors):** Contienen operaciones de negocio complejas re-utilizables como *"Mover una tarea de forma validada entre listas verificando si ambas existen"*, o *"Agendar un Recordatorio, validando pre-requisitos de un Flow NLP y desencadenando Broadcast Receivers simultáneamente"*.

### 3. Capa de Datos (Data Layer) - `data/`
Centraliza la obtención de la "Verdad Absoluta" (Single Source of Truth) para interactuar con la persistencia.
*   **Entities:** (`data/entity`) Mapas o "Data Classes" directos de Kotlin a las tablas SQL de SQLite/Room.
*   **DAOs (Data Access Objects):** (`data/dao`) Las interfaces especializadas de Room llenas de anotaciones como `@Query`, `@Insert` repletas del poder reactivo (*flujos de Kotlin continuos observados por el resto de la app*).
*   **Repositories:** (`data/repository`) Implementaciones bajo el Patrón Repositorio. Ocultan al ViewModel de dónde y cómo exactamente el "Dato X" fue traído (es decir, Room Database, Preferences DataStore, o en el futuro un caché/API).

## Principios Reactivos Sostenidos
Para lidiar con el alto grado de cambios, estado y velocidad exigidos, la aplicación utiliza extensivamente abstracciones reactivas.
Los DAOs empaquetan las respuestas de consultas SQLite/Room como objetos **`Flow<T>`** (Kotlin Coroutines). El repositorio lo transmite hacia el ViewModel y éste termina transformándolo en un estado mapeado y consumido en el UI Thread mediante recolectores como la función `collect{}` orientada a estados de ciclos de vida de Android.
El resultado es un refresco dinámico y constante: cuando modificas el nombre de una Lista o añades un Tag, cada fragmento en pantalla visible que dependa de esa misma colección, se re-pintará instantáneamente sin necesidad de peticiones imperativas manuales desde el UI.
