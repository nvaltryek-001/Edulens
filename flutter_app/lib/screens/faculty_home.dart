import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'alerts_screen.dart';
import 'attendance_screen.dart';
import 'marks_screen.dart';
import 'shortage_screen.dart';

class FacultyHome extends StatefulWidget {
  const FacultyHome({super.key, required this.onLogout});
  final VoidCallback onLogout;
  @override
  State<FacultyHome> createState() => _FacultyHomeState();
}

class _FacultyHomeState extends State<FacultyHome> {
  int _tab = 0;

  static const _titles = ['Take attendance', 'Shortage (85%)', 'Parent alerts', 'IA marks & deadlines'];

  @override
  Widget build(BuildContext context) {
    final api = ApiClient.instance;
    return Scaffold(
      appBar: AppBar(
        leading: Padding(
          padding: const EdgeInsets.all(8),
          child: Image.asset('assets/svit_logo.jpeg'),
        ),
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(_titles[_tab], style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
          Text('${api.username} · ${api.role}', style: const TextStyle(fontSize: 12)),
        ]),
        actions: [IconButton(onPressed: widget.onLogout, icon: const Icon(Icons.logout), tooltip: 'Sign out')],
      ),
      body: IndexedStack(index: _tab, children: const [
        AttendanceScreen(),
        ShortageScreen(),
        AlertsScreen(),
        MarksScreen(),
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.photo_camera_outlined), selectedIcon: Icon(Icons.photo_camera), label: 'Attendance'),
          NavigationDestination(icon: Icon(Icons.warning_amber_outlined), selectedIcon: Icon(Icons.warning_amber), label: 'Shortage'),
          NavigationDestination(icon: Icon(Icons.sms_outlined), selectedIcon: Icon(Icons.sms), label: 'Alerts'),
          NavigationDestination(icon: Icon(Icons.grading_outlined), selectedIcon: Icon(Icons.grading), label: 'Marks'),
        ],
      ),
    );
  }
}
