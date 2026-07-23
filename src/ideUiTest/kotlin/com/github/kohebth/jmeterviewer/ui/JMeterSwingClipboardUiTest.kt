package com.github.kohebth.jmeterviewer.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_C
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.KeyEvent.VK_V
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Locale
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

class JMeterSwingClipboardUiTest {
    private val robotUrl = requiredProperty("remote-robot-url")
    private val remoteRobot = RemoteRobot(robotUrl)
    private val projectDirectory = Paths.get(requiredProperty("jmeter.ide.ui.project.dir"))
    private val reportDirectory = Paths.get(requiredProperty("jmeter.ide.ui.report.dir"))
    private val ideaLog = Paths.get(requiredProperty("jmeter.ide.ui.log.path"))

    private val lines = deterministicLines()
    private val expectedText = lines.joinToString("\n")

    @Test
    fun `native JMeter text area supports navigation selection copy and IDE paste`() {
        try {
            waitUntil("Remote Robot connection", Duration.ofMinutes(2)) {
                runCatching { remoteRobot.callJs<Boolean>("true;") }.getOrDefault(false)
            }

            openProjectFile(JMX_FILE_NAME)
            val scriptArea = selectJsr223ScriptArea()
            replaceScriptWithTenLines(scriptArea)
            exerciseCaretAndSelection(scriptArea)

            scriptArea.keyboard {
                pressing(VK_CONTROL) {
                    waitForModifierKey()
                    key(VK_C, SHORT_KEY_DELAY)
                }
            }
            waitUntil("the copied JMeter script on the system clipboard") {
                clipboardText() == expectedText
            }
            assertEquals(expectedText, clipboardText(), "Ctrl+C must copy all ten lines")

            openProjectFile(CLIPBOARD_TARGET_NAME)
            val ideEditor = remoteRobot.find<IdeTextEditorFixture>(
                IdeTextEditorFixture.locator,
                Duration.ofSeconds(30),
            )
            waitUntil("the clipboard target editor") {
                ideEditor.fileName == CLIPBOARD_TARGET_NAME
            }
            ideEditor.click()
            waitUntil("keyboard focus in the IntelliJ text editor") {
                ideEditor.isFocusOwner
            }

            ideEditor.keyboard {
                pressing(VK_CONTROL) {
                    waitForModifierKey()
                    key(VK_V, SHORT_KEY_DELAY)
                }
            }
            waitUntil("the copied lines pasted into the IntelliJ editor") {
                ideEditor.text == expectedText
            }
            assertEquals(expectedText, ideEditor.text)
            assertEquals(expectedText.length, ideEditor.caretOffset)

            waitUntil("the edited JSR223 script persisted to the copied JMX file") {
                persistedJsr223Script() == expectedText
            }
            assertEquals(expectedText, persistedJsr223Script())
            assertNoIdeErrors()
        } catch (failure: Throwable) {
            captureFailureArtifacts(failure)
            throw failure
        }
    }

    private fun openProjectFile(fileName: String) {
        val frame = remoteRobot.find<IdeFrameFixture>(
            IdeFrameFixture.locator,
            Duration.ofMinutes(2),
        )
        val projectTreeLocator = byXpath(
            "Project tree",
            "//div[@class='ProjectViewTree']",
        )
        if (frame.findAll<ContainerFixture>(projectTreeLocator).isEmpty()) {
            frame.find<ComponentFixture>(
                byXpath(
                    "Project tool window button",
                    "//div[@class='StripeButton' and " +
                        "(@text='Project' or @visible_text='Project' or @accessiblename='Project')]",
                ),
                Duration.ofSeconds(30),
            ).click()
        }
        val projectTree = frame.find<ContainerFixture>(
            projectTreeLocator,
            Duration.ofSeconds(30),
        )
        waitUntil("$fileName in the Project tree", Duration.ofSeconds(30)) {
            projectTree.runJs(
                """
                for (let row = 0; row < component.getRowCount(); row++) {
                    component.expandRow(row);
                }
                """.trimIndent(),
                true,
            )
            projectTree.hasText(fileName)
        }
        projectTree.findText(fileName).doubleClick()
    }

