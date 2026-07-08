package com.github.duync.jmeterviewer;

import javax.swing.JButton;

final class JMeterEditorToolbarState {
    JButton runButton;
    JButton runSelectedButton;
    JButton stopButton;
    JButton shutdownButton;
    JButton resetEnginesButton;
    JButton exitEnginesButton;

    JMeterEditorToolbarState(JButton runButton,
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
