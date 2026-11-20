import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'screens/faculty_home.dart';
import 'screens/login_screen.dart';
import 'screens/student_home.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ApiClient.instance.restore();
  runApp(const EduLensApp());
}

class EduLensApp extends StatelessWidget {
  const EduLensApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EduLens · SVIT',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: const RootGate(),
    );
  }
}

/// Sends the user to the right home screen for their role.
class RootGate extends StatefulWidget {
  const RootGate({super.key});
  @override
  State<RootGate> createState() => _RootGateState();
}

class _RootGateState extends State<RootGate> {
  final api = ApiClient.instance;

  void _refresh() => setState(() {});

  Future<void> _logout() async {
    await api.logout();
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    if (api.token == null) return LoginScreen(onLoggedIn: _refresh);
    if (api.role == 'student') return StudentHome(onLogout: _logout);
    if (api.isStaff) return FacultyHome(onLogout: _logout);
    return LoginScreen(onLoggedIn: _refresh);
  }
}
