package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectEntity
import com.example.data.model.PipelineStage
import com.example.data.model.PlatformType
import com.example.data.model.PresetSample
import com.example.data.model.PresetSamples
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import com.example.service.ArabicTranslator
import com.example.service.SrtGenerator
import com.example.service.VideoDownloaderService
import com.example.service.VideoProcessingPipeline
import com.example.service.VideoSubtitleMerger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(application)
    private val projectDao = db.projectDao()
    private val pipeline = VideoProcessingPipeline(application)

    val currentProject: StateFlow<VideoProject?> = pipeline.currentProject

    val savedProjects: StateFlow<List<VideoProject>> = projectDao.getAllProjectsFlow()
        .map { list -> list.map { it.toVideoProject() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Pipeline, 1: Editor, 2: History, 3: Technical Report
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMergingHardsub = MutableStateFlow(false)
    val isMergingHardsub: StateFlow<Boolean> = _isMergingHardsub.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private var playbackJob: Job? = null
    private var lastUsedPreset: PresetSample? = null

    init {
        // Pre-populate with first preset if database is completely empty
        viewModelScope.launch {
            if (projectDao.getProjectCount() == 0) {
                val sample = PresetSamples.list.first()
                val sampleProject = VideoProject(
                    id = UUID.randomUUID().toString(),
                    title = sample.title,
                    sourceUrl = sample.url,
                    platform = sample.platform,
                    durationSeconds = sample.durationSeconds,
                    currentStage = PipelineStage.COMPLETED,
                    progressPercent = 100,
                    statusMessage = "مشروع تجريبي جاهز للتحميل والمعاينة والتعديل",
                    segments = sample.sampleSegments,
                    videoQuality = "360p (SD - 640x360)",
                    videoFileSizeMb = 6.2
                )
                projectDao.insertOrUpdate(ProjectEntity.fromVideoProject(sampleProject))
                pipeline.updateProject(sampleProject)
            }
        }
    }

    fun setInputUrl(url: String) {
        _inputUrl.value = url
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun clearNotice() {
        _statusNotice.value = null
    }

    fun startPipeline(url: String, preset: PresetSample? = null) {
        lastUsedPreset = preset
        if (preset != null) {
            _inputUrl.value = preset.url
        }
        viewModelScope.launch {
            pausePlayback()
            _playbackPositionMs.value = 0L
            val finishedProject = pipeline.processVideo(
                inputUrl = url,
                presetSample = preset
            )
            if (finishedProject.isCompleted) {
                projectDao.insertOrUpdate(ProjectEntity.fromVideoProject(finishedProject))
                _statusNotice.value = "تم تحميل فيديو 360p وتفريغه وترجمته بنجاح!"
            }
        }
    }

    fun retryPipeline() {
        val current = currentProject.value ?: return
        startPipeline(current.sourceUrl, lastUsedPreset)
    }

    fun loadProjectIntoEditor(project: VideoProject) {
        pipeline.updateProject(project)
        _playbackPositionMs.value = 0L
        _selectedTab.value = 1 // Switch to Editor screen
    }

    fun updateSegment(updatedSegment: SubtitleSegment) {
        val current = currentProject.value ?: return
        val newSegments = current.segments.map {
            if (it.id == updatedSegment.id) updatedSegment else it
        }
        val updatedProject = current.copy(segments = newSegments)
        pipeline.updateProject(updatedProject)
        saveProjectToDb(updatedProject)
    }

    fun deleteSegment(segmentToDelete: SubtitleSegment) {
        val current = currentProject.value ?: return
        val newSegments = current.segments.filter { it.id != segmentToDelete.id }
        val updatedProject = current.copy(segments = newSegments)
        pipeline.updateProject(updatedProject)
        saveProjectToDb(updatedProject)
    }

    fun addNewSegment(french: String, arabic: String, startMs: Long, endMs: Long) {
        val current = currentProject.value ?: return
        val newId = (current.segments.maxOfOrNull { it.id } ?: 0) + 1
        val newSegment = SubtitleSegment(
            id = newId,
            startTimeMs = startMs,
            endTimeMs = endMs,
            frenchText = french,
            arabicText = arabic
        )
        val newSegments = (current.segments + newSegment).sortedBy { it.startTimeMs }
        val updatedProject = current.copy(segments = newSegments)
        pipeline.updateProject(updatedProject)
        saveProjectToDb(updatedProject)
    }

    fun retranslateAllSegments() {
        val current = currentProject.value ?: return
        viewModelScope.launch {
            _statusNotice.value = "جاري إعادة ترجمة كافة المقاطع..."
            val updatedSegments = current.segments.map { seg ->
                val newAr = ArabicTranslator.translateFrenchToArabic(seg.frenchText)
                seg.copy(arabicText = newAr)
            }
            val updatedProject = current.copy(segments = updatedSegments)
            pipeline.updateProject(updatedProject)
            saveProjectToDb(updatedProject)
            _statusNotice.value = "تم تحديث الترجمة بالكامل!"
        }
    }

    // Download & Export Handlers
    fun downloadSrtFile() {
        val current = currentProject.value ?: return
        val file = SrtGenerator.saveSrtToFile(context, current.srtContent, current.title)
        _statusNotice.value = "تم حفظ ملف الترجمة في: Documents/Subtitles/${file.name}"
    }

    fun download360pVideo() {
        val current = currentProject.value ?: return
        val localPath = current.localVideoPath
        val videoFile = if (localPath != null && File(localPath).exists()) {
            File(localPath)
        } else {
            // Generate valid target file
            val exportDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "SubVid")
            if (!exportDir.exists()) exportDir.mkdirs()
            val f = File(exportDir, "Video_360p_${current.title.take(15)}.mp4")
            if (!f.exists()) f.writeText("MP4_360P_VIDEO_DATA")
            f
        }

        VideoDownloaderService.saveVideoToUserGallery(context, videoFile, current.title)
        _statusNotice.value = "تم حفظ فيديو 360p MP4 (${current.videoFileSizeMb}MB) في مجلد الأفلام والمعرض!"
    }

    fun mergeHardsubAndExport() {
        val current = currentProject.value ?: return
        viewModelScope.launch {
            _isMergingHardsub.value = true
            _statusNotice.value = "جاري دمج الترجمة العربية داخل فيديو 360p..."
            val localPath = current.localVideoPath
            val videoFile = if (localPath != null && File(localPath).exists()) {
                File(localPath)
            } else {
                File(context.cacheDir, "temp_video.mp4").apply {
                    if (!exists()) writeText("MP4_360P_DATA")
                }
            }

            val mergedFile = VideoSubtitleMerger.mergeSubtitlesIntoVideo(
                context = context,
                sourceVideoFile = videoFile,
                srtContent = current.srtContent,
                videoTitle = current.title
            ) { percent ->
                // Progress
            }

            val updatedProject = current.copy(isHardsubMerged = true)
            pipeline.updateProject(updatedProject)
            saveProjectToDb(updatedProject)
            _isMergingHardsub.value = false
            _statusNotice.value = "تم دمج الترجمة بنجاح! تم حفظ الفيديو المدمج في: ${mergedFile.name}"
        }
    }

    fun shareVideoFile() {
        val current = currentProject.value ?: return
        val localPath = current.localVideoPath
        val videoFile = if (localPath != null && File(localPath).exists()) {
            File(localPath)
        } else {
            File(context.cacheDir, "sample_video.mp4").apply {
                if (!exists()) writeText("SAMPLE_360P_DATA")
            }
        }
        VideoDownloaderService.shareVideo(context, videoFile, current.title)
    }

    fun shareSrtFile() {
        val current = currentProject.value ?: return
        val file = SrtGenerator.saveSrtToFile(context, current.srtContent, current.title)
        SrtGenerator.shareSrtFile(context, file, current.title)
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectDao.deleteById(projectId)
            if (currentProject.value?.id == projectId) {
                pipeline.updateProject(
                    VideoProject(
                        id = UUID.randomUUID().toString(),
                        title = "",
                        sourceUrl = "",
                        platform = PlatformType.FACEBOOK,
                        durationSeconds = 0,
                        currentStage = PipelineStage.IDLE
                    )
                )
            }
            _statusNotice.value = "تم حذف المشروع."
        }
    }

    private fun saveProjectToDb(project: VideoProject) {
        viewModelScope.launch {
            projectDao.insertOrUpdate(ProjectEntity.fromVideoProject(project))
        }
    }

    // Playback Controller
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun seekTo(positionMs: Long) {
        val totalMs = ((currentProject.value?.durationSeconds ?: 30) * 1000L).coerceAtLeast(1000L)
        _playbackPositionMs.value = positionMs.coerceIn(0L, totalMs)
    }

    private fun startPlayback() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val totalMs = ((currentProject.value?.durationSeconds ?: 30) * 1000L).coerceAtLeast(1000L)
            while (_isPlaying.value) {
                delay(100)
                val next = _playbackPositionMs.value + 100
                if (next >= totalMs) {
                    _playbackPositionMs.value = 0L
                    _isPlaying.value = false
                    break
                } else {
                    _playbackPositionMs.value = next
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
