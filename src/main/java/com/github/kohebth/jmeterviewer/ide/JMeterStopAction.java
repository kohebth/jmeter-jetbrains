package com.github.kohebth.jmeterviewer.ide;

import com.github.kohebth.jmeterviewer.editor.JMeterVisualFileEditor;

public final class JMeterStopAction extends JMeterEditorAction {
    @Override
    protected void perform(JMeterVisualFileEditor editor) {
        editor.stopTest();
    }
}
