package com.github.kohebth.jmeterviewer.editor

import com.github.kohebth.jmeterviewer.execution.JMeterLaunchRequest
import com.github.kohebth.jmeterviewer.execution.JMeterResultBridge
import com.github.kohebth.jmeterviewer.execution.JMeterRunConfiguration
import com.github.kohebth.jmeterviewer.execution.JMeterRunConfigurationType
import com.github.kohebth.jmeterviewer.execution.JMeterRunMode
import com.github.kohebth.jmeterviewer.runtime.JMeterConfigurationException
import com.github.kohebth.jmeterviewer.runtime.JMeterJavaEnvironment
import com.github.kohebth.jmeterviewer.runtime.JMeterRuntimeService
import com.github.kohebth.jmeterviewer.runtime.JMeterRuntimeSession
import com.github.kohebth.jmeterviewer.runtime.JMeterReplaceResult
import com.github.kohebth.jmeterviewer.runtime.JMeterSearchMatch
import com.github.kohebth.jmeterviewer.runtime.JMeterWorkspace
import com.github.kohebth.jmeterviewer.toolwindow.JMeterToolWindowController
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.event.ActionListener
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.Timer

@Service(Service.Level.PROJECT)
class JMeterWorkspaceService(
    private val project: Project,
) : Disposable {
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private val sessions = IdentityHashMap<JMeterVisualFileEditor, JMeterEditorSession>()
    private val documentRegistry = ApplicationManager.getApplication()
        .getService(JMeterDocumentSessionRegistry::class.java)
    private var activeSession: JMeterEditorSession? = null
    private var disposed = false

    fun activate(editor: JMeterVisualFileEditor) = onEdt {
        if (disposed || editor.project !== project || project.isDisposed) {
            return@onEdt
        }
        val session = sessions[editor] ?: openSession(editor) ?: return@onEdt
        if (activeSession !== session) {
            val previous = activeSession
            previous?.deactivate(previous.editor)
            if (previous?.isEditorActive == true) {
                editor.showSwitchBlocked()
                return@onEdt
            }
            activeSession = session
        }
        session.activate(editor)
    }

    fun retry(editor: JMeterVisualFileEditor) {
        activate(editor)
    }

    fun deactivate(editor: JMeterVisualFileEditor) = onEdt {
        val session = sessions[editor] ?: return@onEdt
        session.deactivate(editor)
        if (activeSession === session && !session.isEditorActive) {
            activeSession = null
            if (!project.isDisposed) {
                project.getService(JMeterToolWindowController::class.java).showEmpty()
            }
        }
    }

    fun unregister(editor: JMeterVisualFileEditor) = onEdt {
        val session = sessions.remove(editor)
        if (activeSession === session) {
            activeSession = null
        }
        try {
            session?.unregister(editor)
            session?.dispose()
        } finally {
            documentRegistry.release(editor)
        }
        if (!project.isDisposed && activeSession == null) {
            project.getService(JMeterToolWindowController::class.java).showEmpty()
        }
    }

    fun isModified(editor: JMeterVisualFileEditor): Boolean = onEdt {
        sessions[editor]?.isModified(editor)
            ?: fileDocumentManager.isDocumentUnsaved(editor.document)
    }

    internal fun prepareExternalRun(
        targetFileUrl: String,
        mode: JMeterRunMode,
    ): JMeterLaunchRequest = onEdt {
        sessionForUrl(targetFileUrl)?.prepareExternalRun(mode)
            ?: throw IllegalStateException(
                "The JMeter visual session for $targetFileUrl is no longer open.",
            )
    }

    internal fun bindExternalProcess(request: JMeterLaunchRequest, handler: ProcessHandler) = onEdt {
        sessionForFile(request.ownerFile)?.bindExternalProcess(request, handler)
            ?: run {
                handler.destroyProcess()
                throw IllegalStateException("The JMeter visual session was closed before the run started.")
            }
    }

    internal fun abortExternalRun(request: JMeterLaunchRequest) = onEdt {
        sessionForFile(request.ownerFile)?.abortExternalRun(request)
    }

    internal fun isActive(session: JMeterEditorSession): Boolean = activeSession === session

    internal fun performShortcut(
        editor: JMeterVisualFileEditor,
        command: String,
        argument: String?,
    ) = onEdt {
        sessions[editor]?.performShortcut(command, argument)
    }

    private fun openSession(editor: JMeterVisualFileEditor): JMeterEditorSession? {
        val claimed = documentRegistry.claim(editor) {
            ApplicationManager.getApplication().invokeLater {
                if (!disposed && editor.isValid) {
                    retry(editor)
                }
            }
        }
        if (!claimed) {
            editor.showLoadError(
                "This physical JMX file already has a visual JMeter editor in another IDE window. " +
                    "Close that visual tab, or use Open Text here.",
            )
            return null
        }

        var runtimeSession: JMeterRuntimeSession? = null
        return try {
            runtimeSession = ApplicationManager.getApplication()
                .getService(JMeterRuntimeService::class.java)
                .openSession()
            JMeterEditorSession(this, editor, runtimeSession).also { session ->
                sessions[editor] = session
                documentRegistry.bind(editor, session)
            }
        } catch (failure: JMeterConfigurationException) {
            runtimeSession?.close()
            documentRegistry.release(editor)
            editor.showLoadError(readableMessage(failure), offerConfiguration = true)
            null
        } catch (failure: Exception) {
            runtimeSession?.close()
            documentRegistry.release(editor)
            editor.showLoadError(readableMessage(failure))
            null
        } catch (failure: LinkageError) {
            runtimeSession?.close()
            documentRegistry.release(editor)
            editor.showLoadError(readableMessage(failure))
            null
        }
    }

    private fun sessionForUrl(url: String): JMeterEditorSession? =
        sessions.values.firstOrNull { it.editor.virtualFile.url == url }

    private fun sessionForFile(file: VirtualFile): JMeterEditorSession? =
        sessions.values.firstOrNull { it.editor.virtualFile == file }

    private fun readableMessage(failure: Throwable): String {
        var root = failure
        while (root.cause != null && root.cause !== root) {
            root = root.cause!!
        }
        return root.message ?: root.javaClass.simpleName
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
            sessions.keys.toList().forEach { editor ->
                val session = sessions.remove(editor)
                try {
                    session?.dispose()
                } finally {
                    documentRegistry.release(editor)
                }
            }
            activeSession = null
        }
    }
}

