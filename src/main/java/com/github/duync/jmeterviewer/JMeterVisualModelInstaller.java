package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.util.function.Supplier;

final class JMeterVisualModelInstaller {
    private JMeterVisualModelInstaller() {
    }

    static Installed install(Project project,
                             JMeterTreeModel model,
                             JPanel component,
                             JMeterEditorToolbarState toolbarState,
                             JMeterElementPanel elementPanel,
                             JMeterResultsPanel resultsPanel,
                             JMeterResultsWorkspace resultsWorkspace,
                             JMeterThreadGroupActivity threadGroupActivity,
                             Runnable modified,
                             Runnable suppressSelectionDirty) {
        JMeterTreeActions treeActions = new JMeterTreeActions(model, modified);
        JMeterTreeListener listener = new JMeterTreeListener(model);
        listener.setActionHandler(event -> showSelectedElement(elementPanel, resultsPanel, resultsWorkspace,
                suppressSelectionDirty));
        GuiPackage.initInstance(listener, model);

        Supplier<JTree> treeSupplier = () -> treeActionsTree(treeActions);
        threadGroupActivity.setChangeListener(() -> {
            JTree tree = treeSupplier.get();
            if (tree != null) {
                tree.repaint();
            }
        });
        threadGroupActivity.clear();

        JTree tree = JMeterTreeView.create(model, listener, treeActions, threadGroupActivity, modified);
        JMeterTreeFileActions fileActions = new JMeterTreeFileActions(project, model,
                treeActions::selectedNode, treeActions::selectNode, modified);
        JMeterAddElementDialog addDialog = new JMeterAddElementDialog(project, treeActions);
        JMeterTemplateDialog templateDialog = new JMeterTemplateDialog(project, treeActions);
        JMeterSearchController search = new JMeterSearchController(
                project,
                () -> model,
                () -> tree,
                treeActions::selectNode,
                modified
        );
        JMeterCommandPalette commandPalette = new JMeterCommandPalette(project, treeActions, fileActions,
                addDialog, templateDialog, search);

        component.removeAll();
        component.add(JMeterEditorToolbar.create(toolbarState), BorderLayout.NORTH);
        component.add(JMeterEditorBody.create(tree, elementPanel.component()), BorderLayout.CENTER);
        selectInitialNode(model, tree);
        return new Installed(tree, treeActions, commandPalette, templateDialog);
    }

    private static void selectInitialNode(JMeterTreeModel model, JTree tree) {
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        if (root.getChildCount() == 0) {
            return;
        }
        JMeterTreeNode testPlan = (JMeterTreeNode) root.getChildAt(0);
        TreePath path = new TreePath(testPlan.getPath());
        tree.expandPath(path);
        tree.setSelectionPath(path);
    }

    private static JTree treeActionsTree(JMeterTreeActions actions) {
        return actions.tree();
    }

    private static void showSelectedElement(JMeterElementPanel elementPanel,
                                            JMeterResultsPanel resultsPanel,
                                            JMeterResultsWorkspace resultsWorkspace,
                                            Runnable suppressSelectionDirty) {
        suppressSelectionDirty.run();
        elementPanel.showSelected();
        TestElement selected = selectedElement();
        JMeterActionTrace.info("editor.tree.selection",
                selected == null ? "node=<none>" : "node=\"" + selected.getName() + "\" type="
                        + selected.getClass().getSimpleName());
        JMeterNativeResultView view = resultsPanel.nativeViewFor(selected);
        if (view != null) {
            resultsPanel.configureNativeResultView(view, selected);
            resultsWorkspace.showNativeView(view);
        }
    }

    private static TestElement selectedElement() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getCurrentNode() == null) {
            return null;
        }
        Object object = guiPackage.getCurrentNode().getUserObject();
        return object instanceof TestElement ? (TestElement) object : null;
    }

    static final class Installed {
        private final JTree tree;
        private final JMeterTreeActions treeActions;
        private final JMeterCommandPalette commandPalette;
        private final JMeterTemplateDialog templateDialog;

        private Installed(JTree tree,
                          JMeterTreeActions treeActions,
                          JMeterCommandPalette commandPalette,
                          JMeterTemplateDialog templateDialog) {
            this.tree = tree;
            this.treeActions = treeActions;
            this.commandPalette = commandPalette;
            this.templateDialog = templateDialog;
        }

        JTree tree() {
            return tree;
        }

        JMeterTreeActions treeActions() {
            return treeActions;
        }

        JMeterCommandPalette commandPalette() {
            return commandPalette;
        }

        JMeterTemplateDialog templateDialog() {
            return templateDialog;
        }
    }
}
