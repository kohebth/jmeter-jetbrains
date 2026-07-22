package com.github.kohebth.jmeterviewer.editor

import com.github.kohebth.jmeterviewer.runtime.JMeterNativeShortcut
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/** Registers JMeter shortcuts only on the active embedded editor/tool-window components. */
internal class JMeterShortcutController(
    private val editor: JMeterVisualFileEditor,
    private val service: JMeterWorkspaceService,
    nativeShortcuts: List<JMeterNativeShortcut>,
    roots: List<JComponent>,
    private val treeRoot: JComponent,
) : Disposable {
    private val registrations = mutableListOf<Registration>()

    init {
        val policyBindings = JMeterShortcutPolicy.bindings(SystemInfo.isMac)
        val allowedCommands = policyBindings.map(JMeterShortcutBinding::command).toSet()
        val nativeBindings = nativeShortcuts
            .filter { it.command in allowedCommands }
            .map { shortcut ->
                JMeterShortcutBinding(
                    keyCode = shortcut.keyCode,
                    modifiers = shortcut.modifiers,
                    command = shortcut.command,
                    argument = shortcut.argument,
                )
            }
        val hostBindings = policyBindings.filter { it.command in HOST_ONLY_COMMANDS }
        (nativeBindings + hostBindings)
            .forEach { binding ->
                val keyStroke = KeyStroke.getKeyStroke(binding.keyCode, binding.modifiers)
                roots.distinct().forEach { root ->
                    val action = object : DumbAwareAction() {
                        override fun actionPerformed(event: AnActionEvent) {
                            perform(binding, keyStroke)
                        }
                    }
                    action.registerCustomShortcutSet(CustomShortcutSet(keyStroke), root)
                    registrations += Registration(action, root)
                }
            }
    }

    private fun perform(binding: JMeterShortcutBinding, keyStroke: KeyStroke) {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (binding.command in LOCAL_COMMANDS && invokeLocalAction(focus, binding.command, keyStroke)) {
            return
        }
        if (
            binding.command in TREE_ONLY_COMMANDS &&
            (focus == null || (focus !== treeRoot && !SwingUtilities.isDescendingFrom(focus, treeRoot)))
        ) {
            return
        }
        service.performShortcut(editor, binding.command, binding.argument)
    }

    private fun invokeLocalAction(
        focus: Component?,
        command: String,
        keyStroke: KeyStroke,
    ): Boolean {
        val component = focus as? JComponent ?: return false
        val actionKey = component.getInputMap(JComponent.WHEN_FOCUSED)?.get(keyStroke)
        val mappedAction = actionKey?.let { component.actionMap?.get(it) }
        if (mappedAction != null) {
            mappedAction.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, command))
            return true
        }
        val text = component as? JTextComponent ?: return false
        when (command) {
            "Copy" -> text.copy()
            "Cut" -> text.cut()
            "Paste" -> text.paste()
            "select_all" -> text.selectAll()
            "undo", "redo" -> {
                val fallback = text.actionMap.get(command) ?: return false
                fallback.actionPerformed(ActionEvent(text, ActionEvent.ACTION_PERFORMED, command))
            }
            else -> return false
        }
        return true
    }

    override fun dispose() {
        registrations.forEach { (action, root) -> action.unregisterCustomShortcutSet(root) }
        registrations.clear()
    }

    private data class Registration(
        val action: DumbAwareAction,
        val root: JComponent,
    )

    private companion object {
        val LOCAL_COMMANDS = setOf("Copy", "Cut", "Paste", "select_all", "undo", "redo")
        val HOST_ONLY_COMMANDS = setOf("select_all", "undo", "redo")
        val TREE_ONLY_COMMANDS = setOf(
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
            "quick_component",
        )
    }
}
