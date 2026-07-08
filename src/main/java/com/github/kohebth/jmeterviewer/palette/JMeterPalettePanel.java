package com.github.kohebth.jmeterviewer.palette;

import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.util.Locale;

public final class JMeterPalettePanel {
    private JMeterPalettePanel() {
    }

    public static JComponent create() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextField filter = new JTextField();
        DefaultListModel<JMeterPaletteItem> model = new DefaultListModel<>();
        JList<JMeterPaletteItem> list = new JList<>(model);
        JButton refresh = new JButton("Refresh");
        JLabel status = new JLabel();
        refresh(model, "", status);
        discover(model, filter, status);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled(true);
        list.setTransferHandler(new JMeterPaletteTransferHandler());
        list.setCellRenderer(new Renderer());
        new ListSpeedSearch<>(list, item -> item == null ? "" : item.toString());
        filter.getDocument().addDocumentListener(new FilterListener(model, filter, status));
        refresh.addActionListener(event -> {
            JMeterPaletteCatalog.reset();
            refresh(model, filter.getText(), status);
            discover(model, filter, status);
        });
        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.add(filter, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.WEST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private static void refresh(DefaultListModel<JMeterPaletteItem> model, String query, JLabel status) {
        model.clear();
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);
        for (JMeterPaletteItem item : JMeterPaletteCatalog.items()) {
            if (lowerQuery.isEmpty() || item.toString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                model.addElement(item);
            }
        }
        status.setText(JMeterPaletteCatalog.isDiscovering()
                ? "Discovering plugin elements..."
                : model.getSize() + " elements");
    }

    private static void discover(DefaultListModel<JMeterPaletteItem> model, JTextField filter, JLabel status) {
        status.setText("Discovering plugin elements...");
        JMeterPaletteCatalog.discoverAsync(() -> refresh(model, filter.getText(), status));
    }

    private static final class FilterListener implements DocumentListener {
        private final DefaultListModel<JMeterPaletteItem> model;
        private final JTextField filter;
        private final JLabel status;

        private FilterListener(DefaultListModel<JMeterPaletteItem> model, JTextField filter, JLabel status) {
            this.model = model;
            this.filter = filter;
            this.status = status;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            refresh(model, filter.getText(), status);
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            refresh(model, filter.getText(), status);
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            refresh(model, filter.getText(), status);
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof JMeterPaletteItem) {
                JMeterPaletteItem item = (JMeterPaletteItem) value;
                label.setText(item.kind().name().replace('_', ' ') + " - " + item);
            }
            return label;
        }
    }
}
