/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.action.CheckDirty;
import org.apache.jmeter.gui.action.Load;
import org.apache.jmeter.gui.action.Start;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.util.JSyntaxTextArea;
import org.apache.jmeter.samplers.Clearable;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.gui.ui.TextComponentUI;
import org.apiguardian.api.API;

/**
 * Small, source-level embedding boundary for JMeter's native authoring UI.
 *
 * <p>The workspace deliberately owns one normal {@link GuiPackage}, action
 * router, tree model, tree listener, and hidden {@link MainFrame}. This keeps
 * JMeter's native menus, context actions, shortcuts, and GUI component
 * lifecycle intact while allowing another Swing application to own the
 * visible window, document persistence, and undo history.</p>
 */
@API(since = "5.6.3", status = API.Status.EXPERIMENTAL)
public final class EmbeddedJMeterWorkspace implements AutoCloseable {
    private static final int EMBEDDED_ACTION_ID = 0x4a4d58;
    public static final String RUN_SELECTED_THREAD_GROUPS = "jmeter.run.selected.thread.groups";
    public static final String SHUTDOWN_TEST = "jmeter.shutdown.test";
    public static final String STOP_TEST = "jmeter.stop.test";

    private final GuiPackage guiPackage;
    private final MainFrame mainFrame;
    private final CheckDirty dirtyTracker;
    private final Start startCommand;
    private final JComponent embeddedComponent;
    private final JButton runButton;
    private final JButton shutdownButton;
    private final JButton stopButton;
    private final Timer executionStateTimer;
    private final Map<String, ResultSession> resultSessions = new LinkedHashMap<>();
    private final Deque<ResultSession> reusableResultSessions = new ArrayDeque<>();
    private JTree outlineTree;
    private JComponent outlineComponent;
    private Runnable modelChangeListener = () -> { };
    private ActionListener executionActionListener;
    private boolean closed;

    private EmbeddedJMeterWorkspace(
            GuiPackage guiPackage,
            MainFrame mainFrame,
            CheckDirty dirtyTracker,
            Start startCommand) {
        this.guiPackage = guiPackage;
        this.mainFrame = mainFrame;
        this.dirtyTracker = dirtyTracker;
        this.startCommand = startCommand;
        this.runButton = createToolbarButton(
                "Run selected Thread Group(s)",
                RUN_SELECTED_THREAD_GROUPS,
                "org/apache/jmeter/images/toolbar/22x22/arrow-right-3.png");
        this.shutdownButton = createToolbarButton(
                JMeterUtils.getResString("shutdown"),
                SHUTDOWN_TEST,
                "org/apache/jmeter/images/toolbar/22x22/process-stop-7.png");
        this.stopButton = createToolbarButton(
                JMeterUtils.getResString("stop"),
                STOP_TEST,
                "org/apache/jmeter/images/toolbar/22x22/road-sign-us-stop.png");
        this.embeddedComponent = createEmbeddedComponent();
        disablePerTextUndo(this.embeddedComponent);
        this.executionStateTimer = new Timer(250, event -> updateExecutionControls());
        this.executionStateTimer.start();
        mainFrame.getTree().addTreeSelectionListener(event -> updateExecutionControls());
        updateExecutionControls();
    }

    /**
     * Create the application-wide native JMeter authoring session.
     *
     * @return initialized workspace
     */
    public static EmbeddedJMeterWorkspace create() {
        requireEventDispatchThread();
        if (GuiPackage.getInstance() != null) {
            throw new IllegalStateException("A JMeter GUI workspace is already active");
        }

        GuiPackage guiPackage = null;
        MainFrame mainFrame = null;
        try {
            JMeterTreeModel treeModel = new JMeterTreeModel();
            JMeterTreeListener treeListener = new JMeterTreeListener(treeModel);
            ActionRouter actionRouter = ActionRouter.getInstance();
            actionRouter.populateCommandMap();
            treeListener.setActionHandler(actionRouter);
            GuiPackage.initInstance(treeListener, treeModel);
            guiPackage = GuiPackage.getInstance();
            mainFrame = new MainFrame(treeModel, treeListener, true);
            CheckDirty dirtyTracker = (CheckDirty) actionRouter.getAction(
                    ActionNames.CHECK_DIRTY,
                    CheckDirty.class);
            if (dirtyTracker == null) {
                throw new IllegalStateException("JMeter dirty tracker is unavailable");
            }
            Start startCommand = (Start) actionRouter.getAction(
                    ActionNames.ACTION_START,
                    Start.class);
            if (startCommand == null) {
                throw new IllegalStateException("JMeter start command is unavailable");
            }
            EmbeddedJMeterWorkspace workspace =
                    new EmbeddedJMeterWorkspace(guiPackage, mainFrame, dirtyTracker, startCommand);
            actionRouter.setEmbeddedPostActionListener(
                    event -> {
                        disablePerTextUndo(workspace.embeddedComponent);
                        workspace.modelChangeListener.run();
                    });

            actionRouter.doActionNow(workspace.event(ActionNames.ADD_ALL));
            mainFrame.getTree().setSelectionRow(1);
            return workspace;
        } catch (RuntimeException | Error failure) {
            ActionRouter.getInstance().setEmbeddedPostActionListener(null);
            if (guiPackage != null) {
                try {
                    if (mainFrame != null) {
                        mainFrame.closeEmbedded();
                    } else {
                        guiPackage.unregisterAsListener();
                        MainFrame partialFrame = guiPackage.getMainFrame();
                        if (partialFrame != null) {
                            partialFrame.dispose();
                        }
                    }
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                } finally {
                    GuiPackage.disposeInstance(guiPackage);
                }
            }
            throw failure;
        }
    }

