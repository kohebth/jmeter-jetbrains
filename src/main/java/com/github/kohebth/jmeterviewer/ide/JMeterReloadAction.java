package com.github.kohebth.jmeterviewer.ide;

import com.github.kohebth.jmeterviewer.editor.JMeterVisualFileEditor;

public final class JMeterReloadAction extends JMeterEditorAction {
    @Override
    protected void perform(JMeterVisualFileEditor editor) {
        editor.reloadFromFile();
    }
}
