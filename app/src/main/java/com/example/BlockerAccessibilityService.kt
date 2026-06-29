package com.example

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationChannel
import android.net.Uri
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

    private lateinit var ourPackageName: String
    private lateinit var ourPackageNameLower: String

    @Volatile
    private var blockRegex: Regex? = null
    @Volatile
    private var exemptRegex: Regex? = null

    // Package-aware content scanning throttle to avoid chocking the main thread
    private val lastContentScanMap = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ourPackageName = packageName
        ourPackageNameLower = packageName.lowercase()
        // Pre-compile patterns with defaults so the protection matches instantly from millisecond zero
        updatePatterns(ShieldSettings())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this

        val db = ShieldDatabase.getDatabase(this)
        val repository = ShieldRepository(db.shieldDao())
        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                activeSettings = settings
                updatePatterns(settings)
            }
        }
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
    }

    private fun updatePatterns(settings: ShieldSettings?) {
        val baseKeywords = listOf(
            "porn", "naked", "adult", "xvid", "xnxx", "sex", "hardcore", "xxx", "erotic", "hentai",
            "إباحي", "جنس", "سكس", "بورن", "مواقع إباحية", "موقع جنسي", "مخانيث"
        )
        val keywordsList = mutableListOf<String>()
        keywordsList.addAll(baseKeywords)

        if (settings != null && settings.customKeywords.isNotEmpty()) {
            val customs = settings.customKeywords.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            keywordsList.addAll(customs)
        }

        try {
            val blockPatternString = keywordsList
                .filter { it.isNotEmpty() }
                .joinToString("|") { Regex.escape(it) }
            blockRegex = if (blockPatternString.isNotEmpty()) {
                Regex(blockPatternString, RegexOption.IGNORE_CASE)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val exemptPatternString = academicKeywords
                .filter { it.isNotEmpty() }
                .joinToString("|") { Regex.escape(it) }
            exemptRegex = if (exemptPatternString.isNotEmpty()) {
                Regex(exemptPatternString, RegexOption.IGNORE_CASE)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isPackageWhitelisted(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lowerPkg = pkg.lowercase()
        return lowerPkg == ourPackageNameLower ||
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

        // Optimization 1: Skip checks for our own app immediately using simple string matching to prevent self-closing
        val lowerEventPkg = eventPkg.lowercase()
        val isOurAppEventPkg = lowerEventPkg.isNotEmpty() && (
            lowerEventPkg == ourPackageNameLower ||
            lowerEventPkg.startsWith("com.aistudio.shieldblocker") ||
            lowerEventPkg.contains("shieldblocker") ||
            lowerEventPkg.startsWith("com.example")
        )
        if (isOurAppEventPkg) {
            return
        }

        // Optimization 2: Fast whitelist verification to skip checks for common safe apps instantly
        if (isPackageWhitelisted(eventPkg)) {
            return
        }

        // Optimization 3: Rate limit TYPE_WINDOW_CONTENT_CHANGED to once per 100ms per package to prevent rendering choking.
        // On TYPE_WINDOW_STATE_CHANGED (app focus swap), we reset the timer to scan instantly.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastContentScanMap.remove(eventPkg)
        } else {
            val lastScan = lastContentScanMap[eventPkg] ?: 0L
            if (now - lastScan < CONTENT_SCAN_DELAY_MS) {
                return
            }
            lastContentScanMap[eventPkg] = now
        }

        // Lazy active window checker to avoid slow cross-process rootInActiveWindow queries
        var isOurAppActiveResult: Boolean? = null
        fun checkIfOurAppIsActive(): Boolean {
            if (isOurAppActiveResult != null) return isOurAppActiveResult!!
            val activeRootPkg = try {
                val root = rootInActiveWindow
                val p = root?.packageName?.toString()?.lowercase() ?: ""
                root?.recycle()
                p
            } catch (e: Exception) {
                ""
            }
            isOurAppActiveResult = activeRootPkg.isNotEmpty() && (
                activeRootPkg == ourPackageNameLower ||
                activeRootPkg.startsWith("com.aistudio.shieldblocker") ||
                activeRootPkg.contains("shieldblocker") ||
                activeRootPkg.startsWith("com.example")
            )
            return isOurAppActiveResult!!
        }

        // 1. SECURE UNINSTALL LOCK DETECTION
        val isAdminLocked = (settings?.isAdminLockActive == true && now < settings.adminLockEndTimestampMs) ||
                (settings?.isStrictMonthActive == true && now < settings.strictMonthEndTimestampMs)
        val isUninstallOrSettingsPkg = lowerEventPkg != ourPackageNameLower && (
                lowerEventPkg.contains("settings") ||
                lowerEventPkg.contains("packageinstaller") ||
                lowerEventPkg.contains("permissioncontroller") ||
                lowerEventPkg.contains("securitycenter") ||
                lowerEventPkg.contains("systemmanager") ||
                lowerEventPkg.contains("uninstaller") ||
                lowerEventPkg.contains("deviceadmin") ||
                lowerEventPkg == "android" ||
                lowerEventPkg == "com.android.systemui"
        )

        // Only block settings/installer package if the lock is active AND our app isn't active
        if (isAdminLocked && isUninstallOrSettingsPkg) {
            if (checkIfOurAppIsActive()) {
                return
            }

            val targets = listOf(
                ourPackageNameLower,
                "com.example",
                "shield blocker",
                "shield blocker service",
                "shield device protections",
                "حاجب الدروع",
                "درع الحماية",
                "حماية الحذف",
                "حماية مشرف الجهاز"
            )

            val actions = listOf(
                "uninstall",
                "force stop",
                "deactivate",
                "disable",
                "clear data",
                "storage & cache",
                "app info",
                "device admin",
                "use service",
                "use shield",
                "shortcut",
                "turn off",
                "إلغاء التثبيت",
                "إلغاء تثبيت",
                "إلغاء تفعيل",
                "إلغاء تنشيط",
                "إيقاف إجباري",
                "فرض الإيقاف",
                "معلومات التطبيق",
                "مسح البيانات",
                "مشرف الجهاز",
                "إيقاف الخدمة",
                "ايقاف الخدمة",
                "استخدم درع",
                "استخدام درع",
                "تمكين",
                "تعطيل"
            )

            var matched = false

            val eventSource = event.source
            if (eventSource != null) {
                if (scanWindowForBlock(eventSource, targets, actions)) {
                    matched = true
                }
                eventSource.recycle()
            }

            if (!matched) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    if (scanWindowForBlock(rootNode, targets, actions)) {
                        matched = true
                    }
                    rootNode.recycle()
                }
            }

            if (!matched) {
                try {
                    val windowList = windows
                    if (windowList.isNotEmpty()) {
                        for (window in windowList) {
                            val wRoot = window.root
                            if (wRoot != null) {
                                if (scanWindowForBlock(wRoot, targets, actions)) {
                                    matched = true
                                    wRoot.recycle()
                                    break
                                }
                                wRoot.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            if (matched) {
                performBlockRedirect(eventPkg)
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(ourPackageName)
                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) {}
                return
            }
        }

        // Verify active root whitelisting before any deep content scanning
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

        val isShieldActiveDirect = (currentSettings.isShieldActive && now < currentSettings.shieldEndTimestampMs) ||
                (currentSettings.isStrictMonthActive && now < currentSettings.strictMonthEndTimestampMs)
        if (!isShieldActiveDirect) {
            source.recycle()
            return
        }

        // 2. Strict Social Media Blocking
        if (isPublicSocialMediaApp(eventPkg)) {
            if (now - lastBlockedTime > 2000) {
                lastBlockedTime = now
                performBlockRedirect(eventPkg)
                val isAr = currentSettings.language == "ar"
                val appName = getAppLabel(eventPkg)
                val keyword = if (isAr) "تطبيق تواصل اجتماعي عام" else "Public Social Media"
                showBlockNotification(appName, keyword)
            }
            source.recycle()
            return
        }

        // Robust scan using pre-compiled regex directly matching CharSequence structures
        val violationKeyword = fastContentScan(source)
        source.recycle()

        if (violationKeyword != null) {
            if (now - lastBlockedTime > 5000) {
                lastBlockedTime = now
                try {
                    performBlockRedirect(eventPkg)
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

    // Extremely fast, allocation-free recursive tree scan with native Regex engine matching on CharSequence
    private fun fastContentScan(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null

        val localBlockRegex = blockRegex ?: return null
        val localExemptRegex = exemptRegex

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(AccessibilityNodeInfo.obtain(root))

        var foundViolationKeyword: String? = null
        var isExempt = false

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val nodePkg = node.packageName?.toString() ?: ""
            if (nodePkg.isNotEmpty() && (
                nodePkg.equals(ourPackageName, ignoreCase = true) ||
                nodePkg.startsWith("com.aistudio.shieldblocker", ignoreCase = true) ||
                nodePkg.contains("shieldblocker", ignoreCase = true) ||
                nodePkg.startsWith("com.example", ignoreCase = true)
            )) {
                node.recycle()
                continue
            }

            val text = node.text
            val desc = node.contentDescription

            // Fast Educational Exemption Check First
            if (!isExempt && localExemptRegex != null) {
                if ((text != null && localExemptRegex.containsMatchIn(text)) ||
                    (desc != null && localExemptRegex.containsMatchIn(desc))
                ) {
                    isExempt = true
                    node.recycle()
                    break
                }
            }

            // Positive Match Blocking Scan
            if (foundViolationKeyword == null) {
                val match = if (text != null) localBlockRegex.find(text) else null
                if (match != null) {
                    foundViolationKeyword = match.value
                } else {
                    val descMatch = if (desc != null) localBlockRegex.find(desc) else null
                    if (descMatch != null) {
                        foundViolationKeyword = descMatch.value
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    stack.add(child)
                }
            }
            node.recycle()
        }

        // Guaranteed leak cleanup for any unvisited nodes on early breaks
        while (stack.isNotEmpty()) {
            stack.removeLast().recycle()
        }

        return if (isExempt) null else foundViolationKeyword
    }

    private fun scanWindowForBlock(root: AccessibilityNodeInfo?, targets: List<String>, actions: List<String>): Boolean {
        if (root == null) return false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(AccessibilityNodeInfo.obtain(root))

        var foundTarget = false
        var foundAction = false

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val nodePkg = node.packageName?.toString() ?: ""
            if (nodePkg.isNotEmpty() && (
                nodePkg.equals(ourPackageName, ignoreCase = true) ||
                nodePkg.startsWith("com.aistudio.shieldblocker", ignoreCase = true) ||
                nodePkg.contains("shieldblocker", ignoreCase = true) ||
                nodePkg.startsWith("com.example", ignoreCase = true)
            )) {
                node.recycle()
                continue
            }

            if (!foundTarget || !foundAction) {
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val viewId = node.viewIdResourceName ?: ""

                if (!foundTarget) {
                    if (targets.any { text.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) || viewId.contains(it, ignoreCase = true) }) {
                        foundTarget = true
                    }
                }

                if (!foundAction) {
                    if (actions.any { text.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) || viewId.contains(it, ignoreCase = true) }) {
                        foundAction = true
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    stack.add(child)
                }
            }
            node.recycle()
        }

        while (stack.isNotEmpty()) {
            stack.removeLast().recycle()
        }

        return foundTarget && foundAction
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

    private fun performBlockRedirect(eventPkg: String = "") {
        // 1. Force the target browser to open a blank page to clear the active address bar state and fragment session
        if (eventPkg.isNotEmpty() && !isPackageWhitelisted(eventPkg)) {
            val lowerPkg = eventPkg.lowercase()
            if (lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox") || lowerPkg.contains("opera") || lowerPkg.contains("sbrowser")) {
                try {
                    val blankIntent = Intent(Intent.ACTION_VIEW, Uri.parse("about:blank")).apply {
                        `package` = eventPkg
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(blankIntent)
                } catch (e: Exception) {
                    try {
                        val generalIntent = Intent(Intent.ACTION_VIEW, Uri.parse("about:blank")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(generalIntent)
                    } catch (ex: Exception) {}
                }
            }
        }

        // 2. Send double back action to disrupt resumed tab state and go back in history
        if (eventPkg.isNotEmpty() && !isPackageWhitelisted(eventPkg)) {
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                // Ignore backup issues
            }
        }

        // 3. Perform main redirect to safety on the home screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 4. Force-kill the background process of the app to invalidate its running transient RAM cache
        if (eventPkg.isNotEmpty() && !isPackageWhitelisted(eventPkg)) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(eventPkg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SecureShield Notifications",
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
        const val CONTENT_SCAN_DELAY_MS = 100L
        const val CHANNEL_ID = "shield_blocker_channel"
        const val BLOCK_NOTIF_ID = 2026
        var isServiceRunning = false
        var instance: BlockerAccessibilityService? = null

        fun forceTriggerAction() {
            instance?.performBlockRedirect()
        }
    }
}
