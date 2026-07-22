package com.github.kohebth.jmeterviewer.editor

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import java.util.IdentityHashMap

/** Routes application-wide document callbacks to the one session owning a physical JMX file. */
@Service(Service.Level.APP)
internal class JMeterDocumentSessionRegistry {
    private val ownership = JMeterDocumentOwnershipTable()
    private val sessionsByOwner = IdentityHashMap<JMeterVisualFileEditor, JMeterEditorSession>()
    private val ownersByDocument = IdentityHashMap<Document, JMeterVisualFileEditor>()

    @Synchronized
    fun claim(editor: JMeterVisualFileEditor, onAvailable: () -> Unit): Boolean {
        val path = editor.virtualFile.toNioPath()
        ownersByDocument[editor.document]
            ?.takeIf { it !== editor }
            ?.let { existingOwner -> ownership.claim(path, existingOwner) }
        return ownership.claim(path, editor, onAvailable)
    }

    @Synchronized
    fun bind(editor: JMeterVisualFileEditor, session: JMeterEditorSession) {
        check(ownership.claim(editor.virtualFile.toNioPath(), editor)) {
            "The JMX visual editor does not own ${editor.virtualFile.path}"
        }
        sessionsByOwner[editor] = session
        ownersByDocument[editor.document] = editor
    }

    fun release(editor: JMeterVisualFileEditor) {
        synchronized(this) {
            sessionsByOwner.remove(editor)
            if (ownersByDocument[editor.document] === editor) {
                ownersByDocument.remove(editor.document)
            }
        }
        ownership.release(editor)
    }

    fun maySaveDocument(document: Document): Boolean =
        sessionFor(document)?.maySaveDocument(document) ?: true

    fun mayReloadFile(file: VirtualFile, document: Document): Boolean =
        sessionFor(document)?.mayReloadFile(file, document) ?: true

    fun fileContentReloaded(file: VirtualFile, document: Document) {
        sessionFor(document)?.fileContentReloaded(file, document)
    }

    @Synchronized
    private fun sessionFor(document: Document): JMeterEditorSession? =
        ownersByDocument[document]?.let(sessionsByOwner::get)
}
