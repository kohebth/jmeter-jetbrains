package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.testelement.property.StringProperty;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class JMeterSearchDialog {
    private final Project project;
    private final Supplier<JMeterTreeModel> modelSupplier;
    private final Consumer<JMeterTreeNode> selector;
    private final Runnable modified;
    private final JTextField query = new JTextField(24);
    private final JTextField replacement = new JTextField(24);
    private final DefaultListModel<Match> model = new DefaultListModel<>();
    private final JList<Match> list = new JList<>(model);
    private final JLabel status = new JLabel("");

    JMeterSearchDialog(Project project,
                       Supplier<JMeterTreeModel> modelSupplier,
                       Consumer<JMeterTreeNode> selector,
                       Runnable modified) {
        this.project = project;
        this.modelSupplier = modelSupplier;
        this.selector = selector;
        this.modified = modified;
    }

    void show() {
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    selectCurrent();
                }
            }
        });
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("JMeter Search");
        builder.setCenterPanel(panel());
        builder.setPreferredFocusComponent(query);
        builder.resizable(true);
        builder.show();
    }

    private JComponent panel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(form(), BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent form() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(new JLabel("Find"));
        panel.add(query);
        panel.add(new JLabel("Replace"));
        panel.add(replacement);
        panel.add(button("Search", this::search));
        panel.add(button("Select", this::selectCurrent));
        panel.add(button("Replace Selected", this::replaceSelected));
        panel.add(button("Replace All", this::replaceAll));
        query.addActionListener(event -> search());
        replacement.addActionListener(event -> replaceSelected());
        return panel;
    }

    private JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void search() {
        model.clear();
        String needle = query.getText().trim().toLowerCase(Locale.ROOT);
        JMeterTreeModel treeModel = modelSupplier.get();
        if (needle.isEmpty() || treeModel == null) {
            status.setText("");
            return;
        }
        collect((JMeterTreeNode) treeModel.getRoot(), needle);
        status.setText(model.size() + " matches");
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
            selectCurrent();
        }
    }

    private void collect(JMeterTreeNode node, String needle) {
        TestElement element = node.getTestElement();
        addIfMatch(node, "Name", element.getName(), true, value -> element.setName(value));
        addIfMatch(node, "Comment", element.getComment(), true, value -> element.setComment(value));
        addIfMatch(node, "GUI Class", element.getPropertyAsString(TestElement.GUI_CLASS), false, null);
        addIfMatch(node, "Test Class", element.getPropertyAsString(TestElement.TEST_CLASS), false, null);
        PropertyIterator properties = element.propertyIterator();
        while (properties.hasNext()) {
            org.apache.jmeter.testelement.property.JMeterProperty property = properties.next();
            boolean editable = property instanceof StringProperty
                    && !TestElement.GUI_CLASS.equals(property.getName())
                    && !TestElement.TEST_CLASS.equals(property.getName());
            addIfMatch(node, "Property " + property.getName(), property.getStringValue(), editable,
                    value -> property.setObjectValue(value));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect((JMeterTreeNode) node.getChildAt(i), needle);
        }
    }

    private void addIfMatch(JMeterTreeNode node,
                            String field,
                            String value,
                            boolean editable,
                            Consumer<String> replacer) {
        String needle = query.getText().trim().toLowerCase(Locale.ROOT);
        if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
            model.addElement(new Match(node, field, value, editable, replacer));
        }
    }

    private void selectCurrent() {
        Match match = list.getSelectedValue();
        if (match != null) {
            selector.accept(match.node);
        }
    }

    private void replaceSelected() {
        replace(list.getSelectedValuesList());
    }

    private void replaceAll() {
        List<Match> matches = Collections.list(model.elements());
        replace(matches);
    }

    private void replace(List<Match> matches) {
        String needle = query.getText();
        String value = replacement.getText();
        boolean changed = false;
        JMeterTreeModel treeModel = modelSupplier.get();
        for (Match match : matches) {
            if (match.replace(needle, value)) {
                changed = true;
                if (treeModel != null) {
                    treeModel.nodeChanged(match.node);
                }
            }
        }
        if (changed) {
            modified.run();
            search();
        }
    }

    private static final class Match {
        private final JMeterTreeNode node;
        private final String field;
        private final String value;
        private final boolean editable;
        private final Consumer<String> replacer;

        private Match(JMeterTreeNode node, String field, String value, boolean editable, Consumer<String> replacer) {
            this.node = node;
            this.field = field;
            this.value = value;
            this.editable = editable;
            this.replacer = replacer;
        }

        private boolean replace(String needle, String replacement) {
            if (!editable || replacer == null || needle == null || needle.isEmpty()) {
                return false;
            }
            String next = value.replace(needle, replacement);
            if (Objects.equals(value, next)) {
                return false;
            }
            replacer.accept(next);
            return true;
        }

        @Override public String toString() {
            return (editable ? "" : "[read-only] ") + node.getName() + " - " + field + ": " + value;
        }
    }
}
