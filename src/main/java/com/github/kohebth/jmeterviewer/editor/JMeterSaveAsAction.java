package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;
import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.save.SaveService;

import javax.swing.JButton;
import java.io.FileOutputStream;
import java.util.function.Supplier;

public final class JMeterSaveAsAction {
    private final Project project;
    private final Supplier<JMeterTreeModel> modelSupplier;
    private final JButton button;

    public JMeterSaveAsAction(Project project, Supplier<JMeterTreeModel> modelSupplier) {
        this.project = project;
        this.modelSupplier = modelSupplier;
        button = new JButton("Save As");
        button.addActionListener(event -> saveAs());
    }

    public JButton button() {
        return button;
    }

    private void saveAs() {
        JMeterTreeModel model = modelSupplier.get();
        if (model == null) {
            return;
        }
        VirtualFileWrapper target = FileChooserFactory.getInstance()
                .createSaveFileDialog(new FileSaverDescriptor("Save JMeter Test Plan", "Save current tree as JMX", "jmx"), project)
                .save("test-plan.jmx");
        if (target == null) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(target.getFile())) {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                guiPackage.updateCurrentNode();
            }
            SaveService.saveTree(JMeterTreeLoader.toHashTree(model), output);
            VirtualFile file = target.getVirtualFile(true);
            if (file != null) {
                file.refresh(false, false);
            }
            JMeterIdeNotifications.info(project, "Saved copy to " + target.getFile().getName());
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to save JMX copy: " + exception.getMessage());
        }
    }
}
