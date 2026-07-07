package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;

import javax.swing.*;

final class JMeterLeftPanel {
    private JMeterLeftPanel() {
    }

    static JComponent create(Project project) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Elements", JMeterPalettePanel.create());
        tabs.addTab("Functions", JMeterFunctionPanel.create());
        tabs.addTab("Properties", JMeterPropertiesPanel.create(project));
        tabs.addTab("Plugins", JMeterPluginPanel.create(project));
        return tabs;
    }
}
