package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;

public final class JMeterResultsToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JMeterResultsPanel panel = JMeterResultsWorkspace.get(project).resultsPanel();
        toolWindow.getContentManager().addContent(
                new ContentImpl(JMeterLeftPanel.create(project), "Tools", false)
        );
        toolWindow.getContentManager().addContent(content(project, panel, "Run", panel.monitorComponent()));
        toolWindow.getContentManager().addContent(content(project, panel, "Table", panel.tableComponent()));
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            toolWindow.getContentManager().addContent(content(project, panel, view.title(), panel.nativeComponent(view)));
        }
        toolWindow.getContentManager().addContent(content(project, panel, "Summary", panel.summaryComponent()));
        toolWindow.getContentManager().addContent(content(project, panel, "Log", panel.logComponent()));
    }

    private ContentImpl content(Project project, JMeterResultsPanel panel, String name, JComponent component) {
        return new ContentImpl(wrap(project, panel, component), name, false);
    }

    private JComponent wrap(Project project, JMeterResultsPanel panel, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JMeterResultFileLoader(project, panel).button());
        toolbar.add(new JMeterReportAction(project, panel).button());
        toolbar.add(button("Clear Samples", panel::clearResults));
        toolbar.add(button("Clear Log", panel::clearLog));
        wrapper.add(toolbar, BorderLayout.NORTH);
        wrapper.add(JMeterTabOverflowSupport.apply(component), BorderLayout.CENTER);
        return JMeterTabOverflowSupport.apply(wrapper);
    }

    private JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }
}
