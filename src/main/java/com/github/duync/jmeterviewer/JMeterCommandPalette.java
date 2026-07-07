package com.github.duync.jmeterviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

final class JMeterCommandPalette {
    private final Project project;
    private final java.util.List<Command> commands;

    JMeterCommandPalette(Project project,
                         JMeterTreeActions actions,
                         JMeterTreeFileActions fileActions,
                         JMeterAddElementDialog addDialog,
                         JMeterTemplateDialog templates) {
        this.project = project;
        commands = Arrays.asList(
                new Command("Add element", addDialog::show),
                new Command("Insert template", templates::show),
                new Command("Delete selected", actions::deleteSelected),
                new Command("Duplicate selected", actions::duplicateSelected),
                new Command("Duplicate selected disabled", actions::duplicateSelectedDisabled),
                new Command("Copy selected", actions::copySelected),
                new Command("Cut selected", actions::cutSelected),
                new Command("Paste into selected", actions::pasteIntoSelected),
                new Command("Import JMX into selected", fileActions::importJmx),
                new Command("Export selected node", fileActions::exportSelected),
                new Command("Export sampler and controller names", fileActions::exportNames),
                new Command("Copy sampler and controller names", fileActions::copyNames),
                new Command("Copy tree outline", fileActions::copyOutline),
                new Command("Copy code outline", fileActions::copyCodeOutline),
                new Command("Enable selected", actions::enableSelected),
                new Command("Disable selected", actions::disableSelected),
                new Command("Enable selected subtree", actions::enableSelectedTree),
                new Command("Disable selected subtree", actions::disableSelectedTree),
                new Command("Toggle enabled", actions::toggleSelectedEnabled),
                new Command("Move selected up", actions::moveSelectedUp),
                new Command("Move selected down", actions::moveSelectedDown),
                new Command("Insert Simple Controller parent", actions::insertSimpleControllerParent),
                new Command("Change parent to Simple Controller", actions::changeSelectedParentToSimpleController),
                new Command("Add think times between steps", actions::addThinkTimes),
                new Command("Expand selected", actions::expandSelected),
                new Command("Collapse selected", actions::collapseSelected),
                new Command("Expand all", actions::expandAll),
                new Command("Collapse all", actions::collapseAll)
        );
    }

    JButton button() {
        JButton button = new JButton("Commands");
        button.addActionListener(event -> show());
        return button;
    }

    void show() {
        DefaultListModel<Command> model = new DefaultListModel<>();
        commands.forEach(model::addElement);
        JList<Command> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        new ListSpeedSearch<>(list, command -> command == null ? "" : command.name);
        JTextField filter = new JTextField();
        filter.getDocument().addDocumentListener((SimpleDocumentListener) () -> filter(model, filter.getText()));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    runSelected(list);
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "run");
        list.getActionMap().put("run", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { runSelected(list); }
        });

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("JMeter Commands");
        builder.setCenterPanel(panel(filter, list));
        builder.setPreferredFocusComponent(filter);
        builder.resizable(true);
        builder.show();
    }

    private JComponent panel(JTextField filter, JList<Command> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(filter, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private void filter(DefaultListModel<Command> model, String query) {
        model.clear();
        String lower = query.trim().toLowerCase(Locale.ROOT);
        for (Command command : commands) {
            if (lower.isEmpty() || command.name.toLowerCase(Locale.ROOT).contains(lower)) {
                model.addElement(command);
            }
        }
    }

    private void runSelected(JList<Command> list) {
        Command command = list.getSelectedValue();
        if (command != null) {
            command.action.run();
        }
    }

    private static final class Command {
        private final String name;
        private final Runnable action;

        private Command(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        @Override public String toString() { return name; }
    }

    private interface SimpleDocumentListener extends DocumentListener {
        void update();

        @Override default void insertUpdate(DocumentEvent event) { update(); }
        @Override default void removeUpdate(DocumentEvent event) { update(); }
        @Override default void changedUpdate(DocumentEvent event) { update(); }
    }
}
