package de.mm20.launcher2.ui.settings.crashreporter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class CrashReportScreenVM : ViewModel() {
    fun getCrashReport(fileName: String) = flow<CrashReport?> {
        emit(CrashReporter.getCrashReport(fileName))
    }

    fun getDeviceInformation(context: Context): String {
        return CrashReporter.getDeviceInformation(context)
    }

    fun createIssue(context: Context, crashReport: CrashReport) {
        val stacktrace = crashReport.stacktrace?.lines()?.let {
            if (it.size > 15) it.subList(0, 15)
                .joinToString("\n") + "\n[${it.size - 15} lines truncated]"
            else it.joinToString("\n")
        } ?: ""
        val body =
            "## Description\n\n" +
                    "*Please provide as many information about the crash as possible (What did you do before the crash happened? Steps to reproduce?)*\n\n" +
                    "## Stack trace\n\n" +
                    "```\n" +
                    "${stacktrace}\n" +
                    "```\n\n" +
                    "## Device info\n" +
                    "${getDeviceInformation(context).replace("\n", "<br>")}\n"
        // Fork builds report to the fork's issue tracker, not to upstream
        val url = "https://github.com/abhi-k9/Kvaesitso/issues/new?labels=crash+report&body=${
            URLEncoder.encode(
                body,
                "utf8"
            )
        }"
        context.tryStartActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }

    /**
     * The full report (stack trace and device information) as plain text
     */
    private fun getReportText(context: Context, crashReport: CrashReport): String {
        return "${crashReport.stacktrace ?: crashReport.summary}\n\n${getDeviceInformation(context)}"
    }

    fun copyCrashReport(context: Context, crashReport: CrashReport) {
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(
            ClipData.newPlainText("Kvaesitso crash report", getReportText(context, crashReport))
        )
    }

    fun shareCrashReport(context: Context, crashReport: CrashReport) {
        viewModelScope.launch {
            // Share a copy like the log export: crash report file names contain the time with
            // colons, which aren't allowed on shared storage, so files apps couldn't save them.
            val file = withContext(Dispatchers.IO) {
                try {
                    val time = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(crashReport.time)
                    val type = if (crashReport.type == CrashReportType.Crash) "crash" else "exception"
                    val dir = File(context.cacheDir, "crashreports").apply { mkdirs() }
                    File(dir, "kvaesitso-$type-$time.txt").apply {
                        writeText(getReportText(context, crashReport))
                    }
                } catch (e: IOException) {
                    Log.e("CrashReportScreenVM", "Could not create crash report file", e)
                    null
                }
            } ?: return@launch
            val uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                file
            )
            context.tryStartActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }, null
                )
            )
        }
    }

}
