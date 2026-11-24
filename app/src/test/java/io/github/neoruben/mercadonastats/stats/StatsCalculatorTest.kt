package io.github.neoruben.mercadonastats.stats

import io.github.neoruben.mercadonastats.data.StoredTicket
import io.github.neoruben.mercadonastats.parser.ParsedTicketItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculatorTest {

    private fun fakeTicket(id: Long, dateIso: String, total: Double, vat: Double) = StoredTicket(
        id = id,
        ticketNumber = "T-$id",
        pdfSha256 = "hash-$id",
        gmailMessageId = "msg-$id",
        storeName = "MERCADONA",
        storeAddress = "CL PRUEBA",
        dateTimeIso = dateIso,
        dateDisplay = dateIso,
        totalAmount = total,
        totalVatAmount = vat,
        items = emptyList(),
        vatBreakdown = emptyList()
    )

    @Test
    fun aggregatesWeeklyAndMonthlyTotals() {
        val tickets = listOf(
            fakeTicket(1, "2026-11-02 10:00", 20.00, 2.00), // W45, Nov
            fakeTicket(2, "2026-11-04 18:30", 30.00, 3.00), // W45, Nov
            fakeTicket(3, "2026-11-12 19:00", 50.00, 5.00), // W46, Nov
            fakeTicket(4, "2026-10-20 12:00", 40.00, 4.00)  // W43, Oct
        )

        val summary = StatsCalculator.calculate(tickets)

        assertEquals(4, summary.ticketCount)
        assertEquals(140.00, summary.totalSpent, 0.001)
        assertEquals(14.00, summary.totalVat, 0.001)
        assertEquals(35.00, summary.averageTicket, 0.001)

        assertEquals(2, summary.monthlyStats.size)
        assertEquals("2026-10", summary.monthlyStats[0].periodKey)
        assertEquals(40.00, summary.monthlyStats[0].totalAmount, 0.001)
        assertEquals("2026-11", summary.monthlyStats[1].periodKey)
        assertEquals(100.00, summary.monthlyStats[1].totalAmount, 0.001)
        assertEquals(3, summary.monthlyStats[1].ticketCount)

        assertEquals(3, summary.weeklyStats.size)
        assertEquals("2026-W43", summary.weeklyStats[0].periodKey)
        assertEquals("2026-W45", summary.weeklyStats[1].periodKey)
        assertEquals(50.00, summary.weeklyStats[1].totalAmount, 0.001)
        assertEquals("2026-W46", summary.weeklyStats[2].periodKey)
        assertEquals(50.00, summary.weeklyStats[2].totalAmount, 0.001)
    }

    @Test
    fun buildsPriceHistoryPerProductOrderedByDate() {
        val tickets = listOf(
            // Desordenados a propósito: el histórico debe salir del más antiguo al más reciente.
            fakeTicket(2, "2026-11-10 10:00", 0.0, 0.0).copy(
                items = listOf(
                    ParsedTicketItem(2.0, null, "LECHE ENTERA", 0.95, 1.90),
                    // Repetido en el mismo ticket: solo cuenta el primero.
                    ParsedTicketItem(1.0, null, "Leche  entera", 0.99, 0.99)
                )
            ),
            fakeTicket(1, "2026-10-01 10:00", 0.0, 0.0).copy(
                items = listOf(
                    // Sin precio unitario: se calcula como total / cantidad.
                    ParsedTicketItem(3.0, null, "LECHE ENTERA", null, 2.70),
                    // A peso: el precio es por kg.
                    ParsedTicketItem(0.5, "kg", "PLATANO", 2.20, 1.10)
                )
            )
        )

        val products = StatsCalculator.calculate(tickets).productStats.associateBy { it.productKey }

        val milk = products.getValue("LECHE ENTERA").priceHistory
        assertEquals(listOf("2026-10-01 10:00", "2026-11-10 10:00"), milk.map { it.dateTimeIso })
        assertEquals(0.90, milk[0].unitPrice, 0.001)
        assertEquals(0.95, milk[1].unitPrice, 0.001)

        val banana = products.getValue("PLATANO").priceHistory
        assertEquals(1, banana.size)
        assertEquals(2.20, banana[0].unitPrice, 0.001)
    }
}
