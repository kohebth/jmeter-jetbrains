package com.github.duync.jmeterviewer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;

public final class JMeterVisualFileEditor implements FileEditor, Disposable {
    private final VirtualFile file;
    private final Project project;
    private final JPanel component;
    private final JMeterElementPanel elementPanel;
    private final JEditorPane errorPane;
    private final PropertyChangeSupport propertyChangeSupport;
    private final JButton saveButton;
    private final JButton reloadButton;
    private final JButton runButton;
    private final JButton runLocalButton;
    private final JButton runRemoteButton;
    private final JButton runAllButton;
    private final JButton stopButton;
    private final JButton shutdownButton;
    private final JButton resetEnginesButton;
    private final JButton exitEnginesButton;
    private final JMeterEditorToolbarState toolbarState;
    private final JMeterValidationAction validationAction;
    private final JMeterStatsAction statsAction;
    private final JMeterResultExportActions exportActions;
    private final JMeterResultFileLoader resultFileLoader;
    private final JMeterReportAction reportAction;
    private final JLabel runStatusLabel;
    private final JMeterResultsWorkspace resultsWorkspace;
    private final JMeterResultsPanel resultsPanel;
    private final JMeterRunOptions runOptions;
    private final JMeterThreadControlPanel threadControl;
    private final JMeterSourcePanel sourcePanel;
    private final JMeterRunController runController;
    private final JMeterIdeUndoSupport undoSupport;
    private JMeterTreeModel model;
    private JTree tree;
    private JMeterCommandPalette commandPalette;
    private JMeterTemplateDialog templateDialog;
    private boolean modified;
    private boolean updatingCurrentNode;

    public JMeterVisualFileEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        this.component = new JBPanel<>(new BorderLayout());
        this.elementPanel = new JMeterElementPanel(this::markGuiModified);
        this.errorPane = new JEditorPane("text/plain", "");
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.saveButton = new JButton("Save");
        this.reloadButton = new JButton("Reload");
        this.runButton = new JButton("Run");
        this.runLocalButton = new JButton("Run Local");
        this.runRemoteButton = new JButton("Run Remote");
        this.runAllButton = new JButton("Run All");
        this.stopButton = new JButton("Stop");
        this.shutdownButton = new JButton("Shutdown");
        this.resetEnginesButton = new JButton("Reset Engines");
        this.exitEnginesButton = new JButton("Exit Engines");
        this.runStatusLabel = new JLabel("Idle");
        this.resultsWorkspace = JMeterResultsWorkspace.get(project);
        this.resultsPanel = resultsWorkspace.resultsPanel();
        this.exportActions = new JMeterResultExportActions(project, file, resultsPanel);
        this.resultFileLoader = new JMeterResultFileLoader(project, resultsPanel);
        this.reportAction = new JMeterReportAction(project, resultsPanel);
        this.validationAction = new JMeterValidationAction(() -> model, resultsPanel);
        this.statsAction = new JMeterStatsAction(() -> model, resultsPanel);
        this.runOptions = new JMeterRunOptions(project);
        this.runController = new JMeterRunController(new JMeterEditorRunListener(this::setRunStatus, resultsPanel));
        this.threadControl = new JMeterThreadControlPanel(runController);
        this.sourcePanel = new JMeterSourcePanel(project, file, this::load,
                this::updateCurrentJMeterNode, () -> model, this);
        this.undoSupport = new JMeterIdeUndoSupport(project, file, this::restoreModel);
        this.toolbarState = new JMeterEditorToolbarState(saveButton, reloadButton, runButton, runLocalButton,
                runRemoteButton, runAllButton, stopButton,
                shutdownButton, resetEnginesButton, exitEnginesButton, runStatusLabel, resultFileLoader,
                exportActions, reportAction, validationAction, statsAction, runOptions, threadControl, resultsPanel);

