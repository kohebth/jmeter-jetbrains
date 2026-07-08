package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.palette.JMeterPaletteItem;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.*;

public final class JMeterTreeTransferHandler extends TransferHandler {
    private static final DataFlavor NODE_FLAVOR = new DataFlavor(JMeterTreeNode.class, "JMeter tree node");
    private final JMeterTreeModel model;
    private final Runnable changeListener;
    private JMeterTreeNode draggedNode;

    public JMeterTreeTransferHandler(JMeterTreeModel model, Runnable changeListener) {
        this.model = model;
        this.changeListener = changeListener;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        JMeterTreeNode parent = dropTarget(support);
        if (parent == null) {
            return false;
        }
        if (support.isDataFlavorSupported(NODE_FLAVOR)) {
            return JMeterTreeOperations.canMoveTo(draggedNode, parent);
        }
        if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            JMeterPaletteItem item = droppedItem(support);
            return item != null && JMeterTreeOperations.canAdd(parent, item);
        }
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent component) {
        if (!(component instanceof JTree)) {
            return null;
        }
        JTree tree = (JTree) component;
        Object selected = tree.getLastSelectedPathComponent();
        if (!(selected instanceof JMeterTreeNode)) {
            return null;
        }
        draggedNode = (JMeterTreeNode) selected;
        return new NodeTransferable(draggedNode);
    }

    @Override
    public int getSourceActions(JComponent component) {
        return COPY_OR_MOVE;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        JMeterTreeNode parent = dropTarget(support);
        if (parent == null) {
            return false;
        }
        JMeterTreeNode child = support.isDataFlavorSupported(NODE_FLAVOR)
                ? importNode(support, parent)
                : importPaletteItem(support, parent);
        if (child != null) {
            selectNode(support, child);
            changeListener.run();
            return true;
        }
        return false;
    }

    private JMeterTreeNode importNode(TransferSupport support, JMeterTreeNode parent) {
        JMeterTreeNode node = transferredNode(support);
        if (node == null) {
            return null;
        }
        if ((support.getDropAction() & MOVE) == MOVE) {
            return JMeterTreeOperations.moveTo(model, node, parent);
        }
        return JMeterTreeOperations.paste(model, parent, node);
    }

    private JMeterTreeNode importPaletteItem(TransferSupport support, JMeterTreeNode parent) {
        JMeterPaletteItem item = droppedItem(support);
        return item == null ? null : JMeterTreeOperations.add(model, parent, item);
    }

    private JMeterTreeNode dropTarget(TransferSupport support) {
        if (!(support.getComponent() instanceof JTree)) {
            return null;
        }
        JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
        TreePath path = location.getPath();
        if (path == null) {
            return null;
        }
        Object component = path.getLastPathComponent();
        return component instanceof JMeterTreeNode ? (JMeterTreeNode) component : null;
    }

    private JMeterPaletteItem droppedItem(TransferSupport support) {
        try {
            String key = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
            return JMeterPaletteItem.findByKey(key);
        } catch (Exception exception) {
            return null;
        }
    }

    private JMeterTreeNode transferredNode(TransferSupport support) {
        try {
            return (JMeterTreeNode) support.getTransferable().getTransferData(NODE_FLAVOR);
        } catch (Exception exception) {
            return null;
        }
    }

    private static void selectNode(TransferSupport support, JMeterTreeNode node) {
        if (support.getComponent() instanceof JTree) {
            JTree tree = (JTree) support.getComponent();
            TreePath path = new TreePath(node.getPath());
            tree.expandPath(path.getParentPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private static final class NodeTransferable implements Transferable {
        private final JMeterTreeNode node;

        private NodeTransferable(JMeterTreeNode node) {
            this.node = node;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{NODE_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return NODE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return node;
        }
    }
}
