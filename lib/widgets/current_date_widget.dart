import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:intl/intl.dart';

class CurrentDateWidget extends StatefulWidget {
  final Color fontColor;

  const CurrentDateWidget({super.key, required this.fontColor});

  @override
  State<CurrentDateWidget> createState() => _CurrentDateWidgetState();
}

class _CurrentDateWidgetState extends State<CurrentDateWidget>
    with TickerProviderStateMixin {
  late Ticker _ticker;
  late String _formattedTime;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker((_) => _updateTime());
    _ticker.start();
    _formattedTime = _getFormattedTime();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _updateTime() {
    setState(() {
      _formattedTime = _getFormattedTime();
    });
  }

  String _getFormattedTime() {
    var now = DateTime.now();
    var formatter = DateFormat('HH:mm');
    return formatter.format(now);
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Text(
          _formattedTime,
          style: TextStyle(
            color: widget.fontColor,
            fontWeight: FontWeight.bold,
            fontSize: 16.0,
          ),
        ),
      ],
    );
  }
}
