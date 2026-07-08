package com.github.kohebth.jmeterviewer.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public final class JMeterFileChangeWatcher {
    private JMeterFileChangeWatcher() {
    }

    public static void install(VirtualFile file, Disposable parent, Runnable contentsChanged, Runnable renamed) {
        file.getFileSystem().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(VirtualFileEvent event) {
                if (file.equals(event.getFile())) {
                    ApplicationManager.getApplication().invokeLater(contentsChanged);
                }
            }

            @Override
            public void propertyChanged(VirtualFilePropertyEvent event) {
                if (file.equals(event.getFile()) && VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
                    ApplicationManager.getApplication().invokeLater(renamed);
                }
            }
        }, parent);
    }
}
