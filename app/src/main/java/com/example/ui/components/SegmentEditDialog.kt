package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SubtitleSegment
import com.example.service.ArabicTranslator
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ErrorRed
import kotlinx.coroutines.launch

@Composable
fun SegmentEditDialog(
    segment: SubtitleSegment,
    onDismiss: () -> Unit,
    onSave: (SubtitleSegment) -> Unit,
    onDelete: (SubtitleSegment) -> Unit
) {
    var arabicText by remember { mutableStateOf(segment.arabicText) }
    var frenchText by remember { mutableStateOf(segment.frenchText) }
    var startTimeStr by remember { mutableStateOf(segment.formattedStartTime) }
    var endTimeStr by remember { mutableStateOf(segment.formattedEndTime) }
    var isRetranslating by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("segment_edit_dialog"),
            title = {
                Text(
                    text = "تعديل مقطع الترجمة #${segment.id}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Arabic Translation TextField (RTL)
                    OutlinedTextField(
                        value = arabicText,
                        onValueChange = { arabicText = it },
                        label = { Text("النص المترجم (العربية)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("arabic_subtitle_input"),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // French Speech TextField (LTR)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = frenchText,
                            onValueChange = { frenchText = it },
                            label = { Text("Texte original en français") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("french_subtitle_input"),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Auto-retranslate button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isRetranslating = true
                                val newArabic = ArabicTranslator.translateFrenchToArabic(frenchText)
                                arabicText = newArabic
                                isRetranslating = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_retranslate_button"),
                        enabled = !isRetranslating
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "إعادة الترجمة",
                            tint = EmeraldGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRetranslating) "جاري إعادة الترجمة..." else "إعادة ترجمة هذا المقطع آلياً")
                    }

                    // Timestamps Range
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = startTimeStr,
                                onValueChange = { startTimeStr = it },
                                label = { Text("Début (Start)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endTimeStr,
                                onValueChange = { endTimeStr = it },
                                label = { Text("Fin (End)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newStartMs = SubtitleSegment.parseTimestamp(startTimeStr)
                        val newEndMs = SubtitleSegment.parseTimestamp(endTimeStr)
                        onSave(
                            segment.copy(
                                frenchText = frenchText,
                                arabicText = arabicText,
                                startTimeMs = if (newStartMs > 0) newStartMs else segment.startTimeMs,
                                endTimeMs = if (newEndMs > 0) newEndMs else segment.endTimeMs
                            )
                        )
                    },
                    modifier = Modifier.testTag("save_segment_button")
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { onDelete(segment) },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.testTag("delete_segment_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }
}
