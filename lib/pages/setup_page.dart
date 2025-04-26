import 'dart:math';

import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/services.dart';
import 'package:flutter_keyboard_visibility/flutter_keyboard_visibility.dart';
import 'package:provider/provider.dart';
import 'package:queue_management_system/mappers/queue_doc_mapper.dart';
import 'package:queue_management_system/models/queue_model.dart';
import 'package:queue_management_system/providers/queues_provider.dart';
import 'package:queue_management_system/widgets/queue_widget.dart';

class SetupPage extends StatefulWidget {
  @override
  _SetupPageState createState() => _SetupPageState();
}

class _SetupPageState extends State<SetupPage>
    with AutomaticKeepAliveClientMixin<SetupPage> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  int _selectedIndex = 0;

  List<QueueModel>? _queues;

  @override
  Widget build(BuildContext context) {
    CollectionReference queueCollection = _firestore.collection('queues');
    var queuesProvider = Provider.of<QueuesProvider>(context, listen: false);

    if (queuesProvider.selectedQueue != null) {
      Navigator.pushReplacementNamed(context, '/home');
    }

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("Select a queue: "),
      ),
      body: RawKeyboardListener(
        focusNode: FocusNode(),
        onKey: (event) {
          debugPrint(event.logicalKey.debugName);
          debugPrint("$_selectedIndex");
          if (event.runtimeType == RawKeyUpEvent && event.logicalKey == LogicalKeyboardKey.arrowLeft &&
              _queues != null) {
            if (_selectedIndex > 0) {
              setState(() {
                _selectedIndex = --_selectedIndex;
              });
            }
          }

          if (event.runtimeType == RawKeyUpEvent && event.logicalKey == LogicalKeyboardKey.arrowRight &&
              _queues != null) {
            if (_selectedIndex < (_queues!.length-1)) {
              setState(() {
                _selectedIndex = ++_selectedIndex;
              });
            }
          }
          if (event.runtimeType == RawKeyUpEvent && event.logicalKey == LogicalKeyboardKey.select &&
              _queues != null) {
            queuesProvider.selectedQueue = _queues![_selectedIndex];
            Navigator.pushReplacementNamed(context, '/home');
          }
        },
        child: FutureBuilder<QuerySnapshot>(
          future: queueCollection.get(),
          builder:
              (BuildContext context, AsyncSnapshot<QuerySnapshot> snapshot) {
            if (snapshot.hasError) {
              return Text('Error: ${snapshot.error}');
            }
            if (snapshot.connectionState == ConnectionState.waiting) {
              return Center(
                child: CircularProgressIndicator(),
              );
            }
            if (snapshot.data!.docs.isEmpty) {
              return Center(
                child: Text('No documents found'),
              );
            }
            _queues = snapshot.data!.docs
                .map((d) => QueueDocMapper.docToQueue(d))
                .toList();

            return Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: _queues!
                  .asMap()
                  .entries
                  .map((q) => Focus(
                        onFocusChange: (hasFocus) {
                          setState(() {
                            _selectedIndex = hasFocus ? q.key : -1;
                          });
                        },
                        child: GestureDetector(
                          onTap: () {
                            queuesProvider.selectedQueue = q.value;
                            Navigator.pushReplacementNamed(context, '/home');
                          },
                          child: Container(
                            width: 300,
                            decoration: BoxDecoration(
                              color: _selectedIndex == q.key
                                  ? Colors.greenAccent
                                  : Colors.transparent,
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(
                                color: _selectedIndex == q.key
                                    ? Colors.greenAccent
                                    : Colors.grey,
                              ),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(8.0),
                              child: QueueWidget(queue: q.value),
                            ),
                            margin: EdgeInsets.only(top: 50, bottom: 50),
                          ),
                        ),
                      ))
                  .toList(),
            );
          },
        ),
      ),
    );
  }

  @override
  // TODO: implement wantKeepAlive
  bool get wantKeepAlive => true;
}
