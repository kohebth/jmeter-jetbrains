package com.github.kohebth.jmeterviewer.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public final class JMeterReloadGuard {
    private JMeterReloadGuard() {
    }

    public static boolean canDiscard(Project project, boolean modified) {
        if (!modified) {
            return true;
        }
        return Messages.showYesNoDialog(
                project,
                "Reloading will discard unsaved JMeter changes.",
                "Reload JMeter File",
                Messages.getQuestionIcon()
        ) == Messages.YES;
    }

    public static boolean canReloadExternalChange(Project project, boolean modified) {
        String message = modified
                ? "The JMX file was saved in the text editor. Reloading will discard unsaved JMeter visual changes. Reload now?"
                : "The JMX file was saved in the text editor. Reload the JMeter view now?";
        return Messages.showYesNoDialog(
                project,
                message,
                "Reload JMeter View",
                "Reload",
                "Keep Current View",
                Messages.getQuestionIcon()
        ) == Messages.YES;
    }
}
