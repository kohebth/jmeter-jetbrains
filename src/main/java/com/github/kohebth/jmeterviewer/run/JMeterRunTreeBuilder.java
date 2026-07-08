package com.github.kohebth.jmeterviewer.run;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;

public final class JMeterRunTreeBuilder {
    private JMeterRunTreeBuilder() {
    }

    public static HashTree fullPlan(JMeterTreeModel model) {
        return JMeterTreeLoader.toRunHashTree(model);
    }

    public static HashTree selectedThreadGroup(JMeterTreeModel model, JMeterTreeNode selected) {
        JMeterTreeNode group = threadGroupNode(selected);
        if (group == null) {
            return fullPlan(model);
        }

        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        JMeterTreeNode plan = firstTestPlan(root);
        if (plan == null) {
            return fullPlan(model);
        }

        HashTree tree = new ListedHashTree();
        HashTree planTree = tree.add(cloneElement(plan));
        for (int i = 0; i < plan.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) plan.getChildAt(i);
            if (child == group || isTestLevelElement(child)) {
                addSubTree(planTree, child);
            }
        }
        return tree;
    }

    private static JMeterTreeNode threadGroupNode(JMeterTreeNode selected) {
        JMeterTreeNode current = selected;
        while (current != null) {
            Object value = current.getUserObject();
            if (value instanceof AbstractThreadGroup) {
                return current;
            }
            current = current.getParent() instanceof JMeterTreeNode
                    ? (JMeterTreeNode) current.getParent()
                    : null;
        }
        return null;
    }

    private static JMeterTreeNode firstTestPlan(JMeterTreeNode root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) root.getChildAt(i);
            Object value = child.getUserObject();
            if (value instanceof org.apache.jmeter.testelement.TestPlan) {
                return child;
            }
        }
        return root.getChildCount() == 0 ? null : (JMeterTreeNode) root.getChildAt(0);
    }

    private static boolean isTestLevelElement(JMeterTreeNode node) {
        Object value = node.getUserObject();
        return value instanceof TestElement && !(value instanceof AbstractThreadGroup);
    }

    private static void addSubTree(HashTree parentTree, JMeterTreeNode source) {
        HashTree childTree = parentTree.add(cloneElement(source));
        for (int i = 0; i < source.getChildCount(); i++) {
            addSubTree(childTree, (JMeterTreeNode) source.getChildAt(i));
        }
    }

    private static TestElement cloneElement(JMeterTreeNode node) {
        return (TestElement) node.getTestElement().clone();
    }
}
