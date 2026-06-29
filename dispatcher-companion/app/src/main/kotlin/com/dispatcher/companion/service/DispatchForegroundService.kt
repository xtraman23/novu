package com.dispatcher.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.dispatcher.companion.ServiceLocator
import com.dispatcher.companion.asr.SpeechRecognizerAsr
import com.dispatcher.companion.export.CallArchive
import com.dispatcher.companion.export.Exporter
import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import com.dispatcher.companion.overlay.CopilotOverlay
import com.dispatcher.companion.ui.MainActivity

/**
 * Persistent dispatch-mode service (FR-204): owns ASR and the floating
 * overlay for the duration of a call; START_STICKY so HyperOS restarts it.
 */
class DispatchForegroundService : Service() {

    private var asr: SpeechRecognizerAsr? = null
    private var overlay: CopilotOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDispatch()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startDispatch()
        }
        return START_STICKY
    }

    private fun startDispatch() {
        startForeground(
            NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val session = ServiceLocator.session
        if (!session.active.value) {
            // Recognizer-based capture hears both sides on speakerphone → GOOD.
            session.start(CaptureMethodId.MICROPHONE, CaptureQuality.GOOD)
        }
        if (asr == null) {
            asr = SpeechRecognizerAsr(this) { session.onAsrEvent(it) }.also { it.start() }
        }
        if (overlay == null && android.provider.Settings.canDrawOverlays(this)) {
            overlay = CopilotOverlay(this, session).also { it.show() }
        }
    }

    private fun stopDispatch() {
        asr?.stop(); asr = null
        overlay?.hide(); overlay = null
        val session = ServiceLocator.session
        if (session.active.value) {
            session.stop(outcome = "UNKNOWN")
            archiveCall()
        }
    }

    /** Persist the full transcript and the info-only file as two separate
     *  files in Documents/DispatcherCompanion (FR-800). Never crashes a call. */
    private fun archiveCall() {
        runCatching {
            val session = ServiceLocator.session
            val ts = System.currentTimeMillis()
            Exporter.saveToDocuments(
                this, "transcript_$ts.txt",
                CallArchive.transcriptText(session.transcript.value),
            )
            Exporter.saveToDocuments(
                this, "info_$ts.txt",
                CallArchive.infoText(session.fields.value, session.rateEventsSnapshot()),
            )
        }
    }

    override fun onDestroy() {
        stopDispatch()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Dispatch Mode", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Dispatch Mode active")
            .setContentText("Listening — transcript and PDWCR updating live")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "dispatch_mode"
        private const val NOTIFICATION_ID = 41
        const val ACTION_STOP = "com.dispatcher.companion.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DispatchForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DispatchForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
