import 'package:flutter/widgets.dart';

class QueueModel {
  int number;
  final IconData icon;
  final String queueName;
  final Color backgroundColor;
  final Color foregroundColor;
  final String id;

  final String videoURL;
  late int videoVolume;
  late int soundVolume;
  final int velocity;

  QueueModel(this.number, this.icon, this.queueName, this.backgroundColor,
      this.foregroundColor, this.id, this.videoURL, this.videoVolume, this.soundVolume, this.velocity);
}
