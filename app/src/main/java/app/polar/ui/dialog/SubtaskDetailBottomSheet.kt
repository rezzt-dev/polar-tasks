package app.polar.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import app.polar.databinding.DialogSubtaskDetailBinding

class SubtaskDetailBottomSheet(
    private val content: String,
    private val onEdit: () -> Unit,
    private val onDelete: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSubtaskDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvSubtaskDetailTitle.text = content
        
        binding.btnEdit.setOnClickListener {
            onEdit()
            dismiss()
        }
        
        binding.btnDelete.setOnClickListener {
            onDelete()
            dismiss()
        }
        
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SubtaskDetailBottomSheet"
    }
}
