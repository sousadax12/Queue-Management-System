import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:queue_management_system/auth/operator_scope.dart';
import 'package:queue_management_system/providers/user_provider.dart';

/// Operator sign-in with an email/password account. The account needs an `operator` role
/// (or a staff role) for its sites, granted from the admin portal.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  String _email = '';
  String _password = '';
  bool _isLoading = false;

  final FocusNode _passwordFocus = FocusNode();
  final FocusNode _submitFocus = FocusNode();

  @override
  void dispose() {
    _passwordFocus.dispose();
    _submitFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text('Operator login'),
      ),
      body: _isLoading
          ? const Padding(
              padding: EdgeInsets.all(8.0),
              child: Center(child: CircularProgressIndicator()),
            )
          : Padding(
              padding: const EdgeInsets.all(8.0),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TextFormField(
                      onFieldSubmitted: (_) {
                        FocusScope.of(context).requestFocus(_passwordFocus);
                      },
                      validator: (input) {
                        if (input == null || input.trim().isEmpty) {
                          return 'Please enter an email';
                        }
                        return null;
                      },
                      onSaved: (input) => _email = input!.trim(),
                      autofocus: true,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      decoration: const InputDecoration(labelText: 'Email'),
                    ),
                    TextFormField(
                      focusNode: _passwordFocus,
                      onFieldSubmitted: (_) => _login(),
                      validator: (input) {
                        if (input == null || input.isEmpty) {
                          return 'Please enter a password';
                        }
                        return null;
                      },
                      onSaved: (input) => _password = input!,
                      obscureText: true,
                      autofillHints: const [AutofillHints.password],
                      decoration: const InputDecoration(labelText: 'Password'),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      focusNode: _submitFocus,
                      onPressed: _login,
                      child: const Text('Login'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Future<void> _login() async {
    final form = _formKey.currentState;
    if (form == null || !form.validate()) return;
    form.save();
    setState(() => _isLoading = true);

    final auth = FirebaseAuth.instance;
    try {
      await auth.signInWithEmailAndPassword(email: _email, password: _password);
      // Force a fresh token so claims granted since the last sign-in are seen.
      final scope = await loadOperatorScope(auth, forceRefresh: true);
      if (!mounted) return;
      if (scope == null) {
        setState(() => _isLoading = false);
        await _showError(
          'No operator access',
          'This account has no operator role for any site. Ask your organisation admin '
              'to grant it in the admin portal.',
        );
        return;
      }
      Provider.of<UserProvider>(context, listen: false).scope = scope;
      Navigator.pushReplacementNamed(context, '/setup');
    } on FirebaseAuthException catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      await _showError('Login failed', _messageFor(e));
    }
  }

  String _messageFor(FirebaseAuthException e) {
    switch (e.code) {
      case 'invalid-credential':
      case 'user-not-found':
      case 'wrong-password':
        return 'Wrong email or password.';
      case 'invalid-email':
        return 'That is not a valid email address.';
      case 'user-disabled':
        return 'This account has been disabled.';
      case 'too-many-requests':
        return 'Too many attempts. Try again later.';
      case 'network-request-failed':
        return 'No network connection.';
      default:
        return e.message ?? e.code;
    }
  }

  Future<void> _showError(String title, String message) {
    return showDialog<void>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            ElevatedButton(
              autofocus: true,
              onPressed: () => Navigator.pop(context),
              child: const Text('OK'),
            ),
          ],
        );
      },
    );
  }
}
