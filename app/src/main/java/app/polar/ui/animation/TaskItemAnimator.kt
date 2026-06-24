package app.polar.ui.animation

import androidx.recyclerview.widget.DefaultItemAnimator

/**
 * ItemAnimator optimizado para listas de tareas.
 *
 * - Desactiva animaciones de tipo CHANGE para evitar parpadeos cuando DiffUtil
 *   reutiliza ViewHolders con estado residual de swipe.
 * - Mantiene animaciones de ADD/REMOVE/MOVE para que el desplegable de tareas
 *   completadas y los movimientos de grupos sean fluidos y premium.
 */
class TaskItemAnimator : DefaultItemAnimator() {
    init {
        supportsChangeAnimations = false
        addDuration = 200L
        removeDuration = 200L
        moveDuration = 200L
    }
}
