package app.polar.ui.activity

import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import app.polar.R
import app.polar.databinding.ActivityAuthBinding
import app.polar.ui.viewmodel.AuthViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupForm()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
    }

    private fun setupForm() {
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty().trim()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.signIn(email, password)
        }

        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.auth_email_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(binding.etEmail.text?.toString().orEmpty())
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.auth_reset_password_title))
            .setMessage(getString(R.string.auth_reset_password_message))
            .setView(container)
            .setPositiveButton(getString(R.string.auth_reset_password_send)) { dialog, _ ->
                dialog.dismiss()
                viewModel.resetPassword(input.text?.toString().orEmpty().trim())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showMergeDecisionDialog() {
        MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setTitle(getString(R.string.auth_merge_dialog_title))
            .setMessage(getString(R.string.auth_merge_dialog_message))
            .setPositiveButton(getString(R.string.auth_merge_dialog_upload)) { dialog, _ ->
                dialog.dismiss()
                viewModel.confirmMergeUpload()
            }
            .setNegativeButton(getString(R.string.auth_merge_dialog_discard)) { dialog, _ ->
                dialog.dismiss()
                showDiscardConfirmationDialog()
            }
            .show()
    }

    private fun showDiscardConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_stat_error)
            .setTitle(getString(R.string.auth_merge_discard_warning_title))
            .setMessage(getString(R.string.auth_merge_discard_warning_message))
            .setPositiveButton(getString(R.string.auth_merge_discard_confirm)) { dialog, _ ->
                dialog.dismiss()
                viewModel.confirmDiscardLocalUseCloud()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                // Falls back to the safe default (merge/upload) rather than leaving sync stuck.
                viewModel.confirmMergeUpload()
            }
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.signedIn.collect { signedIn ->
                        binding.layoutSignedIn.visibility = if (signedIn) android.view.View.VISIBLE else android.view.View.GONE
                        binding.layoutSignedOut.visibility = if (signedIn) android.view.View.GONE else android.view.View.VISIBLE
                        if (signedIn) {
                            binding.tvSignedInEmail.text = getString(
                                R.string.account_signed_in_as,
                                viewModel.currentUserEmail.orEmpty()
                            )
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressIndicator.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
                        binding.btnSignIn.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        binding.tvError.visibility = if (error != null) android.view.View.VISIBLE else android.view.View.GONE
                        binding.tvError.text = error
                    }
                }
                launch {
                    viewModel.infoMessage.collect { info ->
                        if (info != null) {
                            Snackbar.make(binding.root, info, Snackbar.LENGTH_LONG).show()
                            viewModel.clearMessages()
                        }
                    }
                }
                launch {
                    viewModel.pendingMergeDecision.collect { pending ->
                        if (pending) {
                            showMergeDecisionDialog()
                        }
                    }
                }
            }
        }
    }
}