    /** @return JMeter's native tree/form editor surface. */
    public JComponent getComponent() {
        ensureOpen();
        disablePerTextUndo(embeddedComponent);
        return embeddedComponent;
    }

    /**
     * Return a second native tree view that shares the authoring tree's model
     * and selection, for placement in the host's JMeter tool window.
     *
     * @return synchronized test-plan outline
     */
    public JComponent getOutlineComponent() {
        ensureOpen();
        if (outlineTree == null) {
            JTree authoringTree = mainFrame.getTree();
            outlineTree = new JTree(authoringTree.getModel());
            outlineTree.setSelectionModel(authoringTree.getSelectionModel());
            outlineTree.setCellRenderer(authoringTree.getCellRenderer());
            outlineTree.setRootVisible(authoringTree.isRootVisible());
            outlineTree.setShowsRootHandles(authoringTree.getShowsRootHandles());
            outlineComponent = new JScrollPane(outlineTree);
        }
        return outlineComponent;
    }

    /** @return native Results Tree for the opaque host session identifier. */
    public JComponent getResultsTreeComponent(String sessionId) {
        ensureOpen();
        return resultSession(sessionId).resultsTree;
    }

    /** @return native Aggregate Report for the opaque host session identifier. */
    public JComponent getAggregateReportComponent(String sessionId) {
        ensureOpen();
        return resultSession(sessionId).aggregateReport;
    }

    /**
     * Reload a historical JMX revision while preserving the selected tree
     * paths. The host restores the active temporary editor and caret after the
     * replacement forms have been mounted.
     */
    public void reloadFromHistory(InputStream inputStream, Path sourcePath)
            throws IOException, IllegalUserActionException {
        requireEventDispatchThread();
        ensureOpen();
        List<int[]> selection = captureSelectionPaths();
        installTree(inputStream, sourcePath);
        restoreSelectionPaths(selection);
    }

    /** Set a callback notified after a visible form changes the native model. */
    public void setModelChangeListener(Runnable listener) {
        ensureOpen();
        modelChangeListener = listener == null ? () -> { } : listener;
    }

    /** Set the host callback for the three embedded execution toolbar actions. */
    public void setExecutionActionListener(ActionListener listener) {
        ensureOpen();
        executionActionListener = listener;
    }

    /** @return whether the native selection contains runnable thread groups. */
    public boolean canRunSelectedThreadGroups() {
        requireEventDispatchThread();
        ensureOpen();
        return startCommand.canRunSelectedThreadGroups();
    }

    /**
     * Start only selected thread groups with transient in-memory result
     * collectors belonging to {@code sessionId}.
     *
     * @return whether the test was started
     */
    public boolean startSelectedThreadGroups(String sessionId) {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        boolean started = startCommand.startSelectedThreadGroups(
                resultSession(sessionId).collectors);
        updateExecutionControls();
        return started;
    }

    /** Request graceful shutdown of the running embedded test. */
    public void shutdownTest() {
        requireEventDispatchThread();
        ensureOpen();
        startCommand.shutdownEmbeddedTest();
        updateExecutionControls();
    }

    /** Stop the running embedded test immediately. */
    public void stopTest() {
        requireEventDispatchThread();
        ensureOpen();
        startCommand.stopEmbeddedTest();
        updateExecutionControls();
    }

    /** @return whether the selected-thread-group engine is active. */
    public boolean isTestRunning() {
        ensureOpen();
        return startCommand.isEmbeddedTestRunning();
    }

    /** Clear both native result views for a JMX session. */
    public void clearResults(String sessionId) {
        requireEventDispatchThread();
        ensureOpen();
        ResultSession session = resultSessions.get(requireSessionId(sessionId));
        if (session != null) {
            session.clear();
        }
    }

    /** Forget result components and samples associated with a closed JMX file. */
    public void discardResults(String sessionId) {
        requireEventDispatchThread();
        ensureOpen();
        ResultSession session = resultSessions.remove(requireSessionId(sessionId));
        if (session != null) {
            session.clear();
            reusableResultSessions.addLast(session);
        }
    }

