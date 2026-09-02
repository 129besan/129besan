package dev.besan.browserbrake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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
                "Browser Brake timer",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Browser Brakeの待機時間と一時解除時間を表示します");
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    public static void showChallenge(Context c) {
        ensureChannel(c);
        long deadline = Prefs.challengeDeadline(c);
        if (deadline <= 0L) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.notify(NOTIFICATION_ID, baseBuilder(c)
                    .setContentTitle("5分間、スマホを操作しないでください")
                    .setContentText("クリック・スクロール・入力などをすると5:00へ戻ります")
                    .setWhen(deadline)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build());
        } catch (SecurityException ignored) {}
    }

    public static void showUnlocked(Context c) {
        ensureChannel(c);
        long deadline = Prefs.unlockUntil(c);
        if (deadline <= 0L) return;

        Intent lockIntent = new Intent(c, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_LOCK_NOW);
        PendingIntent lockPending = PendingIntent.getBroadcast(
                c, 4001, lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.notify(NOTIFICATION_ID, baseBuilder(c)
                    .setContentTitle("ブラウザを一時解放中")
                    .setContentText("時間切れになると自動で再ロックします")
                    .setWhen(deadline)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .addAction(new Notification.Action.Builder(
                            android.R.drawable.ic_lock_lock,
                            "今すぐロック",
                            lockPending
                    ).build())
                    .build());
        } catch (SecurityException ignored) {}
    }

    public static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    private static Notification.Builder baseBuilder(Context c) {
        Intent openIntent = new Intent(c, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                c, 4000, openIntent,
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
}
