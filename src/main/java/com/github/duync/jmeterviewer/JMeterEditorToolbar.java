package com.github.duync.jmeterviewer;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Icon;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

final class JMeterEditorToolbar {
    private JMeterEditorToolbar() {
    }

    static JComponent create(JMeterEditorToolbarState state) {
        JPanel toolbar = new JBPanel<>(new BorderLayout());
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel left = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(compact(state.runButton, "Run Plan", AllIcons.Actions.Run_anything));
        left.add(compact(state.runSelectedButton, "Run Thread Group", AllIcons.Actions.Execute));
        left.add(compact(state.stopButton, "Stop", AllIcons.Actions.Suspend));
        left.add(compact(state.shutdownButton, "Shutdown", AllIcons.Actions.Cancel));

        JPanel right = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(compact(state.resetEnginesButton, "Reset Engines", AllIcons.Actions.Replace));
        right.add(compact(state.exitEnginesButton, "Exit Engines", AllIcons.Actions.Exit));

        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(right, BorderLayout.EAST);
        return toolbar;
    }

    private static JButton compact(JButton button, String tooltip, Icon icon) {
        return JMeterIconButtons.compact(button, tooltip, icon);
    }

}
