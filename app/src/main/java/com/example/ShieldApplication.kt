package com.example

import android.app.Application
import android.os.Build
import java.lang.reflect.Method

class ShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        bypassHiddenApiRestrictions()
    }

    private fun bypassHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val getDeclaredMethodMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                Class.forName("[Ljava.lang.Class;")
            )
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntimeMethod = getDeclaredMethodMethod.invoke(
                vmRuntimeClass,
                "getRuntime",
                null
            ) as Method
            val setHiddenApiExemptionsMethod = getDeclaredMethodMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf(Class.forName("[Ljava.lang.String;"))
            ) as Method
            val vmRuntimeInstance = getRuntimeMethod.invoke(null)
            setHiddenApiExemptionsMethod.invoke(
                vmRuntimeInstance,
                arrayOf(arrayOf("L"))
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
