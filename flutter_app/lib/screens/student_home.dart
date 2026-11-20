import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// Student portal: subject-wise attendance, IA marks, deadlines.
class StudentHome extends StatefulWidget {
  const StudentHome({super.key, required this.onLogout});
  final VoidCallback onLogout;
  @override
  State<StudentHome> createState() => _StudentHomeState();
}

class _StudentHomeState extends State<StudentHome> {
  final api = ApiClient.instance;
  Map<String, dynamic>? _att;
  List<dynamic> _marks = [];
  List<dynamic> _assign = [];
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        api.get('/api/me/attendance'),
        api.get('/api/me/marks'),
        api.get('/api/me/assignments'),
      ]);
      setState(() {
        _att = results[0];
        _marks = results[1]['marks'] as List;
        _assign = results[2]['assignments'] as List;
        _error = null;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final student = _att?['student'] as Map<String, dynamic>?;
    final subjects = (_att?['subjects'] as List?) ?? [];
    final minimum = (_att?['minimumPercent'] as num?)?.toDouble() ?? 85;
    final short = subjects.where((s) => s['shortage'] == true).length;

    return Scaffold(
      appBar: AppBar(
        leading: Padding(padding: const EdgeInsets.all(8), child: Image.asset('assets/svit_logo.jpeg')),
        title: const Text('My attendance'),
        actions: [IconButton(onPressed: widget.onLogout, icon: const Icon(Icons.logout), tooltip: 'Sign out')],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(padding: const EdgeInsets.all(16), children: [
          if (_error != null) Text(_error!, style: const TextStyle(color: AppColors.bad)),
          if (student != null)
            SectionCard(
              title: '${student['name']}',
              subtitle: '${student['usn']} · ${student['department']} · Sem ${student['semester']} · Sec ${student['section']}',
              child: Row(children: [
                Expanded(child: StatTile(value: '${_att!['overallPercent']}%', label: 'Overall')),
                const SizedBox(width: 8),
                Expanded(child: StatTile(value: '${subjects.length}', label: 'Subjects')),
                const SizedBox(width: 8),
                Expanded(child: StatTile(value: '$short', label: 'Below ${minimum.toInt()}%', color: short > 0 ? AppColors.bad : AppColors.ok)),
              ]),
            ),
          const SizedBox(height: 12),
          SectionCard(
            title: 'Subjects',
            subtitle: 'The line marks the ${minimum.toInt()}% VTU minimum.',
            child: Column(children: [
              if (subjects.isEmpty) const EmptyNote('No confirmed classes yet.'),
              for (final s in subjects) _SubjectRow(s as Map<String, dynamic>, minimum),
            ]),
          ),
          const SizedBox(height: 12),
          SectionCard(
            title: 'IA marks',
            child: Column(children: [
              if (_marks.isEmpty) const EmptyNote('No marks yet.'),
              for (final m in _marks)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${m['subject']} · IA ${m['ia_no']}'),
                  trailing: Text('${m['marks']} / ${m['max_marks']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                ),
            ]),
          ),
          const SizedBox(height: 12),
          SectionCard(
            title: 'Upcoming deadlines',
            child: Column(children: [
              if (_assign.isEmpty) const EmptyNote('Nothing due.'),
              for (final a in _assign)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.assignment_outlined, color: AppColors.brand),
                  title: Text('${a['title']}'),
                  subtitle: Text('${a['subject']} · due ${a['deadline']}'),
                ),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _SubjectRow extends StatelessWidget {
  const _SubjectRow(this.s, this.minimum);
  final Map<String, dynamic> s;
  final double minimum;

  @override
  Widget build(BuildContext context) {
    final pct = (s['percent'] as num).toDouble();
    final shortage = s['shortage'] == true;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text('${s['subject']}', style: const TextStyle(fontWeight: FontWeight.w600))),
          Pill('${pct.toStringAsFixed(1)}%', good: !shortage),
        ]),
        const SizedBox(height: 6),
        LayoutBuilder(builder: (context, c) {
          return Stack(children: [
            Container(height: 8, decoration: BoxDecoration(color: const Color(0xFFEEF0F4), borderRadius: BorderRadius.circular(99))),
            Container(
              height: 8,
              width: c.maxWidth * (pct.clamp(0, 100) / 100),
              decoration: BoxDecoration(color: shortage ? AppColors.bad : AppColors.ok, borderRadius: BorderRadius.circular(99)),
            ),
            Positioned(left: c.maxWidth * minimum / 100, child: Container(width: 2, height: 8, color: Colors.black87)),
          ]);
        }),
        const SizedBox(height: 6),
        Text(
          '${s['present']} of ${s['total']} classes · '
          '${shortage ? 'attend the next ${s['mustAttend']} to reach ${minimum.toInt()}%' : 'can miss ${s['canMiss']} more'}',
          style: const TextStyle(color: AppColors.muted, fontSize: 13),
        ),
      ]),
    );
  }
}
