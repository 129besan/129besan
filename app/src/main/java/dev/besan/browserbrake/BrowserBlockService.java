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
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrowserBlockService extends AccessibilityService implements LocationListener {
    private final Set<String> browserPackages = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private boolean receiverRegistered = false;
    private long lastLocationRefreshAt = 0L;
    private long lastInteractionResetAt = 0L;

    private static final long INTERACTION_RESET_THROTTLE_MS = 200L;

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

    private final Runnable stateTimer = new Runnable() {
        @Override public void run() {
            syncTimedState();
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
        registerPackageReceiver();
        startPassiveLocationUpdates();
        refreshHomeFromLastKnown();
        NotificationController.ensureChannel(this);
        restoreTimedState();
        Toast.makeText(this, "Browser Brake が有効になりました", Toast.LENGTH_SHORT).show();
    }

    private void registerPackageReceiver() {
        if (receiverRegistered) return;
        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, pkg, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, pkg);
        }
        receiverRegistered = true;
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
        if (event == null) return;

        if (isMeaningfulUserInteraction(event)) {
            onUserInteraction();
        }

        if (event.getPackageName() == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

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

        if (Prefs.isTemporarilyUnlocked(this)) {
            ensureUnlockTimer();
            return;
        }

        if (Prefs.unlockUntil(this) > 0L) {
            Prefs.clearUnlock(this);
            NotificationController.cancel(this);
        }

        if (!Prefs.isChallengeActive(this)) {
            Prefs.startChallenge(this);
            NotificationController.showChallenge(this);
            scheduleAt(Prefs.challengeDeadline(this));
        }

        performGlobalAction(GLOBAL_ACTION_HOME);
        Toast.makeText(this,
                "ブラウザをロックしました。5分間スマホを操作しなければ15分だけ解放します。残り時間は通知で確認できます。",
                Toast.LENGTH_LONG).show();
    }

    private boolean isMeaningfulUserInteraction(AccessibilityEvent event) {
        int type = event.getEventType();

        switch (type) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                return true;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                // A heads-up notification or other System UI change can occur without user input.
                // System UI clicks/scrolls are caught by the explicit interaction types above.
                return event.getPackageName() == null
                        || !"com.android.systemui".contentEquals(event.getPackageName());

            default:
                return false;
        }
    }

    private void onUserInteraction() {
        if (!Prefs.isChallengeActive(this)) return;

        if (!Prefs.isLockEnabled(this) || !Prefs.lastHomeState(this)) {
            Prefs.cancelChallenge(this);
            NotificationController.cancel(this);
            handler.removeCallbacks(stateTimer);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastInteractionResetAt < INTERACTION_RESET_THROTTLE_MS) return;
        lastInteractionResetAt = now;

        Prefs.resetChallengeFromTouch(this);
        NotificationController.showChallenge(this);
        scheduleAt(Prefs.challengeDeadline(this));
    }

    private void syncTimedState() {
        handler.removeCallbacks(stateTimer);
        long now = System.currentTimeMillis();

        if (!Prefs.isLockEnabled(this) || !Prefs.lastHomeState(this)) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        if (Prefs.isChallengeActive(this)) {
            long deadline = Prefs.challengeDeadline(this);
            if (deadline <= 0L) {
                Prefs.startChallenge(this);
                deadline = Prefs.challengeDeadline(this);
            }

            if (now >= deadline) {
                Prefs.grantTemporaryUnlock(this);
                NotificationController.showUnlocked(this);
                Toast.makeText(this, "5分達成。15分間ブラウザを解放しました", Toast.LENGTH_LONG).show();
                ensureUnlockTimer();
            } else {
                NotificationController.showChallenge(this);
                scheduleAt(deadline);
            }
            return;
        }

        long unlockUntil = Prefs.unlockUntil(this);
        if (unlockUntil > 0L) {
            if (now >= unlockUntil) {
                Prefs.clearUnlock(this);
                NotificationController.cancel(this);
                Toast.makeText(this, "一時解除が終了し、ブラウザを再ロックしました", Toast.LENGTH_SHORT).show();
            } else {
                NotificationController.showUnlocked(this);
                scheduleAt(unlockUntil);
            }
        } else {
            NotificationController.cancel(this);
        }
    }

    private void restoreTimedState() {
        long now = System.currentTimeMillis();
        if (Prefs.isChallengeActive(this)) {
            long deadline = Prefs.challengeDeadline(this);
            if (deadline > 0L && now >= deadline) {
                Prefs.grantTemporaryUnlock(this);
                NotificationController.showUnlocked(this);
                ensureUnlockTimer();
            } else {
                NotificationController.showChallenge(this);
                scheduleAt(deadline);
            }
            return;
        }

        if (Prefs.unlockUntil(this) > now) {
            NotificationController.showUnlocked(this);
            scheduleAt(Prefs.unlockUntil(this));
        } else if (Prefs.unlockUntil(this) > 0L) {
            Prefs.clearUnlock(this);
            NotificationController.cancel(this);
        }
    }

    private void ensureUnlockTimer() {
        long until = Prefs.unlockUntil(this);
        if (until <= 0L) return;
        scheduleAt(until);
    }

    private void scheduleAt(long wallClockTime) {
        handler.removeCallbacks(stateTimer);
        long delay = Math.max(50L, wallClockTime - System.currentTimeMillis());
        handler.postDelayed(stateTimer, delay);
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
        if (best != null) onLocationChanged(best);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        boolean home = Prefs.updateHomeState(this, location);
        if (!home) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            handler.removeCallbacks(stateTimer);
        }
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacks(stateTimer);
        if (receiverRegistered) {
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        super.onDestroy();
    }
}
