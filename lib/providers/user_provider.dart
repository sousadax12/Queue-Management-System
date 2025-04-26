import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/widgets.dart';
import 'package:queue_management_system/models/queue_model.dart';

class UserProvider extends ChangeNotifier {

  late UserCredential _userCredential;

  set userCredential(UserCredential value) {
    _userCredential = value;
    notifyListeners();
  }
}
