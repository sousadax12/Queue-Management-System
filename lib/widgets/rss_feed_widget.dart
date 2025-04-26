import 'dart:async';
import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:http/http.dart' as http;
import 'package:text_scroll/text_scroll.dart';
import 'package:webfeed/webfeed.dart';

class RssFeedWidget extends StatefulWidget {
  final String url;
  final double velocity;

  RssFeedWidget({required this.url, required this.velocity});

  @override
  _RssFeedWidgetState createState() => _RssFeedWidgetState();
}

class _RssFeedWidgetState extends State<RssFeedWidget>
    with SingleTickerProviderStateMixin {
  List<String> _titles = [];

  @override
  void initState() {
    super.initState();
    _loadFeed();
  }

  Future<List<String>> _loadFeed() async {
    var response = await http.get(Uri.parse(widget.url));
    var feed = RssFeed.parse(response.body);
    return feed.items!.map((item) => item.title!).toList();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: _loadFeed(),
      builder: (BuildContext context, AsyncSnapshot<List<String>> snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            !snapshot.hasError &&
            snapshot.hasData) {
          var titles = snapshot.data;
          return Container(
            height: 40.0,
            child: Stack(
              children: [
                Positioned.fill(
                  bottom: 0,
                  child: Container(
                      child: TextScroll(
                        titles!.join("   |   "),
                        velocity: Velocity(pixelsPerSecond: Offset(widget.velocity, 0)),
                        style: TextStyle(color: Colors.white, fontSize: 20),
                        mode: TextScrollMode.endless,
                      )),
                ),
              ],
            ),
          );
        } else
          return Container();
      }
    );
  }

  @override
  void dispose() {
    super.dispose();
  }
}
