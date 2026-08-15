/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

sealed interface InstallationEvent {
    val sessionId: Int

    data class AwaitingConfirmation(override val sessionId: Int) : InstallationEvent

    data class Success(override val sessionId: Int, val packageName: String?) : InstallationEvent

    data class Failure(
        override val sessionId: Int,
        val status: Int,
        val message: String,
    ) : InstallationEvent
}

object InstallationEvents {
    private val mutableEvents = MutableSharedFlow<InstallationEvent>(replay = 1, extraBufferCapacity = 4)
    private val latestBySession = ConcurrentHashMap<Int, InstallationEvent>()
    val events = mutableEvents.asSharedFlow()

    internal fun emit(event: InstallationEvent) {
        latestBySession[event.sessionId] = event
        mutableEvents.tryEmit(event)
    }

    fun latest(sessionId: Int): InstallationEvent? = latestBySession[sessionId]
}

class InstallStatusReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation == null) {
                    InstallationEvents.emit(
                        InstallationEvent.Failure(
                            sessionId,
                            status,
                            "Android no entregó la confirmación de instalación.",
                        ),
                    )
                } else {
                    InstallationEvents.emit(InstallationEvent.AwaitingConfirmation(sessionId))
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallationEvents.emit(
                InstallationEvent.Success(
                    sessionId,
                    intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                ),
            )
            else -> InstallationEvents.emit(
                InstallationEvent.Failure(
                    sessionId,
                    status,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "La instalación fue rechazada por Android.",
                ),
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS =
            "io.github.n_a_monterocarvajal.frankupdater.action.INSTALL_STATUS"
    }
}
