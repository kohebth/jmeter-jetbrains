package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JScrollPane
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField

class JMeterTextAreaPolicyTest {
    @Test
    fun adaptsPersistentTextAreasMountedInScrollPanes() {
        val textArea = JTextArea("script")
        JScrollPane(textArea)

        assertTrue(JMeterTextAreaPolicy.canAdapt(textArea))
    }

    @Test
    fun leavesTransientAndTableEditorsToSwing() {
        val detached = JTextArea("dialog value")
        val tableEditor = JTextArea("cell value")
        JTable(1, 1).add(tableEditor)

        assertFalse(JMeterTextAreaPolicy.canAdapt(detached))
        assertFalse(JMeterTextAreaPolicy.canAdapt(tableEditor))
    }

    @Test
    fun tracksSingleLineFormFieldsInTheJmxHistory() {
        val field = JTextField("plan name")
        JPanel().add(field)

        assertTrue(JMeterTextAreaPolicy.canTrackHistory(field))
    }
}
