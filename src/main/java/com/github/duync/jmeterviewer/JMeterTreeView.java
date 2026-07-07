package com.github.duync.jmeterviewer;

import com.intellij.ui.TreeSpeedSearch;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.TreePath;
import java.awt.event.*;
import java.util.*;

final class JMeterTreeView {
    private JMeterTreeView() {
    }

    static JTree create(JMeterTreeModel model,
                        JMeterTreeListener listener,
                        JMeterTreeActions actions,
                        JMeterThreadGroupActivity activity,
                        Runnable modified) {
        JTree tree = new JTree(model);
        tree.setCellRenderer(new JMeterActivityTreeRenderer(activity));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new JMeterTreeTransferHandler(model, modified));
        new TreeSpeedSearch(tree, path -> path.getLastPathComponent().toString(), true);

        actions.setTree(tree);
        listener.setJTree(tree);
        tree.addTreeSelectionListener(listener);
        tree.addKeyListener(listener);
        tree.addMouseListener(new PopupListener(tree, actions));
        installShortcuts(tree, actions);
        return tree;
    }

    private static void installShortcuts(JTree tree, JMeterTreeActions actions) {
        bind(tree, KeyEvent.VK_DELETE, 0, "jmeter.delete", actions::deleteSelected);
        bind(tree, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, "jmeter.copy", actions::copySelected);
        bind(tree, KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, "jmeter.cut", actions::cutSelected);
        bind(tree, KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, "jmeter.paste", actions::pasteIntoSelected);
        bind(tree, KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, "jmeter.duplicate", actions::duplicateSelected);
        bind(tree, KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK, "jmeter.moveUp", actions::moveSelectedUp);
        bind(tree, KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK, "jmeter.moveDown", actions::moveSelectedDown);
        bind(tree, KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, "jmeter.toggleEnabled", actions::toggleSelectedEnabled);
        bind(tree, KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                "jmeter.enableSubtree", actions::enableSelectedTree);
        bind(tree, KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                "jmeter.disableSubtree", actions::disableSelectedTree);
        bind(tree, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, "jmeter.expandSelected", actions::expandSelected);
        bind(tree, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, "jmeter.collapseSelected", actions::collapseSelected);
        bind(tree, KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                "jmeter.expandAll", actions::expandAll);
        bind(tree, KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                "jmeter.collapseAll", actions::collapseAll);
    }

    private static void bind(JTree tree, int keyCode, int modifiers, String name, Runnable runnable) {
        tree.getInputMap().put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        tree.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                runnable.run();
            }
        });
    }

    private static final class PopupListener extends MouseAdapter {
        private final JTree tree;
        private final JMeterTreeActions actions;

        private PopupListener(JTree tree, JMeterTreeActions actions) {
            this.tree = tree;
            this.actions = actions;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            maybeShowPopup(event);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            maybeShowPopup(event);
        }

        private void maybeShowPopup(MouseEvent event) {
            if (!event.isPopupTrigger()) {
                return;
            }
            TreePath path = tree.getPathForLocation(event.getX(), event.getY());
            if (path != null) {
                tree.setSelectionPath(path);
            }
            createPopup().show(tree, event.getX(), event.getY());
        }

        private JPopupMenu createPopup() {
            JPopupMenu popup = new JPopupMenu();
            popup.add(createAddMenu());
            popup.addSeparator();
            popup.add(menuItem("Delete", actions::deleteSelected));
            popup.add(menuItem("Duplicate", actions::duplicateSelected));
            popup.add(menuItem("Duplicate Disabled", actions::duplicateSelectedDisabled));
            popup.add(menuItem("Copy", actions::copySelected));
            popup.add(menuItem("Cut", actions::cutSelected));
            popup.add(menuItem("Paste", actions::pasteIntoSelected));
            popup.addSeparator();
            popup.add(menuItem("Toggle Enabled", actions::toggleSelectedEnabled));
            popup.add(menuItem("Enable", actions::enableSelected));
            popup.add(menuItem("Disable", actions::disableSelected));
            popup.add(menuItem("Enable Subtree", actions::enableSelectedTree));
            popup.add(menuItem("Disable Subtree", actions::disableSelectedTree));
            popup.addSeparator();
            popup.add(menuItem("Move Up", actions::moveSelectedUp));
            popup.add(menuItem("Move Down", actions::moveSelectedDown));
            popup.addSeparator();
            popup.add(menuItem("Wrap in Simple Controller", actions::insertSimpleControllerParent));
            popup.add(menuItem("Change Parent to Simple Controller", actions::changeSelectedParentToSimpleController));
            popup.add(menuItem("Add Think Times", actions::addThinkTimes));
            popup.addSeparator();
            popup.add(menuItem("Expand", actions::expandSelected));
            popup.add(menuItem("Collapse", actions::collapseSelected));
            popup.add(menuItem("Expand All", actions::expandAll));
            popup.add(menuItem("Collapse All", actions::collapseAll));
            return popup;
        }

        private JMenu createAddMenu() {
            JMenu addMenu = new JMenu("Add");
            JMeterTreeNode selected = actions.selectedNode();
            Map<JMeterPaletteItem.Kind, java.util.List<JMeterPaletteItem>> grouped = addableItemsByKind(selected);
            for (JMeterPaletteItem.Kind kind : JMeterPaletteItem.Kind.values()) {
                java.util.List<JMeterPaletteItem> items = grouped.get(kind);
                if (items != null && !items.isEmpty()) {
                    addMenu.add(new LazyAddCategoryMenu(categoryLabel(kind), items, actions));
                }
            }
            return addMenu;
        }

        private Map<JMeterPaletteItem.Kind, java.util.List<JMeterPaletteItem>> addableItemsByKind(JMeterTreeNode selected) {
            Map<JMeterPaletteItem.Kind, java.util.List<JMeterPaletteItem>> grouped =
                    new EnumMap<>(JMeterPaletteItem.Kind.class);
            for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
                if (JMeterTreeOperations.canAdd(selected, item)) {
                    grouped.computeIfAbsent(item.kind(), ignored -> new ArrayList<>()).add(item);
                }
            }
            return grouped;
        }

        private JMenuItem menuItem(String label, Runnable action) {
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(event -> action.run());
            return item;
        }

        private String categoryLabel(JMeterPaletteItem.Kind kind) {
            switch (kind) {
                case THREAD_GROUP:
                    return "Threads";
                case TEST_FRAGMENT:
                    return "Test Fragments";
                case SAMPLER:
                    return "Samplers";
                case CONTROLLER:
                    return "Logic Controllers";
                case CONFIG:
                    return "Config Elements";
                case ASSERTION:
                    return "Assertions";
                case TIMER:
                    return "Timers";
                case PRE_PROCESSOR:
                    return "Pre Processors";
                case POST_PROCESSOR:
                    return "Post Processors";
                case LISTENER:
                    return "Listeners";
                case NON_TEST:
                    return "Non-Test Elements";
                default:
                    return kind.name();
            }
        }
    }

    private static final class LazyAddCategoryMenu extends JMenu {
        private final java.util.List<JMeterPaletteItem> items;
        private final JMeterTreeActions actions;
        private boolean populated;

        private LazyAddCategoryMenu(String label,
                                    java.util.List<JMeterPaletteItem> items,
                                    JMeterTreeActions actions) {
            super(label);
            this.items = items;
            this.actions = actions;
            addMenuListener(new MenuListener() {
                @Override public void menuSelected(MenuEvent event) { populate(); }
                @Override public void menuDeselected(MenuEvent event) { }
                @Override public void menuCanceled(MenuEvent event) { }
            });
        }

        private void populate() {
            if (populated) {
                return;
            }
            populated = true;
            for (JMeterPaletteItem item : items) {
                JMenuItem menuItem = new JMenuItem(item.toString());
                menuItem.addActionListener(event -> actions.addPaletteItem(item));
                add(menuItem);
            }
        }
    }
}
