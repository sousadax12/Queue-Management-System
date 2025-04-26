import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

class YoutubePlayerWidget extends StatelessWidget {
  final String initialVideoId;

  const YoutubePlayerWidget({required this.initialVideoId});

  @override
  Widget build(BuildContext context) {
    final _controller = YoutubePlayerController(
      params: YoutubePlayerParams(
        mute: true,
        showControls: true,
        showFullscreenButton: true,
      ),
    );

    _controller.loadVideo(initialVideoId);

    return YoutubePlayer(
      controller: _controller,
      aspectRatio: 16 / 9,
    );
  }
}
