import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../config.dart';

class ApiException implements Exception {
  ApiException(this.message, [this.statusCode]);
  final String message;
  final int? statusCode;
  @override
  String toString() => message;
}

/// Thin client for the EduLens Flask API.
class ApiClient {
  ApiClient._();
  static final ApiClient instance = ApiClient._();

  String? token;
  String? role;
  String? username;

  bool get isStaff =>
      const ['administrator', 'hod', 'teacher', 'faculty'].contains(role);
  bool get isAdmin => const ['administrator', 'hod'].contains(role);

  Uri _uri(String path, [Map<String, String>? query]) {
    final base = apiBaseUrl.endsWith('/')
        ? apiBaseUrl.substring(0, apiBaseUrl.length - 1)
        : apiBaseUrl;
    final q = query == null
        ? null
        : (Map<String, String>.from(query)
          ..removeWhere((_, v) => v.trim().isEmpty));
    return Uri.parse('$base$path').replace(queryParameters: q);
  }

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  Map<String, dynamic> _decode(http.Response res) {
    Map<String, dynamic> body = {};
    try {
      final decoded = jsonDecode(utf8.decode(res.bodyBytes));
      if (decoded is Map<String, dynamic>) body = decoded;
    } catch (_) {}
    if (res.statusCode == 401) {
      throw ApiException('Session expired. Please sign in again.', 401);
    }
    if (res.statusCode >= 400 || body['success'] == false) {
      throw ApiException(
        (body['error'] ?? body['message'] ?? 'Request failed (${res.statusCode})')
            .toString(),
        res.statusCode,
      );
    }
    return body;
  }

  Future<Map<String, dynamic>> get(String path,
      [Map<String, String>? query]) async {
    final res = await http
        .get(_uri(path, query), headers: _headers)
        .timeout(const Duration(seconds: 30));
    return _decode(res);
  }

  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body,
      {String method = 'POST'}) async {
    final req = http.Request(method, _uri(path))
      ..headers.addAll(_headers)
      ..body = jsonEncode(body);
    final streamed = await req.send().timeout(const Duration(seconds: 60));
    return _decode(await http.Response.fromStream(streamed));
  }

  // ---------------------------------------------------------------- auth

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString('token');
    role = prefs.getString('role');
    username = prefs.getString('username');
  }

  Future<void> login(String user, String password) async {
    final res = await http
        .post(_uri('/login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'username': user, 'password': password}))
        .timeout(const Duration(seconds: 20));
    final body = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    if (res.statusCode != 200 || body['token'] == null) {
      throw ApiException(
          (body['message'] ?? 'Invalid username or password').toString());
    }
    token = body['token'] as String;
    role = body['role'] as String;
    username = (body['username'] ?? user).toString();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('token', token!);
    await prefs.setString('role', role!);
    await prefs.setString('username', username!);
  }

  Future<void> logout() async {
    token = role = username = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('token');
    await prefs.remove('role');
    await prefs.remove('username');
    await prefs.remove('session_id');
  }

  // ---------------------------------------------------------------- sessions

  Future<Map<String, dynamic>> startSession(Map<String, String> classInfo) =>
      post('/api/sessions/start', classInfo);

  /// Uploads one classroom photo. Returns candidates; nothing is saved yet.
  Future<Map<String, dynamic>> recognize(
      File photo, Map<String, String> classInfo) async {
    final req = http.MultipartRequest('POST', _uri('/api/recognize'))
      ..headers['Authorization'] = 'Bearer $token'
      ..fields.addAll(classInfo)
      ..files.add(await http.MultipartFile.fromPath('image', photo.path,
          filename: 'classroom.jpg'));
    final streamed = await req.send().timeout(const Duration(seconds: 120));
    return _decode(await http.Response.fromStream(streamed));
  }

  Future<Map<String, dynamic>> review(int sessionId) =>
      get('/api/sessions/$sessionId/review');

  Future<Map<String, dynamic>> confirm(
          int sessionId, List<String> presentIds, String reason) =>
      post('/api/attendance/confirm', {
        'sessionId': sessionId,
        'presentStudentIds': presentIds,
        'manualReason': reason,
      });

  Future<void> rememberSession(int? id) async {
    final prefs = await SharedPreferences.getInstance();
    if (id == null) {
      await prefs.remove('session_id');
    } else {
      await prefs.setInt('session_id', id);
    }
  }

  Future<int?> rememberedSession() async =>
      (await SharedPreferences.getInstance()).getInt('session_id');
}
