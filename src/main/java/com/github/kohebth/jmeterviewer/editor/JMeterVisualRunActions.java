package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.results.JMeterResultsPanel;
import com.github.kohebth.jmeterviewer.results.JMeterResultsWorkspace;
import com.github.kohebth.jmeterviewer.run.JMeterRunController;
import com.github.kohebth.jmeterviewer.run.JMeterRunOptions;
import com.github.kohebth.jmeterviewer.run.JMeterThreadGroupActivity;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.JTree;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JMeterVisualRunActions {
    private final Supplier<JMeterTreeModel> model;
    private final Supplier<JTree> tree;
    private final Runnable updateCurrentNode;
    private final JMeterResultsWorkspace resultsWorkspace;
    private final JMeterResultsPanel resultsPanel;
    private final JMeterThreadGroupActivity threadGroupActivity;
    private final JMeterRunController runController;
    private final JMeterRunOptions runOptions;
    private final Consumer<String> runStatus;

    public JMeterVisualRunActions(Supplier<JMeterTreeModel> model,
                           Supplier<JTree> tree,
                           Runnable updateCurrentNode,
                           JMeterResultsWorkspace resultsWorkspace,
                           JMeterResultsPanel resultsPanel,
                           JMeterThreadGroupActivity threadGroupActivity,
                           JMeterRunController runController,
                           JMeterRunOptions runOptions,
                           Consumer<String> runStatus) {
        this.model = model;
        this.tree = tree;
        this.updateCurrentNode = updateCurrentNode;
        this.resultsWorkspace = resultsWorkspace;
        this.resultsPanel = resultsPanel;
        this.threadGroupActivity = threadGroupActivity;
        this.runController = runController;
        this.runOptions = runOptions;
        this.runStatus = runStatus;
    }

    public void runAuto() {
        run(JMeterRunController.RunTarget.AUTO, false);
    }

    public void runLocal() {
        run(JMeterRunController.RunTarget.LOCAL, false);
    }

    public void runRemote() {
        run(JMeterRunController.RunTarget.REMOTE, false);
    }

    public void runLocalAndRemote() {
        run(JMeterRunController.RunTarget.LOCAL_AND_REMOTE, false);
    }

    public void runSelectedThreadGroup() {
        run(JMeterRunController.RunTarget.LOCAL, true);
    }

    private void run(JMeterRunController.RunTarget target, boolean selectedThreadGroupOnly) {
        JMeterTreeModel currentModel = model.get();
        if (currentModel == null || runController.isRunning()) {
            return;
        }

        updateCurrentNode.run();
        resultsPanel.configureNativeResultViews(currentModel);
        resultsWorkspace.updateNativeResultViews(resultsPanel.availableNativeResultViews(currentModel));
        resultsPanel.clear();
        threadGroupActivity.prepare(currentModel);
        JMeterTreeNode selectedNode = selectedTreeNode();
        if (selectedThreadGroupOnly) {
            threadGroupActivity.startSelected(selectedNode);
        } else {
            threadGroupActivity.start();
        }
        resultsPanel.appendDiagnostic("Starting test");
        resultsPanel.runStarted();
        resultsWorkspace.showViewResultsTree();
        runStatus.accept("Starting");
        if (selectedThreadGroupOnly) {
            runController.startSelectedThreadGroup(currentModel, selectedNode, runOptions, target);
        } else {
            runController.start(currentModel, runOptions, target);
        }
    }

    private JMeterTreeNode selectedTreeNode() {
        JTree currentTree = tree.get();
        if (currentTree == null || currentTree.getSelectionPath() == null) {
            return null;
        }
        Object component = currentTree.getSelectionPath().getLastPathComponent();
        return component instanceof JMeterTreeNode ? (JMeterTreeNode) component : null;
    }
}
