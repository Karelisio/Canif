package com.karelisio.canif

import com.karelisio.canif.update.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun semanticVersionComparison() {
        assertTrue(Updater.isNewer("0.2.0", "0.1.9"))
        assertTrue(Updater.isNewer("1.10.0", "1.9.3"))
        assertTrue(Updater.isNewer("1.0.1", "1.0.0-debug"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
    }
}
