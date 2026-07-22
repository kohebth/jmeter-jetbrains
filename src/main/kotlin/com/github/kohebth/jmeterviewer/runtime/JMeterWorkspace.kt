package com.github.kohebth.jmeterviewer.runtime

import java.awt.Component
import java.awt.event.ActionListener
import java.io.InputStream
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.tree.TreePath

internal data class JMeterSearchMatch(
    val path: TreePath,
    val name: String,
    val type: String,
    val breadcrumb: String,
    val replaceable: Boolean,
)

internal data class JMeterReplaceResult(
    val occurrences: Int,
    val supportedNodes: Int,
    val skippedNodes: Int,
)

internal data class JMeterNativeShortcut(
    val keyCode: Int,
    val modifiers: Int,
    val command: String,
    val argument: String?,
)

internal interface JMeterWorkspace : AutoCloseable {
    val isClosed: Boolean

    val component: JComponent

    val outlineComponent: JComponent

    fun searchTestPlan(query: String, caseSensitive: Boolean, regexp: Boolean): List<JMeterSearchMatch>

    fun resetSearch()

    fun selectSearchResult(path: TreePath)

    fun replaceSearchResults(
        matches: List<JMeterSearchMatch>,
        query: String,
        replacement: String,
        caseSensitive: Boolean,
        regexp: Boolean,
    ): JMeterReplaceResult

    fun resultsTreeComponent(sessionId: String): JComponent

    fun aggregateReportComponent(sessionId: String): JComponent

    fun setDialogParent(parent: Component?)

    fun load(input: InputStream, sourcePath: Path)

    fun reloadFromHistory(input: InputStream, sourcePath: Path)

    fun snapshot(): ByteArray

    fun snapshotSelectedThreadGroups(
        actionCommand: String,
        port: Int,
        token: String,
        journalPath: Path,
    ): ByteArray?

    fun appendSampleResult(sessionId: String, xmlFragment: ByteArray)

    val isDirty: Boolean

    fun markSaved()

    fun setModelChangeListener(listener: Runnable?)

    fun setExecutionActionListener(listener: ActionListener?)

    val shortcutDescriptors: List<JMeterNativeShortcut>

    fun performAction(command: String, argument: String? = null)

    fun exportSelectedNodes(): ByteArray?

    fun importNodes(portableJmx: ByteArray): Int

    val canRunSelectedThreadGroups: Boolean

    fun startSelectedThreadGroups(sessionId: String): Boolean

    fun shutdownTest()

    fun stopTest()

    val isTestRunning: Boolean

    fun clearResults(sessionId: String)

    fun discardResults(sessionId: String)
}

