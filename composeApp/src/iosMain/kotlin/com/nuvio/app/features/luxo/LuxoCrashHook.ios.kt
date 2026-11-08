package com.nuvio.app.features.luxo

import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import platform.Foundation.NSException
import platform.Foundation.NSSetUncaughtExceptionHandler
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

/**
 * iPhone/iPad: keeps the details of a crash so the next launch shows them (LuxoCrashReportDialog),
 * the same way the Android build already does. Covers Kotlin exceptions and Objective-C exceptions.
 */
private val installed = AtomicInt(0)

private fun saveCrash(kind: String, details: String) {
    runCatching {
        val device = UIDevice.currentDevice
        val text = "iOS ${device.systemVersion} · ${device.model}\n$kind\n\n$details".take(12000)
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setObject(text, forKey = "luxo_customization_last_crash")
        defaults.synchronize()
    }
}

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
fun luxoInstallIosCrashHook() {
    if (!installed.compareAndSet(0, 1)) return
    var previous: ReportUnhandledExceptionHook? = null
    previous = setUnhandledExceptionHook { error ->
        saveCrash("Kotlin: ${error::class.simpleName}: ${error.message}", error.stackTraceToString())
        previous?.invoke(error)
    }
    NSSetUncaughtExceptionHandler(
        staticCFunction { exception: NSException? ->
            val stack = exception?.callStackSymbols?.joinToString("\n") { it.toString() }.orEmpty()
            saveCrash("ObjC: ${exception?.name}: ${exception?.reason}", stack)
        },
    )
}
