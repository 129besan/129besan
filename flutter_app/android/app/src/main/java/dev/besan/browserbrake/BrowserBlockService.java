package dev.besan.browserbrake;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.besan.browserbrake.rules.BrowserRule;
import dev.besan.browserbrake.rules.RuleRepository;
import dev.besan.browserbrake.rules.TargetGroupCatalog;
import dev.besan.browserbrake.runtime.RuleRuntimeStore;

public class BrowserBlockService extends AccessibilityService implements LocationListener, SensorEventListener {
    private static WeakReference<BrowserBlockService> activeService = new WeakReference<>(null);
    private static final String PAUSED_TRACK_RULE = "runtime_paused_tracker_rule";
    private static final String PAUSED_TRACK_SINCE = "runtime_paused_tracker_since";
    private static final String PAUSED_TRACK_UNTIL = "runtime_paused_tracker_until";
    private static final String PAUSED_TRACK_CHECKPOINT = "runtime_paused_tracker_checkpoint";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private Location latestLocation;
    private SensorManager sensorManager;
    private Sensor stepCounter;
    private float currentStepTotal = -1f;
    private boolean receiverRegistered = false;
    private boolean screenReceiverRegistered = false;
    private boolean stepRegistered = false;

    // Multiple Session entitlements may exist, but only one target app can consume
    // foreground time at a time.
    private String currentForegroundRuleId = "";

    // A manually paused restriction does not block, but target-app foreground time still counts
    // toward that rule's daily usage. This tracker is separate from Session runtime state.
    private String currentPausedForegroundRuleId = "";
    private long currentPausedForegroundSince = 0L;
    private long currentPausedForegroundUntil = 0L;
    private String lastForegroundPackage = "";
    private Set<String> homePackages = new HashSet<>();
    private String visibleBrakeGateRuleId = "";

    private WindowManager windowManager;
    private View sessionOverlay;
    private TextView sessionOverlayText;

    private long lastInteractionResetAt = 0L;
    private long lastDiagnosticAt = 0L;
    private static final long INTERACTION_THROTTLE_MS = 200L;
    private static final long DIAGNOSTIC_THROTTLE_MS = 500L;

    private final Runnable stateTimer = this::syncAllRuntimes;
    private final Runnable overlayUpdater = this::updateSessionOverlayText;
    private final Runnable runtimeHeartbeat = this::runRuntimeHeartbeat;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            TargetApps.invalidateBrowserCache();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
            leaveCurrentForegroundSession();
            leavePausedForegroundUsage(System.currentTimeMillis());
            lastForegroundPackage = "";
            markPhoneBreaksSafe();
            hideSessionOverlay();
            syncAllRuntimes();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = new WeakReference<>(this);
        RuleRuntimeStore.ensureMigrated(this);
        // Settle only the foreground time that was checkpointed while the previous
        // service instance was alive, then reconcile expired wall-clock states.
        RuleRuntimeStore.reconcilePersistentState(this);
        restorePausedForegroundCheckpoint();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        homePackages = resolveHomePackages();
        registerPackageReceiver();
        registerScreenReceiver();
        startPassiveLocationUpdates();
        refreshLastKnownLocation();
        setupStepSensor();
        NotificationController.ensureChannel(this);
        syncAllRuntimes();
        // Accessibility may reconnect while the restricted app is already visible.
        // Query the current root instead of waiting for the next window event.
        handler.postDelayed(this::reconcileForegroundFromRoot, 180L);
        handler.postDelayed(this::reconcileForegroundFromRoot, 900L);
        scheduleRuntimeHeartbeat();
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

    private void refreshLastKnownLocation() {
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
        if (best != null) latestLocation = best;
    }

