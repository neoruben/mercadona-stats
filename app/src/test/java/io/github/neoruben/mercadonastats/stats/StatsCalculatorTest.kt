package io.github.neoruben.mercadonastats.stats

import io.github.neoruben.mercadonastats.data.StoredTicket
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
}
