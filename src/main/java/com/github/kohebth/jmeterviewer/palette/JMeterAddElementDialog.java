package com.github.kohebth.jmeterviewer.palette;

import com.github.kohebth.jmeterviewer.editor.JMeterTreeActions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public final class JMeterAddElementDialog {
    private final Project project;
    private final JMeterTreeActions actions;

    public JMeterAddElementDialog(Project project, JMeterTreeActions actions) {
        this.project = project;
        this.actions = actions;
    }

    public JButton button() {
        JButton button = new JButton("Add Element");
        button.addActionListener(event -> show());
        return button;
    }

    public void show() {
        java.util.List<JMeterPaletteItem> items = addableItems();
        DefaultListModel<JMeterPaletteItem> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        JList<JMeterPaletteItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(model.isEmpty() ? -1 : 0);
        list.setCellRenderer(new Renderer());
        new ListSpeedSearch<>(list, item -> item == null ? "" : item.toString());

        JTextField filter = new JTextField();
        filter.getDocument().addDocumentListener((SimpleDocumentListener) () -> filter(model, items, filter.getText()));
        installRunActions(list);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("Add JMeter Element");
        builder.setCenterPanel(panel(filter, list));
        builder.setPreferredFocusComponent(filter);
        builder.resizable(true);
        builder.show();
    }

    private java.util.List<JMeterPaletteItem> addableItems() {
        JMeterTreeNode parent = actions.selectedNode();
        java.util.List<JMeterPaletteItem> items = new ArrayList<>();
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            if (JMeterAddRules.canAdd(parent, item)) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(item -> item.kind().name() + item.toString()));
        return items;
    }

    private JComponent panel(JTextField filter, JList<JMeterPaletteItem> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(filter, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private void filter(DefaultListModel<JMeterPaletteItem> model, java.util.List<JMeterPaletteItem> items, String query) {
        model.clear();
        String lower = query.trim().toLowerCase(Locale.ROOT);
        for (JMeterPaletteItem item : items) {
            String text = item.kind().name() + " " + item;
            if (lower.isEmpty() || text.toLowerCase(Locale.ROOT).contains(lower)) {
                model.addElement(item);
            }
        }
    }

    private void installRunActions(JList<JMeterPaletteItem> list) {
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    addSelected(list);
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
        list.getActionMap().put("add", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { addSelected(list); }
        });
    }

    private void addSelected(JList<JMeterPaletteItem> list) {
        JMeterPaletteItem item = list.getSelectedValue();
        if (item != null) {
            actions.addPaletteItem(item);
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof JMeterPaletteItem) {
                JMeterPaletteItem item = (JMeterPaletteItem) value;
                label.setText(item.kind().name().replace('_', ' ') + " - " + item);
            }
            return label;
        }
    }

    private interface SimpleDocumentListener extends DocumentListener {
        public void update();
        @Override default void insertUpdate(DocumentEvent event) { update(); }
        @Override default void removeUpdate(DocumentEvent event) { update(); }
        @Override default void changedUpdate(DocumentEvent event) { update(); }
    }
}
