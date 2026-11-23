package io.github.neoruben.mercadonastats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruben.mercadonastats.data.StoredTicket
import io.github.neoruben.mercadonastats.stats.PeriodSpendStat
import io.github.neoruben.mercadonastats.stats.ProductSpendStat
import io.github.neoruben.mercadonastats.stats.SpendSummary
import java.util.Locale

internal val MercadonaGreenDark = Color(0xFF005C26)
internal val MercadonaGreen = Color(0xFF007A33)
internal val MercadonaGreenBright = Color(0xFF009E44)
internal val MercadonaGreenSoft = Color(0xFFE9F5EE)
internal val MercadonaAmber = Color(0xFFD97706)
internal val MercadonaAmberSoft = Color(0xFFFEF3C7)
internal val BackgroundScreen = Color(0xFFF5F7F6)
internal val CardBorder = Color(0xFFE4EAE6)
internal val TextPrimary = Color(0xFF142119)
internal val TextSecondary = Color(0xFF5D6D63)

data class SyncProgress(
    val current: Int,
    val total: Int,
    val message: String
)

@Composable
fun MercadonaMainScaffold(
    tickets: List<StoredTicket>,
    summary: SpendSummary,
    isGmailConnected: Boolean,
    imapAccount: String?,
    syncProgress: SyncProgress?,
    statusMessage: String?,
    errorMessage: String?,
    onSyncGmailClick: () -> Unit,
    onDisconnectGmailClick: () -> Unit,
    onImapSettingsClick: () -> Unit,
    onImportLocalPdfsClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTicketDetail by remember { mutableStateOf<StoredTicket?>(null) }
    val dateRangeText = remember(tickets) {
        if (tickets.isEmpty()) {
            "Sin compras registradas"
        } else {
            val oldest = tickets.last().dateDisplay.substringBefore(" ")
            val newest = tickets.first().dateDisplay.substringBefore(" ")
            if (oldest == newest) "Fecha: $newest" else "Del $oldest al $newest"
        }
    }

    Scaffold(
        containerColor = BackgroundScreen,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MercadonaGreenDark, MercadonaGreen, MercadonaGreenBright)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MercaStats",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = dateRangeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC8EBD5)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = formatEuro(summary.totalSpent),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${summary.ticketCount} tickets",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDFF5E7)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MercadonaGreen,
                    selectedTextColor = MercadonaGreen,
                    indicatorColor = MercadonaGreenSoft,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = "Tickets") },
                    label = { Text("Tickets", fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Estadísticas") },
                    label = { Text("Estadísticas", fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.ShoppingCart, contentDescription = "Productos") },
                    label = { Text("Productos", fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = itemColors
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TicketsTabContent(
                    tickets = tickets,
                    isGmailConnected = isGmailConnected,
                    imapAccount = imapAccount,
                    syncProgress = syncProgress,
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    onSyncGmailClick = onSyncGmailClick,
                    onDisconnectGmailClick = onDisconnectGmailClick,
                    onImapSettingsClick = onImapSettingsClick,
                    onImportLocalPdfsClick = onImportLocalPdfsClick,
                    onTicketClick = { selectedTicketDetail = it }
                )
                1 -> StatsTabContent(summary = summary)
                else -> ProductsTabContent(products = summary.productStats)
            }
        }
    }

    selectedTicketDetail?.let { ticket ->
        TicketDetailDialog(
            ticket = ticket,
            onDismiss = { selectedTicketDetail = null }
        )
    }
}

