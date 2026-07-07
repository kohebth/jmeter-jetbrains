package com.github.duync.jmeterviewer;

import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.apache.jmeter.util.JMeterUtils;

import java.awt.datatransfer.StringSelection;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class JMeterPropertiesFileActions {
    private JMeterPropertiesFileActions() {
    }

    static void importProperties(Project project, Runnable afterImport) {
        VirtualFile file = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor("properties"),
                project,
                null
        );
        if (file == null) {
            return;
        }
        try {
            JMeterUtils.loadJMeterProperties(file.getPath());
            if (afterImport != null) {
                afterImport.run();
            }
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to import JMeter properties: " + exception.getMessage());
        }
    }

    static void exportProperties(Project project) {
        VirtualFileWrapper target = FileChooserFactory.getInstance()
                .createSaveFileDialog(new FileSaverDescriptor(
                        "Export JMeter Properties",
                        "Export current JMeter properties",
                        "properties"), project)
                .save("jmeter.properties");
        if (target == null) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(target.getFile())) {
            output.write(text().getBytes(StandardCharsets.UTF_8));
            VirtualFile virtualFile = target.getVirtualFile(true);
            if (virtualFile != null) {
                virtualFile.refresh(false, false);
            }
            JMeterIdeNotifications.info(project, "Exported JMeter properties");
        } catch (Exception exception) {
            JMeterIdeNotifications.error(project, "Unable to export JMeter properties: " + exception.getMessage());
        }
    }

    static void copyAll() {
        CopyPasteManager.getInstance().setContents(new StringSelection(text()));
    }

    static void copySelected(java.util.List<String> selected) {
        CopyPasteManager.getInstance().setContents(new StringSelection(String.join("\n", selected)));
    }

    private static String text() {
        StringBuilder builder = new StringBuilder();
        TreeSet<String> keys = new TreeSet<>();
        for (Object key : JMeterUtils.getJMeterProperties().keySet()) {
            keys.add(String.valueOf(key));
        }
        for (String key : keys) {
            builder.append(key).append('=').append(JMeterUtils.getProperty(key)).append('\n');
        }
        return builder.toString();
    }
}
