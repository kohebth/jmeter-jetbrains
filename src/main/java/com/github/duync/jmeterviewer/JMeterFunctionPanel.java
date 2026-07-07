package com.github.duync.jmeterviewer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.components.JBScrollPane;
import org.apache.jmeter.functions.Function;
import org.apache.jorphan.reflect.ClassFinder;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;

final class JMeterFunctionPanel {
    private JMeterFunctionPanel() {
    }

    static JComponent create() {
        java.util.List<Entry> entries = new ArrayList<>();
        DefaultListModel<Entry> model = new DefaultListModel<>();
        JLabel status = new JLabel("Loading functions...");
        refresh(model, entries, "");
        JList<Entry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextField filter = new JTextField(18);
        JTextField arguments = new JTextField(28);
        JTextField preview = new JTextField(40);
        preview.setEditable(false);
        JButton copy = new JButton("Copy");
        JButton insert = new JButton("Insert");
        JButton refresh = new JButton("Refresh");
        list.addListSelectionListener(event -> select(list, arguments, preview));
        onTextChange(filter, () -> refresh(model, entries, filter.getText()));
        onTextChange(arguments, () -> updatePreview(list, arguments, preview));
        copy.addActionListener(event -> copySelected(preview));
        insert.addActionListener(event -> insertSelected(preview));
        refresh.addActionListener(event -> discoverAsync(entries, model, filter, status));
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        search.add(new JLabel("Find"));
        search.add(filter);
        JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        editor.add(new JLabel("Args"));
        editor.add(arguments);
        editor.add(new JLabel("Expression"));
        editor.add(preview);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(refresh);
        actions.add(copy);
        actions.add(insert);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(editor, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);
        bottom.add(status, BorderLayout.NORTH);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        discoverAsync(entries, model, filter, status);
        return panel;
    }

    private static void discoverAsync(java.util.List<Entry> entries,
                                      DefaultListModel<Entry> model,
                                      JTextField filter,
                                      JLabel status) {
        status.setText("Loading functions...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            java.util.List<Entry> discovered = discover();
            SwingUtilities.invokeLater(() -> {
                entries.clear();
                entries.addAll(discovered);
                refresh(model, entries, filter.getText());
                status.setText(model.getSize() + " functions");
            });
        });
    }

    private static void refresh(DefaultListModel<Entry> model, java.util.List<Entry> entries, String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (Entry entry : entries) {
            if (needle.isEmpty() || entry.matches(needle)) {
                model.addElement(entry);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static java.util.List<Entry> discover() {
        java.util.List<Entry> entries = new ArrayList<>();
        try {
            for (String className : ClassFinder.findClassesThatExtend(searchPaths(), new Class<?>[]{Function.class}, false)) {
                Entry entry = entryFor(className);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (Exception ignored) {
        }
        entries.sort(Comparator.comparing(entry -> entry.key));
        return entries;
    }

    private static Entry entryFor(String className) {
        try {
            Function function = (Function) JMeterPluginClasspath.loadClass(className).getDeclaredConstructor().newInstance();
            return new Entry(function.getReferenceKey(), function.getArgumentDesc());
        } catch (Exception | LinkageError error) {
            return null;
        }
    }

    private static String[] searchPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isEmpty()) {
            paths.addAll(Arrays.asList(classPath.split(File.pathSeparator)));
        }
        paths.addAll(Arrays.asList(JMeterPluginClasspath.searchPaths()));
        paths.removeIf(String::isEmpty);
        return paths.toArray(new String[0]);
    }

    private static void select(JList<Entry> list, JTextField arguments, JTextField preview) {
        Entry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        arguments.setText(String.join(",", entry.placeholders()));
        updatePreview(list, arguments, preview);
    }

    private static void updatePreview(JList<Entry> list, JTextField arguments, JTextField preview) {
        Entry entry = list.getSelectedValue();
        preview.setText(entry == null ? "" : entry.expression(arguments.getText()));
    }

    private static void copySelected(JTextField preview) {
        if (!preview.getText().isEmpty()) {
            CopyPasteManager.getInstance().setContents(new StringSelection(preview.getText()));
        }
    }

    private static void insertSelected(JTextField preview) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!preview.getText().isEmpty() && owner instanceof JTextComponent && owner != preview) {
            ((JTextComponent) owner).replaceSelection(preview.getText());
        } else {
            copySelected(preview);
        }
    }

    private static void onTextChange(JTextComponent text, Runnable action) {
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        });
    }

    private static final class Entry {
        private final String key;
        private final java.util.List<String> arguments;

        private Entry(String key, java.util.List<String> arguments) {
            this.key = key;
            this.arguments = arguments == null ? Collections.emptyList() : arguments;
        }

        private boolean matches(String needle) {
            return key.toLowerCase(Locale.ROOT).contains(needle)
                    || String.join(" ", arguments).toLowerCase(Locale.ROOT).contains(needle);
        }

        private String expression(String argumentText) {
            return "${" + key + "(" + argumentText.trim() + ")}";
        }

        private java.util.List<String> placeholders() {
            java.util.List<String> values = new ArrayList<>();
            for (String argument : arguments) {
                values.add(argument.replace(',', ' '));
            }
            return values;
        }

        @Override
        public String toString() {
            return key + " - " + String.join(", ", arguments);
        }
    }
}
