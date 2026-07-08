package com.github.kohebth.jmeterviewer.editor;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import java.awt.BorderLayout;

public final class JMeterElementPanel {
    private final JPanel panel;
    private final JEditorPane errorPane;
    private final JMeterGuiDirtyTracker dirtyTracker;

    public JMeterElementPanel(Runnable dirty) {
        panel = new JBPanel<>(new BorderLayout());
        errorPane = new JEditorPane("text/plain", "");
        errorPane.setEditable(false);
        dirtyTracker = new JMeterGuiDirtyTracker(dirty);
    }

    public JComponent component() {
        return panel;
    }

    public void showSelected() {
        panel.removeAll();
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterGUIComponent selectedGui = guiPackage == null ? null : guiPackage.getCurrentGui();
        if (selectedGui instanceof JComponent) {
            JComponent component = (JComponent) selectedGui;
            dirtyTracker.watch(component);
            panel.add(scroller(component), BorderLayout.CENTER);
        } else {
            panel.add(new JLabel(missingGuiMessage()), BorderLayout.NORTH);
        }
        refresh();
    }

    public void watchVisibleControls() {
        dirtyTracker.watch(panel);
    }

    public void showError(String message) {
        errorPane.setText(message);
        panel.removeAll();
        panel.add(new JBScrollPane(errorPane), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        panel.revalidate();
        panel.repaint();
    }

    private JBScrollPane scroller(JComponent component) {
        JBScrollPane scrollPane = new JBScrollPane(component);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private String missingGuiMessage() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getCurrentNode() == null) {
            return "No JMeter GUI is available for this node.";
        }
        JMeterTreeNode currentNode = guiPackage.getCurrentNode();
        Object userObject = currentNode.getUserObject();
        if (!(userObject instanceof TestElement)) {
            return "No JMeter GUI is available for this node.";
        }

        TestElement element = (TestElement) userObject;
        String guiClass = element.getPropertyAsString(TestElement.GUI_CLASS);
        String testClass = element.getPropertyAsString(TestElement.TEST_CLASS);
        return "No JMeter GUI is available for " + element.getName()
                + " (guiclass=" + guiClass + ", testclass=" + testClass + ").";
    }
}
