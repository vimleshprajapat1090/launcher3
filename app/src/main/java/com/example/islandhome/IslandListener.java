package com.example.islandhome;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class IslandListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        if (IslandService.instance != null) {
            IslandService.instance.connectMedia();
        }
    }

    @Override
    public void onNotificationPosted(
        StatusBarNotification status
    ) {
        if (
            status.getPackageName().equals(getPackageName())
            || IslandService.instance == null
        ) {
            return;
        }

        Notification notification = status.getNotification();
        Bundle extras = notification.extras;

        CharSequence title = extras.getCharSequence(
            Notification.EXTRA_TITLE
        );

        CharSequence text = extras.getCharSequence(
            Notification.EXTRA_TEXT
        );

        IslandService.instance.update(
            title == null
                ? status.getPackageName()
                : title.toString(),
            text == null ? "" : text.toString(),
            notification.contentIntent
        );
    }

    @Override
    public void onNotificationRemoved(
        StatusBarNotification status
    ) {
        if (
            IslandService.instance != null
            && !status.getPackageName().equals(getPackageName())
        ) {
            IslandService.instance.update(
                "Island Home",
                "Tap to expand",
                null
            );
        }
    }
}
