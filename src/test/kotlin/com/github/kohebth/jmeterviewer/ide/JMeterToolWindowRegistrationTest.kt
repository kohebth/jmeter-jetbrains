package com.github.kohebth.jmeterviewer.ide

import com.github.kohebth.jmeterviewer.toolwindow.JMeterToolWindowFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JMeterToolWindowRegistrationTest {
    @Test
    fun registersTheBottomJMeterToolWindow() {
        val descriptor = checkNotNull(javaClass.getResource("/META-INF/plugin.xml")).readText()

        assertTrue(descriptor.contains("id=\"JMeter\""))
        assertTrue(descriptor.contains("anchor=\"bottom\""))
        assertEquals("JMeterToolWindowFactory", JMeterToolWindowFactory::class.java.simpleName)
    }
}
