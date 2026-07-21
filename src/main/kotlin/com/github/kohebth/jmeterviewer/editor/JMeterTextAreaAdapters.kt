package com.github.kohebth.jmeterviewer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.JViewport
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

/**
 * Replaces visible persistent JMeter text areas with short-lived IntelliJ
 * editors. Their documents have undo disabled; the backing JMX document owns
 * the only history retained by the IDE.
 */
internal class JMeterTextAreaAdapters(
    private val project: Project,
    private val roots: () -> Collection<JComponent>,
    private val onFocusStarted: () -> Unit,
    private val onFocusEnded: () -> Unit,
    private val onFieldChanged: () -> Unit,
    private val onHistoryAction: (redo: Boolean, focus: JMeterFieldFocus) -> Unit,
) : Disposable {
    private val editorFactory = EditorFactory.getInstance()
    private val adapters = IdentityHashMap<JTextComponent, TextAreaAdapter>()
    private val nativeFields = IdentityHashMap<JTextComponent, NativeFieldTracker>()
    private val scanTimer = Timer(SCAN_INTERVAL_MS) { scan() }
    private var orderedAdapters: List<TextAreaAdapter> = emptyList()
    private var activeAdapter: TextAreaAdapter? = null
    private var activeNativeField: NativeFieldTracker? = null
    private var pendingFocus: JMeterFieldFocus? = null
    private var disposed = false

    private val keyDispatcher = KeyEventDispatcher { event ->
        dispatchHistoryShortcut(event)
    }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
        scanTimer.isRepeats = true
        scanTimer.start()
        scan()
    }

    fun scanNow() {
        scan()
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
        roots().filter { it.isShowing }.forEach {
            collectAdapters(it, found, foundNativeFields)
        }
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

        if (component is JTextArea && JMeterTextAreaPolicy.canAdapt(component)) {
            val adapter = adapters[component] ?: TextAreaAdapter(component).also {
                adapters[component] = it
            }
            found.add(adapter)
            return
        }

        if (component is JTextComponent && JMeterTextAreaPolicy.canTrackHistory(component)) {
            val tracker = nativeFields[component] ?: NativeFieldTracker(component).also {
                nativeFields[component] = it
            }
            foundNativeFields.add(tracker)
            return
        }

        if (component is Container) {
            component.components.toList().forEach { child ->
                collectAdapters(child, found, foundNativeFields)
            }
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
    }

    private fun deactivate(adapter: TextAreaAdapter) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed || activeAdapter !== adapter) {
                return@invokeLater
            }
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, adapter.editor.component)) {
                activeAdapter = null
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
        scanTimer.stop()
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
        if (activeAdapter != null || activeNativeField != null) {
            activeAdapter = null
            activeNativeField = null
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
        private val ideDocument = editorFactory.createDocument(source.text)
        private val discardAllEditsMethod = source.javaClass.methods
            .firstOrNull { it.name == "discardAllEdits" && it.parameterCount == 0 }
        private var syncing = false
        private var released = false

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
            editor = if (source.isEditable) {
                editorFactory.createEditor(ideDocument, project)
            } else {
                editorFactory.createViewer(ideDocument, project)
            }
            editor.settings.apply {
                isLineNumbersShown = false
                isFoldingOutlineShown = false
                additionalColumnsCount = 0
                additionalLinesCount = 0
                isUseSoftWraps = source.lineWrap
            }
            editor.component.putClientProperty(ADAPTER_PROPERTY, this)
            ideDocument.addDocumentListener(ideDocumentListener)
            source.document.addDocumentListener(swingDocumentListener)
            source.actionMap.remove("undo")
            source.actionMap.remove("redo")
            editor.contentComponent.addFocusListener(object : FocusAdapter() {
                override fun focusGained(event: FocusEvent) = activate(this@TextAreaAdapter)

                override fun focusLost(event: FocusEvent) = deactivate(this@TextAreaAdapter)
            })
            viewport.view = editor.component
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

        fun dispose() {
            if (released) {
                return
            }
            released = true
            if (activeAdapter === this) {
                activeAdapter = null
                onFocusEnded()
            }
            syncToSwing()
            source.document.removeDocumentListener(swingDocumentListener)
            ideDocument.removeDocumentListener(ideDocumentListener)
            if (viewport.view === editor.component) {
                viewport.view = source
            }
            editor.component.putClientProperty(ADAPTER_PROPERTY, null)
            editorFactory.releaseEditor(editor)
            adapters.remove(source)
            discardNativeHistory()
        }
    }

    private companion object {
        const val ADAPTER_PROPERTY = "jmeter.intellij.text.area.adapter"
        const val SCAN_INTERVAL_MS = 250
    }
}
