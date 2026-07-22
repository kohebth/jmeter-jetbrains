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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.action.CheckDirty;
import org.apache.jmeter.gui.action.Load;
import org.apache.jmeter.gui.action.RawTextSearcher;
import org.apache.jmeter.gui.action.RegexpSearcher;
import org.apache.jmeter.gui.action.Searcher;
import org.apache.jmeter.gui.action.Start;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.util.JSyntaxTextArea;
import org.apache.jmeter.samplers.Clearable;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.ResultCollectorHelper;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.Visualizer;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.gui.ui.TextComponentUI;
import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedJMeterWorkspace.class);
    private static final int EMBEDDED_ACTION_ID = 0x4a4d58;
    private static final String JSR223_SCRIPT_LANGUAGE = "scriptLanguage";
    private static final String JSR223_CACHE_KEY = "cacheKey";
    private static final String JSR223_SCRIPT = "script";
    public static final String RUN_SELECTED_THREAD_GROUPS = "jmeter.run.selected.thread.groups";
    public static final String SHUTDOWN_TEST = "jmeter.shutdown.test";
    public static final String STOP_TEST = "jmeter.stop.test";

    private final GuiPackage guiPackage;
    private final MainFrame mainFrame;
    private final CheckDirty dirtyTracker;
    private final Start startCommand;
    private final JComponent embeddedComponent;
    private final Map<String, ResultSession> resultSessions = new LinkedHashMap<>();
    private final Deque<ResultSession> reusableResultSessions = new ArrayDeque<>();
    private final JComponent outlineComponent;
    private Runnable modelChangeListener = () -> { };
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
        this.embeddedComponent = mainFrame.getEmbeddedComponent();
        this.outlineComponent = mainFrame.getEmbeddedTreeComponent();
        disablePerTextUndo(this.embeddedComponent);
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
                        Object currentGui = workspace.guiPackage.getCurrentGui();
                        if (currentGui instanceof JComponent) {
                            disablePerTextUndo((JComponent) currentGui);
                        }
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
        return embeddedComponent;
    }

    /**
     * Return JMeter's authoritative native tree for placement in the host's tool window.
     *
     * @return native test-plan tree
     */
    public JComponent getOutlineComponent() {
        ensureOpen();
        return outlineComponent;
    }

    /**
     * Search the current test plan using JMeter's native searchable-token semantics.
     * Each result map contains only bootstrap/JDK values so an isolated host classloader
     * can consume it safely.
     *
     * @return ordered result metadata containing path, name, type, breadcrumb, and replaceability
     */
    public List<Map<String, Object>> searchTestPlan(
            String query,
            boolean caseSensitive,
            boolean regexp) throws Exception {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        resetSearchMarks();
        if (query == null || query.isEmpty()) {
            mainFrame.getTree().repaint();
            return Collections.emptyList();
        }

        Searcher searcher = regexp
                ? new RegexpSearcher(caseSensitive, query)
                : new RawTextSearcher(caseSensitive, query);
        // Fail once with a useful error instead of recompiling an invalid expression per node.
        if (regexp) {
            Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (JMeterTreeNode node : guiPackage.getTreeModel().getNodesOfType(Searchable.class)) {
            Object element = node.getUserObject();
            if (!(element instanceof Searchable)) {
                continue;
            }
            try {
                if (!searcher.search(((Searchable) element).getSearchableTokens())) {
                    continue;
                }
            } catch (Exception failure) {
                LOG.error("Unable to search JMeter element {}", node.getName(), failure);
                continue;
            }
            node.setMarkedBySearch(true);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", new TreePath(node.getPath()));
            result.put("name", node.getName());
            result.put("type", searchableType(node));
            result.put("breadcrumb", breadcrumb(node));
            result.put("replaceable", element instanceof Replaceable);
            results.add(result);
        }
        mainFrame.getTree().repaint();
        return Collections.unmodifiableList(results);
    }

    /** Clear all native search marks from the current test plan. */
    public void resetSearch() {
        requireEventDispatchThread();
        ensureOpen();
        resetSearchMarks();
        mainFrame.getTree().repaint();
    }

    /** Select, expand, and reveal one result in the authoritative native tree. */
    public void selectSearchResult(TreePath path) {
        requireEventDispatchThread();
        ensureOpen();
        Objects.requireNonNull(path, "path");
        JTree tree = mainFrame.getTree();
        tree.setSelectionPath(path);
        tree.expandPath(path.getParentPath());
        tree.scrollPathToVisible(path);
    }

    /**
     * Replace matching content only in JMeter elements implementing {@link Replaceable}.
     * Empty replacement text is intentionally valid.
     *
     * @return occurrences replaced, supported nodes visited, and unsupported nodes skipped
     */
    public int[] replaceSearchResults(
            TreePath[] paths,
            String query,
            String replacement,
            boolean caseSensitive,
            boolean regexp) throws Exception {
        requireEventDispatchThread();
        ensureOpen();
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(replacement, "replacement");
        if (query.isEmpty()) {
            return new int[] { 0, 0, 0 };
        }

        notifyIfModelChanged();
        String expression = regexp ? query : Pattern.quote(query);
        if (regexp) {
            Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        }

        int occurrences = 0;
        int supportedNodes = 0;
        int skippedNodes = 0;
        boolean currentNodeChanged = false;
        JMeterTreeNode currentNode = guiPackage.getCurrentNode();
        for (TreePath path : paths) {
            if (path == null || !(path.getLastPathComponent() instanceof JMeterTreeNode)) {
                skippedNodes++;
                continue;
            }
            JMeterTreeNode node = (JMeterTreeNode) path.getLastPathComponent();
            Object element = node.getUserObject();
            if (!(element instanceof Replaceable)) {
                skippedNodes++;
                continue;
            }
            supportedNodes++;
            int replaced;
            try {
                replaced = ((Replaceable) element).replace(expression, replacement, caseSensitive);
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to replace content in " + node.getName(), failure);
            }
            if (replaced > 0) {
                occurrences += replaced;
                guiPackage.getTreeModel().nodeChanged(node);
                currentNodeChanged |= node == currentNode;
            }
        }
        if (currentNodeChanged) {
            guiPackage.refreshCurrentGui();
        }
        if (occurrences > 0) {
            modelChangeListener.run();
        }
        mainFrame.getTree().repaint();
        return new int[] { occurrences, supportedNodes, skippedNodes };
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

    /** Set the host callback for native selected-thread-group context actions. */
    public void setExecutionActionListener(ActionListener listener) {
        ensureOpen();
        startCommand.setSelectedThreadGroupRunListener(listener);
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
        return started;
    }

    /** Request graceful shutdown of the running embedded test. */
    public void shutdownTest() {
        requireEventDispatchThread();
        ensureOpen();
        startCommand.shutdownEmbeddedTest();
    }

    /** Stop the running embedded test immediately. */
    public void stopTest() {
        requireEventDispatchThread();
        ensureOpen();
        startCommand.stopEmbeddedTest();
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
            if (clearResultSession(session, "discarding a result session")) {
                reusableResultSessions.addLast(session);
            }
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

    /**
     * Serialize only the selected thread groups and add a transient listener
     * that streams XML sample fragments to the IDE result bridge.
     *
     * @param actionCommand native JMeter run command
     * @param port loopback bridge port
     * @param token per-run authentication token
     * @param journalPath fallback journal used if the bridge is unavailable
     * @return restricted JMX, or {@code null} if no thread group is selected
     * @throws IOException when serialization fails
     */
    public byte[] snapshotSelectedThreadGroups(
            String actionCommand,
            int port,
            String token,
            Path journalPath) throws IOException {
        requireEventDispatchThread();
        ensureOpen();
        notifyIfModelChanged();
        TestElement bridgeListener = createResultBridgeListener(port, token, journalPath);
        HashTree tree = startCommand.createSelectedThreadGroupsTree(
                actionCommand,
                Collections.singletonList(bridgeListener));
        if (tree == null) {
            return null;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            SaveService.saveTree(tree, output);
            return output.toByteArray();
        }
    }

    /** Add one externally executed sample to both native result views. */
    public void appendSampleResult(String sessionId, byte[] xmlFragment) throws IOException {
        requireEventDispatchThread();
        ensureOpen();
        resultSession(sessionId).append(xmlFragment);
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
        cleanup("stopping the embedded test", () -> {
            if (startCommand.isEmbeddedTestRunning()) {
                startCommand.stopEmbeddedTest();
            }
        });
        for (ResultSession session : resultSessions.values()) {
            clearResultSession(session, "closing a result session");
        }
        resultSessions.clear();
        reusableResultSessions.clear();
        cleanup("detaching the selected-thread-group listener",
                () -> startCommand.setSelectedThreadGroupRunListener(null));
        modelChangeListener = () -> { };
        cleanup("clearing the dialog parent", () -> guiPackage.setDialogParent(null));
        cleanup("detaching the embedded action listener",
                () -> ActionRouter.getInstance().setEmbeddedPostActionListener(null));
        cleanup("closing the embedded frame", mainFrame::closeEmbedded);
        cleanup("disposing the embedded GUI package", () -> GuiPackage.disposeInstance(guiPackage));
    }

    private static boolean clearResultSession(ResultSession session, String operation) {
        try {
            session.clear();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("Unable to clear JMeter result components while {}", operation, failure);
            return false;
        }
    }

    private static void cleanup(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("Unable to finish JMeter cleanup while {}", operation, failure);
        }
    }

    private void notifyIfModelChanged() {
        if (guiPackage.updateCurrentNodePreservingEditorState()) {
            modelChangeListener.run();
        }
    }

    private void resetSearchMarks() {
        JMeterTreeNode root = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        Enumeration<?> nodes = root.preorderEnumeration();
        List<JMeterTreeNode> allNodes = new ArrayList<>();
        while (nodes.hasMoreElements()) {
            Object candidate = nodes.nextElement();
            if (candidate instanceof JMeterTreeNode) {
                JMeterTreeNode node = (JMeterTreeNode) candidate;
                allNodes.add(node);
                node.setMarkedBySearch(false);
            }
        }
        // JMeter's setMarkedBySearch also tags ancestors, even while clearing a mark.
        // Clear the derived ancestor flag only after all direct marks are gone.
        for (JMeterTreeNode node : allNodes) {
            node.setChildrenNodesHaveMatched(false);
        }
    }

    private static String searchableType(JMeterTreeNode node) {
        try {
            return node.getStaticLabel();
        } catch (RuntimeException | LinkageError ignored) {
            return node.getTestElement().getClass().getSimpleName();
        }
    }

    private static String breadcrumb(JMeterTreeNode node) {
        Object[] path = node.getPath();
        List<String> names = new ArrayList<>();
        for (int index = 1; index < path.length; index++) {
            Object candidate = path[index];
            if (candidate instanceof JMeterTreeNode) {
                names.add(((JMeterTreeNode) candidate).getName());
            }
        }
        return String.join(" › ", names);
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
            if (Boolean.TRUE.equals(textComponent.getClientProperty(TEXT_UNDO_DISABLED_PROPERTY))) {
                return;
            }
            textComponent.putClientProperty(TEXT_UNDO_DISABLED_PROPERTY, Boolean.TRUE);
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

    private static TestElement createResultBridgeListener(
            int port,
            String token,
            Path journalPath) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid result bridge port: " + port);
        }
        String requiredToken = Objects.requireNonNull(token, "token").trim();
        if (requiredToken.isEmpty()) {
            throw new IllegalArgumentException("The result bridge token must not be empty");
        }
        String encodedJournal = Base64.getEncoder().encodeToString(
                Objects.requireNonNull(journalPath, "journalPath")
                        .toAbsolutePath()
                        .normalize()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8));
        String script = resultBridgeScript(port, requiredToken, encodedJournal);
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Object listener = Class.forName(
                    "org.apache.jmeter.visualizers.JSR223Listener",
                    true,
                    loader).getDeclaredConstructor().newInstance();
            if (!(listener instanceof TestElement)) {
                throw new IllegalStateException("JMeter returned an invalid JSR223 listener");
            }
            TestElement testElement = (TestElement) listener;
            testElement.setName("JetBrains Live Results");
            testElement.setEnabled(true);
            testElement.setProperty(TestElement.GUI_CLASS, TestBeanGUI.class.getName());
            testElement.setProperty(TestElement.TEST_CLASS, listener.getClass().getName());
            testElement.setProperty(JSR223_SCRIPT_LANGUAGE, "groovy");
            testElement.setProperty(JSR223_CACHE_KEY, "jetbrains-results-" + requiredToken);
            testElement.setProperty(JSR223_SCRIPT, script);
            return testElement;
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Unable to create the live result bridge listener", failure);
        }
    }

    private static String resultBridgeScript(int port, String token, String encodedJournal) {
        return "def sampleWriter = new java.io.StringWriter()\n"
                + "def previousSaveConfig = sampleResult.getSaveConfig()\n"
                + "sampleResult.setSaveConfig(new org.apache.jmeter.samplers.SampleSaveConfiguration(true))\n"
                + "try {\n"
                + "  org.apache.jmeter.save.SaveService.saveSampleResult(sampleEvent, sampleWriter)\n"
                + "} finally {\n"
                + "  sampleResult.setSaveConfig(previousSaveConfig)\n"
                + "}\n"
                + "byte[] payload = sampleWriter.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)\n"
                + "String sampleId = java.util.UUID.randomUUID().toString()\n"
                + "byte[] idBytes = sampleId.getBytes(java.nio.charset.StandardCharsets.UTF_8)\n"
                + "def journalPath = java.nio.file.Paths.get(new String(java.util.Base64.decoder.decode('"
                + encodedJournal
                + "'), java.nio.charset.StandardCharsets.UTF_8))\n"
                + "synchronized (('jmeter-jetbrains-" + token + "').intern()) {\n"
                + "  def journal = new java.io.DataOutputStream(new java.io.BufferedOutputStream("
                + "java.nio.file.Files.newOutputStream(journalPath, java.nio.file.StandardOpenOption.CREATE, "
                + "java.nio.file.StandardOpenOption.APPEND)))\n"
                + "  try { journal.writeInt(idBytes.length); journal.write(idBytes); "
                + "journal.writeInt(payload.length); journal.write(payload) } finally { journal.close() }\n"
                + "}\n"
                + "try {\n"
                + "  def socket = new java.net.Socket()\n"
                + "  socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.loopbackAddress, "
                + port
                + "), 750)\n"
                + "  def output = new java.io.DataOutputStream(new java.io.BufferedOutputStream(socket.outputStream))\n"
                + "  try { output.writeUTF('" + token + "'); output.writeInt(idBytes.length); "
                + "output.write(idBytes); output.writeInt(payload.length); output.write(payload); output.flush() } "
                + "finally { output.close(); socket.close() }\n"
                + "} catch (Exception ignored) { }\n";
    }

    private static final class ResultSession {
        private static final String RESULTS_TREE_CLASS =
                "org.apache.jmeter.visualizers.ViewResultsFullVisualizer";
        private static final String AGGREGATE_REPORT_CLASS =
                "org.apache.jmeter.visualizers.StatVisualizer";

        private final JComponent resultsTree;
        private final JComponent aggregateReport;
        private final Visualizer resultsVisualizer;
        private final Visualizer aggregateVisualizer;
        private final ResultCollector resultCollector;
        private final List<TestElement> collectors;

        private ResultSession() {
            resultsTree = createVisualizer(RESULTS_TREE_CLASS);
            aggregateReport = createVisualizer(AGGREGATE_REPORT_CLASS);
            resultsVisualizer = (Visualizer) resultsTree;
            aggregateVisualizer = (Visualizer) aggregateReport;
            disablePerTextUndo(resultsTree);
            disablePerTextUndo(aggregateReport);
            resultCollector = createCollector(resultsTree);
            collectors = Arrays.asList(resultCollector, createCollector(aggregateReport));
        }

        private void clear() {
            ((Clearable) resultsTree).clearData();
            ((Clearable) aggregateReport).clearData();
        }

        private void append(byte[] xmlFragment) throws IOException {
            Objects.requireNonNull(xmlFragment, "xmlFragment");
            byte[] prefix = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<testResults version=\"1.2\">").getBytes(StandardCharsets.UTF_8);
            byte[] suffix = "</testResults>".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream document = new ByteArrayOutputStream(
                    prefix.length + xmlFragment.length + suffix.length);
            document.write(prefix);
            document.write(xmlFragment);
            document.write(suffix);
            Visualizer composite = new Visualizer() {
                @Override
                public void add(SampleResult sample) {
                    resultsVisualizer.add(sample);
                    aggregateVisualizer.add(sample);
                }

                @Override
                public boolean isStats() {
                    return false;
                }
            };
            SaveService.loadTestResults(
                    new ByteArrayInputStream(document.toByteArray()),
                    new ResultCollectorHelper(resultCollector, composite));
        }

        private static JComponent createVisualizer(String className) {
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Object visualizer = Class.forName(className, true, loader)
                        .getDeclaredConstructor()
                        .newInstance();
                if (!(visualizer instanceof JComponent)
                        || !(visualizer instanceof Clearable)
                        || !(visualizer instanceof Visualizer)) {
                    throw new IllegalStateException(className + " is not an embeddable JMeter visualizer");
                }
                return (JComponent) visualizer;
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw visualizerFailure(className, failure);
            }
        }

        private static ResultCollector createCollector(JComponent visualizer) {
            try {
                Object collector = visualizer.getClass().getMethod("createTestElement").invoke(visualizer);
                if (!(collector instanceof ResultCollector)) {
                    throw new IllegalStateException("JMeter visualizer returned an invalid result collector");
                }
                return (ResultCollector) collector;
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

    private static final String TEXT_UNDO_DISABLED_PROPERTY =
            "jmeter.intellij.text.undo.disabled";
}
