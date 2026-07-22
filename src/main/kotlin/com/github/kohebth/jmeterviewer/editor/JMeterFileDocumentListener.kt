package com.github.kohebth.jmeterviewer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile

class JMeterFileDocumentListener : FileDocumentManagerListener {
    override fun fileContentReloaded(file: VirtualFile, document: Document) {
        ApplicationManager.getApplication()
            .getServiceIfCreated(JMeterDocumentSessionRegistry::class.java)
            ?.fileContentReloaded(file, document)
    }
}
