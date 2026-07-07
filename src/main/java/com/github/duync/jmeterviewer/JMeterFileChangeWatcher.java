package com.github.duync.jmeterviewer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

import java.util.function.BooleanSupplier;

final class JMeterFileChangeWatcher {
    private JMeterFileChangeWatcher() {
    }

    static void install(VirtualFile file, Disposable parent, BooleanSupplier modified, Runnable reload) {
        file.getFileSystem().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(VirtualFileEvent event) {
                if (file.equals(event.getFile()) && !modified.getAsBoolean()) {
                    ApplicationManager.getApplication().invokeLater(reload);
                }
            }

            @Override
            public void propertyChanged(VirtualFilePropertyEvent event) {
                if (file.equals(event.getFile()) && VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
                    ApplicationManager.getApplication().invokeLater(reload);
                }
            }
        }, parent);
    }
}
