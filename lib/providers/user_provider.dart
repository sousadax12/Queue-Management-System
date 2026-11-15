import 'package:flutter/widgets.dart';
import 'package:queue_management_system/auth/operator_scope.dart';

/// The signed-in operator's scope (null when signed out).
class UserProvider extends ChangeNotifier {
  UserProvider([this._scope]);

  OperatorScope? _scope;

  OperatorScope? get scope => _scope;

  set scope(OperatorScope? value) {
    _scope = value;
    notifyListeners();
  }
}
