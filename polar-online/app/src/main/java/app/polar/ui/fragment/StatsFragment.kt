package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.polar.R
import app.polar.databinding.FragmentStatsBinding
import app.polar.ui.view.HorizontalBarChartView
import app.polar.ui.view.PieChartView
import app.polar.ui.viewmodel.StatsUiState
import app.polar.ui.viewmodel.StatsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStats()
        viewModel.loadStats()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    private fun bindState(state: StatsUiState) {
        if (state.isEmpty) {
            binding.statsContent.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }

        binding.statsContent.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        // Hero
        binding.tvCompletionRate.text = "${state.completionRate}%"
        binding.progressCompletion.setProgressCompat(state.completionRate, true)
        binding.tvCompletionInsight.text = buildInsight(state)

        // Core metrics
        binding.tvTotalCount.text = state.totalTasks.toString()
        binding.tvCompletedCount.text = state.completedTasks.toString()
        binding.tvPendingCount.text = state.pendingTasks.toString()
        binding.tvOverdueCount.text = state.overdueTasks.toString()

        // Weekly chart
        binding.weeklyBarChart.setData(state.weeklyCounts, state.weeklyLabels)

        // Trend chart
        binding.trendLineChart.setData(state.trendCounts, state.trendLabels)

        // Status pie
        bindStatusPie(state)

        // Priority bars
        bindPriorityBars(state)

        // List bars
        bindListBars(state)

        // Streak / best day
        binding.tvStreak.text = state.currentStreak.toString()
        binding.tvBestDay.text = state.bestDay
    }

    private fun buildInsight(state: StatsUiState): String {
        val current = state.completedThisWeek
        val previous = state.completedLastWeek
        return when {
            previous == 0 -> {
                if (current > 0) {
                    getString(R.string.stats_insight_more, current, 100)
                } else {
                    getString(R.string.stats_insight_same)
                }
            }
            current == previous -> getString(R.string.stats_insight_same)
            current > previous -> {
                val diff = current - previous
                val percent = (diff * 100) / previous
                getString(R.string.stats_insight_more, current, percent)
            }
            else -> {
                val diff = previous - current
                val percent = (diff * 100) / previous
                getString(R.string.stats_insight_less, current, percent)
            }
        }
    }

    private fun bindStatusPie(state: StatsUiState) {
        val pendingNotOverdue = (state.pendingTasks - state.overdueTasks).coerceAtLeast(0)
        val slices = mutableListOf<PieChartView.Slice>()

        if (state.completedTasks > 0) {
            slices.add(
                PieChartView.Slice(
                    getString(R.string.stats_status_completed),
                    state.completedTasks.toFloat(),
                    resolveColor(android.R.attr.colorPrimary)
                )
            )
        }
        if (pendingNotOverdue > 0) {
            slices.add(
                PieChartView.Slice(
                    getString(R.string.stats_status_pending),
                    pendingNotOverdue.toFloat(),
                    resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
                )
            )
        }
        if (state.overdueTasks > 0) {
            slices.add(
                PieChartView.Slice(
                    getString(R.string.stats_status_overdue),
                    state.overdueTasks.toFloat(),
                    resolveColor(R.attr.colorError)
                )
            )
        }

        binding.statusPieChart.setData(slices)
    }

    private fun bindPriorityBars(state: StatsUiState) {
        val labels = listOf(
            getString(R.string.priority_none),
            getString(R.string.priority_low),
            getString(R.string.priority_medium),
            getString(R.string.priority_high)
        )
        val colors = listOf(
            resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            resolveColor(R.attr.colorPriorityLow),
            resolveColor(R.attr.colorPriorityMedium),
            resolveColor(R.attr.colorPriorityHigh)
        )

        val bars = state.priorityCounts.mapIndexed { index, count ->
            HorizontalBarChartView.Bar(
                label = labels[index],
                value = count,
                max = state.totalTasks.coerceAtLeast(1),
                color = colors[index]
            )
        }
        binding.priorityBarChart.setData(bars)
    }

    private fun bindListBars(state: StatsUiState) {
        val colorAttrs = listOf(
            android.R.attr.colorPrimary,
            R.attr.colorSuccess,
            R.attr.colorError,
            R.attr.colorPriorityMedium,
            R.attr.colorPriorityLow,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        val bars = state.listCounts.mapIndexed { index, listCount ->
            HorizontalBarChartView.Bar(
                label = listCount.title,
                value = listCount.completed,
                max = listCount.total.coerceAtLeast(1),
                color = resolveColor(colorAttrs.getOrElse(index) { android.R.attr.colorPrimary })
            )
        }
        binding.listBarChart.setData(bars)
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (requireContext().theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            android.graphics.Color.GRAY
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
