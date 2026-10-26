package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.WhisperModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhisperModelManagerTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultModelRemainsBaseForExistingInstallations() {
        WhisperModelManager.selectModel(context, WhisperModelManager.ModelSize.BASE)

        assertEquals(WhisperModelManager.ModelSize.BASE, WhisperModelManager.selectedModel(context))
        assertEquals("Whisper Base INT8 + Silero VAD", WhisperModelManager.modelDisplayName(context))
        assertEquals(170, WhisperModelManager.modelApproximateSizeMb(context))
        assertFalse(WhisperModelManager.isReady(context, WhisperModelManager.ModelSize.BASE))
    }

    @Test
    fun tinyAndBaseSelectionsPersistAndUseSeparateDirectories() {
        WhisperModelManager.selectModel(context, WhisperModelManager.ModelSize.BASE)
        val baseDirectory = WhisperModelManager.modelDir(context).absolutePath

        WhisperModelManager.selectModel(context, WhisperModelManager.ModelSize.TINY)
        assertEquals(WhisperModelManager.ModelSize.TINY, WhisperModelManager.selectedModel(context))
        assertEquals("Whisper Tiny INT8 + Silero VAD", WhisperModelManager.modelDisplayName(context))
        assertEquals(105, WhisperModelManager.modelApproximateSizeMb(context))
        assertTrue(baseDirectory != WhisperModelManager.modelDir(context).absolutePath)

        WhisperModelManager.selectModel(context, WhisperModelManager.ModelSize.BASE)
        assertEquals(baseDirectory, WhisperModelManager.modelDir(context).absolutePath)
    }
}
