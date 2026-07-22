package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JMeterSuspendedFeaturesContractTest {
    @Test
    fun visualDocumentUpdatesCanRunWithoutAddingUndoEntries() {
        val source = Files.readString(
            Path.of(
                "src/main/kotlin/com/github/kohebth/jmeterviewer/editor/" +
                    "JMeterWorkspaceService.kt",
            ),
        )

        assertTrue(source.contains("UndoUtil.disableUndoIn(editor.document, writeMutation)"))
        assertTrue(source.contains("performHistoryActionIfEnabled("))
        assertTrue(source.contains("private fun performHistoryAction("))
    }

    @Test
    fun testPlanSearchUsesTheSourceControlledMountDefault() {
        val source = Files.readString(
            Path.of(
                "src/main/kotlin/com/github/kohebth/jmeterviewer/toolwindow/" +
                    "JMeterToolWindowFactory.kt",
            ),
        )

        assertTrue(source.contains("setSearchVisible(JMeterEditorFeatures.SEARCH_VISIBLE_ON_MOUNT)"))
    }

    @Test
    fun nativeJmeterUndoIsNotSuppressedWithJetbrainsHistory() {
        val adapters = Files.readString(
            Path.of(
                "src/main/kotlin/com/github/kohebth/jmeterviewer/editor/" +
                    "JMeterTextAreaAdapters.kt",
            ),
        )
        val bridge = Files.readString(
            Path.of(
                "vendor/apache-jmeter-5.6.3/src/core/src/main/java/" +
                    "org/apache/jmeter/gui/EmbeddedJMeterWorkspace.java",
            ),
        )

        assertTrue(adapters.contains("if (jetbrainsHistoryEnabled)"))
        assertFalse(adapters.contains("source.actionMap.remove(\"undo\")"))
        assertFalse(bridge.contains("disablePerTextUndo("))
    }
}
