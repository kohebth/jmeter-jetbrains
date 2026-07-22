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
        assertTrue(JMeterTextAreaPolicy.shouldAdapt(textArea, enabled = true))
        assertFalse(JMeterTextAreaPolicy.shouldAdapt(textArea, enabled = false))
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
    fun tracksNativeFormFieldsForAutosave() {
        val field = JTextField("plan name")
        JPanel().add(field)

        assertTrue(JMeterTextAreaPolicy.canTrackChanges(field))
    }

    @Test
    fun tracksNativeMultilineFieldsWhenTheJetbrainsAdapterIsDisabled() {
        val textArea = JTextArea("script")
        JScrollPane(textArea)

        assertFalse(JMeterTextAreaPolicy.shouldAdapt(textArea, enabled = false))
        assertTrue(JMeterTextAreaPolicy.canTrackChanges(textArea))
    }
}
