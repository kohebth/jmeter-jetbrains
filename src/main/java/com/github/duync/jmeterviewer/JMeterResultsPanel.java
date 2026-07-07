package com.github.duync.jmeterviewer;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

final class JMeterResultsPanel {
    private final JMeterSampleResultTableModel sampleResultModel;
    private final JMeterAggregateStatsModel aggregateStatsModel;
    private final JTable sampleResultTable;
    private final JTable aggregateStatsTable;
    private final DefaultMutableTreeNode resultRoot;
    private final DefaultTreeModel resultTreeModel;
    private final JTree resultTree;
    private final JMeterResultDetailTabs tableDetails;
    private final JMeterResultDetailTabs treeDetails;
    private final JMeterNativeViewResultsTreePanel nativeViewResultsTree;
    private final JMeterNativeVisualizerPanel nativeTable;
    private final JMeterNativeVisualizerPanel nativeSummary;
    private final JMeterRunMonitorPanel runMonitor;
    private final JTextArea diagnosticLog;

    JMeterResultsPanel() {
        sampleResultModel = new JMeterSampleResultTableModel();
        aggregateStatsModel = new JMeterAggregateStatsModel();
        sampleResultTable = new JTable(sampleResultModel);
        sampleResultTable.setAutoCreateRowSorter(true);
        sampleResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sampleResultTable.getSelectionModel().addListSelectionListener(event -> showSelectedSampleDetails());
        aggregateStatsTable = new JTable(aggregateStatsModel);
        aggregateStatsTable.setAutoCreateRowSorter(true);

        resultRoot = new DefaultMutableTreeNode("Samples");
        resultTreeModel = new DefaultTreeModel(resultRoot);
        resultTree = new JTree(resultTreeModel);
        resultTree.setRootVisible(true);
        resultTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        resultTree.addTreeSelectionListener(event -> showSelectedTreeSampleDetails());

        tableDetails = new JMeterResultDetailTabs();
        treeDetails = new JMeterResultDetailTabs();
        nativeViewResultsTree = new JMeterNativeViewResultsTreePanel();
        nativeTable = new JMeterNativeVisualizerPanel("View Results in Table",
                "org.apache.jmeter.visualizers.TableVisualizer");
        nativeSummary = new JMeterNativeVisualizerPanel("Summary Report",
                "org.apache.jmeter.visualizers.SummaryReport");
        runMonitor = new JMeterRunMonitorPanel();
        diagnosticLog = new JTextArea(8, 80);
        diagnosticLog.setEditable(false);
    }

    JComponent monitorComponent() {
        return runMonitor.component();
    }

    JComponent tableComponent() {
        return withDetails(new JBScrollPane(sampleResultTable), tableDetails.component());
    }

    JComponent nativeTableComponent() {
        return nativeTable.component();
    }

    JComponent treeComponent() {
        return nativeViewResultsTree.component();
    }

    JComponent summaryComponent() {
        return new JBScrollPane(aggregateStatsTable);
    }

    JComponent nativeSummaryComponent() {
        return nativeSummary.component();
    }

    JComponent logComponent() {
        return new JBScrollPane(diagnosticLog);
    }

    void configureViewResultsTree(TestElement element) {
        nativeViewResultsTree.configure(element);
    }

    void configureNativeTable(TestElement element) {
        nativeTable.configure(element);
    }

    void configureNativeSummary(TestElement element) {
        nativeSummary.configure(element);
    }

    void clear() {
        clearResults();
        clearLog();
        runMonitor.reset();
    }

    void clearResults() {
        sampleResultModel.clear();
        aggregateStatsModel.clear();
        resultRoot.removeAllChildren();
        resultTreeModel.reload();
        tableDetails.clear();
        treeDetails.clear();
        nativeViewResultsTree.clear();
        nativeTable.clear();
        nativeSummary.clear();
    }

    void clearLog() {
        diagnosticLog.setText("");
    }

    void appendSample(SampleResult result) {
        sampleResultModel.add(result);
        aggregateStatsModel.add(result);
        runMonitor.sample(result);
        int lastRow = sampleResultModel.getRowCount() - 1;
        int viewRow = sampleResultTable.convertRowIndexToView(lastRow);
        sampleResultTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        sampleResultTable.scrollRectToVisible(sampleResultTable.getCellRect(viewRow, 0, true));
        nativeViewResultsTree.add(result);
        nativeTable.add(result);
        nativeSummary.add(result);
        appendTreeNode(result);
    }

    void runStarted() {
        runMonitor.start();
    }

    void runStatusChanged(String status) {
        runMonitor.status(status);
    }

    void appendDiagnostic(String message) {
        diagnosticLog.append(java.time.LocalTime.now() + " " + message + "\n");
        diagnosticLog.setCaretPosition(diagnosticLog.getDocument().getLength());
    }

