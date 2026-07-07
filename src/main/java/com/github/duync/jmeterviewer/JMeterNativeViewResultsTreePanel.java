package com.github.duync.jmeterviewer;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;

final class JMeterNativeViewResultsTreePanel {
    private final JMeterNativeVisualizerPanel delegate;

    JMeterNativeViewResultsTreePanel() {
        delegate = new JMeterNativeVisualizerPanel("View Results Tree",
                "org.apache.jmeter.visualizers.ViewResultsFullVisualizer");
    }

    JComponent component() {
        return delegate.component();
    }

    void clear() {
        delegate.clear();
    }

    void configure(TestElement element) {
        delegate.configure(element);
    }

    void add(SampleResult result) {
        delegate.add(result);
    }
}
