package io.github.sospd.quick

import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sospd.SospdApplication
import io.github.sospd.data.PreferencesRepository
import io.github.sospd.domain.Models.ScanRegion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickScoutTest {
    @Test fun bundledOcrReadsStandardCodesOnBothBackgroundsAndRejectsDigits() = runBlocking {
        val ocr = SeedOcr()
        try {
            for ((text, dark) in listOf("ABC-DEF-GHI" to true, "ZZZ-ZZZ-ZZZ" to false, "666" to false)) {
                val bitmap = Bitmap.createBitmap(800, 130, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(if (dark) Color.BLACK else Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (dark) Color.WHITE else Color.BLACK; textSize = 70f; typeface = Typeface.MONOSPACE
                    }
                    canvas.drawText(text, 35f, 90f, paint)
                    assertEquals(if (text == "666") emptyList<String>() else listOf(text), ocr.recognize(bitmap))
                } finally { bitmap.recycle() }
            }
        } finally { ocr.close() }
    }

    @Test fun selectedRegionSurvivesRepositoryReload() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SospdApplication>()
        val value = ScanRegion(0.05, 0.24, 0.95, 0.34, 1080, 2400)
        app.container.preferences.saveScanRegion(value)
        assertEquals(value, PreferencesRepository(app).scanRegion.first())
        assertTrue(value.matchesDisplay(720, 1600))
        assertFalse(value.matchesDisplay(2400, 1080))
    }
}
