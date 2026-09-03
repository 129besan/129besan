package dev.besan.browserbrake;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final int REQ_ACTIVITY = 1003;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView liveStatus;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationController.ensureChannel(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildUi() {
        if (RuleConfig.includeBrowsers(this)) {
            Set<String> custom = RuleConfig.customPackages(this);
            if (custom.removeAll(TargetApps.browserPackages(this))) {
                RuleConfig.setCustomPackages(this, custom);
            }
        }

        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text("Browser Brake", 30f));
        TextView version = text("v0.3.0-alpha3", 13f);
        version.setPadding(0, 0, 0, dp(12));
        root.addView(version);

        liveStatus = text("", 16f);
        liveStatus.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(liveStatus);

        if (Prefs.STATE_READY.equals(Prefs.state(this))) {
            Button readyUse = button("解除条件達成済み：利用する");
            readyUse.setOnClickListener(v -> startActivity(new Intent(this, UnlockGateActivity.class)));
            root.addView(readyUse);
            Button readyDecline = button("解除条件達成済み：今回はやめる");
            readyDecline.setOnClickListener(v -> {
                Prefs.declineReady(this);
                NotificationController.cancel(this);
                buildUi();
            });
            root.addView(readyDecline);
        }

        section(root, "セットアップ");
        Button a11y = button("Accessibility を有効にする");
        a11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(a11y);

        Button notifications = button("通知を許可する");
        notifications.setOnClickListener(v -> requestNotifications());
        root.addView(notifications);

        Button locationSettings = button("位置情報の権限を設定する");
        locationSettings.setOnClickListener(v -> {
            ensureLocationPermission();
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(locationSettings);

        if (Build.VERSION.SDK_INT >= 29) {
            Button activity = button("歩数の権限を許可する");
            activity.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_ACTIVITY));
            root.addView(activity);
        }

        CheckBox enabled = new CheckBox(this);
        enabled.setText("このRuleを有効にする");
        enabled.setChecked(Prefs.isLockEnabled(this));
        enabled.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setLockEnabled(this, checked);
            if (!checked) NotificationController.cancel(this);
            refreshStatus();
        });
        root.addView(enabled);

        section(root, "Rule");
        EditText ruleName = new EditText(this);
        ruleName.setHint("Rule名");
        ruleName.setSingleLine(true);
        ruleName.setText(RuleConfig.ruleName(this));
        ruleName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) RuleConfig.setRuleName(this, ruleName.getText().toString());
        });
        root.addView(ruleName);

        section(root, "対象アプリ");
        CheckBox browsers = new CheckBox(this);
        browsers.setText("Browsersをまとめて対象にする");
        browsers.setChecked(RuleConfig.includeBrowsers(this));
        browsers.setOnCheckedChangeListener((b, checked) -> {
            RuleConfig.setIncludeBrowsers(this, checked);
            if (checked) {
                Set<String> custom = RuleConfig.customPackages(this);
                custom.removeAll(TargetApps.browserPackages(this));
                RuleConfig.setCustomPackages(this, custom);
            }
            buildUi();
        });
        root.addView(browsers);

        root.addView(text("追加一覧には、端末で起動できるアプリを表示します。Browsersで既に対象のブラウザは除外します。", 13f));
        Button appPicker = button("追加の対象アプリを選ぶ（現在 " + RuleConfig.customPackages(this).size() + "個）");
        appPicker.setOnClickListener(v -> showAppPicker());
        root.addView(appPicker);

        section(root, "場所");
        CheckBox allPlaces = new CheckBox(this);
        allPlaces.setText("すべての場所で有効（ALL）");
        allPlaces.setChecked(PlaceStore.isAllPlaces(this));
        allPlaces.setOnCheckedChangeListener((b, checked) -> {
            PlaceStore.setAllPlaces(this, checked);
            buildUi();
        });
        root.addView(allPlaces);

        if (!PlaceStore.isAllPlaces(this)) {
            Set<String> selected = PlaceStore.selectedIds(this);
            for (Place place : PlaceStore.all(this)) {
                CheckBox cb = new CheckBox(this);
                cb.setText(place.name + "（半径 " + Math.round(place.radiusM) + "m）");
                cb.setChecked(selected.contains(place.id));
                cb.setOnCheckedChangeListener((button, checked) -> {
                    Set<String> ids = PlaceStore.selectedIds(this);
                    if (checked) ids.add(place.id); else ids.remove(place.id);
                    PlaceStore.setSelectedIds(this, ids);
                });
                root.addView(cb);
            }
            Button addPlace = button("現在地を場所として追加");
            addPlace.setOnClickListener(v -> promptAddPlace());
            root.addView(addPlace);
            if (!PlaceStore.all(this).isEmpty()) {
                Button manage = button("登録場所を削除する");
                manage.setOnClickListener(v -> showDeletePlaceDialog());
                root.addView(manage);
            }
        }

        section(root, "解除条件");
        CheckBox wait = check("待つ", RuleConfig.challengeWait(this));
        wait.setOnCheckedChangeListener((b, v) -> RuleConfig.setChallengeWait(this, v));
        root.addView(wait);
        addTimeSeek(root, "待つ時間", RuleConfig.waitMs(this), RuleConfig::setWaitMs,
                new long[]{15_000L,30_000L,60_000L,2*60_000L,3*60_000L,5*60_000L,10*60_000L,15*60_000L,30*60_000L});

        CheckBox phone = check("スマホ休憩（操作するとやり直し）", RuleConfig.challengePhoneBreak(this));
        phone.setOnCheckedChangeListener((b, v) -> RuleConfig.setChallengePhoneBreak(this, v));
        root.addView(phone);
        addTimeSeek(root, "スマホ休憩時間", RuleConfig.phoneBreakMs(this), RuleConfig::setPhoneBreakMs,
                new long[]{30_000L,60_000L,2*60_000L,3*60_000L,5*60_000L,7*60_000L,10*60_000L,15*60_000L,20*60_000L,30*60_000L});

        CheckBox walk = check("歩く", RuleConfig.challengeWalk(this));
        walk.setOnCheckedChangeListener((b, v) -> RuleConfig.setChallengeWalk(this, v));
        root.addView(walk);
        addIntSeek(root, "必要歩数", RuleConfig.walkSteps(this), RuleConfig::setWalkSteps,
                new int[]{25,50,100,150,200,300,500,750,1000,1500,2000}, "歩");

        RadioGroup combine = new RadioGroup(this);
        RadioButton all = new RadioButton(this); all.setText("複数選択時: すべて達成（ALL）");
        RadioButton any = new RadioButton(this); any.setText("複数選択時: どれか1つ（ANY）");
        combine.addView(all); combine.addView(any);
        if (RuleConfig.challengeAll(this)) all.setChecked(true); else any.setChecked(true);
        combine.setOnCheckedChangeListener((g, id) -> RuleConfig.setChallengeAll(this, all.isChecked()));
        root.addView(combine);

        section(root, "READY");
        root.addView(text("解除条件を達成しても自動では解放しません。通知またはこの画面から「利用する / 今回はやめる」を決めます。対象アプリを開き直す必要はありません。", 14f));
        addTimeSeekAllowNone(root, "解除資格の有効時間（デフォルト: 制限なし）", RuleConfig.readyTimeoutMs(this), RuleConfig::setReadyTimeoutMs,
                new long[]{0L,5*60_000L,15*60_000L,30*60_000L,60*60_000L,2*60*60_000L,6*60*60_000L});

        section(root, "Session");
        CheckBox ask = check("利用開始前に「何分使う？」を聞く", RuleConfig.askSessionDuration(this));
        ask.setOnCheckedChangeListener((b, v) -> RuleConfig.setAskSessionDuration(this, v));
        root.addView(ask);
        addTimeSeek(root, "既定の1回利用時間", RuleConfig.defaultSessionUsageMs(this), RuleConfig::setDefaultSessionUsageMs,
                new long[]{1*60_000L,3*60_000L,5*60_000L,10*60_000L,15*60_000L,20*60_000L,30*60_000L,45*60_000L,60*60_000L});
        addTimeSeek(root, "Session有効期限（2つ目の時計）", RuleConfig.sessionWindowMs(this), RuleConfig::setSessionWindowMs,
                new long[]{5*60_000L,10*60_000L,15*60_000L,30*60_000L,45*60_000L,60*60_000L,2*60*60_000L,4*60*60_000L});

        section(root, "1日の制限");
        addTimeSeekAllowNone(root, "1日の実使用時間", RuleConfig.dailyUsageLimitMs(this), RuleConfig::setDailyUsageLimitMs,
                new long[]{-1L,15*60_000L,30*60_000L,45*60_000L,60*60_000L,90*60_000L,2*60*60_000L,3*60*60_000L,4*60*60_000L});
        addIntSeekAllowUnlimited(root, "1日の利用回数", RuleConfig.dailySessionLimit(this), RuleConfig::setDailySessionLimit,
                new int[]{-1,1,2,3,4,5,6,8,10,15,20});

        section(root, "利用後の休憩");
        addTimeSeekAllowNone(root, "次の利用まで", RuleConfig.recoveryMs(this), RuleConfig::setRecoveryMs,
                new long[]{0L,30_000L,60_000L,3*60_000L,5*60_000L,10*60_000L,15*60_000L,30*60_000L});

        section(root, "Escalation");
        RadioGroup esc = new RadioGroup(this);
        RadioButton off = new RadioButton(this); off.setText("OFF");
        RadioButton standard = new RadioButton(this); standard.setText("Standard");
        RadioButton strong = new RadioButton(this); strong.setText("Strong");
        esc.addView(off); esc.addView(standard); esc.addView(strong);
        String mode = RuleConfig.escalationMode(this);
        if (RuleConfig.ESC_OFF.equals(mode)) off.setChecked(true);
        else if (RuleConfig.ESC_STRONG.equals(mode)) strong.setChecked(true);
        else standard.setChecked(true);
        esc.setOnCheckedChangeListener((g, id) -> {
            if (off.isChecked()) RuleConfig.setEscalationMode(this, RuleConfig.ESC_OFF);
            else if (strong.isChecked()) RuleConfig.setEscalationMode(this, RuleConfig.ESC_STRONG);
            else RuleConfig.setEscalationMode(this, RuleConfig.ESC_STANDARD);
            refreshStatus();
        });
        root.addView(esc);
        root.addView(text(escalationPreview(), 14f));

        section(root, "今日の上限を超えた後");
        root.addView(text("alpha既定: 解除条件 ×5、時間系は最低10分・最大30分。利用できても最大3分。完全ロックは次版候補。", 14f));

        section(root, "状態確認");
        Button why = button("なぜブロックされた？ / 現在の状態");
        why.setOnClickListener(v -> showWhyDialog());
        root.addView(why);

        Button diagnostics = button("実機診断情報を見る");
        diagnostics.setOnClickListener(v -> showDiagnosticsDialog());
        root.addView(diagnostics);

        Button stopNow = button("現在のChallenge / READY / Sessionを終了して再ロック");
        stopNow.setOnClickListener(v -> {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            refreshStatus();
        });
        root.addView(stopNow);

        TextView note = text("v0.3-alphaは設計検証版です。時刻変更耐性、複数Rule、Schedule、設定弱化遅延、課金はまだ未実装です。", 13f);
        note.setPadding(0, dp(20), 0, dp(20));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    private void refreshStatus() {
        if (liveStatus == null) return;
        Prefs.ensureDailyReset(this);
        long timeLimit = RuleConfig.dailyUsageLimitMs(this);
        int sessionLimit = RuleConfig.dailySessionLimit(this);
        String usage = timeLimit < 0 ? NotificationController.format(Prefs.dailyUsageMs(this)) + " / 制限なし"
                : NotificationController.format(Prefs.dailyUsageMs(this)) + " / " + NotificationController.format(timeLimit);
        String sessions = sessionLimit < 0 ? Prefs.dailySessions(this) + " / 制限なし"
                : Prefs.dailySessions(this) + " / " + sessionLimit;
        liveStatus.setText(
                "Accessibility: " + (accessibilityEnabled() ? "ON" : "OFF") + "\n" +
                "Rule: " + (Prefs.isLockEnabled(this) ? "ON" : "OFF") + "\n" +
                "State: " + Prefs.state(this) + "\n" +
                "今日の実使用: " + usage + "\n" +
                "今日の利用回数: " + sessions + "\n" +
                "Escalation: Level " + Prefs.escalationLevel(this) + "\n" +
                (Prefs.isOverDailyLimit(this) ? "今日の上限を超えています\n" : "") +
                (Prefs.STATE_SESSION.equals(Prefs.state(this))
                        ? "Session実使用残り: " + NotificationController.format(Prefs.liveSessionUsageRemainingMs(this)) + "\n" +
                          "Session利用権: " + NotificationController.format(Math.max(0L, Prefs.sessionWallDeadline(this) - System.currentTimeMillis())) + "\n"
                        : "") +
                "場所: " + (PlaceStore.isAllPlaces(this) ? "ALL" : (Prefs.p(this).getBoolean("last_context_place_match", false) ? "対象内" : "対象外"))
        );
    }

    private void showWhyDialog() {
        String state = Prefs.state(this);
        StringBuilder s = new StringBuilder();
        s.append("Rule: ").append(RuleConfig.ruleName(this)).append("\n");
        s.append("State: ").append(state).append("\n");
        s.append("場所: ").append(PlaceStore.isAllPlaces(this) ? "ALL" : "選択場所").append("\n");
        s.append("今日の利用: ").append(NotificationController.format(Prefs.dailyUsageMs(this))).append("\n");
        s.append("今日の利用回数: ").append(Prefs.dailySessions(this)).append("\n");
        s.append("Escalation: Level ").append(Prefs.escalationLevel(this)).append("\n");
        if (Prefs.isOverDailyLimit(this)) s.append("\n今日の上限を超えています。通常より強い解除条件が適用されます。\n");
        if (Prefs.STATE_CHALLENGING.equals(state)) {
            s.append("\n解除条件を進行中です。\n");
        } else if (Prefs.STATE_READY.equals(state)) {
            s.append("\n解除条件を達成済みです。通知またはBrowser Brakeから利用するか決めてください。\n");
        } else if (Prefs.STATE_SESSION.equals(state)) {
            s.append("\n実使用残り: ").append(NotificationController.format(Prefs.liveSessionUsageRemainingMs(this))).append("\n");
            s.append("利用権残り: ").append(NotificationController.format(Math.max(0L, Prefs.sessionWallDeadline(this) - System.currentTimeMillis()))).append("\n");
        } else if (Prefs.STATE_RECOVERY.equals(state)) {
            s.append("\n利用後の休憩残り: ").append(NotificationController.format(Math.max(0L, Prefs.recoveryDeadline(this) - System.currentTimeMillis()))).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("なぜブロックされた？").setMessage(s.toString()).setPositiveButton("OK", null).show();
    }

    private void showDiagnosticsDialog() {
        long now = System.currentTimeMillis();
        long lastEvent = Prefs.p(this).getLong("debug_last_event_time", 0L);
        int eventType = Prefs.p(this).getInt("debug_last_event_type", -1);
        String pkg = Prefs.p(this).getString("debug_last_event_package", "");
        boolean eventTarget = Prefs.p(this).getBoolean("debug_last_event_target", false);
        boolean foregroundTarget = Prefs.p(this).getBoolean("debug_foreground_target", false);

        StringBuilder s = new StringBuilder();
        s.append("State: ").append(Prefs.state(this)).append("\n");
        s.append("pendingTarget: ").append(Prefs.pendingTarget(this)).append("\n");
        s.append("sessionForegroundSince: ").append(Prefs.sessionForegroundSince(this)).append("\n");
        s.append("sessionLastUseEnd: ").append(Prefs.sessionLastUseEnd(this)).append("\n");
        s.append("liveUsageRemaining: ").append(NotificationController.format(Prefs.liveSessionUsageRemainingMs(this))).append("\n");
        s.append("wallRemaining: ").append(NotificationController.format(Math.max(0L, Prefs.sessionWallDeadline(this) - now))).append("\n");
        s.append("recoveryRemaining: ").append(NotificationController.format(Math.max(0L, Prefs.recoveryDeadline(this) - now))).append("\n");
        s.append("lastEventType: ").append(eventType).append("\n");
        s.append("lastEventPackage: ").append(pkg).append("\n");
        s.append("lastEventTarget: ").append(eventTarget).append("\n");
        s.append("foregroundTarget(debug): ").append(foregroundTarget).append("\n");
        s.append("lastEventAge: ").append(lastEvent > 0L ? (now - lastEvent) + "ms" : "なし").append("\n");

        new AlertDialog.Builder(this)
                .setTitle("Browser Brake 実機診断")
                .setMessage(s.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String escalationPreview() {
        StringBuilder s = new StringBuilder("次回の解除条件倍率: ");
        int base = Prefs.escalationLevel(this);
        for (int i = 0; i < 4; i++) {
            if (i > 0) s.append(" → ");
            s.append(String.format(Locale.JAPAN, "%.1fx", RuleConfig.escalationMultiplier(this, Math.min(4, base + i))));
        }
        s.append("\nSESSION開始で+1。対象アプリへの試行が一定時間なければ徐々に-1。");
        return s.toString();
    }

    private void showAppPicker() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = getPackageManager().queryIntentActivities(launcher, 0);
        Collections.sort(infos, Comparator.comparing(i -> i.loadLabel(getPackageManager()).toString().toLowerCase(Locale.JAPAN)));
        List<String> labels = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            labels.add(info.loadLabel(getPackageManager()) + "\n" + pkg);
            packages.add(pkg);
        }
        Set<String> coveredBrowsers = RuleConfig.includeBrowsers(this)
                ? TargetApps.browserPackages(this)
                : Collections.emptySet();

        if (!coveredBrowsers.isEmpty()) {
            for (int i = packages.size() - 1; i >= 0; i--) {
                if (coveredBrowsers.contains(packages.get(i))) {
                    packages.remove(i);
                    labels.remove(i);
                }
            }
        }

        Set<String> selected = RuleConfig.customPackages(this);
        boolean[] checked = new boolean[packages.size()];
        for (int i = 0; i < packages.size(); i++) checked[i] = selected.contains(packages.get(i));
        new AlertDialog.Builder(this)
                .setTitle(RuleConfig.includeBrowsers(this)
                        ? "追加の対象アプリ（ブラウザは除外）"
                        : "対象アプリを選ぶ")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("保存", (d, w) -> {
                    Set<String> next = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) next.add(packages.get(i));
                    RuleConfig.setCustomPackages(this, next);
                    buildUi();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void promptAddPlace() {
        if (!ensureLocationPermission()) return;
        final EditText input = new EditText(this);
        input.setHint("場所名（例: 自宅、大学、実家）");
        new AlertDialog.Builder(this)
                .setTitle("現在地を登録")
                .setView(input)
                .setPositiveButton("登録", (d, w) -> obtainLocation(input.getText().toString().trim()))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void obtainLocation(String name) {
        if (name.isEmpty()) name = "場所 " + (PlaceStore.all(this).size() + 1);
        final String finalName = name;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = bestLastKnown(lm);
        if (best != null && System.currentTimeMillis() - best.getTime() < 10L * 60_000L) {
            savePlace(finalName, best);
            return;
        }
        try {
            String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                if (loc == null) Toast.makeText(this, "現在地を取得できませんでした", Toast.LENGTH_LONG).show();
                else savePlace(finalName, loc);
            });
        } catch (SecurityException e) {
            Toast.makeText(this, "位置情報権限が必要です", Toast.LENGTH_LONG).show();
        }
    }

    private void savePlace(String name, Location loc) {
        Place p = PlaceStore.add(this, name, loc.getLatitude(), loc.getLongitude(), 200f);
        Set<String> selected = PlaceStore.selectedIds(this);
        selected.add(p.id);
        PlaceStore.setSelectedIds(this, selected);
        Toast.makeText(this, name + "を登録しました", Toast.LENGTH_SHORT).show();
        buildUi();
    }

    private void showDeletePlaceDialog() {
        List<Place> places = PlaceStore.all(this);
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).name;
        new AlertDialog.Builder(this).setTitle("削除する場所")
                .setItems(labels, (d, which) -> {
                    PlaceStore.delete(this, places.get(which).id);
                    buildUi();
                }).show();
    }

    private boolean ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        Toast.makeText(this, "位置情報を許可後、もう一度操作してください", Toast.LENGTH_LONG).show();
        return false;
    }

    private Location bestLastKnown(LocationManager lm) {
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {}
        return best;
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else Toast.makeText(this, "通知は利用可能です", Toast.LENGTH_SHORT).show();
    }

    private boolean accessibilityEnabled() {
        try {
            android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            ComponentName mine = new ComponentName(this, BrowserBlockService.class);
            for (AccessibilityServiceInfo info : enabled) {
                if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
                ComponentName cn = new ComponentName(info.getResolveInfo().serviceInfo.packageName, info.getResolveInfo().serviceInfo.name);
                if (TextUtils.equals(cn.flattenToString(), mine.flattenToString())
                        || TextUtils.equals(cn.flattenToShortString(), mine.flattenToShortString())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void addTimeSeek(LinearLayout root, String title, long current, LongSetter setter, long[] values) {
        addSeek(root, title, indexOf(values, current), values.length - 1, i -> formatPreset(values[i]), i -> setter.set(this, values[i]));
    }

    private void addTimeSeekAllowNone(LinearLayout root, String title, long current, LongSetter setter, long[] values) {
        addSeek(root, title, indexOf(values, current), values.length - 1,
                i -> values[i] <= 0L ? "制限なし" : formatPreset(values[i]), i -> setter.set(this, values[i]));
    }

    private void addIntSeek(LinearLayout root, String title, int current, IntSetter setter, int[] values, String suffix) {
        addSeek(root, title, indexOf(values, current), values.length - 1, i -> values[i] + suffix, i -> setter.set(this, values[i]));
    }

    private void addIntSeekAllowUnlimited(LinearLayout root, String title, int current, IntSetter setter, int[] values) {
        addSeek(root, title, indexOf(values, current), values.length - 1, i -> values[i] < 0 ? "制限なし" : values[i] + "回", i -> setter.set(this, values[i]));
    }

    private void addSeek(LinearLayout root, String title, int start, int max, IndexFormatter formatter, IndexSetter setter) {
        TextView label = text(title + ": " + formatter.format(start), 15f);
        root.addView(label);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max);
        seek.setProgress(start);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                label.setText(title + ": " + formatter.format(progress));
                if (fromUser) setter.set(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { refreshStatus(); }
        });
        root.addView(seek);
    }

    private int indexOf(long[] values, long current) {
        int best = 0; long diff = Long.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            long d = Math.abs(values[i] - current);
            if (d < diff) { diff = d; best = i; }
        }
        return best;
    }

    private int indexOf(int[] values, int current) {
        int best = 0, diff = Integer.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            int d = Math.abs(values[i] - current);
            if (d < diff) { diff = d; best = i; }
        }
        return best;
    }

    private String formatPreset(long ms) {
        if (ms < 60_000L) return (ms / 1000L) + "秒";
        if (ms % (60L * 60_000L) == 0L) return (ms / (60L * 60_000L)) + "時間";
        return (ms / 60_000L) + "分";
    }

    private void section(LinearLayout root, String title) {
        TextView t = text(title, 21f);
        t.setPadding(0, dp(20), 0, dp(6));
        root.addView(t);
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        return t;
    }

    private CheckBox check(String value, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(value);
        c.setChecked(checked);
        return c;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface LongSetter { void set(Context c, long v); }
    private interface IntSetter { void set(Context c, int v); }
    private interface IndexSetter { void set(int i); }
    private interface IndexFormatter { String format(int i); }
}
