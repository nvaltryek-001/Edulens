import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../theme.dart';
import '../widgets/common.dart';

/// Faculty flow: class details -> photos -> review -> confirm.
/// Recognition only suggests; nothing is saved until the teacher confirms.
class AttendanceScreen extends StatefulWidget {
  const AttendanceScreen({super.key});
  @override
  State<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _ReviewRow {
  _ReviewRow(this.data) : present = data['detected'] == true;
  final Map<String, dynamic> data;
  bool present;
  String get id => data['studentId'].toString();
  String get name => (data['name'] ?? '').toString();
  String get usn => (data['usn'] ?? data['studentId']).toString();
  bool get detected => data['detected'] == true;
  double? get confidence => (data['confidence'] as num?)?.toDouble();
}

class _AttendanceScreenState extends State<AttendanceScreen> {
  final api = ApiClient.instance;
  final _picker = ImagePicker();

  final _subject = TextEditingController();
  final _faculty = TextEditingController();
  final _dept = TextEditingController();
  final _sem = TextEditingController();
  final _sec = TextEditingController();
  final _reason = TextEditingController();

  Map<String, dynamic>? _session;
  List<_ReviewRow> _rows = [];
  final List<String> _photoLog = [];
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _faculty.text = api.username ?? '';
    _resume();
  }

  Map<String, String> get _classInfo => {
        'subject': _subject.text.trim(),
        'faculty': _faculty.text.trim(),
        'department': _dept.text.trim(),
        'semester': _sem.text.trim(),
        'section': _sec.text.trim(),
      };

  bool get _active => _session != null && _session!['status'] == 'ACTIVE';

  Future<void> _resume() async {
    final id = await api.rememberedSession();
    if (id == null) return;
    try {
      final r = await api.review(id);
      final s = r['session'] as Map<String, dynamic>?;
      if (s != null && s['status'] == 'ACTIVE') {
        _applySession(s);
        _applyReview(r);
      } else {
        await api.rememberSession(null);
      }
    } catch (_) {
      await api.rememberSession(null);
    }
  }

  void _applySession(Map<String, dynamic> s) {
    setState(() {
      _session = s;
      _subject.text = s['subject'].toString();
      _faculty.text = s['faculty'].toString();
      _dept.text = s['department'].toString();
      _sem.text = s['semester'].toString();
      _sec.text = s['section'].toString();
    });
  }

  void _applyReview(Map<String, dynamic> r) {
    setState(() {
      _rows = (r['students'] as List).map((e) => _ReviewRow(e as Map<String, dynamic>)).toList();
    });
  }

