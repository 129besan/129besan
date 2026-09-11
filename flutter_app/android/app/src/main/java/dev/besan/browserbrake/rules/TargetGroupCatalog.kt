package dev.besan.browserbrake.rules

import android.content.Context
import dev.besan.browserbrake.TargetApps

object TargetGroupCatalog {
    @JvmField
    val SNS_PACKAGES: Set<String> = setOf(
        "com.twitter.android",
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.reddit.frontpage",
        "xyz.blueskyweb.app",
        "com.facebook.katana",
        "org.joinmastodon.android"
    )

    @JvmStatic
    fun isSnsPackage(pkg: String?): Boolean =
        pkg != null && SNS_PACKAGES.contains(pkg)

    @JvmStatic
    fun packageBelongs(context: Context, rule: BrowserRule, pkg: String?): Boolean {
        if (pkg.isNullOrBlank() || pkg == context.packageName) return false
        if (pkg in rule.customPackages) return true
        if (rule.sns && isSnsPackage(pkg)) return true
        return rule.browsers && TargetApps.browserPackages(context).contains(pkg)
    }

    @JvmStatic
    fun targetSummary(context: Context, rule: BrowserRule): String {
        val parts = mutableListOf<String>()
        if (rule.browsers) parts += "ブラウザ"
        if (rule.sns) parts += "SNS"
        if (rule.customPackages.isNotEmpty()) parts += "個別 ${rule.customPackages.size}個"
        return parts.ifEmpty { listOf("対象なし") }.joinToString("・")
    }
}
