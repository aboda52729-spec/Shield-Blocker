package com.example

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.data.ShieldDatabase
import com.example.data.ShieldRepository
import com.example.data.ShieldSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeSettings: ShieldSettings? = null
    private var lastBlockedTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this

        val db = ShieldDatabase.getDatabase(this)
        val repository = ShieldRepository(db.shieldDao())
        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                activeSettings = settings
            }
        }
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
    }

    private fun isPackageWhitelisted(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lowerPkg = pkg.lowercase()
        return lowerPkg == packageName.lowercase() ||
               lowerPkg == "com.android.systemui" ||
               lowerPkg == "com.google.android.packageinstaller" ||
               lowerPkg == "com.android.packageinstaller" ||
               lowerPkg == "com.google.android.gms" ||
               lowerPkg.contains("launcher") ||
               lowerPkg.contains("home") ||
               lowerPkg.contains("keyboard") ||
               lowerPkg.contains("inputmethod")
    }

    private fun isPublicSocialMediaApp(pkg: String): Boolean {
        val lowerPkg = pkg.lowercase()
        return lowerPkg == "com.facebook.katana" || // Facebook
               lowerPkg == "com.facebook.lite" || // Facebook Lite
               lowerPkg == "com.instagram.android" || // Instagram
               lowerPkg == "com.instagram.lite" || // Instagram Lite
               lowerPkg == "com.zhiliaoapp.musically" || // TikTok
               lowerPkg == "com.ss.android.ugc.trill" || // TikTok Lite
               lowerPkg == "com.twitter.android" || // X/Twitter
               lowerPkg == "com.twitter.android.lite" || // X/Twitter Lite
               lowerPkg == "com.snapchat.android" || // Snapchat
               lowerPkg == "com.reddit.frontpage" || // Reddit
               lowerPkg == "com.tumblr" || // Tumblr
               lowerPkg == "com.pinterest" || // Pinterest
               lowerPkg == "com.tinder" || // Tinder
               lowerPkg == "com.badoo.mobile" || // Badoo
               lowerPkg == "com.bumble.app" || // Bumble
               lowerPkg == "net.lovoo.android" || // Lovoo
               lowerPkg == "com.okcupid.okcupid" // OkCupid
               // Note: Excluded messenger-only apps (WhatsApp, Telegram, Messenger)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val eventPkg = event.packageName?.toString() ?: ""
        val now = System.currentTimeMillis()
        val settings = activeSettings

        // 1. SECURE UNINSTALL LOCK DETECTION
        val isAdminLocked = settings?.isAdminLockActive == true && now < settings.adminLockEndTimestampMs
        if (isAdminLocked && (eventPkg == "com.android.settings" || eventPkg == "com.google.android.packageinstaller" || eventPkg == "com.android.packageinstaller")) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val targets = listOf("com.example", "shield blocker", "درع الحماية", "حاجب الدروع", "shield device protections")
                if (hasNodeWithText(rootNode, targets)) {
                    rootNode.recycle()
                    // Instantly bounce home
                    performBlockRedirect()
                    // Launch app instantly to cover Settings and reset states
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                    } catch (e: Exception) {}
                    return
                }
                rootNode.recycle()
            }
        }

        // Standard Whitelist filtering for normal browsing scans
        if (isPackageWhitelisted(eventPkg) || eventPkg == "com.android.settings") {
            return
        }

        // Verify active root
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val activePkg = activeRoot.packageName?.toString() ?: ""
            if (isPackageWhitelisted(activePkg)) {
                activeRoot.recycle()
                return
            }
            activeRoot.recycle()
        }

        val source = event.source ?: return
        val currentSettings = settings ?: run {
            source.recycle()
            return
        }

        val isShieldActiveDirect = currentSettings.isShieldActive && now < currentSettings.shieldEndTimestampMs
        if (!isShieldActiveDirect) {
            source.recycle()
            return
        }

        // 2. Strict Social Media Blocking
        if (isPublicSocialMediaApp(eventPkg)) {
            if (now - lastBlockedTime > 2000) {
                lastBlockedTime = now
                performBlockRedirect()
                val isAr = currentSettings.language == "ar"
                val appName = getAppLabel(eventPkg)
                val keyword = if (isAr) "تطبيق تواصل اجتماعي عام" else "Public Social Media"
                showBlockNotification(appName, keyword)
            }
            source.recycle()
            return
        }

        // Keywords list for scanning
        val keywordsList = mutableListOf(
            "porn", "naked", "adult", "xvid", "xnxx", "sex", "hardcore", "xxx", "erotic", "hentai",
            "إباحي", "جنس", "سكس", "بورن", "مواقع إباحية", "موقع جنسي", "مخانيث"
        )
        if (currentSettings.customKeywords.isNotEmpty()) {
            val customs = currentSettings.customKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            keywordsList.addAll(customs)
        }

        // Robust O(N) scan using unified stack traversal
        val violationKeyword = fastContentScan(source, keywordsList)
        source.recycle()

        if (violationKeyword != null) {
            // Prevent spamming
            if (now - lastBlockedTime > 5000) {
                lastBlockedTime = now
                try {
                    // Instantly exit app to home
                    performBlockRedirect()
                    showBlockNotification(getAppLabel(eventPkg), violationKeyword)
                } catch (e: Exception) {}
            }
        }
    }

    override fun onInterrupt() {}

    private val academicKeywords = listOf(
        "study", "academic", "scientific", "medical", "research", "science", "addiction", "harm", "anatomy", "therapy", "education", "treatment",
        "علمي", "أضرار", "علاج", "دراسة", "بحوث", "طبي", "جامعة", "أكاديمي", "أثر", "أضرار الإباحية", "مخاطر", "وقاية"
    )

    private fun fastContentScan(root: AccessibilityNodeInfo?, negativeKeywords: List<String>): String? {
        if (root == null) return null

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        var foundViolationKeyword: String? = null
        var isExempt = false

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            // Check exemption first
            if (!isExempt) {
                if (academicKeywords.any { text.contains(it) || desc.contains(it) }) {
                    isExempt = true
                    // If we find it's exempt, we can immediately stop scanning and return safe (null)
                    break 
                }
            }

            // Check violations if we haven't already found one
            if (foundViolationKeyword == null) {
                val violation = negativeKeywords.firstOrNull { it.isNotEmpty() && (text.contains(it) || desc.contains(it)) }
                if (violation != null) {
                    foundViolationKeyword = violation
                }
            }

            // If we found a violation but haven't proven it's exempt yet, keep looking for an exemption.
            // But if there's no more children, we'll exit anyway.
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }

        // If it's exempt, then no violation. Else, return the violation if found.
        return if (isExempt) null else foundViolationKeyword
    }

    private fun hasNodeWithText(node: AccessibilityNodeInfo?, targets: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        for (target in targets) {
            if (text.contains(target) || desc.contains(target) || viewId.contains(target)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = hasNodeWithText(child, targets)
            child?.recycle()
            if (found) return true
        }
        return false
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    private fun performBlockRedirect() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shield Blocker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used to warn about inappropriate page closures."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showBlockNotification(appName: String, keyword: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isAr = activeSettings?.language == "ar"
        val title = if (isAr) "تم حظر محتوى مريب!" else "Inappropriate Content Shielded!"
        val actionText = if (isAr) {
            "أغلق درع الحماية نافذة في تطبيق $appName بسبب الكلمة: $keyword"
        } else {
            "Shield activated on $appName due to content matching: $keyword"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(actionText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(BLOCK_NOTIF_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "shield_blocker_channel"
        const val BLOCK_NOTIF_ID = 2026
        var isServiceRunning = false
        var instance: BlockerAccessibilityService? = null

        fun forceTriggerAction() {
            instance?.performBlockRedirect()
        }
    }
}
