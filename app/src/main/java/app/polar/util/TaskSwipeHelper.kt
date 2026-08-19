package app.polar.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import com.google.android.material.R as MaterialR

/**
 * Helper reutilizable para gestos de deslizamiento (swipe) en RecyclerViews.
 *
 * Pinta fondos de color e iconos mientras el usuario desliza un ítem, y delega
 * las acciones finales mediante callbacks. Soporta tanto tareas como listas,
 * ya que no depende del tipo de adapter: solo necesita la posición del ítem.
 *
 * Swipe de izquierda a derecha (dX > 0) -> [rightSwipeConfig].
 * Swipe de derecha a izquierda (dX < 0) -> [leftSwipeConfig].
 *
 * Los iconos se tinen siempre con atributos de tema (colorOnSuccess / colorOnError)
 * para respetar la paleta activa y mantener contraste sin valores hex hardcodeados.
 */
class TaskSwipeHelper(
    private val rightSwipeConfig: SwipeConfig = SwipeConfig(
        backgroundColorAttr = R.attr.colorSuccess,
        iconRes = R.drawable.ic_check,
        iconTintAttr = R.attr.colorOnSuccess
    ),
    private val leftSwipeConfig: SwipeConfig = SwipeConfig(
        backgroundColorAttr = R.attr.colorError,
        iconRes = R.drawable.ic_trash,
        iconTintAttr = MaterialR.attr.colorOnError
    ),
    private val getDragFlagsForHolder: (RecyclerView.ViewHolder) -> Int = { 0 },
    private val getSwipeFlagsForHolder: (RecyclerView.ViewHolder) -> Int = {
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    },
    private val onMoveCallback: (fromPosition: Int, toPosition: Int) -> Boolean = { _, _ -> false },
    private val onSwipedRight: (position: Int) -> Unit = {},
    private val onSwipedLeft: (position: Int) -> Unit = {},
    private val onSelectedChangedCallback: (viewHolder: RecyclerView.ViewHolder?, actionState: Int) -> Unit = { _, _ -> },
    private val onClearViewCallback: (recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) -> Unit = { _, _ -> },
    private val swipeThreshold: Float = DEFAULT_SWIPE_THRESHOLD
) : ItemTouchHelper.SimpleCallback(0, 0) {

    data class SwipeConfig(
        @AttrRes val backgroundColorAttr: Int,
        @DrawableRes val iconRes: Int,
        @AttrRes val iconTintAttr: Int? = null
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val backgroundRect = RectF()

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return try {
            val dragFlags = getDragFlagsForHolder(viewHolder)
            val swipeFlags = getSwipeFlagsForHolder(viewHolder)
            makeMovementFlags(dragFlags, swipeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting movement flags for ${viewHolder::class.java.simpleName}", e)
            0
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return swipeThreshold.coerceIn(0.05f, 0.95f)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return try {
            onMoveCallback.invoke(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        } catch (e: Exception) {
            Log.e(TAG, "Error during move", e)
            false
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        try {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            when (direction) {
                ItemTouchHelper.RIGHT -> onSwipedRight(position)
                ItemTouchHelper.LEFT -> onSwipedLeft(position)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing swipe", e)
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        try {
            onSelectedChangedCallback.invoke(viewHolder, actionState)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSelectedChanged", e)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        try {
            onClearViewCallback.invoke(recyclerView, viewHolder)
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearView", e)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            try {
                drawSwipeBackground(c, viewHolder, dX)
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing swipe background", e)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawSwipeBackground(
        canvas: Canvas,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        if (dX == 0f) return

        val itemView = viewHolder.itemView
        val context = itemView.context

        val config = if (dX > 0) rightSwipeConfig else leftSwipeConfig
        backgroundPaint.color = resolveThemeColor(context, config.backgroundColorAttr)

        val icon = ContextCompat.getDrawable(context, config.iconRes)?.mutate()
        val fallbackTint = resolveThemeColor(context, MaterialR.attr.colorOnSurface)
        val iconTint = config.iconTintAttr?.let { resolveThemeColor(context, it) } ?: fallbackTint
        icon?.setTint(iconTint)

        if (dX > 0) {
            // Izquierda a derecha: fondo desde el borde izquierdo hasta dX
            backgroundRect.set(
                itemView.left.toFloat(),
                itemView.top.toFloat(),
                itemView.left + dX,
                itemView.bottom.toFloat()
            )
            canvas.drawRect(backgroundRect, backgroundPaint)

            icon?.let {
                val iconSize = context.dpToPx(ICON_SIZE_DP)
                val iconMargin = context.dpToPx(ICON_MARGIN_DP)
                val iconLeft = itemView.left + iconMargin
                val iconTop = itemView.top + (itemView.height - iconSize) / 2f
                it.setBounds(
                    iconLeft.toInt(),
                    iconTop.toInt(),
                    (iconLeft + iconSize).toInt(),
                    (iconTop + iconSize).toInt()
                )
                it.draw(canvas)
            }
        } else {
            // Derecha a izquierda: fondo desde el borde derecho + dX hasta el borde derecho
            backgroundRect.set(
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            canvas.drawRect(backgroundRect, backgroundPaint)

            icon?.let {
                val iconSize = context.dpToPx(ICON_SIZE_DP)
                val iconMargin = context.dpToPx(ICON_MARGIN_DP)
                val iconLeft = itemView.right - iconMargin - iconSize
                val iconTop = itemView.top + (itemView.height - iconSize) / 2f
                it.setBounds(
                    iconLeft.toInt(),
                    iconTop.toInt(),
                    (iconLeft + iconSize).toInt(),
                    (iconTop + iconSize).toInt()
                )
                it.draw(canvas)
            }
        }
    }

    private fun resolveThemeColor(context: android.content.Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun android.content.Context.dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    companion object {
        private const val ICON_SIZE_DP = 24f
        private const val ICON_MARGIN_DP = 16f
        private const val DEFAULT_SWIPE_THRESHOLD = 0.5f
        private const val TAG = "TaskSwipeHelper"
    }
}
