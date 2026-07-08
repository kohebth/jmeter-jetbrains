package com.github.kohebth.jmeterviewer.runtime;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class JMeterPluginPanel {
    private JMeterPluginPanel() {
    }

    public static JComponent create(Project project) {
        JMeterPluginClasspathStore store = JMeterPluginClasspathStore.get(project);
        store.applyToClasspath();
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        JLabel activeHome = new JLabel();
        JButton add = new JButton("Add JMeter Home/JAR");
        JButton remove = new JButton("Remove");
        JButton removeMissing = new JButton("Remove Missing");
        JButton refresh = new JButton("Refresh");
        add.addActionListener(event -> addPath(project, store, model, activeHome));
        remove.addActionListener(event -> removeSelected(store, list, model, activeHome));
        removeMissing.addActionListener(event -> {
            store.removeMissing();
            refresh(model, activeHome);
        });
        refresh.addActionListener(event -> refresh(model, activeHome));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(add);
        actions.add(remove);
        actions.add(removeMissing);
        actions.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(activeHome, BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        refresh(model, activeHome);
        return panel;
    }

    private static void addPath(Project project,
                                JMeterPluginClasspathStore store,
                                DefaultListModel<String> model,
                                JLabel activeHome) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, true)
                .withFileFilter(file -> file.isDirectory()
                        || file.getName().endsWith(".jar")
                        || file.getName().equals("jmeter.properties"));
        VirtualFile[] files = FileChooser.chooseFiles(descriptor, project, null);
        for (VirtualFile file : files) {
            store.add(new File(file.getPath()));
        }
        refresh(model, activeHome);
    }

    private static void removeSelected(JMeterPluginClasspathStore store,
                                       JList<String> list,
                                       DefaultListModel<String> model,
                                       JLabel activeHome) {
        for (String path : list.getSelectedValuesList()) {
            store.remove(new File(path));
        }
        refresh(model, activeHome);
    }

    private static void refresh(DefaultListModel<String> model, JLabel activeHome) {
        model.clear();
        for (File path : JMeterPluginClasspath.paths()) {
            model.addElement(path.getAbsolutePath());
        }
        File home = JMeterPluginClasspath.jmeterHome();
        activeHome.setText(home == null
                ? "Active JMeter: embedded runtime"
                : "Active JMeter: " + home.getAbsolutePath());
    }
}
