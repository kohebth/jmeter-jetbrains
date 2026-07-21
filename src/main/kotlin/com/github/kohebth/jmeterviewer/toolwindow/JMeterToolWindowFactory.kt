package com.github.kohebth.jmeterviewer.toolwindow

import com.github.kohebth.jmeterviewer.runtime.JMeterWorkspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class JMeterToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.getService(JMeterToolWindowController::class.java).bind(toolWindow)
    }
}

@Service(Service.Level.PROJECT)
internal class JMeterToolWindowController : Disposable {
    private val outlineHost = TabHost(clearable = false)
    private val resultsHost = TabHost(clearable = true)
    private val aggregateHost = TabHost(clearable = true)
    private var toolWindow: ToolWindow? = null
    private var currentSessionId: String? = null
    private var clearResults: (() -> Unit)? = null
    private var disposed = false

    init {
        showEmpty()
    }

    fun bind(toolWindow: ToolWindow) {
        if (disposed || this.toolWindow === toolWindow) {
            return
        }
        this.toolWindow = toolWindow
        resultsHost.onClear = ::clearCurrentResults
        aggregateHost.onClear = ::clearCurrentResults

        val contentFactory = ContentFactory.SERVICE.getInstance()
        toolWindow.contentManager.addContent(
            contentFactory.createContent(outlineHost, TEST_PLAN_TAB, false),
        )
        toolWindow.contentManager.addContent(
            contentFactory.createContent(resultsHost, RESULTS_TREE_TAB, false),
        )
        toolWindow.contentManager.addContent(
            contentFactory.createContent(aggregateHost, AGGREGATE_REPORT_TAB, false),
        )
    }

    fun show(
        workspace: JMeterWorkspace,
        sessionId: String,
        displayName: String,
        onClearResults: () -> Unit,
    ) {
        if (disposed) {
            return
        }
        currentSessionId = sessionId
        clearResults = onClearResults
        outlineHost.mount(workspace.outlineComponent, "No test plan selected")
        resultsHost.mount(workspace.resultsTreeComponent(sessionId), "No results for $displayName")
        aggregateHost.mount(
            workspace.aggregateReportComponent(sessionId),
            "No aggregate report for $displayName",
        )
    }

    fun showEmpty() {
        if (disposed) {
            return
        }
        currentSessionId = null
        clearResults = null
        outlineHost.unmount("Open a JMX file to inspect its Test Plan")
        resultsHost.unmount("Run selected Thread Group(s) to collect results")
        aggregateHost.unmount("Run selected Thread Group(s) to collect aggregate metrics")
    }

    fun selectResultsTree() {
        val window = toolWindow ?: return
        val resultsContent = window.contentManager.findContent(RESULTS_TREE_TAB) ?: return
        window.contentManager.setSelectedContent(resultsContent)
        window.show()
    }

    fun visibleTextRoots(): List<JComponent> = listOfNotNull(
        resultsHost.mountedComponent,
        aggregateHost.mountedComponent,
    )

    private fun clearCurrentResults() {
        if (currentSessionId != null) {
            clearResults?.invoke()
        }
    }

    override fun dispose() {
        showEmpty()
        disposed = true
        toolWindow = null
    }

    private class TabHost(clearable: Boolean) : JPanel(BorderLayout()) {
        var mountedComponent: JComponent? = null
            private set
        var onClear: (() -> Unit)? = null

        init {
            if (clearable) {
                add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 2)).apply {
                    add(JButton("Clear Results").apply {
                        addActionListener { onClear?.invoke() }
                    })
                }, BorderLayout.NORTH)
            }
        }

        fun mount(component: JComponent, emptyText: String) {
            if (mountedComponent === component) {
                return
            }
            removeMountedComponent()
            removeFromParent(component)
            mountedComponent = component
            add(component, BorderLayout.CENTER)
            toolTipText = emptyText
            revalidate()
            repaint()
        }

        fun unmount(message: String) {
            removeMountedComponent()
            add(JLabel(message), BorderLayout.CENTER)
            revalidate()
            repaint()
        }

        private fun removeMountedComponent() {
            mountedComponent?.let(::remove)
            mountedComponent = null
            components.filterIsInstance<JLabel>().forEach(::remove)
        }

        private fun removeFromParent(component: Component) {
            val parent: Container = component.parent ?: return
            parent.remove(component)
            parent.revalidate()
            parent.repaint()
        }
    }

    private companion object {
        const val TEST_PLAN_TAB = "Test Plan"
        const val RESULTS_TREE_TAB = "Results Tree"
        const val AGGREGATE_REPORT_TAB = "Aggregate Report"
    }
}
