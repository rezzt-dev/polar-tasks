package app.polar.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var maxHeight: Int = 0

    init {
        // Altura máxima por defecto: 250dp
        maxHeight = (250 * context.resources.displayMetrics.density).toInt()
        
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight))
            try {
                if (a.hasValue(0)) {
                    maxHeight = a.getDimensionPixelSize(0, maxHeight)
                }
            } finally {
                a.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var newHeightMeasureSpec = heightMeasureSpec
        if (maxHeight > 0) {
            newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // Evitar que el contenedor padre (como NestedScrollView) intercepte el toque
            // si esta lista tiene elementos suficientes para hacer scroll.
            if (canScrollVertically(1) || canScrollVertically(-1)) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun computeVerticalScrollExtent(): Int {
        return height
    }

    override fun computeVerticalScrollRange(): Int {
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return super.computeVerticalScrollRange()
        val count = adapter?.itemCount ?: 0
        if (count == 0) return 0
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return super.computeVerticalScrollRange()
        val firstView = lm.findViewByPosition(firstVisible) ?: return super.computeVerticalScrollRange()
        return count * firstView.height
    }

    override fun computeVerticalScrollOffset(): Int {
        val lm = layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return super.computeVerticalScrollOffset()
        val count = adapter?.itemCount ?: 0
        if (count == 0) return 0
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return super.computeVerticalScrollOffset()
        val firstView = lm.findViewByPosition(firstVisible) ?: return super.computeVerticalScrollOffset()
        return (firstVisible * firstView.height) - firstView.top
    }
}
