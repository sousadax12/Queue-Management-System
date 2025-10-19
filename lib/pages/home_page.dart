import 'package:audioplayers/audioplayers.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:queue_management_system/mappers/queue_doc_mapper.dart';
import 'package:queue_management_system/models/queue_model.dart';
import 'package:queue_management_system/providers/queues_provider.dart';
import 'package:queue_management_system/widgets/current_date_widget.dart';
import 'package:queue_management_system/widgets/queue_widget.dart';
import 'package:queue_management_system/widgets/rss_feed_widget.dart';
import 'package:queue_management_system/widgets/video_player_widget.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  late FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    var queuesProvider = Provider.of<QueuesProvider>(context, listen: false);

    return Scaffold(
        body: Stack(
      children: [
        FutureBuilder(
          future: loadAudioPlayer(),
          builder: (BuildContext context, AsyncSnapshot<AudioPlayer> snapshot) {
            if (!snapshot.hasError &&
                snapshot.connectionState == ConnectionState.done) {
              var audioPlayer = snapshot.requireData;
              return RawKeyboardListener(
                focusNode: _focusNode,
                autofocus: true,
                onKey: (event) {
                  debugPrint("${event.runtimeType}");
                  if (event.runtimeType == RawKeyUpEvent &&
                      event.logicalKey == LogicalKeyboardKey.arrowLeft) {
                    queuesProvider.decreaseNumber();
                    audioPlayer.play(AssetSource('sounds/pixiedust.mp3'),
                        volume: queuesProvider.selectedQueue?.soundVolume
                            .toDouble());
                  }

                  if (event.runtimeType == RawKeyUpEvent &&
                      event.logicalKey == LogicalKeyboardKey.arrowRight) {
                    queuesProvider.increaseNumber();
                    audioPlayer.play(AssetSource('sounds/pixiedust.mp3'),
                        volume: queuesProvider.selectedQueue?.soundVolume
                            .toDouble());
                  }
                },
                child: Container(),
              );
            }
            return Container();
          },
        ),
        Row(
          children: [
            Flexible(
                flex: 1,
                child: StreamBuilder<QuerySnapshot>(
                    stream: _firestore.collection('queues').snapshots(),
                    builder: (BuildContext context,
                        AsyncSnapshot<QuerySnapshot> snapshot) {
                      if (snapshot.hasError) {
                        return Text('Error: ${snapshot.error}');
                      }
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(
                          child: CircularProgressIndicator(),
                        );
                      }
                      if (snapshot.data!.docs.isEmpty) {
                        return const Center(
                          child: Text('No documents found'),
                        );
                      }

                      List<QueueModel> queues = snapshot.data!.docs
                          .map((d) => QueueDocMapper.docToQueue(d))
                          .toList();

                      return Column(
                        children: () {
                          return queues
                              .map(
                                (q) => Flexible(
                                    flex: 1, child: QueueWidget(queue: q)),
                              )
                              .toList();
                        }(),
                      );
                    })),
            Flexible(
                flex: 3,
                child: Container(
                  color: Colors.black,
                  child: Stack(
                    children: [
                      Center(
                        child: VideoPlayerWidget(
                          videoURL: queuesProvider.selectedQueue!.videoURL,
                          videoVolume: queuesProvider.selectedQueue!.videoVolume.toDouble(),
                        ),
                      ),
                      Container(
                        alignment: Alignment.bottomCenter,
                        child: RssFeedWidget(
                          velocity:
                              queuesProvider.selectedQueue!.velocity.toDouble(),
                          url: 'https://www.rtp.pt/noticias/rss',
                        ),
                      ),
                      Container(
                          padding: const EdgeInsets.all(10),
                          alignment: Alignment.topRight,
                          child: CurrentDateWidget(fontColor: Colors.white))
                    ],
                  ),
                ))
          ],
        ),
      ],
    ));
  }

  Future<AudioPlayer> loadAudioPlayer() async {
    final AudioPlayer audioPlayer = AudioPlayer();
    return audioPlayer;
  }
}
