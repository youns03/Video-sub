package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PlatformType
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.data.model.PipelineStage

@Entity(tableName = "video_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val platform: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val segments: List<SubtitleSegment>,
    val createdAt: Long,
    val audioBitrate: String,
    val detectedLanguage: String,
    val videoQuality: String = "360p (SD - 640x360)",
    val videoFileSizeMb: Double = 6.4,
    val localVideoPath: String? = null,
    val isHardsubMerged: Boolean = false
) {
    fun toVideoProject(): VideoProject {
        return VideoProject(
            id = id,
            title = title,
            sourceUrl = sourceUrl,
            platform = try { PlatformType.valueOf(platform) } catch (e: Exception) { PlatformType.DIRECT_URL },
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            currentStage = PipelineStage.COMPLETED,
            progressPercent = 100,
            statusMessage = "جاهز للتحميل والمعاينة والتصدير",
            segments = segments,
            createdAt = createdAt,
            audioBitrate = audioBitrate,
            detectedLanguage = detectedLanguage,
            videoQuality = videoQuality,
            videoFileSizeMb = videoFileSizeMb,
            localVideoPath = localVideoPath,
            isHardsubMerged = isHardsubMerged
        )
    }

    companion object {
        fun fromVideoProject(project: VideoProject): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                title = project.title,
                sourceUrl = project.sourceUrl,
                platform = project.platform.name,
                durationSeconds = project.durationSeconds,
                thumbnailUrl = project.thumbnailUrl,
                segments = project.segments,
                createdAt = project.createdAt,
                audioBitrate = project.audioBitrate,
                detectedLanguage = project.detectedLanguage,
                videoQuality = project.videoQuality,
                videoFileSizeMb = project.videoFileSizeMb,
                localVideoPath = project.localVideoPath,
                isHardsubMerged = project.isHardsubMerged
            )
        }
    }
}
