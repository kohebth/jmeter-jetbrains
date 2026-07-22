package com.github.kohebth.jmeterviewer.runtime

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale
import java.util.IdentityHashMap
import java.util.stream.Collectors

@Service(Service.Level.APP)
class JMeterRuntimeService : Disposable {
    private val sessions = java.util.Collections.newSetFromMap(
        IdentityHashMap<JMeterRuntimeSession, Boolean>(),
    )

    @Synchronized
    internal fun openSession(): JMeterRuntimeSession {
        val installation = configuredInstallation()
        val activeHome = sessions.firstOrNull()?.installationHome
        if (activeHome != null && activeHome != installation.home) {
            throw JMeterConfigurationException(
                "The JMeter home changed from $activeHome to ${installation.home}. " +
                    "Restart the IDE before loading another JMeter installation.",
            )
        }

        val opened = JMeterRuntime.open(installation, locateBridge())
        return try {
            val workspace = opened.createWorkspace()
            JMeterRuntimeSession(opened, workspace) { closed ->
                synchronized(this) { sessions.remove(closed) }
            }.also(sessions::add)
        } catch (failure: Throwable) {
            try {
                opened.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    internal fun requiresRestart(configuredHome: String): Boolean {
        val activeHome = synchronized(this) {
            sessions.firstOrNull()?.installationHome
        } ?: return false
        if (configuredHome.isBlank()) {
            return true
        }
        val requestedHome = try {
            Path.of(configuredHome).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            return false
        }
        return requestedHome != activeHome
    }

    internal fun configuredInstallation(): JMeterInstallation {
        val configuredHome = ApplicationManager.getApplication()
            .getService(JMeterSettings::class.java)
            .jmeterHome
        if (configuredHome.isBlank()) {
            throw JMeterConfigurationException(
                "Apache JMeter is not configured. Open Settings > Tools > JMeter and select " +
                    "an Apache JMeter ${JMeterInstallation.SUPPORTED_VERSION} installation.",
            )
        }
        val path = try {
            Path.of(configuredHome)
        } catch (failure: InvalidPathException) {
            throw JMeterConfigurationException(
                "The configured JMeter home is invalid: $configuredHome",
                failure,
            )
        }
        return JMeterInstallation.validate(path)
    }

    private fun locateBridge(): Path {
        val descriptor = checkNotNull(PluginManagerCore.getPlugin(PLUGIN_ID)) {
            "Unable to locate the installed JMeter Viewer plugin"
        }
        val bridgeDirectory = descriptor.pluginPath.resolve(BRIDGE_DIRECTORY)
        if (!Files.isDirectory(bridgeDirectory)) {
            throw JMeterRuntimeException(
                "Missing JMeter compatibility bridge directory: $bridgeDirectory",
            )
        }
        val bridges = Files.list(bridgeDirectory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter {
                    val name = it.fileName.toString().lowercase(Locale.ROOT)
                    name.startsWith("apachejmeter_core-") && name.endsWith(".jar")
                }
                .map { it.toAbsolutePath().normalize() }
                .sorted()
                .collect(Collectors.toList())
        }
        if (bridges.size != 1) {
            throw JMeterRuntimeException(
                "Expected one JMeter compatibility bridge in $bridgeDirectory, found ${bridges.size}",
            )
        }
        return bridges.single()
    }

    @Synchronized
    override fun dispose() {
        sessions.toList().forEach { session ->
            try {
                session.close()
            } catch (closeFailure: Throwable) {
                LOG.warn("Unable to close an embedded JMeter runtime session", closeFailure)
            }
        }
        sessions.clear()
    }

    private companion object {
        const val BRIDGE_DIRECTORY = "jmeter-bridge"
        val LOG: Logger = Logger.getInstance(JMeterRuntimeService::class.java)
        val PLUGIN_ID: PluginId = PluginId.getId("com.github.kohebth.jmeterviewer")
    }
}

internal class JMeterRuntimeSession(
    private val runtime: JMeterRuntime,
    val workspace: JMeterWorkspace,
    private val onClosed: (JMeterRuntimeSession) -> Unit,
) : AutoCloseable {
    val installationHome: Path
        get() = runtime.installation.home

    @Volatile
    private var closed = false

    @Synchronized
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        var failure: Throwable? = null
        try {
            val application = ApplicationManager.getApplication()
            if (application.isDispatchThread) {
                workspace.close()
            } else {
                application.invokeAndWait { workspace.close() }
            }
        } catch (caught: Throwable) {
            failure = caught
        }
        try {
            runtime.close()
        } catch (closeFailure: Throwable) {
            if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
        } finally {
            onClosed(this)
        }
        failure?.let { throw it }
    }
}
