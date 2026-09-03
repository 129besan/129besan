package dev.besan.browserbrake;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import android.widget.Toast;

import java.util.List;

import dev.besan.browserbrake.rules.BrowserRule;
import dev.besan.browserbrake.rules.RuleRepository;

public class BrowserBlockService extends AccessibilityService implements LocationListener, SensorEventListener {
    private static WeakReference<BrowserBlockService> activeService = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor stepCounter;
    private float currentStepTotal = -1f;
    private boolean receiverRegistered = false;
    private boolean screenReceiverRegistered = false;
    private boolean stepRegistered = false;
    private boolean currentForegroundTarget = false;
    private WindowManager windowManager;
    private View sessionOverlay;
    private TextView sessionOverlayText;
    private long lastInteractionResetAt = 0L;
    private long lastDiagnosticAt = 0L;
    private static final long INTERACTION_THROTTLE_MS = 200L;
    private static final long DIAGNOSTIC_THROTTLE_MS = 500L;

    private final Runnable stateTimer = this::syncTimedState;
    private final Runnable overlayUpdater = this::updateSessionOverlayText;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            TargetApps.invalidateBrowserCache();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
            if (Prefs.STATE_SESSION.equals(Prefs.state(BrowserBlockService.this))
                    && currentForegroundTarget) {
                currentForegroundTarget = false;
                Prefs.sessionForegroundLeave(BrowserBlockService.this);
                hideSessionOverlay();
                syncTimedState();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = new WeakReference<>(this);
        if (Prefs.STATE_SESSION.equals(Prefs.state(this)) && Prefs.sessionForegroundSince(this) > 0L) {
            // We cannot know which app stayed foreground while the AccessibilityService was disconnected.
            // Prefer pausing accounting rather than burning the user's allowance speculatively.
            Prefs.suspendForegroundAccounting(this);
        }
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        registerPackageReceiver();
        registerScreenReceiver();
        startPassiveLocationUpdates();
        refreshContextFromLastKnown();
        setupStepSensor();
        NotificationController.ensureChannel(this);
        syncTimedState();
        Toast.makeText(this, "AppLockout が有効になりました", Toast.LENGTH_SHORT).show();
    }

