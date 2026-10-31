package com.freshernotes.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SlugTest {
    @Test fun lowercasesAndJoinsWords() = assertEquals("jigs-fixtures", Slug.of("Jigs & Fixtures"))
    @Test fun trimsSeparators() = assertEquals("catia-v5", Slug.of("  CATIA  V5!! "))
    @Test fun emptyBecomesUntitled() = assertEquals("untitled", Slug.of("???"))
}
