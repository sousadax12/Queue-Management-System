import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:queue_management_system/models/queue_model.dart';

class QueueWidget extends StatelessWidget {
  final QueueModel queue;

  const QueueWidget({required this.queue});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: queue.backgroundColor,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Center(
            child: Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                '${queue.queueName}',
                style: TextStyle(
                  fontSize: 45.0,
                  fontWeight: FontWeight.bold,
                  color: queue.foregroundColor,
                ),
              ),
            ),
          ),
          const SizedBox(height: 10.0),
          Center(
            child: Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                '${queue.number}',
                style: TextStyle(
                  fontSize: 70.0,
                  fontWeight: FontWeight.bold,
                  color: queue.foregroundColor,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
