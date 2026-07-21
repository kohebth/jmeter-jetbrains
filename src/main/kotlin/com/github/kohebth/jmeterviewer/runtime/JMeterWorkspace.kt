package com.github.kohebth.jmeterviewer.runtime

import java.awt.Component
import java.awt.event.ActionListener
import java.io.InputStream
import java.nio.file.Path
import javax.swing.JComponent

internal interface JMeterWorkspace : AutoCloseable {
    val component: JComponent

    val outlineComponent: JComponent

    fun resultsTreeComponent(sessionId: String): JComponent

    fun aggregateReportComponent(sessionId: String): JComponent

    fun setDialogParent(parent: Component?)

    fun load(input: InputStream, sourcePath: Path)

    fun reloadFromHistory(input: InputStream, sourcePath: Path)

    fun snapshot(): ByteArray

    val isDirty: Boolean

    fun markSaved()

    fun setModelChangeListener(listener: Runnable?)

    fun setExecutionActionListener(listener: ActionListener?)

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
    private var closed = false
    private val getComponent = workspaceClass.getMethod("getComponent")
    private val getOutlineComponent = workspaceClass.getMethod("getOutlineComponent")
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

    override val component: JComponent
        get() = call(getComponent) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible editor component")

    override val outlineComponent: JComponent
        get() = call(getOutlineComponent) as? JComponent
            ?: throw JMeterRuntimeException("JMeter returned an incompatible outline component")

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
