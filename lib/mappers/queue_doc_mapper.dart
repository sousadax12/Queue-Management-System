import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:queue_management_system/models/queue_model.dart';

class QueueDocMapper {
  static QueueModel docToQueue(DocumentSnapshot document) {
    return QueueModel(
        document['number'],
        IconData(document['icon'], fontFamily: 'FontAwesomeSolid', fontPackage: 'font_awesome_flutter'),
        document['name'],
        Color(document['backgroundColor']),
        Colors.white,
        document.id,
        document['videoURL'],
        document['videoVolume'],
        document['soundVolume'],
        document['velocity']);
  }
}
