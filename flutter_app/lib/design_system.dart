import 'package:flutter/material.dart';

const appInk = Color(0xFF082F4F);
const appBlue = Color(0xFF1769AA);

ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: appBlue,
    brightness: Brightness.light,
    surface: const Color(0xFFF8FBFD),
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: Colors.transparent,
    fontFamily: 'sans-serif',
    fontFamilyFallback: const ['Noto Sans CJK JP', 'Noto Sans JP'],
    textTheme: Typography.material2021(platform: TargetPlatform.android)
        .black
        .apply(fontFamily: 'sans-serif', bodyColor: appInk, displayColor: appInk),
    cardTheme: const CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(24))),
    ),
    listTileTheme: const ListTileThemeData(
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 3),
      iconColor: Color(0xFF285E87),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      filled: true,
      fillColor: Color(0xEFFFFFFF),
      border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(18)), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(18)), borderSide: BorderSide.none),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 70,
      elevation: 0,
      backgroundColor: scheme.surface.withValues(alpha: .96),
      indicatorColor: scheme.secondaryContainer,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(minimumSize: const Size(0, 52), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18))),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(minimumSize: const Size(0, 52), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18))),
    ),
    dividerTheme: const DividerThemeData(color: Color(0x1F315A78), space: 1),
  );
}

class SoftSurface extends StatelessWidget {
  const SoftSurface({super.key, required this.child, this.padding, this.radius = 24});
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final double radius;
  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(radius),
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xF8FFFFFF), Color(0xEDF3FAFF)],
      ),
      border: Border.all(color: const Color(0x66FFFFFF)),
      boxShadow: const [BoxShadow(color: Color(0x160B426C), blurRadius: 22, offset: Offset(0, 8))],
    ),
    padding: padding,
    child: child,
  );
}

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key});
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(6, 10, 6, 8),
    child: Text(text, style: Theme.of(context).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w700, color: const Color(0xFF315F82))),
  );
}
