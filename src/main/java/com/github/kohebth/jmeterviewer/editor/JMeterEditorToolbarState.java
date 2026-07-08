package com.github.kohebth.jmeterviewer.editor;

import javax.swing.JButton;

public final class JMeterEditorToolbarState {
    JButton runButton;
    JButton runSelectedButton;
    JButton stopButton;
    JButton shutdownButton;
    JButton resetEnginesButton;
    JButton exitEnginesButton;

    public JMeterEditorToolbarState(JButton runButton,
                             JButton runSelectedButton,
                             JButton stopButton,
                             JButton shutdownButton,
                             JButton resetEnginesButton,
                             JButton exitEnginesButton) {
        this.runButton = runButton;
        this.runSelectedButton = runSelectedButton;
        this.stopButton = stopButton;
        this.shutdownButton = shutdownButton;
        this.resetEnginesButton = resetEnginesButton;
        this.exitEnginesButton = exitEnginesButton;
    }
}