    private fun selectJsr223ScriptArea(): JMeterScriptAreaFixture {
        val host = remoteRobot.find<TestPlanHostFixture>(
            TestPlanHostFixture.locator,
            Duration.ofMinutes(2),
        )
        val searchButton = host.find<ComponentFixture>(
            byXpath(
                "Test Plan Search button",
                "//div[@class='JButton' and (@text='Search' or @visible_text='Search')]",
            ),
            Duration.ofSeconds(30),
        )
        searchButton.click()

        val textFields = host.findAll<ComponentFixture>(
            byXpath("Test Plan search fields", "//div[@javaclass='javax.swing.JTextField']"),
        )
        assertTrue(textFields.size >= 2, "The visible search panel must contain search and replacement fields")
        val searchField = textFields.firstOrNull {
            it.callJs<Boolean>(
                "component.getParent().getComponent(1).equals(component);",
                true,
            )
        } ?: throw AssertionError("Could not identify the Test Plan search field")

        searchField.click()
        waitUntil("keyboard focus in Test Plan search") {
            searchField.isFocusOwner
        }
        searchField.keyboard {
            selectAll()
            enterText(JSR223_ELEMENT_NAME, 10)
            enter()
        }
        return remoteRobot.find(
            JMeterScriptAreaFixture::class.java,
            JMeterScriptAreaFixture.locator,
            Duration.ofMinutes(1),
        )
    }

    private fun replaceScriptWithTenLines(scriptArea: JMeterScriptAreaFixture) {
        scriptArea.click()
        waitUntil("keyboard focus in JMeter's native script area") {
            scriptArea.isFocusOwner
        }
        scriptArea.keyboard {
            selectAll()
            backspace()
        }
        waitUntil("the original JSR223 script to be cleared") {
            scriptArea.state().text.isEmpty()
        }

        scriptArea.keyboard {
            lines.take(5).forEachIndexed { index, line ->
                if (index > 0) {
                    enter()
                }
                enterText(line, 10)
            }
        }

        val partialText = lines.take(5).joinToString("\n")
        val caretBeforeAutosave = scriptArea.state()
        assertTextState(
            caretBeforeAutosave,
            partialText,
            partialText.length,
            partialText.length,
            "",
            "before the autosave debounce",
        )
        waitUntil("the fifth line to reach the backing JMX document") {
            backingJmxDocumentContains(lines[4])
        }
        assertTextState(
            scriptArea.state(),
            partialText,
            partialText.length,
            partialText.length,
            "",
            "after the autosave debounce",
        )

        scriptArea.keyboard {
            lines.drop(5).forEach { line ->
                enter()
                enterText(line, 10)
            }
        }
        waitUntil("all ten lines to reach the backing JMX document") {
            backingJmxDocumentContains(lines.last())
        }
        assertTextState(
            scriptArea.state(),
            expectedText,
            expectedText.length,
            expectedText.length,
            "",
            "after typing all ten lines",
        )
    }

