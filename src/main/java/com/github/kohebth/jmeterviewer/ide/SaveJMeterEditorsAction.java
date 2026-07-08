package com.github.kohebth.jmeterviewer.ide;

import com.github.kohebth.jmeterviewer.editor.JMeterVisualFileEditor;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class SaveJMeterEditorsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        JMeterVisualFileEditor selected = JMeterOpenEditors.selected(project);
        if (selected != null) {
            selected.save();
            return;
        }
        for (JMeterVisualFileEditor editor : JMeterOpenEditors.modified(project)) {
            editor.save();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean enabled = project != null && hasJMeterEditor(project);
        event.getPresentation().setEnabledAndVisible(enabled);
    }

    private boolean hasJMeterEditor(Project project) {
        if (JMeterOpenEditors.selected(project) != null) {
            return true;
        }
        return !JMeterOpenEditors.all(project).isEmpty();
    }
}
