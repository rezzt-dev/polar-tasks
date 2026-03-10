# Servicios en Segundo Plano (Background Services)

En cualquier gestor de tareas moderno moderno, una de las garantías más importantes para el usuario final es que sus notificaciones suenen exactamente cuando se supone que deben sonar y no queden suprimidas o dormidas (Doze Mode) por el sistema operativo implacable de Android cerrando servicios de fondo.

**Polar** aborda la gestión del tiempo de vida secundario de la app usando dos primitivas oficiales poderosas y recomendadas por Google.

## Gestor de Trabajo: WorkManager (Jetpack)

Para procesos de largo aliento que requieren "Garantía de Ejecución" en el futuro, pero no imperativamente una precisión al micro-segundo, y deben sobreponerse a reinicios de dispositivo, caídas de la propia aplicación o cierres forzosos de la caché de RAM, empleamos la librería de infraestructura **Android WorkManager**.

### ¿Cuándo usamos Workers?
`app/src/main/java/app/polar/worker/*`:
1.  **Limpiezas (Cleanups):** Tareas periódicas, por ejemplo un Worker planificado para ejecutarse a las 3:00 AM que barre o depura en base de datos toda la caché residual muerta o tareas viejas que el usuario configuró hace semanas para autodestrucción.
2.  **Soporte de Recuperación de Estado Local:** Un script para sincronizar respaldos automáticos en un archivo ZIP almacenado fuera de peligro de forma offline.
3.  **Procesamiento Masivo:** Si el usuario decide generar etiquetas de metadatos o marcar masivamente centenas de tareas como completadas concurrentes, estas transacciones pueden envolverse garantizadas con la delegación hacia una instancia pre-configurada `OneTimeWorkRequestBuilder`.

## Alarmas Exactas (Precisión) y Notificaciones

Cuando el Smart Parser detecta una petición del usuario explícita de *"Llamar jefe mañana a las 5:00 PM"*, no podemos depender de un gestor de trabajos en segundo plano general inespecífico; debemos asegurar alertas quirúrgicas locales.

### BroadcastReceivers Temporales (`app.polar.receiver`)
Utilizamos fuertemente el subsistema de reloj del kernel por medio del API `AlarmManager` junto a **Broadcast Receivers** registrados y localizados bajo el directorio en `/receiver`.

*   **Flujo Exacto Programado:**
    1.  Polar establece el valor temporal milisegundo codificado del instante futuro mediante el método exacto (Si las políticas del sistema de SO posterior al nivel API 31 o Android 12 así lo permiten solicitando `SCHEDULE_EXACT_ALARM`).
    2.  El propio `AlarmManager` despertará al dispositivo y un Intent implícito chocará de frente con la escucha de nuestro `ReminderReceiver.kt`.
    3.  Este Receptor tomará control estático (sin necesidad que exista un UI Activity vivo), consumirá un acceso rápido con Inyección Hilt hacia el caso de uso adecuado y detonará una Alerta Visor (`NotificationManagerCompat`), haciendo sonar el banner UI con sonido directo notificando al usuario en su zona nativa superior de alertas Android.
