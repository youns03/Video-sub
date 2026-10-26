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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, isProcessing: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DeepLSettingsStore(context) }
    var plan by remember { mutableStateOf(store.read().plan) }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (store.read().hasApiKey) "مفتاح محفوظ بأمان" else "لم يتم ضبط المفتاح") }
    var busy by remember { mutableStateOf(false) }
    var downloadingModel by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(WhisperModelManager.selectedModel(context)) }
    var modelReady by remember { mutableStateOf(WhisperModelManager.isReady(context, selectedModel)) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelMessage by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val chooseModel: (WhisperModelManager.ModelSize) -> Unit = { chosen ->
        if (!busy && !isProcessing && chosen != selectedModel) {
            WhisperModelManager.selectModel(context, chosen)
            selectedModel = chosen
            modelReady = WhisperModelManager.isReady(context, chosen)
            modelProgress = 0
            modelMessage = ""
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نموذج Whisper؟") },
            text = { Text("سيُحذف النموذج من الهاتف وستحتاج إلى تنزيله قبل التفريغ التالي.") },
            confirmButton = {
                TextButton(enabled = !busy && !isProcessing, onClick = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        try {
                            val deleted = withContext(Dispatchers.IO) { WhisperModelManager.deleteModel(context) }
                            modelReady = !deleted
                            modelMessage = if (deleted) "تم حذف النموذج." else "تعذر حذف النموذج."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            modelMessage = "تعذر حذف النموذج: ${error.message ?: "خطأ غير معروف."}"
                        } finally {
                            busy = false
                        }
                    }
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
        if (isProcessing) {
            Text(
                "الإعدادات مقفلة أثناء معالجة الفيديو لتجنب تغيير مفتاح الترجمة أو النموذج في منتصف العملية.",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }

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
                    enabled = !busy && !isProcessing,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("خطة DeepL", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.AUTO, { plan = DeepLSettingsStore.Plan.AUTO }, enabled = !busy && !isProcessing); Text("تلقائي — يكتشف نوع المفتاح", Modifier.padding(top = 12.dp)) }
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.FREE, { plan = DeepLSettingsStore.Plan.FREE }, enabled = !busy && !isProcessing); Text("الخطة المجانية", Modifier.padding(top = 12.dp)) }
                    Row { RadioButton(plan == DeepLSettingsStore.Plan.PRO, { plan = DeepLSettingsStore.Plan.PRO }, enabled = !busy && !isProcessing); Text("الخطة المدفوعة", Modifier.padding(top = 12.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = apiKey.isNotBlank() && !busy && !isProcessing, onClick = {
                        busy = true
                        status = "جارٍ حفظ المفتاح واختبار الاتصال..."
                        scope.launch {
                            try {
                                store.save(plan, apiKey)
                                status = try {
                                    "نجح الاتصال: ${DeepLTranslator.testConnection(context)}"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    "حُفظ المفتاح، لكن فشل اختبار الاتصال: ${error.message ?: "تعذر الاتصال بـ DeepL."}"
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = "تعذر حفظ الإعدادات: ${error.message ?: "خطأ غير معروف."}"
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("حفظ واختبار") }
                    OutlinedButton(enabled = store.read().hasApiKey && !busy && !isProcessing, onClick = {
                        busy = true
                        status = "جارٍ اختبار الاتصال..."
                        scope.launch {
                            try {
                                store.savePlan(plan)
                                status = try {
                                    "نجح الاتصال: ${DeepLTranslator.testConnection(context)}"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    "فشل اختبار الاتصال: ${error.message ?: "تعذر الاتصال بـ DeepL."}"
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = "تعذر حفظ الإعدادات: ${error.message ?: "خطأ غير معروف."}"
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("اختبار فقط") }
                }
                Text(status, color = if (status.startsWith("نجح")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نماذج Whisper", style = MaterialTheme.typography.titleLarge)
                Text("اختر التوازن المناسب بين السرعة والدقة:")
                Row {
                    RadioButton(
                        selected = selectedModel == WhisperModelManager.ModelSize.TINY,
                        onClick = { chooseModel(WhisperModelManager.ModelSize.TINY) },
                        enabled = !busy && !isProcessing
                    )
                    Text("Tiny INT8 — نحو 105 MB، أسرع وأخف", Modifier.padding(top = 12.dp))
                }
                Row {
                    RadioButton(
                        selected = selectedModel == WhisperModelManager.ModelSize.BASE,
                        onClick = { chooseModel(WhisperModelManager.ModelSize.BASE) },
                        enabled = !busy && !isProcessing
                    )
                    Text("Base INT8 — نحو 170 MB، دقة أفضل غالبًا", Modifier.padding(top = 12.dp))
                }
                Text("النموذج المحدد: ${WhisperModelManager.modelDisplayName(context)}")
                Text("الحجم التقريبي: ${WhisperModelManager.modelApproximateSizeMb(context)} MB — المساحة المتاحة: ${WhisperModelManager.availableFreeMb(context)} MB")
                Text(if (modelReady) "الحالة: مكتمل ومتحقق" else "الحالة: غير منزّل")
                if (downloadingModel && modelProgress > 0) LinearProgressIndicator(progress = { modelProgress / 100f }, Modifier.fillMaxWidth())
                if (modelMessage.isNotBlank()) Text(modelMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && !isProcessing && !modelReady, onClick = {
                        busy = true
                        downloadingModel = true
                        modelProgress = 0
                        modelMessage = "جارٍ بدء تنزيل النموذج..."
                        scope.launch {
                            try {
                                WhisperModelManager.ensureModel(context) { progress, message ->
                                    withContext(Dispatchers.Main.immediate) {
                                        modelProgress = progress
                                        modelMessage = message
                                    }
                                }
                                modelReady = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                modelMessage = error.message ?: "فشل تنزيل النموذج؛ تحقق من الاتصال والمساحة ثم أعد المحاولة."
                            } finally {
                                busy = false
                                downloadingModel = false
                            }
                        }
                    }) { Text(if (modelReady) "تم التنزيل" else "تنزيل النموذج") }
                    OutlinedButton(enabled = !busy && !isProcessing && modelReady, onClick = { confirmDelete = true }) { Text("حذف") }
                }
            }
        }
    }
}
