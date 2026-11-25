package com.example.islandhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    SharedPreferences prefs;
    LinearLayout root;
    LinearLayout dock;
    FrameLayout background;
    HorizontalScrollView pages;
    TextView dots;

    List<ResolveInfo> apps = new ArrayList<>();

    int columns;
    int rows;
    int pageWidth;

    boolean library = false;

    int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    GradientDrawable shape(int first, int second, int radius) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{first, second}
        );

        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xffffffff);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("home", MODE_PRIVATE);

        getWindow().setStatusBarColor(0xff17213e);
        getWindow().setNavigationBarColor(0xff241b49);

        loadApps();
        showHome();
    }

    void loadApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        apps = getPackageManager().queryIntentActivities(query, 0);

        apps.removeIf(
            app -> app.activityInfo.packageName.equals(getPackageName())
        );

        Collections.sort(
            apps,
            new ResolveInfo.DisplayNameComparator(getPackageManager())
        );
    }

    String key(ResolveInfo app) {
        return new ComponentName(
            app.activityInfo.packageName,
            app.activityInfo.name
        ).flattenToString();
    }

    ResolveInfo findApp(String savedKey) {
        for (ResolveInfo app : apps) {
            if (key(app).equals(savedKey)) {
                return app;
            }
        }

        return null;
    }

    void createBackground() {
        background = new FrameLayout(this);

        background.setBackground(
            shape(0xff132449, 0xffa15b80, 0)
        );

        String wallpaper = prefs.getString("wall", "");

        if (!wallpaper.isEmpty()) {
            try {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageURI(Uri.parse(wallpaper));

                background.addView(
                    image,
                    new FrameLayout.LayoutParams(-1, -1)
                );

                View shade = new View(this);
                shade.setBackgroundColor(0x33000000);

                background.addView(
                    shade,
                    new FrameLayout.LayoutParams(-1, -1)
                );
            } catch (Exception ignored) {
            }
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));

        background.addView(
            root,
            new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(background);
    }

    void showHome() {
        library = false;
        createBackground();

        int screenWidthDp =
            getResources().getConfiguration().screenWidthDp;

        int screenHeightDp =
            getResources().getConfiguration().screenHeightDp;

        columns = screenWidthDp >= 600 ? 6 : 4;
        rows = screenHeightDp < 550 ? 2 : 4;

        pageWidth =
            getResources().getDisplayMetrics().widthPixels - dp(24);

        LinearLayout header = new LinearLayout(this);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(36);
        clock.setTextColor(0xffffffff);

        header.addView(
            clock,
            new LinearLayout.LayoutParams(0, dp(58), 1)
        );

        TextView settingsButton = text("Settings", 14);

        header.addView(
            settingsButton,
            new LinearLayout.LayoutParams(dp(84), dp(58))
        );

        settingsButton.setOnClickListener(view -> showSettings());

        root.addView(header);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xffffffff);
        date.setTextSize(15);

        root.addView(date);

        pages = new HorizontalScrollView(this);
        pages.setFillViewport(false);
        pages.setHorizontalScrollBarEnabled(false);

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);

        pages.addView(
            strip,
            new FrameLayout.LayoutParams(-2, -1)
        );

        root.addView(
            pages,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        Set<String> hidden = prefs.getStringSet(
            "hidden",
            new HashSet<>()
        );

        ArrayList<ResolveInfo> visible = new ArrayList<>();

        for (ResolveInfo app : apps) {
            if (!hidden.contains(key(app))) {
                visible.add(app);
            }
        }

        int appsPerPage = columns * rows;

        int pageCount = Math.max(
            1,
            (visible.size() + appsPerPage - 1) / appsPerPage
        );

        for (int page = 0; page < pageCount; page++) {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(columns);
            grid.setRowCount(rows);

            strip.addView(
                grid,
                new LinearLayout.LayoutParams(pageWidth, -1)
            );

            int start = page * appsPerPage;
            int end = Math.min(
                visible.size(),
                (page + 1) * appsPerPage
            );

            for (int index = start; index < end; index++) {
                ResolveInfo app = visible.get(index);

                GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

                params.width = pageWidth / columns;
                params.height = dp(rows == 2 ? 82 : 94);

                grid.addView(createAppTile(app, true), params);
            }
        }

        dots = text("", 13);

        root.addView(
            dots,
            new LinearLayout.LayoutParams(-1, dp(22))
        );

        final int totalPages = pageCount;

        updateDots(0, totalPages);

        pages.setOnScrollChangeListener(
            (view, scrollX, scrollY, oldX, oldY) -> {
                int active = Math.min(
                    totalPages - 1,
                    (scrollX + pageWidth / 2) / pageWidth
                );

                updateDots(active, totalPages);
            }
        );

        pages.setOnTouchListener(new View.OnTouchListener() {
            final Handler handler = new Handler();

            final Runnable snap = () -> {
                int target =
                    ((pages.getScrollX() + pageWidth / 2) / pageWidth)
                    * pageWidth;

                pages.smoothScrollTo(target, 0);
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                handler.removeCallbacks(snap);

                if (
                    event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL
                ) {
                    handler.postDelayed(snap, 180);
                }

                return false;
            }
        });

        TextView search = text("Search / App Library", 14);

        search.setBackground(
            shape(0x44ffffff, 0x22ffffff, 22)
        );

        root.addView(
            search,
            new LinearLayout.LayoutParams(-1, dp(36))
        );

        search.setOnClickListener(view -> showLibrary());

        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(5), dp(6), dp(5), dp(6));

        dock.setBackground(
            shape(0x88ffffff, 0x44ffffff, 30)
        );

        LinearLayout.LayoutParams dockParams =
            new LinearLayout.LayoutParams(-1, dp(88));

        dockParams.topMargin = dp(10);

        root.addView(dock, dockParams);

        for (int index = 0; index < 4; index++) {
            final int slot = index;

            ResolveInfo app = findApp(
                prefs.getString("dock" + index, "")
            );

            View tile = app == null
                ? text("+", 28)
                : createAppTile(app, false);

            dock.addView(
                tile,
                new LinearLayout.LayoutParams(0, -1, 1)
            );

            if (app == null) {
                tile.setOnClickListener(view -> pickDockApp(slot));
            }

            tile.setOnLongClickListener(view -> {
                pickDockApp(slot);
                return true;
            });
        }
    }

    void updateDots(int activePage, int count) {
        StringBuilder value = new StringBuilder();

        for (int index = 0; index < count; index++) {
            value.append(index == activePage ? "● " : "○ ");
        }

        dots.setText(value.toString());
    }

    View createAppTile(ResolveInfo app, boolean showLabel) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(3), dp(4), dp(3), dp(3));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(
            app.loadIcon(getPackageManager())
        );

        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        icon.setBackground(
            shape(0x44ffffff, 0x11ffffff, 16)
        );

        icon.setClipToOutline(true);

        cell.addView(
            icon,
            new LinearLayout.LayoutParams(dp(55), dp(55))
        );

        if (showLabel) {
            TextView label = text(
                app.loadLabel(getPackageManager()).toString(),
                11
            );

            label.setMaxLines(1);
            label.setEllipsize(
                android.text.TextUtils.TruncateAt.END
            );

            label.setShadowLayer(3, 0, 1, 0xff000000);

            cell.addView(
                label,
                new LinearLayout.LayoutParams(-1, dp(23))
            );
        }

        cell.setOnClickListener(view -> launchApp(app));

        cell.setOnLongClickListener(view -> {
            new AlertDialog.Builder(this)
                .setTitle(app.loadLabel(getPackageManager()))
                .setItems(
                    new String[]{
                        "App info",
                        "Hide from Home (still in Library)"
                    },
                    (dialog, selected) -> {
                        if (selected == 0) {
                            open(
                                new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse(
                                        "package:"
                                        + app.activityInfo.packageName
                                    )
                                )
                            );
                        } else {
                            Set<String> hidden = new HashSet<>(
                                prefs.getStringSet(
                                    "hidden",
                                    new HashSet<>()
                                )
                            );

                            hidden.add(key(app));

                            prefs.edit()
                                .putStringSet("hidden", hidden)
                                .apply();

                            showHome();
                        }
                    }
                )
                .show();

            return true;
        });

        return cell;
    }

    void launchApp(ResolveInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        intent.setComponent(
            new ComponentName(
                app.activityInfo.packageName,
                app.activityInfo.name
            )
        );

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        );

        open(intent);
    }

    void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                this,
                "Cannot open: " + error.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    void pickDockApp(int slot) {
        String[] names = new String[apps.size()];

        for (int index = 0; index < names.length; index++) {
            names[index] = apps.get(index)
                .loadLabel(getPackageManager())
                .toString();
        }

        new AlertDialog.Builder(this)
            .setTitle("Dock slot " + (slot + 1))
            .setItems(names, (dialog, selected) -> {
                prefs.edit()
                    .putString(
                        "dock" + slot,
                        key(apps.get(selected))
                    )
                    .apply();

                showHome();
            })
            .setNeutralButton("Clear", (dialog, selected) -> {
                prefs.edit()
                    .remove("dock" + slot)
                    .apply();

                showHome();
            })
            .show();
    }

    void showLibrary() {
        library = true;
        createBackground();

        TextView back = text("‹ Home        App Library", 22);

        root.addView(
            back,
            new LinearLayout.LayoutParams(-1, dp(52))
        );

        back.setOnClickListener(view -> showHome());

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(0xffffffff);
        input.setHintTextColor(0xffdddddd);
        input.setHint("Search installed apps");

        root.addView(input);

        ScrollView scroll = new ScrollView(this);

        root.addView(
            scroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        GridLayout grid = new GridLayout(this);

        int count =
            getResources().getConfiguration().screenWidthDp >= 600
            ? 6 : 4;

        grid.setColumnCount(count);
        scroll.addView(grid);

        Runnable render = () -> {
            grid.removeAllViews();

            String query = input.getText()
                .toString()
                .toLowerCase(Locale.ROOT);

            for (ResolveInfo app : apps) {
                String name = app.loadLabel(getPackageManager())
                    .toString()
                    .toLowerCase(Locale.ROOT);

                if (!name.contains(query)) {
                    continue;
                }

                GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

                params.width =
                    (
                        getResources()
                            .getDisplayMetrics()
                            .widthPixels - dp(24)
                    ) / count;

                params.height = dp(94);

                grid.addView(
                    createAppTile(app, true),
                    params
                );
            }
        };

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                CharSequence value,
                int start,
                int count,
                int after
            ) {
            }

            @Override
            public void onTextChanged(
                CharSequence value,
                int start,
                int before,
                int count
            ) {
                render.run();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        render.run();
    }

    void showSettings() {
        String[] items = {
            "Set default home app",
            "Allow overlay permission",
            "Notification / media access",
            "Allow service notifications",
            "Start Island",
            "Stop Island",
            "Choose wallpaper",
            "Reset wallpaper",
            "Island vertical position",
            "Restore hidden Home apps",
            "System settings"
        };

        new AlertDialog.Builder(this)
            .setTitle("Island Home settings")
            .setItems(items, (dialog, selected) -> {
                switch (selected) {
                    case 0:
                        if (Build.VERSION.SDK_INT >= 29) {
                            RoleManager manager =
                                getSystemService(RoleManager.class);

                            if (
                                manager.isRoleAvailable(
                                    RoleManager.ROLE_HOME
                                )
                            ) {
                                startActivityForResult(
                                    manager.createRequestRoleIntent(
                                        RoleManager.ROLE_HOME
                                    ),
                                    1
                                );

                                break;
                            }
                        }

                        open(
                            new Intent(Settings.ACTION_HOME_SETTINGS)
                        );
                        break;

                    case 1:
                        open(
                            new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())
                            )
                        );
                        break;

                    case 2:
                        open(
                            new Intent(
                                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                            )
                        );
                        break;

                    case 3:
                        if (Build.VERSION.SDK_INT >= 33) {
                        
