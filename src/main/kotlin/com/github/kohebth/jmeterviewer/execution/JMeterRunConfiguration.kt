package com.github.kohebth.jmeterviewer.execution

import com.github.kohebth.jmeterviewer.editor.JMeterWorkspaceService
import com.github.kohebth.jmeterviewer.ide.JMeterFileType
import com.github.kohebth.jmeterviewer.runtime.JMeterSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class JMeterRunConfigurationType : ConfigurationTypeBase(
    ID,
    "JMeter Selected Thread Groups",
    "Run selected thread groups from one JMeter visual editor session",
    JMeterFileType.icon,
) {
    init {
        addFactory(JMeterConfigurationFactory(this))
    }

    companion object {
        const val ID = "JMeterSelectedThreadGroups"
    }
}

private class JMeterConfigurationFactory(type: JMeterRunConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        JMeterRunConfiguration(project, this, "JMeter Selected Thread Groups")

    override fun getId(): String = JMeterRunConfigurationType.ID
}

class JMeterRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
    internal var runMode: JMeterRunMode = JMeterRunMode.AS_IS
    internal var targetFileUrl: String = ""

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        JMeterRunSettingsEditor()

    override fun checkConfiguration() {
        val configuredHome = ApplicationManager.getApplication()
            .getService(JMeterSettings::class.java)
            .jmeterHome
        if (configuredHome.isBlank()) {
            throw RuntimeConfigurationError(
                "Configure Apache JMeter under Settings | Tools | JMeter before running.",
            )
        }
        if (targetFileUrl.isBlank()) {
            throw RuntimeConfigurationError(
                "Start this configuration from a JMX tree's native context menu.",
            )
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        JMeterCommandLineState(environment, targetFileUrl, runMode)

    override fun readExternal(element: Element) {
        super.readExternal(element)
        runMode = element.getAttributeValue(MODE_ATTRIBUTE)
            ?.let { stored -> JMeterRunMode.values().firstOrNull { it.name == stored } }
            ?: JMeterRunMode.AS_IS
        targetFileUrl = element.getAttributeValue(TARGET_FILE_ATTRIBUTE).orEmpty()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(MODE_ATTRIBUTE, runMode.name)
        element.setAttribute(TARGET_FILE_ATTRIBUTE, targetFileUrl)
    }

    private companion object {
        const val MODE_ATTRIBUTE = "jmeterRunMode"
        const val TARGET_FILE_ATTRIBUTE = "jmeterTargetFileUrl"
    }
}

private class JMeterRunSettingsEditor : SettingsEditor<JMeterRunConfiguration>() {
    private val mode = JComboBox(JMeterRunMode.values())
    private val panel = JPanel(BorderLayout(8, 0)).apply {
        add(JLabel("Selected thread groups:"), BorderLayout.WEST)
        add(mode, BorderLayout.CENTER)
    }

    override fun resetEditorFrom(configuration: JMeterRunConfiguration) {
        mode.selectedItem = configuration.runMode
    }

    override fun applyEditorTo(configuration: JMeterRunConfiguration) {
        configuration.runMode = mode.selectedItem as? JMeterRunMode ?: JMeterRunMode.AS_IS
    }

    override fun createEditor(): JComponent = panel
}

private class JMeterCommandLineState(
    environment: ExecutionEnvironment,
    private val targetFileUrl: String,
    private val mode: JMeterRunMode,
) : CommandLineState(environment) {
    init {
        consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project)
    }

    @Throws(ExecutionException::class)
    override fun startProcess(): ProcessHandler {
        val service = environment.project.getService(JMeterWorkspaceService::class.java)
        val request = try {
            service.prepareExternalRun(targetFileUrl, mode)
        } catch (failure: Exception) {
            throw ExecutionException(failure.message ?: "Unable to prepare the JMeter run", failure)
        }
        return try {
            KillableColoredProcessHandler(request.commandLine).also { handler ->
                service.bindExternalProcess(request, handler)
            }
        } catch (failure: Exception) {
            service.abortExternalRun(request)
            if (failure is ExecutionException) {
                throw failure
            }
            throw ExecutionException(failure.message ?: "Unable to start JMeter", failure)
        }
    }
}
