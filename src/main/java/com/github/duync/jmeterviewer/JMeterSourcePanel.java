package com.github.duync.jmeterviewer;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.save.SaveService;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

final class JMeterSourcePanel {
    private final VirtualFile file;
    private final Runnable reloadVisual;
    private final Runnable updateVisualNode;
    private final Supplier<JMeterTreeModel> model;
    private final EditorTextField source;
    private final JPanel panel;

    JMeterSourcePanel(Project project,
                      VirtualFile file,
                      Runnable reloadVisual,
                      Runnable updateVisualNode,
                      Supplier<JMeterTreeModel> model,
                      Disposable parent) {
        this.file = file;
        this.reloadVisual = reloadVisual;
        this.updateVisualNode = updateVisualNode;
        this.model = model;
        source = new EditorTextField("", project, XmlFileType.INSTANCE);
        source.setOneLineMode(false);
        source.setDisposedWith(parent);
        source.addSettingsProvider(editor -> {
            editor.setHorizontalScrollbarVisible(true);
            editor.setVerticalScrollbarVisible(true);
            editor.getSettings().setLineNumbersShown(true);
        });
        panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton reload = new JButton("Reload Source");
        JButton fromVisual = new JButton("Visual to Source");
        JButton apply = new JButton("Apply Source");
        reload.addActionListener(event -> refresh());
        fromVisual.addActionListener(event -> refreshFromVisual());
        apply.addActionListener(event -> apply());
        buttons.add(reload);
        buttons.add(fromVisual);
        buttons.add(apply);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(source, BorderLayout.CENTER);
        refresh();
    }

    JComponent component() {
        return panel;
    }

    void refresh() {
        try {
            source.setText(new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
            source.setCaretPosition(0);
        } catch (Exception exception) {
            source.setText("Unable to read JMX source: " + exception.getMessage());
        }
    }

    void refreshFromVisual() {
        JMeterTreeModel currentModel = model.get();
        if (currentModel == null) {
            return;
        }
        try {
            updateVisualNode.run();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SaveService.saveTree(JMeterTreeLoader.toHashTree(currentModel), output);
            source.setText(output.toString(StandardCharsets.UTF_8.name()));
            source.setCaretPosition(0);
        } catch (Exception exception) {
            source.setText("Unable to render visual JMX source: " + exception.getMessage());
        }
    }

    private void apply() {
        try {
            JMeterVirtualFileWriter.write(file, source.getText().getBytes(StandardCharsets.UTF_8));
            file.refresh(false, false);
            reloadVisual.run();
        } catch (Exception exception) {
            source.setText("Unable to apply JMX source: " + exception.getMessage());
        }
    }
}