internal class JMeterEditorSession(
    private val manager: JMeterWorkspaceService,
    internal val editor: JMeterVisualFileEditor,
    private val runtimeSession: JMeterRuntimeSession,
) : Disposable {
    internal val isEditorActive: Boolean
        get() = activeEditor != null

    private val fileDocumentManager = FileDocumentManager.getInstance()
    private var workspace: JMeterWorkspace? = runtimeSession.workspace
    private var workspaceInitialized = false
    private var loadedFile: VirtualFile? = null
    private var syncState: DocumentSyncState? = null
    private var activeEditor: JMeterVisualFileEditor? = null
    private val resultSessionIds = IdentityHashMap<VirtualFile, String>()
    private var textAreaAdapters: JMeterTextAreaAdapters? = null
    private var shortcutController: JMeterShortcutController? = null
    private var fieldEditGroup: Any? = null
    private var pendingVisualChange = false
    private var runningFile: VirtualFile? = null
    private var runningEditor: JMeterVisualFileEditor? = null
    private var pendingRun: JMeterLaunchRequest? = null
    private var activeRun: JMeterLaunchRequest? = null
    private var activeProcess: ProcessHandler? = null
    private val pendingSamples = ConcurrentLinkedQueue<ExternalSample>()
    private val sampleDrainScheduled = AtomicBoolean()
    private val fieldSnapshotTimer = Timer(FIELD_SNAPSHOT_DELAY_MS) { schedulePendingFieldEditFlush() }
    private var fieldSnapshotGeneration = 0L
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

        if (!persist(editor, saveToDisk = true)) {
            switchBlocked = true
            editor.requestReselect()
            return@onEdt
        }

        detach(editor)
        activeEditor = null
        workspace?.takeUnless(JMeterWorkspace::isClosed)?.setDialogParent(null)
        switchBlocked = false
    }

    fun unregister(editor: JMeterVisualFileEditor) = onEdt {
        val nativeWorkspace = workspace
        val wasActive = activeEditor === editor
        if (activeEditor === editor) {
            if (!editor.project.isDisposed && editor.virtualFile.isValid) {
                persist(editor, saveToDisk = true)
            }
            detach(editor)
            activeEditor = null
            workspace?.takeUnless(JMeterWorkspace::isClosed)?.setDialogParent(null)
            switchBlocked = false
        }

        if (!(runInProgress() && runningFile == editor.virtualFile)) {
            resultSessionIds.remove(editor.virtualFile)?.let { sessionId ->
                if (nativeWorkspace != null && !nativeWorkspace.isClosed) {
                    nativeWorkspace.discardResults(sessionId)
                }
            }
        }
        if (wasActive && !editor.project.isDisposed && !(runInProgress() && runningFile == editor.virtualFile)) {
            editor.project.getService(JMeterToolWindowController::class.java).showEmpty()
        }
    }

    fun isModified(editor: JMeterVisualFileEditor): Boolean = onEdt {
        val documentUnsaved = fileDocumentManager.isDocumentUnsaved(editor.document)
        if (activeEditor !== editor) {
            return@onEdt documentUnsaved
        }

        val nativeWorkspace = workspace
        documentUnsaved || pendingVisualChange || (nativeWorkspace?.let(::nativeIsDirty) ?: false)
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
        val nativeWorkspace = workspace
            ?: throw IllegalStateException("The JMeter visual session is closed.")
        if (!workspaceInitialized) {
            nativeWorkspace.setModelChangeListener(Runnable {
                if (!disposed) {
                    textAreaAdapters?.scanNow()
                    val changedEditor = activeEditor
                    if (changedEditor != null) {
                        ApplicationManager.getApplication().invokeLater {
                            if (!disposed && activeEditor === changedEditor) {
                                changedEditor.refreshModifiedState()
                            }
                        }
                    }
                }
            })
            nativeWorkspace.setExecutionActionListener(ActionListener { event ->
                handleExecutionAction(event.actionCommand)
            })
            workspaceInitialized = true
        }
        return nativeWorkspace
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
        editor.project.getService(JMeterToolWindowController::class.java).resetTestPlanSearch()
        val fingerprint = DocumentFingerprint.of(text)
        loadedFile = editor.virtualFile
        syncState = DocumentSyncState(fingerprint)
        pendingVisualChange = false
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
        commandName: String = JMX_EDIT_COMMAND_NAME,
        commandGroup: Any = fieldEditGroup ?: Any(),
    ): Boolean {
        val snapshot = nativeWorkspace.snapshot()
        val text = String(snapshot, StandardCharsets.UTF_8)
        TextReplacement.between(editor.document.immutableCharSequence, text)?.let { replacement ->
            applyDocumentReplacement(editor, replacement, commandName, commandGroup)
        }
        val fingerprint = DocumentFingerprint.of(editor.document.immutableCharSequence)
        (syncState ?: DocumentSyncState(fingerprint).also { syncState = it }).accept(fingerprint)
        pendingVisualChange = false
        return true
    }

    private fun applyDocumentReplacement(
        editor: JMeterVisualFileEditor,
        replacement: TextReplacement,
        commandName: String,
        commandGroup: Any,
        jetbrainsVisualUndoEnabled: Boolean = JMeterEditorFeatures.JETBRAINS_VISUAL_UNDO_ENABLED,
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
        if (!jetbrainsVisualUndoEnabled) {
            UndoUtil.disableUndoIn(editor.document, writeMutation)
            return
        }
        val commandProcessor = CommandProcessor.getInstance()
        if (commandProcessor.currentCommand != null) {
            writeMutation.run()
            commandProcessor.addAffectedDocuments(editor.project, editor.document)
        } else {
            commandProcessor.executeCommand(
                editor.project,
                writeMutation,
                commandName,
                commandGroup,
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
            workspace = nativeWorkspace,
            sessionId = sessionId,
            displayName = editor.virtualFile.name,
            onClearResults = { nativeWorkspace.clearResults(sessionId) },
            onReplace = { matches, query, replacement, caseSensitive, regexp ->
                replaceSearchResults(
                    editor,
                    nativeWorkspace,
                    matches,
                    query,
                    replacement,
                    caseSensitive,
                    regexp,
                )
            },
            onHistoryAction = { redo ->
                performHistoryActionIfEnabled(
                    JMeterEditorFeatures.JETBRAINS_VISUAL_UNDO_ENABLED,
                    editor,
                    redo,
                    null,
                )
            },
        )
        ToolWindowManager.getInstance(editor.project)
            .getToolWindow(JMETER_TOOL_WINDOW_ID)
            ?.show(Runnable { controller.selectTestPlan() })

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
            adaptMultilineTextAreas = JMeterEditorFeatures.JETBRAINS_MULTILINE_EDITOR_ENABLED,
            jetbrainsHistoryEnabled = JMeterEditorFeatures.JETBRAINS_VISUAL_UNDO_ENABLED,
            onFocusStarted = {
                if (fieldEditGroup == null) {
                    fieldEditGroup = Any()
                }
            },
            onFocusEnded = {
                endFieldEdit(editor)
            },
            onFieldChanged = {
                pendingVisualChange = true
                restartFieldSnapshotTimer()
                editor.refreshModifiedState()
            },
            onHistoryAction = { redo, focus ->
                performHistoryActionIfEnabled(
                    JMeterEditorFeatures.JETBRAINS_VISUAL_UNDO_ENABLED,
                    editor,
                    redo,
                    focus,
                )
            },
            onLanguageContextChanged = editor::showLanguageContext,
        )
        shortcutController?.dispose()
        shortcutController = JMeterShortcutController(
            editor = editor,
            service = manager,
            nativeShortcuts = nativeWorkspace.shortcutDescriptors,
            roots = listOf(editor.component) + controller.shortcutRoots(),
            treeRoot = nativeWorkspace.outlineComponent,
        )
    }

    private fun replaceSearchResults(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
        matches: List<JMeterSearchMatch>,
        query: String,
        replacement: String,
        caseSensitive: Boolean,
        regexp: Boolean,
    ): JMeterReplaceResult {
        check(activeEditor === editor && workspace === nativeWorkspace) {
            "The JMeter test plan is no longer active."
        }
        cancelPendingFieldSnapshot()
        check(synchronizeDocument(editor)) {
            "The current JMeter form could not be synchronized before replacement."
        }

        val replacementGroup = Any()
        fieldEditGroup = replacementGroup
        return try {
            val result = nativeWorkspace.replaceSearchResults(
                matches,
                query,
                replacement,
                caseSensitive,
                regexp,
            )
            if (result.occurrences > 0) {
                snapshotIntoDocument(
                    editor,
                    nativeWorkspace,
                    commandName = JMX_REPLACE_COMMAND_NAME,
                    commandGroup = replacementGroup,
                )
                pendingVisualChange = false
                textAreaAdapters?.scanNow()
                editor.refreshModifiedState()
            }
            result
        } finally {
            fieldEditGroup = Any()
        }
    }

    private fun failSurfaceBinding(
        editor: JMeterVisualFileEditor,
        nativeWorkspace: JMeterWorkspace,
        failure: Throwable,
    ) {
        detach(editor)
        activeEditor = null
        if (!nativeWorkspace.isClosed) {
            nativeWorkspace.setDialogParent(null)
        }
        editor.showLoadError(readableMessage(failure))
    }

    private fun endFieldEdit(editor: JMeterVisualFileEditor) {
        cancelPendingFieldSnapshot()
        if (activeEditor === editor && workspace != null) {
            synchronizeDocument(editor)
            pendingVisualChange = false
        }
        cancelPendingFieldSnapshot()
        fieldEditGroup = null
    }

    private fun restartFieldSnapshotTimer() {
        fieldSnapshotGeneration++
        fieldSnapshotTimer.restart()
    }

    /**
     * Swing timers run on the EDT but outside IntelliJ's write-safe transaction context.
     * Re-enter through the application queue before synchronizing the IDE document.
     */
    private fun schedulePendingFieldEditFlush() {
        val expectedGeneration = fieldSnapshotGeneration
        val application = ApplicationManager.getApplication()
        application.invokeLater(
            Runnable {
                if (expectedGeneration == fieldSnapshotGeneration) {
                    flushPendingFieldEdit()
                }
            },
            ModalityState.defaultModalityState(),
        )
    }

    private fun cancelPendingFieldSnapshot() {
        fieldSnapshotGeneration++
        fieldSnapshotTimer.stop()
    }

    private fun flushPendingFieldEdit() {
        val editor = activeEditor ?: return
        if (!disposed && workspace != null) {
            synchronizeDocument(editor)
            pendingVisualChange = false
            cancelPendingFieldSnapshot()
            editor.refreshModifiedState()
        }
    }

    private fun performHistoryAction(
        editor: JMeterVisualFileEditor,
        redo: Boolean,
        focus: JMeterFieldFocus?,
    ) {
        val nativeWorkspace = workspace ?: return
        if (activeEditor !== editor || loadedFile != editor.virtualFile) {
            return
        }

        cancelPendingFieldSnapshot()
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
        editor.project.getService(JMeterToolWindowController::class.java).resetTestPlanSearch()
        cancelPendingFieldSnapshot()
        val fingerprint = DocumentFingerprint.of(text)
        (syncState ?: DocumentSyncState(fingerprint).also { syncState = it }).accept(fingerprint)
        pendingVisualChange = false
        focus?.let { textAreaAdapters?.restoreAfterHistory(it) }
        fieldEditGroup = Any()
        editor.refreshModifiedState()
    }

    private fun performHistoryActionIfEnabled(
        enabled: Boolean,
        editor: JMeterVisualFileEditor,
        redo: Boolean,
        focus: JMeterFieldFocus?,
    ) {
        if (enabled) {
            performHistoryAction(editor, redo, focus)
        }
    }

    private fun sessionIdFor(file: VirtualFile): String =
        resultSessionIds.getOrPut(file) { UUID.randomUUID().toString() }

    private fun handleExecutionAction(actionCommand: String) {
        val editor = activeEditor ?: return
        val nativeWorkspace = workspace ?: return
        val mode = JMeterRunMode.fromActionCommand(actionCommand) ?: return
        if (runInProgress()) {
            Messages.showInfoMessage(
                editor.project,
                "A JMeter process is already running for ${runningFile?.name ?: "another test plan"}.",
                "JMeter Is Already Running",
            )
            return
        }
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

        val type = ConfigurationTypeUtil.findConfigurationType(JMeterRunConfigurationType::class.java)
        val factory = type.configurationFactories.single()
        val runManager = RunManager.getInstance(editor.project)
        val settings = runManager.createConfiguration(
            "JMeter: ${editor.virtualFile.nameWithoutExtension}",
            factory,
        )
        val configuration = settings.configuration as JMeterRunConfiguration
        configuration.runMode = mode
        configuration.targetFileUrl = editor.virtualFile.url
        configuration.setAllowRunningInParallel(true)
        settings.setActivateToolWindowBeforeRun(false)
        runManager.setTemporaryConfiguration(settings)
        ProgramRunnerUtil.executeConfiguration(
            settings,
            DefaultRunExecutor.getRunExecutorInstance(),
        )
    }

    internal fun performShortcut(command: String, argument: String?) {
        when (command) {
            "save" -> persist(editor, saveToDisk = true)
            "search_tree" -> {
                if (manager.isActive(this)) {
                    editor.project.getService(JMeterToolWindowController::class.java).showSearch()
                }
            }
            "search_reset" -> editor.project
                .getService(JMeterToolWindowController::class.java)
                .resetTestPlanSearch()
            "run_tg", "run_tg_no_timers", "validate_tg" -> handleExecutionAction(command)
            "shutdown" -> activeProcess?.takeIf { !it.isProcessTerminated }?.destroyProcess()
            "stop" -> activeProcess?.takeIf { !it.isProcessTerminated }?.destroyProcess()
            "undo", "redo", "select_all" -> Unit
            else -> {
                ensureWorkspace().performAction(command, argument)
                editor.refreshModifiedState()
            }
        }
    }

    private fun clearRunningState() {
        runningFile = null
        runningEditor = null
        pendingRun = null
        activeRun = null
        activeProcess = null
    }

    internal fun prepareExternalRun(mode: JMeterRunMode): JMeterLaunchRequest = onEdt {
        check(!runInProgress()) { "Another JMeter process is already running." }
        val editor = this.editor.takeIf { it.virtualFile.isValid }
            ?: throw IllegalStateException("The target JMX file is no longer open.")
        val nativeWorkspace = workspace
            ?: throw IllegalStateException("The JMeter visual workspace is not loaded.")
        check(nativeWorkspace.canRunSelectedThreadGroups) {
            "Select one or more Thread Groups in the JMeter tree before running."
        }
        check(persist(editor, saveToDisk = true)) { "The JMX file could not be saved before running." }

        val installation = ApplicationManager.getApplication()
            .getService(JMeterRuntimeService::class.java)
            .configuredInstallation()
        val isWindows = SystemInfo.isWindows
        val commandPrefix = installation.commandLinePrefix(isWindows)
        val javaEnvironment = JMeterJavaEnvironment.resolve(isWindows)
        val sourcePath = editor.virtualFile.toNioPath().toAbsolutePath().normalize()
        val sourceDirectory = sourcePath.parent
            ?: throw IllegalStateException("The JMX file has no parent directory: $sourcePath")
        val restrictedPlan = Files.createTempFile(sourceDirectory, ".jmeter-idea-", ".jmx")
        val journal = Files.createTempFile(sourceDirectory, ".jmeter-idea-", ".results")
        val logFile = Files.createTempFile(sourceDirectory, ".jmeter-idea-", ".log")
        val token = UUID.randomUUID().toString().replace("-", "")
        var bridge: JMeterResultBridge? = null
        try {
            val sessionId = sessionIdFor(editor.virtualFile)
            bridge = JMeterResultBridge(token, journal) { sample ->
                enqueueExternalSample(editor.virtualFile, sessionId, sample)
            }
            val jmx = nativeWorkspace.snapshotSelectedThreadGroups(
                mode.actionCommand,
                bridge.port,
                token,
                journal,
            ) ?: throw IllegalStateException(
                "Select one or more Thread Groups in the JMeter tree before running.",
            )
            Files.write(
                restrictedPlan,
                jmx,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )

            val commandLine = GeneralCommandLine(commandPrefix.first())
                .withParameters(*commandPrefix.drop(1).toTypedArray())
                .withParameters(
                    "-n",
                    "-t",
                    restrictedPlan.toString(),
                    "-j",
                    logFile.toString(),
                )
                .withWorkDirectory(sourceDirectory.toFile())
                .withCharset(StandardCharsets.UTF_8)
                .apply {
                    javaEnvironment.forEach { (name, value) ->
                        withEnvironment(name, value)
                    }
                }
            val request = JMeterLaunchRequest(
                commandLine = commandLine,
                ownerFile = editor.virtualFile,
                sessionId = sessionId,
                restrictedPlan = restrictedPlan,
                logFile = logFile,
                bridge = bridge,
            )
            pendingRun = request
            runningFile = editor.virtualFile
            runningEditor = editor
            request
        } catch (failure: Throwable) {
            bridge?.close()
            deleteRunFile(restrictedPlan)
            deleteRunFile(journal)
            deleteRunFile(logFile)
            throw failure
        }
    }

    internal fun bindExternalProcess(request: JMeterLaunchRequest, handler: ProcessHandler) = onEdt {
        check(pendingRun === request) { "The prepared JMeter run is no longer active." }
        pendingRun = null
        activeRun = request
        activeProcess = handler
        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                finishExternalRun(request)
            }
        })
        showResultsForRunningFile()
    }

    internal fun abortExternalRun(request: JMeterLaunchRequest) = onEdt {
        if (pendingRun === request || activeRun === request) {
            request.bridge.close()
            cleanupRunFiles(request)
            clearRunningState()
        }
    }

    private fun finishExternalRun(request: JMeterLaunchRequest) {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                request.bridge.finishAndReplayJournal()
            } catch (failure: Exception) {
                LOG.warn("Unable to replay the JMeter live-result journal", failure)
            } finally {
                cleanupRunFiles(request)
                ApplicationManager.getApplication().invokeLater {
                    if (activeRun === request || pendingRun === request) {
                        clearRunningState()
                    }
                }
            }
        }
    }

    private fun enqueueExternalSample(file: VirtualFile, sessionId: String, sample: ByteArray) {
        pendingSamples.add(ExternalSample(file, sessionId, sample))
        if (sampleDrainScheduled.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater(::drainExternalSamples)
        }
    }

    private fun drainExternalSamples() {
        var count = 0
        while (count < MAX_SAMPLES_PER_EDT_BATCH) {
            val sample = pendingSamples.poll() ?: break
            if (sample.file == runningFile || sample.file == loadedFile) {
                try {
                    workspace?.appendSampleResult(sample.sessionId, sample.payload)
                } catch (failure: Exception) {
                    LOG.warn("Unable to add an external JMeter sample to the native result views", failure)
                }
            }
            count++
        }
        sampleDrainScheduled.set(false)
        if (pendingSamples.isNotEmpty() && sampleDrainScheduled.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater(::drainExternalSamples)
        }
    }

    private fun showResultsForRunningFile() {
        val editor = runningEditor ?: return
        if (!manager.isActive(this)) {
            return
        }
        val controller = editor.project.getService(JMeterToolWindowController::class.java)
        controller.selectResultsTree(showWindow = false)
    }

    private fun runInProgress(): Boolean = pendingRun != null || activeRun != null

    private fun cleanupRunFiles(request: JMeterLaunchRequest) {
        request.bridge.close()
        deleteRunFile(request.restrictedPlan)
        deleteRunFile(request.bridge.journalPath)
        deleteRunFile(request.logFile)
    }

    private fun deleteRunFile(path: java.nio.file.Path) {
        try {
            Files.deleteIfExists(path)
        } catch (failure: Exception) {
            LOG.debug("Unable to delete temporary JMeter run file $path", failure)
        }
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
        cancelPendingFieldSnapshot()
        textAreaAdapters?.dispose()
        textAreaAdapters = null
        shortcutController?.dispose()
        shortcutController = null
        editor.showLanguageContext(null)
        fieldEditGroup = null
        pendingVisualChange = false
        workspace?.takeUnless(JMeterWorkspace::isClosed)?.component?.let(editor::detachNative)
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
            activeProcess?.takeIf { !it.isProcessTerminated }?.destroyProcess()
            pendingRun?.let(::cleanupRunFiles)
            activeRun?.let(::cleanupRunFiles)
            pendingSamples.clear()
            cancelPendingFieldSnapshot()
            textAreaAdapters?.dispose()
            textAreaAdapters = null
            shortcutController?.dispose()
            shortcutController = null
            workspace?.takeUnless(JMeterWorkspace::isClosed)?.let { nativeWorkspace ->
                try {
                    nativeWorkspace.setExecutionActionListener(null)
                    nativeWorkspace.setModelChangeListener(null)
                    nativeWorkspace.setDialogParent(null)
                } catch (failure: RuntimeException) {
                    LOG.warn("Unable to detach listeners from the embedded JMeter workspace", failure)
                } catch (failure: LinkageError) {
                    LOG.warn("Unable to detach listeners from the embedded JMeter workspace", failure)
                }
            }
            workspace = null
            activeEditor = null
            loadedFile = null
            syncState = null
            resultSessionIds.clear()
            clearRunningState()
            runtimeSession.close()
        }
    }

    private enum class ConflictChoice {
        RELOAD_DOCUMENT,
        OVERWRITE_WITH_VISUAL,
        CANCEL,
    }

    private data class ExternalSample(
        val file: VirtualFile,
        val sessionId: String,
        val payload: ByteArray,
    )

    private companion object {
        val LOG: Logger = Logger.getInstance(JMeterEditorSession::class.java)
        const val FIELD_SNAPSHOT_DELAY_MS = 250
        const val MAX_SAMPLES_PER_EDT_BATCH = 32
        const val JMX_EDIT_COMMAND_NAME = "Edit JMeter Test Plan"
        const val JMX_REPLACE_COMMAND_NAME = "Replace in JMeter Test Plan"
        const val JMETER_TOOL_WINDOW_ID = "JMeter"
    }
}
