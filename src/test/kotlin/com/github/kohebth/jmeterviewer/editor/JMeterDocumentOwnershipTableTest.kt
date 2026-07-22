package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JMeterDocumentOwnershipTableTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun grantsOneVisualOwnerPerPhysicalFileAndWakesAWaiterOnRelease() {
        val table = JMeterDocumentOwnershipTable()
        val firstOwner = Any()
        val secondOwner = Any()
        var available = false
        val file = tempDirectory.resolve("plans").resolve("same.jmx")

        assertTrue(table.claim(file, firstOwner))
        assertFalse(table.claim(file.parent.resolve(".").resolve("same.jmx"), secondOwner) {
            available = true
        })
        assertSame(firstOwner, table.ownerOf(file))

        table.release(firstOwner)

        assertTrue(available)
        assertTrue(table.claim(file, secondOwner))
        assertSame(secondOwner, table.ownerOf(file))
    }

    @Test
    fun differentFilesRemainIndependent() {
        val table = JMeterDocumentOwnershipTable()
        val firstOwner = Any()
        val secondOwner = Any()

        assertTrue(table.claim(tempDirectory.resolve("first.jmx"), firstOwner))
        assertTrue(table.claim(tempDirectory.resolve("second.jmx"), secondOwner))
    }
}
