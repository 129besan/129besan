from pathlib import Path
import base64, zlib

Path("flutter_app/lib/catalog_pages.dart").write_bytes(zlib.decompress(base64.b64decode('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')))

p=Path("flutter_app/lib/main.dart")
s=p.read_text()
old="""      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: Colors.transparent,
        cardTheme: const CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(26)),
          ),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          border: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
          ),
        ),
        navigationBarTheme: NavigationBarThemeData(
          height: 72,
          backgroundColor: scheme.surface.withValues(alpha: .96),
          indicatorColor: scheme.secondaryContainer,
        ),
      ),"""
new="""      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        fontFamily: 'sans-serif',
        scaffoldBackgroundColor: Colors.transparent,
        textTheme: ThemeData.light().textTheme.apply(
          fontFamily: 'sans-serif',
          bodyColor: const Color(0xFF102A43),
          displayColor: const Color(0xFF102A43),
        ),
        cardTheme: const CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          surfaceTintColor: Colors.transparent,
          clipBehavior: Clip.antiAlias,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(24)),
            side: BorderSide(color: Color(0x33FFFFFF)),
          ),
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
          scrolledUnderElevation: 0,
          backgroundColor: Colors.transparent,
          surfaceTintColor: Colors.transparent,
          titleTextStyle: TextStyle(
            fontFamily: 'sans-serif',
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: Color(0xFF102A43),
          ),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Color(0xDFFFFFFF),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
            borderSide: BorderSide(color: Color(0x1F0B5A86)),
          ),
        ),
        navigationBarTheme: NavigationBarThemeData(
          height: 70,
          backgroundColor: Colors.white.withValues(alpha: .94),
          indicatorColor: const Color(0xFFD8EEFA),
          elevation: 0,
          labelTextStyle: WidgetStateProperty.all(
            const TextStyle(fontFamily: 'sans-serif', fontWeight: FontWeight.w600),
          ),
        ),
      ),"""
if old not in s: raise SystemExit("theme needle not found")
s=s.replace(old,new,1)
s=s.replace("color: Theme.of(context).colorScheme.surface.withValues(alpha: .91),", "color: Colors.white.withValues(alpha: .86),")
s=s.replace("Text(subtitle, style: const TextStyle(color: Color(0xFF174C70))),", "Text(subtitle, style: const TextStyle(color: Color(0xFF486581))),")
s=s.replace("subtitle: const Text('対象アプリと解除条件だけ先に決めます'),", "subtitle: const Text('4ステップで、対象と開く前の摩擦を決めます'),")
old_phone="""                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('スマホ休憩'),
                    value: b('challengePhoneBreak', true),
                    onChanged: (v) => setValue('challengePhoneBreak', v),
                  ),
                  if (b('challengePhoneBreak'))
                    _ChoiceRow(
                      label: '休憩時間',
                      value: n('phoneBreakMs', 180000),
                      options: const {60000: '1分', 180000: '3分', 300000: '5分', 600000: '10分'},
                      onChanged: (v) => setValue('phoneBreakMs', v),
                    ),
"""
new_phone="""                  if (b('challengePhoneBreak')) ...[
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('スマホ休憩（旧方式）'),
                      subtitle: const Text('新規設定では使いません。オフにすると一覧から消えます。'),
                      value: true,
                      onChanged: (v) => setValue('challengePhoneBreak', v),
                    ),
                    _ChoiceRow(
                      label: '休憩時間',
                      value: n('phoneBreakMs', 180000),
                      options: const {60000: '1分', 180000: '3分', 300000: '5分', 600000: '10分'},
                      onChanged: (v) => setValue('phoneBreakMs', v),
                    ),
                  ],
"""
if old_phone in s: s=s.replace(old_phone,new_phone,1)
p.write_text(s)

p=Path("flutter_app/pubspec.yaml")
s=p.read_text().replace("version: 0.6.0-alpha.3+21", "version: 0.6.0-alpha.4+22")
p.write_text(s)
