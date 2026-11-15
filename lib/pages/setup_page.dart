import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:queue_management_system/mappers/queue_doc_mapper.dart';
import 'package:queue_management_system/models/queue_model.dart';
import 'package:queue_management_system/providers/queues_provider.dart';
import 'package:queue_management_system/providers/user_provider.dart';
import 'package:queue_management_system/widgets/queue_widget.dart';

class SetupPage extends StatefulWidget {
  const SetupPage({super.key});

  @override
  State<SetupPage> createState() => _SetupPageState();
}

class _SetupPageState extends State<SetupPage>
    with AutomaticKeepAliveClientMixin<SetupPage> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  int _selectedIndex = 0;

  List<QueueModel>? _queues;

  Future<void> _signOut() async {
    final queuesProvider = Provider.of<QueuesProvider>(context, listen: false);
    final userProvider = Provider.of<UserProvider>(context, listen: false);
    await FirebaseAuth.instance.signOut();
    queuesProvider.selectedQueue = null;
    userProvider.scope = null;
    if (!mounted) return;
    Navigator.pushNamedAndRemoveUntil(context, '/', (_) => false);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    var queuesProvider = Provider.of<QueuesProvider>(context, listen: false);
    final scope = Provider.of<UserProvider>(context, listen: false).scope;

    if (queuesProvider.selectedQueue != null) {
      Navigator.pushReplacementNamed(context, '/home');
    }

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("Select a queue: "),
        actions: [
          IconButton(
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
            onPressed: _signOut,
          ),
        ],
      ),
      body: KeyboardListener(
        focusNode: FocusNode(),
        onKeyEvent: (event) {
          debugPrint(event.logicalKey.debugName);
          debugPrint("$_selectedIndex");
          if (event is KeyUpEvent && event.logicalKey == LogicalKeyboardKey.arrowLeft &&
              _queues != null) {
            if (_selectedIndex > 0) {
              setState(() {
                _selectedIndex = --_selectedIndex;
              });
            }
          }

          if (event is KeyUpEvent && event.logicalKey == LogicalKeyboardKey.arrowRight &&
              _queues != null) {
            if (_selectedIndex < (_queues!.length-1)) {
              setState(() {
                _selectedIndex = ++_selectedIndex;
              });
            }
          }
          if (event is KeyUpEvent && event.logicalKey == LogicalKeyboardKey.select &&
              _queues != null) {
            queuesProvider.selectedQueue = _queues![_selectedIndex];
            Navigator.pushReplacementNamed(context, '/home');
          }
        },
        child: FutureBuilder<QuerySnapshot>(
          // Only the queues this operator may advance; the rules reject anything wider.
          future: scope?.queuesQuery(_firestore).get(),
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
                            margin: const EdgeInsets.only(top: 50, bottom: 50),
                            child: Padding(
                              padding: const EdgeInsets.all(8.0),
                              child: QueueWidget(queue: q.value),
                            ),
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
  bool get wantKeepAlive => true;
}
