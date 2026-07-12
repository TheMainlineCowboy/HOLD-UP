package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AppointmentParserTest {
    private val phoenix = ZoneId.of("America/Phoenix")
    private val reference = LocalDateTime.of(2026, 7, 12, 8, 0)

    @Test
    fun extractsMonthDateTimeAndExplicitLocation() {
        val result = AppointmentParser.parse(
            "Appointment: Dental cleaning\nJuly 22, 2026 at 3:30 PM\nLocation: 123 Main Street, Phoenix",
            reference,
            phoenix
        )

        assertEquals("Dental cleaning", result.title)
        assertEquals("123 Main Street, Phoenix", result.location)
        assertEquals(listOf("date", "time", "location"), result.detectedFields)
        assertEquals(
            Instant.parse("2026-07-22T22:30:00Z").toEpochMilli(),
            result.startMillis
        )
    }

    @Test
    fun rollsMissingYearForwardWhenMonthAndDayAlreadyPassed() {
        val result = AppointmentParser.parse(
            "Reservation: Annual checkup\nJune 4 at 9 AM",
            reference,
            phoenix
        )

        assertEquals(
            Instant.parse("2027-06-04T16:00:00Z").toEpochMilli(),
            result.startMillis
        )
    }

    @Test
    fun parsesNumericDateAndMidnight() {
        val result = AppointmentParser.parse(
            "Meeting: Maintenance window\n08/01/2026 at 12:00 AM",
            reference,
            phoenix
        )

        assertEquals(
            Instant.parse("2026-08-01T07:00:00Z").toEpochMilli(),
            result.startMillis
        )
    }

    @Test
    fun refusesToInventStartWhenTimeIsMissing() {
        val result = AppointmentParser.parse(
            "Appointment: Eye exam\nAugust 9, 2026\nLocation: Clinic B",
            reference,
            phoenix
        )

        assertNull(result.startMillis)
        assertEquals(listOf("date", "location"), result.detectedFields)
    }

    @Test
    fun invalidDateDoesNotCrashOrPrefillTime() {
        val result = AppointmentParser.parse(
            "Appointment: Follow-up\nFebruary 31, 2026 at 2 PM",
            reference,
            phoenix
        )

        assertNull(result.startMillis)
        assertTrue("time" in result.detectedFields)
        assertTrue("date" !in result.detectedFields)
    }

    @Test
    fun capsLongTitleAndKeepsParserLocalAndDeterministic() {
        val result = AppointmentParser.parse(
            "Meeting: ${"Very important planning session ".repeat(6)}\nSeptember 1, 2026 at 4 PM",
            reference,
            phoenix
        )

        assertTrue(result.title.length <= 80)
        assertNotNull(result.startMillis)
    }
}
