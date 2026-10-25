package com.example.data.model

enum class PlatformType(val displayName: String, val iconResName: String) {
    FACEBOOK("فيسبوك Facebook", "ic_facebook"),
    YOUTUBE("يوتيوب YouTube", "ic_youtube"),
    DIRECT_URL("رابط مباشر Direct URL", "ic_link"),
    LOCAL_FILE("ملف محلي Local Video", "ic_file")
}

enum class PipelineStage(
    val stageNumber: Int,
    val arabicTitle: String,
    val frenchTitle: String,
    val description: String
) {
    IDLE(0, "جاهز للبدء", "Prêt", "أدخل رابط الفيديو لبدء التحميل والمعالجة"),
    VALIDATING_URL(1, "التحقق من الرابط", "Validation de l'URL", "التحقق من الرابط والتأكد من إمكانية الوصول إلى الفيديو"),
    DOWNLOADING_VIDEO_360P(2, "تحميل الفيديو بدقة 360p", "Téléchargement MP4 360p", "تنزيل ملف الفيديو بالكامل بصيغة MP4 وبدقة 360p لتوفير البيانات والسرعة"),
    EXTRACTING_SPEECH_FR(3, "تفريغ الصوت الفرنسي بدقة Netflix", "Transcription vocale Studio", "تفريغ الصوت إلى سكريبت فرنسي بأختام زمنية ومقاطع متناسقة"),
    TRANSLATING_AR(4, "الترجمة العربية المتزامنة", "Traduction arabe synchronisée", "ترجمة العبارات إلى العربية الفصيحة مع الحفاظ الدقيق على توقيت البداية والنهاية"),
    BUILDING_SRT(5, "إنشاء ملف SRT وتجهيز التنزيل", "Génération SRT & Export", "بناء ملف الترجمة القياسي وتجهيز روابط تحميل الفيديو والـ SRT وخيار الدمج"),
    COMPLETED(6, "اكتملت المعالجة بنجاح", "Terminé avec succès", "الفيديو وملف الترجمة جاهزان للتحميل والمعاينة والدمج"),
    ERROR(-1, "حدث خطأ في المعالجة", "Erreur", "تعذرت معالجة الفيديو في إحدى المراحل. يمكنك إعادة المحاولة")
}

data class VideoProject(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val platform: PlatformType,
    val durationSeconds: Int,
    val thumbnailUrl: String? = null,
    val currentStage: PipelineStage = PipelineStage.IDLE,
    val progressPercent: Int = 0,
    val statusMessage: String = "",
    val segments: List<SubtitleSegment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val videoQuality: String = "360p (SD - 640x360)",
    val videoFileSizeMb: Double = 6.4,
    val localVideoPath: String? = null,
    val isHardsubMerged: Boolean = false,
    val audioBitrate: String = "128 kbps (AAC)",
    val detectedLanguage: String = "Français (fr-FR)",
    val errorMessage: String? = null
) {
    val isCompleted: Boolean
        get() = currentStage == PipelineStage.COMPLETED

    val isProcessing: Boolean
        get() = currentStage in listOf(
            PipelineStage.VALIDATING_URL,
            PipelineStage.DOWNLOADING_VIDEO_360P,
            PipelineStage.EXTRACTING_SPEECH_FR,
            PipelineStage.TRANSLATING_AR,
            PipelineStage.BUILDING_SRT
        )

    val srtContent: String
        get() = buildString {
            segments.forEachIndexed { index, segment ->
                append(segment.toSrtBlock(index + 1, preferArabic = true))
                append("\n")
            }
        }

    val bilingualSrtContent: String
        get() = buildString {
            segments.forEachIndexed { index, segment ->
                append("${index + 1}\n")
                append("${segment.formattedTimeRange}\n")
                append("${segment.arabicText}\n")
                append("(${segment.frenchText})\n\n")
            }
        }
}
