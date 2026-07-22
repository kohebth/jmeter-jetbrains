package com.github.kohebth.jmeterviewer.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.file.Path
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.tree.TreePath

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
            assertEquals(
                ByteArray::class.java,
                api.getMethod(
                    "snapshotSelectedThreadGroups",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Path::class.java,
                ).returnType,
            )
            assertEquals(
                Void.TYPE,
                api.getMethod(
                    "appendSampleResult",
                    String::class.java,
                    ByteArray::class.java,
                ).returnType,
            )
            assertEquals(Boolean::class.javaPrimitiveType, api.getMethod("isDirty").returnType)
            assertEquals(Void.TYPE, api.getMethod("markSaved").returnType)
            assertEquals(JComponent::class.java, api.getMethod("getOutlineComponent").returnType)
            assertEquals(
                List::class.java,
                api.getMethod(
                    "searchTestPlan",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).returnType,
            )
            assertEquals(Void.TYPE, api.getMethod("resetSearch").returnType)
            assertEquals(
                Void.TYPE,
                api.getMethod("selectSearchResult", TreePath::class.java).returnType,
            )
            assertEquals(
                IntArray::class.java,
                api.getMethod(
                    "replaceSearchResults",
                    emptyArray<TreePath>().javaClass,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).returnType,
            )
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
                List::class.java,
                api.getMethod("getShortcutDescriptors").returnType,
            )
            assertEquals(
                Void.TYPE,
                api.getMethod("performAction", String::class.java, String::class.java).returnType,
            )
            assertEquals(ByteArray::class.java, api.getMethod("exportSelectedNodes").returnType)
            assertEquals(
                Int::class.javaPrimitiveType,
                api.getMethod("importNodes", ByteArray::class.java).returnType,
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
