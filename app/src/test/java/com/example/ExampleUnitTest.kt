package com.example

import com.example.model.ExtractedInfo
import com.example.model.MemoTimeFormatter
import com.example.model.RecorderState
import com.example.model.RecorderStatus
import com.example.model.Recording
import com.example.service.VoiceNoteForegroundService
import com.example.ui.screens.ConversationSortOrder
import com.example.ui.screens.ConversationStatusFilter
import com.example.ui.screens.filterAndSortRecordings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

    @Test
    fun `foreground service actions and channel constants are distinct and non-empty`() {
        val actions = setOf(
            VoiceNoteForegroundService.ACTION_START,
            VoiceNoteForegroundService.ACTION_PAUSE,
            VoiceNoteForegroundService.ACTION_RESUME,
            VoiceNoteForegroundService.ACTION_STOP_AND_SAVE,
            VoiceNoteForegroundService.ACTION_DISCARD
        )
        assertEquals(5, actions.size)
        assertTrue(VoiceNoteForegroundService.NOTIFICATION_ID > 0)
        assertTrue(VoiceNoteForegroundService.CHANNEL_ID.isNotBlank())
    }

    @Test
    fun `recorder state defaults reflect idle foreground service state`() {
        val state = RecorderState()
        assertEquals(RecorderStatus.IDLE, state.status)
        assertFalse(state.isForegroundServiceActive)
        assertTrue(state.isCameraPreviewEnabled)
        assertEquals(0, state.capturedFrameCount)
        assertEquals("01:05", MemoTimeFormatter.formatTimestamp(65_000L))
    }

    @Test
    fun `manifest declares camera audio foreground service permissions and service type`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Manifest file must exist at ${manifest.absolutePath}", manifest.exists())
        val content = manifest.readText()
        assertTrue(content.contains("android.permission.CAMERA"))
        assertTrue(content.contains("android.permission.RECORD_AUDIO"))
        assertTrue(content.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(content.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(content.contains("android.permission.FOREGROUND_SERVICE_CAMERA"))
        assertTrue(content.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(content.contains(".service.VoiceNoteForegroundService"))
        assertTrue(content.contains("android:foregroundServiceType=\"microphone|camera\""))
    }

    @Test
    fun `filterAndSortRecordings filters by query category status and sort order`() {
        val rec1 = Recording(
            id = "rec_1",
            title = "Kitchen Contractor Quote",
            category = "Finance",
            tags = listOf("renovation", "budget"),
            durationMs = 42_000L,
            recordedAtMs = 1_000L,
            transcriptStatus = "COMPLETED",
            fullTranscript = "The total estimate is 45000 rupees."
        )
        val rec2 = Recording(
            id = "rec_2",
            title = "Chennai Office Standup",
            category = "Work",
            tags = listOf("sprint"),
            durationMs = 120_000L,
            recordedAtMs = 3_000L,
            transcriptStatus = "PENDING",
            summaryLocations = "Guindy Office"
        )
        val rec3 = Recording(
            id = "rec_3",
            title = "Grocery Run",
            category = "Errands",
            durationMs = 15_000L,
            recordedAtMs = 2_000L,
            transcriptStatus = "COMPLETED"
        )
        val extracted = listOf(
            ExtractedInfo(
                id = "info_1",
                recordingId = "rec_2",
                label = "Deadline",
                value = "Friday 5 PM"
            )
        )
        val list = listOf(rec1, rec2, rec3)

        val newestAll = filterAndSortRecordings(
            recordings = list,
            extractedInfo = extracted,
            transcribingIds = emptySet(),
            searchQuery = "",
            selectedCategory = "All",
            selectedStatus = ConversationStatusFilter.ALL,
            sortOrder = ConversationSortOrder.NEWEST
        )
        assertEquals(listOf("rec_2", "rec_3", "rec_1"), newestAll.map { it.id })

        val workByExtracted = filterAndSortRecordings(
            recordings = list,
            extractedInfo = extracted,
            transcribingIds = emptySet(),
            searchQuery = "Friday",
            selectedCategory = "Work",
            selectedStatus = ConversationStatusFilter.PENDING,
            sortOrder = ConversationSortOrder.NEWEST
        )
        assertEquals(listOf("rec_2"), workByExtracted.map { it.id })

        val longestCompleted = filterAndSortRecordings(
            recordings = list,
            extractedInfo = extracted,
            transcribingIds = emptySet(),
            searchQuery = "",
            selectedCategory = "All",
            selectedStatus = ConversationStatusFilter.TRANSCRIBED,
            sortOrder = ConversationSortOrder.LONGEST
        )
        assertEquals(listOf("rec_1", "rec_3"), longestCompleted.map { it.id })
    }
}
