# Inyección de Dependencias (Dependency Injection)

Una aplicación fuertemente estructurada bajo *Clean Architecture* con múltiples componentes que dependen el uno del otro (Fragmento -> ViewModel -> Use Case -> Repositorio -> Controlador Local/Entity Room Dao) requiere un pegamento para unificar todas las implementaciones o se ahogaría rápidamente en constructores monstruosamente rígidos sin mantenimiento, propensos al desacoplamiento insoportable si necesitas rotar componentes para implementar Testeos Aislados.

**Polar** resuelve este problema eficientemente desde su primer esqueleto introduciendo **Hilt (Dagger-Hilt)**, como su plataforma central de Proveedores de Inversión de Control.

## ¿Por qué Hilt?

Hilt está construido específicamente por Google por sobre las implementaciones del estándar y ultrarrápido Dagger 2, dándole vida a una API mucho más concisa y estándar de facto en desarrollos de Android modernos; reduciendo toda la necesidad previa de construir docenas de módulos extraños, provisioners y componentes customizados (boilerplate). Hilt entrega toda inyección y ciclos de la Aplicación global estandarizados mágicamente solo mediante la adición de Anotaciones al Código.

## Estructuración e Implementación

Para manejar eficazmente el estado de Hilt en el proyecto, revisaremos los cimientos implementados.

### 1. El Ámbito Superior: App Entry Point
El ciclo de auto-generación del árbol de dependencias despierta dentro del paquete raíz interceptando el objeto Application a nivel superior, `app.polar.PolarApplication.kt` junto al uso imperativo de la etiqueta mágica `@HiltAndroidApp`.

Esa etiqueta desencadena y da a luz al contenedor nivel raíz para procesar toda dependencia única Global (Singletons) inyectada en todo el hilo de vida predeterminado que sobreviva a toda instancia simple, y acopla un contenedor a nivel `Activity/Fragment`.

### 2. Contratos de Inyección en Vistas - `@AndroidEntryPoint`
Toda aquella intercepción al sistema para reclamar dependencias subyacentes en una Activity (como `MainActivity`) o los docenas de múltiples Layouts de UI `Fragment` debe poseer a pulso la etiqueta `@AndroidEntryPoint` en el comienzo de la clase y de este modo reclamar ser atendidos independientemente a cada requerimiento inyectable interno necesario, a petición libre.

### 3. Conectores Mágicos de Estado: ViewModels
Para el provisionamiento en clases `ViewModel` inyectados en la capa UI, Hilt ofrece la etiqueta `@HiltViewModel` por sobre su nombre. Aquellas variables o Repositorios que necesiten inicializaciones dentro de cada VM, automáticamente se solicitan delegándolas marcando a toda inyección que cruza el portal en el Constructor primario Kotlin mediante `@Inject constructor(…)`, acoplándose armónicamente al ciclo sin necesidad de fábricas de ViewModels monstruosas y pesadas hechas de forma manual o verbosas.

### 4. La Carpeta de Módulos Central (Módulos de Provisión `di/`)
Es posible que durante nuestro desarrollo debamos proveer clases que simplemente no tienen un constructor inyectable expuesto, por ejemplo:
- Inicialización en cascada de la **Room Database** (Es una interfaz).
- **Controlador Datastore** estático global (Librerías externas al desarrollo estático).
- API **Retrofit Clients**.

Para satisfacer la inyección y darle alcance general al arquetipo a proveer este tipo de librerías exógenas cerradas para proveer a través de `@Provides` delegamos toda esta arquitectura a simples y descriptivos archivos en la ruta del framework, en la importante carpeta llamada `di/` (Dependy Injection):

Allí encontraremos usualmente módulos como `DatabaseModule.kt` marcados fuertemente dictando sus retornos de constructores y variables estáticas que toda arquitectura proveerá cíclicamente a todo archivo `class` en Polar que necesite un DAO o conexión en segundo plano rápida y pre-cargada y en caché.
