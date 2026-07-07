package com.github.duync.jmeterviewer;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

@State(name = "JMeterPluginClasspathStore", storages = @Storage("jmeterPluginClasspath.xml"))
public final class JMeterPluginClasspathStore implements PersistentStateComponent<JMeterPluginClasspathStore.State> {
    private State state = new State();

    static JMeterPluginClasspathStore get(Project project) {
        return project.getService(JMeterPluginClasspathStore.class);
    }

    @Override
    public @NotNull State getState() {
        saveFromClasspath();
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        applyToClasspath();
    }

    void applyToClasspath() {
        JMeterPluginClasspath.addPaths(state.paths);
    }

    void add(File file) {
        JMeterPluginClasspath.addPath(file);
        saveFromClasspath();
    }

    void remove(File file) {
        JMeterPluginClasspath.remove(file);
        saveFromClasspath();
    }

    void removeMissing() {
        JMeterPluginClasspath.removeMissing();
        saveFromClasspath();
    }

    void clear() {
        JMeterPluginClasspath.clear();
        saveFromClasspath();
    }

    private void saveFromClasspath() {
        java.util.List<String> paths = new ArrayList<>();
        for (File path : JMeterPluginClasspath.paths()) {
            paths.add(path.getAbsolutePath());
        }
        state.paths = paths;
    }

    public static final class State {
        public java.util.List<String> paths = new ArrayList<>();
    }
}
