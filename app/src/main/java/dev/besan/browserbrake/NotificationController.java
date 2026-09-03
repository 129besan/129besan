package dev.besan.browserbrake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Locale;

import dev.besan.browserbrake.rules.BrowserRule;
import dev.besan.browserbrake.runtime.RuleRuntimeStore;

public final class NotificationController {
    public static final String CHANNEL_ID = "browser_brake_timer";
    private static final int LEGACY_NOTIFICATION_ID = 2001;
    private static final int RULE_NOTIFICATION_BASE = 4000;

    private NotificationController() {}

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AppLockout",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("解除条件、利用可能時間、利用後の休憩を制限ごとに表示します");
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    public static int notificationId(String ruleId) {
        int hash = ruleId == null ? 0 : ruleId.hashCode();
        return RULE_NOTIFICATION_BASE + Math.floorMod(hash, 50000);
    }

    public static void showChallenge(Context c, String ruleId, int walkedSteps) {
        BrowserRule rule = RuleRuntimeStore.ruleForRuntime(c, ruleId);
        if (rule == null) return;
        ensureChannel(c);

        String text = challengeText(c, ruleId, rule, walkedSteps);
        long nextDeadline = nextChallengeDeadline(c, ruleId, rule);
        Notification.Builder b = baseBuilder(c, ruleId)
                .setContentTitle(RuleRuntimeStore.challengeOverLimit(c, ruleId)
                        ? rule.getName() + ": 今日の通常利用は終了"
                        : rule.getName() + ": 解除条件")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (nextDeadline > 0L) {
            b.setWhen(nextDeadline).setUsesChronometer(true).setChronometerCountDown(true);
        }
        notifySafe(c, ruleId, b.build());
    }

    public static void showReady(Context c, String ruleId) {
        BrowserRule rule = RuleRuntimeStore.ruleForRuntime(c, ruleId);
        if (rule == null) return;
        ensureChannel(c);

        Intent decline = new Intent(c, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_DECLINE_READY)
                .putExtra(NotificationActionReceiver.EXTRA_RULE_ID, ruleId);
        PendingIntent declinePending = PendingIntent.getBroadcast(
                c,
                notificationId(ruleId) + 101,
                decline,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent decide = new Intent(c, UnlockGateActivity.class)
                .putExtra(UnlockGateActivity.EXTRA_RULE_ID, ruleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent decidePending = PendingIntent.getActivity(
                c,
                notificationId(ruleId) + 103,
                decide,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = baseBuilder(c, ruleId)
                .setContentIntent(decidePending)
                .setContentTitle(rule.getName() + ": 解除条件を達成")
                .setContentText("今回は何分使うか選べます")
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "利用時間を選ぶ",
                        decidePending
                ).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "今回はやめる",
                        declinePending
                ).build());

        long deadline = RuleRuntimeStore.readyDeadline(c, ruleId);
        if (deadline > 0L) {
            b.setWhen(deadline).setUsesChronometer(true).setChronometerCountDown(true);
        }
        notifySafe(c, ruleId, b.build());
    }

    public static void showSession(Context c, String ruleId) {
        BrowserRule rule = RuleRuntimeStore.ruleForRuntime(c, ruleId);
        if (rule == null) return;
        ensureChannel(c);

        Intent lockIntent = new Intent(c, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_LOCK_NOW)
                .putExtra(NotificationActionReceiver.EXTRA_RULE_ID, ruleId);
        PendingIntent lockPending = PendingIntent.getBroadcast(
                c,
                notificationId(ruleId) + 102,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long now = System.currentTimeMillis();
        long usage = RuleRuntimeStore.liveSessionUsageRemainingMs(c, ruleId);
        long wallDeadline = RuleRuntimeStore.sessionWallDeadline(c, ruleId);
        boolean foreground = RuleRuntimeStore.sessionForegroundSince(c, ruleId) > 0L;

        Notification.Builder b = baseBuilder(c, ruleId)
                .setContentTitle(RuleRuntimeStore.sessionOverLimit(c, ruleId)
                        ? rule.getName() + ": 上限を超えた追加利用"
                        : rule.getName() + ": 利用中")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_lock_lock,
                        "ロック",
                        lockPending
                ).build());

