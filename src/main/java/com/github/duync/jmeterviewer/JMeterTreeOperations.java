package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.tree.MutableTreeNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

final class JMeterTreeOperations {
    private JMeterTreeOperations() {
    }

    static boolean canRemove(JMeterTreeNode node) {
        return node != null && node.getParent() != null && node.getParent().getParent() != null;
    }

    static boolean canAdd(JMeterTreeNode parent, JMeterPaletteItem item) {
        return JMeterAddRules.canAdd(parent, item);
    }

    static JMeterTreeNode add(JMeterTreeModel model, JMeterTreeNode parent, JMeterPaletteItem item) {
        TestElement element = JMeterAddRules.createAddableElement(parent, item);
        if (element == null) {
            return null;
        }
        JMeterTreeNode child = new JMeterTreeNode(element, model);
        model.insertNodeInto(child, parent, parent.getChildCount());
        return child;
    }

    static JMeterTreeNode addTemplate(JMeterTreeModel model, JMeterTreeNode parent, JMeterTemplate.Node template) {
        JMeterPaletteItem item = JMeterPaletteItem.findByLabel(template.element());
        if (item == null) {
            return null;
        }
        JMeterTreeNode node = add(model, parent, item);
        if (node == null) {
            return null;
        }
        for (JMeterTemplate.Node child : template.children()) {
            addTemplate(model, node, child);
        }
        return node;
    }

    static boolean remove(JMeterTreeModel model, JMeterTreeNode node) {
        if (!canRemove(node)) {
            return false;
        }
        model.removeNodeFromParent(node);
        return true;
    }

    static boolean canToggleEnabled(JMeterTreeNode node) {
        return node != null && node.getParent() != null && node.getParent().getParent() != null;
    }

    static boolean setEnabled(JMeterTreeModel model, JMeterTreeNode node, boolean enabled) {
        if (!canToggleEnabled(node)) {
            return false;
        }
        node.setEnabled(enabled);
        node.getTestElement().setEnabled(enabled);
        model.nodeChanged(node);
        return true;
    }

    static boolean toggleEnabled(JMeterTreeModel model, JMeterTreeNode node) {
        if (!canToggleEnabled(node)) {
            return false;
        }
        return setEnabled(model, node, !node.isEnabled());
    }

    static boolean setEnabledRecursive(JMeterTreeModel model, JMeterTreeNode node, boolean enabled) {
        if (!canToggleEnabled(node)) {
            return false;
        }
        setEnabledRecursive0(model, node, enabled);
        return true;
    }

    static boolean canMoveUp(JMeterTreeNode node) {
        return canReorder(node) && node.getParent().getIndex(node) > 0;
    }

    static boolean canMoveDown(JMeterTreeNode node) {
        return canReorder(node) && node.getParent().getIndex(node) < node.getParent().getChildCount() - 1;
    }

    static boolean moveUp(JMeterTreeModel model, JMeterTreeNode node) {
        if (!canMoveUp(node)) {
            return false;
        }
        move(model, node, node.getParent().getIndex(node) - 1);
        return true;
    }

    static boolean moveDown(JMeterTreeModel model, JMeterTreeNode node) {
        if (!canMoveDown(node)) {
            return false;
        }
        move(model, node, node.getParent().getIndex(node) + 1);
        return true;
    }

    static JMeterTreeNode duplicate(JMeterTreeModel model, JMeterTreeNode node) {
        if (!canRemove(node)) {
            return null;
        }
        JMeterTreeNode copy = deepCopy(node, model);
        MutableTreeNode parent = (MutableTreeNode) node.getParent();
        model.insertNodeInto(copy, parent, parent.getIndex(node) + 1);
        return copy;
    }

    static JMeterTreeNode paste(JMeterTreeModel model, JMeterTreeNode parent, JMeterTreeNode copiedNode) {
        if (parent == null || copiedNode == null || parent.getParent() == null) {
            return null;
        }
        if (!JMeterAddRules.canAddElement(parent, copiedNode.getTestElement())) {
            return null;
        }
        JMeterTreeNode copy = deepCopy(copiedNode, model);
        model.insertNodeInto(copy, parent, parent.getChildCount());
        return copy;
    }

    static JMeterTreeNode pasteExternal(JMeterTreeModel model, JMeterTreeNode parent, JMeterTreeNode copiedNode) {
        JMeterTreeNode externalNode = JMeterXmlClipboard.read(model);
        if (externalNode != null) {
            return paste(model, parent, externalNode);
        }
        return paste(model, parent, copiedNode);
    }

    static boolean canMoveTo(JMeterTreeNode node, JMeterTreeNode parent) {
        return canRemove(node)
                && parent != null
                && node != parent
                && !node.isNodeDescendant(parent)
                && JMeterAddRules.canAddElement(parent, node.getTestElement());
    }

    static JMeterTreeNode moveTo(JMeterTreeModel model, JMeterTreeNode node, JMeterTreeNode parent) {
        if (!canMoveTo(node, parent)) {
            return null;
        }
        model.removeNodeFromParent(node);
        model.insertNodeInto(node, parent, parent.getChildCount());
        return node;
    }

    static JMeterTreeNode deepCopy(JMeterTreeNode source, JMeterTreeModel model) {
        JMeterTreeNode target = new JMeterTreeNode(copyElement(source.getTestElement()), model);
        target.setEnabled(source.isEnabled());
        for (int i = 0; i < source.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) source.getChildAt(i);
            target.add(deepCopy(child, model));
        }
        return target;
    }

    private static boolean canReorder(JMeterTreeNode node) {
        return node != null && node.getParent() != null;
    }

    private static void setEnabledRecursive0(JMeterTreeModel model, JMeterTreeNode node, boolean enabled) {
        node.setEnabled(enabled);
        node.getTestElement().setEnabled(enabled);
        model.nodeChanged(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            setEnabledRecursive0(model, (JMeterTreeNode) node.getChildAt(i), enabled);
        }
    }

    private static void move(JMeterTreeModel model, JMeterTreeNode node, int index) {
        MutableTreeNode parent = (MutableTreeNode) node.getParent();
        model.removeNodeFromParent(node);
        model.insertNodeInto(node, parent, index);
    }

    private static TestElement copyElement(TestElement element) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SaveService.saveElement(element, output);
            return (TestElement) SaveService.loadElement(new ByteArrayInputStream(output.toByteArray()));
        } catch (Exception exception) {
            return (TestElement) element.clone();
        }
    }
}
