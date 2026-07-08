package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;
import com.github.kohebth.jmeterviewer.templates.JMeterNativeTemplateInsertion;
import com.github.kohebth.jmeterviewer.templates.JMeterTemplate;
import com.github.kohebth.jmeterviewer.templates.JMeterTemplateInsertion;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.util.*;

public final class JMeterTreeActions {
    private final JMeterTreeModel model;
    private final Runnable modified;
    private JTree tree;
    private JMeterTreeNode clipboardNode;

    public JMeterTreeActions(JMeterTreeModel model, Runnable modified) {
        this.model = model;
        this.modified = modified;
    }

    public void setTree(JTree tree) { this.tree = tree; }

    public JTree tree() { return tree; }

    public JMeterTreeNode selectedNode() {
        if (tree == null || tree.getSelectionPath() == null) {
            return null;
        }
        Object component = tree.getSelectionPath().getLastPathComponent();
        return component instanceof JMeterTreeNode ? (JMeterTreeNode) component : null;
    }

    public java.util.List<JMeterTreeNode> selectedNodes() {
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

    public void selectNode(JMeterTreeNode node) {
        if (tree == null || node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path.getParentPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    public void selectAllVisible() {
        if (tree == null || tree.getRowCount() == 0) {
            return;
        }
        java.util.List<TreePath> paths = new ArrayList<>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                paths.add(path);
            }
        }
        tree.setSelectionPaths(paths.toArray(new TreePath[0]));
    }

    public void addPaletteItem(JMeterPaletteItem item) {
        JMeterTreeNode added = JMeterTreeOperations.add(model, selectedNode(), item);
        if (added != null) {
            selectNode(added);
            modified.run();
        }
    }

    public void insertTemplate(JMeterTemplate template) {
        if (template.nativeTemplate()) {
            insertNativeTemplate(template);
            return;
        }
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

    private void insertNativeTemplate(JMeterTemplate template) {
        try {
            JMeterTreeNode added = JMeterNativeTemplateInsertion.insert(model, selectedNode(), template);
            if (added != null) {
                selectNode(added);
                modified.run();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to insert JMeter template: " + template.name(), exception);
        }
    }

    public void deleteSelected() {
        boolean changed = false;
        for (JMeterTreeNode node : selectedNodes()) {
            changed |= JMeterTreeOperations.remove(model, node);
        }
        if (changed) {
            modified.run();
        }
    }

    public void toggleSelectedEnabled() {
        if (JMeterTreeOperations.toggleEnabled(model, selectedNode())) {
            modified.run();
        }
    }

    public void enableSelected() {
        if (setSelectedEnabled(true, false)) {
            modified.run();
        }
    }

    public void disableSelected() {
        if (setSelectedEnabled(false, false)) {
            modified.run();
        }
    }

    public void enableSelectedTree() {
        if (setSelectedEnabled(true, true)) {
            modified.run();
        }
    }

    public void disableSelectedTree() {
        if (setSelectedEnabled(false, true)) {
            modified.run();
        }
    }

    public void duplicateSelected() {
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

    public void duplicateSelectedDisabled() {
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

    public void copySelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            clipboardNode = JMeterTreeOperations.deepCopy(selected, model);
            JMeterXmlClipboard.write(selected);
        }
    }

    public void cutSelected() {
        copySelected();
        boolean changed = false;
        for (JMeterTreeNode node : selectedNodes()) {
            changed |= JMeterTreeOperations.remove(model, node);
        }
        if (changed) {
            modified.run();
        }
    }

    public void pasteIntoSelected() {
        JMeterTreeNode pasted = JMeterTreeOperations.pasteExternal(model, selectedNode(), clipboardNode);
        if (pasted != null) {
            selectNode(pasted);
            modified.run();
        }
    }

    public void moveSelectedUp() {
        JMeterTreeNode selected = selectedNode();
        if (JMeterTreeOperations.moveUp(model, selected)) {
            selectNode(selected);
            modified.run();
        }
    }

    public void moveSelectedDown() {
        JMeterTreeNode selected = selectedNode();
        if (JMeterTreeOperations.moveDown(model, selected)) {
            selectNode(selected);
            modified.run();
        }
    }

    public void insertSimpleControllerParent() {
        JMeterTreeNode wrapper = JMeterStructuralOperations.insertParent(model,
                selectedNodesInDisplayOrder(),
                JMeterPaletteItem.findByLabel("Simple Controller"));
        if (wrapper != null) {
            selectNode(wrapper);
            modified.run();
        }
    }

    public void changeSelectedParentToSimpleController() {
        JMeterTreeNode replacement = JMeterStructuralOperations.changeParent(model,
                selectedNode(),
                JMeterPaletteItem.findByLabel("Simple Controller"));
        if (replacement != null) {
            selectNode(replacement);
            modified.run();
        }
    }

    public void addThinkTimes() {
        JMeterTreeNode parent = selectedNode();
        if (JMeterStructuralOperations.addThinkTimes(model, parent) > 0) {
            selectNode(parent);
            modified.run();
        }
    }

    public void expandSelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            expandNode(selected);
        }
    }

    public void collapseSelected() {
        JMeterTreeNode selected = selectedNode();
        if (selected != null) {
            collapseNode(selected);
        }
    }

    public void expandAll() { expandNode((JMeterTreeNode) model.getRoot()); }

    public void collapseAll() { collapseNode((JMeterTreeNode) model.getRoot()); }

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
