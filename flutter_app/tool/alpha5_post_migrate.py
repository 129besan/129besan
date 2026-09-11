from pathlib import Path

# Ensure the context-aware selector helper exists after the first migration rewrites its call site.
p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()
marker = '    private BrowserRule findActiveRuntimeRuleForPackage(String pkg) {'
helper = '''    private BrowserRule findContextMatchingRuleForPackage(String pkg) {
        if (pkg == null || pkg.isBlank()) return null;
        for (BrowserRule rule : RuleRepository.getRules(this)) {
            if (RuleRepository.isEffective(rule)
                    && TargetGroupCatalog.packageBelongs(this, rule, pkg)
                    && isRuleContextEligible(rule)) {
                return rule;
            }
        }
        return null;
    }

'''
if 'private BrowserRule findContextMatchingRuleForPackage' not in s:
    if marker not in s:
        raise SystemExit('missing runtime selector marker')
    s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# Background location is part of readiness for place-based rules on modern Android.
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
old = '''        val location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
'''
new = '''        val location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationReady = location && backgroundLocation
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
'''
if old not in s:
    raise SystemExit('missing health location block')
s = s.replace(old, new, 1)
s = s.replace('''            "location" to location,
            "batteryUnrestricted" to batteryUnrestricted,
''', '''            "location" to location,
            "backgroundLocation" to backgroundLocation,
            "locationReady" to locationReady,
            "batteryUnrestricted" to batteryUnrestricted,
''', 1)
p.write_text(s)

# Settings should distinguish foreground location from the always-allowed state needed by enforcement.
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace("health['location'] == true ? '許可済み' : '場所指定ルールに必要'", "health['locationReady'] == true ? '常に許可されています' : '「常に許可」が必要です'")
p.write_text(s)

# The staging generator uses a Python triple-quoted string. Preserve the Dart \n escape
# instead of letting Python turn it into a literal line break inside a single-quoted string.
p = Path('lib/onboarding_modern.dart')
s = p.read_text()
s = s.replace("validation.join('\n')", "validation.join('\\n')")
p.write_text(s)