    /** Set the currently visible host component used to parent native dialogs. */
    public void setDialogParent(Component parent) {
        ensureOpen();
        guiPackage.setDialogParent(parent);
    }

    /**
     * Replace the native model from an in-memory JMX document.
     * Parsing completes before the current model is mutated.
     *
     * @param inputStream current IDE document contents
     * @param sourcePath local path used by JMeter for relative resources
     * @throws IOException when the document cannot be read
     * @throws IllegalUserActionException when the parsed tree cannot be installed
     */
    public void load(InputStream inputStream, Path sourcePath)
            throws IOException, IllegalUserActionException {
        requireEventDispatchThread();
        ensureOpen();
        installTree(inputStream, sourcePath);
    }

    private void installTree(InputStream inputStream, Path sourcePath)
            throws IOException, IllegalUserActionException {
        HashTree parsedTree = SaveService.loadTree(inputStream);
        Load.insertLoadedTree(EMBEDDED_ACTION_ID, parsedTree, false);
        guiPackage.setTestPlanFile(sourcePath.toAbsolutePath().normalize().toString());
        markSaved();
    }

    /**
     * Flush the visible form and serialize the complete test plan.
     *
     * @return UTF-8-compatible JMX bytes produced by JMeter's SaveService
     * @throws IOException when serialization fails
     */
    public byte[] snapshot() throws IOException {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        HashTree tree = guiPackage.getTreeModel().getTestPlan();
        convertTreeNodes(tree);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            SaveService.saveTree(tree, output);
            return output.toByteArray();
        }
    }

    /** @return whether the native model differs from its saved baseline. */
    public boolean isDirty() {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        dirtyTracker.doAction(event(ActionNames.CHECK_DIRTY));
        return guiPackage.isDirty();
    }

    /** Record the current native model as the successfully persisted baseline. */
    public void markSaved() {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        HashTree currentTree = guiPackage.getTreeModel().getTestPlan();
        dirtyTracker.doAction(new ActionEvent(
                currentTree,
                EMBEDDED_ACTION_ID,
                ActionNames.SUB_TREE_SAVED));
    }

    @Override
    public void close() {
        requireEventDispatchThread();
        if (closed) {
            return;
        }
        closed = true;
        executionStateTimer.stop();
        if (startCommand.isEmbeddedTestRunning()) {
            startCommand.stopEmbeddedTest();
        }
        for (ResultSession session : resultSessions.values()) {
            session.clear();
        }
        resultSessions.clear();
        reusableResultSessions.clear();
        executionActionListener = null;
        modelChangeListener = () -> { };
        guiPackage.setDialogParent(null);
        ActionRouter.getInstance().setEmbeddedPostActionListener(null);
        mainFrame.closeEmbedded();
        GuiPackage.disposeInstance(guiPackage);
    }

    private JComponent createEmbeddedComponent() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(runButton);
        toolbar.addSeparator();
        toolbar.add(shutdownButton);
        toolbar.add(stopButton);

        JPanel component = new JPanel(new BorderLayout());
        component.add(toolbar, BorderLayout.NORTH);
        component.add(mainFrame.getEmbeddedComponent(), BorderLayout.CENTER);
        return component;
    }

    private JButton createToolbarButton(String tooltip, String actionCommand, String iconPath) {
        URL iconUrl = JMeterUtils.class.getClassLoader().getResource(iconPath);
        JButton button = iconUrl == null ? new JButton(tooltip) : new JButton(new ImageIcon(iconUrl));
        button.setToolTipText(tooltip);
        button.setActionCommand(actionCommand);
        button.setFocusable(false);
        button.addActionListener(event -> {
            ActionListener listener = executionActionListener;
            if (listener != null) {
                listener.actionPerformed(new ActionEvent(
                        this,
                        ActionEvent.ACTION_PERFORMED,
                        event.getActionCommand()));
            }
        });
        return button;
    }

    private void updateExecutionControls() {
        boolean running = startCommand.isEmbeddedTestRunning();
        runButton.setEnabled(!running && startCommand.canRunSelectedThreadGroups());
        shutdownButton.setEnabled(running);
        stopButton.setEnabled(running);
    }

    private void notifyIfModelChanged() {
        disablePerTextUndo(embeddedComponent);
        if (guiPackage.updateCurrentNodePreservingEditorState()) {
            modelChangeListener.run();
        }
    }

    private ResultSession resultSession(String sessionId) {
        String id = requireSessionId(sessionId);
        return resultSessions.computeIfAbsent(id, ignored -> {
            ResultSession reusable = reusableResultSessions.pollFirst();
            return reusable == null ? new ResultSession() : reusable;
        });
    }

    private static String requireSessionId(String sessionId) {
        String id = Objects.requireNonNull(sessionId, "sessionId").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("The result session identifier must not be empty");
        }
        return id;
    }

    private List<int[]> captureSelectionPaths() {
        TreePath[] selectedPaths = mainFrame.getTree().getSelectionPaths();
        List<int[]> result = new ArrayList<>();
        if (selectedPaths == null) {
            return result;
        }
        TreeModel model = mainFrame.getTree().getModel();
        for (TreePath selectedPath : selectedPaths) {
            Object[] components = selectedPath.getPath();
            int[] indices = new int[Math.max(0, components.length - 1)];
            boolean valid = true;
            for (int index = 1; index < components.length; index++) {
                indices[index - 1] = model.getIndexOfChild(components[index - 1], components[index]);
                if (indices[index - 1] < 0) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                result.add(indices);
            }
        }
        return result;
    }

    private void restoreSelectionPaths(List<int[]> selectedPaths) {
        JTree tree = mainFrame.getTree();
        TreeModel model = tree.getModel();
        List<TreePath> restored = new ArrayList<>();
        for (int[] indices : selectedPaths) {
            Object current = model.getRoot();
            List<Object> components = new ArrayList<>();
            components.add(current);
            boolean valid = true;
            for (int index : indices) {
                if (index < 0 || index >= model.getChildCount(current)) {
                    valid = false;
                    break;
                }
                current = model.getChild(current, index);
                components.add(current);
            }
            if (valid) {
                restored.add(new TreePath(components.toArray()));
            }
        }
        if (restored.isEmpty()) {
            tree.setSelectionRow(1);
        } else {
            tree.setSelectionPaths(restored.toArray(new TreePath[0]));
        }
    }

    private static void disablePerTextUndo(Component component) {
        if (component instanceof JTextComponent) {
            JTextComponent textComponent = (JTextComponent) component;
            TextComponentUI.uninstallUndo(textComponent);
            textComponent.getActionMap().remove("undo");
            textComponent.getActionMap().remove("redo");
            if (textComponent instanceof JSyntaxTextArea) {
                ((JSyntaxTextArea) textComponent).discardAllEdits();
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                disablePerTextUndo(child);
            }
        }
    }

    private static final class ResultSession {
        private static final String RESULTS_TREE_CLASS =
                "org.apache.jmeter.visualizers.ViewResultsFullVisualizer";
        private static final String AGGREGATE_REPORT_CLASS =
                "org.apache.jmeter.visualizers.StatVisualizer";

        private final JComponent resultsTree;
        private final JComponent aggregateReport;
        private final List<TestElement> collectors;

        private ResultSession() {
            resultsTree = createVisualizer(RESULTS_TREE_CLASS);
            aggregateReport = createVisualizer(AGGREGATE_REPORT_CLASS);
            disablePerTextUndo(resultsTree);
            disablePerTextUndo(aggregateReport);
            collectors = Arrays.asList(createCollector(resultsTree), createCollector(aggregateReport));
        }

        private void clear() {
            ((Clearable) resultsTree).clearData();
            ((Clearable) aggregateReport).clearData();
        }

        private static JComponent createVisualizer(String className) {
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Object visualizer = Class.forName(className, true, loader)
                        .getDeclaredConstructor()
                        .newInstance();
                if (!(visualizer instanceof JComponent) || !(visualizer instanceof Clearable)) {
                    throw new IllegalStateException(className + " is not an embeddable JMeter visualizer");
                }
                return (JComponent) visualizer;
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw visualizerFailure(className, failure);
            }
        }

        private static TestElement createCollector(JComponent visualizer) {
            try {
                Object collector = visualizer.getClass().getMethod("createTestElement").invoke(visualizer);
                if (!(collector instanceof TestElement)) {
                    throw new IllegalStateException("JMeter visualizer returned an invalid result collector");
                }
                return (TestElement) collector;
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw visualizerFailure(visualizer.getClass().getName(), failure);
            }
        }

        private static IllegalStateException visualizerFailure(String className, Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException
                    ? ((InvocationTargetException) failure).getTargetException()
                    : failure;
            return new IllegalStateException("Unable to initialize " + className, cause);
        }
    }

    private ActionEvent event(String actionName) {
        return new ActionEvent(this, EMBEDDED_ACTION_ID, actionName);
    }

    private static void convertTreeNodes(HashTree tree) {
        for (Object key : new ArrayList<>(tree.list())) {
            convertTreeNodes(tree.getTree(key));
            if (key instanceof JMeterTreeNode) {
                TestElement testElement = ((JMeterTreeNode) key).getTestElement();
                tree.replaceKey(key, testElement);
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("The embedded JMeter workspace is closed");
        }
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Embedded JMeter workspace operations must run on the Swing EDT");
        }
    }
}
