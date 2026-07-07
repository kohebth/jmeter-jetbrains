package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterCellRenderer;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

final class JMeterActivityTreeRenderer implements TreeCellRenderer {
    private final JMeterCellRenderer delegate = new JMeterCellRenderer();
    private final JMeterThreadGroupActivity activity;

    JMeterActivityTreeRenderer(JMeterThreadGroupActivity activity) {
        this.activity = activity;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
        Component component = delegate.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus);
        String label = activityLabel(value);
        if (!label.isEmpty() && component instanceof JLabel) {
            JLabel text = (JLabel) component;
            text.setText(text.getText() + " " + label);
        }
        return component;
    }

    private String activityLabel(Object value) {
        if (!(value instanceof JMeterTreeNode)) {
            return "";
        }
        Object userObject = ((JMeterTreeNode) value).getUserObject();
        if (!(userObject instanceof TestElement)) {
            return "";
        }
        TestElement element = (TestElement) userObject;
        return activity.label(element);
    }
}
