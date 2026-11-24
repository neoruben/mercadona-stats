package io.github.neoruben.mercadonastats

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.neoruben.mercadonastats.data.InsertTicketStatus
import io.github.neoruben.mercadonastats.data.StoredTicket
import io.github.neoruben.mercadonastats.data.TicketDatabaseHelper
import io.github.neoruben.mercadonastats.mail.ImapMercadonaService
import io.github.neoruben.mercadonastats.mail.ImapSettings
import io.github.neoruben.mercadonastats.mail.ImapSettingsStore
import io.github.neoruben.mercadonastats.stats.SpendSummary
import io.github.neoruben.mercadonastats.stats.StatsCalculator
import io.github.neoruben.mercadonastats.sync.ImapSyncBus
import io.github.neoruben.mercadonastats.sync.ImapSyncService
import io.github.neoruben.mercadonastats.sync.ImapSyncState
import io.github.neoruben.mercadonastats.sync.savePdfTicket
import io.github.neoruben.mercadonastats.ui.ImapSettingsDialog
import io.github.neoruben.mercadonastats.ui.ImapSettingsInput
import io.github.neoruben.mercadonastats.ui.MercadonaMainScaffold
import io.github.neoruben.mercadonastats.ui.SyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: TicketDatabaseHelper
    private val imapService = ImapMercadonaService()
    private val imapStore by lazy { ImapSettingsStore(applicationContext) }

    private var tickets by mutableStateOf<List<StoredTicket>>(emptyList())
    private var spendSummary by mutableStateOf(StatsCalculator.calculate(emptyList()))
    private var syncProgress by mutableStateOf<SyncProgress?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    // Cuenta de correo IMAP con la que se sincronizan los tickets
    private var imapSettings by mutableStateOf<ImapSettings?>(null)
    private var showImapDialog by mutableStateOf(false)
    private var imapDialogBusy by mutableStateOf(false)
    private var imapDialogError by mutableStateOf<String?>(null)

    // Solo para ver el progreso en la notificación: la sincronización funciona igual sin permiso.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val openPdfsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            importLocalPdfs(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dbHelper = TicketDatabaseHelper.get(applicationContext)
        reloadFromDatabase()
        observeImapSync()
        imapSettings = imapStore.load()
        if (imapSettings != null && savedInstanceState == null) {
            syncFromImap()
        }
        if (savedInstanceState == null) {
            handleSharedPdfIntent(intent)
        }

        setContent {
            MaterialTheme {
                MercadonaMainScaffold(
                    tickets = tickets,
                    summary = spendSummary,
                    imapAccount = imapSettings?.email,
                    syncProgress = syncProgress,
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    onSyncClick = {
                        if (imapSettings != null) {
                            syncFromImap()
                        } else {
                            imapDialogError = null
                            showImapDialog = true
                        }
                    },
                    onImapSettingsClick = {
                        imapDialogError = null
                        showImapDialog = true
                    },
                    onImportLocalPdfsClick = {
                        openPdfsLauncher.launch(arrayOf("application/pdf"))
                    }
                )

                if (showImapDialog) {
                    ImapSettingsDialog(
                        current = imapSettings,
                        busy = imapDialogBusy,
                        error = imapDialogError,
                        onSave = ::saveImapSettings,
                        onForget = ::forgetImapAccount,
                        onDismiss = { showImapDialog = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedPdfIntent(intent)
    }

    /** Imports PDFs received through "Share" (SEND / SEND_MULTIPLE) or "Open with" (VIEW). */
    private fun handleSharedPdfIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            Intent.ACTION_VIEW -> intent.data
                ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE }
                ?.let { listOf(it) }
                .orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        // Consume the intent so a recreation doesn't import the same files again.
        intent.action = null
        importLocalPdfs(uris)
    }

    private fun reloadFromDatabase() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dbHelper.getAllTickets() }
            tickets = loaded
            spendSummary = StatsCalculator.calculate(loaded)
        }
    }

    /** Prueba la conexión con los datos del formulario; si va bien, los guarda y sincroniza. */
    private fun saveImapSettings(input: ImapSettingsInput) {
        val settings = ImapSettings(
            host = input.host,
            email = input.email,
            password = input.password.ifBlank { imapSettings?.password.orEmpty() },
            folder = input.folder.ifBlank { ImapSettings.DEFAULT_FOLDER }
        )
        imapDialogBusy = true
        imapDialogError = null

        lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    imapService.open(settings).use { it.findMercadonaMails().size }
                }
                withContext(Dispatchers.IO) { imapStore.save(settings) }
                imapSettings = settings
                showImapDialog = false
                errorMessage = null
                if (found == 0) {
                    statusMessage = "Correo conectado, pero no hay tickets de Mercadona en «${settings.folder}»."
                } else {
                    syncFromImap()
                }
            } catch (e: Exception) {
                imapDialogError = ImapMercadonaService.friendly(e, settings.host).message
            } finally {
                imapDialogBusy = false
            }
        }
    }

    private fun forgetImapAccount() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                imapStore.clear()
                dbHelper.clearSeenImapMessages()
            }
            imapSettings = null
            showImapDialog = false
            statusMessage = "Cuenta de correo olvidada. Tus tickets se conservan."
        }
    }

    /** Lanza la sincronización IMAP en un servicio en primer plano; su progreso llega por [ImapSyncBus]. */
    private fun syncFromImap() {
        if (syncProgress != null || ImapSyncBus.isRunning) return
        errorMessage = null
        statusMessage = null
        askForNotificationPermission()
        ImapSyncService.start(this)
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun observeImapSync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ImapSyncBus.state.collect { state -> showImapSyncState(state) }
            }
        }
    }

    private fun showImapSyncState(state: ImapSyncState) {
        when (state) {
            ImapSyncState.Idle -> Unit
            is ImapSyncState.Running -> syncProgress = state.progress
            is ImapSyncState.Finished -> {
                syncProgress = null
                statusMessage = state.message
                errorMessage = state.error
                reloadFromDatabase()
                ImapSyncBus.consume(state)
            }
        }
    }

    private fun importLocalPdfs(uris: List<Uri>) {
        errorMessage = null
        statusMessage = null
        syncProgress = SyncProgress(0, uris.size, "Importando ${uris.size} PDF(s)...")

        lifecycleScope.launch {
            var inserted = 0
            var duplicates = 0
            var failed = 0

            uris.forEachIndexed { index, uri ->
                syncProgress = SyncProgress(index + 1, uris.size, "Leyendo PDF ${index + 1} de ${uris.size}...")
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: throw IllegalStateException("No se pudo abrir el archivo.")

                    val status = parseAndSavePdfBytes(bytes, messageId = null)
                    if (status == InsertTicketStatus.INSERTED) inserted++ else duplicates++
                } catch (_: Exception) {
                    failed++
                }
            }

            val refreshed = withContext(Dispatchers.IO) { dbHelper.getAllTickets() }
            tickets = refreshed
            spendSummary = StatsCalculator.calculate(refreshed)
            syncProgress = null
            statusMessage = "Importación: $inserted nuevos, $duplicates duplicados, $failed errores."
        }
    }

    private suspend fun parseAndSavePdfBytes(
        pdfBytes: ByteArray,
        messageId: String?
    ): InsertTicketStatus = withContext(Dispatchers.IO) {
        savePdfTicket(applicationContext, dbHelper, pdfBytes, messageId)
    }
}
