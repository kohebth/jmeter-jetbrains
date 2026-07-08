package com.github.kohebth.jmeterviewer.io;

import com.github.kohebth.jmeterviewer.editor.JMeterElementPanel;
import com.github.kohebth.jmeterviewer.ide.JMeterIdeNotifications;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.save.SaveService;

import java.io.ByteArrayOutputStream;

public final class JMeterFileSaver {
    private JMeterFileSaver() {
    }

    public static boolean save(Project project, VirtualFile file, JMeterTreeModel model, JMeterElementPanel errors) {
        return save(project, file, model, errors, true);
    }

    public static boolean save(Project project,
                        VirtualFile file,
                        JMeterTreeModel model,
                        JMeterElementPanel errors,
                        boolean notifySuccess) {
        if (model == null) {
            return false;
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SaveService.saveTree(JMeterTreeLoader.toHashTree(model), output);
            JMeterVirtualFileWriter.write(file, output.toByteArray());
            if (notifySuccess) {
                JMeterIdeNotifications.info(project, "Saved " + file.getName());
            }
            return true;
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to save JMX: " + exception.getMessage());
            errors.showError("Unable to save JMX file: " + exception.getMessage());
            return false;
        }
    }
}
