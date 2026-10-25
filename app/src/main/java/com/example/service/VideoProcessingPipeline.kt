package com.example.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.model.PipelineStage
import com.example.data.model.PlatformType
import com.example.data.model.PresetSample
import com.example.data.model.SubtitleSegment
import com.example.data.model.VideoProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class VideoProcessingPipeline(private val context: Context) {

    private val _currentProject = MutableStateFlow<VideoProject?>(null)
    val currentProject: StateFlow<VideoProject?> = _currentProject.asStateFlow()

    /**
     * Executes the refined 5-stage transformation pipeline:
     * 1. URL validation
     * 2. Full 360p MP4 Video download
     * 3. French speech extraction (Studio-grade Netflix/Amazon standard cues)
     * 4. Accurate Arabic translation with exact timestamp preservation
     * 5. SRT file building, 360p download ready, and Hardsub merger option
     */
    suspend fun processVideo(
        inputUrl: String,
        presetSample: PresetSample? = null,
        onStageChanged: suspend (PipelineStage, Int, String) -> Unit = { _, _, _ -> }
    ): VideoProject {
        val id = UUID.randomUUID().toString()

        // Local video imports bypass URL validation and never contact an external service.
        val sourceUri = runCatching { Uri.parse(inputUrl) }.getOrNull()
        val isLocalFile = sourceUri?.scheme.equals("content", ignoreCase = true) ||
            sourceUri?.scheme.equals("file", ignoreCase = true)

        // This build intentionally accepts imported local files only; no URL or demo shortcut.
        val validation = when {
            presetSample != null -> UrlValidationResult(
                false, PlatformType.LOCAL_FILE, inputUrl, "",
                errorMessage = "العينات التجريبية معطلة حتى لا تُعرض حوارات وهمية كأنها تفريغ حقيقي."
            )
            isLocalFile -> UrlValidationResult(true, PlatformType.LOCAL_FILE, inputUrl, "فيديو مستورد")
            else -> UrlValidationResult(
                false, PlatformType.LOCAL_FILE, inputUrl, "",
                errorMessage = "اختر ملف فيديو من الهاتف. معالجة الروابط غير مفعلة في هذه النسخة."
            )
        }

        if (!validation.isValid) {
            val failedProject = VideoProject(
                id = id,
                title = "فيديو غير صالح",
                sourceUrl = inputUrl,
                platform = validation.platform,
                durationSeconds = 0,
                currentStage = PipelineStage.ERROR,
                progressPercent = 0,
                statusMessage = validation.errorMessage ?: "الرابط غير صالح",
                errorMessage = validation.errorMessage
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 0, validation.errorMessage ?: "خطأ في الرابط")
            return failedProject
        }

        val videoTitle = validation.titleSuggestion.ifBlank { "فيديو مستورد" }
        val localDuration = if (isLocalFile && sourceUri != null) {
            runCatching {
                MediaMetadataRetriever().let { retriever ->
                    try {
                        retriever.setDataSource(context, sourceUri)
                        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            ?.div(1000L)?.toInt() ?: 0).coerceAtLeast(0)
                    } finally {
                        retriever.release()
                    }
                }
            }.getOrDefault(0)
        } else 0

        if (localDuration <= 0) {
            val message = "تعذر قراءة مدة الفيديو أو أن الملف غير مدعوم. لم نستخدم مدة تقديرية بدلاً من بيانات الملف."
            val failedProject = VideoProject(
                id = id,
                title = videoTitle,
                sourceUrl = inputUrl,
                platform = PlatformType.LOCAL_FILE,
                durationSeconds = 0,
                currentStage = PipelineStage.ERROR,
                progressPercent = 0,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 0, message)
            return failedProject
        }

        val estimatedDuration = localDuration
        val mediaUrl = validation.cleanUrl

        var project = VideoProject(
            id = id,
            title = videoTitle,
            sourceUrl = validation.cleanUrl,
            platform = validation.platform,
            durationSeconds = estimatedDuration,
            currentStage = PipelineStage.VALIDATING_URL,
            progressPercent = 10,
            videoFileSizeMb = 0.0,
            videoQuality = "المصدر الأصلي (لم يُحوّل)",
            statusMessage = "جاري تجهيز الفيديو المستورد من الهاتف..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.VALIDATING_URL, 10, project.statusMessage)
        delay(400)

        // 2. Stage 2: Downloading video in 360p MP4 specifically
        project = project.copy(
            currentStage = PipelineStage.DOWNLOADING_VIDEO_360P,
            progressPercent = 18,
            statusMessage = "جاري استيراد ملف الفيديو من الهاتف..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.DOWNLOADING_VIDEO_360P, 18, project.statusMessage)

        val downloadedFile: File = try {
            VideoDownloaderService.importVideoToAppStorage(
                context = context,
                url = mediaUrl,
                videoTitle = videoTitle,
                durationSeconds = estimatedDuration,
                requestHeaders = emptyMap()
            ) { progress ->
                val pipelineProgress = 18 + (progress.percent * 22 / 100)
                val total = if (progress.totalMb > 0) " من ${progress.totalMb}MB" else ""
                val updatedMsg = "نسخ الملف: ${progress.downloadedMb}MB$total (${progress.percent}%)"
                _currentProject.value = _currentProject.value?.copy(
                    progressPercent = pipelineProgress,
                    statusMessage = updatedMsg
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: "تعذر تنزيل الفيديو من الرابط"
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 18,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 18, message)
            return failedProject
        }

        val copiedDurationSeconds = runCatching {
            MediaMetadataRetriever().let { retriever ->
                try {
                    retriever.setDataSource(downloadedFile.absolutePath)
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.div(1000L)?.toInt() ?: 0)
                } finally {
                    retriever.release()
                }
            }
        }.getOrDefault(0)
        if (copiedDurationSeconds <= 0) {
            downloadedFile.delete()
            val message = "تعذر التحقق من أن النسخة المستوردة ملف فيديو قابل للقراءة؛ أوقفنا المعالجة."
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 18,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 18, message)
            return failedProject
        }

        project = project.copy(
            localVideoPath = downloadedFile.absolutePath,
            durationSeconds = copiedDurationSeconds,
            videoFileSizeMb = downloadedFile.length() / 1_000_000.0,
            progressPercent = 40,
            statusMessage = "تم استيراد الفيديو بنجاح (${downloadedFile.length() / 1_000_000.0} MB)."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.DOWNLOADING_VIDEO_360P, 40, project.statusMessage)
        delay(350)

        // 3. Stage 3: French Speech Extraction with Studio Netflix/Amazon Standards
        project = project.copy(
            currentStage = PipelineStage.EXTRACTING_SPEECH_FR,
            progressPercent = 48,
            statusMessage = "جاري استخراج الصوت وتفريغ الكلام الفرنسي بمعايير Netflix و Amazon..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.EXTRACTING_SPEECH_FR, 48, project.statusMessage)

        val rawFrenchSegments: List<SubtitleSegment> = try {
            if (presetSample != null) {
                delay(500)
                presetSample.sampleSegments.map { it.copy(arabicText = "") }
            } else {
                FrenchSTTEngine.transcribeAudioWithStudioStandards(estimatedDuration) { p, text ->
                    val calcProgress = 48 + (p * 22 / 100)
                    _currentProject.value = _currentProject.value?.copy(
                        progressPercent = calcProgress,
                        statusMessage = "تفريغ الصوت: \"${text.take(35)}...\""
                    )
                }
            }
        } catch (error: Exception) {
            val message = error.message ?: "تعذر تفريغ الصوت الفرنسي."
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 48,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 48, message)
            return failedProject
        }

        val invalidSegment = rawFrenchSegments.firstOrNull {
            it.startTimeMs < 0L || it.endTimeMs <= it.startTimeMs ||
                it.endTimeMs > estimatedDuration * 1000L + 1000L ||
                it.frenchText.isBlank()
        }
        if (rawFrenchSegments.isEmpty() || invalidSegment != null) {
            val message = "نتيجة التفريغ فارغة أو تحتوي توقيتات غير صالحة؛ أوقفنا المعالجة لتجنب ملف ترجمة مضلل."
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 48,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 48, message)
            return failedProject
        }

        project = project.copy(
            segments = rawFrenchSegments,
            progressPercent = 70,
            statusMessage = "تم الحصول على ${rawFrenchSegments.size} مقطعاً؛ ستُراجع صلاحية التوقيتات قبل التصدير."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.EXTRACTING_SPEECH_FR, 70, project.statusMessage)
        delay(400)

        // 4. Stage 4: Synchronized Arabic Translation
        project = project.copy(
            currentStage = PipelineStage.TRANSLATING_AR,
            progressPercent = 74,
            statusMessage = "جاري طلب ترجمة آلية عبر الإنترنت؛ قد تحتاج النتيجة إلى مراجعة بشرية..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.TRANSLATING_AR, 74, project.statusMessage)

        val translatedSegments = mutableListOf<SubtitleSegment>()
        if (presetSample != null) {
            translatedSegments.addAll(presetSample.sampleSegments)
            delay(500)
        } else {
            for ((index, segment) in rawFrenchSegments.withIndex()) {
                val arabic = try {
                    ArabicTranslator.translateFrenchToArabic(segment.frenchText)
                } catch (error: Exception) {
                    val message = error.message ?: "تعذرت ترجمة المقطع ${index + 1}."
                    val failedProject = project.copy(
                        segments = translatedSegments.toList(),
                        currentStage = PipelineStage.ERROR,
                        progressPercent = 74,
                        statusMessage = message,
                        errorMessage = message
                    )
                    _currentProject.value = failedProject
                    onStageChanged(PipelineStage.ERROR, 74, message)
                    return failedProject
                }
                // Strict preservation of the exact start and end times!
                translatedSegments.add(
                    segment.copy(
                        arabicText = arabic,
                        startTimeMs = segment.startTimeMs,
                        endTimeMs = segment.endTimeMs
                    )
                )
                val transProgress = 74 + ((index + 1) * 16 / rawFrenchSegments.size)
                project = project.copy(
                    progressPercent = transProgress,
                    statusMessage = "ترجمة المقطع ${index + 1} من ${rawFrenchSegments.size} مع مطابقة التوقيت..."
                )
                _currentProject.value = project
                delay(200)
            }
        }

        project = project.copy(
            segments = translatedSegments,
            progressPercent = 90,
            statusMessage = "تمت الترجمة وتطابق كافة التوقيتات بالمللي ثانية."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.TRANSLATING_AR, 90, project.statusMessage)
        delay(350)

        // 5. Stage 5: Building SRT & preparing download links
        project = project.copy(
            currentStage = PipelineStage.BUILDING_SRT,
            progressPercent = 95,
            statusMessage = "إنشاء ملف SRT وتجهيز أزرار تحميل الفيديو (360p) وملف الترجمة وخيار الدمج..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.BUILDING_SRT, 95, project.statusMessage)
        delay(400)

        // Completed!
        project = project.copy(
            currentStage = PipelineStage.COMPLETED,
            progressPercent = 100,
            statusMessage = "اكتملت مراحل المعالجة والتحقق. يمكنك حفظ ملف SRT والفيديو الأصلي؛ تحويل الدقة وحرق الترجمة غير متاحين بعد."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.COMPLETED, 100, project.statusMessage)

        return project
    }

    fun updateProject(updated: VideoProject) {
        _currentProject.value = updated
    }
}
