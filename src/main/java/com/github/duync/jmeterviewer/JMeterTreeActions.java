package com.github.duync.jmeterviewer;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.util.*;

final class JMeterTreeActions {
    private final JMeterTreeModel model;
    private final Runnable modified;
    private JTree tree;
    private JMeterTreeNode clipboardNode;

    JMeterTreeActions(JMeterTreeModel model, Runnable modified) {
        this.model = model;
        this.modified = modified;
    }

    void setTree(JTree tree) {
        this.tree = tree;
    }

    JMeterTreeNode selectedNode() {
        if (tree == null || tree.getSelectionPath() == null) {
            return null;
        }
        Object component = tree.getSelectionPath().getLastPathComponent();
        return component instanceof JMeterTreeNode ? (JMeterTreeNode) component : null;
    }

    java.util.List<JMeterTreeNode> selectedNodes() {
        java.util.List<JMeterTreeNode> nodes = new ArrayList<>();
        if (tree == null || tree.getSelectionPaths() == null) {
            return nodes;
        }
        for (TreePath path : tree.getSelectionPaths()) {
            Object component = path.getLastPathComponent();
            if (component instanceof JMeterTreeNode) {
                nodes.add((JMeterTreeNode) component);
            }
        }
        nodes.sort(Comparator.comparingInt(node -> -node.getLevel()));
        return nodes;
    }

    void selectNode(JMeterTreeNode node) {
        if (tree == null || node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path.getParentPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    void addPaletteItem(JMeterPaletteItem item) {
        JMeterTreeNode added = JMeterTreeOperations.add(model, selectedNode(), item);
        if (added != null) {
            selectNode(added);
            modified.run();
        }
    }

    void insertTemplate(JMeterTemplate template) {
        JMeterTreeNode lastAdded = null;
        for (JMeterTemplate.Node root : template.roots()) {
            JMeterTreeNode parent = JMeterTemplateInsertion.parent(model, selectedNode(), root);
            if (parent == null) {
                continue;
            }
            JMeterTreeNode added = JMeterTreeOperations.addTemplate(model, parent, root);
            if (added != null) {
                lastAdded = added;
            }
        }
        if (lastAdded != null) {
            selectNode(lastAdded);
            modified.run();
        }
    }

    void deleteSelected() {
        boolean changed = false;
        for (JMeterTreeNode node : selectedNodes()) {
            changed |= JMeterTreeOperations.remove(model, node);
        }
        if (changed) {
            modified.run();
        }
    }

    void toggleSelectedEnabled() {
        if (JMeterTreeOperations.toggleEnabled(model, selectedNode())) {
            modified.run();
        }
    }

    void enableSelected() {
        if (setSelectedEnabled(true, false)) {
            modified.run();
        }
    }

    void disableSelected() {
        if (setSelectedEnabled(false, false)) {
            modified.run();
        }
    }

    void enableSelectedTree() {
        if (setSelectedEnabled(true, true)) {
            modified.run();
        }
    }

    void disableSelectedTree() {
        if (setSelectedEnabled(false, true)) {
            modified.run();
        }
    }

    void duplicateSelected() {
        JMeterTreeNode lastDuplicated = null;
        for (JMeterTreeNode node : selectedNodesInDisplayOrder()) {
            JMeterTreeNode duplicated = JMeterTreeOperations.duplicate(model, node);
            if (duplicated != null) {
                lastDuplicated = duplicated;
            }
        }
        if (lastDuplicated != null) {
            selectNode(lastDuplicated);
            modified.run();
        }
    }

    void duplicateSelectedDisabled() {
        JMeterTreeNode lastDuplicated = null;
        for (JMeterTreeNode node : selectedNodesInDisplayOrder()) {
            JMeterTreeNode duplicated = JMeterTreeOperations.duplicate(model, node);
            if (duplicated != null) {
                JMeterTreeOperations.setEnabledRecursive(model, duplicated, false);
                lastDuplicated = duplicated;
            }
        }
        if (lastDuplicated != null) {
            selectNode(lastDuplicated);
            modified.run();
        }
    }

    void copySelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            clipboardNode = JMeterTreeOperations.deepCopy(selected, model);
            JMeterXmlClipboard.write(selected);
        }
    }

    void cutSelected() {
        copySelected();
        boolean changed = false;
        for (JMeterTreeNode node : selectedNodes()) {
            changed |= JMeterTreeOperations.remove(model, node);
        }
        if (changed) {
            modified.run();
        }
    }

    void pasteIntoSelected() {
        JMeterTreeNode pasted = JMeterTreeOperations.pasteExternal(model, selectedNode(), clipboardNode);
        if (pasted != null) {
            selectNode(pasted);
            modified.run();
        }
    }

    void moveSelectedUp() {
        JMeterTreeNode selected = selectedNode();
        if (JMeterTreeOperations.moveUp(model, selected)) {
            selectNode(selected);
            modified.run();
        }
    }

    void moveSelectedDown() {
        JMeterTreeNode selected = selectedNode();
        if (JMeterTreeOperations.moveDown(model, selected)) {
            selectNode(selected);
            modified.run();
        }
    }

    void insertSimpleControllerParent() {
        JMeterTreeNode wrapper = JMeterStructuralOperations.insertParent(model,
                selectedNodesInDisplayOrder(),
                JMeterPaletteItem.findByLabel("Simple Controller"));
        if (wrapper != null) {
            selectNode(wrapper);
            modified.run();
        }
    }

    void changeSelectedParentToSimpleController() {
        JMeterTreeNode replacement = JMeterStructuralOperations.changeParent(model,
                selectedNode(),
                JMeterPaletteItem.findByLabel("Simple Controller"));
        if (replacement != null) {
            selectNode(replacement);
            modified.run();
        }
    }

    void addThinkTimes() {
        JMeterTreeNode parent = selectedNode();
        if (JMeterStructuralOperations.addThinkTimes(model, parent) > 0) {
            selectNode(parent);
            modified.run();
        }
    }

    void expandSelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            expandNode(selected);
        }
    }

    void collapseSelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            collapseNode(selected);
        }
    }

    void expandAll() {
        expandNode((JMeterTreeNode) model.getRoot());
    }

    void collapseAll() {
        collapseNode((JMeterTreeNode) model.getRoot());
    }

    private boolean setSelectedEnabled(boolean enabled, boolean recursive) {
        boolean changed = false;
        for (JMeterTreeNode node : selectedNodes()) {
            changed |= recursive
                    ? JMeterTreeOperations.setEnabledRecursive(model, node, enabled)
                    : JMeterTreeOperations.setEnabled(model, node, enabled);
        }
        return changed;
    }

    private java.util.List<JMeterTreeNode> selectedNodesInDisplayOrder() {
        java.util.List<JMeterTreeNode> nodes = selectedNodes();
        Collections.reverse(nodes);
        return nodes;
    }

    private void expandNode(JMeterTreeNode node) {
        if (tree == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            expandNode((JMeterTreeNode) node.getChildAt(i));
        }
    }

    private void collapseNode(JMeterTreeNode node) {
        if (tree == null) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collapseNode((JMeterTreeNode) node.getChildAt(i));
        }
        if (node.getParent() != null) {
            tree.collapsePath(new TreePath(node.getPath()));
        }
    }
}
