import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// Students below the VTU minimum in each subject, with parent alerts.
class ShortageScreen extends StatefulWidget {
  const ShortageScreen({super.key});
  @override
  State<ShortageScreen> createState() => _ShortageScreenState();
}

class _ShortageScreenState extends State<ShortageScreen> {
  final api = ApiClient.instance;
  final _dept = TextEditingController();
  final _sem = TextEditingController();
  final _sec = TextEditingController();
  final _sub = TextEditingController();
  List<dynamic> _rows = [];
  bool _loading = false;

  Map<String, String> get _filters => {
        'department': _dept.text.trim(),
        'semester': _sem.text.trim(),
        'section': _sec.text.trim(),
        'subject': _sub.text.trim(),
      };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final r = await api.get('/api/shortage', {..._filters, 'threshold': '$minimumAttendance'});
      setState(() => _rows = r['students'] as List);
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _sendAlerts() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Alert parents?'),
        content: const Text('Send an SMS/e-mail to parents of every student listed. Each parent is alerted at most once per subject per week.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Send')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      final body = Map<String, dynamic>.from(_filters)..removeWhere((_, v) => v.toString().isEmpty);
      final r = await api.post('/api/notifications/parents/shortage', body);
      if (mounted) showMessage(context, 'Alerts sent: ${r['sent']} · failed: ${r['failed']}');
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    }
  }

  Widget _f(TextEditingController c, String label) =>
      Expanded(child: TextField(controller: c, decoration: InputDecoration(labelText: label)));

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(padding: const EdgeInsets.all(16), children: [
        SectionCard(
          title: 'Below ${minimumAttendance.toInt()}% (VTU, per subject)',
          subtitle: 'Leave filters empty to see all classes.',
          child: Column(children: [
            Row(children: [_f(_dept, 'Dept'), const SizedBox(width: 8), _f(_sem, 'Sem'), const SizedBox(width: 8), _f(_sec, 'Sec')]),
            const SizedBox(height: 8),
            Row(children: [_f(_sub, 'Subject')]),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: OutlinedButton(onPressed: _load, child: const Text('Show list'))),
              const SizedBox(width: 10),
              Expanded(child: FilledButton.icon(onPressed: _rows.isEmpty ? null : _sendAlerts, icon: const Icon(Icons.sms), label: const Text('Alert parents'))),
            ]),
          ]),
        ),
        const SizedBox(height: 12),
        if (_loading) const LinearProgressIndicator(),
        if (!_loading && _rows.isEmpty) const EmptyNote('No students below the minimum.'),
        for (final s in _rows)
          Card(
            margin: const EdgeInsets.only(bottom: 8),
            child: ListTile(
              title: Text('${s['name']}'),
              subtitle: Text('${s['usn']} · ${s['subject']} · ${s['present']}/${s['total']} classes'
                  '\nNeeds ${s['mustAttend']} more in a row${s['parentPhoneOnFile'] == true ? '' : ' · no parent mobile'}'),
              isThreeLine: true,
              trailing: Pill('${s['percent']}%', good: false, warning: (s['percent'] as num) >= 75),
            ),
          ),
        const SizedBox(height: 8),
        const Text('Percentages count confirmed sessions only.', style: TextStyle(color: AppColors.muted, fontSize: 12)),
      ]),
    );
  }
}
