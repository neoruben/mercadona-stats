package io.github.neoruben.mercadonastats

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.github.neoruben.mercadonastats.data.InsertTicketStatus
import io.github.neoruben.mercadonastats.data.StoredTicket
import io.github.neoruben.mercadonastats.data.TicketDatabaseHelper
import io.github.neoruben.mercadonastats.gmail.GmailMercadonaService
import io.github.neoruben.mercadonastats.gmail.GmailOAuthManager
import io.github.neoruben.mercadonastats.parser.MercadonaTicketParser
import io.github.neoruben.mercadonastats.parser.PdfTextExtractor
import io.github.neoruben.mercadonastats.stats.SpendSummary
import io.github.neoruben.mercadonastats.stats.StatsCalculator
import io.github.neoruben.mercadonastats.ui.MercadonaMainScaffold
import io.github.neoruben.mercadonastats.ui.SyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: TicketDatabaseHelper
    private val gmailService = GmailMercadonaService()
    private val prefs by lazy { getSharedPreferences("mercadona_sync_prefs", MODE_PRIVATE) }

    companion object {
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }

    private var accessToken by mutableStateOf<String?>(null)
    private var tickets by mutableStateOf<List<StoredTicket>>(emptyList())
    private var spendSummary by mutableStateOf(StatsCalculator.calculate(emptyList()))
    private var syncProgress by mutableStateOf<SyncProgress?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    private val authResolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(activityResult.data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    accessToken = token
                    prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, true).apply()
                    errorMessage = null
                    syncFromGmail(token)
                } else {
                    syncProgress = null
                    errorMessage = "Google no devolvió un token de acceso."
                }
            } catch (e: Exception) {
                syncProgress = null
                errorMessage = "Error al completar la autorización: ${e.message}"
            }
        } else {
            syncProgress = null
            errorMessage = "Autorización cancelada."
        }
    }

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
        dbHelper = TicketDatabaseHelper(applicationContext)
        reloadFromDatabase()
        if (prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)) {
            trySilentAutoSync()
        }

        setContent {
            MaterialTheme {
                MercadonaMainScaffold(
                    tickets = tickets,
                    summary = spendSummary,
                    isGmailConnected = !accessToken.isNullOrBlank(),
                    syncProgress = syncProgress,
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    onSyncGmailClick = {
                        val token = accessToken
                        if (token.isNullOrBlank()) {
                            authorizeAndSyncGmail()
                        } else {
                            syncFromGmail(token)
                        }
                    },
                    onDisconnectGmailClick = {
                        accessToken = null
                        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, false).apply()
                        statusMessage = "Sesión de Gmail desconectada."
                    },
                    onImportLocalPdfsClick = {
                        openPdfsLauncher.launch(arrayOf("application/pdf"))
                    }
                )
            }
        }
    }

    private fun reloadFromDatabase() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dbHelper.getAllTickets() }
            tickets = loaded
            spendSummary = StatsCalculator.calculate(loaded)
        }
    }

    private fun trySilentAutoSync() {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GmailOAuthManager.GMAIL_READONLY_SCOPE)))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(authRequest)
            .addOnSuccessListener { result ->
                if (!result.hasResolution()) {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        accessToken = token
                        syncFromGmail(token)
                    }
                }
            }
    }

    private fun authorizeAndSyncGmail() {
        errorMessage = null
        syncProgress = SyncProgress(0, 0, "Conectando con Gmail (solo lectura)...")

        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GmailOAuthManager.GMAIL_READONLY_SCOPE)))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(authRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        authResolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        syncProgress = null
                        errorMessage = "Se requiere consentimiento pero no se recibió PendingIntent."
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        accessToken = token
                        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, true).apply()
                        syncFromGmail(token)
                    } else {
                        syncProgress = null
                        errorMessage = "No se obtuvo token de acceso de Google."
                    }
                }
            }
            .addOnFailureListener { e ->
                syncProgress = null
                errorMessage = "Error de autorización Google: ${e.message}"
            }
    }

    private fun syncFromGmail(token: String) {
        errorMessage = null
        statusMessage = null
        syncProgress = SyncProgress(0, 0, "Buscando correos de Mercadona en Gmail...")

        lifecycleScope.launch {
            try {
                val allIdsResult = gmailService.listMercadonaMessageIds(token)
                val allIds = allIdsResult.getOrElse { err ->
                    syncProgress = null
                    errorMessage = err.message ?: "Error al listar correos de Gmail."
                    return@launch
                }

                withContext(Dispatchers.IO) { dbHelper.deleteTicketsWithoutItems() }
                val knownIds = withContext(Dispatchers.IO) { dbHelper.getKnownGmailMessageIds() }
                val newIds = allIds.filterNot { it in knownIds }

                if (newIds.isEmpty()) {
                    syncProgress = null
                    statusMessage = "Todo al día: los ${allIds.size} correos ya estaban en SQLite."
                    return@launch
                }

                var insertedCount = 0
                var duplicateCount = 0
                var failedCount = 0

                newIds.forEachIndexed { index, msgId ->
                    syncProgress = SyncProgress(
                        current = index + 1,
                        total = newIds.size,
                        message = "Procesando ${index + 1} de ${newIds.size} correos nuevos..."
                    )

                    try {
                        val details = gmailService.getMessageDetails(token, msgId)
                        val pdfAtt = details.pdfAttachments.firstOrNull()
                        if (pdfAtt == null) {
                            failedCount++
                        } else {
                            val pdfBytes = gmailService.downloadPdfAttachment(
                                accessToken = token,
                                messageId = msgId,
                                attachmentId = pdfAtt.attachmentId
                            )
                            val status = parseAndSavePdfBytes(pdfBytes, gmailMessageId = msgId)
                            when (status) {
                                InsertTicketStatus.INSERTED -> insertedCount++
                                else -> duplicateCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MercadonaSync", "Error procesando msgId=$msgId: ${e.message}")
                        failedCount++
                    }
                }

                val refreshed = withContext(Dispatchers.IO) { dbHelper.getAllTickets() }
                tickets = refreshed
                spendSummary = StatsCalculator.calculate(refreshed)
                syncProgress = null
                statusMessage = buildString {
                    append("Sincronización completada: $insertedCount nuevos")
                    if (duplicateCount > 0) append(", $duplicateCount duplicados omitidos")
                    if (failedCount > 0) append(", $failedCount sin PDF válido")
                }
            } catch (e: Exception) {
                syncProgress = null
                errorMessage = "Error durante la sincronización: ${e.message}"
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

                    val status = parseAndSavePdfBytes(bytes, gmailMessageId = null)
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
        gmailMessageId: String?
    ): InsertTicketStatus = withContext(Dispatchers.IO) {
        val sha256 = StatsCalculator.sha256Hex(pdfBytes)
        val rawText = PdfTextExtractor.extractText(applicationContext, pdfBytes)
        val parsedTicket = MercadonaTicketParser.parse(rawText)
        dbHelper.insertTicketIfNew(
            ticket = parsedTicket,
            pdfSha256 = sha256,
            gmailMessageId = gmailMessageId
        )
    }
}
