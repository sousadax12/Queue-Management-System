import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:youtube_player_flutter/youtube_player_flutter.dart';

class VideoPlayerWidget extends StatefulWidget {
  final String videoURL;
  final double videoVolume;

  const VideoPlayerWidget({
    super.key,
    required this.videoURL,
    required this.videoVolume,
  });

  @override
  State<VideoPlayerWidget> createState() => _VideoPlayerWidgetState();
}

class _VideoPlayerWidgetState extends State<VideoPlayerWidget> {
  VideoPlayerController? _videoController;
  YoutubePlayerController? _youtubeController;
  bool _isLoading = true;
  bool _hasError = false;

  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }

  @override
  void didUpdateWidget(VideoPlayerWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Check if video URL changed
    if (oldWidget.videoURL != widget.videoURL) {
      _disposeControllers();
      _initializePlayer();
    } else if (oldWidget.videoVolume != widget.videoVolume) {
      // Update volume if only volume changed
      _updateVolume();
    }
  }

  @override
  void dispose() {
    _disposeControllers();
    super.dispose();
  }

  void _disposeControllers() {
    _videoController?.dispose();
    _videoController = null;
    _youtubeController?.dispose();
    _youtubeController = null;
  }

  Future<void> _initializePlayer() async {
    setState(() {
      _isLoading = true;
      _hasError = false;
    });

    try {
      if (_isYouTubeUrl(widget.videoURL)) {
        await _initializeYouTubePlayer();
      } else {
        await _initializeVideoPlayer();
      }
      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      debugPrint('Error initializing player: $e');
      setState(() {
        _isLoading = false;
        _hasError = true;
      });
    }
  }

  Future<void> _initializeYouTubePlayer() async {
    final ids = _extractYouTubeIds(widget.videoURL);
    final videoId = ids['videoId'];

    if (videoId == null && ids['playlistId'] != null) {
      debugPrint('Playlist detected but youtube_player_flutter has limited playlist support');
      throw Exception('Playlists not supported');
    }

    if (videoId != null) {
      _youtubeController = YoutubePlayerController(
        initialVideoId: videoId,
        flags: const YoutubePlayerFlags(
          autoPlay: true,
          mute: true,
          disableDragSeek: false,
          loop: true,
          isLive: false,
          forceHD: false,
          enableCaption: true,
          hideControls: false,
          controlsVisibleAtStart: false,
        ),
      );
      _updateVolume();
    } else {
      throw Exception('No valid YouTube video ID found');
    }
  }

  Future<void> _initializeVideoPlayer() async {
    _videoController = VideoPlayerController.networkUrl(Uri.parse(widget.videoURL));
    await _videoController!.initialize();
    _videoController!.setLooping(true);
    _videoController!.play();
    _updateVolume();
  }

  void _updateVolume() {
    if (_youtubeController != null) {
      _youtubeController!.setVolume((widget.videoVolume * 100).round());
    } else if (_videoController != null) {
      _videoController!.setVolume(widget.videoVolume);
    }
  }

  bool _isYouTubeUrl(String url) {
    return url.contains('youtube.com') || url.contains('youtu.be');
  }

  Map<String, String?> _extractYouTubeIds(String url) {
    String? videoId;
    String? playlistId;

    final uri = Uri.parse(url);

    // Check for playlist
    if (uri.queryParameters.containsKey('list')) {
      playlistId = uri.queryParameters['list'];
    }

    // Extract video ID
    if (uri.host.contains('youtube.com')) {
      videoId = uri.queryParameters['v'];
    } else if (uri.host.contains('youtu.be')) {
      videoId = uri.pathSegments.isNotEmpty ? uri.pathSegments[0] : null;
    }

    return {'videoId': videoId, 'playlistId': playlistId};
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (_hasError) {
      return Container();
    }

    if (_youtubeController != null) {
      return YoutubePlayer(
        controller: _youtubeController!,
        showVideoProgressIndicator: true,
        progressIndicatorColor: Colors.red,
        progressColors: const ProgressBarColors(
          playedColor: Colors.red,
          handleColor: Colors.redAccent,
        ),
      );
    } else if (_videoController != null && _videoController!.value.isInitialized) {
      return AspectRatio(
        aspectRatio: _videoController!.value.aspectRatio,
        child: VideoPlayer(_videoController!),
      );
    }

    return Container();
  }
}
