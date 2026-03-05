package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.polar.databinding.FragmentTutorialStepBinding

class TutorialStepFragment : Fragment() {
    private var _binding: FragmentTutorialStepBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTutorialStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            binding.tvTitle.setText(it.getInt(ARG_TITLE))
            binding.tvDescription.setText(it.getInt(ARG_DESC))
            binding.ivIcon.setImageResource(it.getInt(ARG_ICON))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESC = "arg_desc"
        private const val ARG_ICON = "arg_icon"

        fun newInstance(titleRes: Int, descRes: Int, iconRes: Int): TutorialStepFragment {
            val fragment = TutorialStepFragment()
            val args = Bundle().apply {
                putInt(ARG_TITLE, titleRes)
                putInt(ARG_DESC, descRes)
                putInt(ARG_ICON, iconRes)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
