package dev.besan.browserbrake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import dev.besan.browserbrake.runtime.RuleRuntimeStore;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_LOCK_NOW = "dev.besan.browserbrake.action.LOCK_NOW";
    public static final String ACTION_DECLINE_READY = "dev.besan.browserbrake.action.DECLINE_READY";
    public static final String EXTRA_RULE_ID = "rule_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String ruleId = intent.getStringExtra(EXTRA_RULE_ID);
        if (ruleId == null || ruleId.isBlank()) return;

        String state = RuleRuntimeStore.state(context, ruleId);

        if (ACTION_LOCK_NOW.equals(intent.getAction())) {
            if (!RuleRuntimeStore.STATE_SESSION.equals(state)) {
                BrowserBlockService.requestRuntimeSync();
                return;
            }

            RuleRuntimeStore.finishSession(context, ruleId);
            if (RuleRuntimeStore.STATE_RECOVERY.equals(RuleRuntimeStore.state(context, ruleId))) {
                NotificationController.showRecovery(context, ruleId);
                Toast.makeText(context, "利用を終了し、利用後の休憩に入りました", Toast.LENGTH_SHORT).show();
            } else {
                NotificationController.cancel(context, ruleId);
                Toast.makeText(context, "利用を終了して再ロックしました", Toast.LENGTH_SHORT).show();
            }
            BrowserBlockService.requestRuntimeSync();
            return;
        }

        if (ACTION_DECLINE_READY.equals(intent.getAction())) {
            if (!RuleRuntimeStore.STATE_READY.equals(state)) {
                BrowserBlockService.requestRuntimeSync();
                return;
            }

            RuleRuntimeStore.declineReady(context, ruleId);
            NotificationController.cancel(context, ruleId);
            BrowserBlockService.requestRuntimeSync();
            Toast.makeText(context, "今回は利用しません", Toast.LENGTH_SHORT).show();
        }
    }
}
