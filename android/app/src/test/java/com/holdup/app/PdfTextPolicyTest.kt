package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextPolicyTest {
    @Test
    fun pagesToRead_capsLargeDocuments() {
        assertEquals(PdfTextPolicy.MAX_PAGES, PdfTextPolicy.pagesToRead(42))
    }

    @Test
    fun pagesToRead_preservesSmallDocuments() {
        assertEquals(3, PdfTextPolicy.pagesToRead(3))
    }

    @Test
    fun combine_removesBlankPagesAndNormalizesSpacing() {
        assertEquals(
            "First page\n\nSecond page",
            PdfTextPolicy.combine(listOf("  First page  ", "   ", "Second page\n"))
        )
    }

    @Test
    fun combine_capsExtractedText() {
        val oversized = "a".repeat(PdfTextPolicy.MAX_CHARACTERS + 500)
        assertEquals(PdfTextPolicy.MAX_CHARACTERS, PdfTextPolicy.combine(listOf(oversized)).length)
    }
}
