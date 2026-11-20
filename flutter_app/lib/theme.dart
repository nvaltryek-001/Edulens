import 'package:flutter/material.dart';

/// SVIT colours: maroon brand with neutral surfaces.
class AppColors {
  static const brand = Color(0xFF8A1538);
  static const brandSoft = Color(0xFFF6E8ED);
  static const ok = Color(0xFF1E7A46);
  static const okSoft = Color(0xFFE5F4EA);
  static const warn = Color(0xFFA35C00);
  static const warnSoft = Color(0xFFFFF3E0);
  static const bad = Color(0xFFB3261E);
  static const badSoft = Color(0xFFFDECEA);
  static const surface = Color(0xFFF5F6F8);
  static const muted = Color(0xFF5F6B7A);
}

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.brand,
    primary: AppColors.brand,
    surface: Colors.white,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.surface,
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Colors.black87,
      elevation: 0,
      scrolledUnderElevation: 1,
    ),
    cardTheme: const CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(12)),
        side: BorderSide(color: Color(0xFFE2E6EC)),
      ),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      border: OutlineInputBorder(),
      isDense: true,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 44),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
  );
}
