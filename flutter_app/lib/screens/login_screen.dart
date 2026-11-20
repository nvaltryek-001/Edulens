import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.onLoggedIn});
  final VoidCallback onLoggedIn;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _busy = false;
  bool _hide = true;
  String? _error;

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ApiClient.instance.login(_user.text.trim(), _pass.text);
      widget.onLoggedIn();
    } catch (e) {
      setState(() => _error = e.toString().contains('SocketException')
          ? 'Cannot reach the EduLens server at $apiBaseUrl. Check Wi-Fi.'
          : e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Image.asset('assets/svit_logo.jpeg', height: 88),
                  const SizedBox(height: 12),
                  Text('EduLens',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700)),
                  const Text('Sai Vidya Institute of Technology\nOne photo · Verified attendance',
                      textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted)),
                  const SizedBox(height: 32),
                  TextField(
                    controller: _user,
                    decoration: const InputDecoration(labelText: 'Username, USN or college e-mail', prefixIcon: Icon(Icons.person_outline)),
                    textInputAction: TextInputAction.next,
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _pass,
                    obscureText: _hide,
                    onSubmitted: (_) => _submit(),
                    decoration: InputDecoration(
                      labelText: 'Password',
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        icon: Icon(_hide ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                        onPressed: () => setState(() => _hide = !_hide),
                      ),
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(_error!, style: const TextStyle(color: AppColors.bad)),
                  ],
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Text('Sign in'),
                  ),
                  const SizedBox(height: 16),
                  const Text('Accounts are created by the SVIT EduLens administrator.',
                      textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted, fontSize: 12)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
