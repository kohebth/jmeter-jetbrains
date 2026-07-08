package com.github.kohebth.jmeterviewer.ide;

import com.intellij.openapi.fileEditor.FileEditorPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JMeterFileEditorProviderTest {
    @Test
    void keepsDefaultEditorAvailableAfterVisualEditor() {
        assertEquals(FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR,
                new JMeterFileEditorProvider().getPolicy());
    }
}
