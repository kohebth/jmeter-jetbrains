package com.github.kohebth.jmeterviewer.editor

import com.github.kohebth.jmeterviewer.runtime.JMeterConfigurationException
import com.github.kohebth.jmeterviewer.runtime.JMeterRuntimeService
import com.github.kohebth.jmeterviewer.runtime.JMeterWorkspace
import com.github.kohebth.jmeterviewer.toolwindow.JMeterToolWindowController
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.event.ActionListener
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.Timer

@Service(Service.Level.APP)
class JMeterWorkspaceService : Disposable {
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private var workspace: JMeterWorkspace? = null
    private var loadedFile: VirtualFile? = null
    private var syncState: DocumentSyncState? = null
    private var activeEditor: JMeterVisualFileEditor? = null
    private val resultSessionIds = IdentityHashMap<VirtualFile, String>()
    private var textAreaAdapters: JMeterTextAreaAdapters? = null
    private var fieldEditGroup: Any? = null
    private var runningFile: VirtualFile? = null
    private var runningEditor: JMeterVisualFileEditor? = null
    private var wasTestRunning = false
    private val fieldSnapshotTimer = Timer(FIELD_SNAPSHOT_DELAY_MS) { flushPendingFieldEdit() }
    private var switchBlocked = false
    private var internalDocumentSave = false
    private var reloadAfterExternalChange = false
    private var disposed = false

    init {
        fieldSnapshotTimer.isRepeats = false
    }

    fun activate(editor: JMeterVisualFileEditor) = onEdt {
        if (disposed || editor.project.isDisposed || !editor.virtualFile.isValid) {
            return@onEdt
        }

        if (
            workspace?.isTestRunning == true &&
            runningFile != null &&
            runningFile != editor.virtualFile
        ) {
            editor.showSwitchBlocked("The running JMeter test plan is pinned until it stops.")
            runningEditor?.requestReselect()
            return@onEdt
        }

        val previous = activeEditor
        if (previous != null && previous !== editor) {
            if (switchBlocked || !persist(previous, saveToDisk = true)) {
                switchBlocked = true
                editor.showSwitchBlocked()
                previous.requestReselect()
                return@onEdt
            }
            detach(previous)
            activeEditor = null
        }

        switchBlocked = false
        val nativeWorkspace = try {
            ensureWorkspace()
        } catch (failure: JMeterConfigurationException) {
            editor.showLoadError(readableMessage(failure), offerConfiguration = true)
            return@onEdt
        } catch (failure: Exception) {
            editor.showLoadError(readableMessage(failure))
            return@onEdt
        } catch (failure: LinkageError) {
            editor.showLoadError(readableMessage(failure))
            return@onEdt
        }
        val documentFingerprint = DocumentFingerprint.of(editor.document.immutableCharSequence)

        try {
            when {
                loadedFile != editor.virtualFile -> loadDocument(editor, nativeWorkspace)
                syncState == null -> loadDocument(editor, nativeWorkspace)
                documentFingerprint != syncState!!.loadedFingerprint && nativeIsDirty(nativeWorkspace) -> {
                    when (askConflict(editor)) {
                        ConflictChoice.RELOAD_DOCUMENT -> loadDocument(editor, nativeWorkspace)
                        ConflictChoice.OVERWRITE_WITH_VISUAL -> snapshotIntoDocument(editor, nativeWorkspace)
                        ConflictChoice.CANCEL -> {
                            editor.showLoadError("The visual and text editors both contain unsaved changes.")
                            return@onEdt
                        }
                    }
                }
                documentFingerprint != syncState!!.loadedFingerprint -> loadDocument(editor, nativeWorkspace)
            }
        } catch (failure: Exception) {
            editor.showLoadError(readableMessage(failure))
            return@onEdt
        } catch (failure: LinkageError) {
            editor.showLoadError(readableMessage(failure))
            return@onEdt
        }

        try {
            activeEditor = editor
            attach(editor, nativeWorkspace.component)
            nativeWorkspace.setDialogParent(editor.component)
            bindWorkspaceSurfaces(editor, nativeWorkspace)
            refreshExecutionState(nativeWorkspace)
        } catch (failure: Exception) {
            failSurfaceBinding(editor, nativeWorkspace, failure)
        } catch (failure: LinkageError) {
            failSurfaceBinding(editor, nativeWorkspace, failure)
        }
    }

    fun retry(editor: JMeterVisualFileEditor) {
        activate(editor)
    }

