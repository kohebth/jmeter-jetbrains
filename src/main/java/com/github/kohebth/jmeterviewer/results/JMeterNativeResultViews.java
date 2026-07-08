package com.github.kohebth.jmeterviewer.results;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import java.util.*;

public final class JMeterNativeResultViews {
    private final EnumMap<JMeterNativeResultView, JMeterNativeVisualizerPanel> panels =
            new EnumMap<>(JMeterNativeResultView.class);

    public JMeterNativeResultViews() {
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            panels.put(view, new JMeterNativeVisualizerPanel(view.title(), view.visualizerClass()));
        }
    }

    public JComponent component(JMeterNativeResultView view) {
        return panels.get(view).component();
    }

    public void clear() {
        panels.values().forEach(JMeterNativeVisualizerPanel::clear);
    }

    public void add(SampleResult result) {
        panels.values().forEach(panel -> panel.add(result));
    }

    public void configure(JMeterNativeResultView view, TestElement element) {
        panels.get(view).configure(element);
    }

    public void configureFromModel(JMeterTreeModel model) {
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            configure(view, JMeterViewResultsTreeLocator.find(model, view.guiClasses()));
        }
    }

    public EnumSet<JMeterNativeResultView> availableViews(JMeterTreeModel model) {
        EnumSet<JMeterNativeResultView> available = EnumSet.noneOf(JMeterNativeResultView.class);
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            if (JMeterViewResultsTreeLocator.find(model, view.guiClasses()) != null) {
                available.add(view);
            }
        }
        return available;
    }

    public JMeterNativeResultView matchingView(TestElement element) {
        for (JMeterNativeResultView view : JMeterNativeResultView.values()) {
            if (JMeterViewResultsTreeLocator.hasGuiClass(element, view.guiClasses())) {
                return view;
            }
        }
        return null;
    }
}
