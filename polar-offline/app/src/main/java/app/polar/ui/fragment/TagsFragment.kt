package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.FragmentTagsBinding
import app.polar.ui.activity.TaskDetailActivity
import app.polar.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TagsFragment : Fragment() {

    private var _binding: FragmentTagsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TagsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = TagsAdapter { tagModel ->
            showTagFilteredTasksBottomSheet(tagModel.tag, tagModel.tasks)
        }

        binding.rvTagsGrid.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTagsGrid.adapter = adapter

        viewModel.repository.getAllTasks().observe(viewLifecycleOwner) { tasks ->
            if (tasks != null) {
                val tagCounts = mutableMapOf<String, MutableList<Task>>()
                tasks.forEach { task ->
                    val tags = task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    tags.forEach { tag ->
                        tagCounts.getOrPut(tag) { mutableListOf() }.add(task)
                    }
                }
                val tagModels = tagCounts.map { (tag, tList) -> TagModel(tag, tList.size, tList) }
                    .sortedByDescending { it.count }
                adapter.submitList(tagModels)
            }
        }
    }

    private fun showTagFilteredTasksBottomSheet(tag: String, tasks: List<Task>) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_tag_tasks, null)

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetTagTitle)
        val rvTasks = view.findViewById<RecyclerView>(R.id.rvSheetTasks)

        tvTitle.text = "tareas con etiqueta #$tag"

        val taskAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
                val tvDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
                val tvDate: TextView = itemView.findViewById(R.id.tvTaskDate)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
                // hide some elements not used in summary
                v.findViewById<View>(R.id.cbTaskComplete).visibility = View.GONE
                v.findViewById<View>(R.id.tagsContainer).visibility = View.GONE
                return TaskViewHolder(v)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val h = holder as TaskViewHolder
                val task = tasks[position]
                h.tvTitle.text = task.title
                if (task.description.isNotBlank()) {
                    h.tvDescription.text = task.description
                    h.tvDescription.visibility = View.VISIBLE
                } else {
                    h.tvDescription.visibility = View.GONE
                }
                if (task.dueDate != null) {
                    h.tvDate.text = app.polar.util.DateUtils.formatTaskDate(h.itemView.context, task.dueDate)
                    h.tvDate.visibility = View.VISIBLE
                } else {
                    h.tvDate.visibility = View.GONE
                }

                h.itemView.setOnClickListener {
                    dialog.dismiss()
                    val intent = android.content.Intent(requireContext(), TaskDetailActivity::class.java).apply {
                        putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
                    }
                    startActivity(intent)
                }
            }

            override fun getItemCount(): Int = tasks.size
        }

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = taskAdapter

        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class TagModel(val tag: String, val count: Int, val tasks: List<Task>)

    class TagsAdapter(private val onTagClick: (TagModel) -> Unit) :
        RecyclerView.Adapter<TagsAdapter.ViewHolder>() {

        private var items = emptyList<TagModel>()

        fun submitList(newItems: List<TagModel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag_full, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTagName: TextView = itemView.findViewById(R.id.tvTagName)
            private val tvTagCount: TextView = itemView.findViewById(R.id.tvTagCount)

            fun bind(model: TagModel) {
                tvTagName.text = "#${model.tag}"
                tvTagCount.text = "${model.count} ${if (model.count == 1) "tarea" else "tareas"}"
                itemView.setOnClickListener { onTagClick(model) }
            }
        }
    }
}
