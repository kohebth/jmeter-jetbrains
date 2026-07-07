package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;

final class JMeterTemplateDialog {
    private final Project project;
    private final JMeterTreeActions actions;

    JMeterTemplateDialog(Project project, JMeterTreeActions actions) {
        this.project = project;
        this.actions = actions;
    }

    JButton button() {
        JButton button = new JButton("Templates");
        button.addActionListener(event -> show());
        return button;
    }

    void show() {
        DefaultListModel<JMeterTemplate> model = new DefaultListModel<>();
        JMeterTemplate.defaults().forEach(model::addElement);
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
        builder.setCenterPanel(panel(list, description));
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

    private JComponent panel(JList<JMeterTemplate> list, JTextArea description) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.72d);
        split.setTopComponent(new JBScrollPane(list));
        split.setBottomComponent(new JBScrollPane(description));
        return split;
    }

    private void describe(JList<JMeterTemplate> list, JTextArea description) {
        JMeterTemplate template = list.getSelectedValue();
        description.setText(template == null ? "" : template.description());
        description.setCaretPosition(0);
    }
}
