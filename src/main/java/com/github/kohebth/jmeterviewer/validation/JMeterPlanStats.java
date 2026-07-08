package com.github.kohebth.jmeterviewer.validation;

import org.apache.jmeter.gui.tree.*;
import org.apache.jmeter.testelement.TestElement;

import java.util.*;

public final class JMeterPlanStats {
    private JMeterPlanStats() {
    }

    public static String describe(JMeterTreeModel model) {
        if (model == null) {
            return "No JMeter model loaded.";
        }
        Map<String, Integer> classes = new TreeMap<>();
        Counter counter = new Counter();
        collect((JMeterTreeNode) model.getRoot(), classes, counter);
        StringBuilder output = new StringBuilder();
        output.append("Elements: ").append(counter.elements).append('\n');
        output.append("Enabled: ").append(counter.enabled).append('\n');
        output.append("Disabled: ").append(counter.disabled).append('\n');
        output.append("Top-level: ").append(((JMeterTreeNode) model.getRoot()).getChildCount()).append('\n');
        for (Map.Entry<String, Integer> entry : classes.entrySet()) {
            output.append(entry.getValue()).append(" x ").append(entry.getKey()).append('\n');
        }
        return output.toString();
    }

    private static void collect(JMeterTreeNode node, Map<String, Integer> classes, Counter counter) {
        Object userObject = node.getUserObject();
        if (userObject instanceof TestElement) {
            TestElement element = (TestElement) userObject;
            counter.elements++;
            if (node.isEnabled()) {
                counter.enabled++;
            } else {
                counter.disabled++;
            }
            classes.merge(element.getClass().getSimpleName(), 1, Integer::sum);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect((JMeterTreeNode) node.getChildAt(i), classes, counter);
        }
    }

    private static final class Counter {
        private int elements;
        private int enabled;
        private int disabled;
    }
}