    fun deactivate(editor: JMeterVisualFileEditor) = onEdt {
        if (activeEditor !== editor) {
            return@onEdt
        }

        workspace?.let(::refreshExecutionState)
        if (workspace?.isTestRunning == true && runningFile == editor.virtualFile) {
            editor.showSwitchBlocked("The running JMeter test plan is pinned until it stops.")
            editor.requestReselect()
            return@onEdt
        }

        if (!persist(editor, saveToDisk = true)) {
            switchBlocked = true
            editor.requestReselect()
            return@onEdt
        }

        detach(editor)
        activeEditor = null
        workspace?.setDialogParent(null)
        switchBlocked = false
    }

    fun unregister(editor: JMeterVisualFileEditor) = onEdt {
        val nativeWorkspace = workspace
        val wasActive = activeEditor === editor
        if (nativeWorkspace != null) {
            refreshExecutionState(nativeWorkspace)
        }
        if (runningFile == editor.virtualFile && nativeWorkspace?.isTestRunning == true) {
            nativeWorkspace.stopTest()
            clearRunningState()
        }

        if (activeEditor === editor) {
            if (!editor.project.isDisposed && editor.virtualFile.isValid) {
                persist(editor, saveToDisk = true)
            }
            detach(editor)
            activeEditor = null
            workspace?.setDialogParent(null)
            switchBlocked = false
        }

        resultSessionIds.remove(editor.virtualFile)?.let { sessionId ->
            nativeWorkspace?.discardResults(sessionId)
        }
        if (wasActive && !editor.project.isDisposed) {
            editor.project.getService(JMeterToolWindowController::class.java).showEmpty()
        }
    }

    fun isModified(editor: JMeterVisualFileEditor): Boolean = onEdt {
        val documentUnsaved = fileDocumentManager.isDocumentUnsaved(editor.document)
        if (activeEditor !== editor) {
            return@onEdt documentUnsaved
        }

        val nativeWorkspace = workspace
        if (nativeWorkspace != null) {
            refreshExecutionState(nativeWorkspace)
        }
        documentUnsaved || (nativeWorkspace?.let(::nativeIsDirty) ?: false)
    }

    /** Called by IntelliJ before explicit Save, Save All, and autosave. */
    fun maySaveDocument(document: com.intellij.openapi.editor.Document): Boolean = onEdt {
        if (internalDocumentSave) {
            return@onEdt true
        }
        val editor = activeEditor
        if (editor == null || editor.document !== document) {
            return@onEdt true
        }

        val synchronized = synchronizeDocument(editor)
        if (synchronized) {
            scheduleSavedBaseline(editor)
        } else {
            switchBlocked = true
            editor.requestReselect()
        }
        synchronized
    }

    /** Called before IntelliJ replaces a Document after an external file change. */
    fun mayReloadFile(file: VirtualFile, document: com.intellij.openapi.editor.Document): Boolean = onEdt {
        val editor = activeEditor
        val nativeWorkspace = workspace
        if (editor == null || nativeWorkspace == null || editor.virtualFile != file || editor.document !== document) {
            return@onEdt true
        }
        try {
            if (!nativeIsDirty(nativeWorkspace)) {
                reloadAfterExternalChange = true
                return@onEdt true
            }

            when (askConflict(editor)) {
                ConflictChoice.RELOAD_DOCUMENT -> {
                    reloadAfterExternalChange = true
                    true
                }
                ConflictChoice.OVERWRITE_WITH_VISUAL -> {
                    if (snapshotIntoDocument(editor, nativeWorkspace)) {
                        ApplicationManager.getApplication().invokeLater {
                            if (activeEditor === editor) {
                                persist(editor, saveToDisk = true)
                            }
                        }
                    }
                    false
                }
                ConflictChoice.CANCEL -> false
            }
        } catch (failure: Exception) {
            editor.showLoadError(readableMessage(failure))
            false
        } catch (failure: LinkageError) {
            editor.showLoadError(readableMessage(failure))
            false
        }
    }

    /** Called after IntelliJ accepted external file contents into the Document. */
    fun fileContentReloaded(file: VirtualFile, document: com.intellij.openapi.editor.Document) = onEdt {
        if (!reloadAfterExternalChange) {
            return@onEdt
        }
        reloadAfterExternalChange = false
        val editor = activeEditor
        val nativeWorkspace = workspace
        if (editor == null || nativeWorkspace == null || editor.virtualFile != file || editor.document !== document) {
            return@onEdt
        }

        try {
            loadDocument(editor, nativeWorkspace)
            attach(editor, nativeWorkspace.component)
        } catch (failure: Exception) {
            editor.showLoadError(readableMessage(failure))
        } catch (failure: LinkageError) {
            editor.showLoadError(readableMessage(failure))
        }
        editor.refreshModifiedState()
    }

