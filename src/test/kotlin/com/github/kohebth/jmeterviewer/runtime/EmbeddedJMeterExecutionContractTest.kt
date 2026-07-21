package com.github.kohebth.jmeterviewer.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmbeddedJMeterExecutionContractTest {
    @Test
    fun exposesSelectedThreadGroupExecutionWithoutAWholePlanEntryPoint() {
        ExternalJMeterTestSupport.openRuntime().use { runtime ->
            val start = runtime.classLoader.loadClass("org.apache.jmeter.gui.action.Start")

            assertEquals(
                Boolean::class.javaPrimitiveType,
                start.getMethod("canRunSelectedThreadGroups").returnType,
            )
            assertEquals(
                Boolean::class.javaPrimitiveType,
                start.getMethod("startSelectedThreadGroups", List::class.java).returnType,
            )
            assertEquals(Void.TYPE, start.getMethod("shutdownEmbeddedTest").returnType)
            assertEquals(Void.TYPE, start.getMethod("stopEmbeddedTest").returnType)
            assertEquals(
                Boolean::class.javaPrimitiveType,
                start.getMethod("isEmbeddedTestRunning").returnType,
            )
        }
    }
}
