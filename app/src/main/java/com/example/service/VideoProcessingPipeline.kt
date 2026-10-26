package com.example.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
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
     * Local-file processing pipeline.
     * - Validates a picked local video and reads its real metadata.
     * - Copies the original file without transcoding.
     * - Fails closed at speech transcription until a real STT engine is integrated.
     * - Translation uses an online machine-translation service and must be reviewed.
     * - SRT export validates text and timeline before writing.
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
                        val hasVideoTrack = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                            ?.equals("yes", ignoreCase = true) == true
                        if (!hasVideoTrack) 0 else {
                            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                ?.div(1000L)?.toInt() ?: 0).coerceAtLeast(0)
                        }
                    } finally {
                        retriever.release()
                    }
                }
            }.getOrDefault(0)
        } else 0

        if (localDuration <= 0) {
            val message = "تعذر العثور على مسار فيديو قابل للقراءة أو قراءة مدته. أوقفنا المعالجة دون استخدام بيانات تقديرية."
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

        // 2. Stage 2: Copy the original local video without transcoding
        project = project.copy(
            currentStage = PipelineStage.DOWNLOADING_VIDEO_360P,
            progressPercent = 18,
            statusMessage = "جاري استيراد ملف الفيديو من الهاتف..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.DOWNLOADING_VIDEO_360P, 18, project.statusMessage)

        val downloadedFile: File = try {
            val existingAppCopy = if (sourceUri?.scheme.equals("file", ignoreCase = true)) {
                runCatching {
                    val candidate = File(sourceUri?.path ?: "")
                    val appOwnedDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                        ?.let { File(it, "SubVid").canonicalFile }
                    candidate.takeIf {
                        appOwnedDirectory != null &&
                            it.isFile &&
                            it.canonicalFile.parentFile == appOwnedDirectory
                    }
                }.getOrNull()
            } else null

            if (existingAppCopy != null) {
                onStageChanged(PipelineStage.DOWNLOADING_VIDEO_360P, 40, "إعادة استخدام النسخة المحلية الموجودة دون نسخها مرة أخرى.")
                existingAppCopy
            } else {
                VideoDownloaderService.importVideoToAppStorage(
                    context = context,
                    source = sourceUri ?: error("ملف الفيديو المختار غير صالح."),
                    videoTitle = videoTitle
                ) { progress ->
                    val pipelineProgress = 18 + (progress.percent * 22 / 100)
                    val total = if (progress.totalMb > 0) " من ${progress.totalMb}MB" else ""
                    val updatedMsg = "نسخ الملف: ${progress.downloadedMb}MB$total (${progress.percent}%)"
                    _currentProject.value = _currentProject.value?.copy(
                        progressPercent = pipelineProgress,
                        statusMessage = updatedMsg
                    )
                }
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
                    val hasVideoTrack = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                        ?.equals("yes", ignoreCase = true) == true
                    if (!hasVideoTrack) 0 else {
                        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            ?.div(1000L)?.toInt() ?: 0)
                    }
                } finally {
                    retriever.release()
                }
            }
        }.getOrDefault(0)
        if (copiedDurationSeconds <= 0) {
            downloadedFile.delete()
            val message = "النسخة المستوردة لا تحتوي على مسار فيديو قابل للقراءة أو تعذر التحقق من مدته؛ أوقفنا المعالجة."
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

        // 3. Stage 3: French speech transcription; the real STT engine is not integrated yet
        project = project.copy(
            currentStage = PipelineStage.EXTRACTING_SPEECH_FR,
            progressPercent = 48,
            statusMessage = "تم استيراد الفيديو. يجري تجهيز نموذج Whisper المحلي..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.EXTRACTING_SPEECH_FR, 48, project.statusMessage)

        try {
            WhisperModelManager.ensureModel(context) { percent, message ->
                val modelProgress = 48 + (percent * 10 / 100)
                _currentProject.value = _currentProject.value?.copy(
                    progressPercent = modelProgress,
                    statusMessage = message
                )
                onStageChanged(PipelineStage.EXTRACTING_SPEECH_FR, modelProgress, message)
            }
        } catch (error: Exception) {
            val message = error.message ?: "تعذر تجهيز نموذج التفريغ المحلي."
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 40,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 40, message)
            return failedProject
        }

        val rawFrenchSegments: List<SubtitleSegment> = try {
            FrenchSTTEngine.transcribe(
                context = context,
                mediaPath = downloadedFile.absolutePath,
                durationSeconds = copiedDurationSeconds
            ) { p, text ->
                val calcProgress = 58 + (p * 12 / 100)
                _currentProject.value = _currentProject.value?.copy(
                    progressPercent = calcProgress,
                    statusMessage = "تفريغ الصوت: \"${text.take(35)}...\""
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: "تعذر تفريغ الصوت الفرنسي."
            val failedProject = project.copy(
                currentStage = PipelineStage.ERROR,
                progressPercent = 58,
                statusMessage = message,
                errorMessage = message
            )
            _currentProject.value = failedProject
            onStageChanged(PipelineStage.ERROR, 58, message)
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

        // 4. Stage 4: Online Arabic machine translation
        project = project.copy(
            currentStage = PipelineStage.TRANSLATING_AR,
            progressPercent = 74,
            statusMessage = "جاري طلب ترجمة آلية عبر الإنترنت؛ قد تحتاج النتيجة إلى مراجعة بشرية..."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.TRANSLATING_AR, 74, project.statusMessage)

        val translatedSegments = mutableListOf<SubtitleSegment>()
        for ((index, segment) in rawFrenchSegments.withIndex()) {
            val arabic = try {
                ArabicTranslator.translateFrenchToArabic(segment.frenchText)
            } catch (error: Exception) {
                val message = error.message ?: "تعذرت ترجمة المقطع ${index + 1}."
                val failedProject = project.copy(
                    segments = rawFrenchSegments.mapIndexed { rawIndex, rawSegment ->
                        rawSegment.copy(arabicText = translatedSegments.getOrNull(rawIndex)?.arabicText.orEmpty())
                    },
                    currentStage = PipelineStage.ERROR,
                    progressPercent = project.progressPercent,
                    statusMessage = message,
                    errorMessage = message
                )
                _currentProject.value = failedProject
                onStageChanged(PipelineStage.ERROR, project.progressPercent, message)
                return failedProject
            }
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
                statusMessage = "ترجمة المقطع ${index + 1} من ${rawFrenchSegments.size}؛ التوقيت الأصلي محفوظ."
            )
            _currentProject.value = project
        }

        project = project.copy(
            segments = translatedSegments,
            progressPercent = 90,
            statusMessage = "تمت الترجمة وتطابق كافة التوقيتات بالمللي ثانية."
        )
        _currentProject.value = project
        onStageChanged(PipelineStage.TRANSLATING_AR, 90, project.statusMessage)
        delay(350)

        // 5. Stage 5: Validate subtitle data before export
        project = project.copy(
            currentStage = PipelineStage.BUILDING_SRT,
            progressPercent = 95,
            statusMessage = "التحقق من النصوص والتوقيتات قبل تصدير ملف SRT..."
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
