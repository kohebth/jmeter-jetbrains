package com.github.kohebth.jmeterviewer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.vfs.VirtualFile

class JMeterDocumentSynchronizationVetoer : FileDocumentSynchronizationVetoer() {
    override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean =
        registryIfCreated()?.maySaveDocument(document) ?: true

    override fun mayReloadFileContent(file: VirtualFile, document: Document): Boolean =
        registryIfCreated()?.mayReloadFile(file, document) ?: true

    private fun registryIfCreated(): JMeterDocumentSessionRegistry? =
        ApplicationManager.getApplication().getServiceIfCreated(JMeterDocumentSessionRegistry::class.java)
}
