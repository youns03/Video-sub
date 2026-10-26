package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.service.DeepLSettingsStore
import com.example.service.DeepLTranslator
import com.example.service.WhisperModelManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DeepLSettingsStore(context) }
    var plan by remember { mutableStateOf(store.read().plan) }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (store.read().hasApiKey) "مفتاح محفوظ بأمان" else "لم يتم ضبط المفتاح") }
    var busy by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(WhisperModelManager.isReady(context)) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelMessage by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نموذج Whisper؟") },
            text = { Text("سيُحذف النموذج من الهاتف وستحتاج إلى تنزيله قبل التفريغ التالي.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    modelReady = !WhisperModelManager.deleteModel(context)
                    modelMessage = if (modelReady) "تعذر حذف النموذج." else "تم حذف النموذج."
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        Text("الترجمة تحتاج إلى الإنترنت، أما تفريغ الصوت فيعمل محليًا بعد تنزيل النموذج.", style = MaterialTheme.typography.bodyMedium)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إعدادات الترجمة", style = MaterialTheme.typography.titleLarge)
                Text("المحرّك: DeepL — الفرنسية إلى العربية")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مفتاح DeepL API") },
                    placeholder = { Text(if (store.read().hasApiKey) "مفتاح محفوظ؛ أدخل قيمة جديدة للتبديل" else "الصق المفتاح هنا") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("خطة DeepL", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.AUTO, { plan = DeepLSettingsStore.Plan.AUTO }); Text("تلقائي — يكتشف نوع المفتاح", Modifier.padding(top = 12.dp)) }
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.FREE, { plan = DeepLSettingsStore.Plan.FREE }); Text("الخطة المجانية", Modifier.padding(top = 12.dp)) }
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.PRO, { plan = DeepLSettingsStore.Plan.PRO }); Text("الخطة المدفوعة", Modifier.padding(top = 12.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = apiKey.isNotBlank() && !busy, onClick = {
                        busy = true
                        status = "جارٍ حفظ المفتاح واختبار الاتصال..."
                        store.save(plan, apiKey)
                        scope.launch {
                            status = runCatching { "نجح الاتصال: ${DeepLTranslator.testConnection(context)}" }
                                .getOrElse { it.message ?: "فشل اتصال DeepL" }
                            busy = false
                        }
                    }) { Text("حفظ واختبار") }
                    OutlinedButton(enabled = store.read().hasApiKey && !busy, onClick = {
                        busy = true
                        scope.launch {
                            status = runCatching { "نجح الاتصال: ${DeepLTranslator.testConnection(context)}" }
                                .getOrElse { it.message ?: "فشل اتصال DeepL" }
                            busy = false
                        }
                    }) { Text("اختبار فقط") }
                }
                Text(status, color = if (status.startsWith("نجح")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نموذج Whisper المتوافق", style = MaterialTheme.typography.titleLarge)
                Text("النموذج المتاح حاليًا: ${WhisperModelManager.modelDisplayName()}")
                Text("الحجم التقريبي: ${WhisperModelManager.modelApproximateSizeMb()} MB — المساحة المتاحة: ${WhisperModelManager.availableFreeMb(context)} MB")
                Text(if (modelReady) "الحالة: مكتمل ومتحقق" else "الحالة: غير منزّل")
                if (busy && modelProgress > 0) LinearProgressIndicator(progress = { modelProgress / 100f }, Modifier.fillMaxWidth())
                if (modelMessage.isNotBlank()) Text(modelMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && !modelReady, onClick = {
                        busy = true
                        modelProgress = 0
                        scope.launch {
                            runCatching {
                                WhisperModelManager.ensureModel(context) { progress, message ->
                                    modelProgress = progress
                                    modelMessage = message
                                }
                                modelReady = true
                            }.onFailure { modelMessage = it.message ?: "فشل تنزيل النموذج" }
                            busy = false
                        }
                    }) { Text(if (modelReady) "تم التنزيل" else "تنزيل النموذج") }
                    OutlinedButton(enabled = !busy && modelReady, onClick = { confirmDelete = true }) { Text("حذف") }
                }
            }
        }
    }
}
