package com.karelisio.canif

import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun idsAreUniqueAndEveryCategoryHasTools() {
        val ids = ToolRegistry.all.map { it.id }
        assertEquals(ids.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), ids.size, ids.toSet().size)
        Category.entries.forEach { c -> assertTrue("$c is empty", ToolRegistry.all.any { it.category == c }) }
        assertTrue(ids.all { it.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) })
    }
}
