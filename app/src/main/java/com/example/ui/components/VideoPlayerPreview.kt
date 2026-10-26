package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.PrimaryBlue

@Composable
fun VideoPlayerPreview(
    project: VideoProject,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = (project.durationSeconds * 1000L).coerceAtLeast(1000L)
    val videoPath = project.localVideoPath
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var playerError by remember(videoPath) { mutableStateOf<String?>(null) }

    DisposableEffect(videoPath, videoView) {
        val playerToDispose = videoView
        onDispose { playerToDispose?.stopPlayback() }
    }

    LaunchedEffect(videoView, isPlaying, videoPath) {
        if (isPlaying) {
            while (true) {
                delay(200)
                val player = videoView ?: continue
                if (player.isPlaying) onPlaybackPositionChanged(player.currentPosition.toLong())
            }
        }
    }

    // Find the currently active subtitle based on the real playback timestamp
    val activeSegment = project.segments.find {
        currentPositionMs >= it.startTimeMs && currentPositionMs <= it.endTimeMs
    }

    var subtitleFontSize by remember { mutableStateOf(16.sp) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .testTag("video_player_box"),
            contentAlignment = Alignment.Center
        ) {
            // Real Android video playback; subtitle timing follows VideoView's actual position.
            if (!videoPath.isNullOrBlank()) {
                AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            tag = videoPath
                            setBackgroundColor(android.graphics.Color.BLACK)
                            setVideoURI(Uri.fromFile(File(videoPath)))
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = false
                                playerError = null
                                if (isPlaying) start()
                            }
                            setOnCompletionListener { onPlaybackEnded() }
                            setOnErrorListener { _, _, _ ->
                                playerError = "تعذر تشغيل هذا الفيديو؛ قد يكون تنسيقه أو ترميزه غير مدعوم على الهاتف."
                                onPlaybackEnded()
                                true
                            }
                            videoView = this
                        }
                    },
                    update = { player ->
                        videoView = player
                        if (player.tag != videoPath) {
                            player.tag = videoPath
                            playerError = null
                            player.setVideoURI(Uri.fromFile(File(videoPath)))
                        }
                        if (isPlaying) {
                            if (!player.isPlaying) player.start()
                        } else if (player.isPlaying) {
                            player.pause()
                        }
                        if (kotlin.math.abs(player.currentPosition.toLong() - currentPositionMs) > 800L) {
                            player.seekTo(currentPositionMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = PrimaryBlue.copy(alpha = 0.25f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(project.title, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                    Text(
                        "ملف الفيديو الأصلي غير متاح؛ يمكنك مع ذلك تعديل نصوص الترجمة.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (playerError != null) {
                Box(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Text(playerError.orEmpty(), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }

            // Synced Arabic Subtitle Overlay (positioned at lower-third)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 54.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activeSegment != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = activeSegment.arabicText,
                                color = Color(0xFFFFEE55), // High-visibility yellow subtitle
                                fontSize = subtitleFontSize,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("active_subtitle_text")
                            )
                        }
                    }
                }
            }

            // Player Overlay Controls
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Center Playback Buttons
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSeekTo((currentPositionMs - 5000L).coerceAtLeast(0L)) },
                            modifier = Modifier.testTag("rewind_5s_button")
                        ) {
                            Icon(
                                Icons.Default.FastRewind,
                                contentDescription = "تأخير 5 ثواني",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                                .clickable { onPlayPauseToggle() }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { onSeekTo((currentPositionMs + 5000L).coerceAtMost(totalDurationMs)) },
                            modifier = Modifier.testTag("forward_5s_button")
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = "تقديم 5 ثواني",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom Time Slider Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimeMinSec(currentPositionMs),
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = formatTimeMinSec(totalDurationMs),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        Slider(
                            value = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { ratio ->
                                onSeekTo((ratio * totalDurationMs).toLong())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .testTag("video_seek_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryBlue,
                                activeTrackColor = PrimaryBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMinSec(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
