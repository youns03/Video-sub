package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.SrtGenerator
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryBlue

@Composable
fun TechnicalReportScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val fullReportText = """
# تقرير عام شامل: تطوير تطبيق Android لتحويل فيديوهات Facebook بالفرنسية إلى ترجمة عربية

## 1. فكرة التطبيق والهدف التقني
تطوير تطبيق Android متكامل لمعالجة مقاطع الفيديو من Facebook (Reels, Watch, Posts) باللغة الفرنسية، وتحويل الصوت المنطوق إلى نص وترجمته بدقة إلى العربية وتوليد ملف SRT متزامن دون الاعتماد على Gemini API.

## 2. المراحل السبع الأساسية للتطبيق:
- المرحلة الأولى: إدخال الرابط والتحقق من نوعه وصلاحية الوصول (Facebook, YouTube, Direct).
- المرحلة الثانية: استخراج المسار الصوتي وتحسين جودته وضغطه بمعدل 128 kbps لتقليل البيانات.
- المرحلة الثالثة: تحويل الكلام الفرنسي إلى نص وتحديد التوقيتات الدقيقة لكل مقطع كلامي (VAD & STT).
- المرحلة الرابعة: الترجمة الذكية إلى العربية مع مراعاة السياق وتقسيم الأسطر لتناسب الشاشة.
- المرحلة الخامسة: إنشاء ملف الترجمة SRT المتوافق مع معايير SubRip واتجاه RTL.
- المرحلة السادسة: حفظ ومشاركة الملف مع المشغلات الخارجية وحفظ الفيديو.
- المرحلة السابعة: محرر ومعاينة تفاعلية لتصحيح المقاطع وضبط التوقيتات وإعادة الترجمة.

## 3. المعلومات التقنية الضرورية:
- الوصول لفيديوهات فيسبوك: الفيديوهات العامة (Public) يمكن استخراج مسارها الصوتي عبر الروابط المباشرة أو عبر مكتبات وسيطة؛ بينما تتطلب الروابط الخاصة استيثاقاً.
- دقة التعرف على الكلام: نماذج Whisper و Vosk توفر دقة عالية في تمييز اللهجات الفرنسية وإزالة الضوضاء.
- جودة الترجمة: تجنب الترجمة الحرفية واستبدال علامات الترقيم بالعربية (، و ؟).
- مزامنة التوقيت: حساب التوقيت بالمللي ثانية لضمان ظهور الترجمة في اللحظة المناسبة.
    """.trimIndent()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Article,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "دليل التقرير التقني والمعماري",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Text(
                                text = "دراسة شاملة لآليات المعالجة والتعرف الصوتي والترجمة",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SrtGenerator.copyToClipboard(context, fullReportText, "التقرير التقني")
                            Toast.makeText(context, "تم نسخ التقرير كاملاً إلى الحافظة", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_report_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ التقرير")
                    }

                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "تقرير تطوير تطبيق ترجمة فيديوهات Facebook")
                                putExtra(android.content.Intent.EXTRA_TEXT, fullReportText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "مشاركة التقرير"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_report_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة التقرير")
                    }
                }
            }

            // Section 1: Idea
            item {
                ReportSectionCard(
                    title = "1. فكرة التطبيق والهدف التقني",
                    content = "تطوير تطبيق Android يتيح للمستخدم إدخال رابط فيديو من Facebook (أو YouTube)، ثم معالجة الفيديو واستخراج الكلام الفرنسي المنطوق فيه، وترجمته إلى العربية، وإنشاء ملف ترجمة بصيغة SRT متزامن مع توقيت الكلام الأصلي دون الاعتماد على Gemini API.\nالهدف هو تبسيط العملية كاملة داخل تطبيق واحد، دون الحاجة إلى تنفيذ الخطوات يدويًا أو استخدام تطبيقات متعددة."
                )
            }

            // Section 2: The 7 Stages
            item {
                ReportSectionCard(
                    title = "2. المراحل الأساسية السبع للتطبيق",
                    content = """
• المرحلة الأولى (التحقق من الرابط): استقبال رابط فيسبوك أو يوتيوب، فحص الصلاحية وبروتوكول HTTPS وكشف ما إذا كان الفيديو عاماً أم يحتاج تسجيل دخول.
• المرحلة الثانية (تجهيز واستخراج الصوت): عزل المسار الصوتي وتخفيض معدل البت إلى 128 kbps لتقليل استهلاك باقة البيانات وتسريع الرفع والمعالجة.
• المرحلة الثالثة (استخراج الكلام الفرنسي): تقطيع المقاطع الصوتية بحسب فترات السكون (VAD) وتحويل الصوت إلى نص فرنسي مع أختام زمنية بالمللي ثانية.
• المرحلة الرابعة (ترجمة النص إلى العربية): ترجمة سياقية تحافظ على المعنى، وتجنب استخدام Gemini API، مع تقسيم الجمل لتلائم شاشات الهواتف.
• المرحلة الخامسة (إنشاء ملف الترجمة SRT): صياغة ملف SubRip القياسي بتسلسل أرقام وتوقيتات منضبطة ودعم اتجاه النص من اليمين إلى اليسار (RTL).
• المرحلة السادسة (حفظ النتائج): حفظ الملف في مساحة التخزين (Documents/Subtitles)، وإمكانية المشاركة والفتح عبر مشغلات الفيديو.
• المرحلة السابعة (المعاينة والمحرر التفاعلي): تتيح للمستخدم مراجعة كل مقطع، والتعديل الفوري، وإعادة الترجمة عند وجود خطأ، وتشغيل الفيديو بشكل متزامن.
                    """.trimIndent()
                )
            }

            // Section 3: Technical Constraints
            item {
                ReportSectionCard(
                    title = "3. المعلومات التقنية الضرورية قبل التنفيذ",
                    content = """
• طريقة الوصول لفيديوهات Facebook: الروابط العامة (Public Reels & Watch) يمكن معالجتها مباشرة، بينما تتطلب المقاطع في المجموعات المغلقة ملفات تعريف الارتباط أو توثيق Graph API.
• جودة التعرف الصوتي الفرنسي: نماذج Whisper (OpenAI / HuggingFace) و Vosk توفر دقة فائقة مع اللهجات الفرنسية السريعة والضوضاء.
• جودة الترجمة وملاءمة الترجمة للعين: مراعاة ألا يتجاوز طول السطر 40-42 حرفاً، وتحويل علامات الترقيم للنمط العربي (؟ و ،).
• مزامنة التوقيتات: الاعتماد على توقيتات دقيقة بالصيغة (00:00:00,000) لتفادي أي انزياح زمني أثناء المشاهدة.
• استهلاك الذاكرة والبطارية: معالجة الصوت فقط بدلاً من تنزيل الفيديو الكامل يوفر ما يصل إلى 85% من البيانات ويضمن أداءً خفيفاً على أجهزة أندرويد.
                    """.trimIndent()
                )
            }

            // Section 4: Architecture
            item {
                ReportSectionCard(
                    title = "4. البنية البرمجية المعتمدة في التطبيق",
                    content = """
• واجهة المستخدم: Jetpack Compose مع Material Design 3، ودعم كامل للغة العربية (RTL).
• قاعدة البيانات: Room Database لتخزين المشاريع والمقاطع محلياً للرجوع إليها في أي وقت دون اتصال.
• التصدير والتخزين: Storage Access Framework ودعم مشاركة ملفات SRT و WebVTT.
• محرك المعالجة: خط أنابيب متسلسل (Sequential Pipeline) يعرض نسبة التقدم في كل مرحلة ويتيح إعادة المحاولة دون فقدان البيانات.
                    """.trimIndent()
                )
            }
        }
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PrimaryBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
