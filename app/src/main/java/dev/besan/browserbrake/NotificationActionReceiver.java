package dev.besan.browserbrake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_LOCK_NOW = "dev.besan.browserbrake.action.LOCK_NOW";
    public static final String ACTION_DECLINE_READY = "dev.besan.browserbrake.action.DECLINE_READY";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (ACTION_LOCK_NOW.equals(intent.getAction())) {
            Prefs.clearTransientState(context);
            NotificationController.cancel(context);
            Toast.makeText(context, "再ロックしました", Toast.LENGTH_SHORT).show();
        } else if (ACTION_DECLINE_READY.equals(intent.getAction())) {
            Prefs.declineReady(context);
            NotificationController.cancel(context);
            Toast.makeText(context, "今回は利用しません", Toast.LENGTH_SHORT).show();
        }
    }
}
