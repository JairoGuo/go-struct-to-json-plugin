package com.github.jairoguo.gostructtojsonplugin;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;

public class Notifier {
    private static final NotificationGroup notificationGroup;

    static {
        notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("CopyStructJson.NotificationGroup");
    }

    public static void notifyWarning(Project project, String msg) {
        notificationGroup.createNotification(msg, NotificationType.WARNING).notify(project);
    }

    public static void notifyInfo(Project project, String msg) {
        notificationGroup.createNotification(msg, NotificationType.INFORMATION).notify(project);
    }

    public static void notifyError(Project project, String msg) {
        notificationGroup.createNotification(msg, NotificationType.ERROR).notify(project);

    }

    private Notifier() {
    }
}
