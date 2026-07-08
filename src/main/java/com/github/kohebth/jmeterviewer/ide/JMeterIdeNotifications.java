package com.github.kohebth.jmeterviewer.ide;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;

public final class JMeterIdeNotifications {
    private static final String GROUP_ID = "JMeter Viewer";

    private JMeterIdeNotifications() {
    }

    public static void info(Project project, String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    public static void warn(Project project, String message) {
        notify(project, message, NotificationType.WARNING);
    }

    public static void error(Project project, String message) {
        notify(project, message, NotificationType.ERROR);
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(message, type)
                .notify(project);
    }
}
