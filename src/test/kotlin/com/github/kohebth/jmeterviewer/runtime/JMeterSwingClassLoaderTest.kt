package com.github.kohebth.jmeterviewer.runtime

import com.github.kohebth.jmeterviewer.execution.JMeterResultBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.awt.Component
import java.awt.Container
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class JMeterSwingClassLoaderTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun runtimeLoaderOnlyExposesHostSwingUiDelegates() {
        openRuntime().use { runtime ->
            assertSame(
                HostTreeUI::class.java,
                runtime.classLoader.loadClass(HostTreeUI::class.java.name),
            )
            assertThrows<ClassNotFoundException> {
                runtime.classLoader.loadClass(JMeterSwingClassLoaderTest::class.java.name)
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JMETER_GUI_SMOKE", matches = "true")
    fun opensAndReopensAComplexPlanWithHostLookAndFeelAndRecordedResults() {
        openRuntime().use { runtime ->
            withHostTreeUi {
                val workspace = onEdt(runtime::createWorkspace)
                try {
                    val fixture = resourcePath(COMPLEX_PLAN_RESOURCE)
                    val outlineTree = onEdt {
                        Files.newInputStream(fixture).use { input ->
                            workspace.load(input, fixture)
                        }

                        val tree = descendants<JTree>(workspace.outlineComponent).single()
                        assertSame(runtime.classLoader, tree.javaClass.classLoader)
                        assertSame(HostTreeUI::class.java, tree.ui.javaClass)
                        assertComplexPlan(workspace)

                        val reopened = tempDirectory.resolve("reopened-complex-localhost-plan.jmx")
                        Files.write(reopened, workspace.snapshot())
                        Files.newInputStream(reopened).use { input ->
                            workspace.load(input, reopened)
                        }
                        assertComplexPlan(workspace)
                        tree
                    }

                    JMeterLocalhostServer().use { server ->
                        val journal = tempDirectory.resolve("restricted-results.journal")
                        val token = UUID.randomUUID().toString().replace("-", "")
                        val samples = CopyOnWriteArrayList<ByteArray>()
                        val restrictedPlan = tempDirectory.resolve("restricted-localhost-plan.jmx")
                        val restrictedProcess = JMeterResultBridge(token, journal, samples::add).use { bridge ->
                            val jmx = onEdt {
                                val threadGroups = workspace.searchTestPlan(
                                    "Thread Group",
                                    caseSensitive = true,
                                    regexp = false,
                                ).filter { it.name.matches(Regex("0[1-3] .+ Thread Group")) }
                                assertEquals(3, threadGroups.size)
                                outlineTree.selectionPaths = threadGroups.map { it.path }.toTypedArray()
                                requireNotNull(
                                    workspace.snapshotSelectedThreadGroups(
                                        actionCommand = "run_tg",
                                        port = bridge.port,
                                        token = token,
                                        journalPath = journal,
                                    ),
                                )
                            }
                            assertRestrictedListener(jmx)
                            loadRestrictedJmx(runtime, jmx)
                            assertFalse(
                                String(onEdt(workspace::snapshot), StandardCharsets.UTF_8)
                                    .contains("JetBrains Live Results"),
                                "Transient result bridge leaked into the editor snapshot",
                            )
                            Files.write(restrictedPlan, jmx)

                            val run = runRestrictedPlan(
                                plan = restrictedPlan,
                                fixtureDirectory = fixture.parent,
                                csv = resourcePath(USERS_RESOURCE),
                                serverPort = server.port,
                            )
                            bridge.finishAndReplayJournal()
                            assertEquals(
                                0,
                                run.exitCode,
                                "JMeter non-GUI run failed:\n${run.output}\n${run.jmeterLog}",
                            )
                            run
                        }

                        assertTrue(
                            samples.isNotEmpty(),
                            "Result bridge received no samples:\n" +
                                restrictedProcess.output + "\n" + restrictedProcess.jmeterLog,
                        )
                        assertRecordedRequests(
                            requests = server.requests,
                            serverFailures = server.failures,
                            process = restrictedProcess,
                            samples = samples,
                        )
                        val healthSample = samples.firstOrNull { sample ->
                            String(sample, StandardCharsets.UTF_8)
                                .contains("lb=\"Health Request\"")
                        } ?: throw AssertionError("The result bridge did not receive Health Request")
                        val healthXml = String(healthSample, StandardCharsets.UTF_8)
                        listOf(
                            "s=\"true\"",
                            "rc=\"200\"",
                            "<responseHeader",
                            "<requestHeader",
                            "<responseData",
                            "<method",
                            "<queryString",
                            "<java.net.URL>",
                        ).forEach { expected ->
                            assertTrue(
                                healthXml.contains(expected),
                                "Bridged sample did not contain $expected: $healthXml",
                            )
                        }

                        val resultsTree = onEdt {
                            workspace.resultsTreeComponent(RESULT_SESSION).also {
                                samples.forEach { sample ->
                                    workspace.appendSampleResult(RESULT_SESSION, sample)
                                }
                            }
                        }
                        val (tree, sampleNode) = awaitSample(resultsTree, HEALTH_SAMPLE_LABEL)
                        onEdt {
                            tree.selectionPath = TreePath(sampleNode.path)
                            val renderedText = descendants<JTextComponent>(resultsTree)
                                .joinToString("\n") { it.text }
                            listOf(
                                "GET http://${JMeterLocalhostServer.HOST}:${server.port}/health",
                                JMeterLocalhostServer.HEALTH_RESPONSE,
                            ).forEach { expected ->
                                assertTrue(
                                    renderedText.contains(expected),
                                    "Results Tree did not render: $expected",
                                )
                            }
                            listOf(
                                "${JMeterLocalhostServer.REQUEST_HEADER}: " +
                                    JMeterLocalhostServer.REQUEST_HEADER_VALUE,
                                "${JMeterLocalhostServer.RESPONSE_HEADER}: " +
                                    JMeterLocalhostServer.RESPONSE_HEADER_VALUE,
                            ).forEach { expected ->
                                assertTrue(
                                    renderedText.lowercase(Locale.ROOT)
                                        .contains(expected.lowercase(Locale.ROOT)),
                                    "Results Tree did not render: $expected",
                                )
                            }
                        }
                    }
                } finally {
                    onEdt(workspace::close)
                }
            }
        }
    }

    private fun assertComplexPlan(workspace: JMeterWorkspace) {
        val expectedNames = listOf(
            "Shared CSV Users",
            "Smoke Request Headers",
            "01 Authentication Thread Group",
            "02 Catalog Thread Group",
            "03 Observability Thread Group",
            "Login Request",
            "Profile Request",
            "Catalog Request",
            "Product Request",
            "Health Request",
            "Metrics Request",
            "Build Login Correlation ID",
            "Extract Login Token",
            "Attach Profile Region",
            "Capture Profile Title",
            "Build Catalog Query",
            "Extract First Product",
            "Attach Product Correlation ID",
            "Record Product Status",
            "Prepare Health Probe",
            "Extract Health Status",
            "Prepare Metrics Probe",
            "Record Metrics Response",
        )
        expectedNames.forEach { name ->
            assertTrue(
                workspace.searchTestPlan(name, caseSensitive = true, regexp = false)
                    .any { it.name == name },
                "Native workspace did not open $name",
            )
        }

        val threadGroups = workspace.searchTestPlan(
            "Thread Group",
            caseSensitive = true,
            regexp = false,
        ).filter { it.name.matches(Regex("0[1-3] .+ Thread Group")) }
        assertEquals(3, threadGroups.size)

        val product = workspace.searchTestPlan(
            "Product Request",
            caseSensitive = true,
            regexp = false,
        ).single { it.name == "Product Request" }
        assertTrue(product.breadcrumb.contains("Browse Catalog Transaction"))
    }

    private fun assertRestrictedListener(restrictedJmx: ByteArray) {
        val restrictedXml = String(restrictedJmx, StandardCharsets.UTF_8)
        val bridgeTags = restrictedXml.lineSequence()
            .filter { it.contains("<JSR223Listener") }
            .toList()
        assertEquals(1, bridgeTags.size, "Restricted JMX should contain one result bridge")
        val bridgeTag = bridgeTags.single()
            .trim()
        listOf(
            "guiclass=\"TestBeanGUI\"",
            "testclass=\"JSR223Listener\"",
            "testname=\"JetBrains Live Results\"",
            "enabled=\"true\"",
        ).forEach { attribute ->
            assertTrue(
                bridgeTag.contains(attribute),
                "Restricted result bridge was missing $attribute: $bridgeTag",
            )
        }
        listOf(
            "<stringProp name=\"scriptLanguage\">groovy</stringProp>",
            "<stringProp name=\"cacheKey\">jetbrains-results-",
            "<stringProp name=\"script\">",
            "SaveService.saveSampleResult",
        ).forEach { property ->
            assertTrue(
                restrictedXml.contains(property),
                "Restricted result bridge was missing $property",
            )
        }
    }

    private fun loadRestrictedJmx(runtime: JMeterRuntime, restrictedJmx: ByteArray) {
        runtime.withContextClassLoader {
            val saveService = runtime.classLoader.loadClass("org.apache.jmeter.save.SaveService")
            runtime.invoke(
                saveService.getMethod("loadTree", InputStream::class.java),
                null,
                ByteArrayInputStream(restrictedJmx),
            )
        }
    }

    private fun runRestrictedPlan(
        plan: Path,
        fixtureDirectory: Path,
        csv: Path,
        serverPort: Int,
    ): ProcessResult {
        val isWindows = System.getProperty("os.name")
            .lowercase(Locale.ROOT)
            .startsWith("windows")
        val installation = JMeterInstallation.validate(ExternalJMeterTestSupport.home)
        val consoleLog = tempDirectory.resolve("restricted-console.log")
        val jmeterLog = tempDirectory.resolve("restricted-jmeter.log")
        val command = installation.commandLinePrefix(isWindows) + listOf(
            "-n",
            "-t",
            plan.toString(),
            "-j",
            jmeterLog.toString(),
            "-Jsmoke.host=${JMeterLocalhostServer.HOST}",
            "-Jsmoke.port=$serverPort",
            "-Jsmoke.csv=$csv",
        )
        val processBuilder = ProcessBuilder(command)
            .directory(fixtureDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(consoleLog.toFile())
        JMeterJavaEnvironment.resolve(isWindows).forEach { (name, value) ->
            processBuilder.environment()[name] = value
        }
        val process = processBuilder.start()
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            throw AssertionError("JMeter non-GUI smoke test timed out after $PROCESS_TIMEOUT_SECONDS seconds")
        }
        return ProcessResult(
            exitCode = process.exitValue(),
            output = readTextIfPresent(consoleLog),
            jmeterLog = readTextIfPresent(jmeterLog),
        )
    }

    private fun readTextIfPresent(path: Path): String = if (Files.isRegularFile(path)) {
        Files.readString(path)
    } else {
        "Log file was not created: $path"
    }

    private fun assertRecordedRequests(
        requests: List<JMeterLocalhostServer.RecordedRequest>,
        serverFailures: List<Throwable>,
        process: ProcessResult,
        samples: List<ByteArray>,
    ) {
        val paths = requests.map { it.path }
        val sampleDiagnostics = samples.joinToString("\n") { sample ->
            String(sample, StandardCharsets.UTF_8)
        }
        assertTrue(
            serverFailures.isEmpty(),
            serverFailures.joinToString("\n") { it.stackTraceToString() },
        )
        listOf("/login", "/catalog", "/health", "/metrics").forEach { path ->
            assertTrue(
                paths.contains(path),
                "Localhost server did not receive $path: $paths\n" +
                    serverFailures.joinToString("\n") { it.stackTraceToString() } + "\n" +
                    process.output + "\n" + process.jmeterLog + "\n" + sampleDiagnostics,
            )
        }
        assertTrue(paths.any { it.startsWith("/profiles/") })
        assertTrue(paths.any { it.startsWith("/products/") })
        requests.forEach { request ->
            assertEquals(
                JMeterLocalhostServer.REQUEST_HEADER_VALUE,
                request.requestHeader,
                "Missing smoke request header for ${request.path}",
            )
        }
        val login = requests.first { it.path == "/login" }
        assertEquals("POST", login.method)
        assertTrue(login.body.contains("user="), "Login request did not include CSV user data")
    }

    private fun awaitSample(
        resultsTree: JComponent,
        label: String,
    ): Pair<JTree, DefaultMutableTreeNode> {
        val deadline = System.nanoTime() + RESULT_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            val result = onEdt {
                descendants<JTree>(resultsTree).firstNotNullOfOrNull { tree ->
                    val root = tree.model.root
                    (0 until tree.model.getChildCount(root))
                        .map { tree.model.getChild(root, it) }
                        .filterIsInstance<DefaultMutableTreeNode>()
                        .firstOrNull { it.toString() == label }
                        ?.let { tree to it }
                }
            }
            if (result != null) {
                return result
            }
            Thread.sleep(25)
        }
        throw AssertionError("Results Tree did not receive $label")
    }

    private inline fun <reified T : Component> descendants(root: Component): List<T> {
        val matches = mutableListOf<T>()
        val pending = java.util.ArrayDeque<Component>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (component is T) {
                matches.add(component)
            }
            if (component is Container) {
                component.components.forEach(pending::addLast)
            }
        }
        return matches
    }

    private fun resourcePath(resource: String): Path =
        Path.of(requireNotNull(javaClass.getResource(resource)).toURI())

    private fun <T> withHostTreeUi(action: () -> T): T {
        val previous = onEdt {
            val defaults = UIManager.getDefaults()
            UiDefaultsState(
                hadTreeUi = defaults.containsKey("TreeUI"),
                treeUi = defaults["TreeUI"],
                hadClassLoader = defaults.containsKey("ClassLoader"),
                classLoader = defaults["ClassLoader"],
            ).also {
                defaults["TreeUI"] = HostTreeUI::class.java.name
                defaults.remove("ClassLoader")
            }
        }
        return try {
            action()
        } finally {
            onEdt {
                val defaults = UIManager.getDefaults()
                restoreDefault(defaults, "TreeUI", previous.hadTreeUi, previous.treeUi)
                restoreDefault(
                    defaults,
                    "ClassLoader",
                    previous.hadClassLoader,
                    previous.classLoader,
                )
            }
        }
    }

    private fun restoreDefault(
        defaults: javax.swing.UIDefaults,
        key: String,
        existed: Boolean,
        value: Any?,
    ) {
        if (existed) {
            defaults[key] = requireNotNull(value)
        } else {
            defaults.remove(key)
        }
    }

    private fun openRuntime(): JMeterRuntime = JMeterRuntime.open(
        installation = JMeterInstallation.validate(ExternalJMeterTestSupport.home),
        bridgeJar = ExternalJMeterTestSupport.bridge,
        hostClassLoader = HostTreeUI::class.java.classLoader,
    )

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            try {
                result.set(action())
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private companion object {
        const val COMPLEX_PLAN_RESOURCE = "/jmx/smoke/complex-localhost-plan.jmx"
        const val USERS_RESOURCE = "/jmx/smoke/users.csv"
        const val HEALTH_SAMPLE_LABEL = "Health Request"
        const val RESULT_SESSION = "complex-localhost-smoke"
        const val RESULT_TIMEOUT_NANOS = 5_000_000_000L
        const val PROCESS_TIMEOUT_SECONDS = 45L
        const val PROCESS_STOP_TIMEOUT_SECONDS = 5L
    }

    private data class ProcessResult(
        val exitCode: Int,
        val output: String,
        val jmeterLog: String,
    )

    private data class UiDefaultsState(
        val hadTreeUi: Boolean,
        val treeUi: Any?,
        val hadClassLoader: Boolean,
        val classLoader: Any?,
    )
}
