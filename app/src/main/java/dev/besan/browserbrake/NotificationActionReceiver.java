package dev.besan.browserbrake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_LOCK_NOW = "dev.besan.browserbrake.action.LOCK_NOW";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_LOCK_NOW.equals(intent.getAction())) return;
        Prefs.clearTransientState(context);
        NotificationController.cancel(context);
        Toast.makeText(context, "ブラウザを再ロックしました", Toast.LENGTH_SHORT).show();
    }
}
