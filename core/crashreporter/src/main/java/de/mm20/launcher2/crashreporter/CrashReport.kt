package de.mm20.launcher2.crashreporter

import android.icu.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class CrashReport(
    val type: CrashReportType,
    val time: Date,
    val summary: String,
    val stacktrace: String?,
    val filePath: String
) {
    companion object {
        suspend fun fromFile(file: File, loadStackTrace: Boolean): CrashReport {
            // The file name contains the time too, but formatted with the locale (digits, calendar)
            // at the time of the crash, so it can't be parsed reliably.
            val time = Date(file.lastModified())
            val content = if (loadStackTrace) {
                withContext(Dispatchers.IO) {
                    file.inputStream().bufferedReader().use {
                        it.readText()
                    }
                }
            } else null
            val summary = content?.substringBefore("\n")
                ?: withContext(Dispatchers.IO) {
                    file.inputStream().bufferedReader().use {
                        it.readLine() ?: ""
                    }
                }
            return CrashReport(
                type = if (file.name.endsWith("_crash.txt")) CrashReportType.Crash else CrashReportType.Exception,
                time = time,
                summary = summary,
                stacktrace = content,
                filePath = file.absolutePath
            )
        }
    }
}

enum class CrashReportType {
    Exception,
    Crash
}