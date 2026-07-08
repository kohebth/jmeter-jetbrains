package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.ide.JMeterActionTrace;
import com.github.kohebth.jmeterviewer.ide.JMeterFileChangeWatcher;
import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;
import com.github.kohebth.jmeterviewer.ide.JMeterIdeUndoSupport;
import com.github.kohebth.jmeterviewer.io.JMeterFileSaver;
import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;
import com.github.kohebth.jmeterviewer.results.JMeterResultsPanel;
import com.github.kohebth.jmeterviewer.results.JMeterResultsWorkspace;
import com.github.kohebth.jmeterviewer.run.JMeterEditorRunListener;
import com.github.kohebth.jmeterviewer.run.JMeterRunController;
import com.github.kohebth.jmeterviewer.run.JMeterRunOptions;
import com.github.kohebth.jmeterviewer.run.JMeterThreadControlPanel;
import com.github.kohebth.jmeterviewer.run.JMeterThreadGroupActivity;
import com.github.kohebth.jmeterviewer.runtime.EmbeddedJMeterRuntime;
import com.github.kohebth.jmeterviewer.runtime.JMeterPluginClasspathStore;
import com.github.kohebth.jmeterviewer.templates.JMeterTemplateDialog;
import com.github.kohebth.jmeterviewer.validation.JMeterValidationAction;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    private final JButton runButton, runSelectedButton, stopButton, shutdownButton, resetEnginesButton, exitEnginesButton;
    private final JMeterEditorToolbarState toolbarState;
    private final JMeterValidationAction validationAction;
    private final JLabel runStatusLabel;
    private final JMeterResultsWorkspace resultsWorkspace;
    private final JMeterResultsPanel resultsPanel;
    private final JMeterRunOptions runOptions;
    private final JMeterThreadControlPanel threadControl;
    private final JMeterThreadGroupActivity threadGroupActivity;
    private final JMeterRunController runController;
    private final JMeterVisualRunActions runActions;
    private final JMeterIdeUndoSupport undoSupport;
    private final UserDataHolderBase userData;
    private final Disposable editorDisposable;
    private JMeterTreeModel model;
    private JTree tree;
    private JMeterTreeActions treeActions;
    private JMeterCommandPalette commandPalette;
    private JMeterTemplateDialog templateDialog;
    private boolean modified;
    private boolean updatingCurrentNode;
    private boolean disposed;
    private boolean suppressNextFileChange;
    private int suppressedGuiDirtyEvents;

    public JMeterVisualFileEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        this.component = new JBPanel<>(new BorderLayout());
        this.elementPanel = new JMeterElementPanel(this::markGuiModified);
        this.errorPane = new JEditorPane("text/plain", "");
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.editorDisposable = Disposer.newDisposable("JMeter visual editor " + file.getPath());
        Disposer.register(project, editorDisposable);
        this.runButton = new JButton("Run Plan");
        this.runSelectedButton = new JButton("Run Thread Group");
        this.stopButton = new JButton("Stop");
        this.shutdownButton = new JButton("Shutdown");
        this.resetEnginesButton = new JButton("Reset Engines");
        this.exitEnginesButton = new JButton("Exit Engines");
        this.runStatusLabel = new JLabel("Idle");
        this.resultsWorkspace = JMeterResultsWorkspace.get(project);
        this.resultsPanel = resultsWorkspace.resultsPanel();
        this.validationAction = new JMeterValidationAction(() -> model, resultsPanel);
        this.runOptions = new JMeterRunOptions(project);
        this.threadGroupActivity = new JMeterThreadGroupActivity();
        this.runController = new JMeterRunController(
                new JMeterEditorRunListener(this::setRunStatus, resultsPanel, threadGroupActivity));
        this.runActions = new JMeterVisualRunActions(() -> model, () -> tree, this::flushGuiChanges,
                resultsWorkspace, resultsPanel, threadGroupActivity, runController, runOptions, this::setRunStatus);
        this.threadControl = new JMeterThreadControlPanel(runController);
        this.undoSupport = new JMeterIdeUndoSupport(project, file, this::restoreModel);
        this.userData = new UserDataHolderBase();
        this.toolbarState = new JMeterEditorToolbarState(runButton, runSelectedButton, stopButton,
                shutdownButton, resetEnginesButton, exitEnginesButton);
        installRunControls();

        JMeterEditorControls.wire(
                component,
                editorDisposable,
                toolbarState,
                this::save,
                this::reloadFromFile,
                runActions::runAuto,
                runActions::runSelectedThreadGroup,
                runController,
                validationAction,
                this::showCommands
        );
        JMeterEditorShortcuts.installTreeEditing(component, editorDisposable, () -> treeActions);
        JMeterFileChangeWatcher.install(file, editorDisposable, this::handleExternalFileChange, this::load);
        load();
    }

    public void reloadFromFile() {
        JMeterActionTrace.info("editor.reload.request", traceState());
        if (JMeterReloadGuard.canDiscard(project, modified)) {
            load();
        } else {
            JMeterActionTrace.info("editor.reload.cancelled", traceState());
        }
    }

    private void load() {
        try {
            JMeterActionTrace.info("editor.load.start", traceState());
            EmbeddedJMeterRuntime.ensureReady();
            JMeterPluginClasspathStore.get(project).applyToClasspath();
            model = JMeterTreeLoader.load(new File(file.getPath()));
            undoSupport.reset(model);
            suppressGuiDirtyForSettling("load");
            installModel();
            setModified(false);
            suppressGuiDirtyForSettling("load.complete");
            JMeterActionTrace.info("editor.load.success", traceState());
        } catch (Exception exception) {
            JMeterActionTrace.warn("editor.load.failed " + traceState(), exception);
            JMeterIdeNotifications.error(project, "Unable to load JMX: " + exception.getMessage());
            showLoadError(exception);
        }
    }

    private void installModel() {
        JMeterActionTrace.info("editor.model.install", traceState());
        JMeterVisualModelInstaller.Installed installed = JMeterVisualModelInstaller.install(
                project, model, component, toolbarState, elementPanel, resultsPanel, resultsWorkspace,
                threadGroupActivity, this::markTreeModified, this::suppressSelectionDirty);
        tree = installed.tree();
        treeActions = installed.treeActions();
        commandPalette = installed.commandPalette();
        templateDialog = installed.templateDialog();
        refreshResultTabs();
    }

    public void showCommands() { if (commandPalette != null) commandPalette.show(); }

    public void showTemplates() { if (templateDialog != null) templateDialog.show(); }

    private void restoreModel(JMeterTreeModel restoredModel) {
        model = restoredModel;
        installModel();
        setModified(true);
    }

    public void save() { save(true); }

    public void saveSilently() { save(false); }

    private void save(boolean notifySuccess) {
        JMeterActionTrace.info("editor.save.start", traceState());
        flushGuiChanges();
        suppressNextFileChange = true;
        if (JMeterFileSaver.save(project, file, model, elementPanel, notifySuccess)) {
            setModified(false);
            JMeterActionTrace.info("editor.save.success", traceState());
        } else {
            suppressNextFileChange = false;
            JMeterActionTrace.info("editor.save.failed", traceState());
        }
    }

    private void handleExternalFileChange() {
        if (suppressNextFileChange) {
            suppressNextFileChange = false;
            JMeterActionTrace.info("editor.file.change.ignored", traceState());
            return;
        }
        JMeterActionTrace.info("editor.file.change.external", traceState());
        if (JMeterReloadGuard.canReloadExternalChange(project, modified)) {
            load();
        } else {
            JMeterActionTrace.info("editor.file.change.reload.cancelled", traceState());
        }
    }

    public void runTest() {
        JMeterActionTrace.info("editor.run.action", traceState());
        runActions.runAuto();
    }

    public void stopTest() {
        JMeterActionTrace.info("editor.stop.action", traceState());
        runController.stop();
    }

    public void validatePlan() { validationAction.validateNow(); }

    private void installRunControls() {
        resultsWorkspace.setRunControls(this, runOptions.component(), threadControl.component(), runStatusLabel);
    }

    private void setRunStatus(String status) {
        JMeterActionTrace.info("editor.run.status", "status=\"" + status + "\" " + traceState());
        runStatusLabel.setText(status);
        boolean running = runController.isRunning();
        runButton.setEnabled(!running);
        runSelectedButton.setEnabled(!running);
        stopButton.setEnabled(running);
        shutdownButton.setEnabled(running);
    }

    private void markGuiModified() {
        if (updatingCurrentNode || suppressedGuiDirtyEvents > 0) {
            JMeterActionTrace.info("editor.gui.dirty.suppressed",
                    "updating=" + updatingCurrentNode + " suppressed=" + suppressedGuiDirtyEvents + " "
                            + traceState());
            return;
        }
        JMeterActionTrace.info("editor.gui.dirty", traceState());
        setModified(true);
    }

    private void flushGuiChanges() {
        updateCurrentJMeterNode(true);
    }

    private void updateCurrentJMeterNode() {
        updateCurrentJMeterNode(false);
    }

    private void updateCurrentJMeterNode(boolean rearmGui) {
        try {
            updatingCurrentNode = true;
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null || model == null || tree == null) {
                JMeterActionTrace.info("editor.gui.flush.skipped", traceState());
                return;
            }
            JMeterActionTrace.info("editor.gui.flush.start", traceState());
            guiPackage.updateCurrentNode();
            JMeterTreeNode currentNode = guiPackage.getCurrentNode();
            if (currentNode != null) {
                model.nodeChanged(currentNode);
                tree.repaint();
            }
            if (rearmGui) {
                suppressDelayedGuiDirty();
                elementPanel.showSelected();
            }
            JMeterActionTrace.info("editor.gui.flush.done", traceState());
        } finally {
            updatingCurrentNode = false;
        }
    }

    private void suppressDelayedGuiDirty() {
        suppressGuiDirtyForSettling("gui.flush");
    }

    private void suppressSelectionDirty() {
        suppressGuiDirtyForSettling("selection");
    }

    private void suppressGuiDirtyForSettling(String reason) {
        suppressedGuiDirtyEvents++;
        Timer timer = new Timer(250, event -> {
            if (suppressedGuiDirtyEvents > 0) {
                suppressedGuiDirtyEvents--;
            }
            JMeterActionTrace.info("editor.gui.dirty.suppression.done",
                    "reason=" + reason + " remaining=" + suppressedGuiDirtyEvents + " " + traceState());
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void setModified(boolean modified) {
        boolean oldValue = this.modified;
        this.modified = modified;
        propertyChangeSupport.firePropertyChange(FileEditor.PROP_MODIFIED, oldValue, modified);
        updateEditorTabName();
        if (oldValue != modified) {
            JMeterActionTrace.info("editor.modified",
                    "old=" + oldValue + " new=" + modified + " " + traceState());
        }
    }

    private void markTreeModified() {
        JMeterActionTrace.info("editor.tree.dirty", traceState());
        undoSupport.record(model);
        refreshResultTabs();
        setModified(true);
    }

    private void refreshResultTabs() {
        if (model == null) {
            return;
        }
        resultsWorkspace.updateNativeResultViews(resultsPanel.availableNativeResultViews(model));
    }

    private void showLoadError(Exception exception) {
        errorPane.setEditable(false);
        errorPane.setText(exception.getMessage());
        component.removeAll();
        component.add(new JBScrollPane(errorPane));
    }

    @Override public @NotNull JComponent getComponent() { return component; }
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return null; }
    @Override public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() { return editorTabName(); }
    @Override public @NotNull VirtualFile getFile() { return file; }
    @Override public void setState(@NotNull FileEditorState state) { }
    @Override public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) { return FileEditorState.INSTANCE; }
    @Override public boolean isModified() { return modified; }
    @Override public boolean isValid() { return file.isValid(); }
    @Override public void selectNotify() { installRunControls(); }

    @Override public void deselectNotify() { }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { propertyChangeSupport.addPropertyChangeListener(listener); }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { propertyChangeSupport.removePropertyChangeListener(listener); }

    @Override public @Nullable FileEditorLocation getCurrentLocation() { return null; }

    @Override public <T> @Nullable T getUserData(@NotNull Key<T> key) { return userData.getUserData(key); }

    @Override public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) { userData.putUserData(key, value); }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        JMeterActionTrace.info("editor.dispose", traceState());
        disposed = true;
        resultsWorkspace.clearRunControls(this);
        runController.exitEngines();
        Disposer.dispose(editorDisposable);
    }

    private String traceState() {
        return "file=" + JMeterActionTrace.file(file) + " node=" + JMeterActionTrace.currentNode()
                + " modified=" + modified;
    }

    private String editorTabName() {
        return modified ? "*JMeter" : "JMeter";
    }

    private void updateEditorTabName() {
        Runnable update = () -> {
            FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
            if (manager instanceof FileEditorManagerImpl) {
                EditorComposite composite = ((FileEditorManagerImpl) manager).getComposite(this);
                if (composite != null) {
                    composite.setDisplayName(this, editorTabName());
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }
}
