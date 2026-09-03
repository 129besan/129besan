package dev.besan.browserbrake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Locale;

public final class NotificationController {
    public static final String CHANNEL_ID = "browser_brake_timer";
    public static final int NOTIFICATION_ID = 2001;

    private NotificationController() {}

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Browser Brake",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("解除条件、利用可能時間、利用後の休憩を表示します");
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    public static void showChallenge(Context c, int walkedSteps) {
        ensureChannel(c);
        String text = challengeText(c, walkedSteps);
        long nextDeadline = nextChallengeDeadline(c);
        Notification.Builder b = baseBuilder(c)
                .setContentTitle(Prefs.challengeOverLimit(c) ? "今日の上限を超えています" : "解除条件を進めています")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (nextDeadline > 0L) {
            b.setWhen(nextDeadline).setUsesChronometer(true).setChronometerCountDown(true);
        }
        notifySafe(c, b.build());
    }

    public static void showReady(Context c) {
        ensureChannel(c);
        Intent decline = new Intent(c, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_DECLINE_READY);
        PendingIntent declinePending = PendingIntent.getBroadcast(
                c, 4101, decline, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent decide = new Intent(c, UnlockGateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent decidePending = PendingIntent.getActivity(
                c, 4103, decide, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = baseBuilder(c)
                .setContentIntent(decidePending)
                .setContentTitle("解除条件を達成しました")
                .setContentText("通知をタップして、今回は何分使うか決めてください")
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
        long deadline = Prefs.readyDeadline(c);
        if (deadline > 0L) {
            b.setWhen(deadline).setUsesChronometer(true).setChronometerCountDown(true);
        }
        notifySafe(c, b.build());
    }

    public static void showSession(Context c) {
        ensureChannel(c);
        Intent lockIntent = new Intent(c, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_LOCK_NOW);
        PendingIntent lockPending = PendingIntent.getBroadcast(
                c, 4102, lockIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long now = System.currentTimeMillis();
        long usage = Prefs.liveSessionUsageRemainingMs(c);
        long wallDeadline = Prefs.sessionWallDeadline(c);
        boolean foreground = Prefs.sessionForegroundSince(c) > 0L;

        Notification.Builder b = baseBuilder(c)
                .setContentTitle(Prefs.sessionOverLimit(c) ? "上限超過後の短時間利用中" : "利用中")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_lock_lock,
                        "今すぐ終了",
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

        notifySafe(c, b.build());
    }

    public static void showRecovery(Context c) {
        ensureChannel(c);
        long deadline = Prefs.recoveryDeadline(c);
        if (deadline <= 0L) return;
        notifySafe(c, baseBuilder(c)
                .setContentTitle("利用後の休憩中")
                .setContentText("次に使えるまで")
                .setWhen(deadline)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build());
    }

    public static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    private static long nextChallengeDeadline(Context c) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        long wait = Prefs.challengeWaitDeadline(c);
        long phone = Prefs.challengePhoneDeadline(c);
        if (wait > now) best = Math.min(best, wait);
        if (phone > now) best = Math.min(best, phone);
        return best == Long.MAX_VALUE ? 0L : best;
    }

    private static String challengeText(Context c, int walked) {
        StringBuilder s = new StringBuilder();
        long now = System.currentTimeMillis();
        boolean first = true;
        if (RuleConfig.challengeWait(c)) {
            s.append("待つ ").append(format(Math.max(0L, Prefs.challengeWaitDeadline(c) - now)));
            first = false;
        }
        if (RuleConfig.challengePhoneBreak(c)) {
            if (!first) s.append(" / ");
            s.append("スマホ休憩 ").append(format(Math.max(0L, Prefs.challengePhoneDeadline(c) - now)));
            first = false;
        }
        if (RuleConfig.challengeWalk(c)) {
            if (!first) s.append(" / ");
            s.append("歩く ").append(Math.min(walked, Prefs.challengeRequiredSteps(c)))
                    .append("/").append(Prefs.challengeRequiredSteps(c)).append("歩");
        }
        if (s.length() == 0) s.append("解除条件がありません");
        if (RuleConfig.challengeAll(c)) s.append("（すべて）");
        else s.append("（どれか1つ）");
        return s.toString();
    }

    private static Notification.Builder baseBuilder(Context c) {
        Intent openIntent = new Intent(c, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                c, 4100, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    private static void notifySafe(Context c, Notification notification) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try { nm.notify(NOTIFICATION_ID, notification); } catch (SecurityException ignored) {}
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
