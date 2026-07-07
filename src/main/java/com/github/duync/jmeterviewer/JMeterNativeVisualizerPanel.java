package com.github.duync.jmeterviewer;

import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.samplers.Clearable;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.Visualizer;

import javax.swing.*;
import java.awt.*;

final class JMeterNativeVisualizerPanel {
    private final JPanel panel = new JPanel(new BorderLayout());
    private final Visualizer visualizer;
    private final Clearable clearable;
    private final JMeterGUIComponent guiComponent;
    private final JTextArea fallback;

    JMeterNativeVisualizerPanel(String label, String className) {
        Visualizer createdVisualizer = null;
        Clearable createdClearable = null;
        JMeterGUIComponent createdGui = null;
        JTextArea error = null;
        try {
            EmbeddedJMeterRuntime.ensureReady();
            Object instance = JMeterPluginClasspath.loadClass(className).getDeclaredConstructor().newInstance();
            createdVisualizer = instance instanceof Visualizer ? (Visualizer) instance : null;
            createdClearable = instance instanceof Clearable ? (Clearable) instance : null;
            createdGui = instance instanceof JMeterGUIComponent ? (JMeterGUIComponent) instance : null;
            if (instance instanceof JComponent) {
                JComponent component = (JComponent) instance;
                component.setName(label);
                JMeterTabOverflowSupport.apply(component);
                panel.add(component, BorderLayout.CENTER);
            } else {
                error = new JTextArea(className + " is not a Swing component.");
                error.setEditable(false);
                panel.add(new JBScrollPane(error), BorderLayout.CENTER);
            }
        } catch (Exception | LinkageError exception) {
            error = new JTextArea("Unable to create " + label + ":\n" + exception);
            error.setEditable(false);
            panel.add(new JBScrollPane(error), BorderLayout.CENTER);
        }
        visualizer = createdVisualizer;
        clearable = createdClearable;
        guiComponent = createdGui;
        fallback = error;
    }

    JComponent component() {
        return panel;
    }

    void clear() {
        if (clearable != null) {
            clearable.clearData();
        }
        if (fallback != null) {
            fallback.setCaretPosition(0);
        }
    }

    void configure(TestElement element) {
        if (guiComponent != null && element != null) {
            guiComponent.configure(element);
        }
    }

    void add(SampleResult result) {
        if (visualizer != null && result != null) {
            visualizer.add(result);
        }
    }
}