    private fun ensureWorkspace(): JMeterWorkspace {
        workspace?.let { return it }
        return ApplicationManager.getApplication()
            .getService(JMeterRuntimeService::class.java)
            .createWorkspace()
            .also { nativeWorkspace ->
                nativeWorkspace.setModelChangeListener(Runnable {
                    if (!disposed) {
                        fieldSnapshotTimer.restart()
                    }
                })
                nativeWorkspace.setExecutionActionListener(ActionListener { event ->
                    handleExecutionAction(event.actionCommand)
                })
                workspace = nativeWorkspace
            }
    }

    private fun loadDocument(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
    ) {
        val text = editor.document.immutableCharSequence.toString()
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        nativeWorkspace.load(
            ByteArrayInputStream(bytes),
            editor.virtualFile.toNioPath(),
        )
        val fingerprint = DocumentFingerprint.of(text)
        loadedFile = editor.virtualFile
        syncState = DocumentSyncState(fingerprint)
        nativeMarkSaved(nativeWorkspace)
    }

    private fun persist(editor: JMeterVisualFileEditor, saveToDisk: Boolean): Boolean {
        if (!synchronizeDocument(editor)) {
            return false
        }
        if (!saveToDisk) {
            return true
        }

        if (fileDocumentManager.isDocumentUnsaved(editor.document)) {
            internalDocumentSave = true
            try {
                fileDocumentManager.saveDocument(editor.document)
            } finally {
                internalDocumentSave = false
            }
        }

        if (fileDocumentManager.isDocumentUnsaved(editor.document)) {
            Messages.showErrorDialog(
                editor.project,
                "The JMeter test plan could not be saved. The tab switch was cancelled.",
                "Unable to Save JMeter Test Plan",
            )
            return false
        }

        workspace?.let(::nativeMarkSaved)
        syncState?.accept(DocumentFingerprint.of(editor.document.immutableCharSequence))
        editor.refreshModifiedState()
        return true
    }

    private fun synchronizeDocument(editor: JMeterVisualFileEditor): Boolean {
        val nativeWorkspace = workspace ?: return true
        if (loadedFile != editor.virtualFile) {
            return true
        }

        val documentFingerprint = DocumentFingerprint.of(editor.document.immutableCharSequence)
        val state = syncState ?: DocumentSyncState(documentFingerprint).also { syncState = it }
        return try {
            when (state.actionFor(documentFingerprint, nativeIsDirty(nativeWorkspace))) {
                DocumentSyncAction.NONE -> true
                DocumentSyncAction.RELOAD_DOCUMENT -> {
                    loadDocument(editor, nativeWorkspace)
                    true
                }
                DocumentSyncAction.SNAPSHOT_NATIVE -> snapshotIntoDocument(editor, nativeWorkspace)
                DocumentSyncAction.CONFLICT -> when (askConflict(editor)) {
                    ConflictChoice.RELOAD_DOCUMENT -> {
                        loadDocument(editor, nativeWorkspace)
                        true
                    }
                    ConflictChoice.OVERWRITE_WITH_VISUAL -> snapshotIntoDocument(editor, nativeWorkspace)
                    ConflictChoice.CANCEL -> false
                }
            }
        } catch (failure: Exception) {
            editor.showLoadError(readableMessage(failure))
            false
        } catch (failure: LinkageError) {
            editor.showLoadError(readableMessage(failure))
            false
        }
    }

    private fun snapshotIntoDocument(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
    ): Boolean {
        val snapshot = nativeWorkspace.snapshot()
        val text = String(snapshot, StandardCharsets.UTF_8)
        TextReplacement.between(editor.document.immutableCharSequence, text)?.let { replacement ->
            applyDocumentReplacement(editor, replacement)
        }
        val fingerprint = DocumentFingerprint.of(editor.document.immutableCharSequence)
        (syncState ?: DocumentSyncState(fingerprint).also { syncState = it }).accept(fingerprint)
        return true
    }

    private fun applyDocumentReplacement(
        editor: JMeterVisualFileEditor,
        replacement: TextReplacement,
    ) {
        val application = ApplicationManager.getApplication()
        val mutation = Runnable {
            editor.document.replaceString(
                replacement.offset,
                replacement.offset + replacement.oldLength,
                replacement.newText,
            )
        }
        val writeMutation = Runnable {
            if (application.isWriteAccessAllowed) {
                mutation.run()
            } else {
                application.runWriteAction(mutation)
            }
        }
        val commandProcessor = CommandProcessor.getInstance()
        if (commandProcessor.currentCommand != null) {
            writeMutation.run()
            commandProcessor.addAffectedDocuments(editor.project, editor.document)
        } else {
            commandProcessor.executeCommand(
                editor.project,
                writeMutation,
                JMX_EDIT_COMMAND_NAME,
                fieldEditGroup ?: Any(),
                editor.document,
            )
        }
    }