        if (foreground) {
            b.setContentText("実使用時間を消費中 / 利用権は " + formatClock(wallDeadline) + " まで")
                    .setWhen(now + usage)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true);
        } else {
            b.setContentText("実使用 残り " + format(usage) + "（停止中） / 利用権は " + formatClock(wallDeadline) + " まで")
                    .setUsesChronometer(false);
        }

        notifySafe(c, ruleId, b.build());
    }

    public static void showFullLock(Context c, String ruleId, String restrictionName) {
        ensureChannel(c);
        String name = restrictionName == null || restrictionName.isBlank() ? "AppLockout" : restrictionName;
        Notification.Builder b = baseBuilder(c, ruleId)
                .setContentTitle(name + ": 完全ロック")
                .setContentText("この制限が有効なため、今は対象アプリを開けません")
                .setStyle(new Notification.BigTextStyle()
                        .bigText("この制限が有効なため、今は対象アプリを開けません。AppLockoutで場所や制限内容を確認できます。"))
                .setOngoing(false)
                .setOnlyAlertOnce(false);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(12_000L);
        notifySafe(c, ruleId, b.build());
    }

    public static void showRecovery(Context c, String ruleId) {
        BrowserRule rule = RuleRuntimeStore.ruleForRuntime(c, ruleId);
        if (rule == null) return;
        ensureChannel(c);

        long deadline = RuleRuntimeStore.recoveryDeadline(c, ruleId);
        if (deadline <= 0L) return;

        notifySafe(c, ruleId, baseBuilder(c, ruleId)
                .setContentTitle(rule.getName() + ": 利用後の休憩")
                .setContentText("次に使えるまで")
                .setWhen(deadline)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build());
    }

    public static void cancel(Context c, String ruleId) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(ruleId));
    }

    // Compatibility helper for settings/upgrade paths that need to clear all AppLockout runtime notifications.
    public static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.cancel(LEGACY_NOTIFICATION_ID);
        for (String ruleId : RuleRuntimeStore.activeRuleIds(c)) {
            nm.cancel(notificationId(ruleId));
        }
    }

    private static long nextChallengeDeadline(Context c, String ruleId, BrowserRule rule) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;

        long wait = RuleRuntimeStore.challengeWaitDeadline(c, ruleId);
        long phone = RuleRuntimeStore.challengePhoneDeadline(c, ruleId);
        if (rule.getChallengeWait() && wait > now) best = Math.min(best, wait);
        if (rule.getChallengePhoneBreak() && phone > now) best = Math.min(best, phone);
        return best == Long.MAX_VALUE ? 0L : best;
    }

    private static String challengeText(Context c, String ruleId, BrowserRule rule, int walked) {
        StringBuilder s = new StringBuilder();
        long now = System.currentTimeMillis();
        boolean first = true;

        if (rule.getChallengeWait()) {
            s.append("待つ ").append(format(Math.max(
                    0L,
                    RuleRuntimeStore.challengeWaitDeadline(c, ruleId) - now
            )));
            first = false;
        }

        if (rule.getChallengePhoneBreak()) {
            if (!first) s.append(" / ");
            s.append("スマホ休憩 ").append(format(Math.max(
                    0L,
                    RuleRuntimeStore.challengePhoneDeadline(c, ruleId) - now
            )));
            first = false;
        }

        if (rule.getChallengeWalk()) {
            if (!first) s.append(" / ");
            s.append("歩く ")
                    .append(Math.min(walked, RuleRuntimeStore.challengeRequiredSteps(c, ruleId)))
                    .append("/")
                    .append(RuleRuntimeStore.challengeRequiredSteps(c, ruleId))
                    .append("歩");
        }

        if (s.length() == 0) s.append("解除条件がありません");
        if (rule.getChallengeAll()) s.append("（すべて）");
        else s.append("（どれか1つ）");
        return s.toString();
    }

    private static Notification.Builder baseBuilder(Context c, String ruleId) {
        Intent openIntent = new Intent(c, MainActivity.class)
                .putExtra("open_runtime_rule_id", ruleId);
        PendingIntent openPending = PendingIntent.getActivity(
                c,
                notificationId(ruleId),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL_ID)
                : new Notification.Builder(c);

        return b
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(openPending)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setAutoCancel(false);
    }

    private static void notifySafe(Context c, String ruleId, Notification notification) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.notify(notificationId(ruleId), notification);
        } catch (SecurityException ignored) {}
    }

    public static String format(long ms) {
        long sec = Math.max(0L, (ms + 999L) / 1000L);
        return String.format(Locale.JAPAN, "%d:%02d", sec / 60L, sec % 60L);
    }

    public static String formatClock(long wallClockMs) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(wallClockMs);
        return String.format(Locale.JAPAN, "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE));
    }
}
