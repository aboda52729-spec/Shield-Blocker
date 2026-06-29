package com.example

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.data.ShieldDatabase
import kotlinx.coroutines.runBlocking

class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val settings = runBlocking {
            try {
                ShieldDatabase.getDatabase(context).shieldDao().getSettingsDirect()
            } catch (e: Exception) {
                null
            }
        }
        val isAdminLocked = settings?.isAdminLockActive == true && System.currentTimeMillis() < settings.adminLockEndTimestampMs
        if (isAdminLocked) {
            // Trigger instant redirect to cover Settings
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            } catch (e: Exception) {}

            val isAr = settings?.language == "ar"
            return if (isAr) {
                "درع الحماية نشط ومؤمّن! لا يمكن إلغاء تفعيل مشرف الجهاز طوال فترة قفل الحماية الصارمة."
            } else {
                "Shield protection is active and secured! Device administration cannot be deactivated until the lock duration expires."
            }
        }
        return super.onDisableRequested(context, intent)
    }
}