        JMeterEditorControls.wire(
                component,
                saveButton,
                reloadButton,
                runButton,
                runLocalButton,
                runRemoteButton,
                runAllButton,
                stopButton,
                shutdownButton,
                resetEnginesButton,
                exitEnginesButton,
                this::save,
                this::reloadFromFile,
                this::runTest,
                () -> runTest(JMeterRunController.RunTarget.LOCAL),
                () -> runTest(JMeterRunController.RunTarget.REMOTE),
                () -> runTest(JMeterRunController.RunTarget.LOCAL_AND_REMOTE),
                runController,
                validationAction,
                this::showCommands
        );
        JMeterFileChangeWatcher.install(file, this, this::isModified, this::load);
        load();
    }

    void reloadFromFile() {
        if (JMeterReloadGuard.canDiscard(project, modified)) {
            load();
        }
    }

    private void load() {
        try {
            EmbeddedJMeterRuntime.ensureReady();
            JMeterPluginClasspathStore.get(project).applyToClasspath();
            model = JMeterTreeLoader.load(new File(file.getPath()));
            undoSupport.reset(model);
            installModel();
            setModified(false);
            selectInitialNode();
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to load JMX: " + exception.getMessage());
            showLoadError(exception);
        }
    }

    private void installModel() {
        JMeterTreeActions treeActions = new JMeterTreeActions(model, this::markTreeModified);

        JMeterTreeListener listener = new JMeterTreeListener(model);
        listener.setActionHandler(event -> showSelectedElement());
        GuiPackage.initInstance(listener, model);

        tree = JMeterTreeView.create(model, listener, treeActions, this::markTreeModified);
        JMeterTreeFileActions fileActions = new JMeterTreeFileActions(project, model,
                treeActions::selectedNode, treeActions::selectNode, this::markTreeModified);
        JMeterAddElementDialog addDialog = new JMeterAddElementDialog(project, treeActions);
        templateDialog = new JMeterTemplateDialog(project, treeActions);
        JMeterSearchController search = new JMeterSearchController(
                project,
                () -> model,
                () -> tree,
                treeActions::selectNode,
                this::markTreeModified
        );
        commandPalette = new JMeterCommandPalette(project, treeActions, fileActions, addDialog, templateDialog, search);

        component.removeAll();
        component.add(JMeterToolbarFactory.create(project, () -> model, toolbarState,
                treeActions, fileActions, addDialog, templateDialog, commandPalette, search), BorderLayout.NORTH);
        sourcePanel.refresh();
        component.add(JMeterEditorBody.create(tree, elementPanel.component(), sourcePanel), BorderLayout.CENTER);
    }

    void showCommands() { if (commandPalette != null) commandPalette.show(); }

    void showTemplates() { if (templateDialog != null) templateDialog.show(); }

    private void restoreModel(JMeterTreeModel restoredModel) {
        model = restoredModel;
        installModel();
        selectInitialNode();
        setModified(true);
    }

    private void selectInitialNode() {
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        if (root.getChildCount() == 0) {
            return;
        }

        JMeterTreeNode testPlan = (JMeterTreeNode) root.getChildAt(0);
        TreePath path = new TreePath(testPlan.getPath());
        tree.expandPath(path);
        tree.setSelectionPath(path);
    }

    void save() {
        save(true);
    }

    void saveSilently() {
        save(false);
    }

    private void save(boolean notifySuccess) {
        updateCurrentJMeterNode();
        if (JMeterFileSaver.save(project, file, model, elementPanel, notifySuccess)) {
            sourcePanel.refresh();
            setModified(false);
        }
    }

    void runTest() {
        runTest(JMeterRunController.RunTarget.AUTO);
    }

    private void runTest(JMeterRunController.RunTarget target) {
        if (model == null || runController.isRunning()) {
            return;
        }
        updateCurrentJMeterNode();
        resultsPanel.clear();
        resultsPanel.appendDiagnostic("Starting test");
        resultsPanel.runStarted();
        resultsWorkspace.showViewResultsTree();
        setRunStatus("Starting");
        runController.start(model, runOptions, target);
    }

    private void showSelectedElement() {
        elementPanel.showSelected();
        if (isViewResultsTreeSelected()) {
            resultsWorkspace.showViewResultsTree();
        }
    }

    private boolean isViewResultsTreeSelected() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getCurrentNode() == null) {
            return false;
        }
        Object object = guiPackage.getCurrentNode().getUserObject();
        if (!(object instanceof TestElement)) {
            return false;
        }
        TestElement element = (TestElement) object;
        return "ViewResultsFullVisualizer".equals(element.getPropertyAsString(TestElement.GUI_CLASS))
                || "org.apache.jmeter.visualizers.ViewResultsFullVisualizer"
                .equals(element.getPropertyAsString(TestElement.GUI_CLASS));
    }

    void stopTest() { runController.stop(); }

    void validatePlan() { validationAction.validateNow(); }

    private void setRunStatus(String status) {
        runStatusLabel.setText(status);
        boolean running = runController.isRunning();
        runButton.setEnabled(!running);
        runLocalButton.setEnabled(!running);
        runRemoteButton.setEnabled(!running);
        runAllButton.setEnabled(!running);
        stopButton.setEnabled(running);
        shutdownButton.setEnabled(running);
    }

    private void markGuiModified() {
        if (updatingCurrentNode) {
            return;
        }
        updateCurrentJMeterNode();
        markTreeModified();
    }

    private void updateCurrentJMeterNode() {
        try {
            updatingCurrentNode = true;
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null || model == null || tree == null) {
                return;
            }
            guiPackage.updateCurrentNode();
            JMeterTreeNode currentNode = guiPackage.getCurrentNode();
            if (currentNode != null) {
                model.nodeChanged(currentNode);
                tree.repaint();
            }
        } finally {
            updatingCurrentNode = false;
        }
    }

    private void setModified(boolean modified) {
        boolean oldValue = this.modified;
        this.modified = modified;
        saveButton.setEnabled(modified);
        propertyChangeSupport.firePropertyChange(FileEditor.PROP_MODIFIED, oldValue, modified);
    }

    private void markTreeModified() {
        undoSupport.record(model);
        setModified(true);
    }

    private void showLoadError(Exception exception) {
        errorPane.setEditable(false);
        errorPane.setText(exception.getMessage());
        component.removeAll();
        component.add(new JBScrollPane(errorPane));
    }

    @Override public @NotNull JComponent getComponent() { return component; }
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return component; }
    @Override public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() { return "JMeter"; }
    @Override public @NotNull VirtualFile getFile() { return file; }
    @Override public void setState(@NotNull FileEditorState state) { }
    @Override public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) { return FileEditorState.INSTANCE; }
    @Override public boolean isModified() { return modified; }
    @Override public boolean isValid() { return file.isValid(); }
    @Override
    public void selectNotify() {
        if (!modified) {
            load();
        }
    }

    @Override public void deselectNotify() { }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    @Override public @Nullable FileEditorLocation getCurrentLocation() { return null; }

    @Override public <T> @Nullable T getUserData(@NotNull Key<T> key) { return null; }

    @Override public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) { }

    @Override public void dispose() { }
}
