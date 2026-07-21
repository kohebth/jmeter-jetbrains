package com.github.kohebth.jmeterviewer.editor

import java.awt.Component
import javax.swing.CellRendererPane
import javax.swing.JTable
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

internal object JMeterTextAreaPolicy {
    fun canAdapt(component: JTextComponent): Boolean {
        if (component.parent !is JViewport) {
            return false
        }
        return !hasExcludedAncestor(component)
    }

    fun canTrackHistory(component: JTextComponent): Boolean =
        component.isEditable && !hasExcludedAncestor(component)

    private fun hasExcludedAncestor(component: JTextComponent): Boolean =
        SwingUtilities.getAncestorOfClass(JTable::class.java, component) != null ||
            SwingUtilities.getAncestorOfClass(CellRendererPane::class.java, component) != null ||
            hasTransientEditorAncestor(component)

    private fun hasTransientEditorAncestor(component: Component): Boolean {
        var current = component.parent
        while (current != null) {
            val name = current.javaClass.name
            if (
                name.contains("CellEditor") ||
                name.contains("Popup") ||
                name.contains("Dialog")
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
