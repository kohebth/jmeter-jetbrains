package com.github.duync.jmeterviewer;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.util.JMeterUtils;

import javax.swing.*;
import java.awt.*;
import java.util.*;

final class JMeterAdvancedRunOptions {
    private final Project project;
    private final JButton button;
    private final JTextField userPropertiesFile;
    private final JTextArea jmeterProperties;
    private final JTextArea systemProperties;
    private final Set<String> appliedJMeterKeys = new HashSet<>();
    private final Set<String> appliedSystemKeys = new HashSet<>();

    JMeterAdvancedRunOptions(Project project) {
        this.project = project;
        button = new JButton("Options");
        userPropertiesFile = new JTextField(32);
        jmeterProperties = new JTextArea(8, 48);
        systemProperties = new JTextArea(8, 48);
        button.addActionListener(event -> showDialog());
    }

    JButton button() {
        return button;
    }

    void apply() {
        loadUserPropertiesFile();
        applyJMeterProperties(JMeterKeyValueOptions.parse(jmeterProperties.getText()));
        applySystemProperties(JMeterKeyValueOptions.parse(systemProperties.getText()));
    }

    JMeterRunProfile.Advanced snapshot() {
        return new JMeterRunProfile.Advanced(
                userPropertiesFile.getText(),
                jmeterProperties.getText(),
                systemProperties.getText()
        );
    }

    void restore(JMeterRunProfile.Advanced profile) {
        JMeterRunProfile.Advanced safeProfile = profile == null ? JMeterRunProfile.Advanced.empty() : profile;
        userPropertiesFile.setText(safeProfile.userPropertiesFile());
        jmeterProperties.setText(safeProfile.jmeterProperties());
        systemProperties.setText(safeProfile.systemProperties());
    }

    private void showDialog() {
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("JMeter Run Options");
        builder.setCenterPanel(createPanel());
        builder.setPreferredFocusComponent(jmeterProperties);
        builder.resizable(true);
        builder.showAndGet();
    }

    private JComponent createPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel filePanel = new JPanel(new BorderLayout(4, 0));
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> chooseUserPropertiesFile());
        filePanel.add(new JLabel("User properties file"), BorderLayout.WEST);
        filePanel.add(userPropertiesFile, BorderLayout.CENTER);
        filePanel.add(browse, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        JMeterTabOverflowSupport.apply(tabs);
        tabs.addTab("JMeter Properties", new JScrollPane(jmeterProperties));
        tabs.addTab("System Properties", new JScrollPane(systemProperties));
        panel.add(filePanel, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private void chooseUserPropertiesFile() {
        VirtualFile file = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
                project,
                null
        );
        if (file != null) {
            userPropertiesFile.setText(file.getPath());
        }
    }

    private void loadUserPropertiesFile() {
        String path = userPropertiesFile.getText().trim();
        if (!path.isEmpty()) {
            JMeterUtils.loadJMeterProperties(path);
        }
    }

    private void applyJMeterProperties(Map<String, String> values) {
        for (String key : appliedJMeterKeys) {
            if (!values.containsKey(key)) {
                JMeterUtils.getJMeterProperties().remove(key);
            }
        }
        values.forEach(JMeterUtils::setProperty);
        appliedJMeterKeys.clear();
        appliedJMeterKeys.addAll(values.keySet());
    }

    private void applySystemProperties(Map<String, String> values) {
        for (String key : appliedSystemKeys) {
            if (!values.containsKey(key)) {
                System.clearProperty(key);
            }
        }
        values.forEach(System::setProperty);
        appliedSystemKeys.clear();
        appliedSystemKeys.addAll(values.keySet());
    }
}