@Composable
private fun TicketsTabContent(
    tickets: List<StoredTicket>,
    isGmailConnected: Boolean,
    imapAccount: String?,
    syncProgress: SyncProgress?,
    statusMessage: String?,
    errorMessage: String?,
    onSyncGmailClick: () -> Unit,
    onDisconnectGmailClick: () -> Unit,
    onImapSettingsClick: () -> Unit,
    onImportLocalPdfsClick: () -> Unit,
    onTicketClick: (StoredTicket) -> Unit
) {
    val isSyncing = syncProgress != null
    // Si hay una cuenta IMAP guardada, es la que se usa para sincronizar.
    val usesImap = imapAccount != null
    val isConnected = usesImap || isGmailConnected
    val connectionText = when {
        usesImap -> "Correo: $imapAccount"
        isGmailConnected -> "Gmail conectado (solo lectura)"
        else -> "Correo sin conectar"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) MercadonaGreenBright else Color(0xFFB0BEC5))
                            )
                            Text(
                                text = connectionText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) MercadonaGreen else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (usesImap) {
                            Text(
                                text = "Ajustes",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isSyncing, onClick = onImapSettingsClick)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else if (isGmailConnected) {
                            Text(
                                text = "Desconectar",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onDisconnectGmailClick)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSyncGmailClick,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MercadonaGreen),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Sincronizar" else "Conectar Gmail",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onImportLocalPdfsClick,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MercadonaGreen.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = null,
                                tint = MercadonaGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF", color = MercadonaGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!usesImap) {
                        Text(
                            text = "¿Otro correo o una etiqueta concreta? Conectar por IMAP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MercadonaGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isSyncing, onClick = onImapSettingsClick)
                                .padding(vertical = 4.dp)
                        )
                    }

                    if (syncProgress != null) {
                        val progress = if (syncProgress.total > 0) {
                            syncProgress.current.toFloat() / syncProgress.total.toFloat()
                        } else {
                            0f
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (syncProgress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = MercadonaGreen,
                                    trackColor = MercadonaGreenSoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = MercadonaGreen,
                                    trackColor = MercadonaGreenSoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                            Text(
                                text = syncProgress.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    } else if (!statusMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MercadonaGreenSoft
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MercadonaGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MercadonaGreenDark
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB71C1C),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (tickets.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = MercadonaGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Sin tickets todavía",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Pulsa \"Conectar Gmail\", conecta tu correo por IMAP o usa \"PDF\" para importar tus compras de Mercadona.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val groupedByMonth = tickets.groupBy { it.dateTimeIso.take(7) }
            val monthKeys = groupedByMonth.keys.toList()

            items(monthKeys, key = { it }) { monthKey ->
                val monthTickets = groupedByMonth[monthKey].orEmpty()
                val monthTotal = monthTickets.sumOf { it.totalAmount }
                val isFirstMonth = monthKey == monthKeys.firstOrNull()
                var expanded by remember(monthKey) { mutableStateOf(isFirstMonth) }

                MonthTicketsGroup(
                    monthTitle = formatMonthYearTitle(monthKey),
                    monthTotal = monthTotal,
                    tickets = monthTickets,
                    expanded = expanded,
                    onToggleExpand = { expanded = !expanded },
                    onTicketClick = onTicketClick
                )
            }
        }
    }
}

@Composable
private fun MonthTicketsGroup(
    monthTitle: String,
    monthTotal: Double,
    tickets: List<StoredTicket>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onTicketClick: (StoredTicket) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundScreen),
        border = BorderStroke(1.dp, MercadonaGreen.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MercadonaGreen)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${tickets.size} ${if (tickets.size == 1) "compra" else "compras"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD8F3DC)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = formatEuro(monthTotal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Plegar" else "Desplegar",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundScreen)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tickets.forEach { ticket ->
                        TicketCard(ticket = ticket, onClick = { onTicketClick(ticket) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: StoredTicket, onClick: () -> Unit) {
    val (dayBadge, monthBadge) = remember(ticket.dateTimeIso) {
        extractDayMonthBadge(ticket.dateTimeIso)
    }
    val timeOnly = remember(ticket.dateDisplay) {
        ticket.dateDisplay.substringAfter(" ", "")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MercadonaGreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dayBadge,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MercadonaGreenDark,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = monthBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MercadonaGreen,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = ticket.storeAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$timeOnly · ${ticket.items.size} prod.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "· IVA ${formatEuro(ticket.totalVatAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MercadonaGreenSoft
            ) {
                Text(
                    text = formatEuro(ticket.totalAmount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MercadonaGreenDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StatsTabContent(summary: SpendSummary) {
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Mensual, 1 = Semanal
    val statsList = if (modeTab == 0) summary.monthlyStats else summary.weeklyStats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryKpiCard(
                    title = "Gasto Total",
                    value = formatEuro(summary.totalSpent),
                    icon = Icons.Rounded.Euro,
                    accent = MercadonaGreen,
                    bg = MercadonaGreenSoft,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Ticket Medio",
                    value = formatEuro(summary.averageTicket),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    accent = Color(0xFF0284C7),
                    bg = Color(0xFFE0F2FE),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryKpiCard(
                    title = "IVA Soportado",
                    value = formatEuro(summary.totalVat),
                    icon = Icons.Rounded.Storefront,
                    accent = MercadonaAmber,
                    bg = MercadonaAmberSoft,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Compras",
                    value = summary.ticketCount.toString(),
                    icon = Icons.Rounded.ShoppingBag,
                    accent = Color(0xFF7C3AED),
                    bg = Color(0xFFEDE9FE),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Evolución del gasto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        SegmentedSwitch(
                            options = listOf("Mes", "Semana"),
                            selectedIndex = modeTab,
                            onSelect = { modeTab = it }
                        )
                    }

                    if (statsList.isEmpty()) {
                        Text(
                            text = "Sin datos suficientes todavía.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        SpendBarChart(
                            stats = statsList.takeLast(12),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        items(statsList.reversed(), key = { it.periodKey }) { stat ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stat.periodLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${stat.ticketCount} ${if (stat.ticketCount == 1) "compra" else "compras"} · IVA ${formatEuro(stat.totalVatAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = formatEuro(stat.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MercadonaGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundScreen)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MercadonaGreenDark else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SpendBarChart(
    stats: List<PeriodSpendStat>,
    modifier: Modifier = Modifier
) {
    val maxAmount = (stats.maxOfOrNull { it.totalAmount } ?: 1.0).coerceAtLeast(1.0)
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .height(195.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEachIndexed { index, stat ->
            val ratio = (stat.totalAmount / maxAmount).toFloat().coerceIn(0.06f, 1f)
            val isLatest = index == stats.lastIndex
            val barBrush = if (isLatest) {
                Brush.verticalGradient(listOf(MercadonaGreenBright, MercadonaGreenDark))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF66BB6A), MercadonaGreen))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .width(56.dp)
                    .height(190.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.0f€", stat.totalAmount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLatest) MercadonaGreenDark else TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height((125 * ratio).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(barBrush)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stat.periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLatest) TextPrimary else TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TicketDetailDialog(
    ticket: StoredTicket,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Ticket ${ticket.dateDisplay}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${ticket.storeAddress} · #${ticket.ticketNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = "Productos (${ticket.items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MercadonaGreen
                    )
                }
                items(ticket.items) { item ->
                    val qtyText = if (item.unit == "kg") {
                        String.format(Locale.US, "%.3f kg", item.quantity)
                    } else {
                        "${item.quantity.toInt()}x"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$qtyText ${item.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatEuro(item.totalPrice),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = CardBorder)
                    Text(
                        text = "Desglose IVA",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MercadonaGreen
                    )
                }
                items(ticket.vatBreakdown) { vat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IVA ${vat.vatRatePercent}% (Base ${formatEuro(vat.baseAmount)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = formatEuro(vat.vatAmount),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = CardBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TOTAL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = formatEuro(ticket.totalAmount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MercadonaGreen
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = MercadonaGreen, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProductsTabContent(products: List<ProductSpendStat>) {
    var sortTab by remember { mutableIntStateOf(0) } // 0 = Más comprados, 1 = Mayor importe
    var searchQuery by remember { mutableStateOf("") }
    var selectedProductKey by remember { mutableStateOf<String?>(null) }

    selectedProductKey
        ?.let { key -> products.firstOrNull { it.productKey == key } }
        ?.let { product ->
            ProductPriceDialog(product = product, onDismiss = { selectedProductKey = null })
        }

    val filtered = remember(products, searchQuery, sortTab) {
        val q = searchQuery.trim()
        val base = if (q.isEmpty()) {
            products
        } else {
            products.filter { it.displayName.contains(q, ignoreCase = true) }
        }
        if (sortTab == 0) {
            base.sortedWith(
                compareByDescending<ProductSpendStat> { it.purchaseCount }
                    .thenByDescending { it.totalQuantity }
                    .thenByDescending { it.totalAmount }
            )
        } else {
            base.sortedByDescending { it.totalAmount }
        }
    }

    val maxBarValue = remember(filtered, sortTab) {
        if (filtered.isEmpty()) 1.0
        else if (sortTab == 0) filtered.maxOf { it.purchaseCount.toDouble() }.coerceAtLeast(1.0)
        else filtered.maxOf { it.totalAmount }.coerceAtLeast(1.0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ranking (${filtered.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        SegmentedSwitch(
                            options = listOf("Frecuencia", "Gasto"),
                            selectedIndex = sortTab,
                            onSelect = { sortTab = it }
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar producto (ej. llet, pollo...)") },
                        supportingText = { Text("Pulsa un producto para ver cómo ha cambiado su precio.") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Limpiar",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MercadonaGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = BackgroundScreen,
                            unfocusedContainerColor = BackgroundScreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = if (products.isEmpty()) {
                            "Todavía no hay productos registrados. Sincroniza tus tickets primero."
                        } else {
                            "No se han encontrado productos para \"$searchQuery\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(filtered, key = { _, item -> item.productKey }) { index, product ->
                val ratio = if (sortTab == 0) {
                    (product.purchaseCount / maxBarValue).toFloat().coerceIn(0.05f, 1f)
                } else {
                    (product.totalAmount / maxBarValue).toFloat().coerceIn(0.05f, 1f)
                }
                val qtyText = if (product.unit == "kg") {
                    String.format(Locale("es", "ES"), "%.2f kg", product.totalQuantity)
                } else {
                    val qInt = product.totalQuantity.toInt()
                    if (kotlin.math.abs(product.totalQuantity - qInt) < 0.01) {
                        "$qInt uds"
                    } else {
                        String.format(Locale("es", "ES"), "%.2f uds", product.totalQuantity)
                    }
                }

                val rankBg = when (index) {
                    0 -> Color(0xFFFEF3C7)
                    1 -> Color(0xFFF1F5F9)
                    2 -> Color(0xFFFFEDD5)
                    else -> MercadonaGreenSoft
                }
                val rankColor = when (index) {
                    0 -> Color(0xFFB45309)
                    1 -> Color(0xFF475569)
                    2 -> Color(0xFFC2410C)
                    else -> MercadonaGreenDark
                }

                Card(
                    onClick = { selectedProductKey = product.productKey },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(rankBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = rankColor
                                    )
                                }
                                Column {
                                    Text(
                                        text = product.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$qtyText · ${product.purchaseCount} ${if (product.purchaseCount == 1) "compra" else "compras"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Text(
                                text = formatEuro(product.totalAmount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MercadonaGreen
                            )
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            color = MercadonaGreenBright,
                            trackColor = MercadonaGreenSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

private val MONTH_SHORT_ES = mapOf(
    "01" to "ENE", "02" to "FEB", "03" to "MAR", "04" to "ABR",
    "05" to "MAY", "06" to "JUN", "07" to "JUL", "08" to "AGO",
    "09" to "SEP", "10" to "OCT", "11" to "NOV", "12" to "DIC"
)

private val MONTH_FULL_ES = mapOf(
    "01" to "Enero", "02" to "Febrero", "03" to "Marzo", "04" to "Abril",
    "05" to "Mayo", "06" to "Junio", "07" to "Julio", "08" to "Agosto",
    "09" to "Septiembre", "10" to "Octubre", "11" to "Noviembre", "12" to "Diciembre"
)

private fun formatMonthYearTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    return if (parts.size == 2) {
        val monthName = MONTH_FULL_ES[parts[1]] ?: parts[1]
        "$monthName ${parts[0]}"
    } else {
        monthKey
    }
}

private fun extractDayMonthBadge(dateTimeIso: String): Pair<String, String> {
    val datePart = dateTimeIso.substringBefore(" ")
    val parts = datePart.split("-")
    return if (parts.size == 3) {
        val day = parts[2]
        val month = MONTH_SHORT_ES[parts[1]] ?: parts[1]
        day to month
    } else {
        "--" to "---"
    }
}

private fun formatEuro(amount: Double): String {
    return String.format(Locale("es", "ES"), "%.2f €", amount)
}
