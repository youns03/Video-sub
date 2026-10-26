package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.service.SrtGenerator
import com.example.service.SubtitleQualityAnalyzer
import com.example.ui.components.SegmentEditDialog
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryBlue

@Composable
fun EditorScreen(
    project: VideoProject?,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    onUpdateSegment: (SubtitleSegment) -> Unit,
    onDeleteSegment: (SubtitleSegment) -> Unit,
    onAddNewSegment: (french: String, arabic: String, startMs: Long, endMs: Long) -> Unit,
    onRetranslateAll: () -> Unit,
    onDownload360pVideo: () -> Unit = {},
    onMergeHardsub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingSegment by remember { mutableStateOf<SubtitleSegment?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (project == null || project.segments.isEmpty()) {
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
                    Text(
                        text = "لا توجد ترجمة مفتوحة حالياً",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "قم بمعالجة فيديو جديد من تبويب \"المعالجة\" أو افتح مشروعاً من السجل لمعاينته وتعديله هنا.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@CompositionLocalProvider
        }

        fun validatedSrtContent(): String? = try {
            SrtGenerator.buildSrtContent(project.segments)
        } catch (error: IllegalArgumentException) {
            Toast.makeText(context, error.message ?: "تعذر إنشاء ملف SRT صالح.", Toast.LENGTH_LONG).show()
            null
        }

        val filteredSegments = remember(project.segments, searchQuery) {
            if (searchQuery.isBlank()) {
                project.segments
            } else {
                project.segments.filter {
                    it.arabicText.contains(searchQuery, ignoreCase = true) ||
                            it.frenchText.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        val subtitleWarnings = remember(project.segments) {
            SubtitleQualityAnalyzer.analyze(project.segments)
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
        ) {
            // Video Player with Synced Subtitle Overlay
            item {
                VideoPlayerPreview(
                    project = project,
                    currentPositionMs = playbackPositionMs,
                    isPlaying = isPlaying,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSeekTo = onSeekTo,
                    onPlaybackPositionChanged = onPlaybackPositionChanged,
                    onPlaybackEnded = onPlaybackEnded
                )
            }

            if (subtitleWarnings.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("subtitle_quality_warnings"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "تحتاج الترجمة إلى مراجعة (${subtitleWarnings.size} ملاحظة)",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            subtitleWarnings.take(4).forEach { warning ->
                                Text(
                                    warning.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (subtitleWarnings.size > 4) {
                                Text(
                                    "وتوجد ${subtitleWarnings.size - 4} ملاحظات أخرى في المقاطع.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Quick Export & Tools Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مقاطع الترجمة (${project.segments.size} مقطع)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        validatedSrtContent()?.let { content ->
                                            SrtGenerator.copyToClipboard(context, content)
                                            Toast.makeText(context, "تم نسخ محتوى SRT إلى الحافظة", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("copy_srt_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ SRT", tint = PrimaryBlue)
                                }

                                IconButton(
                                    onClick = {
                                        validatedSrtContent()?.let { content ->
                                            val file = SrtGenerator.saveSrtToFile(context, content, project.title)
                                            SrtGenerator.shareSrtFile(context, file, project.title)
                                        }
                                    },
                                    modifier = Modifier.testTag("share_editor_srt_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة SRT", tint = EmeraldGreen)
                                }

                                IconButton(
                                    onClick = onDownload360pVideo,
                                    modifier = Modifier.testTag("editor_download_video_button")
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "تحميل فيديو 360p",
                                        tint = PrimaryBlue
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        validatedSrtContent()?.let { content ->
                                            val file = SrtGenerator.saveSrtToFile(context, content, project.title)
                                            Toast.makeText(context, "تم حفظ الملف في Documents/Subtitles:\n${file.name}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("download_srt_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "حفظ SRT", tint = EmeraldGreen)
                                }
                            }
                        }

                        // Action Buttons: Retranslate all & Add Segment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetranslateAll,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("retranslate_all_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة ترجمة الكل", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_segment_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إضافة مقطع +", fontSize = 12.sp)
                            }
                        }

                        // Search Field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في النصوص الفرنسية أو العربية...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_subtitles_field"),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "بحث")
                            },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // Segments List
            itemsIndexed(filteredSegments, key = { _, item -> item.id }) { index, segment ->
                val isCurrentlyPlaying = playbackPositionMs >= segment.startTimeMs && playbackPositionMs <= segment.endTimeMs

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSeekTo(segment.startTimeMs)
                        }
                        .testTag("subtitle_card_${segment.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentlyPlaying) PrimaryBlue.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isCurrentlyPlaying) CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue)
                    ) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Header with segment index & timestamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isCurrentlyPlaying) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentlyPlaying) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = segment.formattedTimeRange,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            // Edit Action Button
                            IconButton(
                                onClick = { editingSegment = segment },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("edit_segment_${segment.id}_button")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "تعديل المقطع",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Translated Arabic Subtitle Text
                        Text(
                            text = segment.arabicText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Original French Speech Text
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = segment.frenchText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Edit Dialog
        if (editingSegment != null) {
            SegmentEditDialog(
                segment = editingSegment!!,
                onDismiss = { editingSegment = null },
                onSave = { updated ->
                    onUpdateSegment(updated)
                    editingSegment = null
                    Toast.makeText(context, "تم حفظ التعديل بنجاح", Toast.LENGTH_SHORT).show()
                },
                onDelete = { toDelete ->
                    onDeleteSegment(toDelete)
                    editingSegment = null
                    Toast.makeText(context, "تم حذف المقطع", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add Segment Dialog
        if (showAddDialog) {
            val lastEnd = project.segments.lastOrNull()?.endTimeMs ?: 0L
            SegmentEditDialog(
                segment = SubtitleSegment(
                    id = (project.segments.maxOfOrNull { it.id } ?: 0) + 1,
                    startTimeMs = lastEnd + 200L,
                    endTimeMs = lastEnd + 3500L,
                    frenchText = "",
                    arabicText = ""
                ),
                onDismiss = { showAddDialog = false },
                onSave = { newSeg ->
                    onAddNewSegment(newSeg.frenchText, newSeg.arabicText, newSeg.startTimeMs, newSeg.endTimeMs)
                    showAddDialog = false
                    Toast.makeText(context, "تمت إضافة المقطع الجديد", Toast.LENGTH_SHORT).show()
                },
                onDelete = { showAddDialog = false }
            )
        }
    }
}
