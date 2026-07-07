package com.github.duync.jmeterviewer;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;

final class JMeterIdeNotifications {
    private static final String GROUP_ID = "JMeter Viewer";

    private JMeterIdeNotifications() {
    }

    static void info(Project project, String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    static void warn(Project project, String message) {
        notify(project, message, NotificationType.WARNING);
    }

    static void error(Project project, String message) {
        notify(project, message, NotificationType.ERROR);
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(message, type)
                .notify(project);
    }
}
