package app.polar.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuItemCompat
import app.polar.R
import com.google.android.material.color.MaterialColors

/**
 * Estilo común de los menús emergentes: iconos visibles en el color secundario del tema y la
 * acción destructiva ("eliminar") en el color de error, con su icono a juego.
 */
fun PopupMenu.applyPolarStyle(context: Context, destructiveItemId: Int = R.id.action_delete): PopupMenu {
    setForceShowIcon(true)
    val neutral = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
    val error = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, 0)
    for (i in 0 until menu.size()) {
        val item = menu.getItem(i)
        val destructive = item.itemId == destructiveItemId
        MenuItemCompat.setIconTintList(item, ColorStateList.valueOf(if (destructive) error else neutral))
        if (destructive) {
            item.title = SpannableString(item.title).apply {
                setSpan(ForegroundColorSpan(error), 0, length, 0)
            }
        }
    }
    return this
}
