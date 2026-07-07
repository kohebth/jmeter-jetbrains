package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;

final class JMeterTemplateDialog {
    private final Project project;
    private final JMeterTreeActions actions;
    private final JMeterTemplateStore store;

    JMeterTemplateDialog(Project project, JMeterTreeActions actions) {
        this.project = project;
        this.actions = actions;
        this.store = JMeterTemplateStore.get(project);
    }

    JButton button() {
        JButton button = new JButton("Templates");
        button.addActionListener(event -> show());
        return button;
    }

    void show() {
        DefaultListModel<JMeterTemplate> model = new DefaultListModel<>();
        refresh(model);
        JList<JMeterTemplate> list = new JList<>(model);
        JTextArea description = new JTextArea(6, 50);
        description.setEditable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.addListSelectionListener(event -> describe(list, description));
        new ListSpeedSearch<>(list, template -> template == null ? "" : template.toString());
        describe(list, description);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("JMeter Templates");
        builder.setCenterPanel(panel(model, list, description));
        builder.setPreferredFocusComponent(list);
        builder.setOkOperation(() -> {
            JMeterTemplate template = list.getSelectedValue();
            if (template != null) {
                actions.insertTemplate(template);
            }
            builder.getDialogWrapper().close(0);
        });
        builder.resizable(true);
        builder.show();
    }

    private JComponent panel(DefaultListModel<JMeterTemplate> model,
                             JList<JMeterTemplate> list,
                             JTextArea description) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(actions(model, list), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.72d);
        split.setTopComponent(new JBScrollPane(list));
        split.setBottomComponent(new JBScrollPane(description));
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent actions(DefaultListModel<JMeterTemplate> model, JList<JMeterTemplate> list) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton saveSelected = new JButton("Save Selected");
        JButton deleteCustom = new JButton("Delete Custom");
        saveSelected.addActionListener(event -> saveSelectedTemplate(model));
        deleteCustom.addActionListener(event -> deleteSelectedTemplate(model, list));
        panel.add(saveSelected);
        panel.add(deleteCustom);
        return panel;
    }

    private void describe(JList<JMeterTemplate> list, JTextArea description) {
        JMeterTemplate template = list.getSelectedValue();
        description.setText(template == null ? "" : template.description());
        description.setCaretPosition(0);
    }

    private void refresh(DefaultListModel<JMeterTemplate> model) {
        model.clear();
        JMeterTemplate.defaults().forEach(model::addElement);
        store.templates().forEach(model::addElement);
    }

    private void saveSelectedTemplate(DefaultListModel<JMeterTemplate> model) {
        if (actions.selectedNode() == null) {
            JMeterIdeNotifications.warn(project, "Select a JMeter element to save as a template");
            return;
        }
        String name = Messages.showInputDialog(project, "Template name", "Save JMeter Template", null);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        store.save(name.trim(), "Custom template saved from " + actions.selectedNode().getName(),
                JMeterTemplateCapture.roots(actions.selectedNode()));
        refresh(model);
    }

    private void deleteSelectedTemplate(DefaultListModel<JMeterTemplate> model, JList<JMeterTemplate> list) {
        JMeterTemplate template = list.getSelectedValue();
        if (template == null || !template.custom()) {
            JMeterIdeNotifications.warn(project, "Select a custom JMeter template to delete");
            return;
        }
        store.delete(template.name());
        refresh(model);
    }
}
