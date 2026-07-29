import 'package:flutter/material.dart';

/// Design tokens for Open Wearable Insights.
///
/// A calm, clinical-but-warm visual language for a health/longevity
/// wearable app: teal-cyan primary (trust, precision), health-green accent
/// (positive signal), and a strict semantic status palette (good/fair/poor)
/// so widgets never reach for a raw `Colors.red`/`Colors.green` again —
/// see [AppStatusColors].

class AppSpacing {
  const AppSpacing._();
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
  static const double xxxl = 48;
}

class AppRadius {
  const AppRadius._();
  static const double sm = 10;
  static const double md = 14;
  static const double lg = 20;
  static const double pill = 999;
}

/// Semantic status colors, distinct from the brand [ColorScheme].
///
/// Material 3's ColorScheme has no "success" or "warning" slot, and every
/// screen in this app needs to express good/fair/poor/info signal (scores,
/// data confidence, trend direction) — so it's a first-class [ThemeExtension]
/// instead of ad hoc `Colors.green.shade600` sprinkled through widgets.
@immutable
class AppStatusColors extends ThemeExtension<AppStatusColors> {
  final Color good;
  final Color onGood;
  final Color goodContainer;
  final Color onGoodContainer;
  final Color fair;
  final Color onFair;
  final Color fairContainer;
  final Color onFairContainer;
  final Color poor;
  final Color onPoor;
  final Color poorContainer;
  final Color onPoorContainer;
  final Color info;
  final Color onInfo;
  final Color infoContainer;
  final Color onInfoContainer;

  const AppStatusColors({
    required this.good,
    required this.onGood,
    required this.goodContainer,
    required this.onGoodContainer,
    required this.fair,
    required this.onFair,
    required this.fairContainer,
    required this.onFairContainer,
    required this.poor,
    required this.onPoor,
    required this.poorContainer,
    required this.onPoorContainer,
    required this.info,
    required this.onInfo,
    required this.infoContainer,
    required this.onInfoContainer,
  });

  static const light = AppStatusColors(
    good: Color(0xFF3C5C33),
    onGood: Color(0xFFFFFFFF),
    goodContainer: Color(0xFFE7EEE3),
    onGoodContainer: Color(0xFF23361D),
    fair: Color(0xFF8A5A17),
    onFair: Color(0xFFFFFFFF),
    fairContainer: Color(0xFFF6E8CF),
    onFairContainer: Color(0xFF4A3609),
    poor: Color(0xFF9A3922),
    onPoor: Color(0xFFFFFFFF),
    poorContainer: Color(0xFFF7E3DB),
    onPoorContainer: Color(0xFF5C210F),
    info: Color(0xFF4A6FA5),
    onInfo: Color(0xFFFFFFFF),
    infoContainer: Color(0xFFE3EAF3),
    onInfoContainer: Color(0xFF23374F),
  );

  static const dark = AppStatusColors(
    good: Color(0xFF8FCB7C),
    onGood: Color(0xFF1D3212),
    goodContainer: Color(0xFF2C4020),
    onGoodContainer: Color(0xFFC9E8BC),
    fair: Color(0xFFE8B84B),
    onFair: Color(0xFF402D06),
    fairContainer: Color(0xFF4A3712),
    onFairContainer: Color(0xFFF6DDA0),
    poor: Color(0xFFFF8A78),
    onPoor: Color(0xFF4A150A),
    poorContainer: Color(0xFF522017),
    onPoorContainer: Color(0xFFFFC4B8),
    info: Color(0xFF7FA3D6),
    onInfo: Color(0xFF12233D),
    infoContainer: Color(0xFF253C5C),
    onInfoContainer: Color(0xFFC7DAF2),
  );

  /// The three-tier band used everywhere a 0-100 score is shown
  /// (readiness, sleep score, data completeness, ...).
  Color forScore(num score, {num goodAt = 75, num fairAt = 50}) {
    if (score >= goodAt) return good;
    if (score >= fairAt) return fair;
    return poor;
  }

  Color containerForScore(num score, {num goodAt = 75, num fairAt = 50}) {
    if (score >= goodAt) return goodContainer;
    if (score >= fairAt) return fairContainer;
    return poorContainer;
  }

  @override
  AppStatusColors copyWith({
    Color? good,
    Color? onGood,
    Color? goodContainer,
    Color? onGoodContainer,
    Color? fair,
    Color? onFair,
    Color? fairContainer,
    Color? onFairContainer,
    Color? poor,
    Color? onPoor,
    Color? poorContainer,
    Color? onPoorContainer,
    Color? info,
    Color? onInfo,
    Color? infoContainer,
    Color? onInfoContainer,
  }) {
    return AppStatusColors(
      good: good ?? this.good,
      onGood: onGood ?? this.onGood,
      goodContainer: goodContainer ?? this.goodContainer,
      onGoodContainer: onGoodContainer ?? this.onGoodContainer,
      fair: fair ?? this.fair,
      onFair: onFair ?? this.onFair,
      fairContainer: fairContainer ?? this.fairContainer,
      onFairContainer: onFairContainer ?? this.onFairContainer,
      poor: poor ?? this.poor,
      onPoor: onPoor ?? this.onPoor,
      poorContainer: poorContainer ?? this.poorContainer,
      onPoorContainer: onPoorContainer ?? this.onPoorContainer,
      info: info ?? this.info,
      onInfo: onInfo ?? this.onInfo,
      infoContainer: infoContainer ?? this.infoContainer,
      onInfoContainer: onInfoContainer ?? this.onInfoContainer,
    );
  }

