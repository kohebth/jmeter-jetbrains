package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.palette.JMeterLeftPanel;
import com.github.kohebth.jmeterviewer.ui.JMeterIcons;
import com.github.kohebth.jmeterviewer.ui.JMeterTabOverflowSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.*;

public final class JMeterResultsToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JMeterResultsWorkspace workspace = JMeterResultsWorkspace.get(project);
        JMeterResultsPanel panel = workspace.resultsPanel();
        List<JMeterResultsWorkspace.ContentSpec> leadingContents = new ArrayList<>();
        leadingContents.add(JMeterResultsWorkspace.content("Tools", JMeterIcons.TOOL_WINDOW,
                JMeterLeftPanel.create(project)));
        leadingContents.add(JMeterResultsWorkspace.content("Run", null,
                wrap(project, panel, runComponent(workspace, panel))));
        leadingContents.add(JMeterResultsWorkspace.content("Table", null,
                wrap(project, panel, panel.tableComponent())));
        Map<JMeterNativeResultView, JMeterResultsWorkspace.ContentSpec> nativeContents =
                new EnumMap<>(JMeterNativeResultView.class);
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            nativeContents.put(view, JMeterResultsWorkspace.content(view.title(), null,
                    wrap(project, panel, panel.nativeComponent(view))));
        }
        List<JMeterResultsWorkspace.ContentSpec> trailingContents = new ArrayList<>();
        trailingContents.add(JMeterResultsWorkspace.content("Summary", null,
                wrap(project, panel, panel.summaryComponent())));
        trailingContents.add(JMeterResultsWorkspace.content("Log", null,
                wrap(project, panel, panel.logComponent())));
        toolWindow.setIcon(JMeterIcons.TOOL_WINDOW);
        workspace.installContents(toolWindow, leadingContents, nativeContents, trailingContents);
    }

    private JComponent runComponent(JMeterResultsWorkspace workspace, JMeterResultsPanel panel) {
        JPanel run = new JPanel(new BorderLayout());
        run.add(workspace.runControlsComponent(), BorderLayout.NORTH);
        run.add(panel.monitorComponent(), BorderLayout.CENTER);
        return run;
    }

    private JComponent wrap(Project project, JMeterResultsPanel panel, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(actions(project, panel), BorderLayout.NORTH);
        wrapper.add(JMeterTabOverflowSupport.apply(component), BorderLayout.CENTER);
        return JMeterTabOverflowSupport.apply(wrapper);
    }

    private JComponent actions(Project project, JMeterResultsPanel panel) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        actions.add(new JMeterResultFileLoader(project, panel).button());
        actions.add(new JMeterReportAction(project, panel).button());
        actions.add(button("Clear Samples", panel::clearResults));
        actions.add(button("Clear Log", panel::clearLog));
        return actions;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }
}
