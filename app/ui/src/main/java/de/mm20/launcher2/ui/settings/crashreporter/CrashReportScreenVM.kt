package de.mm20.launcher2.ui.settings.crashreporter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.flow.flow
import java.io.File
import java.net.URLEncoder

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
        val uri = try {
            FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                File(crashReport.filePath)
            )
        } catch (e: IllegalArgumentException) {
            null
        }
        // text/plain (not */*), so that the file can be saved, and the full report as text (not
        // only the device information) for apps that only take the text
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, crashReport.summary)
        intent.putExtra(Intent.EXTRA_TEXT, getReportText(context, crashReport))
        if (uri != null) intent.putExtra(Intent.EXTRA_STREAM, uri)
        context.tryStartActivity(Intent.createChooser(intent, "Share via"))
    }

}
