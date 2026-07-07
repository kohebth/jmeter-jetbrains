package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.tree.MutableTreeNode;
import java.util.*;

final class JMeterStructuralOperations {
    private JMeterStructuralOperations() {
    }

    static JMeterTreeNode insertParent(JMeterTreeModel model,
                                       java.util.List<JMeterTreeNode> nodes,
                                       JMeterPaletteItem controller) {
        if (nodes.isEmpty() || controller == null || controller.kind() != JMeterPaletteItem.Kind.CONTROLLER) {
            return null;
        }
        JMeterTreeNode parent = commonParent(nodes);
        if (parent == null || !JMeterTreeOperations.canAdd(parent, controller)) {
            return null;
        }
        java.util.List<JMeterTreeNode> ordered = displayOrder(nodes);
        int index = insertionIndex(ordered);
        JMeterTreeNode wrapper = JMeterTreeOperations.add(model, parent, controller);
        if (wrapper == null) {
            return null;
        }
        model.removeNodeFromParent(wrapper);
        model.insertNodeInto(wrapper, (MutableTreeNode) parent, index);
        for (JMeterTreeNode node : ordered) {
            if (JMeterTreeOperations.canMoveTo(node, wrapper)) {
                model.removeNodeFromParent(node);
                model.insertNodeInto(node, wrapper, wrapper.getChildCount());
            }
        }
        return wrapper;
    }

    static JMeterTreeNode changeParent(JMeterTreeModel model, JMeterTreeNode node, JMeterPaletteItem controller) {
        if (node == null || controller == null || controller.kind() != JMeterPaletteItem.Kind.CONTROLLER) {
            return null;
        }
        JMeterTreeNode parent = (JMeterTreeNode) node.getParent();
        if (parent == null || !JMeterTreeOperations.canAdd(parent, controller)) {
            return null;
        }
        int index = parent.getIndex(node);
        JMeterTreeNode replacement = JMeterTreeOperations.add(model, parent, controller);
        if (replacement == null) {
            return null;
        }
        model.removeNodeFromParent(replacement);
        model.insertNodeInto(replacement, parent, index);
        while (node.getChildCount() > 0) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(0);
            if (!JMeterTreeOperations.canMoveTo(child, replacement)) {
                break;
            }
            model.removeNodeFromParent(child);
            model.insertNodeInto(child, replacement, replacement.getChildCount());
        }
        model.removeNodeFromParent(node);
        return replacement;
    }

    static int addThinkTimes(JMeterTreeModel model, JMeterTreeNode parent) {
        JMeterPaletteItem timer = JMeterPaletteItem.findByLabel("Constant Timer");
        if (parent == null || timer == null) {
            return 0;
        }
        int inserted = 0;
        for (int i = parent.getChildCount() - 1; i > 0; i--) {
            JMeterTreeNode previous = (JMeterTreeNode) parent.getChildAt(i - 1);
            JMeterTreeNode current = (JMeterTreeNode) parent.getChildAt(i);
            if (isStep(previous) && isStep(current)) {
                JMeterTreeNode timerNode = JMeterTreeOperations.add(model, parent, timer);
                if (timerNode != null) {
                    model.removeNodeFromParent(timerNode);
                    model.insertNodeInto(timerNode, parent, i);
                    inserted++;
                }
            }
        }
        return inserted;
    }

    private static JMeterTreeNode commonParent(java.util.List<JMeterTreeNode> nodes) {
        JMeterTreeNode parent = (JMeterTreeNode) nodes.get(0).getParent();
        for (JMeterTreeNode node : nodes) {
            if (node.getParent() != parent || !JMeterTreeOperations.canRemove(node)) {
                return null;
            }
        }
        return parent;
    }

    private static java.util.List<JMeterTreeNode> displayOrder(java.util.List<JMeterTreeNode> nodes) {
        java.util.List<JMeterTreeNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparingInt(node -> node.getParent().getIndex(node)));
        return ordered;
    }

    private static int insertionIndex(java.util.List<JMeterTreeNode> ordered) {
        return ordered.stream().mapToInt(node -> node.getParent().getIndex(node)).min().orElse(0);
    }

    private static boolean isStep(JMeterTreeNode node) {
        JMeterPaletteItem item = JMeterPaletteItem.findByTestClass(node.getTestElement().getClass().getName());
        return item != null && (item.kind() == JMeterPaletteItem.Kind.SAMPLER
                || item.kind() == JMeterPaletteItem.Kind.CONTROLLER);
    }
}
