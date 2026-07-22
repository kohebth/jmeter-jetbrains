package com.github.kohebth.jmeterviewer.editor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class JMeterEditorFeaturesTest {
    @Test
    fun unstableVisualFeaturesAreDisabledByDefault() {
        assertFalse(JMeterEditorFeatures.JETBRAINS_MULTILINE_EDITOR_ENABLED)
        assertFalse(JMeterEditorFeatures.VISUAL_UNDO_ENABLED)
        assertFalse(JMeterEditorFeatures.SEARCH_VISIBLE_ON_MOUNT)
    }
}
