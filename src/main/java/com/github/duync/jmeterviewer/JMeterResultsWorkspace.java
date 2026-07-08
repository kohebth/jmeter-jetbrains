package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.components.JBPanel;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.*;

public final class JMeterResultsWorkspace {
    static final String TOOL_WINDOW_ID = "JMeter";

    private final Project project;
    private final JMeterResultsPanel resultsPanel = new JMeterResultsPanel();
    private final List<ContentSpec> leadingContents = new ArrayList<>();
    private final List<ContentSpec> trailingContents = new ArrayList<>();
    private final EnumMap<JMeterNativeResultView, ContentSpec> nativeContents =
            new EnumMap<>(JMeterNativeResultView.class);
    private final JPanel runControlsPanel = new JBPanel<>(new BorderLayout());
    private EnumSet<JMeterNativeResultView> availableNativeViews = EnumSet.noneOf(JMeterNativeResultView.class);
    private ToolWindow toolWindow;
    private Object runControlsOwner;

    public JMeterResultsWorkspace(Project project) {
        this.project = project;
    }

    static JMeterResultsWorkspace get(Project project) {
        return project.getService(JMeterResultsWorkspace.class);
    }

    JMeterResultsPanel resultsPanel() {
        return resultsPanel;
    }

    JComponent runControlsComponent() {
        return runControlsPanel;
    }

    void setRunControls(Object owner,
                        JComponent runOptions,
                        JComponent threadControl,
                        JLabel statusLabel) {
        runControlsOwner = owner;
        JPanel row = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(runOptions);
        row.add(threadControl);
        row.add(statusLabel);
        runControlsPanel.removeAll();
        runControlsPanel.add(row, BorderLayout.CENTER);
        runControlsPanel.revalidate();
        runControlsPanel.repaint();
    }

    void clearRunControls(Object owner) {
        if (runControlsOwner != owner) {
            return;
        }
        runControlsOwner = null;
        runControlsPanel.removeAll();
        runControlsPanel.revalidate();
        runControlsPanel.repaint();
    }

    void installContents(ToolWindow toolWindow,
                         List<ContentSpec> leadingContents,
                         Map<JMeterNativeResultView, ContentSpec> nativeContents,
                         List<ContentSpec> trailingContents) {
        this.toolWindow = toolWindow;
        this.leadingContents.clear();
        this.leadingContents.addAll(leadingContents);
        this.nativeContents.clear();
        this.nativeContents.putAll(nativeContents);
        this.trailingContents.clear();
        this.trailingContents.addAll(trailingContents);
        rebuildContents();
    }

    void updateNativeResultViews(EnumSet<JMeterNativeResultView> views) {
        EnumSet<JMeterNativeResultView> next = views == null || views.isEmpty()
                ? EnumSet.noneOf(JMeterNativeResultView.class)
                : EnumSet.copyOf(views);
        if (availableNativeViews.equals(next)) {
            return;
        }
        availableNativeViews = next;
        rebuildContents();
    }

    void show() {
        ToolWindow currentToolWindow = currentToolWindow();
        if (currentToolWindow != null) {
            currentToolWindow.show();
        }
    }

    void showViewResultsTree() {
        if (!availableNativeViews.contains(JMeterNativeResultView.VIEW_RESULTS_TREE)) {
            showContent("Table");
            return;
        }
        showNativeView(JMeterNativeResultView.VIEW_RESULTS_TREE);
    }

    void showNativeView(JMeterNativeResultView view) {
        showContent(view.title());
    }

    private void showContent(String contentName) {
        ToolWindow currentToolWindow = currentToolWindow();
        if (currentToolWindow == null) {
            return;
        }
        Content content = currentToolWindow.getContentManager().findContent(contentName);
        if (content != null) {
            currentToolWindow.getContentManager().setSelectedContent(content, true);
        }
        currentToolWindow.show();
    }

    private void rebuildContents() {
        if (toolWindow == null) {
            return;
        }
        ContentManager manager = toolWindow.getContentManager();
        String selected = selectedContentName(manager);
        clearContents(manager);
        addContents(manager, leadingContents);
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            if (availableNativeViews.contains(view)) {
                addContent(manager, nativeContents.get(view));
            }
        }
        addContents(manager, trailingContents);
        restoreSelection(manager, selected);
    }

    private ToolWindow currentToolWindow() {
        if (toolWindow != null) {
            return toolWindow;
        }
        return ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
    }

    private String selectedContentName(ContentManager manager) {
        Content selected = manager.getSelectedContent();
        return selected == null ? null : selected.getDisplayName();
    }

    private void clearContents(ContentManager manager) {
        while (manager.getContentCount() > 0) {
            manager.removeContent(manager.getContent(0), false);
        }
    }

    private void addContents(ContentManager manager, List<ContentSpec> specs) {
        for (ContentSpec spec : specs) {
            addContent(manager, spec);
        }
    }

    private void addContent(ContentManager manager, ContentSpec spec) {
        if (spec == null) {
            return;
        }
        Content content = new ContentImpl(spec.component, spec.title, false);
        if (spec.icon != null) {
            content.setIcon(spec.icon);
            content.setPopupIcon(spec.icon);
        }
        manager.addContent(content);
    }

    private void restoreSelection(ContentManager manager, String selected) {
        if (selected == null) {
            return;
        }
        Content content = manager.findContent(selected);
        if (content != null) {
            manager.setSelectedContent(content, true);
        }
    }

    static ContentSpec content(String title, Icon icon, JComponent component) {
        return new ContentSpec(title, icon, component);
    }

    static final class ContentSpec {
        private final String title;
        private final Icon icon;
        private final JComponent component;

        private ContentSpec(String title, Icon icon, JComponent component) {
            this.title = title;
            this.icon = icon;
            this.component = component;
        }
    }
}
