package com.github.kohebth.jmeterviewer.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.file.Path
import java.awt.event.ActionListener
import javax.swing.JComponent

class EmbeddedJMeterWorkspaceContractTest {
    @Test
    fun exposesTheSmallEmbeddingBoundaryUsedByTheIdeFromTheBridge() {
        ExternalJMeterTestSupport.openRuntime().use { runtime ->
            val api = runtime.classLoader.loadClass(
                "org.apache.jmeter.gui.EmbeddedJMeterWorkspace",
            )

            assertEquals(JComponent::class.java, api.getMethod("getComponent").returnType)
            assertEquals(
                Void.TYPE,
                api.getMethod("load", InputStream::class.java, Path::class.java).returnType,
            )
            assertEquals(ByteArray::class.java, api.getMethod("snapshot").returnType)
            assertEquals(Boolean::class.javaPrimitiveType, api.getMethod("isDirty").returnType)
            assertEquals(Void.TYPE, api.getMethod("markSaved").returnType)
            assertEquals(JComponent::class.java, api.getMethod("getOutlineComponent").returnType)
            assertEquals(
                JComponent::class.java,
                api.getMethod("getResultsTreeComponent", String::class.java).returnType,
            )
            assertEquals(
                JComponent::class.java,
                api.getMethod("getAggregateReportComponent", String::class.java).returnType,
            )
            assertEquals(
                Void.TYPE,
                api.getMethod("reloadFromHistory", InputStream::class.java, Path::class.java).returnType,
            )
            assertEquals(
                Void.TYPE,
                api.getMethod("setModelChangeListener", Runnable::class.java).returnType,
            )
            assertEquals(
                Void.TYPE,
                api.getMethod("setExecutionActionListener", ActionListener::class.java).returnType,
            )
            assertEquals(
                Boolean::class.javaPrimitiveType,
                api.getMethod("canRunSelectedThreadGroups").returnType,
            )
            assertEquals(
                Boolean::class.javaPrimitiveType,
                api.getMethod("startSelectedThreadGroups", String::class.java).returnType,
            )
            assertEquals(Void.TYPE, api.getMethod("shutdownTest").returnType)
            assertEquals(Void.TYPE, api.getMethod("stopTest").returnType)
            assertEquals(Boolean::class.javaPrimitiveType, api.getMethod("isTestRunning").returnType)
            assertEquals(Void.TYPE, api.getMethod("clearResults", String::class.java).returnType)
            assertEquals(Void.TYPE, api.getMethod("discardResults", String::class.java).returnType)
            assertEquals(Void.TYPE, api.getMethod("close").returnType)

            val mainFrame = runtime.classLoader.loadClass("org.apache.jmeter.gui.MainFrame")
            assertEquals(
                Boolean::class.javaPrimitiveType,
                mainFrame.getMethod("isEmbeddedMode").returnType,
            )

            val source = Path.of(api.protectionDomain.codeSource.location.toURI())
                .toAbsolutePath()
                .normalize()
            assertEquals(ExternalJMeterTestSupport.bridge, source)
        }
    }
}
