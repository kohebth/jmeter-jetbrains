package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.run.JMeterThreadGroupActivity;

import org.apache.jmeter.gui.tree.JMeterCellRenderer;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public final class JMeterActivityTreeRenderer implements TreeCellRenderer {
    private final JMeterCellRenderer delegate = new JMeterCellRenderer();
    private final JMeterThreadGroupActivity activity;

    public JMeterActivityTreeRenderer(JMeterThreadGroupActivity activity) {
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
        if (component instanceof JLabel) {
            JLabel text = (JLabel) component;
            text.setText(text.getText() + suffix(value));
        }
        return component;
    }

    private String suffix(Object value) {
        TestElement element = element(value);
        if (element == null) {
            return "";
        }
        String type = JMeterElementTypeLabel.of(element);
        String activityText = activity.label(element);
        StringBuilder builder = new StringBuilder();
        if (!type.isEmpty()) {
            builder.append(" [").append(type).append("]");
        }
        if (!activityText.isEmpty()) {
            builder.append(" ").append(activityText);
        }
        return builder.toString();
    }

    private TestElement element(Object value) {
        if (!(value instanceof JMeterTreeNode)) {
            return null;
        }
        Object userObject = ((JMeterTreeNode) value).getUserObject();
        if (!(userObject instanceof TestElement)) {
            return null;
        }
        return (TestElement) userObject;
    }
}
