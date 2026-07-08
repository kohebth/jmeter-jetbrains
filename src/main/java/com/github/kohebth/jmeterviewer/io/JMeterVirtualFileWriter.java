package com.github.kohebth.jmeterviewer.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public final class JMeterVirtualFileWriter {
    private JMeterVirtualFileWriter() {
    }

    public static void write(VirtualFile file, byte[] bytes) throws IOException {
        IOException[] error = new IOException[1];
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                file.setBinaryContent(bytes);
            } catch (IOException exception) {
                error[0] = exception;
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
    }
}
