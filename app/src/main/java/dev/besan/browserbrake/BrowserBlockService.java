package dev.besan.browserbrake;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrowserBlockService extends AccessibilityService implements LocationListener {
    private final Set<String> browserPackages = new HashSet<>();
    private LocationManager locationManager;
    private boolean receiversRegistered = false;
    private long lastLocationRefreshAt = 0L;

    private static final Set<String> KNOWN_BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.microsoft.emmx",
            "com.microsoft.emmx.beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "com.yandex.browser"
    ));

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (Prefs.isLockEnabled(context) && Prefs.isChallengeActive(context) && Prefs.lastHomeState(context)) {
                    Prefs.markScreenOff(context);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (Prefs.isChallengeActive(context)) {
                    long offAt = Prefs.screenOffAt(context);
                    if (offAt > 0L) {
                        long elapsed = System.currentTimeMillis() - offAt;
                        if (elapsed >= Prefs.WAIT_MS) {
                            Prefs.grantTemporaryUnlock(context);
                            Toast.makeText(context, "15分間ブラウザを解放しました", Toast.LENGTH_LONG).show();
                        } else {
                            Prefs.resetScreenOff(context);
                            long remain = Math.max(0L, Prefs.WAIT_MS - elapsed);
                            Toast.makeText(context, "5分未満だったので待機をリセットしました（不足 " + ((remain + 59999L) / 60000L) + "分）", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshBrowserPackages();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        refreshBrowserPackages();
        registerReceivers();
        startPassiveLocationUpdates();
        refreshHomeFromLastKnown();
        Toast.makeText(this, "Browser Brake が有効になりました", Toast.LENGTH_SHORT).show();
    }

    private void registerReceivers() {
        if (receiversRegistered) return;
        IntentFilter screen = new IntentFilter();
        screen.addAction(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screen, Context.RECEIVER_EXPORTED);
            registerReceiver(packageReceiver, pkg, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, screen);
            registerReceiver(packageReceiver, pkg);
        }
        receiversRegistered = true;
    }

    private void startPassiveLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30_000L, 50f, this);
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    private void refreshBrowserPackages() {
        browserPackages.clear();
        browserPackages.addAll(KNOWN_BROWSERS);
        try {
            Intent selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            List<ResolveInfo> infos = getPackageManager().queryIntentActivities(selector, PackageManager.MATCH_ALL);
            for (ResolveInfo info : infos) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    browserPackages.add(info.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        String pkg = event.getPackageName().toString();
        if (getPackageName().equals(pkg)) return;
        if (!browserPackages.contains(pkg)) return;
        if (!Prefs.isLockEnabled(this) || !Prefs.isHomeSet(this)) return;

        long now = System.currentTimeMillis();
        if (now - lastLocationRefreshAt > 30_000L) {
            refreshHomeFromLastKnown();
            lastLocationRefreshAt = now;
        }

        if (!Prefs.lastHomeState(this)) return;
        if (Prefs.isTemporarilyUnlocked(this)) return;

        if (!Prefs.isChallengeActive(this)) Prefs.startChallenge(this);
        performGlobalAction(GLOBAL_ACTION_HOME);
        Toast.makeText(this,
                "ブラウザをロックしました。画面を5分OFFにすると15分だけ解放します。",
                Toast.LENGTH_LONG).show();
    }

    private void refreshHomeFromLastKnown() {
        if (!Prefs.isHomeSet(this)) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        if (locationManager == null) locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        Location best = null;
        try {
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {}
        if (best != null) Prefs.updateHomeState(this, best);
    }

    @Override public void onLocationChanged(Location location) {
        if (location != null) Prefs.updateHomeState(this, location);
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (receiversRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        super.onDestroy();
    }
}
