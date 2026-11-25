package com.example.islandhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private JSONArray layout = new JSONArray();
    private LinearLayout root;
    private FrameLayout backdrop;
    private FrameLayout homeArea;
    private int page, columns = 4, capacity = 12, contentWidth;
    private boolean library, dragging, twoFinger;
    private String dragged;
    private float downX, downY, twoY;
    private long edgeTime;
    private Runnable authenticated;
    private static final int AUTH = 811, WALL = 812;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(0xffffffff);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
    private String key(ResolveInfo a) {
        return new ComponentName(a.activityInfo.packageName, a.activityInfo.name).flattenToString();
    }
    private ResolveInfo findApp(String id) {
        for (ResolveInfo a : apps) if (key(a).equals(id)) return a;
        return null;
    }
    private Set<String> hiddenSet() {
        return new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
    }
    private boolean hidden(String id) { return hiddenSet().contains(id); }
    private JSONObject item(String id) {
        if (id == null) return null;
        for (int i = 0; i < layout.length(); i++) {
            JSONObject o = layout.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
    private void save() { prefs.edit().putString("layout3", layout.toString()).apply(); }
    private void removeItem(String id) {
        JSONArray next = new JSONArray();
        for (int i = 0; i < layout.length(); i++) {
            JSONObject o = layout.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) next.put(o);
        }
        layout = next;
    }
    private void load() {
        apps.clear();
        for (ResolveInfo a : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (!getPackageName().equals(a.activityInfo.packageName)) apps.add(a);
        }
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(getPackageManager()));
        try { layout = new JSONArray(prefs.getString("layout3", "[]")); }
        catch (Exception e) { layout = new JSONArray(); }
        Set<String> known = new HashSet<>();
        for (int i = 0; i < layout.length(); i++) {
            JSONObject o = layout.optJSONObject(i);
            if (o == null) continue;
            if (o.optBoolean("folder")) {
                JSONArray children = o.optJSONArray("children");
                if (children != null) for (int j = 0; j < children.length(); j++) known.add(children.optString(j));
            } else known.add(o.optString("id"));
        }
        for (ResolveInfo a : apps) {
            if (!known.contains(key(a))) {
                try {
                    int target = maxPage();
                    if (pageItems(target).size() >= capacity) target++;
                    layout.put(new JSONObject().put("id", key(a)).put("page", target));
                } catch (Exception ignored) { }
            }
        }
        save();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", 0);
        columns = getResources().getConfiguration().screenWidthDp >= 600 ? 6 : 4;
        capacity = columns * 3;
        getWindow().setStatusBarColor(0xff173959);
        getWindow().setNavigationBarColor(0xff35304e);
        load();
        home();
    }
    private boolean visible(JSONObject o) {
        if (o.optBoolean("folder")) {
            JSONArray c = o.optJSONArray("children");
            if (c != null) for (int i = 0; i < c.length(); i++) {
                String id = c.optString(i);
                if (findApp(id) != null && !hidden(id)) return true;
            }
            return false;
        }
        String id = o.optString("id");
        return findApp(id) != null && !hidden(id);
    }
    private int maxPage() {
        int n = 0;
        for (int i = 0; i < layout.length(); i++) {
            JSONObject o = layout.optJSONObject(i);
            if (o != null && visible(o)) n = Math.max(n, o.optInt("page"));
        }
        return n;
    }
    private ArrayList<JSONObject> pageItems(int n) {
        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < layout.length(); i++) {
            JSONObject o = layout.optJSONObject(i);
            if (o != null && visible(o) && o.optInt("page") == n) list.add(o);
        }
        return list;
    }
    private void shell() {
        backdrop = new FrameLayout(this) {
            @Override public boolean dispatchDragEvent(DragEvent e) {
                int action = e.getAction();
                if (action == DragEvent.ACTION_DRAG_STARTED) {
                    dragging = true;
                    edgeTime = System.currentTimeMillis();
                }
                if (action == DragEvent.ACTION_DRAG_LOCATION && !library && dragged != null
                        && System.currentTimeMillis() - edgeTime > 900) {
                    int target = page;
                    if (e.getX() > getWidth() - dp(35) && page < maxPage() + 1) target++;
                    else if (e.getX() < dp(35) && page > 0) target--;
                    if (target != page) {
                        page = target;
                        edgeTime = System.currentTimeMillis();
                        post(() -> { if (dragging && dragged != null && !library) renderPage(); });
                    }
                }
                boolean handled = super.dispatchDragEvent(e);
                if (action == DragEvent.ACTION_DRAG_ENDED) {
                    dragging = false;
                    dragged = null;
                }
                return handled;
            }
        };
        backdrop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff173959, 0xff634b85, 0xffb46980}));
        String wall = prefs.getString("wall", "");
        if (!wall.isEmpty()) {
            try {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageURI(Uri.parse(wall));
                backdrop.addView(image, new FrameLayout.LayoutParams(-1, -1));
            } catch (Exception ignored) { }
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        backdrop.addView(root, new FrameLayout.LayoutParams(-1, -1));
        contentWidth = getResources().getDisplayMetrics().widthPixels - dp(24);
        root.setOnLongClickListener(v -> { deviceAuth(this::settings); return true; });
        root.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DROP) moveToPage(dragged, page);
            return true;
        });
        setContentView(backdrop);
    }
    private void home() {
        library = false;
        shell();
        LinearLayout cards = new LinearLayout(this);
        root.addView(cards, new LinearLayout.LayoutParams(-1, dp(104)));
        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        calendar.setGravity(Gravity.CENTER);
        calendar.setBackground(shape(0xddffffff, 22));
        cards.addView(calendar, new LinearLayout.LayoutParams(0, -1, 1));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, d MMM"); date.setFormat24Hour("EEE, d MMM");
        date.setTextColor(0xffb33d51); date.setTextSize(18); date.setGravity(Gravity.CENTER);
        calendar.addView(date);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a"); clock.setFormat24Hour("HH:mm");
        clock.setTextColor(0xff242438); clock.setTextSize(28); clock.setGravity(Gravity.CENTER);
        calendar.addView(clock);
        LinearLayout batteryCard = new LinearLayout(this);
        batteryCard.setGravity(Gravity.CENTER); batteryCard.setBackground(shape(0xddffffff, 22));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -1, 1); bp.leftMargin = dp(8);
        cards.addView(batteryCard, bp);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra("level", -1);
        int scale = battery == null ? 100 : battery.getIntExtra("scale", 100);
        TextView bt = text(level < 0 ? "Battery" : "Battery\n" + Math.round(100f * level / Math.max(1, scale)) + "%", 23);
        bt.setTextColor(0xff258461); batteryCard.addView(bt);
        calendar.setOnLongClickListener(v -> { deviceAuth(this::settings); return true; });
        batteryCard.setOnLongClickListener(v -> { deviceAuth(this::settings); return true; });
        homeArea = new FrameLayout(this);
        root.addView(homeArea, new LinearLayout.LayoutParams(-1, 0, 1));
        renderPage();
        TextView search = text("Search · App Library", 14);
        search.setBackground(shape(0x55ffffff, 20));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(34)));
        search.setOnClickListener(v -> showLibrary());
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER); dock.setBackground(shape(0x77ffffff, 26));
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, dp(78)); d.topMargin = dp(8);
        root.addView(dock, d);
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            String id = prefs.getString("dock" + i, "");
            ResolveInfo a = findApp(id);
            View tile = a == null || hidden(id) ? text("+", 28) : appTile(a, false, 52);
            dock.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
            if (a == null || hidden(id)) tile.setOnClickListener(v -> deviceAuth(() -> pickDock(slot)));
            tile.setOnLongClickListener(v -> { deviceAuth(() -> pickDock(slot)); return true; });
        }
    }
    private void renderPage() {
        if (homeArea == null || library) return;
        homeArea.removeAllViews();
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        homeArea.addView(container, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        container.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout grid = new GridLayout(this); grid.setColumnCount(columns); grid.setMinimumHeight(dp(290));
        scroll.addView(grid);
        for (JSONObject o : pageItems(page)) {
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = contentWidth / columns; gp.height = dp(94);
            grid.addView(homeTile(o), gp);
        }
        grid.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DROP) moveToPage(dragged, page);
            return true;
        });
        container.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DROP) moveToPage(dragged, page);
            return true;
        });
        container.addView(text("Page " + (page + 1) + " / " + (Math.max(page, maxPage()) + 1)
                + "  ·  swipe left/right", 12), new LinearLayout.LayoutParams(-1, dp(28)));
    }
    private View appTile(ResolveInfo a, boolean label, int size) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this); image.setImageDrawable(a.loadIcon(getPackageManager()));
        image.setBackground(shape(0x66ffffff, 15)); image.setClipToOutline(true);
        cell.addView(image, new LinearLayout.LayoutParams(dp(size), dp(size)));
        if (label) {
            TextView t = text(a.loadLabel(getPackageManager()).toString(), 11);
            t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(t, new LinearLayout.LayoutParams(-1, dp(24)));
        }
        cell.setOnClickListener(v -> launch(key(a), false));
        cell.setOnLongClickListener(v -> { appMenu(key(a)); return true; });
        return cell;
    }
    private View homeTile(JSONObject o) {
        String id = o.optString("id"); View tile;
        if (o.optBoolean("folder")) {
            LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            GridLayout preview = new GridLayout(this); preview.setColumnCount(2); preview.setBackground(shape(0x88ffffff, 16));
            JSONArray children = o.optJSONArray("children"); int count = 0;
            if (children != null) for (int i = 0; i < children.length() && count < 4; i++) {
                ResolveInfo a = findApp(children.optString(i)); if (a == null || hidden(key(a))) continue;
                ImageView image = new ImageView(this); image.setImageDrawable(a.loadIcon(getPackageManager()));
                preview.addView(image, new android.view.ViewGroup.LayoutParams(dp(23), dp(23))); count++;
            }
            cell.addView(preview, new LinearLayout.LayoutParams(dp(54), dp(54)));
            cell.addView(text(o.optString("name", "Folder"), 11));
            cell.setOnClickListener(v -> folder(o)); tile = cell;
        } else tile = appTile(findApp(id), true, 54);
        tile.setOnLongClickListener(v -> {
            dragged = id;
            boolean started = v.startDragAndDrop(ClipData.newPlainText("item", id), new View.DragShadowBuilder(v), id, 0);
            dragging = started;
            if (!started) dragged = null;
            return true;
        });
        tile.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DROP) {
                if (dragged != null && !id.equals(dragged)) combine(dragged, id);
            }
            return true;
        });
        return tile;
    }
    private void moveToPage(String id, int target) {
        JSONObject o = item(id); if (o == null) return;
        try { o.put("page", pageItems(target).size() >= capacity && o.optInt("page") != target ? target + 1 : target); save(); }
        catch (Exception ignored) { }
        root.post(() -> { if (!library) renderPage(); });
    }
    private void combine(String source, String target) {
        JSONObject s = item(source), t = item(target);
        if (s == null || t == null) return;
        if (s.optBoolean("folder")) { toast("Folder merging is not supported"); return; }
        try {
            if (t.optBoolean("folder")) { t.getJSONArray("children").put(source); removeItem(source); }
            else {
                JSONObject f = new JSONObject().put("id", "folder:" + UUID.randomUUID()).put("folder", true)
                        .put("name", "Folder").put("page", t.optInt("page"))
                        .put("children", new JSONArray().put(target).put(source));
                removeItem(source); removeItem(target); layout.put(f);
            }
            save();
        } catch (Exception ignored) { }
        root.post(() -> { if (!library) renderPage(); });
    }
    private void folder(JSONObject f) {
        ScrollView scroll = new ScrollView(this); GridLayout grid = new GridLayout(this); grid.setColumnCount(3); scroll.addView(grid);
        JSONArray children = f.optJSONArray("children");
        if (children != null) for (int i = 0; i < children.length(); i++) {
            String id = children.optString(i); ResolveInfo a = findApp(id); if (a == null || hidden(id)) continue;
            View tile = appTile(a, true, 48);
            tile.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setItems(new String[]{"Move to Home", "App info / Uninstall"}, (d, which) -> {
                    if (which == 1) { appMenu(id); return; }
                    try {
                        JSONArray next = new JSONArray();
                        for (int j = 0; j < children.length(); j++) if (!id.equals(children.optString(j))) next.put(children.optString(j));
                        f.put("children", next);
                        layout.put(new JSONObject().put("id", id).put("page", page));
                        if (next.length() == 0) removeItem(f.optString("id")); save(); home();
                    } catch (Exception ignored) { }
                }).show(); return true;
            });
            grid.addView(tile, new android.view.ViewGroup.LayoutParams(dp(88), dp(88)));
        }
        new AlertDialog.Builder(this).setTitle(f.optString("name", "Folder")).setView(scroll)
                .setNeutralButton("Rename", (d, which) -> {
                    EditText input = new EditText(this); input.setText(f.optString("name", "Folder"));
                    new AlertDialog.Builder(this).setTitle("Folder name").setView(input).setPositiveButton("Save", (x, y) -> {
                        try { f.put("name", input.getText().toString()); save(); home(); } catch (Exception ignored) { }
                    }).setNegativeButton("Cancel", null).show();
                }).setPositiveButton("Close", null).show();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (!dragging) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { downX = e.getX(); downY = e.getY(); twoFinger = false; }
            if (action == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
                twoFinger = true; twoY = (e.getY(0) + e.getY(1)) / 2;
            }
            if (twoFinger && e.getPointerCount() >= 2 && action == MotionEvent.ACTION_MOVE
                    && (e.getY(0) + e.getY(1)) / 2 - twoY > dp(85)) {
                cancelChild(e); twoFinger = false; pin(false, this::hiddenScreen); return true;
            }
            if (!twoFinger && action == MotionEvent.ACTION_UP && !library
                    && Math.abs(e.getX() - downX) > dp(75)
                    && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY) * 1.5f) {
                cancelChild(e);
                if (e.getX() < downX) { if (page < maxPage()) { page++; renderPage(); } else showLibrary(); }
                else if (page > 0) { page--; renderPage(); }
                return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }
    private void cancelChild(MotionEvent event) {
        MotionEvent copy = MotionEvent.obtain(event); copy.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(copy); copy.recycle();
    }
    private void appMenu(String id) {
        ResolveInfo a = findApp(id); if (a == null || hidden(id)) return;
        new AlertDialog.Builder(this).setTitle(a.loadLabel(getPackageManager()))
                .setItems(new String[]{"App info", "Uninstall"}, (d, which) -> open(new Intent(
                        which == 0 ? Settings.ACTION_APPLICATION_DETAILS_SETTINGS : Intent.ACTION_DELETE,
                        Uri.parse("package:" + a.activityInfo.packageName)))).show();
    }
    private String category(ResolveInfo a) {
        int c = Build.VERSION.SDK_INT >= 26 ? a.activityInfo.applicationInfo.category : -1;
        switch (c) {
            case ApplicationInfo.CATEGORY_GAME: return "Games";
            case ApplicationInfo.CATEGORY_AUDIO: case ApplicationInfo.CATEGORY_VIDEO: return "Entertainment";
            case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Productivity";
            case ApplicationInfo.CATEGORY_IMAGE: return "Photos";
            case ApplicationInfo.CATEGORY_MAPS: return "Travel";
            case ApplicationInfo.CATEGORY_NEWS: return "Information";
            default: return "Utilities & Other";
        }
    }
    private void showLibrary() {
        library = true; shell();
        LinearLayout header = new LinearLayout(this);
        TextView back = text("‹ Home", 16), title = text("App Library", 24), menu = text("⋮", 30);
        header.addView(back, new LinearLayout.LayoutParams(dp(70), dp(48)));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(header);
        back.setOnClickListener(v -> home());
        menu.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setItems(new String[]{"Hide apps", "Set / change hidden-area PIN"}, (d, n) -> {
                    if (n == 0) hidePicker(null); else deviceAuth(() -> pin(true, () -> toast("PIN saved")));
                }).show());
        EditText search = new EditText(this); search.setTextColor(-1); search.setHintTextColor(0xffdddddd); search.setHint("Search"); root.addView(search);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        Runnable render = () -> {
            body.removeAllViews(); TreeMap<String, ArrayList<ResolveInfo>> groups = new TreeMap<>();
            String q = search.getText().toString().toLowerCase(Locale.ROOT);
            for (ResolveInfo a : apps) if (!hidden(key(a)) && a.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(q)) {
                String cat = category(a);
                if (!groups.containsKey(cat)) groups.put(cat, new ArrayList<ResolveInfo>());
                groups.get(cat).add(a);
            }
            LinearLayout row = null; int index = 0;
            for (String name : groups.keySet()) {
                if (index++ % 2 == 0) { row = new LinearLayout(this); body.addView(row); }
                ArrayList<ResolveInfo> list = groups.get(name);
                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(7), dp(7), dp(7), dp(7)); card.setBackground(shape(0x66ffffff, 24));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(176), 1); cp.setMargins(dp(4), dp(6), dp(4), dp(6)); row.addView(card, cp);
                LinearLayout caption = new LinearLayout(this);
                TextView label = text(name, 12), dots = text("⋮", 24);
                caption.addView(label, new LinearLayout.LayoutParams(0, dp(32), 1));
                caption.addView(dots, new LinearLayout.LayoutParams(dp(30), dp(32))); card.addView(caption);
                dots.setOnClickListener(v -> new AlertDialog.Builder(this).setItems(new String[]{"Hide apps in " + name}, (d, n) -> hidePicker(list)).show());
                label.setOnClickListener(v -> categoryScreen(name, list));
                GridLayout grid = new GridLayout(this); grid.setColumnCount(2); card.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
                for (int j = 0; j < Math.min(4, list.size()); j++) grid.addView(appTile(list.get(j), false, 42),
                        new android.view.ViewGroup.LayoutParams(Math.max(dp(44), (contentWidth / 2 - dp(22)) / 2), dp(54)));
                TextView more = text("All " + list.size() + " apps ›", 11); card.addView(more, new LinearLayout.LayoutParams(-1, dp(22)));
                more.setOnClickListener(v -> categoryScreen(name, list));
            }
            if (index % 2 == 1 && row != null) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            public void afterTextChanged(Editable e) { }
        }); render.run();
    }
    private void categoryScreen(String name, ArrayList<ResolveInfo> list) {
        ScrollView scroll = new ScrollView(this); GridLayout grid = new GridLayout(this); grid.setColumnCount(3); scroll.addView(grid);
        for (ResolveInfo a : list) if (!hidden(key(a))) grid.addView(appTile(a, true, 48), new android.view.ViewGroup.LayoutParams(dp(88), dp(88)));
        new AlertDialog.Builder(this).setTitle(name).setView(scroll).setNeutralButton("Hide apps", (d, n) -> hidePicker(list)).setPositiveButton("Close", null).show();
    }
    private void hidePicker(ArrayList<ResolveInfo> filter) {
        ArrayList<ResolveInfo> list = new ArrayList<>();
        for (ResolveInfo a : filter == null ? apps : filter) if (!hidden(key(a))) list.add(a);
        String[] labels = new String[list.size()]; boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Hide apps · no device lock")
                .setMultiChoiceItems(labels, checked, (d, i, value) -> checked[i] = value)
                .setPositiveButton("Hide", (d, n) -> {
                    Set<String> set = hiddenSet(); for (int i = 0; i < checked.length; i++) if (checked[i]) set.add(key(list.get(i)));
                    prefs.edit().putStringSet("hidden", set).apply(); showLibrary();
                }).setNegativeButton("Cancel", null).show();
    }
    private String derive(String value, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), android.util.Base64.decode(salt, 0), 120000, 256);
        try { return android.util.Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), 2); }
        finally { spec.clearPassword(); }
    }
    private void pin(boolean reset, Runnable success) {
        if (!reset && prefs.getString("pinHash", "").isEmpty()) { toast("Set hidden-area PIN in Library menu first"); return; }
        if (!reset && prefs.getLong("pinUntil", 0) > System.currentTimeMillis()) { toast("Too many attempts. Wait one minute."); return; }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        EditText first = new EditText(this); first.setInputType(18); first.setHint(reset ? "New PIN: at least 4 digits" : "PIN"); fields.addView(first);
        EditText confirmation = new EditText(this); confirmation.setInputType(18); confirmation.setHint("Confirm PIN"); if (reset) fields.addView(confirmation);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(reset ? "Set launcher PIN" : "Hidden apps")
                .setView(fields).setPositiveButton(reset ? "Save" : "Unlock", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = first.getText().toString();
            try {
                if (reset) {
                    if (value.length() < 4 || !value.matches("[0-9]+") || !value.equals(confirmation.getText().toString())) {
                        first.setError("Use matching PINs, at least 4 digits"); return;
                    }
                    byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
                    String salt = android.util.Base64.encodeToString(bytes, 2);
                    prefs.edit().putString("pinSalt", salt).putString("pinHash", derive(value, salt)).putInt("pinFails", 0).putLong("pinUntil", 0).apply();
                    dialog.dismiss(); success.run();
                } else {
                    String hash = derive(value, prefs.getString("pinSalt", ""));
                    if (MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            prefs.getString("pinHash", "").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        prefs.edit().putInt("pinFails", 0).putLong("pinUntil", 0).apply(); dialog.dismiss(); success.run();
                    } else {
                        int fails = prefs.getInt("pinFails", 0) + 1;
                        prefs.edit().putInt("pinFails", fails >= 5 ? 0 : fails).putLong("pinUntil", fails >= 5 ? System.currentTimeMillis() + 60000 : 0).apply();
                        first.setError("Wrong PIN"); if (fails >= 5) { dialog.dismiss(); toast("Too many attempts. Wait one minute."); }
                    }
                }
            } catch (Exception e) { toast("PIN operation failed"); }
        })); dialog.show();
    }
    private void hiddenScreen() {
        ArrayList<ResolveInfo> list = new ArrayList<>(); for (ResolveInfo a : apps) if (hidden(key(a))) list.add(a);
        String[] labels = new String[list.size()]; for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Hidden apps · unlocked").setItems(labels, (d, i) -> launch(key(list.get(i)), true))
                .setNeutralButton("Unhide apps", (d, n) -> {
                    boolean[] checked = new boolean[list.size()];
                    new AlertDialog.Builder(this).setTitle("Unhide apps").setMultiChoiceItems(labels, checked, (x, i, value) -> checked[i] = value)
                            .setPositiveButton("Unhide", (x, y) -> {
                                Set<String> set = hiddenSet(); for (int i = 0; i < checked.length; i++) if (checked[i]) set.remove(key(list.get(i)));
                                prefs.edit().putStringSet("hidden", set).apply(); home();
                            }).setNegativeButton("Cancel", null).show();
                }).setNegativeButton("Close", null).show();
    }
    private void launch(String id, boolean unlocked) {
        if (hidden(id) && !unlocked) return; ResolveInfo a = findApp(id); if (a == null) return;
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(a.activityInfo.packageName, a.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void open(Intent intent) { try { startActivity(intent); } catch (Exception e) { toast("Action unavailable"); } }
    private void deviceAuth(Runnable next) {
        KeyguardManager manager = getSystemService(KeyguardManager.class);
        if (manager == null || !manager.isDeviceSecure()) {
            new AlertDialog.Builder(this).setMessage("Set device screen lock for protected settings and PIN reset.")
                    .setPositiveButton("Security settings", (d, n) -> open(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                    .setNegativeButton("Cancel", null).show(); return;
        }
        Intent intent = manager.createConfirmDeviceCredentialIntent("Launcher settings", "Confirm device credential");
        if (intent != null) { authenticated = next; startActivityForResult(intent, AUTH); }
    }
    private void pickDock(int slot) {
        ArrayList<ResolveInfo> list = new ArrayList<>(); for (ResolveInfo a : apps) if (!hidden(key(a))) list.add(a);
        String[] labels = new String[list.size()]; for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Dock app").setItems(labels, (d, i) -> {
            prefs.edit().putString("dock" + slot, key(list.get(i))).apply(); home();
        }).setNeutralButton("Clear", (d, n) -> { prefs.edit().remove("dock" + slot).apply(); home(); }).setNegativeButton("Cancel", null).show();
    }
    private void settings() {
        new AlertDialog.Builder(this).setTitle("Launcher settings").setItems(new String[]{"Default Home", "Overlay access",
                "Notification / media access", "Service notification permission", "Start Island", "Stop Island", "Wallpaper", "Set / reset hidden PIN"}, (d, which) -> {
            switch (which) {
                case 0: open(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
                case 1: open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); break;
                case 2: open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); break;
                case 3: if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 813); break;
                case 4:
                    if (!Settings.canDrawOverlays(this)) { toast("Grant overlay access first"); break; }
                    try {
                        Intent service = new Intent(this, IslandService.class);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                    } catch (Exception e) { toast("Could not start Island: " + e.getMessage()); }
                    break;
                case 5: stopService(new Intent(this, IslandService.class)); break;
                case 6: startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), WALL); break;
                case 7: pin(true, () -> toast("PIN saved")); break;
            }
        }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == AUTH) { Runnable next = authenticated; authenticated = null; if (result == RESULT_OK && next != null) next.run(); }
        if (request == WALL && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            prefs.edit().putString("wall", uri.toString()).apply(); home();
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); authenticated = null; dragging = false; dragged = null; load(); home();
    }
    @Override public void onBackPressed() {
        if (library) home(); else if (page > 0) { page = 0; renderPage(); }
    }
}
