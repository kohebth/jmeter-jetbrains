package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTree;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class JMeterSearchController {
    private final Supplier<JMeterTreeModel> modelSupplier;
    private final Supplier<JTree> treeSupplier;
    private final Consumer<JMeterTreeNode> nodeSelector;
    private final Project project;
    private final Runnable modified;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final List<JMeterTreeNode> matches;
    private int searchIndex = -1;

    JMeterSearchController(Project project,
                           Supplier<JMeterTreeModel> modelSupplier,
                           Supplier<JTree> treeSupplier,
                           Consumer<JMeterTreeNode> nodeSelector,
                           Runnable modified) {
        this.project = project;
        this.modelSupplier = modelSupplier;
        this.treeSupplier = treeSupplier;
        this.nodeSelector = nodeSelector;
        this.modified = modified;
        this.searchField = new JTextField(18);
        this.statusLabel = new JLabel("");
        this.matches = new ArrayList<>();
        this.searchField.addActionListener(event -> findNext());
    }

    JTextField field() {
        return searchField;
    }

    JLabel statusLabel() {
        return statusLabel;
    }

    void findNext() {
        if (refreshMatches()) {
            searchIndex = (searchIndex + 1) % matches.size();
            nodeSelector.accept(matches.get(searchIndex));
            updateStatus();
        }
    }

    void findPrevious() {
        if (refreshMatches()) {
            searchIndex = searchIndex <= 0 ? matches.size() - 1 : searchIndex - 1;
            nodeSelector.accept(matches.get(searchIndex));
            updateStatus();
        }
    }

    void showDialog() {
        new JMeterSearchDialog(project, modelSupplier, nodeSelector, modified).show();
    }

    private boolean refreshMatches() {
        clearSearchMarks();
        matches.clear();
        JMeterTreeModel model = modelSupplier.get();
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty() || model == null) {
            searchIndex = -1;
            statusLabel.setText("");
            return false;
        }

        collectMatches((JMeterTreeNode) model.getRoot(), query);
        for (JMeterTreeNode match : matches) {
            match.setMarkedBySearch(true);
        }
        repaintTree();
        if (matches.isEmpty()) {
            searchIndex = -1;
            statusLabel.setText("0");
            return false;
        }
        if (searchIndex >= matches.size()) {
            searchIndex = -1;
        }
        return true;
    }

    private void collectMatches(JMeterTreeNode node, String query) {
        if (matches(node, query)) {
            matches.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectMatches((JMeterTreeNode) node.getChildAt(i), query);
        }
    }

    private boolean matches(JMeterTreeNode node, String query) {
        TestElement element = node.getTestElement();
        if (contains(node.getName(), query)
                || contains(element.getPropertyAsString(TestElement.GUI_CLASS), query)
                || contains(element.getPropertyAsString(TestElement.TEST_CLASS), query)
                || contains(element.getComment(), query)) {
            return true;
        }
        PropertyIterator properties = element.propertyIterator();
        while (properties.hasNext()) {
            JMeterProperty property = properties.next();
            if (contains(property.getName(), query) || contains(property.getStringValue(), query)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private void clearSearchMarks() {
        JMeterTreeModel model = modelSupplier.get();
        if (model != null) {
            clearSearchMarks((JMeterTreeNode) model.getRoot());
            repaintTree();
        }
    }

    private void clearSearchMarks(JMeterTreeNode node) {
        node.setMarkedBySearch(false);
        for (int i = 0; i < node.getChildCount(); i++) {
            clearSearchMarks((JMeterTreeNode) node.getChildAt(i));
        }
    }

    private void repaintTree() {
        JTree tree = treeSupplier.get();
        if (tree != null) {
            tree.repaint();
        }
    }

    private void updateStatus() {
        statusLabel.setText((searchIndex + 1) + "/" + matches.size());
    }
}
