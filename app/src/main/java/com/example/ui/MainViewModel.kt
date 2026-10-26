package com.example.ui

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        .map { list -> list.map { it.toVideoProject() }.filterNot { project -> PresetSamples.list.any { sample -> sample.url == project.sourceUrl } } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Pipeline, 1: Editor, 2: History, 3: Settings
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMergingHardsub = MutableStateFlow(false)
    val isMergingHardsub: StateFlow<Boolean> = _isMergingHardsub.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private var lastUsedPreset: PresetSample? = null

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
            val finishedProject = try {
                pipeline.processVideo(inputUrl = url, presetSample = preset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _statusNotice.value = error.message ?: "تعذرت معالجة الفيديو بسبب خطأ غير متوقع."
                return@launch
            }
            if (finishedProject.isCompleted ||
                (finishedProject.localVideoPath != null && finishedProject.segments.isNotEmpty())) {
                val saved = persistProjectSafely(
                    finishedProject,
                    "انتهت المعالجة، لكن تعذر حفظ المشروع في السجل."
                )
                if (saved) {
                    _statusNotice.value = if (finishedProject.isCompleted) {
                        "اكتملت المعالجة وحُفظ المشروع."
                    } else {
                        "حُفظ النص الفرنسي وتوقيتاته؛ يمكنك إعادة الترجمة دون إعادة التفريغ."
                    }
                }
            }
        }
    }

    fun retryPipeline() {
        val current = currentProject.value ?: return
        val retrySource = current.localVideoPath?.let { path -> Uri.fromFile(File(path)).toString() } ?: current.sourceUrl
        startPipeline(retrySource, lastUsedPreset)
    }

    /** Retry only missing Arabic translations; keep French text and timing unchanged. */
    fun retryTranslation() {
        val original = currentProject.value ?: return
        if (original.segments.isEmpty()) return

        viewModelScope.launch {
            var working = original.copy(
                currentStage = PipelineStage.TRANSLATING_AR,
                progressPercent = 74,
                statusMessage = "إعادة ترجمة المقاطع الناقصة؛ لن يُعاد تفريغ الصوت.",
                errorMessage = null
            )
            val segments = original.segments.toMutableList()
            pipeline.updateProject(working)

            for (index in segments.indices) {
                val segment = segments[index]
                if (segment.arabicText.isBlank()) {
                    try {
                        val arabic = ArabicTranslator.translateFrenchToArabic(segment.frenchText)
                        segments[index] = segment.copy(arabicText = arabic)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        working = working.copy(
                            segments = segments.toList(),
                            currentStage = PipelineStage.ERROR,
                            progressPercent = 74 + (index * 16 / segments.size.coerceAtLeast(1)),
                            statusMessage = error.message ?: "تعذرت الترجمة؛ احتفظنا بالتفريغ الفرنسي والنتائج المكتملة.",
                            errorMessage = error.message ?: "فشل الاتصال بخدمة الترجمة."
                        )
                        pipeline.updateProject(working)
                        if (persistProjectSafely(working, "تعذر حفظ التفريغ الفرنسي في السجل.")) {
                            _statusNotice.value = "حُفظ التفريغ الفرنسي. أعد المحاولة لاحقًا دون إعادة التفريغ."
                        }
                        return@launch
                    }
                }

                working = working.copy(
                    segments = segments.toList(),
                    progressPercent = 74 + ((index + 1) * 16 / segments.size.coerceAtLeast(1)),
                    statusMessage = "ترجمة المقطع " + (index + 1) + " من " + segments.size + "؛ التوقيت الأصلي محفوظ."
                )
                pipeline.updateProject(working)
            }

            val completed = working.copy(
                currentStage = PipelineStage.COMPLETED,
                progressPercent = 100,
                statusMessage = "اكتملت الترجمة مع الحفاظ على التفريغ الفرنسي والتوقيتات.",
                errorMessage = null
            )
            pipeline.updateProject(completed)
            if (persistProjectSafely(completed, "اكتملت الترجمة، لكن تعذر حفظ المشروع في السجل.")) {
                _statusNotice.value = "اكتملت الترجمة وحُفظ المشروع."
            }
        }
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
        if (current.segments.isEmpty()) {
            _statusNotice.value = "لا توجد مقاطع لإعادة ترجمتها."
            return
        }
        viewModelScope.launch {
            _statusNotice.value = "جاري إعادة ترجمة المقاطع..."
            try {
                val updatedSegments = current.segments.map { seg ->
                    val newAr = ArabicTranslator.translateFrenchToArabic(seg.frenchText)
                    if (newAr.isBlank()) throw IllegalStateException("أعادت خدمة الترجمة نصاً فارغاً.")
                    seg.copy(arabicText = newAr)
                }
                val updatedProject = current.copy(segments = updatedSegments)
                pipeline.updateProject(updatedProject)
                if (persistProjectSafely(updatedProject, "تم تحديث الترجمة، لكن تعذر حفظ المشروع.")) {
                    _statusNotice.value = "تم تحديث الترجمة."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _statusNotice.value = error.message ?: "تعذرت إعادة الترجمة؛ لم نُحفظ نتيجة جزئية."
            }
        }
    }

    // Download & Export Handlers
    fun downloadSrtFile() {
        val current = currentProject.value ?: return
        if (current.segments.isEmpty() || current.segments.any { it.arabicText.isBlank() }) {
            _statusNotice.value = "لا يمكن تصدير SRT: لا توجد ترجمة عربية مكتملة وصالحة."
            return
        }
        try {
            val srt = SrtGenerator.buildSrtContent(current.segments)
            val file = SrtGenerator.saveSrtToFile(context, srt, current.title)
            _statusNotice.value = "تم حفظ ملف الترجمة في: Documents/Subtitles/${file.name}"
        } catch (error: Exception) {
            _statusNotice.value = error.message ?: "تعذر إنشاء ملف SRT صالح."
        }
    }

    fun download360pVideo() {
        val current = currentProject.value ?: return
        val videoFile = current.localVideoPath?.let(::File)
        if (videoFile == null || !videoFile.isFile || videoFile.length() <= 0L) {
            _statusNotice.value = "لم نعثر على ملف الفيديو الأصلي. لن نصدر فيديو تجريبيًا بدلاً منه."
            return
        }
        viewModelScope.launch {
            _statusNotice.value = "جارٍ حفظ الفيديو الأصلي في المعرض..."
            try {
                val savedUri = withContext(Dispatchers.IO) {
                    VideoDownloaderService.saveVideoToUserGallery(context, videoFile, current.title)
                }
                _statusNotice.value = if (savedUri != null) {
                    "تم حفظ الفيديو الأصلي في المعرض. لم يتم تحويل دقته إلى 360p."
                } else {
                    "تعذر حفظ الفيديو في المعرض؛ تحقق من مساحة التخزين وتنسيق الملف."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _statusNotice.value = error.message ?: "تعذر حفظ الفيديو في المعرض."
            }
        }
    }

    fun mergeHardsubAndExport() {
        val current = currentProject.value ?: return
        viewModelScope.launch {
            _isMergingHardsub.value = true
            _statusNotice.value = "جارٍ التحقق من إمكانية دمج الترجمة..."
            try {
                val localPath = current.localVideoPath
                val videoFile = localPath?.let(::File)
                if (videoFile == null || !videoFile.isFile || videoFile.length() <= 0L) {
                    throw IllegalStateException("ملف الفيديو الأصلي غير موجود.")
                }
                if (current.segments.isEmpty() || current.segments.any { it.arabicText.isBlank() }) {
                    throw IllegalStateException("لا توجد ترجمة عربية مكتملة لدمجها.")
                }
                VideoSubtitleMerger.mergeSubtitlesIntoVideo(
                    context = context,
                    sourceVideoFile = videoFile,
                    srtContent = SrtGenerator.buildSrtContent(current.segments),
                    videoTitle = current.title
                ) { }
                _statusNotice.value = "تعذر دمج الترجمة: محرك الحرق الحقيقي غير مدمج بعد."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _statusNotice.value = error.message ?: "تعذر دمج الترجمة داخل الفيديو."
            } finally {
                _isMergingHardsub.value = false
            }
        }
    }

    fun shareVideoFile() {
        val current = currentProject.value ?: return
        val videoFile = current.localVideoPath?.let(::File)
        if (videoFile == null || !videoFile.isFile || videoFile.length() <= 0L) {
            _statusNotice.value = "ملف الفيديو الأصلي غير متاح للمشاركة."
            return
        }
        VideoDownloaderService.shareVideo(context, videoFile, current.title)
    }

    fun shareSrtFile() {
        val current = currentProject.value ?: return
        if (current.segments.isEmpty() || current.segments.any { it.arabicText.isBlank() }) {
            _statusNotice.value = "لا يمكن مشاركة SRT: لا توجد ترجمة عربية مكتملة وصالحة."
            return
        }
        try {
            val srt = SrtGenerator.buildSrtContent(current.segments)
            val file = SrtGenerator.saveSrtToFile(context, srt, current.title)
            SrtGenerator.shareSrtFile(context, file, current.title)
        } catch (error: Exception) {
            _statusNotice.value = error.message ?: "تعذرت مشاركة ملف الترجمة."
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _statusNotice.value = error.message ?: "تعذر حذف المشروع من السجل."
            }
        }
    }


    private suspend fun persistProjectSafely(project: VideoProject, failureMessage: String): Boolean {
        return try {
            projectDao.insertOrUpdate(ProjectEntity.fromVideoProject(project))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _statusNotice.value = error.message?.let { "$failureMessage $it" } ?: failureMessage
            false
        }
    }

    private fun saveProjectToDb(project: VideoProject) {
        viewModelScope.launch {
            persistProjectSafely(project, "تعذر حفظ التعديلات في السجل.")
        }
    }

    // Playback state is driven by the real Android VideoView, not a simulated timer.
    fun togglePlayPause() {
        if (_isPlaying.value) pausePlayback() else startPlayback()
    }

    fun seekTo(positionMs: Long) {
        val totalMs = ((currentProject.value?.durationSeconds ?: 30) * 1000L).coerceAtLeast(1000L)
        _playbackPositionMs.value = positionMs.coerceIn(0L, totalMs)
    }

    fun updatePlaybackPosition(positionMs: Long) {
        val totalMs = ((currentProject.value?.durationSeconds ?: 30) * 1000L).coerceAtLeast(1000L)
        _playbackPositionMs.value = positionMs.coerceIn(0L, totalMs)
    }

    fun onPlaybackEnded() {
        _isPlaying.value = false
        _playbackPositionMs.value = 0L
    }

    private fun startPlayback() {
        _isPlaying.value = true
    }

    fun pausePlayback() {
        _isPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
    }
}
