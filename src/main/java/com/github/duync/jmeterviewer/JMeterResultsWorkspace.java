package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

public final class JMeterResultsWorkspace {
    static final String TOOL_WINDOW_ID = "JMeter Results";

    private final Project project;
    private final JMeterResultsPanel resultsPanel = new JMeterResultsPanel();

    public JMeterResultsWorkspace(Project project) {
        this.project = project;
    }

    static JMeterResultsWorkspace get(Project project) {
        return project.getService(JMeterResultsWorkspace.class);
    }

    JMeterResultsPanel resultsPanel() {
        return resultsPanel;
    }

    void show() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.show();
        }
    }
}
