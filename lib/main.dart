import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_spinkit/flutter_spinkit.dart';
import 'package:provider/provider.dart';
import 'package:queue_management_system/auth/operator_scope.dart';
import 'package:queue_management_system/pages/home_page.dart';
import 'package:queue_management_system/pages/login_page.dart';
import 'package:queue_management_system/pages/setup_page.dart';
import 'package:queue_management_system/providers/queues_provider.dart';
import 'package:queue_management_system/providers/user_provider.dart';
import 'firebase_options.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late final Future<OperatorScope?> _startup = _loadFirebase();

  /// Initialise Firebase and restore the operator session, if any. Operators sign in with
  /// an email/password account that has an `operator` claim (issue #6); there is no
  /// anonymous sign-in any more.
  Future<OperatorScope?> _loadFirebase() async {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
    return loadOperatorScope(FirebaseAuth.instance);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<OperatorScope?>(
      future: _startup,
      builder: (BuildContext context, AsyncSnapshot<OperatorScope?> snapshot) {
        if (snapshot.hasError) {
          return MaterialApp(
            home: Scaffold(
              body: Center(
                child: Text("Error: ${snapshot.error}"),
              ),
            ),
          );
        }

        if (snapshot.connectionState == ConnectionState.done) {
          final scope = snapshot.data;
          return MultiProvider(
            providers: [
              ListenableProvider(create: (_) => QueuesProvider()),
              ListenableProvider(create: (_) => UserProvider(scope)),
            ],
            child: MaterialApp(
              title: 'Queue Management System',
              theme: ThemeData(primarySwatch: Colors.blue),
              initialRoute: scope == null ? '/' : '/setup',
              routes: {
                '/': (context) => const LoginScreen(),
                '/setup': (context) => const SetupPage(),
                '/home': (context) => const HomePage(),
              },
            ),
          );
        }

        return const SpinKitRotatingCircle(
          color: Colors.white,
          size: 50.0,
        );
      },
    );
  }
}