    private void setupStepSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        updateStepSensorRegistration();
    }

    private boolean anyWalkChallengeActive() {
        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) continue;
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule != null && rule.getChallengeWalk()) return true;
        }
        return false;
    }

    private void updateStepSensorRegistration() {
        boolean needed = anyWalkChallengeActive();
        if (needed) startStepCounter();
        else stopStepCounter();
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

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isTransientOverlayPackage(pkg)) {
            lastForegroundPackage = pkg;
            updatePausedForeground(pkg);
            updatePhoneBreakForeground(pkg);
        }

        // Active episodes are matched against their start-time snapshot first.
        // This preserves the rule-edit contract: target/context edits apply to the
        // next Brake, not to an already-running Challenge/READY/Session/Recovery.
        BrowserRule runtimeMatchingRule = findActiveRuntimeRuleForPackage(pkg);
        BrowserRule durableMatchingRule = RuleRepository.findMatchingRule(this, pkg);

        updateSessionForeground(type, pkg, runtimeMatchingRule);
        recordRuntimeDiagnostic(type, pkg,
                runtimeMatchingRule != null ? runtimeMatchingRule : durableMatchingRule);

        if (!getPackageName().equals(pkg) && isMeaningfulUserInteraction(event)) {
            onUserInteraction(pkg);
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if (!Prefs.isLockEnabled(this)) return;

        BrowserRule matchingRule = runtimeMatchingRule;
        if (matchingRule == null) {
            if (durableMatchingRule == null) return;

            // The durable restriction may have been edited while its current episode
            // is still active. If the current snapshot does not include this package,
            // do not let the edit leak into the running episode.
            String durableRuleId = durableMatchingRule.getId();
            if (!RuleRuntimeStore.STATE_LOCKED.equals(
                    RuleRuntimeStore.state(this, durableRuleId))) {
                return;
            }
            matchingRule = durableMatchingRule;
        }

        refreshLastKnownLocation();
        if (!isContextActive(matchingRule)) {
            if (!RuleRuntimeStore.STATE_LOCKED.equals(RuleRuntimeStore.state(this, matchingRule.getId()))) {
                clearRuntime(matchingRule.getId());
            }
            return;
        }

        String ruleId = matchingRule.getId();
        syncRuleRuntime(ruleId);
        String state = RuleRuntimeStore.state(this, ruleId);

        if (RuleRuntimeStore.STATE_LOCKED.equals(state)) {
            if (matchingRule.getFullLock()) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                NotificationController.showFullLock(this, ruleId, matchingRule.getName());
                return;
            }

            RuleRuntimeStore.startChallenge(this, matchingRule, pkg, currentStepTotal);
            updateStepSensorRegistration();
            evaluateChallenge(ruleId);
            if (RuleRuntimeStore.STATE_READY.equals(RuleRuntimeStore.state(this, ruleId))) {
                launchUnlockGate(ruleId);
            } else {
                launchBrakeGate(false, matchingRule);
            }
            scheduleNextRuntimeTimer();
            return;
        }

        RuleRuntimeStore.recordTargetAttempt(this, ruleId);

        if (RuleRuntimeStore.STATE_SESSION.equals(state)) {
            NotificationController.showSession(this, ruleId);
            scheduleNextRuntimeTimer();
            return;
        }

        if (RuleRuntimeStore.STATE_RECOVERY.equals(state)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            NotificationController.showRecovery(this, ruleId);
            Toast.makeText(this, matchingRule.getName() + " は利用後の休憩中です", Toast.LENGTH_SHORT).show();
            scheduleNextRuntimeTimer();
            return;
        }

        if (RuleRuntimeStore.STATE_READY.equals(state)) {
            NotificationController.showReady(this, ruleId);
            launchUnlockGate(ruleId);
            scheduleNextRuntimeTimer();
            return;
        }

        if (RuleRuntimeStore.STATE_CHALLENGING.equals(state)) {
            evaluateChallenge(ruleId);
            if (RuleRuntimeStore.STATE_READY.equals(RuleRuntimeStore.state(this, ruleId))) {
                launchUnlockGate(ruleId);
            } else {
                NotificationController.showChallenge(this, ruleId,
                        currentStepTotal >= 0f
                                ? RuleRuntimeStore.walkedSteps(this, ruleId, currentStepTotal)
                                : 0);
                // Re-enter the intervention surface instead of silently kicking the user home.
                // This is especially important for Phone Break, where opening the target app
                // should make it obvious that the quiet period has been interrupted.
                launchBrakeGate(false, matchingRule);
            }
            scheduleNextRuntimeTimer();
        }
    }

    private BrowserRule findActiveRuntimeRuleForPackage(String pkg) {
        if (pkg == null || pkg.isBlank()) return null;
        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            BrowserRule runtimeRule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (runtimeRule != null
                    && TargetGroupCatalog.packageBelongs(this, runtimeRule, pkg)) {
                return runtimeRule;
            }
        }
        return null;
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

    private void onUserInteraction(String pkg) {
        long now = System.currentTimeMillis();
        if (now - lastInteractionResetAt < INTERACTION_THROTTLE_MS) return;
        lastInteractionResetAt = now;

        boolean changed = false;
        boolean safePackage = isPhoneBreakSafePackage(pkg);
        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) continue;
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule != null && rule.getChallengePhoneBreak()) {
                boolean safeForRule = safePackage || ruleId.equals(visibleBrakeGateRuleId);
                if (safeForRule) {
                    // Any deliberate interaction while in a safe surface restarts the quiet period.
                    RuleRuntimeStore.resetPhoneBreakDeadline(this, ruleId);
                } else {
                    RuleRuntimeStore.markPhoneBreakUnsafe(this, ruleId);
                }
                changed = true;
            }
        }

        if (changed) {
            syncAllRuntimes();
        }
    }

    private void evaluateChallenge(String ruleId) {
        if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) return;
        BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
        if (rule == null) {
            clearRuntime(ruleId);
            return;
        }

        long now = System.currentTimeMillis();
        boolean waitEnabled = rule.getChallengeWait();
        boolean phoneEnabled = rule.getChallengePhoneBreak();
        boolean walkEnabled = rule.getChallengeWalk();
        int enabled = (waitEnabled ? 1 : 0) + (phoneEnabled ? 1 : 0) + (walkEnabled ? 1 : 0);

        boolean waitDone = !waitEnabled || now >= RuleRuntimeStore.challengeWaitDeadline(this, ruleId);
        boolean phoneDone = !phoneEnabled ||
                (RuleRuntimeStore.challengePhoneSafeSince(this, ruleId) > 0L
                        && now >= RuleRuntimeStore.challengePhoneDeadline(this, ruleId));
        int walked = currentStepTotal >= 0f
                ? RuleRuntimeStore.walkedSteps(this, ruleId, currentStepTotal)
                : 0;
        boolean walkDone = !walkEnabled || walked >= RuleRuntimeStore.challengeRequiredSteps(this, ruleId);

        boolean done;
        if (enabled == 0) done = true;
        else if (rule.getChallengeAll()) done = waitDone && phoneDone && walkDone;
        else done = (waitEnabled && waitDone) || (phoneEnabled && phoneDone) || (walkEnabled && walkDone);

        if (done) {
            RuleRuntimeStore.markReady(this, ruleId);
            NotificationController.showReady(this, ruleId);
            Toast.makeText(this, rule.getName() + " の解除条件を達成しました", Toast.LENGTH_SHORT).show();
        } else {
            NotificationController.showChallenge(this, ruleId, walked);
        }
    }

    private boolean isRuleContextEligible(BrowserRule rule) {
        if (!Prefs.isLockEnabled(this) || rule == null || !rule.getEnabled()) return false;
        if (rule.getAllPlaces()) return true;
        return PlaceStore.matchesRule(this, rule, latestLocation);
    }

    private boolean isContextActive(BrowserRule rule) {
        return rule != null && RuleRepository.isEffective(rule) && isRuleContextEligible(rule);
    }

    private Set<String> resolveHomePackages() {
        Set<String> result = new HashSet<>();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<android.content.pm.ResolveInfo> homes = getPackageManager().queryIntentActivities(intent, 0);
            for (android.content.pm.ResolveInfo info : homes) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    result.add(info.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private boolean isPhoneBreakSafePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return getPackageName().equals(pkg) || homePackages.contains(pkg);
    }

    private void markPhoneBreaksSafe() {
        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) continue;
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule != null && rule.getChallengePhoneBreak()) {
                RuleRuntimeStore.markPhoneBreakSafe(this, ruleId);
            }
        }
    }

    private void updatePhoneBreakForeground(String pkg) {
        boolean safePackage = isPhoneBreakSafePackage(pkg);
        boolean changed = false;
        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) continue;
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule == null || !rule.getChallengePhoneBreak()) continue;
            boolean safeForRule = safePackage || ruleId.equals(visibleBrakeGateRuleId);
            if (safeForRule) RuleRuntimeStore.markPhoneBreakSafe(this, ruleId);
            else RuleRuntimeStore.markPhoneBreakUnsafe(this, ruleId);
            changed = true;
        }
        if (changed) scheduleNextRuntimeTimer();
    }

    private void reconcileForegroundFromRoot() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) return;
            String pkg = root.getPackageName().toString();
            if (pkg.isEmpty() || isTransientOverlayPackage(pkg)) return;

            lastForegroundPackage = pkg;
            updatePausedForeground(pkg);
            updatePhoneBreakForeground(pkg);
            BrowserRule runtimeRule = findActiveRuntimeRuleForPackage(pkg);
            updateSessionForeground(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, pkg, runtimeRule);
            syncAllRuntimes();
        } catch (Exception ignored) {}
    }

    private void runRuntimeHeartbeat() {
        handler.removeCallbacks(runtimeHeartbeat);
        long now = System.currentTimeMillis();
        if (currentForegroundRuleId != null && !currentForegroundRuleId.isEmpty()) {
            RuleRuntimeStore.checkpointForeground(this, currentForegroundRuleId, now);
        }
        checkpointPausedForeground(now);
        scheduleRuntimeHeartbeat();
    }

    private void scheduleRuntimeHeartbeat() {
        handler.removeCallbacks(runtimeHeartbeat);
        boolean sessionLive = currentForegroundRuleId != null && !currentForegroundRuleId.isEmpty();
        boolean pausedLive = currentPausedForegroundRuleId != null && !currentPausedForegroundRuleId.isEmpty();
        if (sessionLive || pausedLive) {
            handler.postDelayed(runtimeHeartbeat, 1_000L);
        }
    }

    private void persistPausedForegroundTracker(long checkpoint) {
        if (currentPausedForegroundRuleId == null || currentPausedForegroundRuleId.isEmpty()) return;
        Prefs.p(this).edit()
                .putString(PAUSED_TRACK_RULE, currentPausedForegroundRuleId)
                .putLong(PAUSED_TRACK_SINCE, currentPausedForegroundSince)
                .putLong(PAUSED_TRACK_UNTIL, currentPausedForegroundUntil)
                .putLong(PAUSED_TRACK_CHECKPOINT, checkpoint)
                .apply();
    }

    private void checkpointPausedForeground() {
        checkpointPausedForeground(System.currentTimeMillis());
    }

    private void checkpointPausedForeground(long now) {
        if (currentPausedForegroundRuleId == null || currentPausedForegroundRuleId.isEmpty()) return;
        persistPausedForegroundTracker(now);
    }

    private void clearPausedForegroundTracker() {
        Prefs.p(this).edit()
                .remove(PAUSED_TRACK_RULE)
                .remove(PAUSED_TRACK_SINCE)
                .remove(PAUSED_TRACK_UNTIL)
                .remove(PAUSED_TRACK_CHECKPOINT)
                .apply();
    }

    private void restorePausedForegroundCheckpoint() {
        String ruleId = Prefs.p(this).getString(PAUSED_TRACK_RULE, "");
        long since = Prefs.p(this).getLong(PAUSED_TRACK_SINCE, 0L);
        long until = Prefs.p(this).getLong(PAUSED_TRACK_UNTIL, 0L);
        long checkpoint = Prefs.p(this).getLong(PAUSED_TRACK_CHECKPOINT, 0L);
        if (ruleId != null && !ruleId.isEmpty() && since > 0L && checkpoint > since) {
            long chargedUntil = until > 0L ? Math.min(checkpoint, until) : checkpoint;
            if (chargedUntil > since) {
                RuleRepository.addPausedUsageInterval(this, ruleId, since, chargedUntil);
            }
        }
        clearPausedForegroundTracker();
    }

    private void updatePausedForeground(String pkg) {
        BrowserRule pausedRule = RuleRepository.findPausedRule(this, pkg);
        if (pausedRule != null && !isRuleContextEligible(pausedRule)) pausedRule = null;

        String nextRuleId = pausedRule == null ? "" : pausedRule.getId();
        if (currentPausedForegroundRuleId.equals(nextRuleId)) return;

        leavePausedForegroundUsage(System.currentTimeMillis());
        if (pausedRule != null) {
            currentPausedForegroundRuleId = pausedRule.getId();
            currentPausedForegroundSince = System.currentTimeMillis();
            currentPausedForegroundUntil = pausedRule.getPausedUntilMs();
            persistPausedForegroundTracker(currentPausedForegroundSince);
        }
        scheduleNextRuntimeTimer();
        scheduleRuntimeHeartbeat();
    }

    private void leavePausedForegroundUsage(long now) {
        if (currentPausedForegroundRuleId == null || currentPausedForegroundRuleId.isEmpty()) return;
        long until = currentPausedForegroundUntil > 0L
                ? Math.min(now, currentPausedForegroundUntil)
                : now;
        if (currentPausedForegroundSince > 0L && until > currentPausedForegroundSince) {
            RuleRepository.addPausedUsageInterval(
                    this, currentPausedForegroundRuleId, currentPausedForegroundSince, until);
        }
        currentPausedForegroundRuleId = "";
        currentPausedForegroundSince = 0L;
        currentPausedForegroundUntil = 0L;
        clearPausedForegroundTracker();
        scheduleRuntimeHeartbeat();
    }

    private void syncPausedForegroundAccounting() {
        if (currentPausedForegroundRuleId == null || currentPausedForegroundRuleId.isEmpty()) return;
        BrowserRule rule = RuleRepository.getRule(this, currentPausedForegroundRuleId);
        long now = System.currentTimeMillis();
        boolean stillPaused = rule != null
                && rule.getEnabled()
                && rule.getPausedUntilMs() > now
                && rule.getPausedUntilMs() == currentPausedForegroundUntil
                && TargetGroupCatalog.packageBelongs(this, rule, lastForegroundPackage)
                && isRuleContextEligible(rule);
        if (!stillPaused) leavePausedForegroundUsage(now);
    }

    private void enforceCurrentForegroundIfNeeded() {
        if (lastForegroundPackage == null || lastForegroundPackage.isEmpty()) return;
        if (getPackageName().equals(lastForegroundPackage) || isTransientOverlayPackage(lastForegroundPackage)) return;

        BrowserRule rule = RuleRepository.findMatchingRule(this, lastForegroundPackage);
        if (rule == null || !isContextActive(rule)) return;
        String ruleId = rule.getId();
        if (!RuleRuntimeStore.STATE_LOCKED.equals(RuleRuntimeStore.state(this, ruleId))) return;

        if (rule.getFullLock()) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            NotificationController.showFullLock(this, ruleId, rule.getName());
            return;
        }

        RuleRuntimeStore.startChallenge(this, rule, lastForegroundPackage, currentStepTotal);
        updateStepSensorRegistration();
        evaluateChallenge(ruleId);
        if (RuleRuntimeStore.STATE_READY.equals(RuleRuntimeStore.state(this, ruleId))) {
            launchUnlockGate(ruleId);
        } else {
            launchBrakeGate(false, rule);
        }
    }

    private void updateSessionForeground(int eventType, String pkg, BrowserRule matchingRule) {
        String targetSessionRuleId = "";
        if (matchingRule != null
                && RuleRuntimeStore.STATE_SESSION.equals(
                        RuleRuntimeStore.state(this, matchingRule.getId()))) {
            targetSessionRuleId = matchingRule.getId();
        }

        if (!targetSessionRuleId.isEmpty()) {
            if (!currentForegroundRuleId.equals(targetSessionRuleId)) {
                leaveCurrentForegroundSession();
                currentForegroundRuleId = targetSessionRuleId;
                RuleRuntimeStore.sessionForegroundEnter(this, targetSessionRuleId);
                scheduleRuntimeHeartbeat();
            }
            NotificationController.showSession(this, targetSessionRuleId);
            showSessionOverlay(targetSessionRuleId);
            scheduleNextRuntimeTimer();
            return;
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !currentForegroundRuleId.isEmpty()
                && !isTransientOverlayPackage(pkg)) {
            leaveCurrentForegroundSession();
            hideSessionOverlay();
            syncAllRuntimes();
        }
    }

    private void leaveCurrentForegroundSession() {
        if (currentForegroundRuleId == null || currentForegroundRuleId.isEmpty()) return;
        String ruleId = currentForegroundRuleId;
        currentForegroundRuleId = "";
        RuleRuntimeStore.sessionForegroundLeave(this, ruleId);
        NotificationController.showSession(this, ruleId);
        scheduleRuntimeHeartbeat();
    }

    private boolean isTransientOverlayPackage(String pkg) {
        if (pkg == null) return true;
        if ("com.android.systemui".equals(pkg)) return true;

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

    private void recordRuntimeDiagnostic(int eventType, String pkg, BrowserRule matchingRule) {
        long now = System.currentTimeMillis();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && now - lastDiagnosticAt < DIAGNOSTIC_THROTTLE_MS) {
            return;
        }
        lastDiagnosticAt = now;

        Prefs.p(this).edit()
                .putInt("debug_last_event_type", eventType)
                .putString("debug_last_event_package", pkg)
                .putString("debug_last_event_rule_id", matchingRule == null ? "" : matchingRule.getId())
                .putString("debug_foreground_rule_id", currentForegroundRuleId)
                .putString("debug_paused_foreground_rule_id", currentPausedForegroundRuleId)
                .putLong("debug_last_event_time", now)
                .apply();
    }

    public static void requestRuntimeSync() {
        BrowserBlockService service = activeService.get();
        if (service != null) {
            service.handler.post(service::syncAllRuntimes);
        }
    }

    public static void setBrakeGateVisible(String ruleId, boolean visible) {
        BrowserBlockService service = activeService.get();
        if (service == null) return;
        service.handler.post(() -> {
            if (visible) {
                service.visibleBrakeGateRuleId = ruleId == null ? "" : ruleId;
                if (!service.visibleBrakeGateRuleId.isEmpty()
                        && RuleRuntimeStore.STATE_CHALLENGING.equals(
                                RuleRuntimeStore.state(service, service.visibleBrakeGateRuleId))) {
                    RuleRuntimeStore.markPhoneBreakSafe(service, service.visibleBrakeGateRuleId);
                }
            } else if (ruleId == null || ruleId.equals(service.visibleBrakeGateRuleId)) {
                service.visibleBrakeGateRuleId = "";
            }
            service.scheduleNextRuntimeTimer();
        });
    }

    private void syncAllRuntimes() {
        handler.removeCallbacks(stateTimer);
        if (currentForegroundRuleId != null && !currentForegroundRuleId.isEmpty()) {
            RuleRuntimeStore.checkpointForeground(this, currentForegroundRuleId);
        }
        checkpointPausedForeground();

        if (!Prefs.isLockEnabled(this)) {
            leaveCurrentForegroundSession();
            hideSessionOverlay();
            for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
                clearRuntime(ruleId);
            }
            updateStepSensorRegistration();
            return;
        }

        refreshLastKnownLocation();
        syncPausedForegroundAccounting();
        enforceCurrentForegroundIfNeeded();
        Set<String> active = RuleRuntimeStore.activeRuleIds(this);
        for (String ruleId : active) {
            syncRuleRuntime(ruleId);
        }

        if (!currentForegroundRuleId.isEmpty()
                && !RuleRuntimeStore.STATE_SESSION.equals(
                        RuleRuntimeStore.state(this, currentForegroundRuleId))) {
            currentForegroundRuleId = "";
            hideSessionOverlay();
        } else if (!currentForegroundRuleId.isEmpty()) {
            showSessionOverlay(currentForegroundRuleId);
        } else {
            hideSessionOverlay();
        }

        updateStepSensorRegistration();
        scheduleNextRuntimeTimer();
    }

    private void syncRuleRuntime(String ruleId) {
        BrowserRule runtimeRule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
        BrowserRule durableRule = RuleRepository.getRule(this, ruleId);

        if (runtimeRule == null || durableRule == null || !RuleRepository.isEffective(durableRule)) {
            clearRuntime(ruleId);
            return;
        }

        if (!isContextActive(runtimeRule)) {
            if (currentForegroundRuleId.equals(ruleId)) {
                leaveCurrentForegroundSession();
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
            clearRuntime(ruleId);
            return;
        }

        String state = RuleRuntimeStore.state(this, ruleId);
        long now = System.currentTimeMillis();

        if (RuleRuntimeStore.STATE_CHALLENGING.equals(state)) {
            evaluateChallenge(ruleId);
            return;
        }

        if (RuleRuntimeStore.STATE_READY.equals(state)) {
            long deadline = RuleRuntimeStore.readyDeadline(this, ruleId);
            if (deadline > 0L && now >= deadline) {
                RuleRuntimeStore.declineReady(this, ruleId);
                NotificationController.cancel(this, ruleId);
            } else {
                NotificationController.showReady(this, ruleId);
            }
            return;
        }

        if (RuleRuntimeStore.STATE_SESSION.equals(state)) {
            long wall = RuleRuntimeStore.sessionWallDeadline(this, ruleId);
            long usage = RuleRuntimeStore.liveSessionUsageRemainingMs(this, ruleId);
            if (usage <= 0L || wall <= 0L || now >= wall) {
                boolean kick = currentForegroundRuleId.equals(ruleId);
                if (kick) {
                    leaveCurrentForegroundSession();
                    hideSessionOverlay();
                }
                RuleRuntimeStore.finishSession(this, ruleId);
                if (kick) performGlobalAction(GLOBAL_ACTION_HOME);

                if (RuleRuntimeStore.STATE_RECOVERY.equals(RuleRuntimeStore.state(this, ruleId))) {
                    NotificationController.showRecovery(this, ruleId);
                } else {
                    NotificationController.cancel(this, ruleId);
                }
            } else {
                NotificationController.showSession(this, ruleId);
            }
            return;
        }

        if (RuleRuntimeStore.STATE_RECOVERY.equals(state)) {
            long deadline = RuleRuntimeStore.recoveryDeadline(this, ruleId);
            if (deadline <= 0L || now >= deadline) {
                RuleRuntimeStore.finishRecovery(this, ruleId);
                NotificationController.cancel(this, ruleId);
            } else {
                NotificationController.showRecovery(this, ruleId);
            }
            return;
        }

        clearRuntime(ruleId);
    }

    private void clearRuntime(String ruleId) {
        if (currentForegroundRuleId.equals(ruleId)) {
            leaveCurrentForegroundSession();
            hideSessionOverlay();
        }
        if (ruleId.equals(visibleBrakeGateRuleId)) {
            visibleBrakeGateRuleId = "";
        }
        RuleRuntimeStore.clearRuntime(this, ruleId);
        NotificationController.cancel(this, ruleId);
    }

    private void scheduleNextRuntimeTimer() {
        handler.removeCallbacks(stateTimer);
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;

        for (BrowserRule rule : RuleRepository.getRules(this)) {
            if (rule.getEnabled() && rule.getPausedUntilMs() > now) {
                next = Math.min(next, rule.getPausedUntilMs());
            }
        }

        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule == null) continue;
            String state = RuleRuntimeStore.state(this, ruleId);

            if (RuleRuntimeStore.STATE_CHALLENGING.equals(state)) {
                long wait = RuleRuntimeStore.challengeWaitDeadline(this, ruleId);
                long phone = RuleRuntimeStore.challengePhoneDeadline(this, ruleId);
                if (rule.getChallengeWait() && wait > 0L) next = Math.min(next, wait);
                if (rule.getChallengePhoneBreak() && phone > 0L) next = Math.min(next, phone);
            } else if (RuleRuntimeStore.STATE_READY.equals(state)) {
                long ready = RuleRuntimeStore.readyDeadline(this, ruleId);
                if (ready > 0L) next = Math.min(next, ready);
            } else if (RuleRuntimeStore.STATE_SESSION.equals(state)) {
                long wall = RuleRuntimeStore.sessionWallDeadline(this, ruleId);
                if (wall > 0L) next = Math.min(next, wall);
                if (currentForegroundRuleId.equals(ruleId)) {
                    long usage = RuleRuntimeStore.liveSessionUsageRemainingMs(this, ruleId);
                    next = Math.min(next, now + Math.max(50L, usage));
                }
            } else if (RuleRuntimeStore.STATE_RECOVERY.equals(state)) {
                long recovery = RuleRuntimeStore.recoveryDeadline(this, ruleId);
                if (recovery > 0L) next = Math.min(next, recovery);
            }
        }

        if (next != Long.MAX_VALUE) {
            handler.postDelayed(stateTimer, Math.max(50L, next - now));
        }
    }

    private void launchBrakeGate(boolean fullLock, BrowserRule rule) {
        Intent intent = new Intent(this, BrakeGateActivity.class)
                .putExtra(BrakeGateActivity.EXTRA_FULL_LOCK, fullLock)
                .putExtra(BrakeGateActivity.EXTRA_RULE_ID, rule.getId())
                .putExtra(BrakeGateActivity.EXTRA_RESTRICTION_NAME, rule.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // Rule-specific notification remains as a fallback.
        }
    }

    private void launchUnlockGate(String ruleId) {
        Intent intent = new Intent(this, UnlockGateActivity.class)
                .putExtra(UnlockGateActivity.EXTRA_RULE_ID, ruleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // READY notification remains as a fallback.
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showSessionOverlay(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()
                || !currentForegroundRuleId.equals(ruleId)
                || !RuleRuntimeStore.STATE_SESSION.equals(RuleRuntimeStore.state(this, ruleId))) {
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
                new int[]{0xF0123F9E, 0xF045469A}
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

        TextView lock = new TextView(this);
        lock.setText("ロック");
        lock.setTextColor(Color.WHITE);
        lock.setTextSize(13f);
        lock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lock.setPadding(dp(10), dp(6), dp(10), dp(6));

        GradientDrawable lockBg = new GradientDrawable();
        lockBg.setColor(0x33FFFFFF);
        lockBg.setCornerRadius(dp(18));
        lock.setBackground(lockBg);
        lock.setOnClickListener(v -> {
            String active = currentForegroundRuleId;
            if (active == null || active.isEmpty()) return;
            leaveCurrentForegroundSession();
            hideSessionOverlay();
            RuleRuntimeStore.finishSession(this, active);
            performGlobalAction(GLOBAL_ACTION_HOME);

            if (RuleRuntimeStore.STATE_RECOVERY.equals(RuleRuntimeStore.state(this, active))) {
                NotificationController.showRecovery(this, active);
            } else {
                NotificationController.cancel(this, active);
            }
            syncAllRuntimes();
        });
        root.addView(lock);

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
        String ruleId = currentForegroundRuleId;
        if (sessionOverlay == null || sessionOverlayText == null
                || ruleId == null || ruleId.isEmpty()
                || !RuleRuntimeStore.STATE_SESSION.equals(RuleRuntimeStore.state(this, ruleId))) {
            hideSessionOverlay();
            return;
        }

        RuleRuntimeStore.checkpointForeground(this, ruleId);
        sessionOverlayText.setText(
                "残り " + NotificationController.format(
                        RuleRuntimeStore.liveSessionUsageRemainingMs(this, ruleId)
                )
        );
        handler.postDelayed(overlayUpdater, 1_000L);
    }

    private void hideSessionOverlay() {
        handler.removeCallbacks(overlayUpdater);
        if (sessionOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(sessionOverlay);
            } catch (Exception ignored) {}
        }
        sessionOverlay = null;
        sessionOverlayText = null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        latestLocation = location;

        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule != null && !isContextActive(rule)) {
                if (currentForegroundRuleId.equals(ruleId)) {
                    leaveCurrentForegroundSession();
                    hideSessionOverlay();
                    performGlobalAction(GLOBAL_ACTION_HOME);
                }
                clearRuntime(ruleId);
            }
        }
        syncAllRuntimes();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER || event.values.length == 0) return;
        currentStepTotal = event.values[0];

        for (String ruleId : RuleRuntimeStore.activeRuleIds(this)) {
            if (!RuleRuntimeStore.STATE_CHALLENGING.equals(RuleRuntimeStore.state(this, ruleId))) continue;
            BrowserRule rule = RuleRuntimeStore.ruleForRuntime(this, ruleId);
            if (rule != null && rule.getChallengeWalk()) {
                RuleRuntimeStore.updateWalkBaselineIfNeeded(this, ruleId, currentStepTotal);
                evaluateChallenge(ruleId);
            }
        }

        updateStepSensorRegistration();
        scheduleNextRuntimeTimer();
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
        handler.removeCallbacks(runtimeHeartbeat);
        leaveCurrentForegroundSession();
        leavePausedForegroundUsage(System.currentTimeMillis());
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
