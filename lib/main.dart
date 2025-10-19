import 'dart:developer';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_spinkit/flutter_spinkit.dart';
import 'package:provider/provider.dart';
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

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      builder: (BuildContext context, AsyncSnapshot<dynamic> snapshot) {
// Check for errors
        if (snapshot.hasError) {
          return MaterialApp(
            home: Scaffold(
              body: Center(
                child: Text("Error: ${snapshot.error}"),
              ),
            ),
          );
        }

        // Once complete, show your application
        if (snapshot.connectionState == ConnectionState.done) {

          User? user = FirebaseAuth.instance.currentUser;
          return MultiProvider(
            providers: [
              ListenableProvider(create: (_) => QueuesProvider()),
              ListenableProvider(create: (_) => UserProvider())
            ],
            child: MaterialApp(
              title: 'Flutter Demo',
              theme: ThemeData(
                // This is the theme of your application.
                //
                // Try running your application with "flutter run". You'll see the
                // application has a blue toolbar. Then, without quitting the app, try
                // changing the primarySwatch below to Colors.green and then invoke
                // "hot reload" (press "r" in the console where you ran "flutter run",
                // or simply save your changes to "hot reload" in a Flutter IDE).
                // Notice that the counter didn't reset back to zero; the application
                // is not restarted.
                primarySwatch: Colors.blue,
              ),
              initialRoute: () {
                return "/setup";
              }(),
              routes: {
                '/': (context) => LoginScreen(),
                '/setup': (context) => SetupPage(),
                '/home': (context) =>
                    const HomePage(),
              },
            ),
          );
        }
        log(snapshot.toString());

        // Otherwise, show something whilst waiting for initialization to complete
        return SpinKitRotatingCircle(
          color: Colors.white,
          size: 50.0,
        );
      },
      future: loadFirebase(),
    );
  }

  loadFirebase() async {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
    UserCredential userCredential = await FirebaseAuth.instance.signInAnonymously();
  }
}
