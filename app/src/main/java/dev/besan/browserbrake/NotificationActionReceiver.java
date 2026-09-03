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

        String state = Prefs.state(context);

        if (ACTION_LOCK_NOW.equals(intent.getAction())) {
            if (!Prefs.STATE_SESSION.equals(state)) {
                // Ignore stale Session actions. In particular, never let an old notification
                // clear a newer Recovery/Challenge state.
                BrowserBlockService.requestRuntimeSync();
                return;
            }

            Prefs.finishSession(context);
            if (Prefs.STATE_RECOVERY.equals(Prefs.state(context))) {
                NotificationController.showRecovery(context);
                BrowserBlockService.requestRuntimeSync();
                Toast.makeText(context, "利用を終了し、利用後の休憩に入りました", Toast.LENGTH_SHORT).show();
            } else {
                NotificationController.cancel(context);
                BrowserBlockService.requestRuntimeSync();
                Toast.makeText(context, "利用を終了して再ロックしました", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (ACTION_DECLINE_READY.equals(intent.getAction())) {
            if (!Prefs.STATE_READY.equals(state)) {
                BrowserBlockService.requestRuntimeSync();
                return;
            }

            Prefs.declineReady(context);
            NotificationController.cancel(context);
            BrowserBlockService.requestRuntimeSync();
            Toast.makeText(context, "今回は利用しません", Toast.LENGTH_SHORT).show();
        }
    }
}
