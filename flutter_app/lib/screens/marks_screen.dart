import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/common.dart';

/// IA marks entry for a class and assignment deadlines.
class MarksScreen extends StatefulWidget {
  const MarksScreen({super.key});
  @override
  State<MarksScreen> createState() => _MarksScreenState();
}

class _MarksScreenState extends State<MarksScreen> {
  final api = ApiClient.instance;
  final _sub = TextEditingController();
  final _dept = TextEditingController();
  final _sem = TextEditingController();
  final _sec = TextEditingController();
  final _ia = TextEditingController(text: '1');
  final _max = TextEditingController(text: '50');
  final _title = TextEditingController();
  DateTime? _deadline;

  List<dynamic> _students = [];
  final Map<String, TextEditingController> _marks = {};
  List<dynamic> _assignments = [];

  Map<String, String> get _cls => {
        'department': _dept.text.trim(),
        'semester': _sem.text.trim(),
        'section': _sec.text.trim(),
      };

  @override
  void initState() {
    super.initState();
    _loadAssignments();
  }

  Future<void> _loadClass() async {
    try {
      final roster = await api.get('/api/students', _cls);
      final existing = await api.get('/api/ia-marks', {'subject': _sub.text.trim(), 'iaNo': _ia.text.trim()});
      final byId = {for (final m in existing['marks'] as List) m['student_id'].toString(): m['marks']};
      setState(() {
        _students = roster['students'] as List;
        _marks
          ..clear()
          ..addAll({
            for (final s in _students)
              s['student_id'].toString(): TextEditingController(text: byId[s['student_id'].toString()]?.toString() ?? ''),
          });
      });
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    }
  }

  Future<void> _saveMarks() async {
    final entries = _marks.entries
        .where((e) => e.value.text.trim().isNotEmpty)
        .map((e) => {'studentId': e.key, 'marks': double.tryParse(e.value.text.trim())})
        .toList();
    try {
      final r = await api.post('/api/ia-marks', {
        'subject': _sub.text.trim(),
        'iaNo': int.tryParse(_ia.text.trim()) ?? 1,
        'maxMarks': double.tryParse(_max.text.trim()) ?? 50,
        'marks': entries,
      });
      final errors = (r['errors'] as List).join('; ');
      if (mounted) showMessage(context, 'Saved ${r['saved']}${errors.isEmpty ? '' : ' · $errors'}', error: errors.isNotEmpty);
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    }
  }

  Future<void> _loadAssignments() async {
    try {
      final r = await api.get('/api/assignments');
      setState(() => _assignments = r['assignments'] as List);
    } catch (_) {}
  }

  Future<void> _postDeadline() async {
    if (_deadline == null) {
      showMessage(context, 'Pick a deadline date', error: true);
      return;
    }
    final d = _deadline!;
    final date = '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    try {
      await api.post('/api/assignments', {..._cls, 'subject': _sub.text.trim(), 'title': _title.text.trim(), 'deadline': date});
      _title.clear();
      await _loadAssignments();
      if (mounted) showMessage(context, 'Deadline posted');
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    }
  }

  Widget _f(TextEditingController c, String label, {TextInputType? type}) =>
      Expanded(child: TextField(controller: c, keyboardType: type, decoration: InputDecoration(labelText: label)));

  @override
  Widget build(BuildContext context) {
    return ListView(padding: const EdgeInsets.all(16), children: [
      SectionCard(
        title: 'Class',
        child: Column(children: [
          Row(children: [_f(_sub, 'Subject'), const SizedBox(width: 8), _f(_ia, 'IA no.', type: TextInputType.number), const SizedBox(width: 8), _f(_max, 'Max', type: TextInputType.number)]),
          const SizedBox(height: 8),
          Row(children: [_f(_dept, 'Dept'), const SizedBox(width: 8), _f(_sem, 'Sem'), const SizedBox(width: 8), _f(_sec, 'Sec')]),
          const SizedBox(height: 12),
          SizedBox(width: double.infinity, child: OutlinedButton(onPressed: _loadClass, child: const Text('Load students'))),
        ]),
      ),
      const SizedBox(height: 12),
      SectionCard(
        title: 'IA marks',
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          if (_students.isEmpty) const EmptyNote('Load a class to enter marks.'),
          for (final s in _students)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(children: [
                Expanded(child: Text('${s['usn']}\n${s['name']}')),
                SizedBox(
                  width: 90,
                  child: TextField(
                    controller: _marks[s['student_id'].toString()],
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Marks'),
                  ),
                ),
              ]),
            ),
          if (_students.isNotEmpty) FilledButton(onPressed: _saveMarks, child: const Text('Save marks')),
        ]),
      ),
      const SizedBox(height: 12),
      SectionCard(
        title: 'Assignment deadline',
        subtitle: 'Uses the subject and class above. Students see it in their app.',
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          TextField(controller: _title, decoration: const InputDecoration(labelText: 'Title')),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            icon: const Icon(Icons.event),
            label: Text(_deadline == null ? 'Pick deadline' : '${_deadline!.day}/${_deadline!.month}/${_deadline!.year}'),
            onPressed: () async {
              final now = DateTime.now();
              final picked = await showDatePicker(context: context, firstDate: now, lastDate: now.add(const Duration(days: 180)), initialDate: now.add(const Duration(days: 7)));
              if (picked != null) setState(() => _deadline = picked);
            },
          ),
          const SizedBox(height: 8),
          FilledButton(onPressed: _postDeadline, child: const Text('Post deadline')),
          const SizedBox(height: 8),
          for (final a in _assignments)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.assignment_outlined),
              title: Text('${a['title']}'),
              subtitle: Text('${a['subject']} · ${a['department']}-${a['semester']}${a['section']} · due ${a['deadline']}'),
            ),
        ]),
      ),
    ]);
  }
}
