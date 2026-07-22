package com.github.kohebth.jmeterviewer.editor

import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Process-wide ownership table for visual JMX editors.
 *
 * JMeter keeps GUI singletons in static fields, so a physical file must have one
 * visual owner even when two IDE project windows resolve it through different
 * path spellings. Different files are deliberately independent.
 */
internal class JMeterDocumentOwnershipTable {
    private val entries = LinkedHashMap<String, Entry>()
    private val keysByOwner = IdentityHashMap<Any, MutableSet<String>>()

    @Synchronized
    fun claim(path: Path, owner: Any, onAvailable: (() -> Unit)? = null): Boolean {
        val key = key(path)
        val current = entries[key]
        if (current == null) {
            entries[key] = Entry(owner)
            keysByOwner.getOrPut(owner) { LinkedHashSet() }.add(key)
            return true
        }
        if (current.owner === owner) {
            return true
        }
        if (onAvailable != null && current.waiters.none { it.owner === owner }) {
            current.waiters.add(Waiter(owner, onAvailable))
        }
        return false
    }

    @Synchronized
    fun ownerOf(path: Path): Any? = entries[key(path)]?.owner

    fun release(owner: Any) {
        val callbacks = synchronized(this) {
            val ownedKeys = keysByOwner.remove(owner).orEmpty().toList()
            buildList {
                ownedKeys.forEach { key ->
                    val entry = entries[key]
                    if (entry?.owner === owner) {
                        entries.remove(key)
                        addAll(entry.waiters.map(Waiter::callback))
                    }
                }
                entries.values.forEach { entry ->
                    entry.waiters.removeAll { it.owner === owner }
                }
            }
        }
        callbacks.forEach { it() }
    }

    private fun key(path: Path): String {
        val normalized = try {
            if (Files.exists(path)) path.toRealPath() else path.toAbsolutePath().normalize()
        } catch (_: Exception) {
            path.toAbsolutePath().normalize()
        }.toString()
        return if (java.io.File.separatorChar == '\\') {
            normalized.lowercase(Locale.ROOT)
        } else {
            normalized
        }
    }

    private class Entry(
        val owner: Any,
        val waiters: MutableList<Waiter> = mutableListOf(),
    )

    private data class Waiter(
        val owner: Any,
        val callback: () -> Unit,
    )
}
