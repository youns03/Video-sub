package com.example.service

import com.example.data.model.PlatformType
import java.net.URI

data class UrlValidationResult(
    val isValid: Boolean,
    val platform: PlatformType,
    val cleanUrl: String,
    val titleSuggestion: String,
    val isPotentiallyPrivate: Boolean = false,
    val errorMessage: String? = null
)

object UrlValidator {

    fun validate(input: String): UrlValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult(
                isValid = false,
                platform = PlatformType.DIRECT_URL,
                cleanUrl = "",
                titleSuggestion = "",
                errorMessage = "الرجاء إدخال أو لصق رابط الفيديو أولاً"
            )
        }

        // Check if local file path
        if (trimmed.startsWith("content://") || trimmed.startsWith("file://") || trimmed.endsWith(".mp4") && !trimmed.startsWith("http")) {
            return UrlValidationResult(
                isValid = true,
                platform = PlatformType.LOCAL_FILE,
                cleanUrl = trimmed,
                titleSuggestion = "فيديو محلي مُختار (${trimmed.substringAfterLast('/').take(20)})"
            )
        }

        val urlLower = trimmed.lowercase()

        // Check HTTP / HTTPS protocol
        if (!urlLower.startsWith("http://") && !urlLower.startsWith("https://")) {
            return UrlValidationResult(
                isValid = false,
                platform = PlatformType.DIRECT_URL,
                cleanUrl = trimmed,
                titleSuggestion = "",
                errorMessage = "الرابط غير صحيح، يجب أن يبدأ بـ https:// أو http://"
            )
        }

        return try {
            val uri = URI(trimmed)
            val host = (uri.host ?: "").lowercase()

            val isFacebookHost = host == "facebook.com" || host.endsWith(".facebook.com") ||
                    host == "fb.watch" || host.endsWith(".fb.watch") ||
                    host == "fb.com" || host.endsWith(".fb.com")
            val isYouTubeHost = host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "youtu.be" || host.endsWith(".youtu.be")

            when {
                isFacebookHost -> {
                    val isPrivate = urlLower.contains("groups/") && !urlLower.contains("public") ||
                            urlLower.contains("story.php") ||
                            urlLower.contains("login")
                    val isReel = urlLower.contains("/reel/")
                    val isWatch = urlLower.contains("/watch")
                    val suggestion = when {
                        isReel -> "مقطع ريلز فيسبوك (Reel)"
                        isWatch -> "فيديو فيسبوك ووتش (Watch)"
                        else -> "فيديو فيسبوك (Facebook Video)"
                    }
                    UrlValidationResult(
                        isValid = true,
                        platform = PlatformType.FACEBOOK,
                        cleanUrl = trimmed,
                        titleSuggestion = suggestion,
                        isPotentiallyPrivate = isPrivate,
                        errorMessage = if (isPrivate) "تنبيه: يبدو أن هذا الفيديو قد يتطلب تسجيل الدخول أو في مجموعة خاصة. يرجى التأكد من أن الفيديو عام (Public)." else null
                    )
                }

                isYouTubeHost -> {
                    val isShorts = urlLower.contains("/shorts/")
                    val suggestion = if (isShorts) "يوتيوب شورتس (Shorts)" else "فيديو يوتيوب (YouTube Video)"
                    UrlValidationResult(
                        isValid = true,
                        platform = PlatformType.YOUTUBE,
                        cleanUrl = trimmed,
                        titleSuggestion = suggestion
                    )
                }

                urlLower.endsWith(".mp4") || urlLower.endsWith(".webm") || urlLower.endsWith(".m3u8") ||
                        urlLower.endsWith(".mov") || urlLower.endsWith(".m4a") || urlLower.endsWith(".mp3") -> {
                    UrlValidationResult(
                        isValid = true,
                        platform = PlatformType.DIRECT_URL,
                        cleanUrl = trimmed,
                        titleSuggestion = "فيديو مباشر (${trimmed.substringAfterLast('/').take(20)})"
                    )
                }

                // General web video page
                else -> {
                    UrlValidationResult(
                        isValid = true,
                        platform = PlatformType.DIRECT_URL,
                        cleanUrl = trimmed,
                        titleSuggestion = "فيديو من الويب (${host})",
                        isPotentiallyPrivate = false
                    )
                }
            }
        } catch (e: Exception) {
            UrlValidationResult(
                isValid = false,
                platform = PlatformType.DIRECT_URL,
                cleanUrl = trimmed,
                titleSuggestion = "",
                errorMessage = "صيغة الرابط غير صالحة. تأكد من نسخ الرابط كاملاً."
            )
        }
    }
}