    private fun exerciseCaretAndSelection(scriptArea: JMeterScriptAreaFixture) {
        scriptArea.keyboard {
            pressing(VK_CONTROL) {
                waitForModifierKey()
                key(VK_HOME, SHORT_KEY_DELAY)
            }
        }
        assertTextState(scriptArea.state(), expectedText, 0, 0, "", "after Ctrl+Home")

        scriptArea.keyboard {
            repeat(6) { key(VK_RIGHT, SHORT_KEY_DELAY) }
        }
        assertTextState(scriptArea.state(), expectedText, 6, 6, "", "after Right")

        scriptArea.keyboard {
            repeat(3) { key(VK_DOWN, SHORT_KEY_DELAY) }
        }
        assertTextState(scriptArea.state(), expectedText, 81, 81, "", "after Down")

        scriptArea.keyboard {
            repeat(2) { key(VK_LEFT, SHORT_KEY_DELAY) }
        }
        assertTextState(scriptArea.state(), expectedText, 79, 79, "", "after Left")

        scriptArea.keyboard {
            key(VK_UP, SHORT_KEY_DELAY)
        }
        assertTextState(scriptArea.state(), expectedText, 54, 54, "", "after Up")

        scriptArea.keyboard {
            pressing(VK_SHIFT) {
                waitForModifierKey()
                repeat(5) { key(VK_RIGHT, SHORT_KEY_DELAY) }
            }
        }
        assertTextState(
            scriptArea.state(),
            expectedText,
            59,
            54,
            expectedText.substring(54, 59),
            "after Shift+Right",
        )

        scriptArea.keyboard {
            pressing(VK_SHIFT) {
                waitForModifierKey()
                repeat(2) { key(VK_LEFT, SHORT_KEY_DELAY) }
            }
        }
        assertTextState(
            scriptArea.state(),
            expectedText,
            57,
            54,
            expectedText.substring(54, 57),
            "after Shift+Left",
        )

        scriptArea.keyboard {
            key(VK_HOME, SHORT_KEY_DELAY)
        }
        assertTextState(scriptArea.state(), expectedText, 50, 50, "", "after Home")

        scriptArea.keyboard {
            key(VK_END, SHORT_KEY_DELAY)
        }
        assertTextState(scriptArea.state(), expectedText, 74, 74, "", "after End")

        scriptArea.keyboard {
            pressing(VK_CONTROL) {
                waitForModifierKey()
                key(VK_END, SHORT_KEY_DELAY)
            }
        }
        assertTextState(
            scriptArea.state(),
            expectedText,
            expectedText.length,
            expectedText.length,
            "",
            "after Ctrl+End",
        )

        scriptArea.keyboard {
            pressing(VK_CONTROL) {
                waitForModifierKey()
                key(VK_HOME, SHORT_KEY_DELAY)
            }
        }
        assertTextState(scriptArea.state(), expectedText, 0, 0, "", "after the second Ctrl+Home")

        scriptArea.keyboard {
            pressing(VK_CONTROL) {
                waitForModifierKey()
                pressing(VK_SHIFT) {
                    waitForModifierKey()
                    key(VK_END, SHORT_KEY_DELAY)
                }
            }
        }
        assertTextState(
            scriptArea.state(),
            expectedText,
            expectedText.length,
            0,
            expectedText,
            "after Ctrl+Shift+End",
        )
    }

    private fun backingJmxDocumentContains(needle: String): Boolean {
        val escapedNeedle = needle.escapeJavaScript()
        return remoteRobot.callJs(
            """
            const projects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (projects.length == 0) {
                false;
            } else {
                const project = projects[0];
                const path = project.getBasePath() + "/$JMX_FILE_NAME";
                const file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path);
                const document = file == null
                    ? null
                    : com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file);
                document != null && document.getText().indexOf("$escapedNeedle") >= 0;
            }
            """.trimIndent(),
            true,
        )
    }

    private fun clipboardText(): String = remoteRobot.callJs(
        """
        const clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        const value = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
        value == null ? "" : value.toString();
        """.trimIndent(),
        true,
    )

