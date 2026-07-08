package com.github.kohebth.jmeterviewer.runtime;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.openapi.project.Project;
import org.apache.jmeter.util.JMeterUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;

public final class JMeterPropertiesPanel {
    private JMeterPropertiesPanel() {
    }

    public static JComponent create(Project project) {
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        JTextField filter = new JTextField(18);
        JTextField key = new JTextField(16);
        JTextField value = new JTextField(18);
        JButton set = new JButton("Set");
        JButton remove = new JButton("Remove");
        JButton importFile = new JButton("Import");
        JButton exportFile = new JButton("Export");
        JButton copySelected = new JButton("Copy Selected");
        JButton copyAll = new JButton("Copy All");
        JButton refresh = new JButton("Refresh");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        form.add(new JLabel("Find"));
        form.add(filter);
        form.add(new JLabel("Key"));
        form.add(key);
        form.add(new JLabel("Value"));
        form.add(value);
        form.add(set);
        form.add(remove);
        form.add(importFile);
        form.add(exportFile);
        form.add(copySelected);
        form.add(copyAll);
        form.add(refresh);
        set.addActionListener(event -> setProperty(key, value, model, filter));
        remove.addActionListener(event -> removeProperty(key, model, filter));
        importFile.addActionListener(event -> JMeterPropertiesFileActions.importProperties(project,
                () -> refresh(model, filter)));
        exportFile.addActionListener(event -> JMeterPropertiesFileActions.exportProperties(project));
        copySelected.addActionListener(event -> JMeterPropertiesFileActions.copySelected(list.getSelectedValuesList()));
        copyAll.addActionListener(event -> JMeterPropertiesFileActions.copyAll());
        refresh.addActionListener(event -> refresh(model, filter));
        onTextChange(filter, () -> refresh(model, filter));
        list.addListSelectionListener(event -> select(list, key, value));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        refresh(model, filter);
        return panel;
    }

    private static void setProperty(JTextField key,
                                    JTextField value,
                                    DefaultListModel<String> model,
                                    JTextField filter) {
        if (!key.getText().trim().isEmpty()) {
            JMeterUtils.setProperty(key.getText().trim(), value.getText());
            refresh(model, filter);
        }
    }

    private static void removeProperty(JTextField key, DefaultListModel<String> model, JTextField filter) {
        JMeterUtils.getJMeterProperties().remove(key.getText().trim());
        refresh(model, filter);
    }

    private static void refresh(DefaultListModel<String> model, JTextField filter) {
        model.clear();
        String needle = filter.getText().trim().toLowerCase(Locale.ROOT);
        TreeSet<String> keys = new TreeSet<>();
        for (Object key : JMeterUtils.getJMeterProperties().keySet()) {
            keys.add(String.valueOf(key));
        }
        for (String key : keys) {
            String row = key + "=" + JMeterUtils.getProperty(key);
            if (needle.isEmpty() || row.toLowerCase(Locale.ROOT).contains(needle)) {
                model.addElement(row);
            }
        }
    }

    private static void select(JList<String> list, JTextField key, JTextField value) {
        String selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        int separator = selected.indexOf('=');
        key.setText(separator < 0 ? selected : selected.substring(0, separator));
        value.setText(separator < 0 ? "" : selected.substring(separator + 1));
    }

    private static void onTextChange(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(DocumentEvent event) { action.run(); }
        });
    }
}
