package dev.besan.browserbrake;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UnlockGateActivity extends Activity {
    private LinearLayout choices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.STATE_READY.equals(Prefs.state(this))) {
            Toast.makeText(this, "解除可能な状態ではありません", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(Prefs.isOverDailyLimit(this) ? "今日の上限を超えています" : "解除条件を達成しました");
        title.setTextSize(26f);
        root.addView(title);

        TextView message = new TextView(this);
        message.setText(Prefs.isOverDailyLimit(this)
                ? "通常の利用上限を超えています。本当に必要な場合だけ短時間利用できます。"
                : "本当に必要なら利用してください。そうでなければ今回はやめられます。");
        message.setTextSize(17f);
        message.setPadding(0, dp(12), 0, dp(20));
        root.addView(message);

        Button use = button("利用する");
        root.addView(use);

        Button decline = button("今回はやめる");
        decline.setOnClickListener(v -> {
            Prefs.declineReady(this);
            NotificationController.cancel(this);
            finish();
        });
        root.addView(decline);

        choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setVisibility(View.GONE);
        choices.setPadding(0, dp(20), 0, 0);
        root.addView(choices);

        use.setOnClickListener(v -> {
            if (RuleConfig.askSessionDuration(this)) {
                use.setVisibility(View.GONE);
                showDurationChoices();
            } else {
                startSession(RuleConfig.defaultSessionUsageMs(this));
            }
        });

        setContentView(root);
    }

    private void showDurationChoices() {
        choices.removeAllViews();
        choices.setVisibility(View.VISIBLE);

        TextView label = new TextView(this);
        label.setText("今回は何分使いますか？");
        label.setTextSize(20f);
        choices.addView(label);

        long dailyRemaining = Prefs.dailyUsageRemainingMs(this);
        boolean over = Prefs.isOverDailyLimit(this);
        long[] defaults = over
                ? new long[]{60_000L, 3L * 60_000L, RuleConfig.overLimitSessionMs(this)}
                : new long[]{5L * 60_000L, 10L * 60_000L, 15L * 60_000L};

        Set<Long> unique = new LinkedHashSet<>();
        for (long d : defaults) {
            long v = d;
            if (over) v = Math.min(v, RuleConfig.overLimitSessionMs(this));
            else if (dailyRemaining >= 0L) v = Math.min(v, dailyRemaining);
            if (v >= 60_000L) unique.add(v);
        }
        if (unique.isEmpty()) unique.add(over ? RuleConfig.overLimitSessionMs(this) : 60_000L);

        List<Long> values = new ArrayList<>(unique);
        for (long value : values) {
            Button b = button((value / 60_000L) + "分使う");
            b.setOnClickListener(v -> startSession(value));
            choices.addView(b);
        }

        if (!over && dailyRemaining >= 0L) {
            TextView remaining = new TextView(this);
            remaining.setText("今日の残り利用時間: " + NotificationController.format(dailyRemaining));
            remaining.setPadding(0, dp(8), 0, 0);
            choices.addView(remaining);
        }
    }

    private void startSession(long usageMs) {
        String pkg = Prefs.pendingTarget(this);
        Prefs.startSession(this, usageMs);
        NotificationController.showSession(this);

        Intent launch = pkg.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Toast.makeText(this, "対象アプリをもう一度開いてください", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