  Future<void> _run(Future<void> Function() task) async {
    setState(() => _busy = true);
    try {
      await task();
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _start() => _run(() async {
        final missing = _classInfo.entries.where((e) => e.value.isEmpty).map((e) => e.key).toList();
        if (missing.isNotEmpty) throw ApiException('Fill in: ${missing.join(', ')}');
        final r = await api.startSession(_classInfo);
        final s = r['session'] as Map<String, dynamic>;
        await api.rememberSession(s['sessionId'] as int);
        _photoLog.clear();
        _applySession(s);
        _applyReview(await api.review(s['sessionId'] as int));
      });

  Future<void> _addPhoto(ImageSource source) => _run(() async {
        final List<XFile> picked;
        if (source == ImageSource.camera) {
          final one = await _picker.pickImage(source: source, imageQuality: 90, maxWidth: 2560);
          picked = one == null ? [] : [one];
        } else {
          picked = await _picker.pickMultiImage(imageQuality: 90, maxWidth: 2560);
        }
        for (final photo in picked) {
          final r = await api.recognize(File(photo.path), _classInfo);
          setState(() => _photoLog.add(
              'Photo ${_photoLog.length + 1}: ${(r['students'] as List).length} recognised, '
              '${r['candidatesSoFar']}/${r['rosterSize']} detected so far'));
        }
        _applyReview(await api.review(_session!['sessionId'] as int));
      });

  Future<void> _confirm() async {
    final present = _rows.where((r) => r.present).length;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Save and lock?'),
        content: Text('${_session!['subject']} · ${_session!['department']}-${_session!['semester']}${_session!['section']}\n\n'
            'Present: $present   Absent: ${_rows.length - present}   Total: ${_rows.length}\n\n'
            'Parents of absent students will be notified.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Back to review')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true) return;
    await _run(() async {
      final r = await api.confirm(
        _session!['sessionId'] as int,
        _rows.where((e) => e.present).map((e) => e.id).toList(),
        _reason.text.trim(),
      );
      await api.rememberSession(null);
      _reason.clear();
      setState(() {
        _session = null;
        _rows = [];
        _photoLog.clear();
      });
      if (mounted) showMessage(context, r['message'].toString());
    });
  }

  Widget _field(TextEditingController c, String label, {String? hint}) => Expanded(
        child: TextField(
          controller: c,
          enabled: !_active,
          decoration: InputDecoration(labelText: label, hintText: hint),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final present = _rows.where((r) => r.present).length;
    final detected = _rows.where((r) => r.detected).length;

    return RefreshIndicator(
      onRefresh: () async {
        if (_active) _applyReview(await api.review(_session!['sessionId'] as int));
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            title: 'Class details',
            subtitle: 'Recognition only suggests. Nothing is saved until you confirm.',
            child: Column(children: [
              Row(children: [_field(_subject, 'Subject', hint: 'BCS701'), const SizedBox(width: 10), _field(_faculty, 'Faculty')]),
              const SizedBox(height: 10),
              Row(children: [
                _field(_dept, 'Dept', hint: 'CSD'),
                const SizedBox(width: 10),
                _field(_sem, 'Sem', hint: '7'),
                const SizedBox(width: 10),
                _field(_sec, 'Section', hint: 'A'),
              ]),
              const SizedBox(height: 12),
              if (!_active)
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: _busy ? null : _start,
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('Start session'),
                  ),
                )
              else
                Row(children: [
                  const Icon(Icons.circle, size: 10, color: AppColors.ok),
                  const SizedBox(width: 8),
                  Expanded(child: Text('Session #${_session!['sessionId']} active')),
                ]),
            ]),
          ),
          if (_active) ...[
            const SizedBox(height: 12),
            SectionCard(
              title: 'Classroom photos',
              subtitle: 'Take 1–4 photos: left, centre, right and back rows.',
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: _busy ? null : () => _addPhoto(ImageSource.camera),
                      icon: const Icon(Icons.photo_camera),
                      label: const Text('Camera'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _busy ? null : () => _addPhoto(ImageSource.gallery),
                      icon: const Icon(Icons.photo_library_outlined),
                      label: const Text('Gallery'),
                    ),
                  ),
                ]),
                if (_busy) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
                for (final line in _photoLog)
                  Padding(padding: const EdgeInsets.only(top: 8), child: Text(line, style: const TextStyle(color: AppColors.muted))),
              ]),
            ),
            const SizedBox(height: 12),
            SectionCard(
              title: 'Review and confirm',
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                GridView.count(
                  crossAxisCount: 2,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  childAspectRatio: 2.6,
                  mainAxisSpacing: 8,
                  crossAxisSpacing: 8,
                  children: [
                    StatTile(value: '${_rows.length}', label: 'On roster'),
                    StatTile(value: '$detected', label: 'Detected by camera'),
                    StatTile(value: '$present', label: 'Marked present', color: AppColors.ok),
                    StatTile(value: '${_rows.length - present}', label: 'Will be absent', color: AppColors.bad),
                  ],
                ),
                const SizedBox(height: 8),
                Row(children: [
                  TextButton(onPressed: () => setState(() => _rows.forEach((r) => r.present = true)), child: const Text('All present')),
                  TextButton(onPressed: () => setState(() => _rows.forEach((r) => r.present = false)), child: const Text('All absent')),
                ]),
                if (_rows.isEmpty) const EmptyNote('No students on this roster. Ask the admin to import it.'),
                for (final r in _rows)
                  CheckboxListTile(
                    value: r.present,
                    onChanged: (v) => setState(() => r.present = v ?? false),
                    contentPadding: EdgeInsets.zero,
                    title: Text(r.name),
                    subtitle: Text(r.usn),
                    secondary: r.detected
                        ? Pill(r.confidence == null ? 'camera' : r.confidence!.toStringAsFixed(2))
                        : const Pill('not seen', good: false, warning: true),
                  ),
                const SizedBox(height: 8),
                TextField(
                  controller: _reason,
                  decoration: const InputDecoration(labelText: 'Reason for manual changes (optional)'),
                ),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _busy || _rows.isEmpty ? null : _confirm,
                  icon: const Icon(Icons.check),
                  label: const Text('Confirm and save'),
                ),
              ]),
            ),
          ],
        ],
      ),
    );
  }
}