    private fun persistedJsr223Script(): String? {
        val jmxFile = projectDirectory.resolve(JMX_FILE_NAME).toFile()
        return runCatching {
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(jmxFile)
            val processors = document.getElementsByTagName("JSR223PreProcessor")
            for (index in 0 until processors.length) {
                val processor = processors.item(index) as? Element ?: continue
                if (processor.getAttribute("testname") != JSR223_ELEMENT_NAME) {
                    continue
                }
                val properties = processor.getElementsByTagName("stringProp")
                for (propertyIndex in 0 until properties.length) {
                    val property = properties.item(propertyIndex) as? Element ?: continue
                    if (property.getAttribute("name") == "script") {
                        return@runCatching property.textContent
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun assertNoIdeErrors() {
        waitUntil("the IDE log file", Duration.ofSeconds(20)) {
            Files.isRegularFile(ideaLog)
        }
        val contents = Files.readString(ideaLog)
        KNOWN_THREADING_ERRORS.forEach { errorText ->
            assertTrue(
                !contents.contains(errorText),
                "IDE log contains the forbidden threading/command error '$errorText'",
            )
        }

        val pluginError = contents
            .split(Regex("(?=\\d{4}-\\d{2}-\\d{2})"))
            .firstOrNull { block ->
                block.contains("ERROR") && block.contains(PLUGIN_PACKAGE)
            }
        assertTrue(
            pluginError == null,
            "IDE log contains an error attributed to the JMeter plugin:\n${pluginError.orEmpty()}",
        )
    }

    private fun captureFailureArtifacts(failure: Throwable) {
        runCatching { Files.createDirectories(reportDirectory) }
        runCatching {
            ImageIO.write(
                remoteRobot.getScreenshot(),
                "png",
                reportDirectory.resolve("swing-keyboard-clipboard-failure.png").toFile(),
            )
        }
        runCatching {
            val connection = URL(robotUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.inputStream.use { input ->
                Files.copy(
                    input,
                    reportDirectory.resolve("component-hierarchy.html"),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        runCatching {
            if (Files.isRegularFile(ideaLog)) {
                Files.copy(
                    ideaLog,
                    reportDirectory.resolve("idea.log"),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
        runCatching {
            val stackTrace = StringWriter().also { writer ->
                failure.printStackTrace(PrintWriter(writer))
            }.toString()
            Files.writeString(
                reportDirectory.resolve("failure.txt"),
                stackTrace,
            )
        }
        System.err.println("IDE UI failure artifacts: ${reportDirectory.toAbsolutePath()}")
    }

    private fun assertTextState(
        actual: SwingTextState,
        text: String,
        dot: Int,
        mark: Int,
        selectedText: String,
        phase: String,
    ) {
        assertEquals(text, actual.text, "Text changed unexpectedly $phase")
        assertEquals(dot, actual.dot, "Caret dot is wrong $phase")
        assertEquals(mark, actual.mark, "Caret mark is wrong $phase")
        assertEquals(selectedText, actual.selectedText, "Selection is wrong $phase")
    }

    private fun waitUntil(
        description: String,
        timeout: Duration = Duration.ofSeconds(20),
        interval: Duration = Duration.ofMillis(200),
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastFailure: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                if (condition()) {
                    return
                }
            } catch (failure: Throwable) {
                lastFailure = failure
            }
            Thread.sleep(interval.toMillis())
        }
        throw AssertionError("Timed out waiting for $description after ${timeout.seconds}s", lastFailure)
    }

    private fun waitForModifierKey() {
        Thread.sleep(MODIFIER_KEY_SETTLE_MILLIS)
    }

    private fun deterministicLines(): List<String> {
        val random = Random(0x5EED)
        return (1..10).map { lineNumber ->
            val suffix = buildString {
                repeat(16) {
                    append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)])
                }
            }
            String.format(Locale.ROOT, "line-%02d-%s", lineNumber, suffix)
        }
    }

    private fun String.escapeJavaScript(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private fun requiredProperty(name: String): String =
        System.getProperty(name)?.takeIf(String::isNotBlank)
            ?: error("Required system property '$name' is missing")

    private companion object {
        const val JMX_FILE_NAME = "complex-localhost-plan.jmx"
        const val CLIPBOARD_TARGET_NAME = "clipboard-target.txt"
        const val JSR223_ELEMENT_NAME = "Build Login Correlation ID"
        const val PLUGIN_PACKAGE = "com.github.kohebth.jmeterviewer"
        const val RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        const val MODIFIER_KEY_SETTLE_MILLIS = 150L

        val SHORT_KEY_DELAY: Duration = Duration.ofMillis(50)
        val KNOWN_THREADING_ERRORS = listOf(
            "Must not change document outside command or undo-transparent action",
            "Write-unsafe context",
            "Read access is allowed from inside read-action only",
        )
    }
}
