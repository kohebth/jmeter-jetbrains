package com.github.kohebth.jmeterviewer.templates;

import org.apache.jmeter.gui.tree.JMeterTreeNode;

import java.util.*;

public final class JMeterTemplateCapture {
    private JMeterTemplateCapture() {
    }

    public static java.util.List<JMeterTemplate.Node> roots(JMeterTreeNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(capture(node));
    }

    private static JMeterTemplate.Node capture(JMeterTreeNode node) {
        java.util.List<JMeterTemplate.Node> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            children.add(capture((JMeterTreeNode) node.getChildAt(i)));
        }
        return JMeterTemplate.node(node.getName(), children);
    }
}
