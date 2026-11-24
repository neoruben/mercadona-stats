package io.github.neoruben.mercadonastats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruben.mercadonastats.stats.PricePoint
import io.github.neoruben.mercadonastats.stats.ProductSpendStat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val PriceUp = Color(0xFFC62828)
private val PriceDown = MercadonaGreen
private val EsLocale = Locale("es", "ES")

/** Histórico de precios de un producto: resumen, gráfica (se puede tocar cada punto) y lista de compras. */
@Composable
internal fun ProductPriceDialog(product: ProductSpendStat, onDismiss: () -> Unit) {
    val history = product.priceHistory
    val perUnit = if (product.unit == "kg") "/kg" else "/ud"
    var selected by remember(product.productKey) { mutableIntStateOf(history.lastIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = product.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Precio ${if (product.unit == "kg") "por kg" else "por unidad"} · " +
                        "${history.size} ${if (history.size == 1) "compra" else "compras"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (history.isEmpty()) {
                    Text(
                        text = "No hay precios registrados para este producto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    return@Column
                }

                PriceSummary(history)

                if (history.size >= 2) {
                    val point = history[selected.coerceIn(history.indices)]
                    Text(
                        text = "${formatLongDate(point.dateTimeIso)} · ${formatPrice(point.unitPrice)}$perUnit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MercadonaGreenDark
                    )
                    PriceLineChart(
                        points = history,
                        selectedIndex = selected,
                        onSelect = { selected = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatShortDate(history.first().dateTimeIso), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(formatShortDate(history.last().dateTimeIso), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                } else {
                    Text(
                        text = "Solo hay una compra: la gráfica aparecerá cuando vuelvas a comprarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                HorizontalDivider(color = CardBorder)
                Text(
                    text = "Compras",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MercadonaGreen
                )
                // De la más reciente a la más antigua, con la diferencia respecto a la compra anterior.
                for (index in history.indices.reversed()) {
                    val current = history[index]
                    val previous = history.getOrNull(index - 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatLongDate(current.dateTimeIso),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (previous != null) {
                                val diff = current.unitPrice - previous.unitPrice
                                if (abs(diff) >= 0.005) {
                                    Text(
                                        text = (if (diff > 0) "▲ +" else "▼ ") + formatPrice(diff),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (diff > 0) PriceUp else PriceDown
                                    )
                                }
                            }
                            Text(
                                text = formatPrice(current.unitPrice) + perUnit,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
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
private fun PriceSummary(history: List<PricePoint>) {
    val prices = history.map { it.unitPrice }
    val first = prices.first()
    val last = prices.last()
    val change = if (first > 0) (last - first) / first * 100 else 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCell("Último", formatPrice(last), TextPrimary, Modifier.weight(1f))
        SummaryCell("Mínimo", formatPrice(prices.min()), TextPrimary, Modifier.weight(1f))
        SummaryCell("Máximo", formatPrice(prices.max()), TextPrimary, Modifier.weight(1f))
        if (history.size >= 2) {
            val color = when {
                change > 0.05 -> PriceUp
                change < -0.05 -> PriceDown
                else -> TextPrimary
            }
            val sign = if (change > 0.05) "+" else ""
            SummaryCell("Variación", sign + String.format(EsLocale, "%.1f %%", change), color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = MercadonaGreenSoft, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

/** Gráfica de línea con el eje X proporcional al tiempo; al tocarla se elige el punto más cercano. */
@Composable
private fun PriceLineChart(points: List<PricePoint>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = TextSecondary)
    val prices = points.map { it.unitPrice }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val padding = ((maxPrice - minPrice) * 0.15).takeIf { it > 0 } ?: (maxPrice * 0.1).coerceAtLeast(0.05)
    val low = (minPrice - padding).coerceAtLeast(0.0)
    val high = maxPrice + padding

    // Posición horizontal según la fecha; si todas coinciden, se reparten por orden.
    val times = points.map { parseDateTime(it.dateTimeIso)?.toEpochSecond(ZoneOffset.UTC)?.toDouble() }
    val useTime = times.all { it != null } && times.first() != times.last()
    val fractions = points.indices.map { i ->
        if (useTime) {
            (times[i]!! - times.first()!!) / (times.last()!! - times.first()!!)
        } else {
            i.toDouble() / (points.size - 1)
        }
    }

    val minLabel = formatPrice(minPrice)
    val maxLabel = formatPrice(maxPrice)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val left = 44.dp.toPx()
                    val right = size.width - 10.dp.toPx()
                    val nearest = fractions.indices.minBy { i ->
                        abs(left + fractions[i].toFloat() * (right - left) - tap.x)
                    }
                    onSelect(nearest)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        fun x(i: Int) = left + fractions[i].toFloat() * (right - left)
        fun y(price: Double) = (bottom - (price - low) / (high - low) * (bottom - top)).toFloat()

        // Líneas guía del mínimo y el máximo, con su precio a la izquierda.
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        val guides = if (maxPrice > minPrice) listOf(maxPrice to maxLabel, minPrice to minLabel) else listOf(maxPrice to maxLabel)
        for ((price, label) in guides) {
            val gy = y(price)
            drawLine(CardBorder, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(measured, topLeft = Offset(0f, gy - measured.size.height / 2f))
        }

        val line = Path()
        points.indices.forEach { i ->
            if (i == 0) line.moveTo(x(i), y(prices[i])) else line.lineTo(x(i), y(prices[i]))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(points.lastIndex), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(MercadonaGreenBright.copy(alpha = 0.25f), Color.Transparent), startY = top, endY = bottom)
        )
        drawPath(
            line,
            MercadonaGreen,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val sel = selectedIndex.coerceIn(points.indices)
        drawLine(
            MercadonaGreen.copy(alpha = 0.35f),
            Offset(x(sel), top),
            Offset(x(sel), bottom),
            strokeWidth = 1.dp.toPx()
        )
        points.indices.forEach { i ->
            drawCircle(MercadonaGreen, radius = 3.dp.toPx(), center = Offset(x(i), y(prices[i])))
        }
        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x(sel), y(prices[sel])))
        drawCircle(MercadonaGreenDark, radius = 6.dp.toPx(), center = Offset(x(sel), y(prices[sel])), style = Stroke(2.5.dp.toPx()))
    }
}

private val INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val SHORT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy")
private val LONG_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun parseDateTime(iso: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(iso.trim(), INPUT_FORMAT) }.getOrNull()

private fun formatShortDate(iso: String): String =
    parseDateTime(iso)?.format(SHORT_FORMAT) ?: iso.substringBefore(" ")

private fun formatLongDate(iso: String): String =
    parseDateTime(iso)?.format(LONG_FORMAT) ?: iso.substringBefore(" ")

private fun formatPrice(amount: Double): String = String.format(EsLocale, "%.2f €", amount)
