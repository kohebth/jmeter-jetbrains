package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertTrue
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
}
