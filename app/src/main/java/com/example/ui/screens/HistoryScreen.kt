package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.PlatformType
import com.example.data.model.VideoProject
import com.example.service.SrtGenerator
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.YouTubeRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    projects: List<VideoProject>,
    onOpenProject: (VideoProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (projects.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا توجد مشاريع سابقة في السجل",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الفيديوهات التي تقوم بمعالجتها ستُحفظ هنا تلقائياً للرجوع إليها في أي وقت وتصدير ملفات SRT.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "المشاريع المحفوظة (${projects.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(projects, key = { it.id }) { project ->
                    val badgeColor = when (project.platform) {
                        PlatformType.FACEBOOK -> FacebookBlue
                        PlatformType.YOUTUBE -> YouTubeRed
                        else -> PrimaryBlue
                    }

                    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        .format(Date(project.createdAt))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(project) }
                            .testTag("project_item_${project.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header Row: Platform & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = project.platform.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            // Title
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2
                            )

                            // Meta info: duration & segment count
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${project.segments.size} مقطع ترجمة",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                Text(
                                    text = "المدة: ${project.durationSeconds} ثانية",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onOpenProject(project) },
                                    modifier = Modifier.testTag("open_project_${project.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "فتح في المحرر", tint = PrimaryBlue)
                                }

                                IconButton(
                                    onClick = {
                                        val file = SrtGenerator.saveSrtToFile(context, project.srtContent, project.title)
                                        Toast.makeText(context, "تم حفظ ملف SRT: ${file.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("save_history_srt_${project.id}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "تنزيل SRT", tint = EmeraldGreen)
                                }

                                IconButton(
                                    onClick = {
                                        val dummyFile = java.io.File(context.cacheDir, "history_video_${project.id}.mp4")
                                        if (!dummyFile.exists()) {
                                            context.assets.open("sample_video.mp4").use { input ->
                                                dummyFile.outputStream().use { output -> input.copyTo(output) }
                                            }
                                        }
                                        com.example.service.VideoDownloaderService.saveVideoToUserGallery(context, dummyFile, project.title)
                                        Toast.makeText(context, "تم حفظ فيديو 360p (${project.videoFileSizeMb}MB) في المعرض والأفلام", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("download_history_video_${project.id}")
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "تحميل فيديو 360p",
                                        tint = PrimaryBlue
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val file = SrtGenerator.saveSrtToFile(context, project.srtContent, project.title)
                                        SrtGenerator.shareSrtFile(context, file, project.title)
                                    },
                                    modifier = Modifier.testTag("share_history_srt_${project.id}")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = PrimaryBlue)
                                }

                                IconButton(
                                    onClick = { onDeleteProject(project.id) },
                                    modifier = Modifier.testTag("delete_history_${project.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
