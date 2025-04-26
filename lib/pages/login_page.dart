import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';

class LoginScreen extends StatefulWidget {
  @override
  _LoginScreenState createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  late String _email, _password;
  bool _isLoading = false;
  User? user = FirebaseAuth.instance.currentUser;

  FocusNode? myFocusNode1;
  FocusNode? myFocusNode2;
  FocusNode? submitFocusNode;



  @override
  void initState() {
    super.initState();
    myFocusNode1 = FocusNode();
    myFocusNode2 = FocusNode();
    submitFocusNode = FocusNode();

    // To ensure that the first text field gets the initial focus.
    myFocusNode1!.requestFocus();
  }


  @override
  void dispose() {
    myFocusNode1!.dispose();
    myFocusNode2!.dispose();
    submitFocusNode!.dispose();

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      appBar: AppBar(
        title: Text('Login'),
      ),
      body: _isLoading
          ? Padding(
            padding: const EdgeInsets.all(8.0),
            child: Center(
                child: CircularProgressIndicator(),
              ),
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
                        FocusScope.of(context).requestFocus(myFocusNode2);
                      },
                      validator: (input) {
                        if (input == null || input.isEmpty) {
                          return 'Please enter an email';
                        }
                        return null;
                      },
                      onSaved: (input) => _email = input!,
                      autofocus: true,
                      decoration: InputDecoration(
                        labelText: 'Email',
                      ),
                    ),
                    TextFormField(
                      onFieldSubmitted: (_) {
                        FocusScope.of(context).requestFocus(submitFocusNode);
                      },
                      validator: (input) {
                        if (input == null || input.isEmpty) {
                          return 'Please enter a password';
                        }
                        return null;
                      },
                      onSaved: (input) => _password = input!,
                      obscureText: true,
                      decoration: InputDecoration(
                        labelText: 'Password',
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () {
                        _login();
                      },
                      child: Text('Login'),
                    ),
                  ],
                ),
              ),
          ),
    );
  }

  void _login() async {
    if (_formKey.currentState!.validate()) {
      _formKey.currentState!.save();
      setState(() {
        _isLoading = true;
      });
      try {
        UserCredential userCredential = await _auth.signInWithEmailAndPassword(
          email: _email,
          password: _password,
        );
        if (userCredential.user != null) {
          Navigator.popAndPushNamed(context, '/setup');
        } else {
          showDialog(
            context: context,
            builder: (BuildContext context) {
              return AlertDialog(
                title: Text('Email not verified'),
                content:
                    Text('Please check your email and verify your account.'),
                actions: [
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        _isLoading = false;
                      });
                      return Navigator.pop(context);
                    },
                    child: Text('OK'),
                  ),
                ],
              );
            },
          );
        }
      } on FirebaseAuthException catch (e) {
        setState(() {
          _isLoading = false;
        });
        if (e.code == 'user-not-found') {
          showDialog(
            context: context,
            builder: (BuildContext context) {
              return AlertDialog(
                title: Text('Error'),
                content: Text('No user found for that email.'),
                actions: [
                  ElevatedButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text('OK'),
                  ),
                ],
              );
            },
          );
        } else if (e.code == 'wrong-password') {
          showDialog(
            context: context,
            builder: (BuildContext context) {
              return AlertDialog(
                title: Text('Error'),
                content: Text('Wrong password provided for that user.'),
                actions: [
                  ElevatedButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text('OK'),
                  ),
                ],
              );
            },
          );
        }
      }
    }
  }
}