    private fun bindWorkspaceSurfaces(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
    ) {
        val sessionId = sessionIdFor(editor.virtualFile)
        val controller = editor.project.getService(JMeterToolWindowController::class.java)
        controller.show(
            nativeWorkspace,
            sessionId,
            editor.virtualFile.name,
        ) {
            nativeWorkspace.clearResults(sessionId)
        }

        textAreaAdapters?.dispose()
        val editorComponent = nativeWorkspace.component
        textAreaAdapters = JMeterTextAreaAdapters(
            project = editor.project,
            roots = {
                buildList {
                    add(editorComponent)
                    addAll(controller.visibleTextRoots())
                }
            },
            onFocusStarted = {
                if (fieldEditGroup == null) {
                    fieldEditGroup = Any()
                }
            },
            onFocusEnded = {
                endFieldEdit(editor)
            },
            onFieldChanged = {
                fieldSnapshotTimer.restart()
                editor.refreshModifiedState()
            },
            onHistoryAction = { redo, focus ->
                performHistoryAction(editor, redo, focus)
            },
        )
    }

    private fun failSurfaceBinding(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
        failure: Throwable,
    ) {
        detach(editor)
        activeEditor = null
        nativeWorkspace.setDialogParent(null)
        editor.showLoadError(readableMessage(failure))
    }

    private fun endFieldEdit(editor: JMeterVisualFileEditor) {
        fieldSnapshotTimer.stop()
        if (activeEditor === editor && workspace != null) {
            synchronizeDocument(editor)
        }
        fieldSnapshotTimer.stop()
        fieldEditGroup = null
    }

    private fun flushPendingFieldEdit() {
        val editor = activeEditor ?: return
        if (!disposed && workspace != null) {
            synchronizeDocument(editor)
            fieldSnapshotTimer.stop()
            editor.refreshModifiedState()
        }
    }

