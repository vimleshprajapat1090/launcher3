package com.example.islandhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public class IslandService extends Service {

    public static IslandService instance;

    WindowManager windowManager;
    WindowManager.LayoutParams windowParams;

    LinearLayout panel;
    LinearLayout controls;

    TextView title;
    TextView subtitle;
    TextView play;
    TextView previous;
    TextView next;

    boolean expanded = false;

    MediaController controller;
    MediaSessionManager mediaManager;

    String notice = "Island Home";
    String body = "Tap to expand";

    PendingIntent notificationAction;

    final Handler handler = new Handler(Looper.getMainLooper());

    final MediaSessionManager.OnActiveSessionsChangedListener
        sessionsListener = sessions -> chooseSession(sessions);

    final MediaController.Callback mediaCallback =
        new MediaController.Callback() {

            @Override
            public void onPlaybackStateChanged(
                PlaybackState state
            ) {
                render();
            }

            @Override
            public void onMetadataChanged(
                MediaMetadata metadata
            ) {
                render();
            }

            @Override
            public void onSessionDestroyed() {
                refreshSessions();
            }
        };

    int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    TextView label(String value, int size) {
        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);

        view.setEllipsize(
            android.text.TextUtils.TruncateAt.END
        );

        return view;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager notifications =
            getSystemService(NotificationManager.class);

        notifications.createNotificationChannel(
            new NotificationChannel(
                "island",
                "Island overlay",
                NotificationManager.IMPORTANCE_LOW
            )
        );

        PendingIntent openHome = PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE
                | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification =
            new Notification.Builder(this, "island")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Island media controls active")
                .setContentText(
                    "Open Island Home settings to stop"
                )
                .setContentIntent(openHome)
                .build();

        startForeground(1, notification);

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager =
            getSystemService(WindowManager.class);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(12), dp(4), dp(12), dp(4));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(dp(28));

        panel.setBackground(background);
        panel.setElevation(dp(8));

        title = label("Island Home", 13);

        panel.addView(
            title,
            new LinearLayout.LayoutParams(-1, dp(29))
        );

        subtitle = label("", 12);
        subtitle.setTextColor(0xffbbbbbb);

        panel.addView(
            subtitle,
            new LinearLayout.LayoutParams(-1, dp(25))
        );

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        previous = label("Previous", 13);
        play = label("Play", 15);
        next = label("Next", 13);

        controls.addView(
            previous,
            new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        controls.addView(
            play,
            new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        controls.addView(
            next,
            new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        panel.addView(controls);

        panel.setOnClickListener(view -> {
            expanded = !expanded;
            render();
        });

        panel.setOnLongClickListener(view -> {
            try {
                PendingIntent action =
                    controller != null
                    ? controller.getSessionActivity()
                    : notificationAction;

                if (action != null) {
                    action.send();
                }
            } catch (Exception ignored) {
            }

            return true;
        });

        previous.setOnClickListener(
            view -> sendTransportCommand(0)
        );

        play.setOnClickListener(
            view -> sendTransportCommand(1)
        );

        next.setOnClickListener(
            view -> sendTransportCommand(2)
        );

        windowParams = new WindowManager.LayoutParams(
            dp(174),
            dp(38),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        windowParams.gravity =
            Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        windowParams.y = dp(
            getSharedPreferences("home", MODE_PRIVATE)
                .getInt("islandY", 4)
        );

        try {
            windowManager.addView(panel, windowParams);
            instance = this;
        } catch (Exception error) {
            stopSelf();
            return;
        }

        mediaManager =
            getSystemService(MediaSessionManager.class);

        connectMedia();
        render();
    }

    ComponentName notificationListenerComponent() {
        return new ComponentName(
            this,
            IslandListener.class
        );
    }

    public void connectMedia() {
        if (mediaManager == null) {
            return;
        }

        try {
            mediaManager.removeOnActiveSessionsChangedListener(
                sessionsListener
            );

            mediaManager.addOnActiveSessionsChangedListener(
                sessionsListener,
                notificationListenerComponent(),
                handler
            );

            refreshSessions();
        } catch (SecurityException error) {
            notice = "Enable notification access";
            body = "Island Home > Settings";
            render();
        }
    }

    void refreshSessions() {
        if (mediaManager == null) {
            return;
        }

        try {
            chooseSession(
                mediaManager.getActiveSessions(
                    notificationListenerComponent()
                )
            );
        } catch (SecurityException error) {
            chooseSession(Collections.emptyList());
        }
    }

    void chooseSession(List<MediaController> sessions) {
        MediaController selected = null;

        if (sessions != null) {
            for (MediaController candidate : sessions) {
                PlaybackState state =
                    candidate.getPlaybackState();

                if (
                    state != null
                    && state.getState()
                        == PlaybackState.STATE_PLAYING
                ) {
                    selected = candidate;
                    break;
                }
            }

            if (selected == null && !sessions.isEmpty()) {
                selected = sessions.get(0);
            }
        }

        if (controller != null) {
            try {
                controller.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {
            }
        }

        controller = selected;

        if (controller != null) {
            controller.registerCallback(
                mediaCallback,
                handler
            );
        }

        render();
    }

    void sendTransportCommand(int command) {
        if (controller == null) {
            return;
        }

        try {
            MediaController.TransportControls transport =
                controller.getTransportControls();

            if (command == 0) {
                transport.skipToPrevious();
            } else if (command == 2) {
                transport.skipToNext();
            } else {
                PlaybackState state =
                    controller.getPlaybackState();

                if (
                    state != null
                    && state.getState()
                        == PlaybackState.STATE_PLAYING
                ) {
                    transport.pause();
                } else {
                    transport.play();
                }
            }
        } catch (Exception error) {
            Toast.makeText(
                this,
                "Player did not accept control",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    void setControlEnabled(TextView view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.35f);
    }

    void render() {
        if (panel == null || windowParams == null) {
            return;
        }

        String name = notice;
        String description = body;

        PlaybackState playbackState = null;

        if (controller != null) {
            MediaMetadata metadata =
                controller.getMetadata();

            name = metadata == null
                ? controller.getPackageName()
                : metadata.getString(
                    MediaMetadata.METADATA_KEY_TITLE
                );

            if (name == null || name.isEmpty()) {
                name = controller.getPackageName();
            }

            description = metadata == null
                ? ""
                : metadata.getString(
                    MediaMetadata.METADATA_KEY_ARTIST
                );

            playbackState = controller.getPlaybackState();
        }

        title.setText(name);

        subtitle.setText(
            description == null ? "" : description
        );

        subtitle.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        controls.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        long actions = playbackState == null
            ? 0
            : playbackState.getActions();

        boolean playing =
            playbackState != null
            && playbackState.getState()
                == PlaybackState.STATE_PLAYING;

        play.setText(playing ? "Pause" : "Play");

        setControlEnabled(
            previous,
            (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0
        );

        setControlEnabled(
            next,
            (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0
        );

        long requiredAction = playing
            ? PlaybackState.ACTION_PAUSE
            : PlaybackState.ACTION_PLAY;

        boolean canToggle =
            controller != null
            && (
                (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0
                || (actions & requiredAction) != 0
            );

        setControlEnabled(play, canToggle);

        int expandedWidth = Math.min(
            320,
            getResources().getConfiguration().screenWidthDp - 20
        );

        windowParams.width =
            dp(expanded ? expandedWidth : 174);

        windowParams.height =
            dp(expanded ? 112 : 38);

        try {
            windowManager.updateViewLayout(
                panel,
                windowParams
            );
        } catch (Exception ignored) {
        }
    }

    public void update(
        String notificationTitle,
        String notificationBody,
        PendingIntent action
    ) {
        handler.post(() -> {
            notice = notificationTitle;
            body = notificationBody;
            notificationAction = action;

            refreshSessions();
            render();
        });
    }

    public void reposition() {
        if (windowParams != null) {
            windowParams.y = dp(
                getSharedPreferences("home", MODE_PRIVATE)
                    .getInt("islandY", 4)
            );

            render();
        }
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        instance = null;

        if (mediaManager != null) {
            try {
                mediaManager.removeOnActiveSessionsChangedListener(
                    sessionsListener
                );
            } catch (Exception ignored) {
            }
        }

        if (controller != null) {
            controller.unregisterCallback(mediaCallback);
        }

        handler.removeCallbacksAndMessages(null);

        if (windowManager != null && panel != null) {
            try {
                windowManager.removeView(panel);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
            }