  @override
  AppStatusColors lerp(ThemeExtension<AppStatusColors>? other, double t) {
    if (other is! AppStatusColors) return this;
    Color l(Color a, Color b) => Color.lerp(a, b, t)!;
    return AppStatusColors(
      good: l(good, other.good),
      onGood: l(onGood, other.onGood),
      goodContainer: l(goodContainer, other.goodContainer),
      onGoodContainer: l(onGoodContainer, other.onGoodContainer),
      fair: l(fair, other.fair),
      onFair: l(onFair, other.onFair),
      fairContainer: l(fairContainer, other.fairContainer),
      onFairContainer: l(onFairContainer, other.onFairContainer),
      poor: l(poor, other.poor),
      onPoor: l(onPoor, other.onPoor),
      poorContainer: l(poorContainer, other.poorContainer),
      onPoorContainer: l(onPoorContainer, other.onPoorContainer),
      info: l(info, other.info),
      onInfo: l(onInfo, other.onInfo),
      infoContainer: l(infoContainer, other.infoContainer),
      onInfoContainer: l(onInfoContainer, other.onInfoContainer),
    );
  }
}

extension AppThemeX on ThemeData {
  AppStatusColors get status => extension<AppStatusColors>() ?? AppStatusColors.light;
}

class AppTheme {
  const AppTheme._();

  // "Terracotta Wellness" — warm neutral ground, deep forest primary, clay
  // accent, sage secondary. Organic/calm rather than clinical-blue.
  static const _primaryLight = Color(0xFF2F4A3C);
  static const _primaryDark = Color(0xFF9CC49B);

  static ThemeData light() => _build(Brightness.light);
  static ThemeData dark() => _build(Brightness.dark);

  static ColorScheme _lightScheme() {
    final base = ColorScheme.fromSeed(seedColor: _primaryLight, brightness: Brightness.light);
    return base.copyWith(
      primary: _primaryLight,
      onPrimary: const Color(0xFFFFFFFF),
      primaryContainer: const Color(0xFFE1E9DD),
      onPrimaryContainer: const Color(0xFF1D3226),
      secondary: const Color(0xFF7C9A72),
      onSecondary: const Color(0xFFFFFFFF),
      secondaryContainer: const Color(0xFFE7EEE3),
      onSecondaryContainer: const Color(0xFF3C5C33),
      tertiary: const Color(0xFFC6733E),
      onTertiary: const Color(0xFFFFFFFF),
      tertiaryContainer: const Color(0xFFF6E1CE),
      onTertiaryContainer: const Color(0xFF7A3E14),
      error: const Color(0xFF9A3922),
      onError: const Color(0xFFFFFFFF),
      errorContainer: const Color(0xFFF7E3DB),
      onErrorContainer: const Color(0xFF5C210F),
      surface: const Color(0xFFFAF6EF),
      onSurface: const Color(0xFF2B2620),
      surfaceContainerLowest: const Color(0xFFFFFFFF),
      surfaceContainerLow: const Color(0xFFFFFFFF),
      surfaceContainer: const Color(0xFFF5F0E6),
      surfaceContainerHigh: const Color(0xFFF1EBDD),
      surfaceContainerHighest: const Color(0xFFEDE5D4),
      onSurfaceVariant: const Color(0xFF6B6255),
      outline: const Color(0xFFC9BEA9),
      outlineVariant: const Color(0xFFEAE1D2),
      inverseSurface: const Color(0xFF2B2620),
      onInverseSurface: const Color(0xFFFAF6EF),
      inversePrimary: const Color(0xFF9CC49B),
    );
  }

  static ColorScheme _darkScheme() {
    final base = ColorScheme.fromSeed(seedColor: _primaryDark, brightness: Brightness.dark);
    return base.copyWith(
      primary: _primaryDark,
      onPrimary: const Color(0xFF17301E),
      primaryContainer: const Color(0xFF24402C),
      onPrimaryContainer: const Color(0xFFB8DAB4),
      secondary: const Color(0xFFA8C29C),
      onSecondary: const Color(0xFF23331E),
      secondaryContainer: const Color(0xFF354A2C),
      onSecondaryContainer: const Color(0xFFC7E0BC),
      tertiary: const Color(0xFFE2996A),
      onTertiary: const Color(0xFF4A2408),
      tertiaryContainer: const Color(0xFF5C3316),
      onTertiaryContainer: const Color(0xFFF6CBA6),
      error: const Color(0xFFE2836A),
      onError: const Color(0xFF4A160A),
      errorContainer: const Color(0xFF5C2415),
      onErrorContainer: const Color(0xFFF6CFC0),
      surface: const Color(0xFF1C1912),
      onSurface: const Color(0xFFEDE7DA),
      surfaceContainerLowest: const Color(0xFF17140F),
      surfaceContainerLow: const Color(0xFF211D15),
      surfaceContainer: const Color(0xFF262119),
      surfaceContainerHigh: const Color(0xFF2C271D),
      surfaceContainerHighest: const Color(0xFF322C21),
      onSurfaceVariant: const Color(0xFFB7AD9B),
      outline: const Color(0xFF8C8271),
      outlineVariant: const Color(0xFF3A342A),
      inverseSurface: const Color(0xFFEDE7DA),
      onInverseSurface: const Color(0xFF2B2620),
      inversePrimary: const Color(0xFF2F4A3C),
    );
  }

