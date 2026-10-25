package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PipelineStage
import com.example.data.model.VideoProject
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue

@Composable
fun PipelineProgressCard(
    project: VideoProject,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("pipeline_progress_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with stage status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = project.currentStage.arabicTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (project.currentStage == PipelineStage.ERROR) ErrorRed else MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = project.currentStage.frenchTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    }

                    // Progress percentage badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (project.currentStage == PipelineStage.COMPLETED) EmeraldGreen.copy(alpha = 0.2f)
                                else if (project.currentStage == PipelineStage.ERROR) ErrorRed.copy(alpha = 0.2f)
                                else PrimaryBlue.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${project.progressPercent}%",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (project.currentStage == PipelineStage.COMPLETED) EmeraldGreen
                                else if (project.currentStage == PipelineStage.ERROR) ErrorRed
                                else PrimaryBlue
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Progress Bar
                val animatedProgress by animateFloatAsState(
                    targetValue = project.progressPercent / 100f,
                    label = "progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("pipeline_progress_bar"),
                    color = if (project.currentStage == PipelineStage.COMPLETED) EmeraldGreen
                    else if (project.currentStage == PipelineStage.ERROR) ErrorRed
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Status message
                Text(
                    text = project.statusMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5 Steps Visual Flow
                val stages = listOf(
                    PipelineStage.VALIDATING_URL,
                    PipelineStage.DOWNLOADING_VIDEO_360P,
                    PipelineStage.EXTRACTING_SPEECH_FR,
                    PipelineStage.TRANSLATING_AR,
                    PipelineStage.BUILDING_SRT
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stages.forEach { stage ->
                        val isDone = project.currentStage.stageNumber > stage.stageNumber || project.isCompleted
                        val isCurrent = project.currentStage == stage
                        val isFailed = project.currentStage == PipelineStage.ERROR && project.progressPercent < (stage.stageNumber * 20)

                        StageRowItem(
                            stage = stage,
                            isDone = isDone,
                            isCurrent = isCurrent,
                            isFailed = isFailed
                        )
                    }
                }

                // Error Retry Button
                AnimatedVisibility(visible = project.currentStage == PipelineStage.ERROR) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("retry_pipeline_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة المحاولة")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة المحاولة من هذه المرحلة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRowItem(
    stage: PipelineStage,
    isDone: Boolean,
    isCurrent: Boolean,
    isFailed: Boolean
) {
    val iconBgColor by animateColorAsState(
        targetValue = when {
            isDone -> EmeraldGreen
            isCurrent -> PrimaryBlue
            isFailed -> ErrorRed
            else -> Color.Gray.copy(alpha = 0.3f)
        },
        label = "stageBg"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDone -> Icon(
                    Icons.Default.Check,
                    contentDescription = "تم",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                isCurrent -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                isFailed -> Icon(
                    Icons.Default.Error,
                    contentDescription = "فشل",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                else -> Text(
                    text = "${stage.stageNumber}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = stage.arabicTitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isDone -> MaterialTheme.colorScheme.onSurface
                    isCurrent -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
        )
    }
}
