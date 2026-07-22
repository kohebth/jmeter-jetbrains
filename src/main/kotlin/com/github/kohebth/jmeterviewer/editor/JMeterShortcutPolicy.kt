package com.github.kohebth.jmeterviewer.editor

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal data class JMeterShortcutBinding(
    val keyCode: Int,
    val modifiers: Int,
    val command: String,
    val argument: String? = null,
)

/** Embedded-safe subset of JMeter's native key map. */
internal object JMeterShortcutPolicy {
    fun bindings(isMac: Boolean): List<JMeterShortcutBinding> {
        val menu = if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        fun bind(
            keyCode: Int,
            command: String,
            modifiers: Int = menu,
            argument: String? = null,
        ) = JMeterShortcutBinding(keyCode, modifiers, command, argument)

        return buildList {
            add(bind(KeyEvent.VK_C, "Copy"))
            add(bind(KeyEvent.VK_C, "duplicate", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_X, "Cut"))
            add(bind(KeyEvent.VK_V, "Paste"))
            add(bind(KeyEvent.VK_A, "select_all"))
            add(bind(KeyEvent.VK_Z, "undo"))
            add(bind(KeyEvent.VK_Z, "redo", menu or InputEvent.SHIFT_DOWN_MASK))
            if (!isMac) {
                add(bind(KeyEvent.VK_Y, "redo"))
            }
            add(bind(KeyEvent.VK_DELETE, "remove", 0))
            add(bind(KeyEvent.VK_T, "toggle"))
            add(bind(KeyEvent.VK_UP, "move_up", InputEvent.ALT_DOWN_MASK))
            add(bind(KeyEvent.VK_DOWN, "move_down", InputEvent.ALT_DOWN_MASK))
            add(bind(KeyEvent.VK_LEFT, "move_left", InputEvent.ALT_DOWN_MASK))
            add(bind(KeyEvent.VK_RIGHT, "move_right", InputEvent.ALT_DOWN_MASK))
            add(bind(KeyEvent.VK_LEFT, "collapse", InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_RIGHT, "expand", InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_MINUS, "collapse all"))
            add(bind(KeyEvent.VK_SUBTRACT, "collapse all"))
            add(bind(KeyEvent.VK_MINUS, "expand all", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_SUBTRACT, "expand all", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_S, "save"))
            add(bind(KeyEvent.VK_F, "search_tree"))
            add(bind(KeyEvent.VK_F, "search_reset", menu or InputEvent.ALT_DOWN_MASK))
            add(bind(KeyEvent.VK_R, "run_tg"))
            add(bind(KeyEvent.VK_N, "run_tg_no_timers", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_COMMA, "shutdown"))
            add(bind(KeyEvent.VK_PERIOD, "stop"))
            add(bind(KeyEvent.VK_D, "debug_off"))
            add(bind(KeyEvent.VK_D, "debug_on", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_G, "save_graphics"))
            add(bind(KeyEvent.VK_G, "save_graphics_all", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_H, "help"))
            add(bind(KeyEvent.VK_W, "what_class"))
            add(bind(KeyEvent.VK_F1, "functions", menu or InputEvent.SHIFT_DOWN_MASK))
            add(bind(KeyEvent.VK_E, "action.clear_all"))
            add(bind(KeyEvent.VK_E, "action.clear", menu or InputEvent.SHIFT_DOWN_MASK))
            (0..9).forEach { slot ->
                add(bind(KeyEvent.VK_0 + slot, "quick_component", argument = slot.toString()))
            }
        }
    }
}
