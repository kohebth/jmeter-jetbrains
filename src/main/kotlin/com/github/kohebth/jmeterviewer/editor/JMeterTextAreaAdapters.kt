package com.github.kohebth.jmeterviewer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.Rectangle
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent as SwingDocumentEvent
import javax.swing.event.DocumentListener as SwingDocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent

internal data class JMeterFieldFocus(
    val slot: Int,
    val caretOffset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val nativeComponent: JTextComponent? = null,
)

internal data class JMeterLanguageContext(
    val languages: List<JMeterEditorLanguage>,
    val selected: JMeterEditorLanguage,
    val selectLanguage: (JMeterEditorLanguage) -> Unit,
    val reformat: () -> Unit,
)

/**
 * Tracks visible JMeter text fields for autosave and, when enabled, replaces
 * persistent multiline fields with short-lived IntelliJ editors. The adapter
 * implementation remains available while native JMeter fields are the stable
 * default.
 */
internal class JMeterTextAreaAdapters(
    private val project: Project,
    private val roots: () -> Collection<JComponent>,
    private val adaptMultilineTextAreas: Boolean,
    private val onFocusStarted: () -> Unit,
    private val onFocusEnded: () -> Unit,
    private val onFieldChanged: () -> Unit,
    private val onHistoryAction: (redo: Boolean, focus: JMeterFieldFocus) -> Unit,
    private val onLanguageContextChanged: (JMeterLanguageContext?) -> Unit,
) : Disposable {
    private val editorFactory = EditorFactory.getInstance()
    private val adapters = IdentityHashMap<JTextComponent, TextAreaAdapter>()
    private val nativeFields = IdentityHashMap<JTextComponent, NativeFieldTracker>()
    private val reconcileTimer = Timer(0) { scan() }
    private val observedContainers = identitySet<Container>()
    private val observedRoots = identitySet<JComponent>()
    private var orderedAdapters: List<TextAreaAdapter> = emptyList()
    private var activeAdapter: TextAreaAdapter? = null
    private var activeNativeField: NativeFieldTracker? = null
    private var pendingFocus: JMeterFieldFocus? = null
    private var disposed = false

    private val keyDispatcher = KeyEventDispatcher { event ->
        dispatchHistoryShortcut(event)
    }
    private val containerListener = object : ContainerAdapter() {
        override fun componentAdded(event: ContainerEvent) = scheduleScan()

        override fun componentRemoved(event: ContainerEvent) = scheduleScan()
    }
    private val hierarchyListener = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
            scheduleScan()
        }
    }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
        reconcileTimer.isRepeats = false
        scan()
    }

    fun scanNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            scan()
        } else {
            ApplicationManager.getApplication().invokeLater(::scan)
        }
    }

    fun restoreAfterHistory(focus: JMeterFieldFocus) {
        focus.nativeComponent?.let { component ->
            ApplicationManager.getApplication().invokeLater {
                if (!disposed) {
                    restoreNativeFocus(component, focus)
                }
            }
            return
        }
        pendingFocus = focus
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) {
                scan()
            }
        }
    }

    private fun scan() {
        if (disposed || project.isDisposed || !SwingUtilities.isEventDispatchThread()) {
            return
        }

        val found = mutableListOf<TextAreaAdapter>()
        val foundNativeFields = mutableListOf<NativeFieldTracker>()
        val foundContainers = identitySet<Container>()
        val currentRoots = roots().toList()
        updateObservedRoots(currentRoots)
        currentRoots.filter { it.isShowing }.forEach {
            collectAdapters(it, found, foundNativeFields, foundContainers)
        }
        updateObservedContainers(foundContainers)
        adapters.values.toList()
            .filterNot(found::contains)
            .forEach(TextAreaAdapter::dispose)
        nativeFields.values.toList()
            .filterNot(foundNativeFields::contains)
            .forEach(NativeFieldTracker::dispose)

        orderedAdapters = found
        orderedAdapters.forEachIndexed { index, adapter -> adapter.slot = index }
        restorePendingFocus()
    }

    private fun collectAdapters(
        component: Component,
        found: MutableList<TextAreaAdapter>,
        foundNativeFields: MutableList<NativeFieldTracker>,
        foundContainers: MutableSet<Container>,
    ) {
        if (!component.isShowing) {
            return
        }
        if (component is JComponent) {
            val mounted = component.getClientProperty(ADAPTER_PROPERTY) as? TextAreaAdapter
            if (mounted != null) {
                found.add(mounted)
                return
            }
        }
        if (component is Container) {
            foundContainers.add(component)
        }

        if (
            component is JTextArea &&
            JMeterTextAreaPolicy.shouldAdapt(component, adaptMultilineTextAreas)
        ) {
            val adapter = adapters[component] ?: TextAreaAdapter(component).also {
                adapters[component] = it
            }
            found.add(adapter)
            return
        }

        if (component is JTextComponent && JMeterTextAreaPolicy.canTrackChanges(component)) {
            val tracker = nativeFields[component] ?: NativeFieldTracker(component).also {
                nativeFields[component] = it
            }
            foundNativeFields.add(tracker)
            return
        }

        if (component is Container) {
            component.components.toList().forEach { child ->
                collectAdapters(child, found, foundNativeFields, foundContainers)
            }
        }
    }

    private fun scheduleScan() {
        if (disposed) {
            return
        }
        if (SwingUtilities.isEventDispatchThread()) {
            reconcileTimer.restart()
        } else {
            ApplicationManager.getApplication().invokeLater {
                if (!disposed) {
                    reconcileTimer.restart()
                }
            }
        }
    }

    private fun updateObservedRoots(currentRoots: Collection<JComponent>) {
        observedRoots.toList().filterNot(currentRoots::contains).forEach { root ->
            root.removeHierarchyListener(hierarchyListener)
            observedRoots.remove(root)
        }
        currentRoots.filterNot(observedRoots::contains).forEach { root ->
            root.addHierarchyListener(hierarchyListener)
            observedRoots.add(root)
        }
    }

    private fun updateObservedContainers(current: Set<Container>) {
        observedContainers.toList().filterNot(current::contains).forEach { container ->
            container.removeContainerListener(containerListener)
            observedContainers.remove(container)
        }
        current.filterNot(observedContainers::contains).forEach { container ->
            container.addContainerListener(containerListener)
            observedContainers.add(container)
        }
    }

    private fun restorePendingFocus() {
        val focus = pendingFocus ?: return
        val adapter = orderedAdapters.getOrNull(focus.slot) ?: return
        pendingFocus = null
        adapter.restoreFocus(focus)
    }

    private fun activate(adapter: TextAreaAdapter) {
        if (activeAdapter === adapter) {
            return
        }
        if (activeAdapter != null || activeNativeField != null) {
            onFocusEnded()
        }
        activeAdapter = adapter
        activeNativeField = null
        onFocusStarted()
        onLanguageContextChanged(adapter.languageContext())
    }

    private fun activate(tracker: NativeFieldTracker) {
        if (activeNativeField === tracker) {
            return
        }
        if (activeAdapter != null || activeNativeField != null) {
            onFocusEnded()
        }
        activeAdapter = null
        activeNativeField = tracker
        onFocusStarted()
        onLanguageContextChanged(null)
    }

    private fun deactivate(adapter: TextAreaAdapter) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed || activeAdapter !== adapter) {
                return@invokeLater
            }
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, adapter.editor.component)) {
                activeAdapter = null
                onLanguageContextChanged(null)
                onFocusEnded()
            }
        }
    }

    private fun deactivate(tracker: NativeFieldTracker) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed || activeNativeField !== tracker) {
                return@invokeLater
            }
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, tracker.source)) {
                activeNativeField = null
                onFocusEnded()
            }
        }
    }

    private fun restoreNativeFocus(component: JTextComponent, focus: JMeterFieldFocus) {
        val textLength = component.document.length
        component.caretPosition = focus.caretOffset.coerceIn(0, textLength)
        component.select(
            focus.selectionStart.coerceIn(0, textLength),
            focus.selectionEnd.coerceIn(0, textLength),
        )
        component.requestFocusInWindow()
    }

    private fun dispatchHistoryShortcut(event: KeyEvent): Boolean {
        if (disposed || event.id != KeyEvent.KEY_PRESSED) {
            return false
        }
        val shortcutMask = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        if (event.modifiersEx and shortcutMask == 0 || event.modifiersEx and InputEvent.ALT_DOWN_MASK != 0) {
            return false
        }
        val redo = when {
            event.keyCode == KeyEvent.VK_Y -> true
            event.keyCode == KeyEvent.VK_Z && event.isShiftDown -> true
            event.keyCode == KeyEvent.VK_Z -> false
            else -> return false
        }
        val focus = activeAdapter?.let { adapter ->
            if (!SwingUtilities.isDescendingFrom(event.component, adapter.editor.component)) {
                return false
            }
            adapter.captureFocus()
        } ?: activeNativeField?.let { tracker ->
            if (!SwingUtilities.isDescendingFrom(event.component, tracker.source)) {
                return false
            }
            tracker.captureFocus()
        } ?: return false

        event.consume()
        onHistoryAction(redo, focus)
        return true
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        reconcileTimer.stop()
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
        observedRoots.forEach { it.removeHierarchyListener(hierarchyListener) }
        observedRoots.clear()
        observedContainers.forEach { it.removeContainerListener(containerListener) }
        observedContainers.clear()
        if (activeAdapter != null || activeNativeField != null) {
            activeAdapter = null
            activeNativeField = null
            onLanguageContextChanged(null)
            onFocusEnded()
        }
        adapters.values.toList().forEach(TextAreaAdapter::dispose)
        adapters.clear()
        nativeFields.values.toList().forEach(NativeFieldTracker::dispose)
        nativeFields.clear()
        orderedAdapters = emptyList()
    }

    private inner class NativeFieldTracker(
        val source: JTextComponent,
    ) {
        private val document = source.document
        private var released = false
        private val focusListener = object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) = activate(this@NativeFieldTracker)

            override fun focusLost(event: FocusEvent) = deactivate(this@NativeFieldTracker)
        }
        private val documentListener = object : SwingDocumentListener {
            override fun insertUpdate(event: SwingDocumentEvent) = changed()

            override fun removeUpdate(event: SwingDocumentEvent) = changed()

            override fun changedUpdate(event: SwingDocumentEvent) = changed()

            private fun changed() {
                if (!released) {
                    onFieldChanged()
                }
            }
        }

        init {
            source.addFocusListener(focusListener)
            document.addDocumentListener(documentListener)
            source.actionMap.remove("undo")
            source.actionMap.remove("redo")
        }

        fun captureFocus(): JMeterFieldFocus = JMeterFieldFocus(
            slot = -1,
            caretOffset = source.caretPosition,
            selectionStart = source.selectionStart,
            selectionEnd = source.selectionEnd,
            nativeComponent = source,
        )

        fun dispose() {
            if (released) {
                return
            }
            released = true
            if (activeNativeField === this) {
                activeNativeField = null
                onFocusEnded()
            }
            source.removeFocusListener(focusListener)
            document.removeDocumentListener(documentListener)
            nativeFields.remove(source)
        }
    }

    private inner class TextAreaAdapter(
        private val source: JTextArea,
    ) {
        val editor: Editor
        var slot: Int = -1
        private val viewport = source.parent as JViewport
        private val outerScrollPane = viewport.parent as? JScrollPane
        private val syntaxStyle = source.javaClass.methods
            .firstOrNull { it.name == "getSyntaxEditingStyle" && it.parameterCount == 0 }
            ?.let { method -> runCatching { method.invoke(source) as? String }.getOrNull() }
        private var language = JMeterSyntaxLanguage.resolve(
            syntaxStyle,
            source.getClientProperty(LANGUAGE_OVERRIDE_PROPERTY) as? String,
        )
        private val ideDocument = createLanguageDocument(language.fileType)
        private val discardAllEditsMethod = source.javaClass.methods
            .firstOrNull { it.name == "discardAllEdits" && it.parameterCount == 0 }
        private var syncing = false
        private var released = false
        private val editorHost: JComponent
        private var editorScrollPane: JScrollPane? = null
        private val oldVerticalPolicy = outerScrollPane?.verticalScrollBarPolicy
        private val oldHorizontalPolicy = outerScrollPane?.horizontalScrollBarPolicy
        private val wheelForwarder = MouseWheelListener(::forwardWheelAtBoundary)

        private val ideDocumentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!syncing) {
                    syncToSwing()
                    onFieldChanged()
                }
            }
        }
        private val swingDocumentListener = object : SwingDocumentListener {
            override fun insertUpdate(event: SwingDocumentEvent) = syncFromSwingLater()

            override fun removeUpdate(event: SwingDocumentEvent) = syncFromSwingLater()

            override fun changedUpdate(event: SwingDocumentEvent) = syncFromSwingLater()
        }

        init {
            UndoUtil.disableUndoFor(ideDocument)
            editor = editorFactory.createEditor(
                ideDocument,
                project,
                language.fileType,
                !source.isEditable,
            )
            editor.settings.apply {
                isLineNumbersShown = false
                isFoldingOutlineShown = false
                additionalColumnsCount = 0
                additionalLinesCount = 0
                isUseSoftWraps = source.lineWrap
            }
            editorHost = ViewportEditorHost(editor.component).apply {
                putClientProperty(ADAPTER_PROPERTY, this@TextAreaAdapter)
            }
            ideDocument.addDocumentListener(ideDocumentListener)
            source.document.addDocumentListener(swingDocumentListener)
            source.actionMap.remove("undo")
            source.actionMap.remove("redo")
            editor.contentComponent.addFocusListener(object : FocusAdapter() {
                override fun focusGained(event: FocusEvent) = activate(this@TextAreaAdapter)

                override fun focusLost(event: FocusEvent) = deactivate(this@TextAreaAdapter)
            })
            outerScrollPane?.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            outerScrollPane?.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            viewport.view = editorHost
            editorScrollPane = SwingUtilities.getAncestorOfClass(
                JScrollPane::class.java,
                editor.contentComponent,
            ) as? JScrollPane
            editorScrollPane?.addMouseWheelListener(wheelForwarder)
            discardNativeHistory()
        }

        fun captureFocus(): JMeterFieldFocus {
            val selection = editor.selectionModel
            return JMeterFieldFocus(
                slot = slot,
                caretOffset = editor.caretModel.offset,
                selectionStart = selection.selectionStart,
                selectionEnd = selection.selectionEnd,
            )
        }

        fun restoreFocus(focus: JMeterFieldFocus) {
            val textLength = ideDocument.textLength
            val start = focus.selectionStart.coerceIn(0, textLength)
            val end = focus.selectionEnd.coerceIn(0, textLength)
            editor.caretModel.moveToOffset(focus.caretOffset.coerceIn(0, textLength))
            editor.selectionModel.setSelection(start, end)
            editor.contentComponent.requestFocusInWindow()
        }

        fun languageContext(): JMeterLanguageContext = JMeterLanguageContext(
            languages = JMeterSyntaxLanguage.installedLanguages(),
            selected = language,
            selectLanguage = ::selectLanguage,
            reformat = ::reformat,
        )

        private fun selectLanguage(selected: JMeterEditorLanguage) {
            if (released || selected.fileType == language.fileType) {
                return
            }
            language = selected
            source.putClientProperty(LANGUAGE_OVERRIDE_PROPERTY, selected.extension)
            (editor as? EditorEx)?.highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(project, selected.fileType)
            onLanguageContextChanged(languageContext())
        }

        private fun reformat() {
            if (released || !source.isEditable) {
                return
            }
            val temporaryFile = ReadAction.compute<com.intellij.psi.PsiFile, RuntimeException> {
                PsiFileFactory.getInstance(project).createFileFromText(
                    "jmeter-field.${language.extension}",
                    language.fileType,
                    ideDocument.immutableCharSequence,
                )
            }
            ApplicationManager.getApplication().runWriteAction {
                CodeStyleManager.getInstance(project).reformat(temporaryFile)
            }
            val formatted = ReadAction.compute<String, RuntimeException> { temporaryFile.text }
            if (formatted != ideDocument.text) {
                ApplicationManager.getApplication().runWriteAction {
                    ideDocument.setText(formatted)
                }
            }
        }

        private fun createLanguageDocument(fileType: FileType) =
            ReadAction.compute<com.intellij.openapi.editor.Document?, RuntimeException> {
                PsiFileFactory.getInstance(project)
                    .createFileFromText("jmeter-field.${language.extension}", fileType, source.text)
                    .let { psiFile -> PsiDocumentManager.getInstance(project).getDocument(psiFile) }
            } ?: editorFactory.createDocument(source.text)

        private fun syncToSwing() {
            val replacement = TextReplacement.between(source.text, ideDocument.immutableCharSequence)
                ?: return
            syncing = true
            try {
                val document = source.document
                if (replacement.oldLength > 0) {
                    document.remove(replacement.offset, replacement.oldLength)
                }
                if (replacement.newText.isNotEmpty()) {
                    document.insertString(replacement.offset, replacement.newText, null)
                }
                discardNativeHistory()
            } catch (failure: BadLocationException) {
                throw IllegalStateException("Unable to synchronize a JMeter text area", failure)
            } finally {
                syncing = false
            }
        }

        private fun syncFromSwingLater() {
            if (syncing || released) {
                return
            }
            ApplicationManager.getApplication().invokeLater {
                if (!released && !syncing) {
                    syncFromSwing()
                }
            }
        }

        private fun syncFromSwing() {
            val replacement = TextReplacement.between(ideDocument.immutableCharSequence, source.text)
                ?: return
            val oldCaret = editor.caretModel.offset
            val oldSelectionStart = editor.selectionModel.selectionStart
            val oldSelectionEnd = editor.selectionModel.selectionEnd
            syncing = true
            try {
                ApplicationManager.getApplication().runWriteAction(Runnable {
                    ideDocument.replaceString(
                        replacement.offset,
                        replacement.offset + replacement.oldLength,
                        replacement.newText,
                    )
                })
                val textLength = ideDocument.textLength
                editor.caretModel.moveToOffset(oldCaret.coerceIn(0, textLength))
                editor.selectionModel.setSelection(
                    oldSelectionStart.coerceIn(0, textLength),
                    oldSelectionEnd.coerceIn(0, textLength),
                )
                discardNativeHistory()
            } finally {
                syncing = false
            }
        }

        private fun discardNativeHistory() {
            try {
                discardAllEditsMethod?.invoke(source)
            } catch (_: ReflectiveOperationException) {
                // Some third-party text components do not expose an undo queue.
            }
        }

        private fun forwardWheelAtBoundary(event: MouseWheelEvent) {
            if (event.isConsumed || event.wheelRotation == 0 || event.isShiftDown) {
                return
            }
            val editorPane = editorScrollPane ?: return
            val model = editorPane.verticalScrollBar.model
            val atStart = event.wheelRotation < 0 && model.value <= model.minimum
            val atEnd = event.wheelRotation > 0 && model.value + model.extent >= model.maximum
            if (!atStart && !atEnd) {
                return
            }
            val ancestor = ancestorScrollPane() ?: return
            val point = SwingUtilities.convertPoint(event.component, event.point, ancestor)
            ancestor.dispatchEvent(
                MouseWheelEvent(
                    ancestor,
                    event.id,
                    event.`when`,
                    event.modifiersEx,
                    point.x,
                    point.y,
                    event.xOnScreen,
                    event.yOnScreen,
                    event.clickCount,
                    event.isPopupTrigger,
                    event.scrollType,
                    event.scrollAmount,
                    event.wheelRotation,
                    event.preciseWheelRotation,
                ),
            )
            event.consume()
        }

        private fun ancestorScrollPane(): JScrollPane? {
            var current: Component? = outerScrollPane?.parent
            while (current != null) {
                if (current is JScrollPane && current !== outerScrollPane && current !== editorScrollPane) {
                    return current
                }
                current = current.parent
            }
            return null
        }

        fun dispose() {
            if (released) {
                return
            }
            released = true
            if (activeAdapter === this) {
                activeAdapter = null
                onLanguageContextChanged(null)
                onFocusEnded()
            }
            syncToSwing()
            editorScrollPane?.removeMouseWheelListener(wheelForwarder)
            source.document.removeDocumentListener(swingDocumentListener)
            ideDocument.removeDocumentListener(ideDocumentListener)
            if (viewport.view === editorHost) {
                viewport.view = source
            }
            if (oldVerticalPolicy != null) {
                outerScrollPane?.verticalScrollBarPolicy = oldVerticalPolicy
            }
            if (oldHorizontalPolicy != null) {
                outerScrollPane?.horizontalScrollBarPolicy = oldHorizontalPolicy
            }
            editorHost.putClientProperty(ADAPTER_PROPERTY, null)
            editorFactory.releaseEditor(editor)
            adapters.remove(source)
            discardNativeHistory()
        }
    }

    private companion object {
        const val ADAPTER_PROPERTY = "jmeter.intellij.text.area.adapter"
        const val LANGUAGE_OVERRIDE_PROPERTY = "jmeter.intellij.text.area.language"

        fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    }
}

private class ViewportEditorHost(component: JComponent) : JPanel(java.awt.BorderLayout()), Scrollable {
    init {
        add(component, java.awt.BorderLayout.CENTER)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = if (orientation == SwingConstants.VERTICAL) 16 else 8

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = if (orientation == SwingConstants.VERTICAL) {
        visibleRect.height.coerceAtLeast(16)
    } else {
        visibleRect.width.coerceAtLeast(8)
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = true
}
