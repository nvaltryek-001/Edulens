/// Server address. Pass it at build time:
///   flutter run --dart-define=API_URL=http://192.168.1.50:5000
/// Default 10.0.2.2 is the host computer when running in the Android emulator.
const String apiBaseUrl = String.fromEnvironment(
  'API_URL',
  defaultValue: 'http://10.0.2.2:5000',
);

/// VTU minimum attendance per course.
const double minimumAttendance = 85;
