import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/widgets.dart';
import 'package:queue_management_system/models/queue_model.dart';

class QueuesProvider extends ChangeNotifier {
  QueueModel? selectedQueue;

  void increaseNumber() {
    if (selectedQueue != null) {
      if (selectedQueue!.number < 99) {
        selectedQueue!.number++;
      } else {
        selectedQueue!.number = 0;
      }
    }
    update();
    notifyListeners();
  }

  void decreaseNumber() {
    if (selectedQueue != null) {
      if (selectedQueue!.number > 0) {
        selectedQueue!.number--;
      } else {
        selectedQueue!.number = 99;
      }
    }
    update();
    notifyListeners();
  }


  void  update() async{
      // Get a reference to the document you want to update
      DocumentReference docRef = FirebaseFirestore.instance.collection('queues').doc(selectedQueue!.id);

      // Update the document data
      await docRef.update({
        'number': selectedQueue!.number,
      });
  }

}