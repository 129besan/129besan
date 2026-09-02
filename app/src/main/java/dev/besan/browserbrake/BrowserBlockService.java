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
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.List;

public class BrowserBlockService extends AccessibilityService implements LocationListener, SensorEventListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor stepCounter;
    private float currentStepTotal = -1f;
    private boolean receiverRegistered = false;
    private boolean stepRegistered = false;
    private boolean currentForegroundTarget = false;
    private long lastInteractionResetAt = 0L;
    private static final long INTERACTION_THROTTLE_MS = 200L;

    private final Runnable stateTimer = this::syncTimedState;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // TargetApps queries installed browsers on demand. No cached list to refresh.
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerPackageReceiver();
        startPassiveLocationUpdates();
        refreshContextFromLastKnown();
        setupStepSensor();
        NotificationController.ensureChannel(this);
        syncTimedState();
        Toast.makeText(this, "Browser Brake v0.3 が有効になりました", Toast.LENGTH_SHORT).show();
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
        if (event == null) return;

        if (isMeaningfulUserInteraction(event)) onUserInteraction();

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if (event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        boolean target = TargetApps.isTarget(this, pkg);
        updateSessionForeground(target);

        if (!target) return;
        if (!Prefs.isLockEnabled(this)) return;

        refreshContextFromLastKnown();
        if (!isContextActive()) {
            if (!Prefs.STATE_LOCKED.equals(Prefs.state(this))) {
                Prefs.clearTransientState(this);
                NotificationController.cancel(this);
            }
            return;
        }

        String state = Prefs.state(this);
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
                performGlobalAction(GLOBAL_ACTION_HOME);
                launchGate();
                return;
            }
        }

        if (Prefs.STATE_CHALLENGING.equals(state)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            evaluateChallenge();
            return;
        }

        if (Prefs.STATE_LOCKED.equals(state)) {
            Prefs.startChallenge(this, pkg, currentStepTotal);
            startStepCounter();
            performGlobalAction(GLOBAL_ACTION_HOME);
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
            Toast.makeText(this, "解除条件を達成しました。必要なら対象アプリをもう一度開いてください", Toast.LENGTH_LONG).show();
            scheduleReadyTimer();
        } else {
            NotificationController.showChallenge(this, walked);
            scheduleChallengeTimer();
        }
    }

    private void launchGate() {
        Intent i = new Intent(this, UnlockGateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(i); }
        catch (Exception e) {
            Toast.makeText(this, "Browser Brakeを開いて利用を開始してください", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isContextActive() {
        if (!Prefs.isLockEnabled(this)) return false;
        if (PlaceStore.isAllPlaces(this)) return true;
        return Prefs.p(this).getBoolean("last_context_place_match", false);
    }

    private void updateSessionForeground(boolean target) {
        if (!Prefs.STATE_SESSION.equals(Prefs.state(this))) {
            currentForegroundTarget = false;
            return;
        }
        if (target && !currentForegroundTarget) {
            currentForegroundTarget = true;
            Prefs.sessionForegroundEnter(this);
            scheduleSessionTimer();
        } else if (!target && currentForegroundTarget) {
            currentForegroundTarget = false;
            Prefs.sessionForegroundLeave(this);
            syncTimedState();
        }
    }

    private void syncTimedState() {
        handler.removeCallbacks(stateTimer);
        String state = Prefs.state(this);
        long now = System.currentTimeMillis();

        if (!Prefs.isLockEnabled(this)) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
        }

        if (!isContextActive()) {
            Prefs.clearTransientState(this);
            NotificationController.cancel(this);
            return;
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
                if (kick) performGlobalAction(GLOBAL_ACTION_HOME);
                syncTimedState();
            } else {
                NotificationController.showSession(this);
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
        handler.removeCallbacks(stateTimer);
        stopStepCounter();
        if (receiverRegistered) {
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        super.onDestroy();
    }
}