  static ThemeData _build(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final scheme = isDark ? _darkScheme() : _lightScheme();
    final status = isDark ? AppStatusColors.dark : AppStatusColors.light;
    final textTheme = _textTheme(scheme);

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      textTheme: textTheme,
      extensions: [status],
      visualDensity: VisualDensity.standard,
      splashFactory: InkSparkle.splashFactory,
      appBarTheme: AppBarTheme(
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge,
        iconTheme: IconThemeData(color: scheme.onSurfaceVariant),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
          side: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.6)),
        ),
        margin: EdgeInsets.zero,
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant,
        space: AppSpacing.xl,
        thickness: 1,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
          elevation: 0,
          backgroundColor: scheme.primary,
          foregroundColor: scheme.onPrimary,
          disabledBackgroundColor: scheme.onSurface.withValues(alpha: 0.12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(64, 48),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
          side: BorderSide(color: scheme.outline),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: const Size(48, 48),
          foregroundColor: scheme.onSurfaceVariant,
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: scheme.surfaceContainerHigh,
        selectedColor: scheme.primaryContainer,
        labelStyle: textTheme.labelLarge!.copyWith(color: scheme.onSurface),
        secondaryLabelStyle: textTheme.labelLarge!.copyWith(color: scheme.onPrimaryContainer),
        side: BorderSide.none,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.pill)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerHighest,
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
        labelStyle: textTheme.bodyMedium,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: scheme.primary,
        linearTrackColor: scheme.surfaceContainerHighest,
        circularTrackColor: scheme.surfaceContainerHighest,
      ),
      listTileTheme: ListTileThemeData(
        iconColor: scheme.onSurfaceVariant,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.md)),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: scheme.surfaceContainer,
        surfaceTintColor: Colors.transparent,
        indicatorColor: scheme.primaryContainer,
        elevation: 0,
        height: 68,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return textTheme.labelMedium!.copyWith(
            color: selected ? scheme.onSurface : scheme.onSurfaceVariant,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? scheme.onPrimaryContainer : scheme.onSurfaceVariant,
          );
        }),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: scheme.tertiary,
        foregroundColor: isDark ? Colors.black : Colors.white,
        elevation: 1,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.lg)),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: scheme.inverseSurface,
        contentTextStyle: textTheme.bodyMedium!.copyWith(color: scheme.onInverseSurface),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.md)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.lg)),
        ),
      ),
      expansionTileTheme: ExpansionTileThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.md)),
        collapsedShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.md)),
        iconColor: scheme.onSurfaceVariant,
        collapsedIconColor: scheme.onSurfaceVariant,
      ),
    );
  }

  static TextTheme _textTheme(ColorScheme scheme) {
    final base = ThemeData(brightness: scheme.brightness).textTheme;
    return base.copyWith(
      // Score hero numbers (readiness, sleep score, completeness %).
      displayLarge: base.displayLarge?.copyWith(
        fontSize: 60,
        fontWeight: FontWeight.w700,
        letterSpacing: -1.5,
        height: 1.0,
        color: scheme.onSurface,
      ),
      headlineSmall: base.headlineSmall?.copyWith(
        fontSize: 22,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
        color: scheme.onSurface,
      ),
      titleLarge: base.titleLarge?.copyWith(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.1,
        color: scheme.onSurface,
      ),
      titleMedium: base.titleMedium?.copyWith(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: scheme.onSurface,
      ),
      bodyLarge: base.bodyLarge?.copyWith(
        fontSize: 16,
        height: 1.5,
        color: scheme.onSurface,
      ),
      bodyMedium: base.bodyMedium?.copyWith(
        fontSize: 14,
        height: 1.5,
        color: scheme.onSurfaceVariant,
      ),
      bodySmall: base.bodySmall?.copyWith(
        fontSize: 12,
        height: 1.4,
        color: scheme.onSurfaceVariant,
      ),
      labelLarge: base.labelLarge?.copyWith(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.1,
      ),
      labelMedium: base.labelMedium?.copyWith(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.2,
      ),
      labelSmall: base.labelSmall?.copyWith(
        fontSize: 11,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.4,
        color: scheme.onSurfaceVariant,
      ),
    );
  }
}
