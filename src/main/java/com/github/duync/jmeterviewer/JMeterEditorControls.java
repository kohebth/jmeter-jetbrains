package com.github.duync.jmeterviewer;

import com.intellij.openapi.Disposable;

import javax.swing.*;

final class JMeterEditorControls {
    private JMeterEditorControls() {
    }

    static void wire(JComponent component,
                     Disposable parent,
                     JMeterEditorToolbarState state,
                     Runnable save,
                     Runnable reload,
                     Runnable run,
                     Runnable runSelected,
                     JMeterRunController runController,
                     JMeterValidationAction validationAction,
                     Runnable commands) {
        state.runButton.addActionListener(event -> run.run());
        state.runSelectedButton.addActionListener(event -> runSelected.run());
        state.stopButton.addActionListener(event -> runController.stop());
        state.shutdownButton.addActionListener(event -> runController.shutdown());
        state.resetEnginesButton.addActionListener(event -> runController.resetEngines());
        state.exitEnginesButton.addActionListener(event -> runController.exitEngines());
        state.stopButton.setEnabled(false);
        state.shutdownButton.setEnabled(false);
        JMeterEditorShortcuts.install(component, parent, save, reload, run, runController::stop,
                validationAction::validateNow, commands);
    }
}
