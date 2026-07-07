package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

final class JMeterViewResultsTreeLocator {
    private static final String SHORT_GUI_CLASS = "ViewResultsFullVisualizer";
    private static final String FULL_GUI_CLASS = "org.apache.jmeter.visualizers.ViewResultsFullVisualizer";

    private JMeterViewResultsTreeLocator() {
    }

    static TestElement find(JMeterTreeModel model) {
        return find(model, SHORT_GUI_CLASS, FULL_GUI_CLASS);
    }

    static TestElement find(JMeterTreeModel model, String... guiClasses) {
        if (model == null || !(model.getRoot() instanceof JMeterTreeNode)) {
            return null;
        }
        return find((JMeterTreeNode) model.getRoot(), guiClasses);
    }

    static boolean isViewResultsTree(TestElement element) {
        return hasGuiClass(element, SHORT_GUI_CLASS, FULL_GUI_CLASS);
    }

    static boolean hasGuiClass(TestElement element, String... guiClasses) {
        if (element == null) {
            return false;
        }
        String guiClass = element.getPropertyAsString(TestElement.GUI_CLASS);
        for (String candidate : guiClasses) {
            if (candidate.equals(guiClass)) {
                return true;
            }
        }
        return false;
    }

    private static TestElement find(JMeterTreeNode node, String... guiClasses) {
        TestElement element = node.getTestElement();
        if (hasGuiClass(element, guiClasses)) {
            return element;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TestElement found = find((JMeterTreeNode) node.getChildAt(i), guiClasses);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
