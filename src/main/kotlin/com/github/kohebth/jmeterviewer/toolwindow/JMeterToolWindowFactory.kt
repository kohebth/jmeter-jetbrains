package com.github.kohebth.jmeterviewer.toolwindow

import com.github.kohebth.jmeterviewer.editor.JMeterEditorFeatures
import com.github.kohebth.jmeterviewer.runtime.JMeterWorkspace
import com.github.kohebth.jmeterviewer.runtime.JMeterReplaceResult
import com.github.kohebth.jmeterviewer.runtime.JMeterSearchMatch
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import java.awt.event.InputEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class JMeterToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.getService(JMeterToolWindowController::class.java).bind(toolWindow)
    }
}

@Service(Service.Level.PROJECT)
internal class JMeterToolWindowController : Disposable {
    private val outlineHost = TestPlanHost()
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
        onReplace: (
            matches: List<JMeterSearchMatch>,
            query: String,
            replacement: String,
            caseSensitive: Boolean,
            regexp: Boolean,
        ) -> JMeterReplaceResult,
        onHistoryAction: (redo: Boolean) -> Unit,
    ) {
        if (disposed) {
            return
        }
        currentSessionId = sessionId
        clearResults = onClearResults
        outlineHost.mount(workspace.outlineComponent, workspace, onReplace, onHistoryAction)
        resultsHost.mount(workspace.resultsTreeComponent(sessionId), "No results for $displayName")
        aggregateHost.mount(
            workspace.aggregateReportComponent(sessionId),
            "No aggregate report for $displayName",
        )
        selectTestPlan()
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

    fun selectResultsTree(showWindow: Boolean = true) {
        val window = toolWindow ?: return
        val resultsContent = window.contentManager.findContent(RESULTS_TREE_TAB) ?: return
        window.contentManager.setSelectedContent(resultsContent)
        if (showWindow) {
            window.show()
        }
    }

    fun selectTestPlan() {
        val window = toolWindow ?: return
        val testPlanContent = window.contentManager.findContent(TEST_PLAN_TAB) ?: return
        window.contentManager.setSelectedContent(testPlanContent)
        window.show()
    }

    fun resetTestPlanSearch() {
        outlineHost.clearSearch()
    }

    fun showSearch() {
        selectTestPlan()
        outlineHost.showSearch()
    }

    fun visibleTextRoots(): List<JComponent> = listOfNotNull(
        resultsHost.mountedComponent,
        aggregateHost.mountedComponent,
    )

    fun shortcutRoots(): List<JComponent> = toolWindow?.component?.let(::listOf)
        ?: listOf(outlineHost, resultsHost, aggregateHost)

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

    private class TestPlanHost : JPanel(BorderLayout()) {
        private val searchToggle = JButton("Search")
        private val searchPanel = SearchPanel(
            jetbrainsHistoryEnabled = JMeterEditorFeatures.JETBRAINS_VISUAL_UNDO_ENABLED,
        )
        private var splitPane: JSplitPane? = null
        private var mountedComponent: JComponent? = null
        private var workspace: JMeterWorkspace? = null
        private var onReplace: ((List<JMeterSearchMatch>, String, String, Boolean, Boolean) -> JMeterReplaceResult)? = null

        init {
            add(JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply {
                add(searchToggle)
            }, BorderLayout.NORTH)
            searchToggle.addActionListener { setSearchVisible(!searchPanel.isVisible) }
            searchPanel.onSearch = ::search
            searchPanel.onReset = ::resetSearch
            searchPanel.onSelect = { match -> workspace?.selectSearchResult(match.path) }
            searchPanel.onReplace = ::replace
            val shortcutMask = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
            val shortcutKey = KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask)
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcutKey, "jmeter.search")
            actionMap.put("jmeter.search", object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    setSearchVisible(true)
                    searchPanel.focusSearch()
                }
            })
            unmount("Open a JMX file to inspect its Test Plan")
        }

        fun mount(
            component: JComponent,
            workspace: JMeterWorkspace,
            onReplace: (List<JMeterSearchMatch>, String, String, Boolean, Boolean) -> JMeterReplaceResult,
            onHistoryAction: (Boolean) -> Unit,
        ) {
            this.workspace?.resetSearch()
            removeCenter()
            removeFromParent(component)
            mountedComponent = component
            this.workspace = workspace
            this.onReplace = onReplace
            searchPanel.onHistoryAction = onHistoryAction
            searchPanel.clear()
            val treeHost = JPanel(BorderLayout()).apply { add(component, BorderLayout.CENTER) }
            splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeHost, searchPanel).apply {
                resizeWeight = 0.68
                isContinuousLayout = true
                border = null
            }
            add(splitPane, BorderLayout.CENTER)
            setSearchVisible(JMeterEditorFeatures.SEARCH_VISIBLE_ON_MOUNT)
            revalidate()
            repaint()
        }

        fun unmount(message: String) {
            workspace?.resetSearch()
            workspace = null
            onReplace = null
            searchPanel.onHistoryAction = null
            mountedComponent = null
            searchPanel.clear()
            removeCenter()
            add(JLabel(message), BorderLayout.CENTER)
            searchToggle.isEnabled = false
            revalidate()
            repaint()
        }

        fun clearSearch() {
            searchPanel.clear()
        }

        fun showSearch() {
            setSearchVisible(true)
            searchPanel.focusSearch()
        }

        private fun search(query: String, caseSensitive: Boolean, regexp: Boolean) =
            workspace?.searchTestPlan(query, caseSensitive, regexp).orEmpty()

        private fun resetSearch() {
            workspace?.resetSearch()
        }

        private fun replace(
            matches: List<JMeterSearchMatch>,
            query: String,
            replacement: String,
            caseSensitive: Boolean,
            regexp: Boolean,
        ): JMeterReplaceResult = onReplace?.invoke(
            matches,
            query,
            replacement,
            caseSensitive,
            regexp,
        ) ?: JMeterReplaceResult(0, 0, 0)

        private fun setSearchVisible(visible: Boolean) {
            val split = splitPane ?: return
            searchToggle.isEnabled = true
            if (visible) {
                if (split.rightComponent !== searchPanel) {
                    split.rightComponent = searchPanel
                }
                searchPanel.isVisible = true
                split.dividerSize = 8
                split.setDividerLocation(0.68)
                searchToggle.text = "Hide Search"
            } else {
                searchPanel.isVisible = false
                split.rightComponent = null
                split.dividerSize = 0
                searchToggle.text = "Search"
            }
            revalidate()
            repaint()
        }

        private fun removeCenter() {
            splitPane?.let(::remove)
            splitPane = null
            components.filterIsInstance<JLabel>().forEach(::remove)
        }

        private fun removeFromParent(component: Component) {
            val parent: Container = component.parent ?: return
            parent.remove(component)
            parent.revalidate()
            parent.repaint()
        }
    }

    private class SearchPanel(
        private val jetbrainsHistoryEnabled: Boolean,
    ) : JPanel(BorderLayout(6, 6)) {
        var onSearch: ((String, Boolean, Boolean) -> List<JMeterSearchMatch>)? = null
        var onReset: (() -> Unit)? = null
        var onSelect: ((JMeterSearchMatch) -> Unit)? = null
        var onReplace: ((List<JMeterSearchMatch>, String, String, Boolean, Boolean) -> JMeterReplaceResult)? = null
        var onHistoryAction: ((Boolean) -> Unit)? = null

        private val searchField = JTextField()
        private val replacementField = JTextField()
        private val caseSensitive = JCheckBox("Case sensitive", true)
        private val regexp = JCheckBox("Regular expression", false)
        private val resultModel = DefaultListModel<JMeterSearchMatch>()
        private val results = JList(resultModel)
        private val replaceButton = JButton("Replace")
        private val replaceAllButton = JButton("Replace All")
        private val status = JLabel("Enter text to search the test plan")
        private val debounceTimer = Timer(250) { performSearch() }

        init {
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            debounceTimer.isRepeats = false
            val fields = JPanel(java.awt.GridLayout(0, 1, 4, 4)).apply {
                add(JLabel("Search"))
                add(searchField)
                add(JLabel("Replace with"))
                add(replacementField)
                add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply {
                    add(caseSensitive)
                    add(regexp)
                })
            }
            add(fields, BorderLayout.NORTH)

            results.selectionMode = ListSelectionModel.SINGLE_SELECTION
            results.addListSelectionListener { event ->
                if (!event.valueIsAdjusting) {
                    results.selectedValue?.let { onSelect?.invoke(it) }
                    updateButtons()
                }
            }
            add(JScrollPane(results), BorderLayout.CENTER)

            add(JPanel(BorderLayout(4, 4)).apply {
                add(status, BorderLayout.NORTH)
                add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply {
                    add(replaceButton)
                    add(replaceAllButton)
                    add(JButton("Reset").apply { addActionListener { clear() } })
                }, BorderLayout.SOUTH)
            }, BorderLayout.SOUTH)

            val searchChanges = object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = scheduleSearch()
                override fun removeUpdate(event: DocumentEvent) = scheduleSearch()
                override fun changedUpdate(event: DocumentEvent) = scheduleSearch()
            }
            searchField.document.addDocumentListener(searchChanges)
            caseSensitive.addActionListener { scheduleSearch() }
            regexp.addActionListener { scheduleSearch() }
            searchField.addActionListener { performSearch() }
            replaceButton.addActionListener {
                results.selectedValue?.let { replace(listOf(it)) }
            }
            replaceAllButton.addActionListener { replace(modelValues()) }
            if (jetbrainsHistoryEnabled) {
                val shortcutMask = if (SystemInfo.isMac) {
                    InputEvent.META_DOWN_MASK
                } else {
                    InputEvent.CTRL_DOWN_MASK
                }
                installHistoryShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask), false)
                installHistoryShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcutMask), true)
                installHistoryShortcut(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask or InputEvent.SHIFT_DOWN_MASK),
                    true,
                )
            }
            results.cellRenderer = javax.swing.ListCellRenderer { list, value, index, selected, focused ->
                val label = DefaultListCellRenderer().getListCellRendererComponent(
                    list,
                    value,
                    index,
                    selected,
                    focused,
                ) as JLabel
                label.text = "<html><b>${escape(value.name)}</b> &nbsp; ${escape(value.type)}" +
                    "<br><font color='gray'>${escape(value.breadcrumb)}</font></html>"
                label.toolTipText = if (value.replaceable) {
                    "Replacement is supported by this JMeter element"
                } else {
                    "Searchable; replacement is not supported by this JMeter element"
                }
                label
            }
            updateButtons()
        }

        fun focusSearch() {
            searchField.requestFocusInWindow()
            searchField.selectAll()
        }

        fun clear() {
            debounceTimer.stop()
            searchField.text = ""
            replacementField.text = ""
            resultModel.clear()
            status.text = "Enter text to search the test plan"
            onReset?.invoke()
            updateButtons()
        }

        private fun scheduleSearch() {
            debounceTimer.restart()
        }

        private fun performSearch(statusOverride: String? = null) {
            debounceTimer.stop()
            val query = searchField.text
            if (query.isEmpty()) {
                resultModel.clear()
                onReset?.invoke()
                status.text = statusOverride ?: "Enter text to search the test plan"
                updateButtons()
                return
            }
            try {
                val matches = onSearch?.invoke(query, caseSensitive.isSelected, regexp.isSelected).orEmpty()
                resultModel.clear()
                matches.forEach(resultModel::addElement)
                status.text = statusOverride
                    ?: "${matches.size} matches; ${matches.count(JMeterSearchMatch::replaceable)} replaceable"
                if (matches.isNotEmpty()) {
                    results.selectedIndex = 0
                }
            } catch (failure: Throwable) {
                resultModel.clear()
                status.text = rootMessage(failure)
            }
            updateButtons()
        }

        private fun replace(matches: List<JMeterSearchMatch>) {
            if (matches.isEmpty()) {
                return
            }
            try {
                val result = onReplace?.invoke(
                    matches,
                    searchField.text,
                    replacementField.text,
                    caseSensitive.isSelected,
                    regexp.isSelected,
                ) ?: return
                val message = "Replaced ${result.occurrences} occurrences in " +
                    "${result.supportedNodes} supported elements; skipped ${result.skippedNodes} unsupported"
                performSearch(message)
            } catch (failure: Throwable) {
                status.text = rootMessage(failure)
            }
        }

        private fun updateButtons() {
            replaceButton.isEnabled = results.selectedValue?.replaceable == true
            replaceAllButton.isEnabled = modelValues().any(JMeterSearchMatch::replaceable)
        }

        private fun modelValues(): List<JMeterSearchMatch> =
            (0 until resultModel.size()).map(resultModel::getElementAt)

        private fun installHistoryShortcut(shortcut: KeyStroke, redo: Boolean) {
            val actionKey = if (redo) "jmeter.search.redo.$shortcut" else "jmeter.search.undo.$shortcut"
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut, actionKey)
            actionMap.put(actionKey, object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    onHistoryAction?.invoke(redo)
                }
            })
        }

        private fun rootMessage(failure: Throwable): String {
            var root = failure
            while (root.cause != null && root.cause !== root) {
                root = root.cause!!
            }
            return root.message ?: root.javaClass.simpleName
        }

        private fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
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
