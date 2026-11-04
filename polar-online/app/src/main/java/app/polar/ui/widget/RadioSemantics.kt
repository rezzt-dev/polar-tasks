package app.polar.ui.widget

import android.view.View
import android.widget.RadioButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Anuncia la vista como una opcion de un grupo excluyente ("boton de opcion, marcado"), para
 * selectores dibujados a mano (miniaturas de tema, muestras de color). Llamala otra vez al cambiar
 * la seleccion.
 */
fun View.setRadioSemantics(checked: Boolean) {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = RadioButton::class.java.name
            info.isCheckable = true
            info.isChecked = checked
        }
    })
}
