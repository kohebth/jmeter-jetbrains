package com.github.kohebth.jmeterviewer.editor

import com.github.kohebth.jmeterviewer.ide.JMeterFileEditorProvider
import com.github.kohebth.jmeterviewer.ide.JMeterSettingsConfigurable
import com.github.kohebth.jmeterviewer.runtime.JMeterSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class JMeterVisualFileEditor(
    val project: Project,
    val virtualFile: VirtualFile,
) : FileEditor {
    private val userData = UserDataHolderBase()
    private val propertyChanges = PropertyChangeSupport(this)
    private val host = JBPanel<JBPanel<*>>(BorderLayout())
    private val workspaceService = ApplicationManager.getApplication()
        .getService(JMeterWorkspaceService::class.java)
    private val modifiedPoller = Timer(MODIFIED_POLL_INTERVAL_MS) { refreshModifiedState() }
    private var lastModified = false
    private var disposed = false

    val document: Document = checkNotNull(
        FileDocumentManager.getInstance().getDocument(virtualFile),
    ) { "JMeter visual editor requires a text Document for ${virtualFile.path}" }

    init {
        showStatus("Select this tab to load JMeter's native editor.")
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                refreshModifiedState()
            }
        }, this)
        modifiedPoller.isRepeats = true
    }

    internal fun attachNative(component: JComponent) {
        removeFromParent(component)
        host.removeAll()
        host.add(component, BorderLayout.CENTER)
        host.revalidate()
        host.repaint()
    }

    internal fun detachNative(component: JComponent) {
        if (component.parent === host) {
            host.remove(component)
            showStatus("JMeter session saved. Select this tab to continue editing.")
        }
    }

    internal fun showLoadError(message: String, offerConfiguration: Boolean = false) {
        host.removeAll()
        val content = JPanel(BorderLayout(0, 12))
        content.add(
            JBLabel("<html><b>Unable to open this test plan in JMeter.</b><br>${escapeHtml(message)}</html>"),
            BorderLayout.CENTER,
        )
        val actions = JPanel(FlowLayout(FlowLayout.LEADING, 8, 0))
        if (offerConfiguration) {
            actions.add(JButton("Configure JMeter").apply {
                addActionListener { configureJMeter() }
            })
        }
        actions.add(JButton("Retry").apply {
            addActionListener { workspaceService.retry(this@JMeterVisualFileEditor) }
        })
        actions.add(JButton("Open Text").apply {
            addActionListener { openTextEditor() }
        })
        content.add(actions, BorderLayout.SOUTH)
        host.add(content, BorderLayout.NORTH)
        host.revalidate()
        host.repaint()
    }

    internal fun showSwitchBlocked(
        message: String = "Another JMeter tab could not be saved. Resolve it before switching plans.",
    ) {
        showStatus(message)
    }

    internal fun requestReselect() {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed && virtualFile.isValid && !project.isDisposed) {
                FileEditorManager.getInstance(project).setSelectedEditor(
                    virtualFile,
                    JMeterFileEditorProvider.EDITOR_TYPE_ID,
                )
            }
        }
    }

    internal fun refreshModifiedState() {
        if (disposed) {
            return
        }
        val modified = workspaceService.isModified(this)
        if (modified != lastModified) {
            val previous = lastModified
            lastModified = modified
            propertyChanges.firePropertyChange(FileEditor.PROP_MODIFIED, previous, modified)
            FileEditorManager.getInstance(project).updateFilePresentation(virtualFile)
        }
    }

    private fun showStatus(text: String) {
        host.removeAll()
        host.add(JBLabel(text), BorderLayout.NORTH)
        host.revalidate()
        host.repaint()
    }

    private fun openTextEditor() {
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, virtualFile),
            true,
        )
    }

    private fun configureJMeter() {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            JMeterSettingsConfigurable::class.java,
        )
        val configuredHome = ApplicationManager.getApplication()
            .getService(JMeterSettings::class.java)
            .jmeterHome
        if (configuredHome.isNotEmpty()) {
            workspaceService.retry(this)
        }
    }

    private fun removeFromParent(component: Component) {
        val parent: Container = component.parent ?: return
        parent.remove(component)
        parent.revalidate()
        parent.repaint()
    }

    override fun getComponent(): JComponent = host

    override fun getPreferredFocusedComponent(): JComponent = host

    override fun getName(): String = "JMeter"

    override fun getFile(): VirtualFile = virtualFile

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = !disposed && workspaceService.isModified(this)

    override fun isValid(): Boolean = !disposed && virtualFile.isValid

    override fun selectNotify() {
        modifiedPoller.start()
        workspaceService.activate(this)
        refreshModifiedState()
    }

    override fun deselectNotify() {
        modifiedPoller.stop()
        workspaceService.deactivate(this)
        refreshModifiedState()
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChanges.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChanges.removePropertyChangeListener(listener)
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun <T : Any?> getUserData(key: Key<T>): T? = userData.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        userData.putUserData(key, value)
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        modifiedPoller.stop()
        workspaceService.unregister(this)
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")

    private companion object {
        const val MODIFIED_POLL_INTERVAL_MS = 400
    }
}
