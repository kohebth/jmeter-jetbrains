package com.github.kohebth.jmeterviewer.editor

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JMeterShortcutPolicyTest {
    @Test
    fun exposesEmbeddedSafeAuthoringShortcutsWithoutStandaloneOrRemoteActions() {
        val commands = JMeterShortcutPolicy.bindings(isMac = false)
            .map(JMeterShortcutBinding::command)
            .toSet()

        listOf(
            "Copy",
            "Cut",
            "Paste",
            "duplicate",
            "remove",
            "toggle",
            "move_up",
            "move_down",
            "move_left",
            "move_right",
            "collapse",
            "expand",
            "collapse all",
            "expand all",
            "save",
            "search_tree",
            "run_tg",
            "shutdown",
            "stop",
        ).forEach { command ->
            assertTrue(command in commands, "Missing embedded shortcut for $command")
        }

        listOf(
            "open",
            "close",
            "exit",
            "save_all_as",
            "remote_start_all",
            "remote_stop_all",
            "remote_shut_all",
        ).forEach { command ->
            assertFalse(command in commands, "Unsafe embedded shortcut exposed: $command")
        }
        assertFalse(commands.any { it.startsWith("laf:") })
    }

    @Test
    fun includesAllTenQuickComponentSlots() {
        val quickSlots = JMeterShortcutPolicy.bindings(isMac = false)
            .filter { it.command == "quick_component" }
            .mapNotNull(JMeterShortcutBinding::argument)

        assertTrue(quickSlots == (0..9).map(Int::toString))
    }

    @Test
    fun clearShortcutsMatchJmeterNativeAssignments() {
        val bindings = JMeterShortcutPolicy.bindings(isMac = false)

        assertEquals(
            JMeterShortcutBinding(
                KeyEvent.VK_E,
                InputEvent.CTRL_DOWN_MASK,
                "action.clear_all",
            ),
            bindings.single { it.command == "action.clear_all" },
        )
        assertEquals(
            JMeterShortcutBinding(
                KeyEvent.VK_E,
                InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
                "action.clear",
            ),
            bindings.single { it.command == "action.clear" },
        )
    }
}
