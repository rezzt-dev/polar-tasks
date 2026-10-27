package app.polar.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.polar.R
import app.polar.receiver.NotificationActionReceiver
import app.polar.ui.dialog.SnoozeSheet
import app.polar.ui.viewmodel.SnoozeTarget
import app.polar.ui.viewmodel.SnoozeViewModel
import app.polar.util.DateUtils
import app.polar.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Botón "posponer" de una notificación: muestra [SnoozeSheet] sobre lo que haya en pantalla, sin
 * abrir la app, y al elegir pospone el aviso y retira la notificación. Si se cierra sin elegir, la
 * notificación sigue ahí.
 */
@AndroidEntryPoint
class SnoozeActivity : BaseActivity() {

    private val viewModel: SnoozeViewModel by viewModels()
    private var notificationId = -1

    override fun windowStyleOverlay(): Int = R.style.ThemeOverlay_Polar_Transparent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = targetFrom(intent.extras)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (target == null) {
            finish()
            return
        }

        supportFragmentManager.setFragmentResultListener(SnoozeSheet.REQUEST_KEY, this) { _, result ->
            val until = SnoozeSheet.untilFrom(result)
            if (until != null) viewModel.snooze(target, until) else finish()
        }
        observeViewModel()

        if (savedInstanceState == null) {
            SnoozeSheet.newInstance(intent.getStringExtra(EXTRA_TITLE))
                .show(supportFragmentManager, SnoozeSheet.TAG)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.outcome.collect { outcome ->
                        when (outcome) {
                            null -> return@collect
                            is SnoozeViewModel.Outcome.Snoozed -> toast(
                                getString(R.string.snoozed_until, DateUtils.formatDateTime(this@SnoozeActivity, outcome.until))
                            )
                            SnoozeViewModel.Outcome.NotPending -> toast(getString(R.string.snooze_not_pending))
                        }
                        if (notificationId != -1) NotificationHelper.cancel(this@SnoozeActivity, notificationId)
                        finish()
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        if (error != null) {
                            toast(error)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        // Mismos extras que los botones de la notificación (NotificationActionReceiver).
        const val EXTRA_TASK_ID = NotificationActionReceiver.EXTRA_TASK_ID
        const val EXTRA_REMINDER_ID = NotificationActionReceiver.EXTRA_REMINDER_ID
        const val EXTRA_NOTIFICATION_ID = NotificationActionReceiver.EXTRA_NOTIFICATION_ID
        const val EXTRA_TITLE = "EXTRA_TITLE"

        private fun targetFrom(extras: Bundle?): SnoozeTarget? {
            val reminderId = extras?.getLong(EXTRA_REMINDER_ID, -1L) ?: -1L
            val taskId = extras?.getLong(EXTRA_TASK_ID, -1L) ?: -1L
            return when {
                reminderId != -1L -> SnoozeTarget(SnoozeTarget.Kind.REMINDER, reminderId)
                taskId != -1L -> SnoozeTarget(SnoozeTarget.Kind.TASK, taskId)
                else -> null
            }
        }
    }
}
