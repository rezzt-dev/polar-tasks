package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.FragmentEisenhowerBinding
import app.polar.ui.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EisenhowerFragment : Fragment() {
    private var _binding: FragmentEisenhowerBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEisenhowerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskViewModel.getAllTasks().observe(viewLifecycleOwner) { tasks ->
            populateQuadrants(tasks)
        }
    }

    private fun populateQuadrants(tasks: List<Task>) {
        val activeTasks = tasks.filter { !it.completed && !it.isDeleted }

        binding.layoutQ1Tasks.removeAllViews()
        binding.layoutQ2Tasks.removeAllViews()
        binding.layoutQ3Tasks.removeAllViews()
        binding.layoutQ4Tasks.removeAllViews()

        for (task in activeTasks) {
            val taskView = createTaskItemView(task)
            when (task.priority) {
                3 -> binding.layoutQ1Tasks.addView(taskView)
                2 -> binding.layoutQ2Tasks.addView(taskView)
                1 -> binding.layoutQ3Tasks.addView(taskView)
                else -> binding.layoutQ4Tasks.addView(taskView)
            }
        }
    }

    private fun createTaskItemView(task: Task): View {
        val density = resources.displayMetrics.density
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = (36 * density).toInt()
        }

        // MaterialCheckBox para heredar el estilo de casilla del tema (cuadrada o circular)
        val checkbox = com.google.android.material.checkbox.MaterialCheckBox(requireContext()).apply {
            isChecked = task.completed
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            scaleX = 0.85f
            scaleY = 0.85f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    taskViewModel.setTaskCompletion(task, true)
                    Snackbar.make(binding.root, R.string.task_completed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        val titleTextView = TextView(requireContext()).apply {
            text = task.title
            androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Polar_BodyMedium)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply { marginStart = (6 * density).toInt() }
        }

        layout.addView(checkbox)
        layout.addView(titleTextView)
        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
