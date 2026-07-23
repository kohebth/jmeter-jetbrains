package com.github.kohebth.jmeterviewer.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath

@FixtureName("IDE frame")
class IdeFrameFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath("IDE frame", "//div[@class='IdeFrameImpl']")
    }
}

@FixtureName("JMeter Test Plan host")
class TestPlanHostFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath(
            "JMeter Test Plan host",
            "//div[@class='TestPlanHost']",
        )
    }
}

@FixtureName("JMeter native script area")
class JMeterScriptAreaFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ComponentFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath(
            "visible JMeter JSyntaxTextArea",
            "//div[@javaclass='org.apache.jmeter.gui.util.JSyntaxTextArea']",
        )
    }

    fun state(): SwingTextState {
        val values = callJs<ArrayList<String>>(
            """
            const values = new java.util.ArrayList();
            const selected = component.getSelectedText();
            values.add(component.getText());
            values.add(String(component.getCaret().getDot()));
            values.add(String(component.getCaret().getMark()));
            values.add(selected == null ? "" : selected.toString());
            values;
            """.trimIndent(),
            true,
        )
        return SwingTextState(
            text = values[0],
            dot = values[1].toInt(),
            mark = values[2].toInt(),
            selectedText = values[3],
        )
    }
}

data class SwingTextState(
    val text: String,
    val dot: Int,
    val mark: Int,
    val selectedText: String,
)

@FixtureName("IntelliJ text editor")
class IdeTextEditorFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ComponentFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath(
            "visible IntelliJ editor component",
            "//div[@class='EditorComponentImpl']",
        )
    }

    val text: String
        get() = callJs(
            "component.getEditor().getDocument().getText();",
            true,
        )

    val caretOffset: Int
        get() = callJs(
            "component.getEditor().getCaretModel().getOffset();",
            true,
        )

    val fileName: String
        get() = callJs(
            """
            const file = component.getEditor().getVirtualFile();
            file == null ? "" : file.getName();
            """.trimIndent(),
            true,
        )
}
