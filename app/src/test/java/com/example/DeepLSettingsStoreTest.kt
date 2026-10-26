package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.DeepLSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeepLSettingsStoreTest {
    @Test
    fun selectedPlanCanBeChangedWithoutReplacingSavedKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DeepLSettingsStore(context)

        store.savePlan(DeepLSettingsStore.Plan.FREE)
        assertEquals(DeepLSettingsStore.Plan.FREE, store.read().plan)

        store.savePlan(DeepLSettingsStore.Plan.PRO)
        assertEquals(DeepLSettingsStore.Plan.PRO, store.read().plan)
    }
}
