package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.service.VoiceNoteForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MemoMind", appName)
    }

    @Test
    fun `manifest declares camera microphone and foreground service types`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(requested.contains(Manifest.permission.CAMERA))
        assertTrue(requested.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_CAMERA))
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))

        val serviceInfo = pm.getServiceInfo(
            ComponentName(context, VoiceNoteForegroundService::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(serviceInfo.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val expectedFlags = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            assertEquals(expectedFlags, serviceInfo.foregroundServiceType and expectedFlags)
        }
    }

    @Test
    fun `foreground service stops cleanly when record audio permission is denied`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        val controller = Robolectric.buildService(
            VoiceNoteForegroundService::class.java,
            Intent(app, VoiceNoteForegroundService::class.java).apply {
                action = VoiceNoteForegroundService.ACTION_START
            }
        ).create().startCommand(0, 0)

        val service = controller.get()
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `foreground service creates notification channel when started`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(VoiceNoteForegroundService::class.java).create()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(VoiceNoteForegroundService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        controller.destroy()
    }
}
