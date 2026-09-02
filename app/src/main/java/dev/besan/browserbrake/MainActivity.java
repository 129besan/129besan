package dev.besan.browserbrake;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private TextView status;
    private Button lockButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Browser Brake");
        title.setTextSize(30f);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("家にいる間、ブラウザを開くとホームへ戻します。\n画面を5分OFFにすると、15分だけブラウザを解放します。");
        intro.setTextSize(16f);
        intro.setPadding(0, 0, 0, dp(18));
        root.addView(intro);

        status = new TextView(this);
        status.setTextSize(16f);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button accessibility = button("1. Accessibility を有効にする");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        Button setHome = button("2. 現在地を自宅として登録");
        setHome.setOnClickListener(v -> ensureLocationThen(true));
        root.addView(setHome);

        Button updateLocation = button("現在地で家判定を更新");
        updateLocation.setOnClickListener(v -> ensureLocationThen(false));
        root.addView(updateLocation);

        Button locationSettings = button("位置情報を「常に許可」に設定する");
        locationSettings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Toast.makeText(this, "権限 → 位置情報 → 常に許可 を選んでください", Toast.LENGTH_LONG).show();
        });
        root.addView(locationSettings);

        lockButton = button("制限をONにする");
        lockButton.setOnClickListener(v -> {
            if (!Prefs.isHomeSet(this)) {
                Toast.makeText(this, "先に現在地を自宅として登録してください", Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.setLockEnabled(this, !Prefs.isLockEnabled(this));
            if (!Prefs.isLockEnabled(this)) Prefs.clearUnlock(this);
            refreshUi();
        });
        root.addView(lockButton);

        Button testChrome = button("Chromeを開いて動作テスト");
        testChrome.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.android.chrome");
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "Chromeが見つかりません", Toast.LENGTH_SHORT).show();
        });
        root.addView(testChrome);

        TextView note = new TextView(this);
        note.setText("\n初期版の注意\n・Device Owner / root は使いません。\n・Accessibility を手動でOFFにする、またはこのアプリを削除すると回避できます。\n・位置情報が取得不能になった場合は、最後に確認できた家/外の状態を維持します。\n・ブラウザ判定はAndroidのブラウザカテゴリ＋主要ブラウザのpackage名で行います。");
        note.setTextSize(14f);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void ensureLocationThen(boolean saveAsHome) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            Toast.makeText(this, "位置情報を許可後、もう一度ボタンを押してください", Toast.LENGTH_LONG).show();
            return;
        }
        obtainLocation(saveAsHome);
    }

    private void obtainLocation(boolean saveAsHome) {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = bestLastKnown(lm);
        if (best != null && System.currentTimeMillis() - best.getTime() < 10L * 60L * 1000L) {
            applyLocation(best, saveAsHome);
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                    if (loc == null) {
                        Toast.makeText(this, "現在地を取得できませんでした。位置情報をONにしてください", Toast.LENGTH_LONG).show();
                    } else {
                        applyLocation(loc, saveAsHome);
                    }
                });
            } else {
                Toast.makeText(this, "位置情報を再取得してからもう一度試してください", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "位置情報権限が必要です", Toast.LENGTH_LONG).show();
        }
    }

    private Location bestLastKnown(LocationManager lm) {
        Location best = null;
        try {
            List<String> providers = lm.getProviders(true);
            for (String provider : providers) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {}
        return best;
    }

    private void applyLocation(Location loc, boolean saveAsHome) {
        if (saveAsHome) {
            Prefs.setHome(this, loc.getLatitude(), loc.getLongitude());
            Toast.makeText(this, "この場所を自宅として登録しました", Toast.LENGTH_LONG).show();
        } else {
            boolean home = Prefs.updateHomeState(this, loc);
            Toast.makeText(this, home ? "現在地: 自宅判定" : "現在地: 外出判定", Toast.LENGTH_LONG).show();
        }
        refreshUi();
    }

    private boolean accessibilityEnabled() {
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> enabled =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            ComponentName mine = new ComponentName(this, BrowserBlockService.class);
            String mineFlat = mine.flattenToString();
            String mineShort = mine.flattenToShortString();
            for (AccessibilityServiceInfo info : enabled) {
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                    ComponentName cn = new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                            info.getResolveInfo().serviceInfo.name);
                    if (TextUtils.equals(cn.flattenToString(), mineFlat) || TextUtils.equals(cn.flattenToShortString(), mineShort)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void refreshUi() {
        if (status == null) return;
        boolean a11y = accessibilityEnabled();
        boolean homeSet = Prefs.isHomeSet(this);
        boolean lock = Prefs.isLockEnabled(this);
        boolean home = Prefs.lastHomeState(this);
        long now = System.currentTimeMillis();
        long unlockRemaining = Math.max(0L, Prefs.unlockUntil(this) - now);
        long offAt = Prefs.screenOffAt(this);
        String challenge = "待機なし";
        if (Prefs.isChallengeActive(this)) {
            if (offAt > 0) {
                long remaining = Math.max(0L, Prefs.WAIT_MS - (now - offAt));
                challenge = "画面OFF待機中: あと約 " + formatDuration(remaining);
            } else {
                challenge = "画面をOFFにすると5分待機開始";
            }
        }

        String distance = Prefs.lastDistance(this) >= 0
                ? String.format(Locale.JAPAN, "%.0f m", Prefs.lastDistance(this)) : "未取得";

        status.setText(
                "Accessibility: " + (a11y ? "ON" : "OFF") + "\n" +
                "自宅登録: " + (homeSet ? "済み" : "未設定") + "\n" +
                "現在の家判定: " + (home ? "HOME" : "AWAY") + " (距離 " + distance + ")\n" +
                "制限: " + (lock ? "ON" : "OFF") + "\n" +
                "チャレンジ: " + challenge + "\n" +
                "一時解除: " + (unlockRemaining > 0 ? "残り " + formatDuration(unlockRemaining) : "なし")
        );
        lockButton.setText(lock ? "制限をOFFにする" : "制限をONにする");
    }

    private String formatDuration(long ms) {
        long sec = (ms + 999L) / 1000L;
        return String.format(Locale.JAPAN, "%d:%02d", sec / 60L, sec % 60L);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
