import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// Log of every SMS / e-mail sent to parents.
class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});
  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final api = ApiClient.instance;
  Map<String, dynamic>? _status;
  List<dynamic> _rows = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final st = await api.get('/api/notifications/parents/status');
      final r = await api.get('/api/notifications/parents');
      setState(() {
        _status = st;
        _rows = r['notifications'] as List;
      });
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = _status?['smsProvider'];
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(padding: const EdgeInsets.all(16), children: [
        SectionCard(
          title: 'Parent alerts',
          subtitle: provider == null
              ? 'Loading…'
              : 'SMS: $provider${provider == 'log' ? ' (demo mode, recorded but not sent)' : ''} · '
                  'E-mail: ${_status?['emailEnabled'] == true ? 'on' : 'off'}',
          child: const Text('Absence alerts are sent automatically when you confirm a session. '
              'Shortage alerts are sent from the Shortage tab or weekly by the server.'),
        ),
        const SizedBox(height: 12),
        if (_rows.isEmpty) const EmptyNote('No parent alerts yet.'),
        for (final n in _rows)
          Card(
            margin: const EdgeInsets.only(bottom: 8),
            child: ListTile(
              leading: Icon(n['channel'] == 'EMAIL' ? Icons.email_outlined : Icons.sms_outlined, color: AppColors.brand),
              title: Text('${n['student_id']} · ${n['kind']}'),
              subtitle: Text('${n['message']}\n${n['recipient']} · ${(n['created_at'] ?? '').toString()}'),
              isThreeLine: true,
              trailing: Pill('${n['status']}', good: n['status'] == 'SENT'),
            ),
          ),
      ]),
    );
  }
}
