package io.github.neoruben.mercadonastats.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.neoruben.mercadonastats.MainActivity
import io.github.neoruben.mercadonastats.data.TicketDatabaseHelper
import io.github.neoruben.mercadonastats.mail.ImapMercadonaService
import io.github.neoruben.mercadonastats.mail.ImapSettingsStore
import io.github.neoruben.mercadonastats.ui.SyncProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sincroniza los tickets por IMAP como servicio en primer plano, con una notificación de progreso
 * y un wake lock, para que no se corte al apagarse la pantalla o al salir de la app.
 */
class ImapSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var importer: ImapTicketImporter? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatorio en cada arranque con startForegroundService, aunque ya esté sincronizando.
        startInForeground(progressNotification(SyncProgress(0, 0, "Preparando la sincronización...")))
        if (job?.isActive == true) return START_NOT_STICKY

        val settings = ImapSettingsStore(applicationContext).load()
        if (settings == null) {
            stopNow()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        val worker = ImapTicketImporter(applicationContext, TicketDatabaseHelper.get(applicationContext))
        importer = worker
        ImapSyncBus.update(ImapSyncState.Running(SyncProgress(0, 0, "Conectando con ${settings.host}...")))

        job = scope.launch {
            val result = runCatching {
                worker.run(settings) { current, total, message ->
                    val progress = SyncProgress(current, total, message)
                    ImapSyncBus.update(ImapSyncState.Running(progress))
                    notify(PROGRESS_ID, progressNotification(progress))
                }
            }
            val error = result.exceptionOrNull()?.let {
                Log.w(TAG, "Sincronización IMAP fallida", it)
                ImapMercadonaService.friendly(it, settings.host).message
            }
            val message = result.getOrNull()
            ImapSyncBus.update(ImapSyncState.Finished(message, error))
            notify(RESULT_ID, resultNotification(message ?: error.orEmpty(), error != null))
            stopNow()
        }
        return START_NOT_STICKY
    }

    /** Android 15 limita los servicios dataSync a 6 horas al día. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        importer?.cancel()
    }

    override fun onDestroy() {
        importer?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startInForeground(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, PROGRESS_ID, notification, type)
    }

    private fun stopNow() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MercaStats:imapSync").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun progressNotification(progress: SyncProgress) =
        NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Importando tickets del correo")
            .setContentText(progress.message)
            .setProgress(progress.total, progress.current, progress.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun resultNotification(text: String, isError: Boolean) =
        NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(
                if (isError) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done
            )
            .setContentTitle(if (isError) "No se pudo completar la importación" else "Tickets importados")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notify(id: Int, notification: android.app.Notification) {
        // Sin permiso de notificaciones no se muestran, pero la sincronización sigue igual.
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Importación de tickets", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return CHANNEL_ID
    }

    companion object {
        private const val TAG = "MercadonaImap"
        private const val CHANNEL_ID = "imap_sync"
        private const val PROGRESS_ID = 1001
        private const val RESULT_ID = 1002
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        /** Arranca la sincronización; si ya hay una en marcha, no hace nada. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ImapSyncService::class.java))
        }
    }
}
