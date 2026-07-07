package com.github.duync.jmeterviewer;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.File;

final class JMeterPluginPanel {
    private JMeterPluginPanel() {
    }

    static JComponent create(Project project) {
        JMeterPluginClasspathStore store = JMeterPluginClasspathStore.get(project);
        store.applyToClasspath();
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        JButton add = new JButton("Add JAR/Folder");
        JButton remove = new JButton("Remove");
        JButton removeMissing = new JButton("Remove Missing");
        JButton refresh = new JButton("Refresh");
        add.addActionListener(event -> addPath(project, store, model));
        remove.addActionListener(event -> removeSelected(store, list, model));
        removeMissing.addActionListener(event -> {
            store.removeMissing();
            refresh(model);
        });
        refresh.addActionListener(event -> refresh(model));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(add);
        actions.add(remove);
        actions.add(removeMissing);
        actions.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        refresh(model);
        return panel;
    }

    private static void addPath(Project project, JMeterPluginClasspathStore store, DefaultListModel<String> model) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, true)
                .withFileFilter(file -> file.isDirectory() || file.getName().endsWith(".jar"));
        VirtualFile[] files = FileChooser.chooseFiles(descriptor, project, null);
        for (VirtualFile file : files) {
            store.add(new File(file.getPath()));
        }
        refresh(model);
    }

    private static void removeSelected(JMeterPluginClasspathStore store,
                                       JList<String> list,
                                       DefaultListModel<String> model) {
        for (String path : list.getSelectedValuesList()) {
            store.remove(new File(path));
        }
        refresh(model);
    }

    private static void refresh(DefaultListModel<String> model) {
        model.clear();
        for (File path : JMeterPluginClasspath.paths()) {
            model.addElement(path.getAbsolutePath());
        }
    }
}
