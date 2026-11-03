package com.maheshreddy.mym.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBuilderTest {
    @Test
    fun pageGroupsNumbersAndDeduplicates() {
        val page = PageBuilder.build(
            "Drill jigs", "Jigs and Fixtures", "",
            listOf(
                PageEntry(1, "Bushes", EntryType.KNOWLEDGE, "- Bushes are hardened.\n- They guide the drill.", 1),
                PageEntry(2, "Worn bushes", EntryType.LESSON, "- Replace worn bushes.", 3),
                PageEntry(3, "Repeat", EntryType.KNOWLEDGE, "- bushes are hardened!\n## Detail\n- New point.", 2),
                PageEntry(4, "Dupe only", EntryType.IDEA, "- They guide the drill.", 4),
            ),
        )
        assertTrue(page, page.startsWith("# Drill jigs\n\n_Jigs and Fixtures_\n\n## Knowledge"))
        assertTrue(page.contains("### 1. Bushes"))
        assertTrue(page.contains("### 2. Repeat\n**Detail**\n- New point."))
        assertEquals("duplicate bullet removed", 1, Regex("(?i)bushes are hardened").findAll(page).count())
        assertTrue(page.contains("## Lessons\n\n### 3. Worn bushes"))
        assertTrue(page.contains("### 4. Dupe only\n_(covered above)_"))
        assertTrue(page.indexOf("## Knowledge") < page.indexOf("## Lessons"))
    }

    @Test
    fun emptyPage() = assertTrue(PageBuilder.build("T", null, "", emptyList()).contains("_No entries yet._"))
}
