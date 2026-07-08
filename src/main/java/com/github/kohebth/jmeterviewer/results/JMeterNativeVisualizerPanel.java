package com.github.kohebth.jmeterviewer.results;

import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspath;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.samplers.Clearable;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.Visualizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;

public final class JMeterNativeVisualizerPanel {
    private final JPanel panel = new JPanel(new BorderLayout());
    private final String label;
    private final String className;
    private Visualizer visualizer;
    private Clearable clearable;
    private JMeterGUIComponent guiComponent;
    private JTextArea fallback;
    private TestElement pendingConfiguration;
    private boolean loaded;
    private boolean loading;

    public JMeterNativeVisualizerPanel(String label, String className) {
        this.label = label;
        this.className = className;
        panel.add(new JLabel("Open this tab to load " + label), BorderLayout.NORTH);
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) {
                load();
            }
        });
    }

    public JComponent component() {
        return panel;
    }

    public void clear() {
        if (clearable != null) {
            clearable.clearData();
        }
        if (fallback != null) {
            fallback.setCaretPosition(0);
        }
    }

    public void configure(TestElement element) {
        if (element == null) {
            return;
        }
        pendingConfiguration = element;
        if (!loaded && !panel.isShowing()) {
            return;
        }
        if (!loaded) {
            load();
            return;
        }
        if (guiComponent != null) {
            guiComponent.configure(element);
        }
    }

    public void add(SampleResult result) {
        if (visualizer != null && result != null) {
            visualizer.add(result);
        }
    }

    private void load() {
        if (loaded || loading) {
            return;
        }
        loading = true;
        panel.removeAll();
        panel.add(new JLabel("Loading " + label + "..."), BorderLayout.NORTH);
        panel.revalidate();
        panel.repaint();
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            preloadClass(false);
        } else {
            application.executeOnPooledThread(() -> preloadClass(true));
        }
    }

    private void preloadClass(boolean finishOnEdt) {
        Class<?> loadedClass = null;
        Throwable failure = null;
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            EmbeddedJMeterRuntime.ensureReady();
            previousLoader = JMeterPluginClasspath.activateThread();
            loadedClass = JMeterPluginClasspath.loadClass(className);
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            JMeterPluginClasspath.restoreThread(previousLoader);
        }
        Class<?> resolvedClass = loadedClass;
        Throwable resolvedFailure = failure;
        if (finishOnEdt) {
            SwingUtilities.invokeLater(() -> finishLoad(resolvedClass, resolvedFailure));
        } else {
            finishLoad(resolvedClass, resolvedFailure);
        }
    }

    private void finishLoad(Class<?> visualizerClass, Throwable preloadFailure) {
        Visualizer createdVisualizer = null;
        Clearable createdClearable = null;
        JMeterGUIComponent createdGui = null;
        JTextArea error = null;
        panel.removeAll();
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (preloadFailure != null) {
                throw preloadFailure;
            }
            previousLoader = JMeterPluginClasspath.activateThread();
            Object instance = visualizerClass.getDeclaredConstructor().newInstance();
            createdVisualizer = instance instanceof Visualizer ? (Visualizer) instance : null;
            createdClearable = instance instanceof Clearable ? (Clearable) instance : null;
            createdGui = instance instanceof JMeterGUIComponent ? (JMeterGUIComponent) instance : null;
            error = addInstance(instance);
        } catch (Throwable throwable) {
            error = new JTextArea("Unable to create " + label + ":\n" + rootCause(throwable));
            error.setEditable(false);
            panel.add(new JBScrollPane(error), BorderLayout.CENTER);
        } finally {
            JMeterPluginClasspath.restoreThread(previousLoader);
        }
        visualizer = createdVisualizer;
        clearable = createdClearable;
        guiComponent = createdGui;
        fallback = error;
        loading = false;
        loaded = true;
        applyPendingConfiguration();
        panel.revalidate();
        panel.repaint();
    }

    private JTextArea addInstance(Object instance) {
        if (instance instanceof JComponent) {
            JComponent component = (JComponent) instance;
            component.setName(label);
            panel.add(component, BorderLayout.CENTER);
            return null;
        }
        JTextArea error = new JTextArea(className + " is not a Swing component.");
        error.setEditable(false);
        panel.add(new JBScrollPane(error), BorderLayout.CENTER);
        return error;
    }

    private void applyPendingConfiguration() {
        if (guiComponent != null && pendingConfiguration != null) {
            guiComponent.configure(pendingConfiguration);
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.lang.reflect.InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
