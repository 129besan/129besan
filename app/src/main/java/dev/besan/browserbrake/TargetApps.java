package dev.besan.browserbrake;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TargetApps {
    private static final Set<String> KNOWN_BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.microsoft.emmx",
            "com.microsoft.emmx.beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "com.yandex.browser"
    ));

    private TargetApps() {}

    public static Set<String> browserPackages(Context c) {
        Set<String> out = new HashSet<>(KNOWN_BROWSERS);
        try {
            Intent selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            List<ResolveInfo> infos = c.getPackageManager().queryIntentActivities(selector, 0);
            for (ResolveInfo info : infos) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    out.add(info.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean isTarget(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        if (RuleConfig.customPackages(c).contains(pkg)) return true;
        return RuleConfig.includeBrowsers(c) && browserPackages(c).contains(pkg);
    }
}
