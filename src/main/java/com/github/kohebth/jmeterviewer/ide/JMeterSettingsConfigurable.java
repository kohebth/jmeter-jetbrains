package com.github.kohebth.jmeterviewer.ide;

import com.github.kohebth.jmeterviewer.runtime.JMeterPluginPanel;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JMeterSettingsConfigurable implements SearchableConfigurable {
    private final Project project;
    private JComponent component;

    public JMeterSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return "jmeter.viewer";
    }

    @Override
    public @Nls String getDisplayName() {
        return "JMeter";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (component == null) {
            component = JMeterPluginPanel.create(project);
        }
        return component;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() {
    }

    @Override
    public void reset() {
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }
}
