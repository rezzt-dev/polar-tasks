package app.polar.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
    private val swipeThreshold: Float = DEFAULT_SWIPE_THRESHOLD,
    private val swipeConfigForHolder: ((RecyclerView.ViewHolder, Boolean) -> SwipeConfig)? = null,
    private val cornerRadiusDp: Float = 0f
) : ItemTouchHelper.SimpleCallback(0, 0) {

    data class SwipeConfig(
        @AttrRes val backgroundColorAttr: Int,
        @DrawableRes val iconRes: Int,
        @AttrRes val iconTintAttr: Int? = null,
        @StringRes val labelRes: Int? = null
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val backgroundRect = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

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

        val config = swipeConfigForHolder?.invoke(viewHolder, dX > 0)
            ?: if (dX > 0) rightSwipeConfig else leftSwipeConfig
        backgroundPaint.color = resolveThemeColor(context, config.backgroundColorAttr)
        val icon = ContextCompat.getDrawable(context, config.iconRes)?.mutate()
        val iconTint = resolveThemeColor(context, config.iconTintAttr ?: MaterialR.attr.colorOnSurface)
        icon?.setTint(iconTint)
        val left = if (dX > 0) itemView.left.toFloat() else (itemView.right + dX).coerceAtLeast(itemView.left.toFloat())
        val right = if (dX > 0) (itemView.left + dX).coerceAtMost(itemView.right.toFloat()) else itemView.right.toFloat()
        val save = canvas.save()
        canvas.clipRect(left, itemView.top.toFloat(), right, itemView.bottom.toFloat())
        backgroundRect.set(itemView.left.toFloat(), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
        val radius = context.dpToPx(cornerRadiusDp)
        canvas.drawRoundRect(backgroundRect, radius, radius, backgroundPaint)

        val iconSize = context.dpToPx(ICON_SIZE_DP)
        val label = config.labelRes?.let(context::getString)
        labelPaint.color = iconTint
        labelPaint.textSize = 12f * context.resources.displayMetrics.scaledDensity
        val labelWidth = label?.let(labelPaint::measureText) ?: 0f
        val inset = context.dpToPx(ICON_MARGIN_DP)
        val actionWidth = maxOf(iconSize, labelWidth) + inset * 2
        val centerX = if (dX > 0) itemView.left + actionWidth / 2 else itemView.right - actionWidth / 2
        val showLabel = label != null && right - left >= actionWidth
        val iconTop = itemView.top + (itemView.height - iconSize) / 2f - if (showLabel) context.dpToPx(10f) else 0f
        icon?.setBounds((centerX - iconSize / 2).toInt(), iconTop.toInt(), (centerX + iconSize / 2).toInt(), (iconTop + iconSize).toInt())
        icon?.draw(canvas)
        if (showLabel) {
            canvas.drawText(label!!, centerX, iconTop + iconSize + context.dpToPx(6f) - labelPaint.fontMetrics.top, labelPaint)
        }
        canvas.restoreToCount(save)
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