    void exportSamples(File file) {
        try {
            JMeterSampleResultExporter.csv(sampleResultModel, file);
            appendDiagnostic("Exported samples to " + file.getPath());
        } catch (IOException exception) {
            appendDiagnostic("Unable to export samples: " + exception.getMessage());
        }
    }

    void exportJtlXml(File file) {
        try {
            JMeterSampleResultExporter.jtlXml(sampleResultModel, file);
            appendDiagnostic("Exported JTL XML to " + file.getPath());
        } catch (IOException exception) {
            appendDiagnostic("Unable to export JTL XML: " + exception.getMessage());
        }
    }

    void exportJtlCsv(File file) {
        try {
            JMeterSampleResultExporter.jtlCsv(sampleResultModel, file);
            appendDiagnostic("Exported JTL CSV to " + file.getPath());
        } catch (IOException exception) {
            appendDiagnostic("Unable to export JTL CSV: " + exception.getMessage());
        }
    }

    void exportLog(File file) {
        try {
            java.nio.file.Files.write(file.toPath(), diagnosticLog.getText().getBytes(StandardCharsets.UTF_8));
            appendDiagnostic("Exported log to " + file.getPath());
        } catch (IOException exception) {
            appendDiagnostic("Unable to export log: " + exception.getMessage());
        }
    }

    void selectNextFailure() {
        selectFailure(1);
    }

    void selectPreviousFailure() {
        selectFailure(-1);
    }

    private void showSelectedSampleDetails() {
        int viewRow = sampleResultTable.getSelectedRow();
        if (viewRow < 0) {
            tableDetails.clear();
            return;
        }

        int modelRow = sampleResultTable.convertRowIndexToModel(viewRow);
        SampleResult result = sampleResultModel.get(modelRow);
        if (result == null) {
            tableDetails.clear();
            return;
        }

        tableDetails.showSample(result);
    }

    private void appendTreeNode(SampleResult result) {
        DefaultMutableTreeNode node = JMeterResultTreeNode.sampleNode(result);
        resultRoot.add(node);
        resultTreeModel.nodesWereInserted(resultRoot, new int[]{resultRoot.getChildCount() - 1});
        TreePath path = new TreePath(node.getPath());
        resultTree.setSelectionPath(path);
        resultTree.scrollPathToVisible(path);
    }

    private void showSelectedTreeSampleDetails() {
        Object component = resultTree.getLastSelectedPathComponent();
        if (!(component instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object value = ((DefaultMutableTreeNode) component).getUserObject();
        if (value instanceof JMeterResultTreeNode) {
            ((JMeterResultTreeNode) value).showDetails(treeDetails);
        }
    }

    private void selectFailure(int direction) {
        selectTableFailure(direction);
        selectTreeFailure(direction);
    }

    private void selectTableFailure(int direction) {
        if (sampleResultModel.getRowCount() == 0) {
            return;
        }
        int start = sampleResultTable.getSelectedRow();
        int modelStart = start < 0 ? -1 : sampleResultTable.convertRowIndexToModel(start);
        for (int offset = 1; offset <= sampleResultModel.getRowCount(); offset++) {
            int row = Math.floorMod(modelStart + direction * offset, sampleResultModel.getRowCount());
            SampleResult result = sampleResultModel.get(row);
            if (result != null && !result.isSuccessful()) {
                int viewRow = sampleResultTable.convertRowIndexToView(row);
                sampleResultTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                sampleResultTable.scrollRectToVisible(sampleResultTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private void selectTreeFailure(int direction) {
        java.util.List<DefaultMutableTreeNode> failures = new ArrayList<>();
        collectFailures(resultRoot, failures);
        if (failures.isEmpty()) {
            return;
        }
        DefaultMutableTreeNode current = selectedTreeNode();
        int currentIndex = failures.indexOf(current);
        int nextIndex = currentIndex < 0
                ? (direction > 0 ? 0 : failures.size() - 1)
                : Math.floorMod(currentIndex + direction, failures.size());
        TreePath path = new TreePath(failures.get(nextIndex).getPath());
        resultTree.setSelectionPath(path);
        resultTree.scrollPathToVisible(path);
    }

    private DefaultMutableTreeNode selectedTreeNode() {
        TreePath path = resultTree.getSelectionPath();
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode)) {
            return null;
        }
        return (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private void collectFailures(DefaultMutableTreeNode node, java.util.List<DefaultMutableTreeNode> failures) {
        Object value = node.getUserObject();
        if (value instanceof JMeterResultTreeNode && ((JMeterResultTreeNode) value).failed()) {
            failures.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectFailures((DefaultMutableTreeNode) node.getChildAt(i), failures);
        }
    }

    private JComponent withDetails(JComponent results, JComponent details) {
        JBSplitter splitter = new JBSplitter(true, 0.62f);
        splitter.setFirstComponent(results);
        splitter.setSecondComponent(details);
        return splitter;
    }
}
