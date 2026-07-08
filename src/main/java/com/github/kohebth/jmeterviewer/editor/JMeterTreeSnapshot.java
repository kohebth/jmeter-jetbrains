package com.github.kohebth.jmeterviewer.editor;

import com.github.kohebth.jmeterviewer.io.JMeterTreeLoader;

import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.save.SaveService;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class JMeterTreeSnapshot {
    private final byte[] bytes;

    private JMeterTreeSnapshot(byte[] bytes) {
        this.bytes = bytes;
    }

    public static JMeterTreeSnapshot capture(JMeterTreeModel model) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SaveService.saveTree(JMeterTreeLoader.toHashTree(model), output);
            return new JMeterTreeSnapshot(output.toByteArray());
        } catch (Exception exception) {
            return null;
        }
    }

    public JMeterTreeModel restore() {
        try {
            Path file = Files.createTempFile("jmeter-undo-", ".jmx");
            Files.write(file, bytes);
            JMeterTreeModel model = JMeterTreeLoader.load(file.toFile());
            Files.deleteIfExists(file);
            return model;
        } catch (Exception exception) {
            return null;
        }
    }

    public boolean sameAs(JMeterTreeSnapshot other) {
        return other != null && Arrays.equals(bytes, other.bytes);
    }
}