    private fun performHistoryAction(
        editor: JMeterVisualFileEditor,
        redo: Boolean,
        focus: JMeterFieldFocus,
    ) {
        val nativeWorkspace = workspace ?: return
        if (activeEditor !== editor || loadedFile != editor.virtualFile) {
            return
        }

        fieldSnapshotTimer.stop()
        snapshotIntoDocument(editor, nativeWorkspace)
        fieldEditGroup = null

        val undoManager = UndoManager.getInstance(editor.project)
        val available = if (redo) {
            undoManager.isRedoAvailable(editor)
        } else {
            undoManager.isUndoAvailable(editor)
        }
        if (!available) {
            fieldEditGroup = Any()
            return
        }

        if (redo) {
            undoManager.redo(editor)
        } else {
            undoManager.undo(editor)
        }
        val text = editor.document.immutableCharSequence.toString()
        nativeWorkspace.reloadFromHistory(
            ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)),
            editor.virtualFile.toNioPath(),
        )
        fieldSnapshotTimer.stop()
        val fingerprint = DocumentFingerprint.of(text)
        (syncState ?: DocumentSyncState(fingerprint).also { syncState = it }).accept(fingerprint)
        textAreaAdapters?.restoreAfterHistory(focus)
        fieldEditGroup = Any()
        editor.refreshModifiedState()
    }

    private fun sessionIdFor(file: VirtualFile): String =
        resultSessionIds.getOrPut(file) { UUID.randomUUID().toString() }

    private fun handleExecutionAction(actionCommand: String) {
        val editor = activeEditor ?: return
        val nativeWorkspace = workspace ?: return
        when (actionCommand) {
            RUN_SELECTED_THREAD_GROUPS -> runSelectedThreadGroups(editor, nativeWorkspace)
            SHUTDOWN_TEST -> nativeWorkspace.shutdownTest()
            STOP_TEST -> nativeWorkspace.stopTest()
        }
        refreshExecutionState(nativeWorkspace)
    }

    private fun runSelectedThreadGroups(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
    ) {
        if (!nativeWorkspace.canRunSelectedThreadGroups) {
            Messages.showInfoMessage(
                editor.project,
                "Select one or more Thread Groups in the JMeter tree before running.",
                "No Thread Group Selected",
            )
            return
        }
        if (!persist(editor, saveToDisk = true)) {
            return
        }

        val sessionId = sessionIdFor(editor.virtualFile)
        if (nativeWorkspace.startSelectedThreadGroups(sessionId)) {
            runningFile = editor.virtualFile
            runningEditor = editor
            wasTestRunning = true
            val controller = editor.project.getService(JMeterToolWindowController::class.java)
            val toolWindow = ToolWindowManager.getInstance(editor.project).getToolWindow(JMETER_TOOL_WINDOW_ID)
            if (toolWindow == null) {
                controller.selectResultsTree()
            } else {
                toolWindow.activate(Runnable { controller.selectResultsTree() })
            }
        }
    }

    private fun refreshExecutionState(nativeWorkspace: JMeterWorkspace) {
        val running = nativeWorkspace.isTestRunning
        if (wasTestRunning && !running) {
            clearRunningState()
        }
        wasTestRunning = running
    }

    private fun clearRunningState() {
        runningFile = null
        runningEditor = null
        wasTestRunning = false
    }

    private fun scheduleSavedBaseline(editor: JMeterVisualFileEditor) {
        val expectedFingerprint = DocumentFingerprint.of(editor.document.immutableCharSequence)
        ApplicationManager.getApplication().invokeLater {
            if (
                activeEditor === editor &&
                !fileDocumentManager.isDocumentUnsaved(editor.document) &&
                DocumentFingerprint.of(editor.document.immutableCharSequence) == expectedFingerprint
            ) {
                workspace?.let(::nativeMarkSaved)
                syncState?.accept(expectedFingerprint)
                editor.refreshModifiedState()
            }
        }
    }

    private fun attach(editor: JMeterVisualFileEditor, component: JComponent) {
        activeEditor?.takeIf { it !== editor }?.detachNative(component)
        editor.attachNative(component)
    }

    private fun detach(editor: JMeterVisualFileEditor) {
        fieldSnapshotTimer.stop()
        textAreaAdapters?.dispose()
        textAreaAdapters = null
        fieldEditGroup = null
        workspace?.component?.let(editor::detachNative)
        if (!editor.project.isDisposed && runningFile == null) {
            editor.project.getService(JMeterToolWindowController::class.java).showEmpty()
        }
    }

    private fun askConflict(editor: JMeterVisualFileEditor): ConflictChoice {
        val selected = Messages.showDialog(
            editor.project,
            "The JMX text changed after the visual editor loaded it, and the visual model also has changes. " +
                "Choose which version to keep.",
            "JMeter Test Plan Conflict",
            arrayOf("Reload external", "Overwrite visual", "Cancel"),
            2,
            Messages.getWarningIcon(),
        )
        return when (selected) {
            0 -> ConflictChoice.RELOAD_DOCUMENT
            1 -> ConflictChoice.OVERWRITE_WITH_VISUAL
            else -> ConflictChoice.CANCEL
        }
    }

    private fun readableMessage(failure: Throwable): String {
        var root = failure
        while (root.cause != null && root.cause !== root) {
            root = root.cause!!
        }
        return root.message ?: root.javaClass.simpleName
    }

    private fun nativeIsDirty(nativeWorkspace: JMeterWorkspace): Boolean =
        nativeWorkspace.isDirty

    private fun nativeMarkSaved(nativeWorkspace: JMeterWorkspace) {
        nativeWorkspace.markSaved()
    }

    private fun <T> onEdt(action: () -> T): T {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return action()
        }

        val value = AtomicReference<Any?>()
        val failure = AtomicReference<Throwable?>()
        application.invokeAndWait {
            try {
                value.set(action())
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        failure.get()?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value.get() as T
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        onEdt {
            fieldSnapshotTimer.stop()
            textAreaAdapters?.dispose()
            textAreaAdapters = null
            workspace?.setExecutionActionListener(null)
            workspace?.setModelChangeListener(null)
            workspace?.close()
            workspace = null
            activeEditor = null
            loadedFile = null
            syncState = null
            resultSessionIds.clear()
            clearRunningState()
        }
    }

    private enum class ConflictChoice {
        RELOAD_DOCUMENT,
        OVERWRITE_WITH_VISUAL,
        CANCEL,
    }

    private companion object {
        const val FIELD_SNAPSHOT_DELAY_MS = 250
        const val JMX_EDIT_COMMAND_NAME = "Edit JMeter Test Plan"
        const val RUN_SELECTED_THREAD_GROUPS = "jmeter.run.selected.thread.groups"
        const val SHUTDOWN_TEST = "jmeter.shutdown.test"
        const val STOP_TEST = "jmeter.stop.test"
        const val JMETER_TOOL_WINDOW_ID = "JMeter"
    }
}
