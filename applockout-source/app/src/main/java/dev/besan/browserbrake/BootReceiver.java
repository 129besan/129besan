package dev.besan.browserbrake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.besan.browserbrake.rules.RuleRepository;
import dev.besan.browserbrake.runtime.RuleRuntimeStore;

/**
 * Reconciles persisted runtime state after a real device reboot or an app update.
 * The AccessibilityService itself is system-managed and must not be started here.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        RuleRepository.ensureMigrated(context);
        RuleRuntimeStore.ensureMigrated(context);
        RuleRuntimeStore.reconcilePersistentState(context);
        NotificationController.ensureChannel(context);
    }
}