internal class ReflectiveJMeterWorkspace(
    private val runtime: JMeterRuntime,
    workspaceClass: Class<*>,
    private val delegate: Any,
) : JMeterWorkspace {
    @Volatile
    private var closed = false
    private val getComponent = workspaceClass.getMethod("getComponent")
    private val getOutlineComponent = workspaceClass.getMethod("getOutlineComponent")
    private val searchTestPlanMethod = workspaceClass.getMethod(
        "searchTestPlan",
        String::class.java,
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
    )
    private val resetSearchMethod = workspaceClass.getMethod("resetSearch")
    private val selectSearchResultMethod = workspaceClass.getMethod(
        "selectSearchResult",
        TreePath::class.java,
    )
    private val replaceSearchResultsMethod = workspaceClass.getMethod(
        "replaceSearchResults",
        emptyArray<TreePath>().javaClass,
        String::class.java,
        String::class.java,
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
    )
    private val getResultsTreeComponent = workspaceClass.getMethod(
        "getResultsTreeComponent",
        String::class.java,
    )
    private val getAggregateReportComponent = workspaceClass.getMethod(
        "getAggregateReportComponent",
        String::class.java,
    )
    private val setDialogParent = workspaceClass.getMethod("setDialogParent", Component::class.java)
    private val load = workspaceClass.getMethod("load", InputStream::class.java, Path::class.java)
    private val reloadFromHistoryMethod = workspaceClass.getMethod(
        "reloadFromHistory",
        InputStream::class.java,
        Path::class.java,
    )
    private val snapshot = workspaceClass.getMethod("snapshot")
    private val snapshotSelectedThreadGroupsMethod = workspaceClass.getMethod(
        "snapshotSelectedThreadGroups",
        String::class.java,
        Int::class.javaPrimitiveType,
        String::class.java,
        Path::class.java,
    )
    private val appendSampleResultMethod = workspaceClass.getMethod(
        "appendSampleResult",
        String::class.java,
        ByteArray::class.java,
    )
    private val getDirty = workspaceClass.getMethod("isDirty")
    private val markSaved = workspaceClass.getMethod("markSaved")
    private val setModelChangeListenerMethod = workspaceClass.getMethod(
        "setModelChangeListener",
        Runnable::class.java,
    )
    private val setExecutionActionListenerMethod = workspaceClass.getMethod(
        "setExecutionActionListener",
        ActionListener::class.java,
    )
    private val getShortcutDescriptorsMethod = workspaceClass.getMethod("getShortcutDescriptors")
    private val performActionMethod = workspaceClass.getMethod(
        "performAction",
        String::class.java,
        String::class.java,
    )
    private val exportSelectedNodesMethod = workspaceClass.getMethod("exportSelectedNodes")
    private val importNodesMethod = workspaceClass.getMethod("importNodes", ByteArray::class.java)
    private val canRunSelectedThreadGroupsMethod = workspaceClass.getMethod("canRunSelectedThreadGroups")
    private val startSelectedThreadGroupsMethod = workspaceClass.getMethod(
        "startSelectedThreadGroups",
        String::class.java,
    )
    private val shutdownTestMethod = workspaceClass.getMethod("shutdownTest")
    private val stopTestMethod = workspaceClass.getMethod("stopTest")
    private val getTestRunning = workspaceClass.getMethod("isTestRunning")
    private val clearResultsMethod = workspaceClass.getMethod("clearResults", String::class.java)
    private val discardResultsMethod = workspaceClass.getMethod("discardResults", String::class.java)
    private val close = workspaceClass.getMethod("close")

    override val isClosed: Boolean
        get() = closed

    override val component: JComponent
        get() = call(getComponent) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible editor component")

    override val outlineComponent: JComponent
        get() = call(getOutlineComponent) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible outline component")

    override fun searchTestPlan(
        query: String,
        caseSensitive: Boolean,
        regexp: Boolean,
    ): List<JMeterSearchMatch> {
        val rawResults = call(searchTestPlanMethod, query, caseSensitive, regexp) as? List<*>
            ?: throw JMeterRuntimeException("JMeter returned incompatible search results")
        return rawResults.map { rawResult ->
            val values = rawResult as? Map<*, *>
                ?: throw JMeterRuntimeException("JMeter returned an incompatible search result")
            JMeterSearchMatch(
                path = values["path"] as? TreePath
                    ?: throw JMeterRuntimeException("A JMeter search result has no tree path"),
                name = values["name"] as? String ?: "",
                type = values["type"] as? String ?: "",
                breadcrumb = values["breadcrumb"] as? String ?: "",
                replaceable = values["replaceable"] as? Boolean ?: false,
            )
        }
    }

    override fun resetSearch() {
        call(resetSearchMethod)
    }

    override fun selectSearchResult(path: TreePath) {
        call(selectSearchResultMethod, path)
    }

    override fun replaceSearchResults(
        matches: List<JMeterSearchMatch>,
        query: String,
        replacement: String,
        caseSensitive: Boolean,
        regexp: Boolean,
    ): JMeterReplaceResult {
        val counts = call(
            replaceSearchResultsMethod,
            matches.map(JMeterSearchMatch::path).toTypedArray(),
            query,
            replacement,
            caseSensitive,
            regexp,
        ) as? IntArray ?: throw JMeterRuntimeException("JMeter returned incompatible replacement totals")
        if (counts.size != 3) {
            throw JMeterRuntimeException("JMeter returned incomplete replacement totals")
        }
        return JMeterReplaceResult(counts[0], counts[1], counts[2])
    }

    override fun resultsTreeComponent(sessionId: String): JComponent =
        call(getResultsTreeComponent, sessionId) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible Results Tree")

    override fun aggregateReportComponent(sessionId: String): JComponent =
        call(getAggregateReportComponent, sessionId) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible Aggregate Report")

    override fun setDialogParent(parent: Component?) {
        call(setDialogParent, parent)
    }

    override fun load(input: InputStream, sourcePath: Path) {
        call(load, input, sourcePath)
    }

    override fun reloadFromHistory(input: InputStream, sourcePath: Path) {
        call(reloadFromHistoryMethod, input, sourcePath)
    }

    override fun snapshot(): ByteArray = call(snapshot) as? ByteArray
        ?: throw JMeterRuntimeException("JMeter returned an incompatible JMX snapshot")

    override fun snapshotSelectedThreadGroups(
        actionCommand: String,
        port: Int,
        token: String,
        journalPath: Path,
    ): ByteArray? = call(
        snapshotSelectedThreadGroupsMethod,
        actionCommand,
        port,
        token,
        journalPath,
    ) as? ByteArray

    override fun appendSampleResult(sessionId: String, xmlFragment: ByteArray) {
        call(appendSampleResultMethod, sessionId, xmlFragment)
    }

    override val isDirty: Boolean
        get() = call(getDirty) as? Boolean
            ?: throw JMeterRuntimeException("JMeter returned an incompatible dirty state")

    override fun markSaved() {
        call(markSaved)
    }

    override fun setModelChangeListener(listener: Runnable?) {
        call(setModelChangeListenerMethod, listener)
    }

    override fun setExecutionActionListener(listener: ActionListener?) {
        call(setExecutionActionListenerMethod, listener)
    }

    override val shortcutDescriptors: List<JMeterNativeShortcut>
        get() {
            val descriptors = call(getShortcutDescriptorsMethod) as? List<*>
                ?: throw JMeterRuntimeException("JMeter returned incompatible shortcut descriptors")
            return descriptors.map { raw ->
                val descriptor = raw as? Map<*, *>
                    ?: throw JMeterRuntimeException("JMeter returned an incompatible shortcut descriptor")
                JMeterNativeShortcut(
                    keyCode = descriptor["keyCode"] as? Int
                        ?: throw JMeterRuntimeException("A JMeter shortcut has no key code"),
                    modifiers = descriptor["modifiers"] as? Int
                        ?: throw JMeterRuntimeException("A JMeter shortcut has no modifiers"),
                    command = descriptor["command"] as? String
                        ?: throw JMeterRuntimeException("A JMeter shortcut has no command"),
                    argument = descriptor["argument"] as? String,
                )
            }
        }

    override fun performAction(command: String, argument: String?) {
        call(performActionMethod, command, argument)
    }

    override fun exportSelectedNodes(): ByteArray? =
        call(exportSelectedNodesMethod) as? ByteArray

    override fun importNodes(portableJmx: ByteArray): Int =
        call(importNodesMethod, portableJmx) as? Int
            ?: throw JMeterRuntimeException("JMeter returned an incompatible imported-node count")

    override val canRunSelectedThreadGroups: Boolean
        get() = call(canRunSelectedThreadGroupsMethod) as? Boolean
            ?: throw JMeterRuntimeException("JMeter returned an incompatible run-selection state")

    override fun startSelectedThreadGroups(sessionId: String): Boolean =
        call(startSelectedThreadGroupsMethod, sessionId) as? Boolean
            ?: throw JMeterRuntimeException("JMeter returned an incompatible run state")

    override fun shutdownTest() {
        call(shutdownTestMethod)
    }

    override fun stopTest() {
        call(stopTestMethod)
    }

    override val isTestRunning: Boolean
        get() = call(getTestRunning) as? Boolean
            ?: throw JMeterRuntimeException("JMeter returned an incompatible execution state")

    override fun clearResults(sessionId: String) {
        call(clearResultsMethod, sessionId)
    }

    override fun discardResults(sessionId: String) {
        call(discardResultsMethod, sessionId)
    }

    @Synchronized
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        runtime.withContextClassLoader {
            runtime.invoke(close, delegate)
        }
    }

    private fun call(method: java.lang.reflect.Method, vararg arguments: Any?): Any? {
        check(!closed) { "The JMeter workspace is closed" }
        return runtime.withContextClassLoader {
            runtime.invoke(method, delegate, *arguments)
        }
    }
}