    private void registerPackageReceiver() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packageReceiver, f);
        receiverRegistered = true;
    }

    private void registerScreenReceiver() {
        if (screenReceiverRegistered) return;
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, f);
        screenReceiverRegistered = true;
    }

    private void startPassiveLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30_000L, 30f, this);
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    private void setupStepSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (Prefs.STATE_CHALLENGING.equals(Prefs.state(this)) && RuleConfig.challengeWalk(this)) startStepCounter();
    }

    private void startStepCounter() {
        if (stepRegistered || stepCounter == null || sensorManager == null) return;
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        stepRegistered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopStepCounter() {
        if (stepRegistered && sensorManager != null) sensorManager.unregisterListener(this);
        stepRegistered = false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        int type = event.getEventType();
        String pkg = event.getPackageName().toString();
        String state = Prefs.state(this);
        String activeRuleId = RuleRepository.activeRuntimeRuleId(this);
        BrowserRule matchingRule = RuleRepository.findMatchingRule(this, pkg);
        boolean belongsToActiveRule = !activeRuleId.isEmpty()
                && RuleRepository.packageBelongsToRule(this, activeRuleId, pkg);

        updateSessionForeground(type, pkg, belongsToActiveRule);
        recordRuntimeDiagnostic(type, pkg, belongsToActiveRule);

        if (!getPackageName().equals(pkg) && isMeaningfulUserInteraction(event)) onUserInteraction();
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        if (!Prefs.isLockEnabled(this)) return;

        if (Prefs.STATE_LOCKED.equals(state)) {
            if (matchingRule == null) return;
            if (!RuleRepository.activateForRuntime(this, matchingRule.getId())) return;

            refreshContextFromLastKnown();
            if (!isContextActive()) {
                RuleRepository.clearActiveRuntimeRule(this);
                return;
            }

            if (RuleConfig.fullLock(this)) {
                String restrictionName = RuleConfig.ruleName(this);
                NotificationController.showFullLock(this, restrictionName);
                launchBrakeGate(true, restrictionName);
                RuleRepository.clearActiveRuntimeRule(this);
                return;
            }

            Prefs.recordTargetAttempt(this);
            Prefs.setPendingTarget(this, pkg);
            Prefs.startChallenge(this, pkg, currentStepTotal);
            startStepCounter();
            launchBrakeGate(false, RuleConfig.ruleName(this));
            evaluateChallenge();
            return;
        }

        if (activeRuleId.isEmpty()) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        if (matchingRule != null && !activeRuleId.equals(matchingRule.getId())) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Toast.makeText(this,
                    "別の制限が進行中です。いったん現在の利用を終えてください",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!belongsToActiveRule) return;

        refreshContextFromLastKnown();
        if (!isContextActive()) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        state = Prefs.state(this);

        if (Prefs.STATE_SESSION.equals(state)) {
            scheduleSessionTimer();
            NotificationController.showSession(this);
            return;
        }

        Prefs.recordTargetAttempt(this);
        Prefs.setPendingTarget(this, pkg);

        if (Prefs.STATE_RECOVERY.equals(state)) {
            if (System.currentTimeMillis() >= Prefs.recoveryDeadline(this)) {
                Prefs.finishRecovery(this);
                RuleRepository.clearActiveRuntimeRule(this);
                state = Prefs.STATE_LOCKED;
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME);
                NotificationController.showRecovery(this);
                Toast.makeText(this, "利用後の休憩中です", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (Prefs.STATE_READY.equals(state)) {
            long readyDeadline = Prefs.readyDeadline(this);
            if (readyDeadline > 0L && System.currentTimeMillis() >= readyDeadline) {
                Prefs.declineReady(this);
                state = Prefs.STATE_LOCKED;
            } else {
                NotificationController.showReady(this);
                launchBrakeGate(false, RuleConfig.ruleName(this));
                return;
            }
        }

        if (Prefs.STATE_CHALLENGING.equals(state)) {
            launchBrakeGate(false, RuleConfig.ruleName(this));
            evaluateChallenge();
        }
    }

    private boolean isMeaningfulUserInteraction(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                return true;
            default:
                return false;
        }
    }

    private void onUserInteraction() {
        if (!Prefs.STATE_CHALLENGING.equals(Prefs.state(this)) || !RuleConfig.challengePhoneBreak(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastInteractionResetAt < INTERACTION_THROTTLE_MS) return;
        lastInteractionResetAt = now;
        Prefs.resetPhoneBreakDeadline(this);
        evaluateChallenge();
    }

    private void evaluateChallenge() {
        if (!Prefs.STATE_CHALLENGING.equals(Prefs.state(this))) return;
        long now = System.currentTimeMillis();

        boolean waitEnabled = RuleConfig.challengeWait(this);
        boolean phoneEnabled = RuleConfig.challengePhoneBreak(this);
        boolean walkEnabled = RuleConfig.challengeWalk(this);
        int enabled = (waitEnabled ? 1 : 0) + (phoneEnabled ? 1 : 0) + (walkEnabled ? 1 : 0);

        boolean waitDone = !waitEnabled || now >= Prefs.challengeWaitDeadline(this);
        boolean phoneDone = !phoneEnabled || now >= Prefs.challengePhoneDeadline(this);
        int walked = currentStepTotal >= 0f ? Prefs.walkedSteps(this, currentStepTotal) : 0;
        boolean walkDone = !walkEnabled || walked >= Prefs.challengeRequiredSteps(this);

        boolean done;
        if (enabled == 0) done = true;
        else if (RuleConfig.challengeAll(this)) done = waitDone && phoneDone && walkDone;
        else done = (waitEnabled && waitDone) || (phoneEnabled && phoneDone) || (walkEnabled && walkDone);

        if (done) {
            Prefs.markReady(this);
            Prefs.clearChallenge(this);
            stopStepCounter();
            handler.removeCallbacks(stateTimer);
            NotificationController.showReady(this);
            Toast.makeText(this, "解除条件を達成しました", Toast.LENGTH_SHORT).show();
            scheduleReadyTimer();
        } else {
            NotificationController.showChallenge(this, walked);
            scheduleChallengeTimer();
        }
    }

    private boolean isContextActive() {
        if (!Prefs.isLockEnabled(this)) return false;
        if (PlaceStore.isAllPlaces(this)) return true;
        return Prefs.p(this).getBoolean("last_context_place_match", false);
    }

    private void updateSessionForeground(int eventType, String pkg, boolean target) {
        if (!Prefs.STATE_SESSION.equals(Prefs.state(this))) {
            currentForegroundTarget = false;
            hideSessionOverlay();
            return;
        }

        // Any event originating from a target app is positive evidence that the target is active.
        if (target) {
            if (!currentForegroundTarget) {
                currentForegroundTarget = true;
                Prefs.sessionForegroundEnter(this);
                NotificationController.showSession(this);
                showSessionOverlay();
                scheduleSessionTimer();
            }
            return;
        }

        // Only a real window-state transition may end foreground accounting.
        // TYPE_WINDOWS_CHANGED and view events from overlays must not pause the timer.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && currentForegroundTarget
                && !isTransientOverlayPackage(pkg)) {
            currentForegroundTarget = false;
            Prefs.sessionForegroundLeave(this);
            hideSessionOverlay();
            syncTimedState();
        }
    }

    private boolean isTransientOverlayPackage(String pkg) {
        if (pkg == null) return true;
        if ("com.android.systemui".equals(pkg)) return true;
        if (getPackageName().equals(pkg)) return true;

        try {
            String flattened = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (flattened != null) {
                android.content.ComponentName ime =
                        android.content.ComponentName.unflattenFromString(flattened);
                if (ime != null && pkg.equals(ime.getPackageName())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void recordRuntimeDiagnostic(int eventType, String pkg, boolean target) {
        long now = System.currentTimeMillis();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && now - lastDiagnosticAt < DIAGNOSTIC_THROTTLE_MS) {
            return;
        }
        lastDiagnosticAt = now;
        Prefs.p(this).edit()
                .putInt("debug_last_event_type", eventType)
                .putString("debug_last_event_package", pkg)
                .putBoolean("debug_last_event_target", target)
                .putBoolean("debug_foreground_target", currentForegroundTarget)
                .putLong("debug_last_event_time", now)
                .apply();
    }

    public static void requestRuntimeSync() {
        BrowserBlockService service = activeService.get();
        if (service != null) {
            service.handler.post(service::syncTimedState);
        }
    }

    private void syncTimedState() {
        handler.removeCallbacks(stateTimer);
        String state = Prefs.state(this);
        long now = System.currentTimeMillis();

        if (!Prefs.STATE_SESSION.equals(state)) {
            hideSessionOverlay();
        }

        if (!Prefs.isLockEnabled(this)) {
            currentForegroundTarget = false;
            hideSessionOverlay();
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        if (!isContextActive()) {
            currentForegroundTarget = false;
            hideSessionOverlay();
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        if (!Prefs.STATE_SESSION.equals(state) && currentForegroundTarget) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            currentForegroundTarget = false;
            hideSessionOverlay();
        }

        if (Prefs.STATE_CHALLENGING.equals(state)) {
            startStepCounter();
            evaluateChallenge();
        } else if (Prefs.STATE_READY.equals(state)) {
            long deadline = Prefs.readyDeadline(this);
            if (deadline > 0L && now >= deadline) {
                Prefs.declineReady(this);
                NotificationController.cancel(this);
            } else {
                NotificationController.showReady(this);
                scheduleReadyTimer();
            }
        } else if (Prefs.STATE_SESSION.equals(state)) {
            long wall = Prefs.sessionWallDeadline(this);
            long usage = Prefs.liveSessionUsageRemainingMs(this);
            if (usage <= 0L || wall <= 0L || now >= wall) {
                boolean kick = currentForegroundTarget;
                Prefs.finishSession(this);
                if (kick) {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    currentForegroundTarget = false;
                }
                syncTimedState();
            } else {
                NotificationController.showSession(this);
                if (currentForegroundTarget) showSessionOverlay();
                else hideSessionOverlay();
                scheduleSessionTimer();
            }
        } else if (Prefs.STATE_RECOVERY.equals(state)) {
            long deadline = Prefs.recoveryDeadline(this);
            if (deadline <= 0L || now >= deadline) {
                Prefs.finishRecovery(this);
                NotificationController.cancel(this);
            } else {
                NotificationController.showRecovery(this);
                scheduleAt(deadline);
            }
        } else {
            stopStepCounter();
            hideSessionOverlay();
            NotificationController.cancel(this);
        }
    }

    private void scheduleChallengeTimer() {
        handler.removeCallbacks(stateTimer);
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        if (RuleConfig.challengeWait(this) && Prefs.challengeWaitDeadline(this) > now)
            best = Math.min(best, Prefs.challengeWaitDeadline(this));
        if (RuleConfig.challengePhoneBreak(this) && Prefs.challengePhoneDeadline(this) > now)
            best = Math.min(best, Prefs.challengePhoneDeadline(this));
        if (best != Long.MAX_VALUE) scheduleAt(best);
    }

    private void scheduleReadyTimer() {
        long deadline = Prefs.readyDeadline(this);
        if (deadline > 0L) scheduleAt(deadline);
    }

    private void scheduleSessionTimer() {
        handler.removeCallbacks(stateTimer);
        long now = System.currentTimeMillis();
        long wall = Prefs.sessionWallDeadline(this);
        long deadline = wall;
        if (currentForegroundTarget) deadline = Math.min(deadline, now + Prefs.liveSessionUsageRemainingMs(this));
        if (deadline > 0L) scheduleAt(deadline);
    }

    private void scheduleAt(long wallClockTime) {
        handler.removeCallbacks(stateTimer);
        long delay = Math.max(50L, wallClockTime - System.currentTimeMillis());
        handler.postDelayed(stateTimer, delay);
    }

    private void launchBrakeGate(boolean fullLock, String restrictionName) {
        Intent intent = new Intent(this, BrakeGateActivity.class)
                .putExtra(BrakeGateActivity.EXTRA_FULL_LOCK, fullLock)
                .putExtra(BrakeGateActivity.EXTRA_RESTRICTION_NAME, restrictionName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // Notification remains as a fallback if this device blocks the activity launch.
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showSessionOverlay() {
        if (!Prefs.STATE_SESSION.equals(Prefs.state(this)) || !currentForegroundTarget) {
            hideSessionOverlay();
            return;
        }
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
        if (windowManager == null) return;

        if (sessionOverlay != null) {
            updateSessionOverlayText();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(14), dp(8), dp(8), dp(8));

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF02257D6, 0xF03D7CF2}
        );
        background.setCornerRadius(dp(28));
        root.setBackground(background);
        root.setElevation(dp(8));

        TextView timer = new TextView(this);
        timer.setTextColor(Color.WHITE);
        timer.setTextSize(14f);
        timer.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        timer.setPadding(0, 0, dp(12), 0);
        root.addView(timer);

        TextView leave = new TextView(this);
        leave.setText("ロック");
        leave.setTextColor(Color.WHITE);
        leave.setTextSize(13f);
        leave.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        leave.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable leaveBg = new GradientDrawable();
        leaveBg.setColor(0x33FFFFFF);
        leaveBg.setCornerRadius(dp(18));
        leave.setBackground(leaveBg);
        leave.setOnClickListener(v -> {
            if (Prefs.STATE_SESSION.equals(Prefs.state(this))) {
                if (currentForegroundTarget) {
                    currentForegroundTarget = false;
                    Prefs.sessionForegroundLeave(this);
                }
                hideSessionOverlay();
                Prefs.finishSession(this);
                performGlobalAction(GLOBAL_ACTION_HOME);
                syncTimedState();
            }
        });
        root.addView(leave);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(10);
        params.y = dp(72);

        try {
            windowManager.addView(root, params);
            sessionOverlay = root;
            sessionOverlayText = timer;
            updateSessionOverlayText();
        } catch (Exception ignored) {
            sessionOverlay = null;
            sessionOverlayText = null;
        }
    }

    private void updateSessionOverlayText() {
        handler.removeCallbacks(overlayUpdater);
        if (sessionOverlay == null || sessionOverlayText == null
                || !Prefs.STATE_SESSION.equals(Prefs.state(this))
                || !currentForegroundTarget) {
            hideSessionOverlay();
            return;
        }
        sessionOverlayText.setText("残り " + NotificationController.format(
                Prefs.liveSessionUsageRemainingMs(this)));
        handler.postDelayed(overlayUpdater, 1_000L);
    }

    private void hideSessionOverlay() {
        handler.removeCallbacks(overlayUpdater);
        if (sessionOverlay != null && windowManager != null) {
            try { windowManager.removeView(sessionOverlay); } catch (Exception ignored) {}
        }
        sessionOverlay = null;
        sessionOverlayText = null;
    }

    private void refreshContextFromLastKnown() {
        if (PlaceStore.isAllPlaces(this)) {
            Prefs.p(this).edit().putBoolean("last_context_place_match", true).apply();
            return;
        }
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
        boolean match = PlaceStore.matches(this, location);
        if (!match && !PlaceStore.isAllPlaces(this)) {
            currentForegroundTarget = false;
            hideSessionOverlay();
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            handler.removeCallbacks(stateTimer);
            stopStepCounter();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER || event.values.length == 0) return;
        currentStepTotal = event.values[0];
        Prefs.updateWalkBaselineIfNeeded(this, currentStepTotal);
        if (Prefs.STATE_CHALLENGING.equals(Prefs.state(this))) evaluateChallenge();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        BrowserBlockService current = activeService.get();
        if (current == this) activeService.clear();
        handler.removeCallbacks(stateTimer);
        handler.removeCallbacks(overlayUpdater);
        hideSessionOverlay();
        stopStepCounter();
        if (receiverRegistered) {
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        }
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        super.onDestroy();
    }
}
