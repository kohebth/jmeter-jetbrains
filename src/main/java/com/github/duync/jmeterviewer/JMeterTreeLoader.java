package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.io.IOException;

final class JMeterTreeLoader {
    private JMeterTreeLoader() {
    }

    static JMeterTreeModel load(File file) throws IOException {
        JMeterPluginClasspath.activate();
        HashTree sourceTree = SaveService.loadTree(file);
        TestElement rootElement = findRootElement(sourceTree);
        JMeterTreeModel model = new JMeterTreeModel(rootElement);
        JMeterTreeNode rootNode = (JMeterTreeNode) model.getRoot();
        JMeterTreeNode firstTopLevel = (JMeterTreeNode) rootNode.getChildAt(0);

        addChildren(model, firstTopLevel, sourceTree.getTree(rootElement));
        for (Object element : sourceTree.getArray()) {
            if (element instanceof TestElement && element != rootElement) {
                JMeterTreeNode node = createNode(model, (TestElement) element);
                model.insertNodeInto(node, rootNode, rootNode.getChildCount());
                addChildren(model, node, sourceTree.getTree(element));
            }
        }
        return model;
    }

    static HashTree toHashTree(JMeterTreeModel model) {
        return toHashTree(model, false);
    }

    static HashTree toRunHashTree(JMeterTreeModel model) {
        return toHashTree(model, true);
    }

    private static HashTree toHashTree(JMeterTreeModel model, boolean cloneElements) {
        JMeterTreeNode rootNode = (JMeterTreeNode) model.getRoot();
        if (rootNode.getChildCount() == 0) {
            throw new IllegalArgumentException("JMeter tree does not contain a test plan");
        }

        HashTree tree = new HashTree();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) rootNode.getChildAt(i);
            tree.add(element(child, cloneElements), childrenToHashTree(child, cloneElements));
        }
        return tree;
    }

    static HashTree toHashTree(JMeterTreeNode node) {
        HashTree tree = new HashTree();
        tree.add(node.getTestElement(), childrenToHashTree(node));
        return tree;
    }

    private static TestElement findRootElement(HashTree tree) {
        Object[] elements = tree.getArray();
        for (Object element : elements) {
            if (element instanceof TestPlan) {
                return (TestElement) element;
            }
        }
        for (Object element : elements) {
            if (element instanceof TestElement) {
                return (TestElement) element;
            }
        }
        throw new IllegalArgumentException("JMX file does not contain a JMeter test element");
    }

    private static void addChildren(JMeterTreeModel model, JMeterTreeNode parent, HashTree tree) {
        for (Object child : tree.getArray()) {
            if (!(child instanceof TestElement)) {
                continue;
            }

            JMeterTreeNode childNode = createNode(model, (TestElement) child);
            model.insertNodeInto(childNode, parent, parent.getChildCount());
            addChildren(model, childNode, tree.getTree(child));
        }
    }

    private static JMeterTreeNode createNode(JMeterTreeModel model, TestElement element) {
        JMeterElementMetadata.normalize(element);
        JMeterTreeNode node = new JMeterTreeNode(element, model);
        try {
            node.setEnabled(element.isEnabled());
        } catch (RuntimeException ignored) {
            node.setEnabled(true);
        }
        return node;
    }

    private static HashTree childrenToHashTree(JMeterTreeNode parent) {
        return childrenToHashTree(parent, false);
    }

    private static HashTree childrenToHashTree(JMeterTreeNode parent, boolean cloneElements) {
        HashTree tree = new HashTree();
        for (int i = 0; i < parent.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) parent.getChildAt(i);
            tree.add(element(child, cloneElements), childrenToHashTree(child, cloneElements));
        }
        return tree;
    }

    private static TestElement element(JMeterTreeNode node, boolean cloneElement) {
        TestElement element = node.getTestElement();
        return cloneElement ? (TestElement) element.clone() : element;
    }
}
